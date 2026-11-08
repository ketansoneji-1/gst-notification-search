# આરતી સંગ્રહ · Aarti Sangrah

A free, offline-friendly web app of Gujarati Aartis, Stutis, Bhajans, Shloks and Mantras.

## Features
- **51 prayers**: 10 Aartis (Jay Adhyashakti, Om Jai Jagdish Hare, Jai Ganesh Deva, Sukhkarta Dukhharta, Om Jai Shiv Omkara, Jai Ambe Gauri, Om Jai Lakshmi Mata, Aarti Kunj Bihari Ki, Aarti Kije Hanuman Lala Ki, Jay Sadguru Swami), Hanuman Chalisa, Ganapati Atharvashirsha (Ganapati Path), Gayatri Chalisa, Shri Ramchandra Kripalu, Vaishnav Jan To, and 35+ Shloks and Mantras (Ganesh, Shiv, Vishnu, Krishna, Ram, Hanuman, Devi, Lakshmi, Saraswati, Guru, Gayatri, Navagraha, morning, before meals, diya lighting, bedtime, Shanti mantras and Bhagavad Gita).
- **Script toggle**: Gujarati only, English only, or both line by line. English in *Easy* style or scholarly *IAST*.
- **Listen**: ▶ Play reads the text aloud line by line using the device's Gujarati / Hindi / Marathi voice (Web Speech API), with the current line highlighted, adjustable speed and a synthesised aarti bell. Tap any line to start from there. Links to sung versions on YouTube, and an option to play your own MP3 alongside the text.
- English meaning (ભાવાર્થ) for every item, filters by type and deity, search in Gujarati or English, favourites, text size, light/dark theme.

## Audio: pandit and singer pronunciation
▶ Play uses the best source available for each prayer:
1. **Your saved recording.** Tap **🎙 Record now** and have your pandit chant (or a singer sing) the prayer once into the phone, or **Save an MP3** you already have. It is stored on the device for that prayer.
2. **A recording listed in `audio/recordings.js`.** Eight freely licensed Wikimedia Commons recordings are listed (credits in `audio/CREDITS.md`). `audio/fetch-audio.sh` downloads them so the APK bundles them; otherwise the app streams them online, and **⬇ Save for offline** keeps them on the phone. Exact matches (Jay Sadguru Swami, Gayatri, Mahamrityunjaya, Om Namo Bhagavate Vasudevaya, Guru Stotram) are used by ▶ Play; related ones (Lakshmi, Ganesh and Radha Govind aartis) are shown as a separate player. Only add recordings you made or have permission to share.
3. **The phone's Hindi / Gujarati voice**, with recitation rules applied (`pronounce.js`): Om, voiced visarga, silent avagraha, pauses at । and ॥, (૨) repeats, and the full final "a" in Sanskrit. A computer voice cannot reproduce svara or a tune; use it only as a fallback.

## Install on mobile (as an app)
The app is a Progressive Web App: it installs with its own home-screen icon, opens full-screen and works offline.
1. Open `https://<your-username>.github.io/gst-notification-search/gujarati-aarti/` in **Chrome on Android**.
2. Tap **⬇ Install app** in the header (or Chrome menu ⋮ → **Install app / Add to Home screen**).
3. On iPhone: open in **Safari** → Share → **Add to Home Screen**.

To get an Android **APK / Play Store package**, paste the same URL into https://www.pwabuilder.com and choose *Package for stores → Android*.

## Run
Open `index.html` in a browser, or visit `/gujarati-aarti/` on the GitHub Pages site. No build step or server needed.

## Add a new aarti
Append an entry to `data.js` (format documented at the top of that file). Text may be typed in Devanagari (`script: 'deva'`, auto-converted to Gujarati script) or directly in Gujarati (`script: 'gu'`).

## Notes
- Read-aloud quality depends on the voices installed on the device. Android/Chrome usually provides Gujarati and Hindi voices; on desktop, install an Indian-language voice in OS settings if none is found.
- Texts follow commonly printed versions; wording can vary slightly between families and sampradayas.
