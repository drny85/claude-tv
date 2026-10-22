package com.claudetv.hub.platform

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import com.claudetv.hub.data.StreamingService
import com.claudetv.hub.data.Title
import java.net.URLEncoder

sealed interface LaunchResult {
    /** The service's app opened on a search for the title. */
    data class OpenedSearch(val service: StreamingService) : LaunchResult
    /** The service's app opened on its home screen. */
    data class OpenedApp(val service: StreamingService) : LaunchResult
    /** The app isn't installed; we opened its Play Store page instead. */
    data class NotInstalled(val service: StreamingService, val openedStore: Boolean) : LaunchResult
    data class Failed(val message: String) : LaunchResult
}

/**
 * Hands playback off to the official streaming app.
 *
 * Exact title deep links need each service's private content id, which no public API
 * exposes, so we try the service's web search link inside its app first and fall back
 * to simply launching the app.
 */
object AppLauncher {

    fun installedPackage(context: Context, service: StreamingService): String? =
        service.packages.firstOrNull { isInstalled(context.packageManager, it) }

    fun launch(context: Context, service: StreamingService, title: Title): LaunchResult {
        val pm = context.packageManager
        val pkg = installedPackage(context, service)
            ?: return LaunchResult.NotInstalled(service, openStore(context, service.packages.first()))

        service.searchUrl?.let { template ->
            val url = template.format(URLEncoder.encode(title.name, "UTF-8"))
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).setPackage(pkg)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (intent.resolveActivity(pm) != null && start(context, intent)) {
                return LaunchResult.OpenedSearch(service)
            }
        }

        val launch = pm.getLeanbackLaunchIntentForPackage(pkg) ?: pm.getLaunchIntentForPackage(pkg)
        return if (launch != null && start(context, launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))) {
            LaunchResult.OpenedApp(service)
        } else {
            LaunchResult.Failed("Couldn't open ${service.displayName}")
        }
    }

    /** Opens a YouTube trailer in the YouTube app for TV, or any app that handles the link. */
    fun playTrailer(context: Context, youtubeKey: String): Boolean {
        val app = Intent(Intent.ACTION_VIEW, Uri.parse("vnd.youtube:$youtubeKey")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (start(context, app)) return true
        val web = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/watch?v=$youtubeKey")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return start(context, web)
    }

    private fun openStore(context: Context, pkg: String): Boolean =
        start(context, Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$pkg")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))

    private fun start(context: Context, intent: Intent): Boolean = try {
        context.startActivity(intent)
        true
    } catch (e: ActivityNotFoundException) {
        false
    } catch (e: SecurityException) {
        false
    }

    private fun isInstalled(pm: PackageManager, pkg: String): Boolean = try {
        pm.getPackageInfo(pkg, 0)
        true
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }
}
