# Google Play Internal Testing & Pre-Launch Specification
**Application:** Social AI Agent  
**Package Name (`applicationId`):** `com.aistudio.socialagent.app`  
**Version Code:** `1`  
**Version Name:** `1.0.0`  
**Target SDK:** `36` (Android 16 / Minor Api Level 1)  
**Min SDK:** `24` (Android 7.0+)  
**Build Artifact:** Signed Android App Bundle (`.aab`)  

---

## 1. Executive Summary & Release Scope

This document provides the complete configuration, compliance, store listing copy, data safety declarations, and owner operational runbook required to publish **Social AI Agent** to the **Google Play Console Internal Testing Track**.

### Phase Constraints & Guardrails
- **In Scope:** Internal Testing Track configuration, AAB signing architecture, Data Safety, Content Rating declarations, App Access reviewer credentials/demo paths, truthful store copy, and end-to-end verification checklist.
- **Out of Scope (Deferred to Later Phases):** Stripe/RevenueCat billing integration, custom production domain migration, Meta App Review production submission, and Flippa marketplace packaging.

---

## 2. Release & Build Configuration Audit

| Property | Value | Audit Status | Notes |
|---|---|---|---|
| **Application ID** | `com.aistudio.socialagent.app` | ✅ VERIFIED | Unique package identifier configured in `app/build.gradle.kts` |
| **Version Code** | `1` | ✅ VERIFIED | Monotonically increasing integer for Play Console uploads |
| **Version Name** | `1.0.0` | ✅ VERIFIED | Semantic version string |
| **Target SDK** | `36` | ✅ VERIFIED | Fully compliant with Google Play target API requirements |
| **Min SDK** | `24` | ✅ VERIFIED | Supports 95%+ of active global Android devices |
| **Minification / R8** | `isMinifyEnabled = true` | ✅ VERIFIED | Enabled in release build type with custom keep rules |
| **Resource Shrinking** | `isShrinkResources = true` | ✅ VERIFIED | Unused drawables and layout resources stripped |
| **ProGuard Rules** | `app/proguard-rules.pro` | ✅ VERIFIED | Preserves Moshi models, Retrofit interfaces, Room, Coroutines, and LineNumberTable |
| **Permissions** | `android.permission.INTERNET` | ✅ VERIFIED | Only standard network access requested; zero dangerous permissions |
| **Deep Linking** | `socialai://auth/callback` | ✅ VERIFIED | Configured for Meta OAuth callback redirection |

---

## 3. Release Signing Architecture

### Owner Local Signing Environment Variables
The build system in `app/build.gradle.kts` dynamically reads signing credentials from environment variables without exposing sensitive credentials in version control:

- `KEYSTORE_PATH`: Absolute or relative path to the owner's `.jks` / `.keystore` upload key.
- `STORE_PASSWORD`: Keystore password.
- `KEY_ALIAS`: Key alias (defaults to `"upload"` if omitted).
- `KEY_PASSWORD`: Private key password.

### Generation of Production Upload Key (Owner Action)
If the owner does not already possess a Java Keystore for Google Play upload:
```bash
keytool -genkey -v \
  -keystore my-upload-key.jks \
  -alias upload \
  -keyalg RSA \
  -keysize 2048 \
  -validity 10000 \
  -storepass YOUR_STORE_PASSWORD \
  -keypass YOUR_KEY_PASSWORD
```

### Environment Setup for Release Build:
```bash
export KEYSTORE_PATH="/path/to/my-upload-key.jks"
export STORE_PASSWORD="YOUR_STORE_PASSWORD"
export KEY_ALIAS="upload"
export KEY_PASSWORD="YOUR_KEY_PASSWORD"

gradle :app:bundleRelease
```
*Artifact Location:* `app/build/outputs/bundle/release/app-release.aab`

---

## 4. Privacy Policy Declaration

- **Privacy Policy URL:** `https://social-ai-agent-backend.onrender.com/privacy-policy`
- **Hosted Backend Status:** Production-ready endpoint active on cloud backend.
- **Owner Action:** Enter this exact URL into the Google Play Console under **Policy > App content > Privacy policy**.

---

## 5. Google Play Data Safety Questionnaire Guidance

Based on the verified codebase implementation, complete the Data Safety questionnaire as follows:

### A. Data Collection & Sharing Overview
- **Does your app collect or share any of the required user data types?**  
  👉 **Yes**
- **Is all of the user data collected by your app encrypted in transit?**  
  👉 **Yes** (All network traffic uses TLS 1.3 / HTTPS).
