# Keri Image Tool

A lightweight Android image compressor focused on getting as close as practical to a user-entered target size while preserving the highest possible JPEG quality.

## Features
- Choose an image using Android's document picker.
- Enter a target size in KB or MB.
- Quality-first compression with iterative resizing only when required.
- Save to Pictures/Keri Image Tool and share through Android's share sheet.
- Offline processing; original image is not modified.
- Android 10+ support, targeting Android 16 (API 36).

**Developed by Shartendu**

## Build
Run `gradle assembleDebug` with JDK 17, Android SDK Platform 36 and Gradle 8.11.1 or newer compatible with Android Gradle Plugin 8.10.1.
