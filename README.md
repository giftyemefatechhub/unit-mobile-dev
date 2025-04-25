# Smart-Home Control App ( _unit-mobile-dev_ )

Welcome to the **Smart-Home Control App**, an Android application crafted in **Kotlin + Jetpack Compose** by the **Unit Mobile Devs** team. The project turns your phone into a universal dashboard for smart-home devices running on our Flask/Docker back-end.

---

## ✨ Key Features

| Capability | Description |
|------------|-------------|
| **Real-time control & telemetry** | Bi-directional Socket.IO updates ensure every light, fan or sensor reflects its live state instantly – no manual refreshes. |
| **Voice commands** | Speak naturally (“bedroom lamp off”) – speech-to-text + fuzzy matching picks the right device and toggles it. |
| **Dynamic dashboard** | Add/remove any discovered device on-the-fly; cards update status and offer one-tap toggles. |
| **Light ↔ Dark theme** | Quick theme-switch button lets users adapt to ambient lighting or preference. |
| **JWT authentication** | Secure login/registration backed by `accessToken` / `refreshToken` flow; tokens are injected into every protected request. |
| **Offline resilience** | Requests queue and retry automatically when connectivity returns (OkHttp + coroutine wrappers). |
| **Modular architecture** | Clean separation of UI (Compose), networking (OkHttp), and sockets (Socket.IO); easily swap the back-end. |
| **Accessibility ready** | Content descriptions for icons, scalable typography, and high-contrast palettes support TalkBack and low-vision users. |

---

## 🏗 Tech Stack

| Layer | Libraries / Tools |
|-------|-------------------|
| **UI** | Jetpack Compose • Material 3 |
| **Language** | Kotlin (JVM 17) |
| **Networking** | OkHttp 5 • Kotlin Coroutines |
| **Real-time** | Socket.IO (client) |
| **Speech** | Android Speech Recognizer API |
| **Back-end** | Flask 2 • SQLAlchemy • JWT Auth • Docker |
| **CI / QA** | Gradle 8 • detekt • ktlint |

---

## 🚀 Quick Start

``bash
# 1 – clone & open in Android Studio
git clone https://github.com/Unit-Mobile-Devs/unit-mobile-dev.git

# 2 – run the back-end (Docker must be running)
docker pull ibmoh/client-server:latest
docker run -d -p 5001:5001 ibmoh/client-server
# → API available at http://localhost:5001

# 3 – build & deploy the app
./gradlew installDebug   # or use the Studio “Run” button

---

## 🔑 Authentication Flow

| Step | Endpoint & Method | What Happens | Important Headers |
|------|------------------|--------------|-------------------|
| **Register** | `POST /user/register` | Creates the user and returns **201 Created** | — |
| **Login** | `POST /user/login` | Returns a short-lived **accessToken** (≈ 1 min) in the JSON body **and** a long-lived **refreshToken** as an HTTP-only cookie | — |
| **Authenticated request** | *any protected route* | Client sends `Authorization: Bearer <accessToken>` | `Authorization` |
| **Refresh token** | `POST /user/token` | Swap an expired accessToken for a new one (refreshToken is sent automatically via cookie when you include `credentials: "include"` or `withCredentials = true`) | `Authorization` (cookie) |
| **Logout** | `POST /user/logout` *(optional)* | Server invalidates the refreshToken; the accessToken just expires naturally | — |

---
