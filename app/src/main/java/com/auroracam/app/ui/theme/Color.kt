package com.auroracam.app.ui.theme

import androidx.compose.ui.graphics.Color

// =========================================================================
// CINEMA & PRO PHOTOGRAPHY DESIGN TOKENS (Multi-Domain Chromatic Architecture)
// =========================================================================

// 1. Structural Surfaces (Smoked Titanium, Dark Slate & Glass Highlights)
val ObsidianBlack = Color(0xFF08090C)
val DarkBackground = Color(0xFF0C0E12)
val DarkSurface = Color(0xFF14171E)
val ElevatedSurface = Color(0xFF1E232D)
val SmokedGlass = Color(0xD912151E)
val GlassHighlight = Color(0x38FFFFFF)
val SlateBorder = Color(0x2EFFFFFF)
val SlateDivider = Color(0x1AFFFFFF)
val OverlayBackground = Color(0x80000000)

// 2. Exposure & Solar Domain (Rich Solar Amber, Sun Gold, Flare Orange)
val SolarGold = Color(0xFFFFB703)
val SolarGoldDim = Color(0x33FFB703)
val SolarOrange = Color(0xFFFB8500)
val AmberGold = Color(0xFFFFB703)
val AmberGoldDim = Color(0x33FFB703)

// 3. Optical Precision & Focus / AE/AF Locking (Pin-sharp Focus Mint & Laser Cyan)
val FocusMint = Color(0xFF00E599)
val FocusMintDim = Color(0x3300E599)
val OpticCyan = Color(0xFF06B6D4)
val OpticCyanDim = Color(0x3306B6D4)

// 4. Telemetry & Sensor Dynamics (Cobalt & Hyper-Silver)
val TelemetryCobalt = Color(0xFF38BDF8)
val TelemetryCobaltDim = Color(0x2838BDF8)
val HyperSilver = Color(0xFFF1F5F9)

// 5. Recording, Stacking & System Status (Signal Ruby & Precision Green)
val SignalRuby = Color(0xFFFF2A4B)
val SignalRubyDim = Color(0x33FF2A4B)
val StatusGreen = FocusMint
val StatusRed = SignalRuby

// 6. Film Looks & Color Chemistry Accents
val LookTeal = Color(0xFF14B8A6)
val LookWarmth = Color(0xFFF97316)
val LookMonochrome = Color(0xFFE2E8F0)
val LookNostalgia = Color(0xFFA855F7)
val LookEmerald = Color(0xFF10B981)

// 7. Micro-Typography & Contrast Hierarchy
val White = Color(0xFFFFFFFF)
val TextPrimary = Color(0xFFF8FAFC)
val TextSecondary = Color(0xFF94A3B8)
val TextMuted = Color(0xFF64748B)

// Compatibility aliases
val AuroraCyan = OpticCyan
val AuroraAmber = SolarGold
