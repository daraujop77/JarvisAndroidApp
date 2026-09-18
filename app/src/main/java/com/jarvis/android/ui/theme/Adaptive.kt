package com.jarvis.android.ui.theme

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp

@Composable
fun rememberAdaptiveLayout(): AdaptiveLayout {
    val cfg = LocalConfiguration.current
    return remember(cfg.screenWidthDp, cfg.screenHeightDp) {
        JarvisVisualSystem.layout(cfg.screenWidthDp.toFloat(), cfg.screenHeightDp.toFloat())
    }
}

/** Centers page content and caps width on tablets. */
@Composable
fun AdaptiveContent(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val layout = rememberAdaptiveLayout()
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter,
    ) {
        Box(
            Modifier
                .widthIn(max = layout.maxContentWidthDp.dp)
                .fillMaxWidth(),
        ) {
            content()
        }
    }
}
