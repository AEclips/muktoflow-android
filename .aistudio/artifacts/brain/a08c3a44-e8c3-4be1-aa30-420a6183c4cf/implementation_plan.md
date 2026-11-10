# ZenFocus - Pomodoro & Mindful Productivity App

A full-featured, calming Pomodoro productivity suite built with Jetpack Compose and Material 3, pairing focused work intervals with mindful task tracking, procedurally synthesized ambient soundscapes (Rain, Cafe, White Noise, Zen Bell), streak tracking, and interactive analytics.

---

### User Review & Critical Decisions

> [!IMPORTANT]
> The following directions were confirmed during initial planning:
> - **Visual Theme & Aesthetic**: Zen Calm with deep forest green, matcha sage, warm bamboo wood, and gentle sand tones, featuring dark mode support.
> - **Feature Scope**: Full Suite covering customizable timer cycles (Focus, Short Break, Long Break), structured Task Management with tags & estimated sessions, Ambient Sound generator, Daily Streaks, and Weekly/Monthly Analytics.
> - **Audio Engine**: Procedural, zero-dependency on-device audio synthesis using `AudioTrack` for continuous calming ambient backgrounds (Rain, Cafe ambient drone, Gentle White Noise) and warm resonant harmonic singing bowl/chime alert tones.

---

### 1. Overview & Core Concept

- **What It Does**: ZenFocus guides users through structured work and rest intervals using the proven Pomodoro Technique (default 25 min focus, 5 min short break, 15 min long break after 4 rounds). It seamlessly weaves together task prioritization, ambient focus soundscapes, and comprehensive session logging.
- **Target Audience**: Students, knowledge workers, creators, and professionals who desire high productivity without anxiety or burnout.
- **Key Value**: Replaces jarring alarms and sterile utility timers with a peaceful, grounding productivity companion that preserves attention, reduces cognitive fatigue, and celebrates daily consistency.

---

### 2. User Experience & Visual Design

#### Key User Flows
1. **Focus Session Flow**:
   - User launches the app to an organic circular countdown timer displaying the current task (or quick focus).
   - Tapping "Start" activates the smooth countdown, optional ambient soundscape (e.g. soothing rain), and subtle breathing pulsing glow.
   - User can pause, skip, or add +5 minutes if in a deep flow state.
   - On completion, a warm harmonic chime sounds, vibration triggers, streak updates, and the app transitions cleanly to Short Break.
2. **Task & Tag Management Flow**:
   - User navigates to the Tasks tab, creates tasks with expected Pomodoro count, due dates, and tags (Work, Study, Writing, Health).
   - Active task binds directly to the main timer display with a single tap.
   - Completed Pomodoros increment the task's progress bar in real time.
3. **Ambient Soundscape Control**:
   - Accessible via a dedicated mini floating drawer or quick sheet on the timer screen.
   - Independent volume slider and instant sound switcher: Off, Raindrops, Forest Stream, Soft White Noise, Warm Cafe Drone.
4. **Analytics & Streak Insights**:
   - Visual summary of today's focus minutes, total sessions completed, and ongoing daily streak.
   - 7-day focus bar chart, hourly distribution, and tag breakdown pie chart.

#### Visual Identity & Theme
- **Theme Palette**:
  - `Primary / Forest Deep`: `#2D5A43` (Deep Pine) / `#6B9E78` (Sage Green in Dark Mode)
  - `Secondary / Warm Bamboo`: `#D4A373` (Earthy Wood / Warm Ochre)
  - `Tertiary / Soft Moss`: `#588157`
  - `Background`: `#F7F9F6` (Light Matcha Parchment) / `#121814` (Deep Night Forest)
  - `Surface`: `#FFFFFF` (Card) / `#1A221C` (Dark Card Surface)
  - `Accent / Break`: `#4A7C59` (Short Break Cyan-Green), `#835338` (Long Break Amber)
