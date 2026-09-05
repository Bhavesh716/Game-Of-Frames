package com.instantcollabmaker.ui.theme

import androidx.compose.ui.unit.dp

/** Spacing scale. Every gap in the app comes from here. */
object Spacing {
    val xxs = 2.dp
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 20.dp
    val xxl = 24.dp
    val xxxl = 32.dp
    val huge = 40.dp
    val giant = 56.dp

    /** Horizontal page gutter, shared by every screen so edges line up. */
    val gutter = 20.dp
}

/** Corner radius scale. */
object Radius {
    val xs = 6.dp
    val sm = 10.dp
    val md = 16.dp
    val lg = 22.dp
    val xl = 28.dp
    val pill = 999.dp
}

/** One-off sizes that need to agree across screens. */
object Sizes {
    val hairline = 1.dp
    val minTouchTarget = 48.dp
    val buttonHeight = 56.dp
    val buttonHeightCompact = 48.dp
    val iconSm = 16.dp
    val iconMd = 20.dp
    val iconLg = 24.dp
    val topBarHeight = 56.dp
    val personThumb = 68.dp
    val progressRing = 172.dp

    /** Portrait video aspect ratio used for every preview surface. */
    const val PORTRAIT_ASPECT = 9f / 16f
}
