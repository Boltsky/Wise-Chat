# Wise Chat — Android AI Chat Assistant

[![Android CI](https://img.shields.io/badge/Platform-Android_14--15_(API_26--35)-green.svg)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.2.10-blue.svg)](https://kotlinlang.org)
[![Compose](https://img.shields.io/badge/Jetpack_Compose-Material_3-purple.svg)](https://developer.android.com/jetpack/compose)
[![ML Kit](https://img.shields.io/badge/ML_Kit-Text_Recognition-orange.svg)](https://developers.google.com/ml-kit/vision/text-recognition/android)
[![License](https://img.shields.io/badge/License-Proprietary-red.svg)]()

> **Think smarter. Reply better.**  
> Wise Chat is a native Android communication assistant that analyzes conversations from screenshots and recommends natural, context-aware replies via a floating system overlay.

---

## 🚀 Key Features

### 1. Floating Overlay Assistant (Flagship Mode)
- **Persistent Accessibility:** Operates directly above WhatsApp, Telegram, Instagram, Messenger, dating applications, and email clients using Android's `TYPE_APPLICATION_OVERLAY`.
- **Draggable Bubble with Inertia:** Circular bubble with touch-drag physics, screen-border edge snapping, and live visual state rings (Idle, Analyzing pulse, Success checkmark, Error badge).
- **In-App Compose Panel:** Expandable bottom sheet embedded directly in the overlay window to view extracted messages, pick reply personalities, and preview suggestions without app-switching.
- **Auto-Hide During Capture:** Automatically hides overlay elements 150ms before screen acquisition to prevent the assistant from screenshotting itself.

### 2. Privacy-Preserving Screen Capture & OCR Pipeline
- **User-Authorized MediaProjection:** Single-frame capture session with system consent dialog and foreground service (`FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION`). Immediate session teardown once the frame is captured.
- **On-Device ML Kit OCR:** Analyzes text locally using Google ML Kit Text Recognition. Zero raw image pixels are uploaded to external cloud servers.
- **Intelligent Flow Reconstruction:**
  - Sorts text elements chronologically.
  - Distinguishes **Incoming** vs. **Outgoing** messages using horizontal bounding alignment heuristics (left-anchored vs. right-anchored).
  - Automatically filters out status bar noise (battery %, WiFi, time, "typing...", "Delivered").
  - Identifies candidate target message with one-tap selection and inline text correction.
- **Screenshot Import Fallback:** Full gallery / Photo Picker workflow (`ACTION_GET_CONTENT`) and Android Sharesheet integration (`ACTION_SEND`).

### 3. Multi-Provider AI Engine (Account Sign-In & BYOK)
Wise Chat provides two ways to connect your favorite AI models:
1. **⚡ 1-Tap Account Sign-In / Service Account:** Directly connect your Google Gemini, OpenAI (ChatGPT), or Anthropic Claude account without manual server configuration.
2. **Bring Your Own Key (BYOK):** For power users who want manual API keys, custom server endpoints, or self-hosted models (Groq, Ollama, Together, vLLM).

| Provider | Endpoint & Implementation | Supported Models |
|---|---|---|
| **Google Gemini** | Google Generative Language REST API / Google Account | `gemini-1.5-flash`, `gemini-2.0-flash`, `gemini-1.5-pro` |
| **OpenAI (ChatGPT)** | Official OpenAI Chat Completions / Account Portal | `gpt-4o-mini`, `gpt-4o`, `o1-mini` |
| **Anthropic Claude** | Official Anthropic Messages API / Claude Account | `claude-3-5-sonnet-20241022`, `claude-3-5-haiku-20241022`, `claude-3-opus-20240229` |
| **OpenAI-Compatible** | Third-party / Self-hosted compatible APIs | Groq (`llama-3.3-70b-versatile`), Together AI, Ollama, DeepSeek, vLLM |
| **Custom REST** | User-configured HTTP endpoint with template interpolation | Custom JSON templates using `{{conversation}}`, `{{target_message}}`, `{{reply_style}}` |

- **Hardware Keystore Encryption:** API keys are encrypted on-device using Android Keystore AES-256 GCM (`EncryptedSharedPreferences`). Keys are masked in the UI (`sk-••••••••abcd`) and never written to logs.
- **Connection Testing:** Live ping and latency measurement (ms) with available model discovery.
- **Provider Fallback:** Automatically switches to a backup configured provider if the primary endpoint experiences rate limits or outages.

### 4. 20 Distinct Reply Personalities & Refinement Engine
Every persona has distinct prompt instructions, configurable tone intensity, length constraints, emoji usage, and optional conversational objectives:

1. **Casual:** Relaxed, everyday vernacular, natural flow.
2. **Funny:** Punchy wit, observational humor, light teasing.
3. **Flirty:** Charismatic chemistry, playful banter, subtle or bold interest.
4. **Inquisitive:** Thoughtful curiosity, engaging follow-up questions.
5. **Formal:** Grammatically pristine, conventional courtesy.
6. **Professional:** Executive clarity, workplace-appropriate, action-oriented.
7. **Emotional:** Heartfelt authenticity mirroring conversation depth.
8. **Empathetic:** Compassionate validation, non-judgmental comfort.
9. **Romantic:** Tender affection, genuine intimacy.
10. **Confident:** Direct, self-assured clarity without arrogance.
11. **Witty:** Clever repartee, sharp intellect, subtle irony.
12. **Playful:** High positive energy, fun banter without romantic pressure.
13. **Supportive:** Uplifting encouragement, reliable reassurance.
14. **Apologetic:** Sincere ownership and constructive next steps.
15. **Persuasive:** Compelling logic and mutual benefit framing.
16. **Diplomatic:** Tactful neutrality and de-escalation.
17. **Boundary-setting:** Firm, polite assertiveness and clear limits.
18. **Concise:** Ultra-brief, telegram-style efficiency.
19. **Detailed:** Complete context and structured explanations.
20. **Custom:** User-defined communication persona with custom directives.

**One-Tap Refinement Actions:**
- *Make it shorter*, *Make it longer*, *More natural*, *Add humor*, *More direct*, *Warmer*, *More confident*, *Less intense*, *Add follow-up question*, *Remove emojis*, *Add emojis*.

### 5. Safe Message Handoff (Human-in-the-Loop)
- **Strict User Agency:** Wise Chat **never** sends messages automatically.
- Each generated reply features an instant **Copy to Clipboard** button with haptic/visual confirmation.
- The user retains 100% control over the final text before pressing "Send" in the target application.

---

## 📱 Application Screens

1. **Onboarding Carousel:** 4-step introduction explaining the BYOK model, permissions, and features.
2. **Home Dashboard:** Live floating bubble toggle, active AI provider card, quick screenshot analyzer, and recently used styles.
3. **Conversation Analysis:** Visual chat view with bubble separation, incoming/outgoing badges, target message picker, and OCR editor.
4. **Style Selector:** Categorized grid of all 20 personalities with sliders for intensity, length, emojis, and objectives.
5. **Generated Replies:** 3–6 distinct response cards with Copy and Refine dropdown actions.
6. **AI Provider Management:** BYOK screen with masked key inputs, live latency testing, and custom endpoint templating.
7. **Custom Styles & Presets:** Create and save reusable prompt voices (e.g., "My Dating Tone", "Executive Client Voice").
8. **Privacy & Settings:** Overlay controls, system permission links, data retention toggles, and cryptographic key wiping.
9. **Reply History:** Local Room database storing copied suggestions with keyword search and one-tap re-copying.

---

## 🏗️ Technical Architecture

```
com.wisechat.app/
├── WiseChatApp.kt                   # Application singleton & DI coordinator
├── MainActivity.kt                  # Entry activity & Sharesheet handler
├── capture/
│   ├── ScreenCapturePermissionActivity.kt # Transparent consent bridge
│   └── ScreenCaptureService.kt      # Foreground single-frame capture
├── overlay/
│   ├── OverlayBubbleView.kt         # Draggable floating bubble (WindowManager)
│   ├── OverlayPanelComposable.kt    # In-overlay Compose bottom sheet
│   └── OverlayService.kt            # Overlay lifecycle & capture coordinator
├── engine/
│   ├── ocr/
│   │   ├── MlKitOcrEngine.kt        # On-device Google ML Kit OCR wrapper
│   │   └── ConversationParser.kt    # Spatial bubble clustering & sender heuristic
│   ├── prompt/
│   │   └── PromptBuilder.kt         # Versioned prompt engineering & JSON schemas
│   └── provider/
│       ├── AiProvider.kt            # Provider abstraction interface
│       ├── GeminiProvider.kt        # Google Gemini REST implementation
│       ├── OpenAiProvider.kt        # OpenAI API implementation
│       ├── OpenAiCompatibleProvider.kt # Groq, Together, Ollama, DeepSeek
│       ├── CustomRestProvider.kt    # Custom endpoint with template engine
│       └── ProviderCoordinator.kt   # Provider routing & fallback logic
├── domain/
│   └── model/                       # Domain entities & enums
├── data/
│   ├── local/                       # Room database, DAO & HistoryEntity
│   ├── preferences/                 # Jetpack DataStore Preferences
│   ├── security/                    # Android Keystore EncryptedSharedPreferences
│   └── repository/                  # Repositories for History, Providers & Styles
└── ui/
    ├── navigation/                  # Compose Navigation graph
    ├── theme/                       # Material 3 Design System (Dark & Light)
    └── screens/                     # 9 primary feature screens
```

---

## 🛠️ Build & Installation

### Prerequisites
- **JDK:** Java 21 LTS
- **Android SDK:** Platforms `android-35`, Build-Tools `36.0.0`
- **Gradle:** 9.3.1 (wrapper included)

### Building the Debug APK
```bash
# Run unit tests
./gradlew test

# Assemble debug APK
./gradlew assembleDebug
```

The compiled APK will be located at:
`app/build/outputs/apk/debug/app-debug.apk`

---

## 🔒 Permissions & Security

| Permission | Purpose |
|---|---|
| `SYSTEM_ALERT_WINDOW` | Required to display the floating assistant bubble above third-party messaging apps. |
| `FOREGROUND_SERVICE` | Required to maintain overlay and single-frame capture lifecycle according to Android rules. |
| `FOREGROUND_SERVICE_MEDIA_PROJECTION` | Android 14+ requirement for user-authorized screen frame extraction. |
| `INTERNET` | Communicates with user-configured AI APIs (Gemini, OpenAI, custom endpoints). |
| `POST_NOTIFICATIONS` | Displays required foreground service notification for overlay and capture status. |

---

## 🧪 Automated Testing

Unit test suite covers:
- **ConversationParserTest:** Verifies spatial clustering, chronological sorting, incoming vs. outgoing horizontal detection, noise filtering.
- **PromptBuilderTest:** Verifies prompt construction, untrusted conversation isolation delimiters, and 20 personality directives.
- **ProviderParsingTest:** Verifies custom REST template variable interpolation and Keystore API key masking.

Execute tests via:
```bash
./gradlew test
```