- **Typography & Hierarchy**:
  - Display: Clean modern condensed numerals for the timer readout (`72.sp`, bold, tabular numbers).
  - Body: M3 clean Sans typography with generous line heights and serene letter spacing.
- **Micro-Interactions**:
  - Organic smooth circular progress stroke with spring physics.
  - Floating action button with haptic feedback on start/pause.
  - Staggered animated visibility for task check-offs and streak badge achievements.

---

### 3. Key Product Decisions & Trade-Offs

- **Decision 1: Procedural Native Sound Synthesis vs Heavy Pre-recorded MP3s**
  - *Chosen Approach*: Synthesize ambient noise (Brown/Pink noise for rain, filtered white noise, multi-frequency sine wave resonant chime) directly with Android's low-latency `AudioTrack`.
  - *Why*: Eliminates bundle bloat, prevents broken network audio streams, enables infinite continuous seamless loops without click artifacts, and works 100% offline.
  - *Alternatives Considered*: Bundling multiple 5MB MP3 assets (increases APK size unnecessarily and risks loop hitching).
- **Decision 2: Device-Local Persistence via Room Database**
  - *Chosen Approach*: Room Database with reactive Kotlin `Flow` queries for tasks, tags, completed session logs, and user preference state.
  - *Why*: Fast, zero-friction experience with no sign-in barriers, perfectly matching the user's personal daily productivity needs while surviving device reboots.
- **Decision 3: Foreground-Safe Session State with ViewModel & Coroutine Dispatcher**
  - *Chosen Approach*: Centralized `TimerEngine` singleton managed by `PomodoroViewModel` running high-precision monotonic clock time calculation (`SystemClock.elapsedRealtime()`).
  - *Why*: Immune to clock drift, pause-resume glitches, and Android lifecycle configuration changes.

---

### 4. Technical Architecture & Data Strategy

```
┌─────────────────────────────────────────────────────────────┐
│                      Jetpack Compose UI                     │
│  ┌──────────────┐ ┌──────────────┐ ┌─────────────┐ ┌──────┐ │
│  │ Timer Screen │ │  Tasks List  │ │  Analytics  │ │ Sound│ │
│  └──────┬───────┘ └──────┬───────┘ └──────┬──────┘ └─┬────┘ │
└─────────┼────────────────┼────────────────┼──────────┼──────┘
          │                │                │          │
┌─────────▼────────────────▼────────────────▼──────────▼──────┐
│                    PomodoroViewModel                        │
│   • UI State Flow        • Active Session Mode              │
│   • Current Task Binding • Daily Streak Calculations        │
└─────────┬─────────────────────────────────┬─────────────────┘
          │                                 │
┌─────────▼──────────────┐        ┌─────────▼─────────────────┐
│     TimerEngine        │        │      AudioEngine          │
│ • Monotonic Tick Loop  │        │ • AudioTrack PCM Stream   │
│ • State: Work/Break    │        │ • Noise & Chime Synthesis │
└─────────┬──────────────┘        └───────────────────────────┘
          │
┌─────────▼───────────────────────────────────────────────────┐
│              Room Database & Repository                     │
│  • TaskEntity          • SessionLogEntity   • SettingsStore │
│  • Reactive Flows      • Aggregated Stats & Daily Heatmap   │
└─────────────────────────────────────────────────────────────┘
```

#### Core Entities
1. **TaskEntity**: `id`, `title`, `notes`, `tag`, `estimatedPomodoros`, `completedPomodoros`, `isCompleted`, `createdAt`, `completedAt`.
2. **SessionLogEntity**: `id`, `taskId?`, `sessionType` (WORK, SHORT_BREAK, LONG_BREAK), `durationSeconds`, `timestamp`, `dateString` (YYYY-MM-DD for grouping).
3. **PomodoroSettings**: `workMinutes` (default 25), `shortBreakMinutes` (default 5), `longBreakMinutes` (default 15), `longBreakInterval` (default 4), `autoStartBreaks`, `autoStartWork`, `ambientSoundType`, `ambientVolume`, `hapticFeedback`.

---
