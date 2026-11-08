/*
 * Recordings bundled with the app (played by ▶ Play instead of the phone voice).
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
  // 'gayatri-mantra': { file: 'audio/gayatri-mantra.mp3', credit: 'Pt. Name, City' },
};
