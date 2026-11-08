/*
 * Pronunciation layer for read-aloud.
 *
 * Phone and browser voices (Hindi / Gujarati / Marathi) are built for modern
 * speech, not for paath or chanting. Fed raw text they read the visarga (ः) as
 * silence, say "avagraha" or skip ऽ, read verse numbers aloud, ignore (२) repeat
 * marks, rush through dandas, and drop the final "a" of Sanskrit words
 * ("वक्रतुण्ड" -> "vakratund"). This module rewrites each line into short
 * speakable phrases with pauses, following the usual rules of recitation:
 *
 *  - ॐ is spoken as "ओम्" (Om) with a pause after it
 *  - Visarga is voiced: "नमः" -> namaha, "हरिः" -> harihi, "गुरुः" -> guruhu
 *    (in Sanskrit); "दुःख" -> "दुह्ख" inside a word
 *  - Avagraha ऽ is removed (नमोऽस्तु -> नमोस्तु), as it is not pronounced
 *  - Verse numbers ॥१॥ and section marks are not spoken
 *  - "(२)" means "sing this phrase twice": the phrase is repeated
 *  - Dandas and commas become pauses; ॥ gets a longer pause
 *  - Sanskrit only: a word ending in a bare consonant keeps its final "a"
 *    by spelling it out, so voices say "deva", not "dev"
 *
 * Input and output are Devanagari; callers convert to Gujarati script when a
 * Gujarati voice is used.
 */
(function (root) {
  'use strict';

  var CONS = /[क-हक़-य़]/;        // क..ह and nukta forms
  var MATRA = /[ा-ौॢॣ]/;        // ा .. ौ
  var VIRAMA = '्';
  var ECHO = {                                      // visarga echo by vowel sign
    '': 'ह', 'ा': 'ह', 'ि': 'हि', 'ी': 'हि', 'ु': 'हु', 'ू': 'हु',
    'े': 'हे', 'ै': 'हि', 'ो': 'हो', 'ौ': 'हु', 'ृ': 'हि'
  };

  function voiceVisarga(word, lang) {
    // inside a word: ः before a consonant -> ह्
    word = word.replace(/ः(?=[क-ह])/g, 'ह्');
    if (word.slice(-1) !== 'ः') return word;
    var body = word.slice(0, -1), last = body.slice(-1);
    if (lang !== 'sa') return body + 'ह';
    var echo = MATRA.test(last) ? (ECHO[last] || 'ह') : ECHO[''];
    // keep the echoed vowel audible: "नमः" -> "नमहा" is too long, "नमह" loses it;
    // a following "अ"-less form is spoken as "namaha" by keepFinalA below.
    return body + echo;
  }

  // Sanskrit: voices drop the inherent final a. Ending the word on the
  // independent vowel "अ" is read as a short final "a" by Hindi/Gujarati/
  // Marathi voices without lengthening it.
  function keepFinalA(word) {
    var last = word.slice(-1);
    if (!CONS.test(last) || word.length === 1) return word;   // न, च, स: already "na", "cha", "sa"
    return word + 'अ';
  }

  function cleanLine(s) {
    return s
      .replace(/॥\s*[०-९0-9.\-]+\s*॥/g, '॥')       // ॥१॥ -> ॥
      .replace(/ऽ/g, '')                               // avagraha
      .replace(/ॐ/g, 'ओम्,')                           // Om + pause
      .replace(/[“”"']/g, '')
      .replace(/\s+/g, ' ')
      .trim();
  }

  /** Expand "(२)" / "(2)": repeat the phrase since the previous pause mark. */
  function expandRepeats(s) {
    var re = /\(\s*[२2]\s*\)/;
    var guard = 0;
    while (re.test(s) && guard++ < 10) {
      var m = s.match(re), before = s.slice(0, m.index).replace(/\s+$/, '');
      var cut = Math.max(before.lastIndexOf(','), before.lastIndexOf('।'), before.lastIndexOf('॥'));
      var phrase = before.slice(cut + 1).trim();
      s = before + (phrase ? ', ' + phrase : '') + s.slice(m.index + m[0].length);
    }
    return s;
  }

  /**
   * Split a Devanagari line into [{ text, pause }] where pause is the silence
   * in ms to leave after the phrase. lang: 'sa' | 'hi' | 'gu' | 'mr'.
   */
  function segments(line, lang) {
    var s = expandRepeats(cleanLine(line));
    var out = [], buf = '';
    function push(p) {
      var t = buf.trim(); buf = '';
      if (!t) { if (out.length) out[out.length - 1].pause = Math.max(out[out.length - 1].pause, p); return; }
      t = t.split(' ').map(function (w) {
        w = voiceVisarga(w, lang);
        return lang === 'sa' ? keepFinalA(w) : w;
      }).join(' ');
      out.push({ text: t, pause: p });
    }
    for (var i = 0; i < s.length; i++) {
      var ch = s[i];
      if (ch === '॥') push(650);
      else if (ch === '।') push(lang === 'sa' ? 450 : 350);
      else if (ch === ',' || ch === ';') push(220);
      else buf += ch;
    }
    push(380);                                        // end of line
    return out;
  }

  var api = { segments: segments };
  if (typeof module !== 'undefined' && module.exports) module.exports = api;
  else root.Pronounce = api;
})(this);
