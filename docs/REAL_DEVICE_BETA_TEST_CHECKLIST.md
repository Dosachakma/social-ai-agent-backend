# Real Device Beta Validation Manual Test Checklist

This checklist is designed for physical device manual testing (Android 7.0 / API 24 through Android 15 / API 36).

---

## 1. Device & Build Specifications

| Parameter | Target Value |
| :--- | :--- |
| **Application ID** | `com.aistudio.socialagent.app` |
| **Version Code** | `1` |
| **Version Name** | `1.0.0` |
| **Minimum SDK** | API 24 (Android 7.0 Nougat) |
| **Target SDK** | API 36 (Android 16 preview / Android 15 compatibility) |
| **Artifact Tested** | Release APK / Debug APK / Internal Testing AAB |

---

## 2. 25-Point Physical Device Test Suite

### Section A: First Impressions & Initialization
- [ ] **1. Fresh Installation:** Install APK via `adb install` or download from Internal Testing track. App installs cleanly with no signature conflicts.
- [ ] **2. First Launch:** Cold start launch completes under 1.5s with branded splash screen animation and zero crash/ANR.
- [ ] **3. Onboarding:** 3-step carousel renders high-DPI illustrations, smooth swipe paging, working "Skip" and "Get Started" actions.
- [ ] **4. Explore Demo Workspace:** Tap "Continue as Guest Demo Mode" or "Sign In". Seamlessly transitions to Enterprise HQ workspace with pre-populated preview data.

### Section B: Core Productivity & Workspaces
- [ ] **5. Dashboard:**
  - Workspace status banner displays "All Systems Live" pill.
  - KPI cards (Connected Channels, Scheduled Queue, Published Today, Total Reach) calculate accurately.
  - Interactive 7D / 30D / 90D line chart responds smoothly to touch scrubber gestures.
- [ ] **6. AI Agent:**
  - Gemini reasoning copilot opens with interactive suggestion chips.
  - Autonomous Level switcher allows selecting "Semi-Autonomous", "Supervised", or "Full Auto".
  - Multi-step tool planning cards display step status indicators and interactive approval buttons.
- [ ] **7. AI Post Generation:**
  - Tap "Create Post" or ask AI Agent to generate copy for Facebook/Instagram/LinkedIn.
  - Formats hashtags, emoji, and character constraints per target platform.
- [ ] **8. Brand Profile & Memory:**
  - View brand context, tone of voice ("Visionary & Authoritative"), target audience, and forbidden keywords.
  - Edit and save brand profile; changes immediately reflect in subsequent AI generations.

### Section C: Content & Schedule Management
- [ ] **9. Content Creation:**
  - Quick action "Create Post" dialog allows entering copy, attaching image asset, selecting channels, and setting publication mode (Draft, Schedule, Publish Now).
- [ ] **10. Content List:**
  - Filter content by status (Draft, Scheduled, Published, Failed).
  - Search posts by keyword and inspect platform badges.
- [ ] **11. Calendar:**
  - Monthly and weekly grid views display queued posts on their scheduled dates.
  - Tap any date or event card to inspect post preview details.
- [ ] **12. Scheduler:**
  - Verify background worker scheduling via Android WorkManager.
  - Simulated background triggers execute on scheduled timestamps.

### Section D: Integrations, Analytics & Settings
- [ ] **13. Social Accounts:**
  - Clearly displays Demo Workspace sample channels (Facebook, Instagram, LinkedIn, TikTok, Twitter).
  - Meta Configuration Diagnostics card shows real-time config status.
  - Live OAuth selector clearly warns if server-side backend or App ID is missing instead of crashing.
  - Token status simulator (Expired, Revoked) updates UI correctly.
- [ ] **14. Analytics:**
  - Deep-dive metrics for multi-channel reach, engagement rate, follower velocity, and top-performing posts.
- [ ] **15. Settings:**
  - Workspace configuration, AI model preferences, security thresholds, and privacy policy links.

### Section E: System & Hardware Edge Cases
- [ ] **16. Dark Mode:**
  - Toggle system dark theme or app theme switcher.
  - All text meets WCAG AA contrast standards; glassmorphism cards adapt gracefully.
- [ ] **17. Back Navigation:**
  - System back button and gesture navigation gracefully navigate up the stack or exit cleanly from root.
- [ ] **18. App Restart (State Preservation):**
  - Force-close app from multitasking view and relaunch; preferences and workspace state persist.
- [ ] **19. Offline Behavior (Airplane Mode):**
  - Enable Airplane Mode. App displays cached local data without crashing; shows clear offline indicators.
- [ ] **20. Backend Unavailable Behavior:**
  - When backend server is unreachable, app displays friendly error cards rather than unhandled network exceptions.
- [ ] **21. Empty States:**
  - Clear content filters or disconnect channels; empty state illustrations with action buttons render properly.
- [ ] **22. Loading States:**
  - Shimmer skeleton loaders and circular spinners render during asynchronous queries.
- [ ] **23. Error Recovery:**
  - Retry buttons and dismissible snackbars allow immediate recovery from failed actions.
- [ ] **24. Screen Rotation:**
  - Rotate device between portrait and landscape (if enabled). UI reflows without losing input state.
- [ ] **25. Tablet / Wide-Screen Layout:**
  - On screens > 600dp (tablets, foldables, Chromebooks), UI centers with `widthIn(max = 1000.dp)` avoiding stretched layout.

---

## 3. Critical User Experience Flow (First-Time User Walkthrough)

```
[Install App / Launch]
         ↓
[Onboarding: 3 Feature Slides]
         ↓
[Sign In / Guest Demo Mode]
         ↓
[Dashboard: View KPIs & Performance Chart]
         ↓
[AI Agent: Tap Suggestion Chip → Watch Plan & Post Generation]
         ↓
[Calendar: View Scheduled Queue]
         ↓
[Social Accounts: Inspect Demo Channels & Diagnostics]
         ↓
[Analytics: Review Growth & Engagement]
```
*(Entire flow operable in under 2 minutes with zero developer intervention).*

---

## 4. Safety & Sandbox Verification

1. **Demo Data Distinction:**
   - All demo posts are explicitly flagged as simulated preview data.
   - No mock calls attempt to write live API data to external Meta/Instagram APIs without user OAuth.
2. **Graceful Live Failover:**
   - Attempting live Meta OAuth without configured backend credentials displays `LIVE CONFIGURATION REQUIRED` with diagnostic hints instead of throwing an unhandled exception or crash.
