# Lumen

<p align="center">
  <img src="art/logo.png" width="128" height="128" alt="Lumen Logo" />
</p>

<p align="center">
  <strong>Cinema-Grade Tactile Creative Camera for Android</strong><br>
  <em>Live double exposure, real-time procedural 3D LUT color grading, temporal echo, motion-only exposure, and light trail accumulation — rendered entirely on the GPU in the viewfinder before pressing the shutter.</em>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Platform-Android%2010%2B%20(API%2029%2B)-000000?style=for-the-badge&logo=android&logoColor=white" alt="Platform" />
  <img src="https://img.shields.io/badge/Graphics-OpenGL%20ES%203.0-E5A00D?style=for-the-badge&logo=opengl&logoColor=black" alt="OpenGL ES 3.0" />
  <img src="https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4?style=for-the-badge&logo=jetpackcompose&logoColor=white" alt="Jetpack Compose" />
  <img src="https://img.shields.io/badge/Camera-Camera2%20HAL-34A853?style=for-the-badge&logo=googlecamera&logoColor=white" alt="Camera2 HAL" />
  <img src="https://img.shields.io/badge/License-Apache%202.0-blue?style=for-the-badge" alt="License" />
</p>

---

## 📖 Table of Contents

1. [Overview & Design Philosophy](#-overview--design-philosophy)
2. [Creative Computational Modes](#-creative-computational-modes)
   - [Standard & QuickStack Burst](#1-standard--quickstack-burst-mode)
   - [Live GPU Double Exposure](#2-live-gpu-double-exposure-mode)
   - [Temporal Echo (Ghost Trails)](#3-temporal-echo-ghost-trails-mode)
   - [Motion-Only Exposure](#4-motion-only-exposure-mode)
   - [Light Trails Accumulation](#5-light-trails-accumulation-mode)
3. [Signature 3D LUT Film Looks](#-signature-3d-lut-film-looks)
   - [Hasselblad Natural Color Solution](#1-hasselblad-natural-color-solution-hasselblad)
   - [Leica M Character](#2-leica-m-character-leica-m)
   - [Fujifilm Classic Chrome](#3-fujifilm-classic-chrome-classic-chrome)
   - [Kodak Portra 400](#4-kodak-portra-400-portra-400)
   - [Aurora Golden Warm](#5-aurora-golden-warm-golden-warm)
   - [Kodachrome Vintage Chrome](#6-kodachrome-vintage-chrome-kodachrome)
   - [Tri-X 400 Monochrome](#7-tri-x-400-monochrome-tri-x-mono)
   - [Custom 3D LUT Import (.CUBE)](#8-custom-3d-lut-import-cube)
4. [Look Uniforms & Analog Effects Engine](#-look-uniforms--analog-effects-engine)
5. [Pro Photographic Controls](#-pro-photographic-controls)
   - [Focus Control (AF / MF / Peaking)](#focus-control-af--mf--peaking)
   - [Manual Shutter Speed & Exposure Bias](#manual-shutter-speed--exposure-bias)
   - [Capture Pipeline (RAW / HDR / 16-Bit FBO)](#capture-pipeline-raw--hdr--16-bit-fbo)
   - [Framing & Cinematic Aspect Ratios](#framing--cinematic-aspect-ratios)
   - [Flash & Illumination](#flash--illumination)
6. [Tactile Control Surface & Viewfinder HUD](#-tactile-control-surface--viewfinder-hud)
7. [Step-by-Step Usage Guides](#-step-by-step-usage-guides)
8. [Rendering Pipeline & Architecture](#-rendering-pipeline--architecture)
9. [Building & Installation](#-building--installation)
10. [License](#-license)

---

## 👁️ Overview & Design Philosophy

**Lumen** transforms your Android device into a physical-feeling, high-performance medium format camera. Traditional mobile camera apps treat color grading, film simulation, and long exposures as post-processing filters applied *after* the picture is taken. 

Lumen processes live camera streams through a multi-pass **OpenGL ES 3.0** shader pipeline in real-time ($60\text{ FPS}$). You see the exact exposure, film grain, analog halation, 3D LUT tone curve, and multi-exposure blends directly on the viewfinder before you trip the shutter.

### Core Architectural Pillars
- **Zero CPU Pixel Work:** Preview frames flow from Camera2 `SurfaceTexture` $\rightarrow$ `GL_TEXTURE_EXTERNAL_OES` $\rightarrow$ GPU Multi-Pass Shaders $\rightarrow$ Screen FBO with zero CPU memory copying.
- **Hardware 3D Textures:** LUTs are parsed into true `GL_TEXTURE_3D` volumes with GPU hardware trilinear interpolation.
- **16-Bit Float HDR Pipeline:** High dynamic range intermediate framebuffers (`GL_RGBA16F`) preserve highlight retention and eliminate gradient banding.
- **Obsidian & Warm Amber Aesthetic:** An industrial design system with deep matte surfaces (`0xFF0F1115`), precision hairline dividers (`0xFF2B313D`), and signature Leica Warm Amber (`0xFFE5A00D`) accents.

---

## 🌀 Creative Computational Modes

Lumen includes 5 GPU-accelerated shooting modes accessible via the top studio menu or parameter drawer:

```
[ STANDARD ]  ·  [ ECHO ]  ·  [ MOTION ]  ·  [ LIGHT TRAILS ]  ·  [ DOUBLE EXP ]
```

---

### 1. Standard & QuickStack Burst Mode
- **What It Does:** The primary cinema photography mode. Renders live full-sensor video through the active 3D LUT color grade, grain generator, and halation passes. When capturing, it can leverage **QuickStack multi-frame computational burst** (6 RAW/YUV frames aligned via spatial phase-correlation and stacked on GPU) to eliminate low-light sensor noise.
- **Adjustable Parameters:**
  - **Look Intensity:** $0\%$ (clean passthrough) to $200\%$ (hyper-grade).
  - **Film Grain:** OFF, ISO 100, ISO 400, ISO 1600, ISO 3200+ (Heavy).
  - **Halation Glow:** OFF, Mild 15%, Warm 30%, Glow 50%, Vintage 75%.
  - **Vignette:** OFF, Soft 10%, Medium 25%, Heavy 45%.
- **How to Use:**
  1. Select `STANDARD` from the top mode bar.
  2. Frame your subject and adjust the look intensity or film grain from the bottom drawer.
  3. Tap the shutter button for instantaneous capture with embedded EXIF and color science metadata.

---

### 2. Live GPU Double Exposure Mode
- **What It Does:** A two-stage multi-exposure engine allowing you to compose and blend two separate photographs directly inside the live viewfinder before capturing.
  - **Stage 1 (`1/2`):** Locks the base exposure into an offscreen GPU Framebuffer (FBO).
  - **Stage 2 (`2/2`):** Renders the live camera preview in real time blended over the Stage 1 base layer using hardware shader blend math.
- **GPU Blend Modes:**
  - `SCREEN`: Multiplies the inverse of both layers—ideal for ethereal silhouettes and bright overlays.
  - `OVERLAY`: Combines multiply and screen modes, boosting midtone contrast.
  - `LIGHTEN`: Retains the brightest pixels between both layers.
  - `ADD`: Direct additive exposure, replicating physical film multi-exposure.
  - `MULTIPLY`: Dark areas mask the underlying image, perfect for silhouette cutouts.
  - `NORMAL`: Smooth alpha transparency blending.
- **Adjustable Parameters:**
  - **Layer 2 Opacity:** $0\%$ to $100\%$ ($25\%$, $50\%$, $75\%$, $100\%$ preset chips).
  - **Horizontal Flip (`FLIP X`):** Mirrors the incoming live camera layer horizontally for surreal symmetrical portraits.
- **How to Use:**
  1. Switch to `DOUBLE EXP` mode. The HUD displays `STAGE: 1/2 (READY)`.
  2. Frame your first subject (e.g. a dark silhouette against a bright sky) and tap the shutter.
  3. The base image freezes in the background, and the HUD updates to `STAGE: 2/2 (LIVE BLEND)`.
  4. Frame your second subject (e.g. flowers, neon signs, textures) and watch the live blend in the viewfinder.
  5. Select your preferred blend mode (`SCREEN`, `LIGHTEN`, etc.) and adjust opacity.
  6. Tap the shutter again to finalize and save the high-resolution composite to your gallery. Tap `CLEAR` at any time to retake Stage 1.

---

### 3. Temporal Echo (Ghost Trails) Mode
- **What It Does:** Emulates vintage multi-image motion delay and chronophotography. Maintains a 3-frame GPU history ring buffer (`glBlitFramebuffer`) that blends previous moments into the current frame with exponential decay.
- **Visual Aesthetic:** Ethereal, dreamy ghosting trails behind any moving subject while keeping static backgrounds sharp.
- **Adjustable Parameters:**
  - **Echo Decay Rate:**
    - `SHORT` ($0.50$): Crisp, tight motion stutter.
    - `NORMAL` ($0.75$): Balanced cinematic motion smear.
    - `DREAMY` ($0.90$): Long, fluid phantom ghosting.
    - `ENDLESS` ($0.98$): Deep atmospheric echo trails.
- **How to Use:**
  1. Select `ECHO` mode.
  2. Open the parameter panel and select your desired decay rate.
  3. Frame dynamic subjects—dancers, moving vehicles, sports, or sweeping camera pans.
  4. Press the shutter to capture the accumulated motion echo.

---

### 4. Motion-Only Exposure Mode
- **What It Does:** A GPU frame-differencing pass that subtracts consecutive frames to isolate moving subjects from stationary environments.
- **Visual Aesthetic:** Static architecture, streets, and backgrounds fade into deep obsidian black, while walking pedestrians, flowing water, or moving hands appear as glowing luminous vectors.
- **Adjustable Parameters:**
  - **Motion Sensitivity Threshold:**
    - `HIGH SENS` ($0.03$): Detects subtle movements and micro-vibrations.
    - `BALANCED` ($0.08$): Standard motion isolation.
    - `SUBTLE` ($0.15$): Captures only distinct, deliberate movements.
    - `GHOST ONLY` ($0.25$): High-velocity movements only.
- **How to Use:**
  1. Select `MOTION` mode.
  2. Hold the camera steady (or mount on a tripod) aimed at a scene with movement (e.g., busy crosswalk).
  3. Watch the background vanish into black while motion trails light up.
  4. Tap the shutter to capture pure movement.

---

### 5. Light Trails Accumulation Mode
- **What It Does:** Simulates long-exposure light painting in the live viewfinder using a hardware Ping-Pong accumulation FBO. It tracks specular highlights across frames and accumulates their max luminescence without overexposing the surrounding scene.
- **Visual Aesthetic:** Silky highway traffic light trails, sparkler writing, star trails, and light stick drawings.
- **Adjustable Parameters:**
  - **Trail Accumulation Decay:**
    - `FAST` ($0.80$): Short glowing light streaks.
    - `SMOOTH` ($0.90$): Classic long-exposure light ribbons.
    - `LONG` ($0.95$): Extended light painting trails.
    - `INFINITE` ($0.99$): Permanent accumulation until reset.
  - **Reset Button (`RESET`):** Instantly clears accumulated light buffers to start a fresh trail.
- **How to Use:**
  1. Select `LIGHT TRAILS` mode in a low-light or night environment.
  2. Point the camera at oncoming traffic, city nightscapes, or have someone move a flashlight/sparkler.
  3. Watch the light trails paint in real-time directly on the screen.
  4. When the composition is complete, press the shutter to freeze and save the image.

---

## 🎨 Signature 3D LUT Film Looks

Lumen incorporates 7 built-in procedural film look generators engineered with strict color science formulas, alongside support for external `.cube` files.

| Dial ID | Name | Subtitle | Character & Color Science |
| :--- | :--- | :--- | :--- |
| **`HAS`** | **HASSELBLAD** | Medium Format Smooth | HNCS natural skin fidelity, rational asymptotic highlight shoulder ($x > 0.55$), neutral shadow floor. |
| **`LEI`** | **LEICA M** | High Microcontrast | Hermite S-curve contrast, warm-amber midtone glow ($L=0.45$), deep shadow punch. |
| **`POR`** | **PORTRA 400** | Warm Pastel Skin Tone | Lifted matte blacks ($+0.035$), golden-peach skin tones, gentle ivory highlight roll-off. |
| **`CHR`** | **CLASSIC CHROME** | Documentary Muted | Deep shadow toe ($x^{1.22}$), muted sky cyan/teal shift, earthy warm reds, low chroma. |
| **`WRM`** | **GOLDEN WARM** | Sunset Amber Halation | Golden hour warmth, soft shadow lift, analog spectral glow. |
| **`KOD`** | **KODACHROME** | Deep Rich Saturated | High-contrast vintage slide film, bold saturated primaries, punchy reds and blues. |
| **`MON`** | **TRI-X MONO** | Achromatic Silver Halide | Classic $Y=0.299R+0.587G+0.114B$ luminance, deep grain response, rich tonal gradation. |
| **`+`** | **+ .CUBE** | Custom 3D LUT | Import industry-standard $17^3$, $33^3$, or $64^3$ `.cube` LUT files from storage. |

---

### 1. Hasselblad Natural Color Solution (`HASSELBLAD`)
- **Aesthetic:** Medium format clarity, true-to-life color accuracy, and graceful highlight rolloff.
- **Color Science:** Replicates Hasselblad's HNCS profile. Applies a rational asymptotic curve above luminance $0.55$ to prevent highlight clipping, maintains a linear shadow floor without artificial black crushing, and utilizes perceptual Oklab color space adjustments to preserve skin tone purity.
- **Default Factory Tuning:** Intensity: $1.0$, Halation: $0.00$, Grain: $0.010$, Vignette: $0.08$.

### 2. Leica M Character (`LEICA M`)
- **Aesthetic:** Dramatic street photography character with rich micro-contrast and a distinctive warm glow.
- **Color Science:** Uses a 5th-order Hermite polynomial S-curve for punchy micro-contrast. Injects a signature warm-amber bias into the midtones ($L = 0.45$) while compressing deep shadows for deep, rich blacks.
- **Default Factory Tuning:** Intensity: $1.0$, Halation: $0.28$, Grain: $0.030$, Vignette: $0.18$, CA: $0.04$.

### 3. Fujifilm Classic Chrome (`CLASSIC CHROME`)
- **Aesthetic:** Gritty, atmospheric documentary and photojournalism style.
- **Color Science:** Implements a steep shadow toe ($x^{1.22}$) for deep, dark shadows, gently desaturates greens and yellows, shifts sky blues toward muted cyan/teal, and renders earthy warm reds with low saturation.
- **Default Factory Tuning:** Intensity: $1.0$, Halation: $0.12$, Grain: $0.035$, Vignette: $0.14$.

### 4. Kodak Portra 400 (`PORTRA 400`)
- **Aesthetic:** The gold standard in analog portrait film—gentle contrast and flattering pastel skin tones.
- **Color Science:** Lifts deep blacks to a soft matte level ($+0.035$), rolls off highlights into soft ivory tones, and applies gentle saturation curves to produce warm golden-peach tones in human skin.
- **Default Factory Tuning:** Intensity: $1.0$, Halation: $0.22$, Grain: $0.040$, Vignette: $0.12$, CA: $0.02$.

### 5. Aurora Golden Warm (`GOLDEN WARM`)
- **Aesthetic:** Radiant sunset lighting, romantic golden hour tones, and vintage atmospheric glow.
- **Color Science:** Infuses amber and golden wavelengths into midtones and highlights with a gentle shadow lift and soft contrast curve.
- **Default Factory Tuning:** Intensity: $1.0$, Halation: $0.25$, Grain: $0.030$, Vignette: $0.12$.

### 6. Kodachrome Vintage Chrome (`KODACHROME`)
- **Aesthetic:** Iconic mid-century color reversal slide film with bold, saturated primaries.
- **Color Science:** Emphasizes rich crimson reds, vibrant sky blues, and warm golden highlights with high global contrast and crisp tonal separation.
- **Default Factory Tuning:** Intensity: $1.0$, Halation: $0.10$, Grain: $0.025$, Vignette: $0.22$, CA: $0.03$.

### 7. Tri-X 400 Monochrome (`TRI-X MONO`)
- **Aesthetic:** Classic silver halide black-and-white film with punchy contrast and organic grain.
- **Color Science:** Uses precise perceptual luminance weighting ($Y = 0.299R + 0.587G + 0.114B$), pushes deep shadow density, retains clean white highlights, and enhances procedural grain visibility.
- **Default Factory Tuning:** Intensity: $1.0$, Halation: $0.16$, Grain: $0.050$, Vignette: $0.24$.

### 8. Custom 3D LUT Import (`+ .CUBE`)
- **How It Works:** Import any `.cube` 3D lookup table from DaVinci Resolve, Adobe Premiere, Final Cut Pro, or Lightroom.
- **Capabilities:** Supports $17\times17\times17$, $33\times33\times33$, and $64\times64\times64$ 3D LUT grids. Files are validated, sanitized, parsed off the main thread, and cached into app storage.
- **Memory Safety:** Enforces strict $10\text{ MB}$ file size limits and canonical path resolution to protect system resources.

---

## 🎛️ Look Uniforms & Analog Effects Engine

Each film look includes companion shader uniforms that can be customized in real-time via tactile steppers and sliders:

```
[ INTENSITY ]  ·  [ GRAIN ]  ·  [ HALATION ]  ·  [ VIGNETTE ]  ·  [ ALL ]
```

- **Look Intensity ($0.0\text{ to }2.0$):** Scales the 3D LUT color grading effect against the neutral camera feed.
- **Procedural Film Grain ($0.0\text{ to }0.15$):** Emulates organic silver halide crystals using a high-speed GPU hash algorithm, varying dynamically per frame. Includes quick presets for `OFF`, `ISO 100`, `ISO 400`, `ISO 1600`, and `ISO 3200+`.
- **Halation Glow ($0.0\text{ to }0.80$):** Simulates internal optical light scatter and red/amber light bleed around bright edges where film emulsion layers interact with the film base.
- **Optical Vignette ($0.0\text{ to }0.60$):** Generates smooth radial lens falloff darkening towards the corners of the frame.
- **Chromatic Aberration:** Replicates physical lens dispersion by slightly offsetting color channels near the frame boundaries.
- **One-Tap Reset (`RESET TO FACTORY DEFAULTS`):** Instantly restores the selected look's calibrated default parameters.

---

## 🎚️ Pro Photographic Controls

Lumen gives you granular hardware control over the camera sensor and ISP:

---

### Focus Control (AF / MF / Peaking)
- **Auto Focus (`AF`):** 0ms tap-to-focus updating `CONTROL_AF_REGIONS` with a corner-chamfered tracking reticle.
- **Manual Focus (`MF`):** Precision diopter slider ($0.0\text{D}$ to $10.0\text{D}$) with quick snap buttons for:
  - `INF ∞` ($0.0\text{D}$) · `3.0m` ($0.33\text{D}$) · `1.5m` ($0.67\text{D}$) · `0.5m` ($2.0\text{D}$) · `MACRO` ($8.0\text{D}$).
- **Focus Peaking (`PEAKING`):** High-pass Sobel edge-detection shader overlaying crisp **Focus Green** highlights over in-focus planes in real time.
- **AE/AF Lock (`[AE/AF-L]`):** Reticle button to pin both focus distance and auto-exposure simultaneously.

---

### Manual Shutter Speed & Exposure Bias
- **Shutter Speed Tray:** Tap the shutter indicator on the top HUD to reveal the manual exposure tray:
  ```
  AUTO · 1/4000s · 1/2000s · 1/1000s · 1/500s · 1/250s · 1/125s · 1/60s · 1/30s · 1/15s · 1/8s · 1/4s · 1/2s · 1s · 2s
  ```
- **Solar Vernier EV Slider:** Drag vertically directly next to the focus reticle to adjust exposure compensation in $1/3$-stop increments from $-2.0\text{ EV}$ to $+2.0\text{ EV}$.
- **Live Telemetry Readout:** Dynamic display of live FPS, active shutter speed, and EV offset.

---

### Capture Pipeline (RAW / HDR / 16-Bit FBO)
- **RAW DNG (`RAW DNG`):** Toggle uncompressed 16-bit DNG sensor output alongside processed images.
- **HDR Computational Stacking (`HDR STACK`):** Multi-frame exposure fusion for extreme dynamic range scenes.
- **16-Bit Float HDR FBO (`16-BIT FBO`):** Enables `GL_RGBA16F` wide-gamut texture allocation for intermediate rendering passes to prevent tone grading quantization artifacts.

---

### Framing & Cinematic Aspect Ratios
Toggle framing modes with custom letterbox overlays:
- **XPAN (`65:24`):** True panoramic ratio inspired by the Hasselblad XPan, complete with orange framing brackets.
- **Square (`1:1`):** Classic medium-format square framing.
- **Standard (`4:3`):** Full sensor capture resolution.

---

### Flash & Illumination
Cycle through flash states directly from the top HUD or pro panel:
- `[OFF]` · `[AUTO]` · `[ON]` · `[TORCH]` (continuous video illumination).

---

## 📐 Tactile Control Surface & Viewfinder HUD

```
+-----------------------------------------------------------+
|  [ 60 FPS · 1/125s (AUTO) · EV +0.0 ]   [ FLASH ] [ STD·HAS ] | <- Top Minimal HUD
|                                                           |
|                        ┌───┐                              |
|                        │ · │ [AE/AF-L]                    | <- 0ms Focus Reticle
|                        └───┘                              |
|                                                           |
|             [ XPAN ]    [ 1:1 ]    [ 4:3 ]                | <- Ratio Selector
|                                                           |
|      (HAS)    (LEI)    [POR]    (CHR)    (WRM)    (+)     | <- Signature Looks Dial
|                                                           |
|   [ GALLERY ]              [ (O) ]          [ MODES & FX ]| <- Mechanical Shutter Dock
+-----------------------------------------------------------+
```

1. **Top Minimal HUD:** Semi-transparent status bar with live telemetry and quick selectors for shutter speed, EV bias, flash, and studio menu.
2. **Interactive Viewfinder:** Tap anywhere for instant focus reticle placement, AE/AF locking, and vertical exposure dragging.
3. **Signature Looks Snapping Dial:** Tactile horizontal dial with haptic feedback to cycle film profiles without opening menus.
4. **Mechanical Shutter Dock:** 
   - **80dp Main Shutter:** Features internal disc compression animation and tactile feedback.
   - **48dp Gallery Squircle:** Displays the most recent capture thumbnail; tap to open the full-screen photo viewer.
   - **Modes & FX Trigger:** Opens the floating creative parameters sheet.

---

## 🚀 Step-by-Step Usage Guides

### Quick Start: Taking Your First Shot
1. Launch Lumen. The camera opens in `STANDARD` mode with the `GOLDEN WARM` look active.
2. Swipe the **Looks Dial** above the shutter button to switch between `HASSELBLAD`, `LEICA M`, `PORTRA 400`, `CLASSIC CHROME`, or `TRI-X MONO`.
3. Tap on your subject to focus. Drag vertically on the reticle to fine-tune exposure.
4. Tap the **Shutter Button** to capture.

---

### How to Create a Double Exposure
1. Tap `[MODES & EFFECTS]` in the bottom dock and select `DOUBLE EXP`.
2. Frame your primary subject (e.g. a portrait in silhouette) and press the shutter.
3. Your base frame is frozen in the background. Frame your secondary texture (e.g. city lights or foliage).
4. Tap `EFFECT CONTROLS` in the adjustment panel:
   - Try different **Blend Modes** (`SCREEN` for soft light, `LIGHTEN` for highlights, `MULTIPLY` for silhouette cutouts).
   - Adjust **Blend Opacity** slider.
   - Toggle `FLIP X` if you want to mirror the live camera feed.
5. Tap the shutter button again to record the composite image to your gallery.

---

### How to Shoot Light Trails at Night
1. Switch to `LIGHT TRAILS` mode.
2. Mount your device on a tripod or hold it firmly against a stable surface.
3. Open `EFFECT CONTROLS` and choose your decay rate (`SMOOTH 0.90` or `LONG 0.95`).
4. Watch cars or light sources paint trails on screen.
5. If the scene gets too cluttered, tap `RESET` to clear the buffer.
6. Tap the shutter to capture the accumulated light painting.

---

### How to Import Custom 3D LUTs (.cube)
1. Transfer any `.cube` LUT file (e.g. from DaVinci Resolve or online presets) to your Android device storage.
2. In Lumen, tap the `+ .CUBE` option on the looks dial or in the studio menu.
3. Android's file picker opens. Select your `.cube` file.
4. Lumen parses the file, builds a hardware `GL_TEXTURE_3D` texture, and applies the color grade instantly to your live viewfinder.
5. The imported LUT is cached in the app and appears in your looks dial for quick access.

---

## 🏛️ Rendering Pipeline & Architecture

```mermaid
flowchart TD
    subgraph Hardware_Input [Camera2 HAL Input]
        Sensor[Image Sensor] -->|YUV_420_888| CamStream[CameraCaptureSession]
        CamStream --> SurfaceTex[SurfaceTexture OES]
    end

    subgraph GPU_Core [OpenGL ES 3.0 Multi-Pass Shader Engine]
        SurfaceTex -->|GL_TEXTURE_EXTERNAL_OES| BasePass[Base Pass: Texture Unpack & Transform]
        BasePass --> FBO_HDR[16-Bit Float HDR FBO (GL_RGBA16F)]
        
        FBO_HDR --> ModeRouter{Active Mode Routing}
        ModeRouter -->|Standard / QuickStack| LUTPass[3D LUT Color Grade (GL_TEXTURE_3D)]
        ModeRouter -->|Double Exposure| BlendPass[Double Exposure FBO Blender]
        ModeRouter -->|Temporal Echo| EchoPass[History Ring Buffer (glBlitFramebuffer)]
        ModeRouter -->|Motion Only| MotionPass[Luminance Differencing Pass]
        ModeRouter -->|Light Trails| AccumPass[Ping-Pong Accumulation FBO]
        
        BlendPass --> LUTPass
        EchoPass --> LUTPass
        MotionPass --> LUTPass
        AccumPass --> LUTPass
        
        LUTPass --> BloomPass[Halation & Optical Glow Pass]
        BloomPass --> GrainPass[Procedural Film Grain Pass]
        GrainPass --> PeakingPass[Sobel Focus Peaking Pass]
    end

    subgraph Render_Outputs [Outputs]
        PeakingPass -->|Viewport Blit| Display[GLSurfaceView / Viewfinder]
        GrainPass -->|glReadPixels RGBA8| CaptureEngine[CaptureSaver / MediaStore API]
        CamStream -->|ImageReader RAW_SENSOR| RawEngine[DNG Creator / Storage]
    end
```

---

## 🛠️ Building & Installation

### System Requirements
- **Android Studio:** Ladybug (2024.2+) or newer
- **JDK:** Version 17
- **Android SDK:** Target API 35, Minimum API 29 (Android 10+)
- **GPU Hardware:** OpenGL ES 3.0 compatible GPU (Adreno, Mali, PowerVR)

### Building via Command Line
```bash
# Clone repository
git clone https://github.com/varun2507027108-oss/LUMEN-cam.git
cd LUMEN-cam

# Build Debug APK
./gradlew assembleDebug

# Build Release APK (with R8 optimization)
./gradlew assembleRelease

# Run Unit Tests
./gradlew test

# Install to connected device via ADB
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

---

## 📄 License

```
Copyright 2026 Lumen Camera Contributors

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```