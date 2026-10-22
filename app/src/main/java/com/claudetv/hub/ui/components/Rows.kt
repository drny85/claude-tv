package com.claudetv.hub.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import com.claudetv.hub.data.ContentRow
import com.claudetv.hub.data.Offer
import com.claudetv.hub.data.RowStyle
import com.claudetv.hub.data.StreamingService
import com.claudetv.hub.data.Title
import com.claudetv.hub.ui.theme.Dimens

/** Per-card extras a row may want to show. */
data class CardExtras(
    val service: StreamingService? = null,
    val included: Boolean = false,
    val caption: String? = null,
    val progress: Float? = null,
)

/** A titled, horizontally scrolling row of cards with tvOS-style pivot scrolling. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TitleRow(
    row: ContentRow,
    offers: Map<String, List<Offer>>,
    isIncluded: (List<Offer>?) -> Boolean,
    onOpen: (Title, String) -> Unit,
    onFocused: (Title) -> Unit,
    modifier: Modifier = Modifier,
    extras: (Title) -> CardExtras? = { null },
) {
    Column(modifier) {
        SectionHeader(row.title, row.subtitle, Modifier.padding(start = Dimens.ScreenH))
        CompositionLocalProvider(LocalBringIntoViewSpec provides rememberPivotSpec(0f, Dimens.ScreenH)) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = Dimens.ScreenH),
                horizontalArrangement = Arrangement.spacedBy(Dimens.CardGap),
            ) {
                itemsIndexed(row.items.distinctBy { it.key }, key = { _, t -> t.key }) { _, t ->
                    val focusKey = "${row.id}/${t.key}"
                    val e = extras(t)
                    val known = offers[t.key]
                    // Service rows already say where everything is, so cards there stay clean.
                    val service = e?.service ?: if (row.service != null) null else
                        (t.demoService ?: known?.firstOrNull { o -> isIncluded(listOf(o)) }?.service)
                    TitleCard(
                        title = t,
                        style = if (row.style == RowStyle.POSTER) RowStyle.POSTER else RowStyle.LANDSCAPE,
                        focusKey = focusKey,
                        onClick = { onOpen(t, focusKey) },
                        onFocused = { onFocused(t) },
                        service = service,
                        included = e?.included ?: (row.service == null && isIncluded(known)),
                        caption = e?.caption,
                        progress = e?.progress,
                    )
                }
            }
        }
    }
}
