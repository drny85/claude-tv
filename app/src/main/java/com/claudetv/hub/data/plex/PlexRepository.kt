package com.claudetv.hub.data.plex

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

sealed interface PlexState {
    data object SignedOut : PlexState
    data class Connecting(val serverName: String?) : PlexState
    /** Signed in, but more than one server; the person picks. */
    data class ChooseServer(val servers: List<PlexServer>) : PlexState
    data class Connected(val server: ConnectedServer) : PlexState
    data class Error(val message: String, val canRetry: Boolean) : PlexState
}

/** Plex sign-in state and the active server, remembered on the TV. */
class PlexRepository(context: Context) {
    private val prefs = context.getSharedPreferences("claude_tv_plex", Context.MODE_PRIVATE)

    val clientId: String = prefs.getString(KEY_CLIENT, null) ?: UUID.randomUUID().toString().also {
        prefs.edit().putString(KEY_CLIENT, it).apply()
    }
    val api = PlexApi(clientId)

    private var userToken: String? = prefs.getString(KEY_USER_TOKEN, null)

    private val _state = MutableStateFlow<PlexState>(if (userToken == null) PlexState.SignedOut else PlexState.Connecting(prefs.getString(KEY_SERVER_NAME, null)))
    val state: StateFlow<PlexState> = _state.asStateFlow()

    val server: ConnectedServer? get() = (state.value as? PlexState.Connected)?.server

    /** Reconnects on launch. Tries the last address first so startup is instant on the home network. */
    suspend fun restore() {
        val token = userToken ?: return
        val lastUrl = prefs.getString(KEY_SERVER_URL, null)
        val lastToken = prefs.getString(KEY_SERVER_TOKEN, null)
        val lastId = prefs.getString(KEY_SERVER_ID, null)
        val lastName = prefs.getString(KEY_SERVER_NAME, null)
        if (lastUrl != null && lastToken != null && lastId != null && lastName != null) {
            val quick = PlexServer(lastName, lastId, lastToken, true, listOf(PlexConnection(lastUrl, true, false)))
            api.connect(quick)?.let { setConnected(it); return }
        }
        discover(token, preferId = lastId)
    }

    suspend fun onSignedIn(token: String) {
        userToken = token
        prefs.edit().putString(KEY_USER_TOKEN, token).apply()
        discover(token, preferId = null)
    }

    private suspend fun discover(token: String, preferId: String?) {
        _state.value = PlexState.Connecting(null)
        val servers = runCatching { api.servers(token) }.getOrElse {
            _state.value = PlexState.Error(
                if (it is PlexException && it.code == 401) "Your Plex sign-in expired. Sign in again." else "Couldn't reach plex.tv. Check your connection.",
                canRetry = !(it is PlexException && it.code == 401),
            )
            if (it is PlexException && it.code == 401) signOut()
            return
        }
        when {
            servers.isEmpty() -> _state.value = PlexState.Error("No Plex Media Server found on this account.", canRetry = true)
            preferId != null && servers.any { it.clientId == preferId } -> choose(servers.first { it.clientId == preferId })
            servers.size == 1 -> choose(servers.first())
            else -> _state.value = PlexState.ChooseServer(servers.sortedByDescending { it.owned })
        }
    }

    suspend fun choose(server: PlexServer) {
        _state.value = PlexState.Connecting(server.name)
        val connected = api.connect(server)
        if (connected == null) {
            _state.value = PlexState.Error("Couldn't reach “${server.name}”. Make sure it's running and on the same network.", canRetry = true)
        } else {
            setConnected(connected)
        }
    }

    suspend fun retry() {
        userToken?.let { discover(it, prefs.getString(KEY_SERVER_ID, null)) } ?: run { _state.value = PlexState.SignedOut }
    }

    private fun setConnected(s: ConnectedServer) {
        prefs.edit()
            .putString(KEY_SERVER_URL, s.baseUrl)
            .putString(KEY_SERVER_TOKEN, s.token)
            .putString(KEY_SERVER_ID, s.clientId)
            .putString(KEY_SERVER_NAME, s.name)
            .apply()
        _state.value = PlexState.Connected(s)
    }

    fun signOut() {
        userToken = null
        prefs.edit()
            .remove(KEY_USER_TOKEN).remove(KEY_SERVER_URL).remove(KEY_SERVER_TOKEN)
            .remove(KEY_SERVER_ID).remove(KEY_SERVER_NAME)
            .apply()
        _state.value = PlexState.SignedOut
    }

    /** Lets Settings switch servers without signing out. */
    suspend fun switchServer() {
        userToken?.let { discover(it, preferId = null) }
    }

    private companion object {
        const val KEY_CLIENT = "client_id"
        const val KEY_USER_TOKEN = "user_token"
        const val KEY_SERVER_URL = "server_url"
        const val KEY_SERVER_TOKEN = "server_token"
        const val KEY_SERVER_ID = "server_id"
        const val KEY_SERVER_NAME = "server_name"
    }
}
