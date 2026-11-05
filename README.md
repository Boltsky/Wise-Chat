# RizzWise — Android AI Dating & Chat Assistant

[![Android CI](https://img.shields.io/badge/Platform-Android_14--15_(API_26--35)-green.svg)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.2.10-blue.svg)](https://kotlinlang.org)
[![Compose](https://img.shields.io/badge/Jetpack_Compose-Material_3-purple.svg)](https://developer.android.com/jetpack/compose)
[![ML Kit](https://img.shields.io/badge/ML_Kit-Text_Recognition-orange.svg)](https://developers.google.com/ml-kit/vision/text-recognition/android)
[![License](https://img.shields.io/badge/License-Proprietary-red.svg)]()

> **Smart Charisma. Instant Chemistry.**  
> RizzWise is a native Android communication assistant that analyzes conversations from screenshots and recommends natural, context-aware replies, high-charm flirty responses, and unique conversation starters via a floating system overlay.

---

### 📥 Download Prebuilt APKs (Universal Android Compatibility)

Ready-to-install APK packages compatible with all Android devices (phones, tablets, foldables, emulators; ARM64, ARMv7, x86, x86_64):

- ⚡ **[Download from High-Speed Edge CDN (rizzwise-release.apk)](https://awake-zephyr-d9x5.here.now/releases/rizzwise/rizzwise-release.apk)** — *Fast global mirror hosted on Cloudflare edge*
- 🚀 **[Download from GitHub Mirror (rizzwise-release.apk)](./apk/rizzwise-release.apk?raw=true)** — *15.6 MB, release build*
- 🛠️ **[Download Debug APK (rizzwise-debug.apk)](./apk/rizzwise-debug.apk?raw=true)** — *23.0 MB, debug build*

---

## 🚀 Key Features

### 1. In-App Version Control & Multi-Endpoint Auto-Update Engine (NEW ✨)
- **High-Speed Edge CDN Hosting:** Primary update manifest hosted on **[awake-zephyr-d9x5.here.now](https://awake-zephyr-d9x5.here.now/api/updates/rizzwise/latest.json)** with zero rate limits, fast download speeds, and resumable transfers.
- **Multi-Tiered Failover:** Automatically syncs with `awake-zephyr-d9x5.here.now`, falls back to GitHub raw `version.json`, and as a last line of defense queries the GitHub Commits API.
- **Background & On-Demand Checking:** Automatic checks on launch or user-configured schedules, plus a manual "Check for Updates" button in Settings.
- **In-App Background APK Downloader:** Integrated Android `DownloadManager` pulls updates directly into downloads and launches the native package installer via secure `FileProvider`.

### 2. Conversation Starters & Unique Pick-Up Lines
- **Strict Anti-Cliché Mandate:** Completely forbids tired clichés (*"Did it hurt when you fell from heaven?"*, *"Are you a magician?"*, *"Are you from Tennessee?"*, generic *"Hey/What's up"*).
- **The "Aww" & Smile Mandate:** Crafted specifically to melt hearts, elicit a soft smile or blush, and feel disarmingly genuine and charming.
- **Simple, Understandable English:** Natural, modern conversational phrasing without archaic or pretentious words.
- **5 Tailored Categories & Curated Vault (30+ Lines):**
  - **Sweet & 'Aww' (🥺):** Sincere warmth, adorable vulnerability, and genuine heart-melters.
  - **Playful Tease (😏):** Witty push-pull challenges and playful tension.
  - **Smooth Rizz (✨):** Effortless magnetic confidence and cool charisma.
  - **Profile & Bio Hook (🎯):** Observant openers connecting to hobbies, pets, or travel photos.
  - **Fun & Curious (🤔):** Engaging dilemmas and debates that break the ice instantly.
- **Dual Mode Access:**
  - Dedicated full-app screen with category selector, bio/topic input, and 1-tap copy.
  - Floating overlay tab to generate or copy openers directly over Tinder, Bumble, Hinge, or WhatsApp without leaving the app.
- **Multi-Provider AI & Curated Fallback:** Real-time generation powered by Gemini, OpenAI, Claude, or local LLMs, with instant offline access to the hand-crafted library.

### 3. Holistic Conversation Understanding & Flirty Persona Upgrade
- **Rhymes with the Whole Chat:** Never analyzes the latest message in isolation. Synthesizes full conversation history, running jokes, callbacks, and conversational rhythm.
- **Anti-Shallow Reactions:** Seamlessly advances the topic when incoming messages are brief (*"haha"*, *"ok"*, memes, or stickers).
- **High-Charm Flirty Replies:** Distinct multi-angle flirting styles (The Heart-Melter, The Playful Tease, The Smooth Charmer, and The Callback Flirt).

### 4. Floating Overlay Assistant (Flagship Mode)
- **Persistent Accessibility:** Operates directly above WhatsApp, Telegram, Instagram, Messenger, dating applications, and email clients using Android's `TYPE_APPLICATION_OVERLAY`.
- **Draggable Bubble with Inertia:** Circular bubble with touch-drag physics, screen-border edge snapping, and live visual state rings (Idle, Analyzing pulse, Success checkmark, Error badge).
- **In-App Compose Panel:** Expandable bottom sheet embedded directly in the overlay window to view extracted messages, pick reply personalities, and preview suggestions without app-switching.
- **Auto-Hide During Capture:** Automatically hides overlay elements 150ms before screen acquisition to prevent the assistant from screenshotting itself.

### 5. Privacy-Preserving Screen Capture & OCR Pipeline
- **User-Authorized MediaProjection:** Single-frame capture session with system consent dialog and foreground service (`FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION`). Immediate session teardown once the frame is captured.
- **On-Device ML Kit OCR:** Analyzes text locally using Google ML Kit Text Recognition. Zero raw image pixels are uploaded to external cloud servers.
- **Intelligent Flow Reconstruction:**
  - Sorts text elements chronologically.
  - Distinguishes **Incoming** vs. **Outgoing** messages using horizontal bounding alignment heuristics (left-anchored vs. right-anchored).
  - Automatically filters out status bar noise (battery %, WiFi, time, "typing...", "Delivered").
  - Identifies candidate target message with one-tap selection and inline text correction.
- **Screenshot Import Fallback:** Full gallery / Photo Picker workflow (`ACTION_GET_CONTENT`) and Android Sharesheet integration (`ACTION_SEND`).

### 6. Multi-Provider AI Engine (Account Sign-In & BYOK)
RizzWise provides two ways to connect your favorite AI models:
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

### 7. 20 Distinct Reply Personalities & Refinement Engine
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

### 8. Safe Message Handoff (Human-in-the-Loop)
- **Strict User Agency:** RizzWise **never** sends messages automatically.
- Each generated reply features an instant **Copy to Clipboard** button with haptic/visual confirmation.
- The user retains 100% control over the final text before pressing "Send" in the target application.

---

## 📱 Application Screens

1. **Onboarding Carousel:** 4-step introduction explaining the BYOK model, permissions, and features.
2. **Home Dashboard:** Live floating bubble toggle, active AI provider card, quick screenshot analyzer, and recently used styles.
3. **Conversation Analysis:** Visual chat view with bubble separation, incoming/outgoing badges, target message picker, and OCR editor.
4. **Conversation Starters:** Dedicated category vault, topic/bio prompt input, and instant 1-tap copy cards.
5. **Style Selector:** Categorized grid of all 20 personalities with sliders for intensity, length, emojis, and objectives.
6. **Generated Replies:** 3–6 distinct response cards with Copy and Refine dropdown actions.
7. **AI Provider Management:** BYOK screen with masked key inputs, live latency testing, and custom endpoint templating.
8. **Custom Styles & Presets:** Create and save reusable prompt voices (e.g., "My Dating Tone", "Executive Client Voice").
9. **Privacy & Settings:** Overlay controls, system permission links, version control / update preferences, data retention toggles, and cryptographic key wiping.
10. **Reply History:** Local Room database storing copied suggestions with keyword search and one-tap re-copying.

---

## 🏗️ Technical Architecture

```
com.rizzwise.app/
├── RizzWiseApp.kt                   # Application singleton & DI coordinator
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
│   ├── provider/
│   │   ├── AiProvider.kt            # Provider abstraction interface
│   │   ├── GeminiProvider.kt        # Google Gemini REST implementation
│   │   ├── OpenAiProvider.kt        # OpenAI API implementation
│   │   ├── OpenAiCompatibleProvider.kt # Groq, Together, Ollama, DeepSeek
│   │   ├── CustomRestProvider.kt    # Custom endpoint with template engine
│   │   └── ProviderCoordinator.kt   # Provider routing & fallback logic
│   └── update/
│       └── AppUpdateDownloader.kt   # In-app background update engine
├── domain/
│   └── model/                       # Domain entities & enums
├── data/
│   ├── local/                       # Room database, DAO & HistoryEntity
│   ├── preferences/                 # Jetpack DataStore Preferences
│   ├── security/                    # Android Keystore EncryptedSharedPreferences
│   └── repository/                  # Repositories for History, Providers, Styles & Updates
└── ui/
    ├── navigation/                  # Compose Navigation graph
    ├── theme/                       # Material 3 Design System (Dark & Light)
    └── screens/                     # 10 primary feature screens
```

---

## 🛠️ Build & Installation

### Prerequisites
- **JDK:** Java 21 LTS
- **Android SDK:** Platforms `android-35`, Build-Tools `36.0.0`
- **Gradle:** 9.3.1 (wrapper included)

### Building the Debug & Release APKs
```bash
# Run unit tests
./gradlew test

# Assemble debug APK
./gradlew assembleDebug

# Assemble release APK
./gradlew assembleRelease
```

The compiled APKs will be located at:
- Debug: `app/build/outputs/apk/debug/app-debug.apk` (or `./apk/rizzwise-debug.apk`)
- Release: `app/build/outputs/apk/release/app-release.apk` (or `./apk/rizzwise-release.apk`)

---

## 🔒 Permissions & Security

| Permission | Purpose |
|---|---|
| `SYSTEM_ALERT_WINDOW` | Required to display the floating assistant bubble above third-party messaging apps. |
| `FOREGROUND_SERVICE` | Required to maintain overlay and single-frame capture lifecycle according to Android rules. |
| `FOREGROUND_SERVICE_MEDIA_PROJECTION` | Android 14+ requirement for user-authorized screen frame extraction. |
| `INTERNET` | Communicates with user-configured AI APIs (Gemini, OpenAI, custom endpoints) and checks for updates. |
| `POST_NOTIFICATIONS` | Displays required foreground service notification for overlay, capture status, and update downloads. |

---

## 🧪 Automated Testing

Unit test suite covers:
- **ConversationStartersTest:** Verifies strict anti-cliché filters, 'Aww' mandate, simple English directive, JSON parsing across markdown fences, and completeness of the 30+ curated library.
- **ConversationParserTest:** Verifies spatial clustering, chronological sorting, incoming vs. outgoing horizontal detection, noise filtering.
- **PromptBuilderTest:** Verifies prompt construction, untrusted conversation isolation delimiters, holistic conversation rhyme, and 20 personality directives.
- **ProviderParsingTest:** Verifies custom REST template variable interpolation and Keystore API key masking.
- **UpdateRepositoryTest:** Verifies version comparison semantics, update check frequencies, and JSON metadata parsing.

Execute tests via:
```bash
./gradlew test
```
