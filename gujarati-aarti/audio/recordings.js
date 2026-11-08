/*
 * Recordings bundled with the app (played by ▶ Play instead of the phone voice).
 *
 * Each entry: file = copy bundled in the app (if present), url = online source used
 * when the bundled copy is missing, exact = true when it follows this app's text
 * (▶ Play uses it), false for a related recording shown separately.
 * Run fetch-audio.sh to download all listed files into this folder before building.
 *
 * To add one:
 *  1. Put the file in this folder, named after the prayer id from data.js,
 *     e.g. audio/gayatri-mantra.mp3  (MP3 or M4A, mono 64 kbps is plenty for voice).
 *  2. Add a line below:  'gayatri-mantra': { file: 'audio/gayatri-mantra.mp3', credit: 'Pt. ____, Mumbai' },
 *  3. Rebuild the APK (gujarati-aarti-android/build.sh) or push to GitHub Pages.
 *
 * Only include recordings you made yourself or have written permission to share.
 * Commercial and YouTube recordings are copyrighted and must not be bundled.
 */
window.RECORDINGS = {
  'jay-sadguru-swami': {"file": "audio/jay-sadguru-swami.ogg", "url": "https://upload.wikimedia.org/wikipedia/commons/3/30/Aarti.ogg", "title": "Swaminarayan temple aarti (Jay Sadguru Swami)", "credit": "Jay Swaminarayan (Commons user)", "license": "GFDL", "licenseUrl": "https://www.gnu.org/licenses/fdl-1.3.html", "source": "https://commons.wikimedia.org/wiki/File:Aarti.ogg", "exact": true, "seconds": 245},
  'gayatri-mantra': {"file": "audio/gayatri-mantra.ogg", "url": "https://upload.wikimedia.org/wikipedia/commons/2/20/Gayatri_Mantra_as_it_is.ogg", "title": "Gayatri Mantra, chanted with Vedic svaras", "credit": "Rameshvar", "license": "FAL", "licenseUrl": "https://artlibre.org/licence/lal/en/", "source": "https://commons.wikimedia.org/wiki/File:Gayatri_Mantra_as_it_is.ogg", "exact": true, "seconds": 22},
  'mahamrityunjaya': {"file": "audio/mahamrityunjaya.ogg", "url": "https://upload.wikimedia.org/wikipedia/commons/4/45/Mrityunjaya.ogg", "title": "Mahamrityunjaya Mantra, chanted with Vedic svaras", "credit": "Rameshvar", "license": "FAL", "licenseUrl": "https://artlibre.org/licence/lal/en/", "source": "https://commons.wikimedia.org/wiki/File:Mrityunjaya.ogg", "exact": true, "seconds": 18},
  'om-namo-bhagavate': {"file": "audio/om-namo-bhagavate.oga", "url": "https://upload.wikimedia.org/wikipedia/commons/1/11/Om_Namo_Bhagavate_Vasudevaya_Pronunciation.oga", "title": "Om Namo Bhagavate Vasudevaya", "credit": "Tito Dutta", "license": "CC BY-SA 3.0", "licenseUrl": "https://creativecommons.org/licenses/by-sa/3.0/", "source": "https://commons.wikimedia.org/wiki/File:Om_Namo_Bhagavate_Vasudevaya_Pronunciation.oga", "exact": true, "seconds": 4},
  'guru-brahma': {"file": "audio/guru-brahma.ogg", "url": "https://upload.wikimedia.org/wikipedia/commons/f/ff/Sanskrit_Chanting_Guru_Stotram.ogg", "title": "Guru Stotram (this shloka is one of its verses)", "credit": "Swami Atmananda", "license": "CC0", "licenseUrl": "https://creativecommons.org/publicdomain/zero/1.0/", "source": "https://commons.wikimedia.org/wiki/File:Sanskrit_Chanting_Guru_Stotram.ogg", "exact": true, "seconds": 295},
  'om-jai-lakshmi-mata': {"file": "audio/om-jai-lakshmi-mata.ogg", "url": "https://upload.wikimedia.org/wikipedia/commons/8/8b/Aarti_Puja%2C_Lakshmi_Laxmi.ogg", "title": "Lakshmi aarti chant", "credit": "Shri hari om sharan ji", "license": "CC0", "licenseUrl": "https://creativecommons.org/publicdomain/zero/1.0/", "source": "https://commons.wikimedia.org/wiki/File:Aarti_Puja,_Lakshmi_Laxmi.ogg", "exact": false, "seconds": 301},
  'jai-ganesh-deva': {"file": "audio/jai-ganesh-deva.ogg", "url": "https://upload.wikimedia.org/wikipedia/commons/2/2d/Aarti_Puja%2C_Ganpati_Vandana.ogg", "title": "Ganesh aarti chant (Ganpati Vandana)", "credit": "Shri hari om sharan ji", "license": "CC0", "licenseUrl": "https://creativecommons.org/publicdomain/zero/1.0/", "source": "https://commons.wikimedia.org/wiki/File:Aarti_Puja,_Ganpati_Vandana.ogg", "exact": false, "seconds": 76},
  'aarti-kunj-bihari': {"file": "audio/aarti-kunj-bihari.ogg", "url": "https://upload.wikimedia.org/wikipedia/commons/3/3c/Aarti_Shri_Radha_Govind%2C_Musical_Puja_Prayer.ogg", "title": "Radha Govind aarti, Jaipur", "credit": "Shri Radha Govind Dev Ji Temple, Jaipur", "license": "CC0", "licenseUrl": "https://creativecommons.org/publicdomain/zero/1.0/", "source": "https://commons.wikimedia.org/wiki/File:Aarti_Shri_Radha_Govind,_Musical_Puja_Prayer.ogg", "exact": false, "seconds": 308},
  // 'prayer-id': { file: 'audio/prayer-id.mp3', credit: 'Pt. Name, City', exact: true },
};
