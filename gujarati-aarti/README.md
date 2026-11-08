# આરતી સંગ્રહ · Aarti Sangrah

A free, offline-friendly web app of Gujarati Aartis, Stutis, Bhajans, Shloks and Mantras.

## Features
- **49 prayers**: 10 Aartis (Jay Adhyashakti, Om Jai Jagdish Hare, Jai Ganesh Deva, Sukhkarta Dukhharta, Om Jai Shiv Omkara, Jai Ambe Gauri, Om Jai Lakshmi Mata, Aarti Kunj Bihari Ki, Aarti Kije Hanuman Lala Ki, Jay Sadguru Swami), Hanuman Chalisa, Shri Ramchandra Kripalu, Vaishnav Jan To, and 35+ Shloks and Mantras (Ganesh, Shiv, Vishnu, Krishna, Ram, Hanuman, Devi, Lakshmi, Saraswati, Guru, Gayatri, Navagraha, morning, before meals, diya lighting, bedtime, Shanti mantras and Bhagavad Gita).
- **Script toggle**: Gujarati only, English only, or both line by line. English in *Easy* style or scholarly *IAST*.
- **Listen**: ▶ Play reads the text aloud line by line using the device's Gujarati / Hindi / Marathi voice (Web Speech API), with the current line highlighted, adjustable speed and a synthesised aarti bell. Tap any line to start from there. Links to sung versions on YouTube, and an option to play your own MP3 alongside the text.
- English meaning (ભાવાર્થ) for every item, filters by type and deity, search in Gujarati or English, favourites, text size, light/dark theme.

## Run
Open `index.html` in a browser, or visit `/gujarati-aarti/` on the GitHub Pages site. No build step or server needed.

## Add a new aarti
Append an entry to `data.js` (format documented at the top of that file). Text may be typed in Devanagari (`script: 'deva'`, auto-converted to Gujarati script) or directly in Gujarati (`script: 'gu'`).

## Notes
- Read-aloud quality depends on the voices installed on the device. Android/Chrome usually provides Gujarati and Hindi voices; on desktop, install an Indian-language voice in OS settings if none is found.
- Texts follow commonly printed versions; wording can vary slightly between families and sampradayas.
