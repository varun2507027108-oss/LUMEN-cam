package com.auroracam.app.ui.theme

import androidx.compose.ui.graphics.Color

// =========================================================================
// LEICA / HASSELBLAD INDUSTRIAL CAMERA INTERFACE DESIGN SYSTEM
// =========================================================================

// 1. Structural Surfaces
val SurfaceDark = Color(0xFF0F1115)       // Deep matte obsidian base
val SurfaceElevated = Color(0xFF181B22)   // Drawers, pills, dials, squircle buttons
val SurfaceActiveCard = Color(0xFF1F2430) // Selected mode / preset cards
val SmokedGlass = Color(0xB80F1115)       // Semi-transparent frosted scrim (alpha 0.72)
val SmokedChipBg = Color(0x66000000)      // Top diagnostic pill background
val BorderHairline = Color(0xFF2B313D)    // Subtle 1.dp control boundary
val GlassBorder = Color(0x33FFFFFF)       // 1.dp subtle glass highlight border

// 2. Monochromatic Text & Inactive Icons
val PureWhite = Color(0xFFF2F4F8)         // Active high-contrast typography
val NeutralSlate = Color(0xFF8E95A5)      // Secondary technical labels & inactive icons
val TextMuted = Color(0xFF636A79)         // Minor hints & subdued metadata

// 3. Single Brand Accent (Leica/Hasselblad Warm Amber)
val WarmAmber = Color(0xFFE5A00D)         // Active indicators, toggles, focus points
val WarmAmberDim = Color(0x2EE5A00D)      // Subtle warm amber active pill fill

// 4. Status & Precision Feedback
val FocusMint = Color(0xFF10B981)         // Pin-sharp focus & AE lock
val FocusMintDim = Color(0x3310B981)
val SignalRuby = Color(0xFFEF4444)        // Recording / critical error

// Compatibility Aliases to maintain zero breakage across the codebase
val ObsidianBlack = SurfaceDark
val DarkBackground = SurfaceDark
val DarkSurface = SurfaceDark
val ElevatedSurface = SurfaceElevated
val SlateBorder = BorderHairline
val GlassHighlight = GlassBorder
val SlateDivider = BorderHairline
val OverlayBackground = Color(0x80000000)
val SolarGold = WarmAmber
val SolarGoldDim = WarmAmberDim
val SolarOrange = WarmAmber
val AmberGold = WarmAmber
val AmberGoldDim = WarmAmberDim
val OpticCyan = NeutralSlate
val OpticCyanDim = WarmAmberDim
val TelemetryCobalt = NeutralSlate
val TelemetryCobaltDim = WarmAmberDim
val HyperSilver = PureWhite
val StatusGreen = FocusMint
val StatusRed = SignalRuby
val White = PureWhite
val TextPrimary = PureWhite
val TextSecondary = NeutralSlate
val AuroraCyan = NeutralSlate
val AuroraAmber = WarmAmber
val LookTeal = WarmAmber
val LookWarmth = WarmAmber
val LookMonochrome = NeutralSlate
val LookNostalgia = WarmAmber
val LookEmerald = FocusMint
