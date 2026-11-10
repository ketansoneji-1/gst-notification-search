# Meal Randomizer (Lunch & Dinner)

A small, browser-only app that randomly picks what to cook for lunch and dinner and enforces a **minimum gap (in days) before the same dish can repeat**.

## Features

- **Today tab:** randomize lunch, dinner or both for any date; spin again until you like the pick, then press **Cook this** to save it to the log.
- **Minimum gap rule:**
  - Default gap set in **Settings** (7 days unless you change it).
  - Optional **per-dish gap** override, e.g. 14 days for Pav Bhaji or Biryani.
  - The gap covers both meals, so the same dish never fills lunch and dinner on the same day.
  - It is checked against past *and* planned meals.
  - Gap `0` switches the rule off for that dish.
- **Smart weighting (optional):** dishes that haven’t been made for longer are more likely to be picked.
- **Week Planner:** generate a 1–31 day plan in one click, re-roll any single slot, and save the whole plan to the log. Slots already in the log stay as they are.
- **Dishes tab:** add, rename, pause/resume or delete dishes, and set each one as lunch, dinner or both. Each dish shows when it was last made and its **next available date**.
- **History tab:** full meal log. You can also log meals cooked outside the app, and you are warned if one breaks the gap rule.
- **Clear message when nothing fits:** if every dish is still inside its gap, the app lists the dishes that become available soonest.
- **Backup:** export/import your data as JSON. Your data stays in your browser’s local storage, and no server is involved.
- **Installable mobile app (PWA):** add it to your phone’s home screen; it opens full-screen with its own icon, has a bottom tab bar, and works offline.
- Light/dark theme and pre-loaded with 22 common Indian home-style dishes that you can edit freely.

## How the gap works

A dish with a gap of **N days** can be scheduled only on a date at least **N days away from every other date it is logged or planned**.

| Gap | Cooked on | Next eligible |
|-----|-----------|---------------|
| 7   | Mon 9 Nov | Mon 16 Nov    |
| 14  | Mon 9 Nov | Mon 23 Nov    |
| 0   | Mon 9 Nov | Any time      |

## Install on your phone

The app is a Progressive Web App (PWA). It must be opened once from its **https** address (GitHub Pages, below) to be installed. After that, it runs offline.

**Android (Chrome):**
1. Open the app link in Chrome.
2. Tap **⬇ Install app** at the top of the page, or Chrome menu **⋮ → Install app / Add to Home screen**.
3. Open **Meals** from your home screen or app drawer.

**iPhone / iPad (Safari):**
1. Open the app link in **Safari**.
2. Tap the **Share** button, then **Add to Home Screen**, then **Add**.
3. Open **Meals** from your home screen.

Notes:
- Your dishes and meal log are stored on the phone itself, separately from any desktop browser. Use **Settings → Export backup** and **Import backup** to move data between devices.
- Uninstalling the app or clearing the browser’s site data deletes the data, so export a backup first.
- After an update is published, the app picks it up in the background; close and reopen it once to see the new version.

## Run it

- **Locally:** open `meal-randomizer/index.html` in any modern browser.
- **GitHub Pages:** once Pages is enabled for this repository, the app is served at `https://<your-username>.github.io/gst-notification-search/meal-randomizer/`.

## Files

| File | Purpose |
|------|---------|
| `index.html` | User interface (tabs, styling, local storage) |
| `engine.js` | Core logic: eligibility, gap checks, weighted random pick, multi-day planner |
| `engine.test.js` | Unit tests for the engine: `node meal-randomizer/engine.test.js` |
| `manifest.webmanifest` | App name, icons and colours used when installing on a phone |
| `sw.js` | Service worker that caches the app for offline use (bump `VERSION` when releasing changes) |
| `icons/` | App icons (Android, iPhone, favicon) |
