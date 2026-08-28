# AuroraCam

> Live double exposure, real-time 3D LUT color grading, and temporal effects — rendered entirely on the GPU in the viewfinder before pressing the shutter.

AuroraCam is a modern, high-performance creative camera application for Android built with **Jetpack Compose**, **direct Camera2 HAL integration**, and **OpenGL ES 3.0**.

---

## Key Features

### 🎨 Live 3D LUT Color Grading & Tone Engine
- **Hardware-Accelerated 3D Textures:** Parses `.cube` files (standard 17³, 33³, 64³ grid sizes) directly on the GPU using `GL_TEXTURE_3D` with trilinear filtering.
- **Built-in Presets:** Warm Gold, Cyberpunk, Cinema, Monochrome, and Emerald with instantaneous live preview.
- **Custom LUT Import:** Import custom `.cube` grading LUTs from device storage via Android Storage Access Framework (SAF).
- **16-Bit Float HDR Pipeline:** High dynamic range intermediate framebuffers (`GL_RGBA16F`) preventing color banding in dark gradients and bright highlights.
- **Look Intensity & Halation Glow:** Real-time bloom/glow blur shader simulating vintage analog film halation around high-contrast edges.

### 🎯 Precision Cinema Focus Reticle & Integrated Exposure Slider
- **Tap-to-Focus 3A Metering:** Tap anywhere on the viewfinder to dynamically update Camera2 `CONTROL_AF_REGIONS` and `CONTROL_AE_REGIONS` with auto-focus trigger cycles.
- **Cinema-Grade Reticle:** Minimalist golden corner brackets with center targeting reticle dot.
- **Integrated EV Exposure Slider:** Vertical draggable Sun slider positioned seamlessly next to the focus reticle. Adjust exposure bias (-2.0 EV to +2.0 EV) in real-time with live monospace readout (`+0.7`, `-1.3`).
- **Edge-Aware Positioning:** Automatically adapts to screen edges so sliders and reticles never clip off-screen.

### 📐 Framing & Aspect Ratio Formats
- **XPAN Panoramic (65:24):** True cinematic ultra-wide crop with orange viewfinder corner guides.
- **Square (1:1):** Classic medium format square framing with translucent letterboxing.
- **Standard (4:3):** Full sensor resolution capture.

### 🎛️ Bottom Options Shelf Ergonomics
- **Unobstructed Viewfinder:** All camera controls and menus are placed cleanly **below the shutter button**, maximizing viewfinder clarity.
- **Organized Control Tabs:**
  - **Looks:** Quick preset selector, `.cube` file importer, and reset.
  - **Tune:** Fine-tune Look intensity (0–100%) and halation glow (0–60%).
  - **Pro:** QuickStack burst toggle, 16-bit Float FBO toggle, and aspect ratio selector.
  - **Blend:** Real-time double exposure blend mode selector (Screen, Overlay, Add, Lighten, Multiply), opacity scrubber, and retake tools.

### ⚡ QuickStack Computational Burst
- High-speed 6-frame burst capture with phase-correlation spatial alignment and GPU temporal stacking for noise reduction in low-light scenes.

### 📸 Live GPU Double Exposure
- Two-stage multi-exposure pipeline:
  - **Stage 1 (Base):** Capture and lock first exposure with live silhouette exposure helper.
  - **Stage 2 (Composite):** Real-time viewfinder blending over the live camera stream with live opacity and horizontal flip controls.
  - Generates full provenance output: saves original base frame, second frame, and blended composite to disk with embedded metadata.

---

## Architectural Principles

1. **Zero CPU pixel work in the preview loop:** Frames stream directly from Camera2 `Surface(SurfaceTexture)` &rarr; `GL_TEXTURE_EXTERNAL_OES` &rarr; GPU multi-pass shaders &rarr; Screen FBO.
2. **Direct Camera2 Architecture:** Direct HAL stream management with zero buffer starvation, atomic telemetry collection (ISO, Shutter speed), and single-shot high-res captures.
3. **Single GL context ownership:** Pure multi-threaded rendering pipeline with synchronized frame invalidation.
4. **Scoped Storage Integration:** Photos saved seamlessly to `Pictures/AuroraCam` via MediaStore API with complete EXIF and telemetry metadata.

---

## Building and Running

### Requirements
- Android Studio Ladybug (2024.2+) or newer
- JDK 17
- Android SDK 35 (minSdk 29)

### CLI Build & Test
```bash
# Build Debug APK
./gradlew assembleDebug

# Run Unit Tests
./gradlew testDebugUnitTest

# Install to connected device via ADB
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

---

## Automated Releases via GitHub Actions

Pushing a tag formatted as `v*` (e.g. `v0.2.0`) automatically triggers GitHub Actions CI/CD to build the release APK and attach it to GitHub Releases.

---

## License

This project is licensed under the Apache License 2.0 - see the [LICENSE](LICENSE) file for details.
