# AuroraCam

> Live double exposure, temporal effects, and signature film-style color grading — rendered entirely on the GPU in the viewfinder before pressing the shutter.

AuroraCam is an open-source real-time creative camera application for Android built with Jetpack Compose, CameraX, and OpenGL ES 3.0.

---

## Device & Hardware Target

- **Primary Target Device:** OnePlus Nord CE 3 Lite (CPH2467)
- **OS / Platform:** Android 15 (API 35), minSdk 29
- **SoC / GPU:** Snapdragon 695 5G / Adreno 619 (OpenGL ES 3.2 supported)
- **Display:** 120Hz LCD
- **Camera2 Hardware Level:** `LEVEL_3`
- **Supported Public Capture Resolutions:**
  - 4:3 &rarr; `3200x2400` (Max)
  - 1:1 &rarr; `2448x2448`
  - 16:9 &rarr; `3840x2160`
  - 20:9 &rarr; `4000x1800`

---

## Architectural Principles

1. **Zero CPU pixel work in the preview loop:** Frames stream directly from CameraX `Surface(SurfaceTexture)` &rarr; `GL_TEXTURE_EXTERNAL_OES` &rarr; GL pass shaders &rarr; Screen FBO.
2. **Proper coordinate mapping:** Always uses `SurfaceTexture.getTransformMatrix()` and shader aspect-ratio correction matrices to eliminate distortion, stretch, and flip.
3. **Pure GPU pipeline:** One GL thread owns the context and surface texture. CPU computation is restricted to single-shot still saving upon shutter press.
4. **Clean scoped storage:** Photos saved to `Pictures/AuroraCam` via MediaStore `IS_PENDING` workflow with `RELATIVE_PATH`.

---

## Building and Running

### Requirements
- Android Studio Ladybug (2024.2+) or Hedgehog+
- JDK 17
- Android SDK 35 (minSdk 29)

### CLI Build
```bash
# Build Debug APK
./gradlew assembleDebug

# Install to connected device via ADB
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

---

## Automated Releases via GitHub Actions

Pushing a tag formatted as `v*` (e.g. `v0.1.0`) automatically builds the debug APK and attaches it to the GitHub Release draft.

---

## License

This project is licensed under the Apache License 2.0 - see the [LICENSE](LICENSE) file for details.
