/*
 * Script conversion for Aarti Sangrah
 *  - devaToGu : Devanagari -> Gujarati script (Unicode blocks are parallel, offset 0x180)
 *  - guToDeva : Gujarati -> Devanagari (used to feed Hindi/Marathi speech voices)
 *  - guToRoman: Gujarati -> English letters, two styles:
 *      'simple' : easy reading (aa -> a, sh, ch, chh ...), word-final schwa dropped
 *                 for Gujarati / Hindi / Marathi, kept for Sanskrit
 *      'iast'   : scholarly IAST with diacritics (ā ī ū ṛ ṃ ḥ ś ṣ ṭ ḍ ṇ)
 */
(function (root) {
  'use strict';

  var DANDA = { '।': 1, '॥': 1 };

  function devaToGu(s) {
    var out = '';
    for (var i = 0; i < s.length; i++) {
      var c = s.charCodeAt(i);
      if (c === 0x093C) continue;                 // nukta: not used in Gujarati script
      if (c >= 0x0900 && c <= 0x097F && !DANDA[s[i]]) {
        out += String.fromCharCode(c + 0x180);
      } else out += s[i];
    }
    return out;
  }

  function guToDeva(s) {
    var out = '';
    for (var i = 0; i < s.length; i++) {
      var c = s.charCodeAt(i);
      if (c >= 0x0A80 && c <= 0x0AFF) out += String.fromCharCode(c - 0x180);
      else out += s[i];
    }
    return out;
  }

  // consonant -> [simple, iast]
  var CONS = {
    'ક': ['k', 'k'], 'ખ': ['kh', 'kh'], 'ગ': ['g', 'g'], 'ઘ': ['gh', 'gh'], 'ઙ': ['n', 'ṅ'],
    'ચ': ['ch', 'c'], 'છ': ['chh', 'ch'], 'જ': ['j', 'j'], 'ઝ': ['jh', 'jh'], 'ઞ': ['n', 'ñ'],
    'ટ': ['t', 'ṭ'], 'ઠ': ['th', 'ṭh'], 'ડ': ['d', 'ḍ'], 'ઢ': ['dh', 'ḍh'], 'ણ': ['n', 'ṇ'],
    'ત': ['t', 't'], 'થ': ['th', 'th'], 'દ': ['d', 'd'], 'ધ': ['dh', 'dh'], 'ન': ['n', 'n'],
    'પ': ['p', 'p'], 'ફ': ['ph', 'ph'], 'બ': ['b', 'b'], 'ભ': ['bh', 'bh'], 'મ': ['m', 'm'],
    'ય': ['y', 'y'], 'ર': ['r', 'r'], 'લ': ['l', 'l'], 'ળ': ['l', 'ḷ'], 'વ': ['v', 'v'],
    'શ': ['sh', 'ś'], 'ષ': ['sh', 'ṣ'], 'સ': ['s', 's'], 'હ': ['h', 'h']
  };
  var VOW = {
    'અ': ['a', 'a'], 'આ': ['aa', 'ā'], 'ઇ': ['i', 'i'], 'ઈ': ['ee', 'ī'], 'ઉ': ['u', 'u'],
    'ઊ': ['oo', 'ū'], 'ઋ': ['ri', 'ṛ'], 'ઍ': ['e', 'ê'], 'એ': ['e', 'e'], 'ઐ': ['ai', 'ai'],
    'ઑ': ['o', 'ô'], 'ઓ': ['o', 'o'], 'ઔ': ['au', 'au']
  };
  var MATRA = {
    'ા': ['a', 'ā'], 'િ': ['i', 'i'], 'ી': ['i', 'ī'], 'ુ': ['u', 'u'], 'ૂ': ['u', 'ū'],
    'ૃ': ['ri', 'ṛ'], 'ૅ': ['e', 'ê'], 'ે': ['e', 'e'], 'ૈ': ['ai', 'ai'], 'ૉ': ['o', 'ô'],
    'ો': ['o', 'o'], 'ૌ': ['au', 'au']
  };
  var VIRAMA = '્', ANUSVARA = 'ં', CHANDRA = 'ઁ', VISARGA = 'ઃ', NUKTA = '઼', AVAGRAHA = 'ઽ', OM = 'ૐ';
  var LABIAL = { 'પ': 1, 'ફ': 1, 'બ': 1, 'ભ': 1, 'મ': 1 };

  function isLetter(ch) {
    return !!(CONS[ch] || VOW[ch] || MATRA[ch] || ch === VIRAMA || ch === ANUSVARA ||
      ch === CHANDRA || ch === VISARGA || ch === NUKTA || ch === AVAGRAHA || ch === OM);
  }

  function romanWord(w, lang, iast) {
    var k = iast ? 1 : 0;
    var parts = [];          // pieces of output
    var lastInherent = -1;   // index in parts of the last inherent 'a'
    var syll = 0;
    for (var i = 0; i < w.length; i++) {
      var ch = w[i];
      if (CONS[ch]) {
        // jña special case (જ્ઞ): simple style reads "gy"
        if (ch === 'જ' && w[i + 1] === VIRAMA && w[i + 2] === 'ઞ') {
          parts.push(iast ? 'jñ' : 'gy'); i += 2;
        } else parts.push(CONS[ch][k]);
        var nx = w[i + 1];
        if (nx === NUKTA) { i++; nx = w[i + 1]; }
        if (nx === VIRAMA) { i++; lastInherent = -1; continue; }
        syll++;
        if (MATRA[nx]) { parts.push(MATRA[nx][k]); i++; lastInherent = -1; }
        else { parts.push('a'); lastInherent = parts.length - 1; }
      } else if (VOW[ch]) {
        parts.push(VOW[ch][k]); syll++; lastInherent = -1;
      } else if (ch === ANUSVARA || ch === CHANDRA) {
        if (iast) parts.push(ch === CHANDRA ? 'm̐' : 'ṃ');
        else {
          var nn = w[i + 1];
          if (nn === undefined) parts.push(lang === 'sa' ? 'm' : 'n');
          else parts.push(LABIAL[nn] ? 'm' : 'n');
        }
        lastInherent = -1;
      } else if (ch === VISARGA) {
        parts.push(iast ? 'ḥ' : 'h'); lastInherent = -1;
      } else if (ch === AVAGRAHA) {
        parts.push('\''); lastInherent = -1;
      } else if (ch === OM) {
        parts.push(iast ? 'oṃ' : 'om'); syll++; lastInherent = -1;
      } else if (MATRA[ch]) {
        parts.push(MATRA[ch][k]);
      }
    }
    // Word-final schwa deletion for modern languages: "जय" -> jay, "राम" -> ram
    if (lang !== 'sa' && syll > 1 && lastInherent === parts.length - 1) parts.pop();
    return parts.join('');
  }

  var GU_DIGITS = '૦૧૨૩૪૫૬૭૮૯';

  function guToRoman(line, lang, style) {
    var iast = style === 'iast';
    var out = '', word = '';
    function flush() { if (word) { out += romanWord(word, lang, iast); word = ''; } }
    for (var i = 0; i < line.length; i++) {
      var ch = line[i];
      if (isLetter(ch)) { word += ch; continue; }
      flush();
      var d = GU_DIGITS.indexOf(ch);
      if (d >= 0) out += d;
      else if (ch === '।') out += '|';
      else if (ch === '॥') out += '||';
      else out += ch;
    }
    flush();
    if (!iast) out = out.replace(/(^|[\s(|])([a-z])/, function (m, a, b) { return a + b.toUpperCase(); });
    return out;
  }

  var api = { devaToGu: devaToGu, guToDeva: guToDeva, guToRoman: guToRoman };
  if (typeof module !== 'undefined' && module.exports) module.exports = api;
  else root.Translit = api;
})(this);