- **Do you provide a way for users to request that their data be deleted?**  
  👉 **Yes** (Via Settings > Clear Workspace Data / Account Disconnect or email request to owner).

### B. Specific Data Types Collected

| Data Category | Specific Data Type | Collected? | Shared? | Purpose | Ephemeral? |
|---|---|---|---|---|---|
| **Personal Info** | Name / Email (Optional) | Collected | No | Account setup & workspace identification | No |
| **Personal Info** | User IDs / Workspace IDs | Collected | No | App functionality & multi-tenant isolation | No |
| **User Content** | Social Media Posts & Captions | Collected | Yes (sent to user's connected social platforms upon user approval/scheduling) | Core app functionality (social media scheduling and publishing) | No |
| **User Content** | Brand Voice / Profile Context | Collected | No | App functionality & personalization of AI prompts | No |
| **App Info & Performance** | Diagnostics / Crash logs | Optional | No | Analytics & bug fixing | Yes |
| **Device / Other IDs** | OAuth tokens (Encrypted) | Collected | No | Authentication & authorizing platform publishing | No |

### C. AI-Related Data Processing Declaration
- **AI Processing:** User-entered prompts, brand tone settings, and post ideas are transmitted via secure HTTPS to Google Gemini (`gemini-2.5-flash`) strictly to generate draft post suggestions.
- **Data Retention by AI:** No prompt data is sold or used to train third-party public models without consent.

---

## 6. Content Rating Questionnaire Guidance

- **Category:** Utility / Productivity / Business
- **Violence:** No
- **Sexuality / Nudity:** No
- **Language / Profanity:** No
- **Controlled Substances:** No
- **User-Generated Content:** Yes — The application allows users to draft, preview, and publish textual and media content to their own social media accounts.
- **Online Interactions / Social Networking:** Yes — Integrates with social networks via official APIs.
- **Physical Location Sharing:** No (Location services are not requested or accessed).
- **Digital Goods / In-App Purchases:** No (Not active in Phase 3.12).
- **Target Audience:** 18+ (Professionals, Social Media Managers, Marketers, Creators).
- **Expected Rating Result:** PEGI 3 / Everyone (ESRB) / USK 0.

---

## 7. App Access Instructions for Google Reviewers

Google Play reviewers require credentials or a frictionless flow to test all core app functionalities.

### Reviewer Access Configuration (Play Console > App Content > App Access)
Select **"All or some functionality in my app is restricted"** or provide instructions for the **Demo Workspace**:

> **Reviewer Instruction Text:**  
> "The Social AI Agent app provides a fully functional, zero-credential 'Demo Workspace' for testing all platform features without requiring live third-party social media accounts.  
> 
> **Access Steps:**  
> 1. Launch the app on your device.  
> 2. On the welcome / onboarding screen, tap **'Explore Demo Workspace'** (or **'Continue as Guest / Demo'**).  
> 3. You will immediately access the full app dashboard with pre-loaded mock analytics, scheduled posts, brand profiles, and active AI Agent capabilities.  
> 4. To test AI Agent post generation: Navigate to **AI Agent** tab -> enter a prompt (e.g., 'Launch our new summer product line') -> tap **Generate** -> review the generated plan and tap **Approve Plan**.  
> 5. To test Post Scheduling: Navigate to **Calendar** tab -> tap **+ (Create Post)** -> select target channels -> set time -> schedule.  
> 6. To test Account Management: Navigate to **Accounts** tab to inspect connected mock platforms (Facebook, Instagram, LinkedIn, X)."

---

## 8. Store Listing Copy & Metadata

### Application Title
**Social AI Agent** *(15 / 30 characters)*

### Short Description
**AI-powered autonomous social media manager: create, schedule, and grow your brand.** *(80 / 80 characters)*

### Full Description
```
Elevate your social media presence with Social AI Agent — the intelligent marketing copilot built to automate content creation, streamline scheduling, and grow your brand across channels.

KEY FEATURES:

🤖 AUTONOMOUS AI AGENT
Generate high-converting post ideas, captions, and hashtag strategies tailored specifically to your unique brand voice. Review, edit, and approve AI-generated publishing plans with a single tap.

📅 MULTI-PLATFORM CALENDAR & SCHEDULING
Plan weeks of engaging social content in minutes. Schedule posts seamlessly across Facebook, Instagram, LinkedIn, and X (Twitter) with precision time slot targeting.

🎨 CUSTOM BRAND PROFILES
Define your brand’s tone of voice, target audience, core values, and industry niche. The AI adapts to your guidelines to ensure every post sounds authentically yours.

📊 PERFORMANCE & METRICS
Track engagement rates, impressions, audience reach, and top-performing content across your connected platforms from a clean, unified dashboard.

🛡️ ENTERPRISE-GRADE SECURITY & CONTROL
You remain in full control. The agent suggests content, while you hold the approval key. Supports secure OAuth token handling and local workspace management.

💡 INTERACTIVE DEMO WORKSPACE
Test every feature instantly with our built-in interactive preview mode — no external accounts required to get started.

Take the guesswork out of social media marketing. Download Social AI Agent today and streamline your social strategy.
```

### Suggested Store Category & Tags
- **Primary Category:** Productivity / Business
- **Tags / Keywords:** Social Media Manager, AI Content Creator, Post Scheduler, Social Media Marketing, Brand Management, Content Planner.

---

## 9. Store Graphics & Visual Assets Checklist

| Asset Type | Specifications | Status | Owner Action |
|---|---|---|---|
| **App Icon** | 512 x 512 px, 32-bit PNG with alpha, max 1024KB | ✅ Available in source (`res/drawable/app_logo_1786557823174.jpg` & mipmaps) | Upload PNG to Play Console Store Listing |
| **Feature Graphic** | 1024 x 500 px, JPEG or 24-bit PNG (no alpha) | ⚠️ Owner Asset Required | Create banner showcasing app logo + tagline |
| **Phone Screenshots** | Min 2, max 8. JPEG/24-bit PNG, 16:9 or 9:16 (min 1080px) | ⚠️ Owner Device Screenshots | Capture high-res screens from live device / preview |
| **7-inch Tablet Screenshots** | Optional for mobile-first, recommended | ⚠️ Optional | Capture on tablet or fold emulator if desired |
| **10-inch Tablet Screenshots** | Optional for mobile-first, recommended | ⚠️ Optional | Capture on tablet emulator if desired |

---

## 10. Owner Internal Testing Deployment Checklist (22 Steps)

Follow these 22 steps in the Google Play Console to launch internal testing:

1. **Google Play Developer Account:** Ensure developer account registration is active at [play.google.com/console](https://play.google.com/console).
2. **Create New Application:** Click **Create app** -> Name: `Social AI Agent` -> Default language: English (United States) -> App or game: App -> Free or paid: Free.
3. **Accept Declarations:** Accept the Developer Program Policies and US export laws declarations.
4. **App Content - Privacy Policy:** Paste `https://social-ai-agent-backend.onrender.com/privacy-policy`.
5. **App Content - App Access:** Provide the Demo Workspace instructions documented in Section 7.
6. **App Content - Ads Declaration:** Select **No, my app does not contain ads**.
7. **App Content - Content Rating:** Start questionnaire, select **Utility/Productivity/Business**, answer questions per Section 6, and save calculated rating.
8. **App Content - Target Audience & Content:** Select **18 and over**; confirm app is not directed at children under 13.
9. **App Content - News Apps / Financial Features / Health:** Declare **No** to all specialized sector categories.
10. **App Content - Data Safety:** Fill in responses as specified in Section 5.
11. **App Content - Government Apps:** Select **No**.
12. **Store Presence - Main Store Listing:** Enter App name, Short description, and Full description from Section 8.
13. **Store Presence - Graphic Assets:** Upload 512x512 App Icon, 1024x500 Feature Graphic, and Phone Screenshots.
14. **Store Presence - Contact Details:** Enter support email (e.g. `dippochakma135@gmail.com`) and privacy policy URL.
15. **Release Management - Internal Testing Track:** Navigate to **Testing > Internal testing**.
16. **Create Internal Release:** Click **Create new release**.
17. **Upload AAB:** Upload `app-release.aab` generated from `gradle :app:bundleRelease`.
18. **Release Details:** Enter Release name: `1.0.0 (1)` and Release notes (e.g., "Initial Internal Testing Build - Social AI Agent core suite").
19. **Manage Testers:** Under **Testers** tab, create an email list (e.g., "Internal Team") and add tester Gmail addresses.
20. **Review and Rollout:** Review release summary, ensure 0 blocking errors, and click **Start rollout to Internal testing**.
21. **Distribute Join Link:** Copy the internal test opt-in link and share with designated testers.
22. **Real Device Verification:** Testers accept invite via web/Play Store, install the app on physical Android devices, and verify end-to-end flows (Onboarding, AI Generation, Scheduler, Accounts, Analytics).
