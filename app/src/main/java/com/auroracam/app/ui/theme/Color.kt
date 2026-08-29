package com.auroracam.app.ui.theme

import androidx.compose.ui.graphics.Color

// =========================================================================
// AURORACAM PHOTOGRAPHIC CONTROL SURFACE COLOR SYSTEM
// A restrained, cinematic material palette designed around the photograph.
// =========================================================================

// 1. Structural Surfaces
val ObsidianChassis = Color(0xFF08090B)     // Base obsidian black chassis
val DarkGraphite = Color(0xFF121417)        // Deep control surface
val GraphiteSurface = Color(0xFF1B1E22)     // Raised surface
val HairlineBorder = Color(0xFF262A2E)      // Precision hairline boundary
val HairlineSubtle = Color(0x1FFFFFFF)      // Ultra-subtle optical line
val SmokedScrim = Color(0xDC0A0C0E)         // Floating scrim overlay

// 2. Photographic Typography
val WarmSilver = Color(0xFFE8E5E0)          // Primary warm photographic white (#E8E5E0)
val Ash = Color(0xFF8C9094)                 // Secondary low-emphasis metadata (#8C9094)
val MutedText = Color(0xFF55595D)           // Subdued labels & inactive markings

// 3. Photographic Accents (Used VERY sparingly — active needles and indicators only)
val AuroraBrass = Color(0xFFD4B27C)         // Active needle, selected indicator
val AuroraBrassDim = Color(0x24D4B27C)      // Subtle active halo
val FocusGreen = Color(0xFF98D8AA)          // Focus lock, focus peaking confirmation
val FocusGreenDim = Color(0x2B98D8AA)       // Peaking halo fill
val SignalAlert = Color(0xFFBA4A43)         // Recording, exposure warning, error

// Standard Aliases for Clean Imports
val Obsidian = ObsidianChassis
val Graphite = DarkGraphite
val SurfaceRaised = GraphiteSurface
val SurfaceRecess = ObsidianChassis
val ParchmentWhite = WarmSilver
val WarmSlate = Ash
val BurntBrass = AuroraBrass
val BurntBrassDim = AuroraBrassDim
val OpticalGreen = FocusGreen
val OpticalGreenDim = FocusGreenDim
val OpticalGreenBright = FocusGreen
val SignalRuby = SignalAlert
val TextDisabled = MutedText

// Backward-Compatibility Aliases
val SurfaceDark = ObsidianChassis
val SurfaceElevated = DarkGraphite
val SurfaceActiveCard = GraphiteSurface
val SmokedGlass = SmokedScrim
val SmokedChipBg = Color(0x66121417)
val BorderHairline = HairlineBorder
val GlassBorder = HairlineSubtle
val PureWhite = WarmSilver
val NeutralSlate = Ash
val WarmAmber = AuroraBrass
val WarmAmberDim = AuroraBrassDim
val FocusMint = FocusGreen
val FocusMintDim = FocusGreenDim
val ObsidianBlack = ObsidianChassis
val DarkBackground = ObsidianChassis
val DarkSurface = ObsidianChassis
val ElevatedSurface = DarkGraphite
val SlateBorder = HairlineBorder
val GlassHighlight = HairlineSubtle
val SlateDivider = HairlineBorder
val OverlayBackground = Color(0x99000000)
val SolarGold = AuroraBrass
val SolarGoldDim = AuroraBrassDim
val SolarOrange = AuroraBrass
val AmberGold = AuroraBrass
val AmberGoldDim = AuroraBrassDim
val OpticCyan = Ash
val OpticCyanDim = AuroraBrassDim
val TelemetryCobalt = Ash
val TelemetryCobaltDim = AuroraBrassDim
val HyperSilver = WarmSilver
val StatusGreen = FocusGreen
val StatusRed = SignalAlert
val White = WarmSilver
val TextPrimary = WarmSilver
val TextSecondary = Ash
val AuroraCyan = Ash
val AuroraAmber = AuroraBrass
val LookTeal = AuroraBrass
val LookWarmth = AuroraBrass
val LookMonochrome = Ash
val LookNostalgia = AuroraBrass
val LookEmerald = FocusGreen
