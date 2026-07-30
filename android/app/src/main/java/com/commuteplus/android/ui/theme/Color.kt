package com.commuteplus.android.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Commute+ color palette.
 *
 * Design rules (from ui-taste.md):
 * - Neutral surfaces + one brand accent for primary actions.
 * - Mode colors are semantic: they MEAN something (transport type).
 * - Never decorative gradients. No rainbow of unrelated accents.
 * - WCAG AA contrast on every surface.
 */

// Brand accent (one only — teal/green, signals "go" / transit / movement)
val BrandAccent = Color(0xFF0D7377)
val BrandAccentLight = Color(0xFF14919B)

// Neutral surfaces
val SurfaceLight = Color(0xFFFAFAFA)
val SurfaceDark = Color(0xFF121212)
val SurfaceContainerLight = Color(0xFFFFFFFF)
val SurfaceContainerDark = Color(0xFF1E1E1E)

// Text
val OnSurfaceLight = Color(0xFF1A1A1A)
val OnSurfaceDark = Color(0xFFE8E8E8)
val OnSurfaceVariantLight = Color(0xFF5A5A5A)
val OnSurfaceVariantDark = Color(0xFFA0A0A0)

// --- Transport mode colors (semantic, consistent everywhere) ---
val ModeBus = Color(0xFF2E7D32)         // Green — earthy, public, BMTC green
val ModeMetroPurple = Color(0xFF6A1B9A)  // Purple Line's actual color
val ModeMetroGreen = Color(0xFF2E7D32)   // Green Line's actual color
val ModeAuto = Color(0xFFF9A825)         // Warm yellow — auto-rickshaw association
val ModeBikeTaxi = Color(0xFFE65100)     // Orange — Rapido brand association
val ModeCab = Color(0xFF1565C0)          // Blue — neutral, professional
val ModeWalk = Color(0xFF757575)         // Gray — subtle, not a mode you "choose"
