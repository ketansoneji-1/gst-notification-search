# ગુજરાતી ચોઘડિયા પંચાંગ · Gujarati Choghadiya & Panchang (Android)

An offline Android app for the Gujarati **Choghadiya** (ચોઘડિયા) and the full **Panchang** (પંચાંગ), with a month calendar that shows the Gujarati tithi beside each English date. The whole app switches between **ગુજરાતી** and **English** with one tap.

## Screens
| Screen | What it shows |
|---|---|
| **Choghadiya** (home) | 8 day + 8 night Choghadiya with colour coding, the running one highlighted, time left, next auspicious Choghadiya, sunrise, sunset, Rahu Kaal, and the Gujarati date |
| **📖 Full Panchang** | Gujarati date (month, paksha, tithi), English date, Vikram Samvat (Kartik-based), Shaka Samvat, Ritu, Ayana, special days; the five limbs (Vaar, Tithi, Nakshatra, Yoga, Karana) with end times; sunrise, sunset, moonrise, moonset, day and night length, Sun and Moon rashi, Sankranti; Abhijit Muhurat, Rahu Kaal, Yamaganda, Gulika Kaal |
| **📅 Calendar** | Month grid: English date + Gujarati tithi; Purnima/Amavasya, Ekadashi and other special days in colour; list of the month's special days; tap a date to open its Panchang |

Menu: **English / ગુજરાતી** switch (Gujarati mode also uses Gujarati digits), place (51 presets or custom latitude/longitude), 12/24-hour time, sunrise method, About.

## Calculation method
| Item | Method |
|---|---|
| Sun | VSOP87 (Meeus, *Astronomical Algorithms*), within 1″ of Swiss Ephemeris |
| Moon | ELP-2000/82 main terms (Meeus ch. 47), within 12″ |
| Ayanamsa | Lahiri (Chitrapaksha) |
| Sunrise / sunset | Upper limb of the Sun with refraction (zenith 90°50′) |
| Tithi | Moon − Sun elongation, 12° each; the tithi at sunrise (udaya tithi) names the day; a tithi skipped between two sunrises (kshaya) is still shown and observed |
| Nakshatra / Yoga / Karana | Sidereal Moon / sidereal Sun + Moon / half-tithi, standard 27 / 27 / 11 names |
| Month | Amanta (Gujarati). Named from the Sun's rashi at the new moon that begins it; a month without a Sankranti is Adhik |
| Vikram Samvat | Changes on Kartik Sud Ekam (Gujarati New Year). Shaka Samvat is Chaitra-based |
| Rahu / Yamaganda / Gulika | Fixed eighths of daytime by weekday |
| Abhijit Muhurat | 8th of 15 day muhurtas (not observed on Wednesday) |
| Choghadiya | Day and night each in 8 equal parts; day starts with the weekday lord; night starts with the lord of the 5th weekday |

### Weekly Choghadiya tables
Day:
| Vaar | 1 | 2 | 3 | 4 | 5 | 6 | 7 | 8 |
|---|---|---|---|---|---|---|---|---|
| Sun | Udveg | Char | Labh | Amrut | Kaal | Shubh | Rog | Udveg |
| Mon | Amrut | Kaal | Shubh | Rog | Udveg | Char | Labh | Amrut |
| Tue | Rog | Udveg | Char | Labh | Amrut | Kaal | Shubh | Rog |
| Wed | Labh | Amrut | Kaal | Shubh | Rog | Udveg | Char | Labh |
| Thu | Shubh | Rog | Udveg | Char | Labh | Amrut | Kaal | Shubh |
| Fri | Char | Labh | Amrut | Kaal | Shubh | Rog | Udveg | Char |
| Sat | Kaal | Shubh | Rog | Udveg | Char | Labh | Amrut | Kaal |

Night:
| Vaar | 1 | 2 | 3 | 4 | 5 | 6 | 7 | 8 |
|---|---|---|---|---|---|---|---|---|
| Sun | Shubh | Amrut | Char | Rog | Kaal | Labh | Udveg | Shubh |
| Mon | Char | Rog | Kaal | Labh | Udveg | Shubh | Amrut | Char |
| Tue | Kaal | Labh | Udveg | Shubh | Amrut | Char | Rog | Kaal |
| Wed | Udveg | Shubh | Amrut | Char | Rog | Kaal | Labh | Udveg |
| Thu | Amrut | Char | Rog | Kaal | Labh | Udveg | Shubh | Amrut |
| Fri | Rog | Kaal | Labh | Udveg | Shubh | Amrut | Char | Rog |
| Sat | Labh | Udveg | Shubh | Amrut | Char | Rog | Kaal | Labh |

