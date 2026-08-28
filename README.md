# AuroraCam

> Live double exposure, real-time 3D LUT color grading, temporal echo, motion-only exposure, and light trail accumulation — rendered entirely on the GPU in the viewfinder before pressing the shutter.

AuroraCam is a cinema-grade creative camera application for Android built with **Jetpack Compose**, **direct Camera2 HAL integration**, and **OpenGL ES 3.0**.

---

## 🌟 Key Features

### 🎨 Live 3D LUT Color Grading & Tone Engine
- **Hardware-Accelerated 3D Textures:** Parses `.cube` files (standard 17³, 33³, 64³ grid sizes) directly on the GPU using `GL_TEXTURE_3D` with trilinear filtering.
- **Built-in Presets:** Warm Gold, Cyberpunk, Cinema, Monochrome, and Emerald with instantaneous live preview.
- **Custom LUT Import:** Import custom `.cube` grading LUTs from device storage via Android Storage Access Framework (SAF).
- **16-Bit Float HDR Pipeline:** High dynamic range intermediate framebuffers (`GL_RGBA16F`) preventing color banding in dark gradients and bright highlights.
- **Local Tone Mapping & Sharpening:** Dynamic range compression and unsharp masking for crisp details.
- **Look Intensity & Halation Glow:** Real-time bloom/glow blur shader simulating vintage analog film halation around high-contrast edges.

### 🌀 Advanced Creative Modes
- **📸 Standard & QuickStack Computational Burst:** High-speed 6-frame burst capture with phase-correlation spatial alignment and GPU temporal stacking for noise reduction in low-light scenes.
- **👥 Live GPU Double Exposure:** Two-stage multi-exposure pipeline with live viewfinder blending (Screen, Overlay, Add, Lighten, Multiply), opacity scrubber, and horizontal flip controls.
- **👻 Temporal Echo (Ghost Trails):** 3-frame GPU history ring buffer blending (`glBlitFramebuffer`) with exponential decay and blend modes to produce vintage multi-image motion trails.
- **🏃 Motion-Only Exposure:** Real-time luminance frame-differencing shader pass isolating moving subjects from static backgrounds with adjustable motion thresholds.
- **✨ Light Trails Mode:** Ping-Pong accumulation FBO max/decay blend engine simulating long exposure light painting in real-time with instant reset controls.

### 🌈 Radial Chromatic Aberration Post-Processing
- Realistic lens wavelength RGB dispersion shader applying radial offsets based on distance from the optical center with full aspect ratio correction.

### 🎛️ Rotary Parameter Wheel & Bottom Control Shelf
- **Unobstructed Viewfinder:** All camera controls and menus are placed cleanly **below the shutter button**, maximizing viewfinder clarity.
- **Tactile Parameter Wheel:** Circular rotary dial UI with angular gesture tracking for precision real-time tuning of:
  - `Echo Decay` (%)
  - `Motion Threshold`
  - `Light Decay` (%)
  - `Aberration` (%)
  - `Look Mix` (%)
  - `Halation Glow` (%)
- **Organized Control Tabs:**
  - **LOOKS:** Quick preset selector, `.cube` file importer, and reset.
  - **EFFECTS:** Interactive rotary parameter wheel for active effects.
  - **FINE_TUNE:** Fine-tune Look intensity (0–100%), halation glow (0–60%), and chromatic aberration (0–100%).
  - **PRO:** QuickStack burst toggle, 16-bit Float FBO toggle, and aspect ratio selector.
  - **CAPTURE:** Real-time double exposure controls and instant Light Trail buffer reset.

### 🎯 Precision Cinema Focus Reticle & Integrated Exposure Slider
- **Tap-to-Focus 3A Metering:** Tap anywhere on the viewfinder to dynamically update Camera2 `CONTROL_AF_REGIONS` and `CONTROL_AE_REGIONS` with auto-focus trigger cycles.
- **Cinema-Grade Reticle:** Minimalist golden corner brackets with center targeting reticle dot.
- **Integrated EV Exposure Slider:** Vertical draggable Sun slider positioned seamlessly next to the focus reticle. Adjust exposure bias (-2.0 EV to +2.0 EV) in real-time with live monospace readout (`+0.7`, `-1.3`).
- **Edge-Aware Positioning:** Automatically adapts to screen edges so sliders and reticles never clip off-screen.

### 📐 Framing & Aspect Ratio Formats
- **XPAN Panoramic (65:24):** True cinematic ultra-wide crop with orange viewfinder corner guides.
- **Square (1:1):** Classic medium format square framing with translucent letterboxing.
- **Standard (4:3):** Full sensor resolution capture.

---

## 🏛️ Architectural Principles

1. **Zero CPU pixel work in the preview loop:** Frames stream directly from Camera2 `Surface(SurfaceTexture)` &rarr; `GL_TEXTURE_EXTERNAL_OES` &rarr; GPU multi-pass shaders &rarr; Screen FBO.
2. **Direct Camera2 Architecture:** Direct HAL stream management with zero buffer starvation, atomic telemetry collection (ISO, Shutter speed), and single-shot high-res captures.
3. **Spec-Compliant FBO Discipline:** Intermediate floating-point processing (`RGBA16F`) for HDR tone grading, with strict normalized fixed-point (`RGBA8`) targets for `glReadPixels` to prevent vendor GPU driver faults.
4. **Zero-ALU Frame History:** Leverages `glBlitFramebuffer` on Qualcomm Adreno hardware to maintain frame history buffers with minimal overhead.
5. **Scoped Storage Integration:** Photos saved seamlessly to `Pictures/AuroraCam` via MediaStore API with complete EXIF and telemetry metadata.

---

## 🛠️ Building and Running

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

## 📦 Automated Releases via GitHub Actions

Pushing a tag formatted as `v*` (e.g. `v0.3.0`) automatically triggers GitHub Actions CI/CD to build the release APK and attach it to GitHub Releases.

---

## 📄 License

This project is licensed under the Apache License 2.0 - see the [LICENSE](LICENSE) file for details.
