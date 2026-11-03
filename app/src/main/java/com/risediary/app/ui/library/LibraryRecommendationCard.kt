package com.risediary.app.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.risediary.app.data.library.LibraryAsset
import com.risediary.app.ui.icons.AppIcons
import com.risediary.app.ui.video.VideoGlassButton
import top.yukonga.miuix.kmp.basic.Text

@Composable
internal fun LibraryRecommendationCard(asset: LibraryAsset, height: Dp, enabled: Boolean, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val backdrop = rememberLayerBackdrop()
    Box(modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp))
        .background(Color.Black).clickable(enabled = enabled, onClickLabel = "观看每日推荐", onClick = onOpen)
        .testTag("library-daily-recommendation").semantics { contentDescription = "每日推荐：${asset.name}" }) {
        Box(Modifier.matchParentSize().layerBackdrop(backdrop)) {
            LibraryCover(asset, Modifier.fillMaxSize(), highResolution = true)
            Box(Modifier.matchParentSize().background(Brush.verticalGradient(
                0f to Color.Black.copy(alpha = .65f), .4f to Color.Black.copy(alpha = .12f), 1f to Color.Black.copy(alpha = .82f))))
        }
        Row(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("每日推荐", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.SemiBold, maxLines = 2)
                Text(asset.name, color = Color.White.copy(alpha = .85f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            VideoGlassButton(onOpen, backdrop, Modifier.size(48.dp), AppIcons.ChevronRight,
                description = "观看每日推荐", fullScreen = true, enabled = enabled)
        }
    }
}

/** A spanning grid item bleeds through its 20dp content gutters, staying inside the viewport. */
internal fun Modifier.libraryFullBleed(gutter: Dp = 20.dp): Modifier = layout { measurable, constraints ->
    val inset = gutter.roundToPx()
    val width = constraints.maxWidth
    val child = measurable.measure(constraints.copy(minWidth = width + inset * 2, maxWidth = width + inset * 2))
    layout(width, child.height) { child.placeRelative(-inset, 0) }
}