## Accuracy (tested)
- **Swiss Ephemeris cross-check, 2026–2046:** 208 dates for Ahmedabad (one every 37 days). Worst difference: tithi end 17 s, nakshatra end 15 s, moonrise 15 s; sunrise and sunset within 60 s. The tithi, nakshatra and yoga index matches on every date.
- **Known dates:** Gujarati New Year 22 Oct 2025 (VS 2082) and 10 Nov 2026 (VS 2083); Diwali 8 Nov 2026 (Chaturdashi at sunrise, Amavasya begins that day); Adhik Jyeshtha 2026; Makar Sankranti in mid January.
- **Every day 2026–2046:** the tithi never jumps by more than one skipped tithi, the samvat never goes backwards, 7–8 Adhik months occur in the 21 years, and every fortnight's Ekadashi is marked.
- Printed panchangs may differ by about ±1 minute (rounding, city coordinates, sunrise convention).

## Valid for the next 20 years and beyond
- All values are **calculated**, not looked up, so no festival or ephemeris table runs out. The VSOP87 and ELP series are valid for centuries.
- ΔT (Earth-rotation correction) is modelled for 2005–2060 and has fallbacks beyond. Even a 10-second error here changes tithi times by under 1 second.
- Adhik months, kshaya tithis and the year change are worked out by rule, so they need no updates.
- The app targets Android 16 (API 36) and runs on Android 8.0 and later. It has no server, account, subscription or online dependency that could stop working.

## Security
| Check | Status |
|---|---|
| Permissions requested | **None** (no internet, location, storage, contacts, SMS, camera, phone). The only manifest entry is an AndroidX signature-level internal permission that other apps cannot obtain |
| Network | No network code; `INTERNET` permission absent; cleartext traffic disabled by network-security config |
| Exposed components | Only the launcher screen is exported. Panchang and Calendar screens are `exported=false`; the AndroidX profile-installer receiver is removed |
| Input from other apps | The only value read from a launch intent (a date) is range-checked (1900–2200); malformed values are covered by a test |
| User input | Latitude/longitude range-checked, place name length-limited and control characters stripped |
| Stored data | Only settings (language, place, time format) in app-private storage; excluded from cloud backup and device transfer |
| Release build | Not debuggable, R8 shrinking and obfuscation enabled, signed with a 4096-bit RSA key (APK Signature Scheme v2) |
| Risky APIs | No WebView, JavaScript, dynamic code loading, reflection, native code, file sharing or content providers of our own |
| Dependencies | AndroidX Core 1.17, AppCompat 1.8, Material 1.14 (current); Android lint security checks set to fail the build |

## Build
Requirements: JDK 17+, Android SDK platform 36.
```bash
cd choghadiya-android
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
# Release (needs keystore.properties, which is git-ignored):
./gradlew :app:assembleRelease
```
`keystore.properties` format:
```
storeFile=/path/to/choghadiya-release.jks
storePassword=...
keyAlias=choghadiya
keyPassword=...
```
GitHub Actions (`.github/workflows/choghadiya-android.yml`) runs the tests and lint and builds a debug APK on every change to this folder.

## Install on a phone
1. Uninstall any earlier test build of the app (it was signed with a different key).
2. Copy `app-release.apk` to the phone, open it, and allow "Install unknown apps" for your file manager when asked.
3. Open **ચોઘડિયા પંચાંગ**.

## Project layout
- `engine/Astro.kt`: Sun, Moon, ayanamsa, ΔT, moonrise, crossing solver.
- `engine/Panchang.kt`: tithi, nakshatra, yoga, karana, lunar month, samvat, kaal periods, special days.
- `engine/Choghadiya.kt`, `engine/SunCalculator.kt`: Choghadiya and sunrise/sunset.
- `engine/Names.kt`, `engine/Cities.kt`: Gujarati/English names, preset places.
- `ui/`: Choghadiya, Panchang and Calendar screens, shared settings and language helper.
- `src/test/`: engine tests, the Swiss Ephemeris reference data (`resources/swisseph_ahmedabad.csv`), and UI smoke tests that render every screen in both languages.
