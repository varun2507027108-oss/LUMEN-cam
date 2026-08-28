package com.auroracam.app.ui

enum class CameraMode(val title: String) {
    STANDARD("Standard"),
    DOUBLE_EXPOSURE("Double Exposure")
}

enum class BlendMode(val modeId: Int, val label: String) {
    NORMAL(0, "Normal"),
    SCREEN(1, "Screen"),
    LIGHTEN(2, "Lighten"),
    ADD(3, "Add"),
    MULTIPLY(4, "Multiply"),
    OVERLAY(5, "Overlay")
}

enum class DxStage {
    STAGE_1_EMPTY, // Waiting for first exposure ("1/2")
    STAGE_2_LOCKED // First exposure locked ("2/2"), live blending
}
