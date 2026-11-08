# ગુજરાતી ચોઘડિયા · Gujarati Choghadiya (Android)

An offline Android app that shows the day and night **Choghadiya** (ચોઘડિયા) for any date and place, highlights the running Choghadiya, and shows sunrise, sunset and Rahu Kaal.

## Features
- 8 day and 8 night Choghadiya with start/end times, in Gujarati and English.
- Colour coding: green = auspicious (Amrut, Shubh, Labh), blue = neutral (Char), red = inauspicious (Udveg, Kaal, Rog).
- "Current Choghadiya" card: time left, and the next auspicious Choghadiya.
- Date navigation (previous / next / date picker / today).
- 51 preset places (28 in Gujarat, 10 other Indian cities, 13 diaspora cities abroad) plus custom latitude/longitude.
- Sunrise/sunset, Rahu Kaal, Dinmaan/Ratrimaan.
- Menu options: sunrise method (upper limb + refraction, or disc centre) and 12/24-hour time.
- Works fully offline. No permissions, no data collected.

## Calculation rules
| Item | Rule |
|---|---|
| Day | Sunrise to sunset, divided into 8 equal parts |
| Night | Sunset to next sunrise, divided into 8 equal parts |
| Hindu day (vaar) | Changes at sunrise. Between midnight and sunrise, the previous day's night is still running |
| Fixed day order | Udveg (Sun) → Char (Venus) → Labh (Mercury) → Amrut (Moon) → Kaal (Saturn) → Shubh (Jupiter) → Rog (Mars) |
| First day Choghadiya | Ruled by the weekday's lord: Sun–Udveg, Mon–Amrut, Tue–Rog, Wed–Labh, Thu–Shubh, Fri–Char, Sat–Kaal |
| Night order | Each step moves two places back in the fixed order: Udveg → Shubh → Amrut → Char → Rog → Kaal → Labh |
| First night Choghadiya | Ruled by the lord of the 5th weekday from today (Sun → Thu = Shubh, Mon → Fri = Char, ...) |
| 8th Choghadiya | Same as the 1st (day and night) |
| Rahu Kaal | Part of the 8 day parts: Sun 8th, Mon 2nd, Tue 7th, Wed 5th, Thu 6th, Fri 4th, Sat 3rd |
| Sunrise/sunset | NOAA / Meeus solar equations; upper limb of the Sun on the horizon with refraction (zenith 90°50′) |

### Weekly table (day)
| Vaar | 1 | 2 | 3 | 4 | 5 | 6 | 7 | 8 |
|---|---|---|---|---|---|---|---|---|
| Sun | Udveg | Char | Labh | Amrut | Kaal | Shubh | Rog | Udveg |
| Mon | Amrut | Kaal | Shubh | Rog | Udveg | Char | Labh | Amrut |
| Tue | Rog | Udveg | Char | Labh | Amrut | Kaal | Shubh | Rog |
| Wed | Labh | Amrut | Kaal | Shubh | Rog | Udveg | Char | Labh |
| Thu | Shubh | Rog | Udveg | Char | Labh | Amrut | Kaal | Shubh |
| Fri | Char | Labh | Amrut | Kaal | Shubh | Rog | Udveg | Char |
| Sat | Kaal | Shubh | Rog | Udveg | Char | Labh | Amrut | Kaal |

### Weekly table (night)
| Vaar | 1 | 2 | 3 | 4 | 5 | 6 | 7 | 8 |
|---|---|---|---|---|---|---|---|---|
| Sun | Shubh | Amrut | Char | Rog | Kaal | Labh | Udveg | Shubh |
| Mon | Char | Rog | Kaal | Labh | Udveg | Shubh | Amrut | Char |
| Tue | Kaal | Labh | Udveg | Shubh | Amrut | Char | Rog | Kaal |
| Wed | Udveg | Shubh | Amrut | Char | Rog | Kaal | Labh | Udveg |
| Thu | Amrut | Char | Rog | Kaal | Labh | Udveg | Shubh | Amrut |
| Fri | Rog | Kaal | Labh | Udveg | Shubh | Amrut | Char | Rog |
| Sat | Labh | Udveg | Shubh | Amrut | Char | Rog | Kaal | Labh |

## Accuracy
- Unit tests check both weekly tables cell by cell, Rahu Kaal, contiguous equal slots, and the before-sunrise rule.
- Sunrise/sunset are checked against the independent Python `astral` library for Ahmedabad, Mumbai, London, Sydney and New York in different seasons; all are within 60 seconds (Ahmedabad differs by about 12 seconds).
- Times are shown rounded to the nearest minute. Printed panchangs may differ by about ±1 minute because of rounding, the city-centre coordinates used, and the sunrise convention.

## Build
Requirements: JDK 17+, Android SDK (platform 35).
```bash
cd choghadiya-android
./gradlew :app:testDebugUnitTest :app:assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```
Or open the `choghadiya-android` folder in Android Studio and press Run.

GitHub Actions (`.github/workflows/choghadiya-android.yml`) builds the APK on every push that touches this folder. You can download it from the run's **Artifacts** section (`choghadiya-debug-apk`).

## Install on a phone
1. Copy `app-debug.apk` to the phone.
2. Open it and allow "Install unknown apps" for your file manager when Android asks.
3. Open **ચોઘડિયા** from the app drawer.

## Project layout
- `engine/SunCalculator.kt`: sunrise/sunset calculation (pure Kotlin, no Android dependency).
- `engine/Choghadiya.kt`: Choghadiya types, sequence rules, Rahu Kaal, slot calculation.
- `engine/Cities.kt`: preset places.
- `ui/MainActivity.kt`: the screen.
- `src/test/.../ChoghadiyaTest.kt`: unit tests.
