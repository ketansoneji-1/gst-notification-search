#!/usr/bin/env bash
# Downloads the freely licensed recordings listed in recordings.js (Wikimedia Commons)
# into this folder, so build.sh bundles them inside the APK for offline use.
set -euo pipefail
cd "$(dirname "$0")"
UA="AartiSangrahBuilder/1.0 (https://github.com/ketansoneji-1/gst-notification-search)"
get() { [ -s "$1" ] && { echo "have $1"; return; }; echo "get  $1"; curl -fsSL --retry 3 -A "$UA" -o "$1.part" "$2" && mv "$1.part" "$1"; sleep 2; }
get jay-sadguru-swami.ogg 'https://upload.wikimedia.org/wikipedia/commons/3/30/Aarti.ogg'
get gayatri-mantra.ogg 'https://upload.wikimedia.org/wikipedia/commons/2/20/Gayatri_Mantra_as_it_is.ogg'
get mahamrityunjaya.ogg 'https://upload.wikimedia.org/wikipedia/commons/4/45/Mrityunjaya.ogg'
get om-namo-bhagavate.oga 'https://upload.wikimedia.org/wikipedia/commons/1/11/Om_Namo_Bhagavate_Vasudevaya_Pronunciation.oga'
get guru-brahma.ogg 'https://upload.wikimedia.org/wikipedia/commons/f/ff/Sanskrit_Chanting_Guru_Stotram.ogg'
get om-jai-lakshmi-mata.ogg 'https://upload.wikimedia.org/wikipedia/commons/8/8b/Aarti_Puja%2C_Lakshmi_Laxmi.ogg'
get jai-ganesh-deva.ogg 'https://upload.wikimedia.org/wikipedia/commons/2/2d/Aarti_Puja%2C_Ganpati_Vandana.ogg'
get aarti-kunj-bihari.ogg 'https://upload.wikimedia.org/wikipedia/commons/3/3c/Aarti_Shri_Radha_Govind%2C_Musical_Puja_Prayer.ogg'
echo Done. See CREDITS.md for attribution.
