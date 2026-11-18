# Study Bully — Study Mode for Galaxy Z Fold5

Turn Study Mode on and every unlock opens a full-screen, edge-to-edge image from your
Pinterest board. Images shuffle with no repeats until the whole collection has been shown.
You dismiss it with a tap or a swipe after a short stare-lock that you set (0–8 s).

## 1. Get the APK (about 5 minutes, no coding)
1. On github.com, create a new **private** repository, e.g. `study-bully`.
2. **Add file → Upload files**, then drag in *everything inside* this folder, including the
   hidden `.github` folder. (On a PC, Explorer may hide it. If it does, create the file
   `.github/workflows/build.yml` by hand in GitHub's editor and paste in the contents.)
3. Commit. Open the **Actions** tab and wait for "Build Study Bully APK" to go green (~4 min).
4. On the phone, open the repo's **Releases** and download `app-release.apk`.
5. Open the APK and allow your browser or My Files to "Install unknown apps" when asked.

Every later push installs over the old version, because the app is always signed with the
included `app/studybully.jks`.

## 2. One-time setup in the app
Open **Study Bully** and tap **FIX** on every red ✗:
- **Notifications.**
- **Appear on top.** This lets it open full-screen on unlock. If the toggle is greyed out
  ("Restricted setting"): App info → ⋮ (top right) → *Allow restricted settings*, then try again.
- **Unrestricted battery.** Also go to App info → Battery → **Unrestricted**. Otherwise One UI
  will kill the listener after a while.
- **+ ADD STUDY MODE TILE.** Or: swipe down twice → ✎ → drag "Study Mode" into the panel.

## 3. Load your board
- **IMPORT FROM PINTEREST BOARD** (your board link is pre-filled). This uses Pinterest's public
  RSS and embed feeds: no login, original quality where Pinterest serves it, duplicates skipped.
  These feeds only list the board's **newest ~25–50 pins**.
- Rest of the board: in the Pinterest app open a pin → **Share** → **Add to Study Bully**.
  Or save pins to your gallery and use **ADD IMAGES FROM GALLERY** (multi-select).
- Near-duplicates (the same picture at a different size) are detected and skipped.
- Images live inside the app's private storage, so they work offline.

## 4. Turning Study Mode on
- **Quick Settings tile:** "Study Mode".
- **Long-press the app icon:** Study Mode ON / OFF shortcuts.
- **Samsung Modes and Routines:** Settings → Modes and Routines → **+ Add mode**, call it
  "Study", and switch **Do not disturb** on in that mode. Then in Study Bully, enable
  **Follow Do Not Disturb**. Starting or stopping your Samsung "Study" mode (manually, on a
  schedule or by location) now switches Study Bully with it. Note that *any* DND activation
  will also trigger it, so leave this off if you use DND for sleep.

## Wall Mode (phone unfolded on the desk)
Open it with the **Wall Mode** Quick Settings tile (add it the same way as the Study Mode
tile), from the long-press shortcut, or with the button in the app.
- The screen stays on, the phone won't sleep, and it cycles your collection full-screen
  with no repeats. Turn the phone sideways and it rotates with you.
- A red session timer counts up in the corner.
- **Tap** = next slide now. **Hold** or **Back** = exit.
- Set how long each slide stays up (10 s – 5 min) under Settings.
- Each slide sits at a slightly different offset, which protects the OLED from burn-in.
  Keep the phone on a charger for long sessions.
- To have the text roasts appear here too, turn on "Mix built-in text roasts".

## What it deliberately does NOT do
- It never appears on the lock screen. It appears only *after* you unlock, so your PIN,
  fingerprint and Emergency call work exactly as before.
- It never appears during a phone call or while one is ringing.
- It can always be dismissed after the stare-lock. Back also works.
- When Study Mode is off, the background listener is stopped completely.

## Honest limitations
- Android won't let an app open itself over other apps without **Appear on top**. Without
  it, the app falls back to a high-priority notification. On an unlocked phone that shows
  as a heads-up banner, not full-screen.
- A small persistent notification ("STUDY MODE ON") stays visible while it's active.
  Android requires this for anything that listens for unlocks.
- If you sideload the app, Samsung may occasionally reset battery settings after a major
  One UI update. Re-check step 2 after updates.
