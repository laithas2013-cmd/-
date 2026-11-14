package com.example.ui.theme

import androidx.compose.ui.graphics.Color

// Tawasul Plus Brand Palette — Pure White & Solar Amber-Yellow
val TawasulGold = Color(0xFFF5B700)
val TawasulGoldDeep = Color(0xFFD49600)
val TawasulGoldSoft = Color(0xFFFFF3C4)
val TawasulGoldChip = Color(0xFFFFE799)
val TawasulOnGold = Color(0xFF1F1600)

val TawasulWhite = Color(0xFFFFFFFF)
val TawasulIvory = Color(0xFFFFFDF7)
val TawasulCream = Color(0xFFFAF6E8)
val TawasulSurfaceVariantLight = Color(0xFFF5EED6)
val TawasulInkLight = Color(0xFF18181B)
val TawasulMutedInkLight = Color(0xFF6B6556)
val TawasulOutlineLight = Color(0xFFE6DDBF)

// Dark Theme Tokens — Obsidian & Luminous Gold
val TawasulGoldDark = Color(0xFFFBBF24)
val TawasulGoldContainerDark = Color(0xFF473300)
val TawasulOnGoldContainerDark = Color(0xFFFFE599)

val TawasulObsidian = Color(0xFF0E1015)
val TawasulCharcoal = Color(0xFF171A21)
val TawasulCharcoalElevated = Color(0xFF222631)
val TawasulInkDark = Color(0xFFF4F4F5)
val TawasulMutedInkDark = Color(0xFFA1A1AA)
val TawasulOutlineDark = Color(0xFF323746)

// Semantic Status Colors
val TawasulOnlineGreen = Color(0xFF16A34A)
val TawasulReadBlue = Color(0xFF0284C7)
val TawasulErrorRed = Color(0xFFDC2626)
val TawasulErrorDark = Color(0xFFF87171)

// Status Card Swatches (keyed by backgroundHex saved in Firestore)
val StatusSwatchColors: Map<String, Color> =
  mapOf(
    "#F5B700" to Color(0xFFD49600),
    "#0F766E" to Color(0xFF0F766E),
    "#1E293B" to Color(0xFF1E293B),
    "#B91C1C" to Color(0xFFB91C1C),
    "#6D28D9" to Color(0xFF6D28D9),
  )
