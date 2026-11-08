# Publishing "Choghadiya Panchang" on Google Play

Everything the app side needs is prepared in this folder. Only the account, payment and console steps must be done by you.

## 1. What is ready
| Item | File / value |
|---|---|
| App bundle to upload | `app-release.aab`, version 2.1.1 (code 4), target API 36, signed with your key (`choghadiya-release.jks`) |
| App icon 512 × 512 | `play-store/icon-512.png` |
| Feature graphic 1024 × 500 | `play-store/feature-graphic-1024x500.png` |
| Phone screenshots 1080 × 1920 | `play-store/screenshots/1_…png` to `6_…png` |
| Privacy policy | `choghadiya-privacy.html` in the repository root, served at `https://ketansoneji-1.github.io/gst-notification-search/choghadiya-privacy.html` once the PR is merged and GitHub Pages is on. **Replace `[SUPPORT EMAIL]` in it first.** |
| Package name (permanent) | `com.kbsc.choghadiya` |

## 2. Create a developer account (one time)
1. Go to <https://play.google.com/console/signup> with the Google account that should own the app.
2. Pick the account type:
   - **Organisation** (e.g. your CA firm). Requires a **D-U-N-S number** (free from Dun & Bradstreet India, usually 1–4 weeks), the firm's website and its phone number. The 12-tester rule below applies only to personal accounts.
   - **Personal.** Faster, but needs the closed test in step 5 before the app can go public.
3. Pay the one-time **US $25** registration fee.
4. Complete identity verification (government ID, address) and verify the contact phone and email.

## 3. Create the app in Play Console
*All apps → Create app*
- App name: `Choghadiya Panchang - Gujarati` (30 characters max)
- Default language: English (India) – en-IN. Add Gujarati (gu) as a translation for the store listing.
- App or game: **App** · Free or paid: **Free**
- Accept the declarations.

## 4. Fill in "App content" (Policy section)
| Question | Answer |
|---|---|
| Privacy policy | The URL from section 1 |
| App access | All functionality is available without special access |
| Ads | No, the app does not contain ads |
| Content rating | Questionnaire category "All other app types"; answer **No** to every question. Expected rating: Everyone / 3+ |
| Target audience | 13–15, 16–17, 18+ (choosing under-13 adds Families policy reviews; the app is safe for children either way) |
| News app | No |
| Data safety | "Does your app collect or share any of the required user data types?" → **No**. All data is processed only on the device. |
| Government app | No |
| Financial features | None |
| Health | None |

## 5. Testing, then production
**Personal account created after 13 Nov 2023:**
1. *Testing → Closed testing → Create track.* Upload `app-release.aab`. When asked, keep **Play App Signing** on (recommended: Google then keeps the final signing key, and a lost upload key can be reset through support).
2. Add at least **12 testers** (their Gmail addresses or a Google Group). Share the opt-in link; each tester must accept it and install the app.
3. Keep at least 12 testers opted in **continuously for 14 days**.
4. Then *Dashboard → Apply for production* and answer the short questionnaire about the test.

**Organisation account:** you can go directly to *Production → Create new release*.

In both cases: upload the AAB, release name `2.1.1`, release notes (below), *Review release → Start rollout*. Google's review usually takes from a few days to a week.

## 6. Store listing text (copy-paste)
**Short description, English (max 80 characters):**
Gujarati Choghadiya, Panchang, festivals & calendar. Offline, no ads.

**Short description, Gujarati:**
ચોઘડિયા, પંચાંગ, તહેવારો અને ગુજરાતી કેલેન્ડર – ઑફલાઇન, જાહેરાત વગર

**Full description, English:**
Choghadiya Panchang gives you the day's auspicious times and the full Gujarati Panchang, in Gujarati or English, fully offline.

