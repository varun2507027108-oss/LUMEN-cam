package com.auroracam.app.ui.model

/**
 * Contextual Telemetry Display Modes.
 *
 * When idle/framing: displays DEFAULT (FPS | Shutter | ISO).
 * When interacting with exposure, focus, or ISO: the strip dynamically
 * morphs into the corresponding measurement scale.
 */
enum class ContextualTelemetryMode {
    DEFAULT,
    EV_ADJUST,
    FOCUS_ADJUST,
    ISO_ADJUST
}
