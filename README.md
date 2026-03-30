# Android-latest-Tools
Android-letest-Boss ek experimental Android application hai jo mobile media consumption ko Spatial Computing (Apple Vision Pro Style) mein convert karta hai. Ye app standard Android views ko LibGDX 3D Engine aur MediaPipe Gesture Tracking ke saath merge karke ek "Screen-less" control experience deta hai


# 🌀 JDK-HandLookingDeep (Spatial Media Engine)
### Android ka Future: 3D Spatial Audio/Video Player with Hand-Gesture Control

[![Build Status](https://img.shields.io/badge/Build-GitHub--Actions-blue?style=for-the-badge&logo=github-actions)](https://github.com/your-username/repo-name/actions)
[![Platform](https://img.shields.io/badge/Platform-Android--8.0+-green?style=for-the-badge&logo=android)](https://developer.android.com)
[![Engine](https://img.shields.io/badge/Engine-LibGDX--3D-red?style=for-the-badge&logo=libgdx)](https://libgdx.com)
[![AI](https://img.shields.io/badge/AI-MediaPipe--Gestures-teal?style=for-the-badge&logo=google)](https://mediapipe.dev)

---

## 🌟 The Vision
**JDK-HandLookingDeep** sirf ek music player nahi hai, ye ek **Spatial Computing** experience hai. Apple Vision Pro ke interface se inspired, ye app aapke normal phone screen ko ek immersive 3D dashboard mein badal deta hai jahan aap bina screen ko touch kiye, sirf hathon ke isharon (Gestures) se music aur videos control kar sakte hain.

---

## 📸 Interface Preview (Conceptual)

| 🎵 3D Music Mode | 🎬 Video Cinema Mode | ✋ Gesture Control |
| :---: | :---: | :---: |
| ![Music](https://raw.githubusercontent.com/your-username/repo-name/main/screenshots/music_3d.png) | ![Video](https://raw.githubusercontent.com/your-username/repo-name/main/screenshots/video_3d.png) | ![Gestures](https://raw.githubusercontent.com/your-username/repo-name/main/screenshots/hand_gestures.gif) |
| *Floating 3D Vinyl & Glass Icons* | *Curved Theater Screen Experience* | *Real-time MediaPipe Tracking* |

---

## 🔥 Key Features

* **💎 Glassmorphic 3D UI:** Saare icons (Play, Pause, Volume) pure **Math-based 3D meshes** hain jo 360° rotate hote hain.
* **🖐️ Hand-Gesture Logic:** * **Thumbs Up (👍):** Play Media.
    * **Thumbs Down (👎):** Pause Media.
    * **Hand Snap (Right/Left):** Next/Previous Track.
* **🎼 Immersive Audio:** Ek realistic spinning 3D vinyl record jo music ke saath interact karta hai.
* **📽️ Spatial Video:** Videos ko ek curved floating mesh par dekhiye jo ambient lighting ke saath adjust hota hai.
* **📁 Smart Media Scanner:** Automatically scans `.mp3` and `.mp4` from storage with metadata syncing using **Gson**.

---

## 🛠️ Technical Stack

* **Language:** Java / Kotlin
* **3D Engine:** [LibGDX](https://libgdx.com/) (OpenGL ES 3.0)
* **AI/ML:** [Google MediaPipe](https://google.github.io/mediapipe/) for Hand Landmark Detection.
* **Physics:** Box2D for haptic 3D button collisions.
* **Media:** ExoPlayer for high-performance playback.
* **CI/CD:** GitHub Actions for automated APK builds.

---

## 🚀 Getting Started

### Prerequisites
* Android Studio Ladybug or later.
* Android Device with Camera (API Level 26+).
* Physical Hand (for testing gestures! 😄).

### Installation
1. Clone the repo:
   ```bash
   git clone [https://github.com/your-username/JDK-HandLookingDeep.git](https://github.com/your-username/JDK-HandLookingDeep.git)