• Choghadiya: all 8 day and 8 night Choghadiya (Amrut, Shubh, Labh, Char, Udveg, Kaal, Rog), with the current one highlighted, time left and the next auspicious period
• Full Panchang: Gujarati date (month, paksha, tithi), Vikram Samvat and Shaka Samvat, tithi, nakshatra, yoga and karana with end times, sunrise, sunset, moonrise, moonset, Sun and Moon rashi, ritu, ayana
• Muhurat: Abhijit, Rahu Kaal, Yamaganda and Gulika Kaal
• Festivals: Diwali, Dhanteras, Bestu Varas, Bhai Bij, Labh Pancham, Navratri, Dussehra, Holi, Uttarayan, Rath Yatra, Janmashtami, Ganesh Chaturthi, Raksha Bandhan, Maha Shivratri, all Ekadashis, Sankrantis, Purnima, Amas and national days
• Calendar: every English date shows its Gujarati tithi, with festivals marked
• 50+ places in Gujarat, India and abroad, or your own latitude/longitude
• One-tap switch between Gujarati and English
• Calculated on your phone with precise astronomical formulas (checked against Swiss Ephemeris). Works for any year with no updates needed.
• No internet, no permissions, no ads, no data collection

**Full description, Gujarati:**
ચોઘડિયા પંચાંગ – ગુજરાતી કે અંગ્રેજીમાં શુભ સમય અને સંપૂર્ણ પંચાંગ, ઇન્ટરનેટ વગર.

• ચોઘડિયા: દિવસ અને રાત્રિના આઠ-આઠ ચોઘડિયા (અમૃત, શુભ, લાભ, ચલ, ઉદ્વેગ, કાળ, રોગ), હાલનું ચોઘડિયું, બાકી સમય અને આગામી શુભ ચોઘડિયું
• સંપૂર્ણ પંચાંગ: ગુજરાતી તારીખ (માસ, પક્ષ, તિથિ), વિક્રમ સંવત, શક સંવત, તિથિ, નક્ષત્ર, યોગ, કરણ સમાપ્તિ સમય સાથે, સૂર્યોદય, સૂર્યાસ્ત, ચંદ્રોદય, ચંદ્રાસ્ત, સૂર્ય-ચંદ્ર રાશિ, ઋતુ, અયન
• મુહૂર્ત: અભિજિત, રાહુ કાળ, યમઘંટ, ગુલિક કાળ
• તહેવારો: દિવાળી, ધનતેરસ, બેસતું વર્ષ, ભાઈબીજ, લાભ પાંચમ, નવરાત્રી, દશેરા, હોળી, ઉત્તરાયણ, રથયાત્રા, જન્માષ્ટમી, ગણેશ ચતુર્થી, રક્ષાબંધન, મહા શિવરાત્રી, બધી એકાદશી, સંક્રાંતિ, પૂનમ, અમાસ અને રાષ્ટ્રીય દિવસો
• કેલેન્ડર: દરેક અંગ્રેજી તારીખ સાથે ગુજરાતી તિથિ અને તહેવાર
• ગુજરાત, ભારત અને વિદેશના 50થી વધુ શહેર અથવા તમારું પોતાનું સ્થળ
• એક ટૅપથી ગુજરાતી / English
• ઇન્ટરનેટ નહીં, પરવાનગી નહીં, જાહેરાત નહીં, કોઈ માહિતી એકત્ર નહીં

**Release notes (2.1.1):**
First release: Choghadiya, full Gujarati Panchang, festival calendar, Gujarati and English. Search the app in English too (Choghadiya, Panchang, Calendar, Festivals).

**Category:** Lifestyle · **Tags:** Calendar, Astrology, Religion

## 7. Future updates
1. Increase `versionCode` (and `versionName`) in `app/build.gradle.kts`.
2. Build with `./gradlew :app:bundleRelease` (needs `keystore.properties` and `choghadiya-release.jks`).
3. Upload the new AAB as a new release in Play Console.

**Keep `choghadiya-release.jks` and its password safe.** It is your upload key. With Play App Signing a lost upload key can be reset through Play support, but that takes time.

## 8. Notes
- Google Play currently requires new apps and updates to target **Android 16 (API 36)**. This app already does.
- Requirements change from time to time. Google's help page on the testing rule: <https://support.google.com/googleplay/android-developer/answer/14151465>
