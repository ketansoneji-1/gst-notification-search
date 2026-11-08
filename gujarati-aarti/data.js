/*
 * Aarti Sangrah – content library
 * ------------------------------------------------------------
 * Each entry:
 *   id      : unique, ASCII letters/digits/hyphens (used in the URL hash)
 *   type    : 'aarti' | 'stuti' | 'bhajan' | 'shlok' | 'mantra'
 *   deity   : key from DEITIES below
 *   tag     : optional occasion label (e.g. 'Morning', 'Before meals')
 *   lang    : language of the composition: 'gu' Gujarati, 'hi' Hindi/Awadhi/Braj,
 *             'sa' Sanskrit, 'mr' Marathi (drives the English transliteration rules)
 *   script  : 'deva' (text typed in Devanagari, auto-converted to Gujarati script)
 *             or 'gu' (text typed directly in Gujarati script)
 *   title   : title in the same script as the text
 *   en      : English title
 *   by      : composer / source, if traditionally known
 *   text    : one line per verse line; blank line = new stanza;
 *             a line starting with "#" is a section label (e.g. Doha / Chaupai)
 *   meaning : English meaning / summary
 *
 * Texts follow the commonly printed versions. Wording varies slightly between
 * households, sampradayas and publishers; cross-check with your family pothi
 * or a published Aarti Sangrah where precision matters.
 */

window.DEITIES = {
  ganesh: { en: 'Ganesh', gu: 'ગણેશ' },
  shiv: { en: 'Shiv', gu: 'શિવ' },
  vishnu: { en: 'Vishnu', gu: 'વિષ્ણુ' },
  krishna: { en: 'Krishna', gu: 'કૃષ્ણ' },
  ram: { en: 'Ram', gu: 'રામ' },
  hanuman: { en: 'Hanuman', gu: 'હનુમાન' },
  ambe: { en: 'Ambe / Durga', gu: 'અંબે મા' },
  lakshmi: { en: 'Lakshmi', gu: 'લક્ષ્મી' },
  saraswati: { en: 'Saraswati', gu: 'સરસ્વતી' },
  swaminarayan: { en: 'Swaminarayan', gu: 'સ્વામિનારાયણ' },
  surya: { en: 'Surya & Navagraha', gu: 'સૂર્ય' },
  guru: { en: 'Guru', gu: 'ગુરુ' },
  universal: { en: 'Universal', gu: 'સર્વ' }
};

window.TYPES = {
  aarti: { en: 'Aarti', gu: 'આરતી' },
  stuti: { en: 'Stuti & Chalisa', gu: 'સ્તુતિ' },
  bhajan: { en: 'Bhajan', gu: 'ભજન' },
  shlok: { en: 'Shlok', gu: 'શ્લોક' },
  mantra: { en: 'Mantra', gu: 'મંત્ર' }
};

window.AARTIS = [
  /* ============================ AARTIS ============================ */
  {
    id: 'jay-adhyashakti', type: 'aarti', deity: 'ambe', lang: 'gu', script: 'gu',
    title: 'જય આદ્યાશક્તિ', en: 'Jay Adhyashakti (Ambe Maa Aarti)', by: 'Shivanand Swami',
    text: `જય આદ્યાશક્તિ મા જય આદ્યાશક્તિ
અખંડ બ્રહ્માંડ નિપજાવ્યાં (૨) પડવે પંડિત મા
ૐ જયો જયો મા જગદંબે

દ્વિતીયા બેય સ્વરૂપ શિવશક્તિ જાણું મા (૨)
બ્રહ્મા ગણપતિ ગાઉં (૨) હર ગાઉં હર મા
ૐ જયો જયો મા જગદંબે

તૃતીયા ત્રણ સ્વરૂપ ત્રિભુવનમાં બેઠાં મા (૨)
ત્રયા થકી તરવેણી (૨) તું તરવેણી મા
ૐ જયો જયો મા જગદંબે

ચોથે ચતુરા મહાલક્ષ્મી મા સચરાચર વ્યાપ્યાં મા (૨)
ચાર ભુજા ચૌ દિશા (૨) પ્રગટ્યાં દક્ષિણમાં
ૐ જયો જયો મા જગદંબે

પંચમે પંચ ઋષિ પંચમી ગુણ પદ્મા મા (૨)
પંચ સહસ્ત્ર ત્યાં સોહીએ (૨) પંચે તત્ત્વો મા
ૐ જયો જયો મા જગદંબે

ષષ્ઠી તું નારાયણી મહિષાસુર માર્યો મા (૨)
નરનારીના રૂપે (૨) વ્યાપ્યાં સઘળે મા
ૐ જયો જયો મા જગદંબે

સપ્તમી સપ્ત પાતાળ સંધ્યા સાવિત્રી મા (૨)
ગૌ ગંગા ગાયત્રી (૨) ગૌરી ગીતા મા
ૐ જયો જયો મા જગદંબે

અષ્ટમી અષ્ટ ભુજા આઈ આનંદા મા (૨)
સુરનર મુનિવર જનમ્યા (૨) દેવ દૈત્યો મા
ૐ જયો જયો મા જગદંબે

નવમી નવકુળ નાગ સેવે નવદુર્ગા મા (૨)
નવરાત્રિનાં પૂજન, શિવરાત્રિનાં અર્ચન, કીધાં હર બ્રહ્મા
ૐ જયો જયો મા જગદંબે

દશમી દશ અવતાર જય વિજયાદશમી મા (૨)
રામે રામ રમાડ્યા (૨) રાવણ રોળ્યો મા
ૐ જયો જયો મા જગદંબે

એકાદશી અગિયારસ કાત્યાયની કામા મા (૨)
કામદુર્ગા કાળિકા (૨) શ્યામા ને રામા
ૐ જયો જયો મા જગદંબે

બારસે બાળા રૂપ બહુચરી અંબા મા (૨)
બટુક ભૈરવ સોહીએ, કાળ ભૈરવ સોહીએ, તારા છે તુજ મા
ૐ જયો જયો મા જગદંબે

તેરસે તુળજા રૂપ તમે તારુણી માતા મા (૨)
બ્રહ્મા વિષ્ણુ સદાશિવ (૨) ગુણ તારા ગાતા
ૐ જયો જયો મા જગદંબે

ચૌદશે ચૌદા રૂપ ચંડી ચામુંડા મા (૨)
ભાવ ભક્તિ કંઈ આપો, ચતુરાઈ કંઈ આપો, સિંહવાહિની માતા
ૐ જયો જયો મા જગદંબે

પૂનમે કુંભ ભર્યો સાંભળજો કરુણા મા (૨)
વશિષ્ઠ દેવે વખાણ્યા, માર્કંડ દેવે વખાણ્યા, ગાઈ શુભ કવિતા
ૐ જયો જયો મા જગદંબે

સંવત સોળ સત્તાવન સોળસે બાવીસમાં મા (૨)
સંવત સોળે પ્રગટ્યા (૨) રેવાને તીરે, મા ગંગાને તીરે
ૐ જયો જયો મા જગદંબે

ત્રાંબાવટી નગરી આઈ રૂપાવટી નગરી મા (૨)
સોળ સહસ્ત્ર ત્યાં સોહીએ (૨) ક્ષમા કરો ગૌરી, મા દયા કરો ગૌરી
ૐ જયો જયો મા જગદંબે

શિવશક્તિની આરતી જે કોઈ ગાશે મા (૨)
ભણે શિવાનંદ સ્વામી, સુખ સંપત્તિ થાશે, હર કૈલાસે જાશે, મા અંબા દુઃખ હરશે
ૐ જયો જયો મા જગદંબે

ભાવ ન જાણું ભક્તિ ન જાણું, નવ જાણું સેવા મા (૨)
વલ્લભ ભટ્ટને આપી (૨) એવી ચરણોની સેવા
ૐ જયો જયો મા જગદંબે`,
    meaning: 'The best-known Gujarati aarti, sung every night of Navratri. Each stanza follows a tithi of the lunar fortnight, from Padvo (first day) to Poonam (full moon), and praises one aspect of the Mother: the primordial Shakti who created the universe, the union of Shiv and Shakti, Mahalakshmi, Narayani who slew Mahishasur, Gayatri, Navadurga, Bahuchar, Tulja Bhavani, Chandi-Chamunda and others. The closing stanzas name the poet Shivanand Swami and ask the Mother for devotion and forgiveness.'
  },
  {
    id: 'jay-jagdish-hare', type: 'aarti', deity: 'vishnu', lang: 'hi', script: 'deva',
    title: 'ॐ जय जगदीश हरे', en: 'Om Jai Jagdish Hare', by: 'Pt. Shardha Ram Phillauri',
    text: `ॐ जय जगदीश हरे, स्वामी जय जगदीश हरे
भक्त जनों के संकट, दास जनों के संकट, क्षण में दूर करे
ॐ जय जगदीश हरे

जो ध्यावे फल पावे, दुःख बिनसे मन का, स्वामी दुःख बिनसे मन का
सुख सम्पति घर आवे, कष्ट मिटे तन का
ॐ जय जगदीश हरे

मात पिता तुम मेरे, शरण गहूँ मैं किसकी, स्वामी शरण गहूँ मैं किसकी
तुम बिन और न दूजा, आस करूँ मैं जिसकी
ॐ जय जगदीश हरे

तुम पूरण परमात्मा, तुम अन्तर्यामी, स्वामी तुम अन्तर्यामी
पारब्रह्म परमेश्वर, तुम सब के स्वामी
ॐ जय जगदीश हरे

तुम करुणा के सागर, तुम पालनकर्ता, स्वामी तुम पालनकर्ता
मैं मूरख फलकामी, मैं सेवक तुम स्वामी, कृपा करो भर्ता
ॐ जय जगदीश हरे

तुम हो एक अगोचर, सबके प्राणपति, स्वामी सबके प्राणपति
किस विधि मिलूँ दयामय, तुमको मैं कुमति
ॐ जय जगदीश हरे

दीन बन्धु दुःख हर्ता, ठाकुर तुम मेरे, स्वामी रक्षक तुम मेरे
अपने हाथ उठाओ, अपने शरण लगाओ, द्वार पड़ा तेरे
ॐ जय जगदीश हरे

विषय विकार मिटाओ, पाप हरो देवा, स्वामी पाप हरो देवा
श्रद्धा भक्ति बढ़ाओ, सन्तन की सेवा
ॐ जय जगदीश हरे`,
    meaning: 'A universal aarti to Lord Vishnu (Jagdish, Lord of the Universe), sung at the close of most pujas in Gujarati homes. The devotee says: whoever meditates on You is freed of sorrow; You are my mother and father, the all-pervading Supreme Being, the ocean of compassion and the protector of all. Lift me with Your own hand, remove my desires and sins, and increase my faith, devotion and service to the saints.'
  },
  {
    id: 'jai-ganesh-deva', type: 'aarti', deity: 'ganesh', lang: 'hi', script: 'deva',
    title: 'जय गणेश जय गणेश देवा', en: 'Jai Ganesh Jai Ganesh Deva', by: 'Surdas (attributed)',
    text: `जय गणेश जय गणेश जय गणेश देवा
माता जाकी पार्वती पिता महादेवा

एकदन्त दयावन्त चार भुजा धारी
माथे पर तिलक सोहे मूसे की सवारी

पान चढ़े फूल चढ़े और चढ़े मेवा
लड्डुअन का भोग लगे सन्त करें सेवा

अन्धन को आँख देत कोढ़िन को काया
बाँझन को पुत्र देत निर्धन को माया

सूर श्याम शरण आए सफल कीजे सेवा
माता जाकी पार्वती पिता महादेवा

दीनन की लाज राखो शम्भु सुतवारी
कामना को पूर्ण करो जाऊँ बलिहारी
जय गणेश जय गणेश जय गणेश देवा`,
    meaning: 'Glory to Lord Ganesh, son of Mother Parvati and Mahadev. He has one tusk, is compassionate, has four arms, wears a tilak on His forehead and rides a mouse. Offerings of betel, flowers, dry fruits and laddoos are made to Him. He gives sight to the blind, health to the sick, children to the childless and wealth to the poor. O son of Shambhu, protect the honour of the humble and fulfil our wishes.'
  },
  {
    id: 'sukhkarta-dukhharta', type: 'aarti', deity: 'ganesh', lang: 'mr', script: 'deva',
    title: 'सुखकर्ता दुःखहर्ता', en: 'Sukhkarta Dukhharta (Marathi)', by: 'Sant Ramdas',
    text: `सुखकर्ता दुःखहर्ता वार्ता विघ्नाची
नुरवी पुरवी प्रेम कृपा जयाची
सर्वांगी सुंदर उटी शेंदुराची
कंठी झळके माळ मुक्ताफळांची

जय देव जय देव जय मंगलमूर्ती
दर्शनमात्रे मनकामना पुरती
जय देव जय देव

रत्नखचित फरा तुज गौरीकुमरा
चंदनाची उटी कुंकुमकेशरा
हिरेजडित मुकुट शोभतो बरा
रुणझुणती नूपुरे चरणी घागरिया
जय देव जय देव जय मंगलमूर्ती

लंबोदर पीतांबर फणिवरबंधना
सरळ सोंड वक्रतुंड त्रिनयना
दास रामाचा वाट पाहे सदना
संकटी पावावे निर्वाणी रक्षावे सुरवरवंदना
जय देव जय देव जय मंगलमूर्ती`,
    meaning: 'The Ganesh aarti sung across Maharashtra and Gujarat during Ganeshotsav. Ganesh gives happiness, removes sorrow and leaves no trace of obstacles; His grace showers love. His whole body glows with sindoor paste and a necklace of pearls shines at His throat. Merely seeing Him fulfils the wishes of the heart. O son of Gauri, adorned with jewels, sandal paste, kumkum and a diamond crown, with anklets ringing at Your feet: big-bellied, yellow-robed, three-eyed Lord with a curved trunk, Your servant Ramdas waits for You; come to our aid in times of trouble.'
  },
  {
    id: 'om-jai-shiv-omkara', type: 'aarti', deity: 'shiv', lang: 'hi', script: 'deva',
    title: 'ॐ जय शिव ओंकारा', en: 'Om Jai Shiv Omkara', by: 'Shivanand Swami',
    text: `ॐ जय शिव ओंकारा, स्वामी जय शिव ओंकारा
ब्रह्मा विष्णु सदाशिव, अर्द्धांगी धारा
ॐ जय शिव ओंकारा

एकानन चतुरानन पञ्चानन राजे
हंसासन गरुड़ासन वृषवाहन साजे
ॐ जय शिव ओंकारा

दो भुज चार चतुर्भुज दसभुज अति सोहे
त्रिगुण रूप निरखते त्रिभुवन जन मोहे
ॐ जय शिव ओंकारा

अक्षमाला वनमाला मुण्डमाला धारी
चन्दन मृगमद सोहै भाले शशिधारी
ॐ जय शिव ओंकारा

श्वेताम्बर पीताम्बर बाघम्बर अंगे
सनकादिक गरुड़ादिक भूतादिक संगे
ॐ जय शिव ओंकारा

कर के मध्य कमण्डलु चक्र त्रिशूलधारी
सुखकारी दुखहारी जगपालन कारी
ॐ जय शिव ओंकारा

ब्रह्मा विष्णु सदाशिव जानत अविवेका
प्रणवाक्षर में शोभित ये तीनों एका
ॐ जय शिव ओंकारा

त्रिगुणस्वामी जी की आरती जो कोई नर गावे
कहत शिवानन्द स्वामी मनवांछित फल पावे
ॐ जय शिव ओंकारा`,
    meaning: 'Glory to Shiv, the embodiment of Omkara. Brahma, Vishnu and Sadashiv are His forms, with Parvati as His other half. He appears with one face, four faces or five faces, riding the swan, Garuda or the bull. He wears rosaries of rudraksha, forest flowers and skulls, sandal paste and musk, with the crescent moon on His forehead, and carries the kamandalu, discus and trident. The ignorant see Brahma, Vishnu and Shiv as separate, but in the sacred syllable Om all three are one. Whoever sings this aarti receives the fruit they desire.'
  },
  {
    id: 'jai-ambe-gauri', type: 'aarti', deity: 'ambe', lang: 'hi', script: 'deva',
    title: 'जय अम्बे गौरी', en: 'Jai Ambe Gauri', by: 'Shivanand Swami',
    text: `जय अम्बे गौरी, मैया जय श्यामा गौरी
तुमको निशदिन ध्यावत, हरि ब्रह्मा शिवरी
जय अम्बे गौरी

माँग सिन्दूर विराजत, टीको मृगमद को
उज्ज्वल से दोउ नैना, चन्द्रवदन नीको
जय अम्बे गौरी

कनक समान कलेवर, रक्ताम्बर राजै
रक्तपुष्प गल माला, कण्ठन पर साजै
जय अम्बे गौरी

केहरि वाहन राजत, खड्ग खप्पर धारी
सुर नर मुनिजन सेवत, तिनके दुखहारी
जय अम्बे गौरी

कानन कुण्डल शोभित, नासाग्रे मोती
कोटिक चन्द्र दिवाकर, सम राजत ज्योती
जय अम्बे गौरी

शुम्भ निशुम्भ बिदारे, महिषासुर घाती
धूम्र विलोचन नैना, निशदिन मदमाती
जय अम्बे गौरी

चण्ड मुण्ड संहारे, शोणित बीज हरे
मधु कैटभ दोउ मारे, सुर भयहीन करे
जय अम्बे गौरी

ब्रह्माणी रुद्राणी, तुम कमला रानी
आगम निगम बखानी, तुम शिव पटरानी
जय अम्बे गौरी

चौंसठ योगिनी मंगल गावत, नृत्य करत भैरों
बाजत ताल मृदंगा, अरु बाजत डमरू
जय अम्बे गौरी

तुम ही जग की माता, तुम ही हो भरता
भक्तन की दुःख हरता, सुख सम्पत्ति करता
जय अम्बे गौरी

भुजा चार अति शोभित, वर मुद्रा धारी
मनवांछित फल पावत, सेवत नर नारी
जय अम्बे गौरी

कंचन थाल विराजत, अगर कपूर बाती
श्रीमालकेतु में राजत, कोटि रतन ज्योती
जय अम्बे गौरी

श्री अम्बेजी की आरती, जो कोई नर गावै
कहत शिवानन्द स्वामी, सुख सम्पत्ति पावै
जय अम्बे गौरी`,
    meaning: 'Glory to Mother Ambe, the fair and the dark Gauri, on whom Vishnu, Brahma and Shiv meditate day and night. Sindoor shines in Her hair parting, Her eyes are bright and Her face is like the moon. She rides the lion, holds the sword and skull-bowl, and removes the suffering of gods, humans and sages. She destroyed Shumbh-Nishumbh, Mahishasur, Dhumralochan, Chand-Mund, Raktabeej and Madhu-Kaitabh, freeing the gods from fear. She is Brahmani, Rudrani and Kamala (Lakshmi), the mother and sustainer of the world. Whoever sings this aarti gains happiness and prosperity.'
  },
  {
    id: 'om-jai-lakshmi-mata', type: 'aarti', deity: 'lakshmi', lang: 'hi', script: 'deva',
    title: 'ॐ जय लक्ष्मी माता', en: 'Om Jai Lakshmi Mata',
    text: `ॐ जय लक्ष्मी माता, मैया जय लक्ष्मी माता
तुमको निशदिन सेवत, हरि विष्णु विधाता
ॐ जय लक्ष्मी माता

उमा रमा ब्रह्माणी, तुम ही जग माता
सूर्य चन्द्रमा ध्यावत, नारद ऋषि गाता
ॐ जय लक्ष्मी माता

दुर्गा रूप निरंजनी, सुख सम्पत्ति दाता
जो कोई तुमको ध्यावत, ऋद्धि सिद्धि धन पाता
ॐ जय लक्ष्मी माता

तुम पाताल निवासिनि, तुम ही शुभदाता
कर्म प्रभाव प्रकाशिनी, भवनिधि की त्राता
ॐ जय लक्ष्मी माता

जिस घर में तुम रहतीं, सब सद्गुण आता
सब सम्भव हो जाता, मन नहीं घबराता
ॐ जय लक्ष्मी माता

तुम बिन यज्ञ न होते, वस्त्र न कोई पाता
खान पान का वैभव, सब तुमसे आता
ॐ जय लक्ष्मी माता

शुभ गुण मन्दिर सुन्दर, क्षीरोदधि जाता
रत्न चतुर्दश तुम बिन, कोई नहीं पाता
ॐ जय लक्ष्मी माता

महालक्ष्मीजी की आरती, जो कोई जन गाता
उर आनन्द समाता, पाप उतर जाता
ॐ जय लक्ष्मी माता`,
    meaning: 'Sung on Diwali, Dhanteras and every Friday. Glory to Mother Lakshmi, served day and night by Vishnu and Brahma. You are Uma, Rama and Brahmani, the mother of the world. Whoever meditates on You gains prosperity, success and wealth. Every virtue enters the home where You live; without You no yajna can be performed and no one has food or clothing. Born from the ocean of milk, You are the temple of all good qualities. Whoever sings this aarti is filled with joy and freed from sin.'
  },
  {
    id: 'aarti-kunj-bihari', type: 'aarti', deity: 'krishna', lang: 'hi', script: 'deva',
    title: 'आरती कुंजबिहारी की', en: 'Aarti Kunj Bihari Ki',
    text: `आरती कुंजबिहारी की, श्री गिरिधर कृष्ण मुरारी की

गले में बैजंती माला, बजावै मुरली मधुर बाला
श्रवण में कुण्डल झलकाला, नंद के आनंद नंदलाला
गगन सम अंग कांति काली, राधिका चमक रही आली
लतन में ठाढ़े बनमाली, भ्रमर सी अलक, कस्तूरी तिलक
चंद्र सी झलक, ललित छवि श्यामा प्यारी की
आरती कुंजबिहारी की

कनकमय मोर मुकुट बिलसै, देवता दरसन को तरसैं
गगन सों सुमन रासि बरसै, बजे मुरचंग, मधुर मिरदंग
ग्वालिन संग, अतुल रति गोप कुमारी की
आरती कुंजबिहारी की

जहाँ ते प्रकट भई गंगा, सकल मन हारिणि श्री गंगा
स्मरन ते होत मोह भंगा, बसी शिव सीस, जटा के बीच
हरै अघ कीच, चरन छवि श्रीबनवारी की
आरती कुंजबिहारी की

चमकती उज्ज्वल तट रेनू, बज रही वृंदावन बेनू
चहुँ दिसि गोपि ग्वाल धेनू, हँसत मृदु मंद, चाँदनी चंद
कटत भव फंद, टेर सुन दीन दुखारी की
आरती कुंजबिहारी की`,
    meaning: 'Aarti of Krishna, who roams the groves (kunj) of Vrindavan and lifted Govardhan. He wears the Vaijayanti garland, plays sweet melodies on the flute and wears shining earrings. His dark body glows like the sky, with Radha shining beside Him. The gods long for a glimpse of His golden peacock crown and flowers rain from the sky. From His feet the holy Ganga arose, which Shiv holds in His matted locks and which washes away sin. On the bright sands of the Yamuna, amid gopis, cowherds and cows, His gentle smile cuts the bonds of worldly life. Hear the call of the humble and suffering.'
  },
  {
    id: 'aarti-hanuman-lala', type: 'aarti', deity: 'hanuman', lang: 'hi', script: 'deva',
    title: 'आरती कीजै हनुमान लला की', en: 'Aarti Kije Hanuman Lala Ki',
    text: `आरती कीजै हनुमान लला की
दुष्ट दलन रघुनाथ कला की

जाके बल से गिरिवर काँपे
रोग दोष जाके निकट न झाँके

अंजनि पुत्र महा बलदाई
सन्तन के प्रभु सदा सहाई

दे बीरा रघुनाथ पठाए
लंका जारि सिया सुधि लाए

लंका सो कोट समुद्र सी खाई
जात पवनसुत बार न लाई

लंका जारि असुर संहारे
सियारामजी के काज सँवारे

लक्ष्मण मूर्छित पड़े सकारे
आनि संजीवन प्राण उबारे

पैठि पाताल तोरि जम कारे
अहिरावण की भुजा उखारे

बाएँ भुजा असुरदल मारे
दाहिने भुजा संतजन तारे

सुर नर मुनि आरती उतारें
जय जय जय हनुमान उचारें

कंचन थार कपूर लौ छाई
आरती करत अंजना माई

जो हनुमानजी की आरती गावे
बसि बैकुण्ठ परम पद पावे`,
    meaning: 'Perform the aarti of Hanuman, the destroyer of the wicked and the power of Shri Ram. Mountains tremble at His strength and disease cannot come near Him. The son of Anjani, ever the helper of saints, was sent by Ram, burnt Lanka and brought news of Sita. He brought the Sanjivani herb to revive the unconscious Lakshman, entered the netherworld and uprooted the arms of Ahiravan. Gods, humans and sages perform His aarti; Mother Anjana herself waves the golden plate of camphor. Whoever sings this aarti attains the supreme abode.'
  },
  {
    id: 'jay-sadguru-swami', type: 'aarti', deity: 'swaminarayan', lang: 'gu', script: 'gu',
    title: 'જય સદ્ગુરુ સ્વામી', en: 'Jay Sadguru Swami (Swaminarayan Aarti)', by: 'Muktanand Swami',
    text: `જય સદ્ગુરુ સ્વામી, પ્રભુ જય સદ્ગુરુ સ્વામી
સહજાનંદ દયાળુ (૨) બળવંત બહુનામી
જય સદ્ગુરુ સ્વામી

ચરણ સરોજ તમારાં વંદું કર જોડી, પ્રભુ
ચરણે શીશ ધર્યાથી (૨) દુઃખ નાખ્યાં તોડી
જય સદ્ગુરુ સ્વામી

નારાયણ નરભ્રાતા દ્વિજકુળ તનુ ધારી, પ્રભુ
પામર પતિત ઉદ્ધાર્યાં (૨) અગણિત નરનારી
જય સદ્ગુરુ સ્વામી

નિત્ય નિત્ય નૌતમ લીલા કરતા અવિનાશી, પ્રભુ
અડસઠ તીરથ ચરણે (૨) કોટિ ગયા કાશી
જય સદ્ગુરુ સ્વામી

પુરુષોત્તમ પ્રગટનું જે દર્શન કરશે, પ્રભુ
કાળ કર્મથી છૂટી (૨) કુટુંબ સહિત તરશે
જય સદ્ગુરુ સ્વામી

આ અવસર કરુણાનિધિ કરુણા બહુ કીધી, પ્રભુ
મુક્તાનંદ કહે મુક્તિ (૨) સુગમ કરી સીધી
જય સદ્ગુરુ સ્વામી`,
    meaning: 'The daily aarti of the Swaminarayan Sampraday, composed by Muktanand Swami in 1802. Glory to the true Guru, the compassionate and mighty Sahajanand Swami of many names. I bow with folded hands at Your lotus feet; placing my head there has broken all my sorrows. You are Narayan, brother of Nar, born in a Brahmin family, and You have uplifted countless fallen men and women. The sixty-eight places of pilgrimage and millions of visits to Kashi rest at Your feet. Whoever has darshan of the manifest Purushottam is freed from time and karma along with their family. Muktanand says: in His compassion He has made liberation easy and direct.'
  },

  /* ======================= STUTI & CHALISA ======================== */
  {
    id: 'shri-ramchandra-kripalu', type: 'stuti', deity: 'ram', lang: 'hi', script: 'deva',
    title: 'श्रीरामचन्द्र कृपालु भजु मन', en: 'Shri Ramchandra Kripalu', by: 'Goswami Tulsidas (Vinay Patrika)',
    text: `श्रीरामचन्द्र कृपालु भजु मन हरण भवभय दारुणम्
नवकञ्ज लोचन कञ्ज मुख कर कञ्ज पद कञ्जारुणम्

कन्दर्प अगणित अमित छवि नवनील नीरद सुन्दरम्
पटपीत मानहुँ तडित रुचि शुचि नौमि जनक सुतावरम्

भजु दीनबन्धु दिनेश दानव दैत्यवंश निकन्दनम्
रघुनन्द आनन्दकन्द कोशलचन्द दशरथ नन्दनम्

शिर मुकुट कुण्डल तिलक चारु उदारु अङ्ग विभूषणम्
आजानुभुज शर चापधर सङ्ग्राम जित खरदूषणम्

इति वदति तुलसीदास शङ्कर शेष मुनि मन रञ्जनम्
मम हृदयकञ्ज निवास कुरु कामादि खलदल गञ्जनम्`,
    meaning: 'O mind, worship the compassionate Shri Ramchandra, who removes the terrible fear of worldly existence. His eyes, face, hands and feet are like fresh red lotuses. His beauty surpasses countless Kamadevs; He is lovely like a fresh dark rain-cloud, and His yellow garment shines like lightning. I bow to the husband of Janaki. Worship the friend of the humble, the sun of the Raghu dynasty, the destroyer of demons, the son of Dashrath and the moon of Kosala. He wears a crown, earrings and tilak, has arms reaching to His knees, carries bow and arrows, and defeated Khar and Dushan in battle. Tulsidas prays: You who delight the minds of Shankar, Shesh and the sages, dwell in the lotus of my heart and destroy the army of desire and other vices.'
  },
  {
    id: 'hanuman-chalisa', type: 'stuti', deity: 'hanuman', lang: 'hi', script: 'deva',
    title: 'श्री हनुमान चालीसा', en: 'Shri Hanuman Chalisa', by: 'Goswami Tulsidas',
    text: `# दोहा
श्रीगुरु चरन सरोज रज, निज मनु मुकुरु सुधारि
बरनउँ रघुबर बिमल जसु, जो दायकु फल चारि
बुद्धिहीन तनु जानिके, सुमिरौं पवन कुमार
बल बुधि बिद्या देहु मोहिं, हरहु कलेस बिकार

# चौपाई
जय हनुमान ज्ञान गुन सागर, जय कपीस तिहुँ लोक उजागर
राम दूत अतुलित बल धामा, अंजनि पुत्र पवनसुत नामा
महाबीर बिक्रम बजरंगी, कुमति निवार सुमति के संगी
कंचन बरन बिराज सुबेसा, कानन कुंडल कुंचित केसा
हाथ बज्र औ ध्वजा बिराजै, काँधे मूँज जनेऊ साजै
संकर सुवन केसरीनंदन, तेज प्रताप महा जग बन्दन
बिद्यावान गुनी अति चातुर, राम काज करिबे को आतुर
प्रभु चरित्र सुनिबे को रसिया, राम लखन सीता मन बसिया
सूक्ष्म रूप धरि सियहिं दिखावा, बिकट रूप धरि लंक जरावा
भीम रूप धरि असुर सँहारे, रामचंद्र के काज सँवारे
लाय सजीवन लखन जियाये, श्रीरघुबीर हरषि उर लाये
रघुपति कीन्ही बहुत बड़ाई, तुम मम प्रिय भरतहि सम भाई
सहस बदन तुम्हरो जस गावैं, अस कहि श्रीपति कंठ लगावैं
सनकादिक ब्रह्मादि मुनीसा, नारद सारद सहित अहीसा
जम कुबेर दिगपाल जहाँ ते, कबि कोबिद कहि सके कहाँ ते
तुम उपकार सुग्रीवहिं कीन्हा, राम मिलाय राज पद दीन्हा
तुम्हरो मंत्र बिभीषन माना, लंकेस्वर भए सब जग जाना
जुग सहस्र जोजन पर भानू, लील्यो ताहि मधुर फल जानू
प्रभु मुद्रिका मेलि मुख माहीं, जलधि लाँघि गये अचरज नाहीं
दुर्गम काज जगत के जेते, सुगम अनुग्रह तुम्हरे तेते
राम दुआरे तुम रखवारे, होत न आज्ञा बिनु पैसारे
सब सुख लहै तुम्हारी सरना, तुम रच्छक काहू को डर ना
आपन तेज सम्हारो आपै, तीनों लोक हाँक तें काँपै
भूत पिसाच निकट नहिं आवै, महाबीर जब नाम सुनावै
नासै रोग हरै सब पीरा, जपत निरंतर हनुमत बीरा
संकट तें हनुमान छुड़ावै, मन क्रम बचन ध्यान जो लावै
सब पर राम तपस्वी राजा, तिन के काज सकल तुम साजा
और मनोरथ जो कोई लावै, सोइ अमित जीवन फल पावै
चारों जुग परताप तुम्हारा, है परसिद्ध जगत उजियारा
साधु संत के तुम रखवारे, असुर निकंदन राम दुलारे
अष्ट सिद्धि नौ निधि के दाता, अस बर दीन जानकी माता
राम रसायन तुम्हरे पासा, सदा रहो रघुपति के दासा
तुम्हरे भजन राम को पावै, जनम जनम के दुख बिसरावै
अंत काल रघुबर पुर जाई, जहाँ जन्म हरि भक्त कहाई
और देवता चित्त न धरई, हनुमत सेइ सर्ब सुख करई
संकट कटै मिटै सब पीरा, जो सुमिरै हनुमत बलबीरा
जै जै जै हनुमान गोसाईं, कृपा करहु गुरुदेव की नाईं
जो सत बार पाठ कर कोई, छूटहि बंदि महा सुख होई
जो यह पढ़ै हनुमान चालीसा, होय सिद्धि साखी गौरीसा
तुलसीदास सदा हरि चेरा, कीजै नाथ हृदय महँ डेरा

# दोहा
पवनतनय संकट हरन, मंगल मूरति रूप
राम लखन सीता सहित, हृदय बसहु सुर भूप`,
    meaning: 'Forty verses (chaupais) in praise of Hanuman, recited especially on Tuesdays and Saturdays. Having cleaned the mirror of the mind with the dust of the Guru\'s lotus feet, Tulsidas describes the pure glory of Ram. Knowing himself to be without wisdom, he remembers the Son of the Wind and asks for strength, intelligence and knowledge. The verses recount Hanuman\'s deeds: messenger of Ram, son of Anjani and Kesari, who showed Sita a tiny form and burnt Lanka in a huge one, brought Sanjivani to revive Lakshman, helped Sugriva and Vibhishan, and guards the door of Ram. Ghosts and evil spirits do not come near one who chants His name; He removes disease, pain and crisis, and grants the eight siddhis and nine treasures. The closing doha asks Hanuman to dwell in the heart along with Ram, Lakshman and Sita.'
  },

  /* ============================ BHAJAN ============================ */
  {
    id: 'vaishnav-jan-to', type: 'bhajan', deity: 'universal', lang: 'gu', script: 'gu',
    title: 'વૈષ્ણવ જન તો', en: 'Vaishnav Jan To', by: 'Narsinh Mehta',
    text: `વૈષ્ણવ જન તો તેને કહીએ, જે પીડ પરાઈ જાણે રે
પર દુઃખે ઉપકાર કરે તોયે, મન અભિમાન ન આણે રે

સકળ લોકમાં સહુને વંદે, નિંદા ન કરે કેની રે
વાચ કાછ મન નિશ્ચળ રાખે, ધન ધન જનની તેની રે

સમદૃષ્ટિ ને તૃષ્ણા ત્યાગી, પરસ્ત્રી જેને માત રે
જિહ્વા થકી અસત્ય ન બોલે, પરધન નવ ઝાલે હાથ રે

મોહ માયા વ્યાપે નહિ જેને, દૃઢ વૈરાગ્ય જેના મનમાં રે
રામ નામ શું તાળી રે લાગી, સકળ તીરથ તેના તનમાં રે

વણલોભી ને કપટરહિત છે, કામ ક્રોધ નિવાર્યાં રે
ભણે નરસૈયો તેનું દરશન કરતાં, કુળ એકોતેર તાર્યાં રે`,
    meaning: 'The 15th-century Gujarati bhajan by Adi Kavi Narsinh Mehta, beloved of Mahatma Gandhi. A true devotee (Vaishnav) is one who feels the pain of others, helps those in sorrow and never lets pride enter the mind. They respect everyone, speak ill of no one, and keep speech, action and thought steady; blessed is the mother of such a person. They see all as equal, have given up craving, regard other women as mothers, never speak untruth and never touch the wealth of others. Attachment and illusion do not hold them; they are firm in detachment and absorbed in the name of Ram, so all places of pilgrimage are within them. Free of greed, deceit, lust and anger: Narsinh says that by seeing such a person, seventy-one generations are liberated.'
  },

  /* ============================ SHLOKS ============================ */
  {
    id: 'vakratunda', type: 'shlok', deity: 'ganesh', tag: 'Before any work', lang: 'sa', script: 'deva',
    title: 'वक्रतुण्ड महाकाय', en: 'Vakratunda Mahakaya',
    text: `वक्रतुण्ड महाकाय सूर्यकोटि समप्रभ ।
निर्विघ्नं कुरु मे देव सर्वकार्येषु सर्वदा ॥`,
    meaning: 'O Lord with the curved trunk and mighty body, whose brilliance equals ten million suns: O God, always remove all obstacles from all my undertakings.'
  },
  {
    id: 'gajananam', type: 'shlok', deity: 'ganesh', lang: 'sa', script: 'deva',
    title: 'गजाननं भूतगणादिसेवितं', en: 'Gajananam Bhuta Ganadi Sevitam',
    text: `गजाननं भूतगणादिसेवितं कपित्थजम्बूफलसारभक्षणम् ।
उमासुतं शोकविनाशकारकं नमामि विघ्नेश्वरपादपङ्कजम् ॥`,
    meaning: 'I bow to the lotus feet of Vighneshwar, the elephant-faced Lord served by the bhoot-ganas, who enjoys the essence of wood-apple and jamun fruits, the son of Uma, the destroyer of sorrow.'
  },
  {
    id: 'karpur-gauram', type: 'shlok', deity: 'shiv', tag: 'After aarti', lang: 'sa', script: 'deva',
    title: 'कर्पूरगौरं करुणावतारं', en: 'Karpur Gauram',
    text: `कर्पूरगौरं करुणावतारं संसारसारं भुजगेन्द्रहारम् ।
सदा वसन्तं हृदयारविन्दे भवं भवानीसहितं नमामि ॥`,
    meaning: 'Recited after every aarti. I bow to Shiv, white as camphor, the incarnation of compassion, the essence of the world, who wears the king of serpents as a garland, and who always dwells in the lotus of the heart, together with Bhavani (Parvati).'
  },
  {
    id: 'mahamrityunjaya', type: 'mantra', deity: 'shiv', tag: 'Health & protection', lang: 'sa', script: 'deva',
    title: 'महामृत्युञ्जय मन्त्र', en: 'Mahamrityunjaya Mantra', by: 'Rig Veda 7.59.12',
    text: `ॐ त्र्यम्बकं यजामहे सुगन्धिं पुष्टिवर्धनम् ।
उर्वारुकमिव बन्धनान्मृत्योर्मुक्षीय माऽमृतात् ॥`,
    meaning: 'We worship the three-eyed Lord (Shiv), who is fragrant and who nourishes all beings. As a ripe cucumber is released from its vine, may He free us from death, and not from immortality.'
  },
  {
    id: 'om-namah-shivaya', type: 'mantra', deity: 'shiv', lang: 'sa', script: 'deva',
    title: 'ॐ नमः शिवाय', en: 'Om Namah Shivaya (Panchakshari)',
    text: `ॐ नमः शिवाय ॥`,
    meaning: 'The five-syllable (panchakshari) mantra: "Salutations to Shiv, the auspicious one." Usually chanted 108 times on a rudraksha mala.'
  },
  {
    id: 'shantakaram', type: 'shlok', deity: 'vishnu', lang: 'sa', script: 'deva',
    title: 'शान्ताकारं भुजगशयनं', en: 'Shantakaram Bhujagashayanam',
    text: `शान्ताकारं भुजगशयनं पद्मनाभं सुरेशं
विश्वाधारं गगनसदृशं मेघवर्णं शुभाङ्गम् ।
लक्ष्मीकान्तं कमलनयनं योगिभिर्ध्यानगम्यं
वन्दे विष्णुं भवभयहरं सर्वलोकैकनाथम् ॥`,
    meaning: 'I bow to Vishnu, whose form is peace, who reclines on the serpent Shesh, from whose navel the lotus springs, the lord of the gods, the support of the universe, vast like the sky, dark like a rain-cloud, with auspicious limbs; the beloved of Lakshmi, lotus-eyed, reached by yogis through meditation, the remover of the fear of worldly existence and the one Lord of all worlds.'
  },
  {
    id: 'om-namo-bhagavate', type: 'mantra', deity: 'vishnu', lang: 'sa', script: 'deva',
    title: 'ॐ नमो भगवते वासुदेवाय', en: 'Om Namo Bhagavate Vasudevaya (Dwadashakshari)',
    text: `ॐ नमो भगवते वासुदेवाय ॥`,
    meaning: 'The twelve-syllable mantra of Vishnu / Krishna: "Salutations to Bhagavan Vasudev, the Lord who dwells in all beings."'
  },
  {
    id: 'vasudeva-sutam', type: 'shlok', deity: 'krishna', lang: 'sa', script: 'deva',
    title: 'वसुदेवसुतं देवं', en: 'Vasudeva Sutam Devam',
    text: `वसुदेवसुतं देवं कंसचाणूरमर्दनम् ।
देवकीपरमानन्दं कृष्णं वन्दे जगद्गुरुम् ॥`,
    meaning: 'I bow to Krishna, the divine son of Vasudev, the slayer of Kans and Chanur, the supreme joy of Devaki, and the Guru of the whole world.'
  },
  {
    id: 'hare-krishna', type: 'mantra', deity: 'krishna', lang: 'sa', script: 'deva',
    title: 'हरे राम हरे कृष्ण', en: 'Hare Rama Hare Krishna (Maha Mantra)', by: 'Kali Santarana Upanishad',
    text: `हरे राम हरे राम राम राम हरे हरे ।
हरे कृष्ण हरे कृष्ण कृष्ण कृष्ण हरे हरे ॥`,
    meaning: 'The sixteen-name Maha Mantra calling upon Hari (the remover of sins), Ram and Krishna. The Kali Santarana Upanishad describes it as the means of crossing the difficulties of the Kali Yuga.'
  },
  {
    id: 'ramaya-ramabhadraya', type: 'shlok', deity: 'ram', lang: 'sa', script: 'deva',
    title: 'रामाय रामभद्राय', en: 'Ramaya Ramabhadraya', by: 'Ram Raksha Stotra',
    text: `रामाय रामभद्राय रामचन्द्राय वेधसे ।
रघुनाथाय नाथाय सीतायाः पतये नमः ॥`,
    meaning: 'Salutations to Ram, to the auspicious Ram, to Ramachandra, the creator; to the Lord of the Raghus, the Master, the husband of Sita.'
  },
  {
    id: 'manojavam', type: 'shlok', deity: 'hanuman', lang: 'sa', script: 'deva',
    title: 'मनोजवं मारुततुल्यवेगं', en: 'Manojavam Marutatulya Vegam', by: 'Ram Raksha Stotra',
    text: `मनोजवं मारुततुल्यवेगं जितेन्द्रियं बुद्धिमतां वरिष्ठम् ।
वातात्मजं वानरयूथमुख्यं श्रीरामदूतं शरणं प्रपद्ये ॥`,
    meaning: 'I take refuge in Shri Ram\'s messenger Hanuman, swift as the mind and fast as the wind, master of his senses, foremost among the wise, the son of the Wind God and chief of the monkey army.'
  },
  {
    id: 'ya-devi-sarvabhuteshu', type: 'shlok', deity: 'ambe', tag: 'Navratri', lang: 'sa', script: 'deva',
    title: 'या देवी सर्वभूतेषु', en: 'Ya Devi Sarvabhuteshu', by: 'Durga Saptashati 5',
    text: `या देवी सर्वभूतेषु शक्तिरूपेण संस्थिता ।
नमस्तस्यै नमस्तस्यै नमस्तस्यै नमो नमः ॥

या देवी सर्वभूतेषु मातृरूपेण संस्थिता ।
नमस्तस्यै नमस्तस्यै नमस्तस्यै नमो नमः ॥

या देवी सर्वभूतेषु बुद्धिरूपेण संस्थिता ।
नमस्तस्यै नमस्तस्यै नमस्तस्यै नमो नमः ॥

या देवी सर्वभूतेषु लक्ष्मीरूपेण संस्थिता ।
नमस्तस्यै नमस्तस्यै नमस्तस्यै नमो नमः ॥

या देवी सर्वभूतेषु शान्तिरूपेण संस्थिता ।
नमस्तस्यै नमस्तस्यै नमस्तस्यै नमो नमः ॥`,
    meaning: 'To the Goddess who dwells in all beings in the form of power (shakti), of the mother, of intelligence, of prosperity (Lakshmi) and of peace: salutations to Her, salutations to Her, salutations to Her, again and again.'
  },
  {
    id: 'sarva-mangala', type: 'shlok', deity: 'ambe', lang: 'sa', script: 'deva',
    title: 'सर्वमङ्गलमाङ्गल्ये', en: 'Sarva Mangala Mangalye', by: 'Durga Saptashati 11',
    text: `सर्वमङ्गलमाङ्गल्ये शिवे सर्वार्थसाधिके ।
शरण्ये त्र्यम्बके गौरि नारायणि नमोऽस्तु ते ॥`,
    meaning: 'O auspiciousness of all that is auspicious, O consort of Shiv, who fulfils every aim; O refuge of all, three-eyed Gauri, O Narayani, salutations to You.'
  },
  {
    id: 'namastestu-mahamaye', type: 'shlok', deity: 'lakshmi', tag: 'Diwali', lang: 'sa', script: 'deva',
    title: 'नमस्तेऽस्तु महामाये', en: 'Namastestu Mahamaye', by: 'Mahalakshmi Ashtakam',
    text: `नमस्तेऽस्तु महामाये श्रीपीठे सुरपूजिते ।
शङ्खचक्रगदाहस्ते महालक्ष्मि नमोऽस्तु ते ॥`,
    meaning: 'Salutations to You, O Mahamaya, seated on the throne of prosperity and worshipped by the gods; O Mahalakshmi, holding the conch, discus and mace, salutations to You.'
  },
  {
    id: 'ya-kundendu', type: 'shlok', deity: 'saraswati', tag: 'Before study', lang: 'sa', script: 'deva',
    title: 'या कुन्देन्दुतुषारहारधवला', en: 'Ya Kundendu Tushara Hara Dhavala',
    text: `या कुन्देन्दुतुषारहारधवला या शुभ्रवस्त्रावृता
या वीणावरदण्डमण्डितकरा या श्वेतपद्मासना ।
या ब्रह्माच्युतशङ्करप्रभृतिभिर्देवैः सदा वन्दिता
सा मां पातु सरस्वती भगवती निःशेषजाड्यापहा ॥`,
    meaning: 'May Goddess Saraswati protect me: She who is fair like the jasmine, the moon and a garland of snow, clothed in white, whose hands are adorned with the veena and the boon-giving staff, who sits on a white lotus, who is always worshipped by Brahma, Vishnu, Shankar and the other gods, and who removes every trace of dullness and ignorance.'
  },
  {
    id: 'saraswati-namastubhyam', type: 'shlok', deity: 'saraswati', tag: 'Before study', lang: 'sa', script: 'deva',
    title: 'सरस्वति नमस्तुभ्यं', en: 'Saraswati Namastubhyam',
    text: `सरस्वति नमस्तुभ्यं वरदे कामरूपिणि ।
विद्यारम्भं करिष्यामि सिद्धिर्भवतु मे सदा ॥`,
    meaning: 'Salutations to You, O Saraswati, giver of boons, who takes any form at will. I am about to begin my studies; may I always succeed.'
  },
  {
    id: 'guru-brahma', type: 'shlok', deity: 'guru', tag: 'Guru Purnima', lang: 'sa', script: 'deva',
    title: 'गुरुर्ब्रह्मा गुरुर्विष्णुः', en: 'Gurur Brahma Gurur Vishnu', by: 'Guru Gita',
    text: `गुरुर्ब्रह्मा गुरुर्विष्णुः गुरुर्देवो महेश्वरः ।
गुरुः साक्षात् परब्रह्म तस्मै श्रीगुरवे नमः ॥`,
    meaning: 'The Guru is Brahma, the Guru is Vishnu, the Guru is the god Maheshwar (Shiv). The Guru is verily the Supreme Brahman; salutations to that revered Guru.'
  },
  {
    id: 'gayatri-mantra', type: 'mantra', deity: 'surya', tag: 'Sandhya', lang: 'sa', script: 'deva',
    title: 'गायत्री मन्त्र', en: 'Gayatri Mantra', by: 'Rig Veda 3.62.10',
    text: `ॐ भूर्भुवः स्वः
तत्सवितुर्वरेण्यं भर्गो देवस्य धीमहि
धियो यो नः प्रचोदयात् ॥`,
    meaning: 'Om. Earth, the space between, and heaven. We meditate on the adorable radiance of the divine Savitr (the Sun as creator); may He inspire and illumine our intellect.'
  },
  {
    id: 'japakusuma', type: 'shlok', deity: 'surya', tag: 'Morning', lang: 'sa', script: 'deva',
    title: 'जपाकुसुमसङ्काशं', en: 'Japakusuma Sankasham (Surya)', by: 'Navagraha Stotra (Vyasa)',
    text: `जपाकुसुमसङ्काशं काश्यपेयं महाद्युतिम् ।
तमोऽरिं सर्वपापघ्नं प्रणतोऽस्मि दिवाकरम् ॥`,
    meaning: 'I bow to the Sun, red like the hibiscus flower, the son of Kashyap, of great brilliance, the enemy of darkness and the destroyer of all sins.'
  },
  {
    id: 'navagraha', type: 'shlok', deity: 'surya', lang: 'sa', script: 'deva',
    title: 'ब्रह्मा मुरारिस्त्रिपुरान्तकारी', en: 'Navagraha Shlok',
    text: `ब्रह्मा मुरारिस्त्रिपुरान्तकारी भानुः शशी भूमिसुतो बुधश्च ।
गुरुश्च शुक्रः शनिराहुकेतवः सर्वे ग्रहाः शान्तिकरा भवन्तु ॥`,
    meaning: 'May Brahma, Vishnu (Murari), Shiv (destroyer of Tripura), and the planets Sun, Moon, Mars (son of the Earth), Mercury, Jupiter (Guru), Venus, Saturn, Rahu and Ketu all bring peace.'
  },
  {
    id: 'karagre-vasate', type: 'shlok', deity: 'universal', tag: 'On waking', lang: 'sa', script: 'deva',
    title: 'कराग्रे वसते लक्ष्मीः', en: 'Karagre Vasate Lakshmi',
    text: `कराग्रे वसते लक्ष्मीः करमध्ये सरस्वती ।
करमूले तु गोविन्दः प्रभाते करदर्शनम् ॥`,
    meaning: 'Recited on waking while looking at the palms. Lakshmi dwells at the tips of the fingers, Saraswati in the middle of the palm, and Govind at the base; so one looks at one\'s hands in the morning.'
  },
  {
    id: 'samudra-vasane', type: 'shlok', deity: 'universal', tag: 'On waking', lang: 'sa', script: 'deva',
    title: 'समुद्रवसने देवि', en: 'Samudra Vasane Devi (Bhumi Vandana)',
    text: `समुद्रवसने देवि पर्वतस्तनमण्डले ।
विष्णुपत्नि नमस्तुभ्यं पादस्पर्शं क्षमस्व मे ॥`,
    meaning: 'Recited before placing the feet on the ground in the morning. O Mother Earth, clothed by the oceans, with mountains as Your bosom, consort of Vishnu, I bow to You; forgive me for touching You with my feet.'
  },
  {
    id: 'brahmarpanam', type: 'shlok', deity: 'universal', tag: 'Before meals', lang: 'sa', script: 'deva',
    title: 'ब्रह्मार्पणं ब्रह्म हविः', en: 'Brahmarpanam (Bhojan Mantra)', by: 'Bhagavad Gita 4.24',
    text: `ब्रह्मार्पणं ब्रह्म हविर्ब्रह्माग्नौ ब्रह्मणा हुतम् ।
ब्रह्मैव तेन गन्तव्यं ब्रह्मकर्मसमाधिना ॥`,
    meaning: 'The act of offering is Brahman, the offering itself is Brahman, offered by Brahman into the fire of Brahman. One who sees Brahman in every action reaches Brahman alone.'
  },
  {
    id: 'annapurne', type: 'shlok', deity: 'ambe', tag: 'Before meals', lang: 'sa', script: 'deva',
    title: 'अन्नपूर्णे सदापूर्णे', en: 'Annapurne Sadapurne', by: 'Annapurna Stotram (Adi Shankaracharya)',
    text: `अन्नपूर्णे सदापूर्णे शङ्करप्राणवल्लभे ।
ज्ञानवैराग्यसिद्ध्यर्थं भिक्षां देहि च पार्वति ॥`,
    meaning: 'O Annapurna, ever full, beloved of Shankar\'s life: O Parvati, give me alms so that I may attain knowledge and detachment.'
  },
  {
    id: 'shubham-karoti', type: 'shlok', deity: 'universal', tag: 'Lighting the diya', lang: 'sa', script: 'deva',
    title: 'शुभं करोति कल्याणम्', en: 'Shubham Karoti Kalyanam (Deep Jyoti)',
    text: `शुभं करोति कल्याणमारोग्यं धनसम्पदा ।
शत्रुबुद्धिविनाशाय दीपज्योतिर्नमोऽस्तु ते ॥`,
    meaning: 'Recited when lighting the evening lamp. Salutations to the light of the lamp, which brings auspiciousness, well-being, good health and wealth, and destroys hostile thinking.'
  },
  {
    id: 'kar-charan-kritam', type: 'shlok', deity: 'shiv', tag: 'Bedtime', lang: 'sa', script: 'deva',
    title: 'करचरणकृतं वाक्कायजं', en: 'Kara Charana Kritam (Forgiveness)', by: 'Shiv Aparadha Kshamapana Stotra',
    text: `करचरणकृतं वाक्कायजं कर्मजं वा
श्रवणनयनजं वा मानसं वापराधम् ।
विहितमविहितं वा सर्वमेतत्क्षमस्व
जय जय करुणाब्धे श्रीमहादेव शम्भो ॥`,
    meaning: 'Recited before sleep. Whatever wrongs I have committed with my hands or feet, by speech or body, through action, through my ears or eyes, or in my mind, whether prescribed or forbidden, forgive them all. Glory, glory to You, O ocean of compassion, Shri Mahadev Shambhu.'
  },
  {
    id: 'kshama-prarthana', type: 'shlok', deity: 'universal', tag: 'End of puja', lang: 'sa', script: 'deva',
    title: 'आवाहनं न जानामि', en: 'Avahanam Na Janami (Kshama Prarthana)',
    text: `आवाहनं न जानामि न जानामि विसर्जनम् ।
पूजां चैव न जानामि क्षमस्व परमेश्वर ॥

मन्त्रहीनं क्रियाहीनं भक्तिहीनं सुरेश्वर ।
यत्पूजितं मया देव परिपूर्णं तदस्तु मे ॥`,
    meaning: 'Recited at the end of puja. I do not know how to invoke You, nor how to bid You farewell, nor how to worship You; forgive me, O Supreme Lord. O Lord of the gods, whatever worship I have done, lacking in mantra, ritual or devotion, may it be complete.'
  },
  {
    id: 'tvameva-mata', type: 'shlok', deity: 'universal', tag: 'After aarti', lang: 'sa', script: 'deva',
    title: 'त्वमेव माता च पिता त्वमेव', en: 'Tvameva Mata Cha Pita Tvameva',
    text: `त्वमेव माता च पिता त्वमेव
त्वमेव बन्धुश्च सखा त्वमेव ।
त्वमेव विद्या द्रविणं त्वमेव
त्वमेव सर्वं मम देव देव ॥`,
    meaning: 'You alone are my mother and my father; You alone are my relative and my friend; You alone are my knowledge and my wealth; You are my everything, O God of gods.'
  },
  {
    id: 'sarve-bhavantu', type: 'shlok', deity: 'universal', tag: 'Shanti', lang: 'sa', script: 'deva',
    title: 'सर्वे भवन्तु सुखिनः', en: 'Sarve Bhavantu Sukhinah',
    text: `सर्वे भवन्तु सुखिनः सर्वे सन्तु निरामयाः ।
सर्वे भद्राणि पश्यन्तु मा कश्चिद्दुःखभाग्भवेत् ॥
ॐ शान्तिः शान्तिः शान्तिः ॥`,
    meaning: 'May all be happy; may all be free from illness; may all see what is auspicious; may no one suffer. Om, peace, peace, peace.'
  },
  {
    id: 'asato-ma', type: 'mantra', deity: 'universal', tag: 'Shanti', lang: 'sa', script: 'deva',
    title: 'असतो मा सद्गमय', en: 'Asato Ma Sadgamaya', by: 'Brihadaranyaka Upanishad 1.3.28',
    text: `ॐ असतो मा सद्गमय ।
तमसो मा ज्योतिर्गमय ।
मृत्योर्मा अमृतं गमय ।
ॐ शान्तिः शान्तिः शान्तिः ॥`,
    meaning: 'Lead me from the unreal to the real; lead me from darkness to light; lead me from death to immortality. Om, peace, peace, peace.'
  },
  {
    id: 'saha-navavatu', type: 'mantra', deity: 'guru', tag: 'Shanti / Before study', lang: 'sa', script: 'deva',
    title: 'ॐ सह नाववतु', en: 'Om Saha Navavatu', by: 'Taittiriya Upanishad',
    text: `ॐ सह नाववतु । सह नौ भुनक्तु ।
सह वीर्यं करवावहै ।
तेजस्वि नावधीतमस्तु मा विद्विषावहै ।
ॐ शान्तिः शान्तिः शान्तिः ॥`,
    meaning: 'Recited by teacher and student together. May He protect us both; may He nourish us both; may we work together with energy; may our study be brilliant; may we never be hostile to each other. Om, peace, peace, peace.'
  },
  {
    id: 'purnamadah', type: 'mantra', deity: 'universal', tag: 'Shanti', lang: 'sa', script: 'deva',
    title: 'ॐ पूर्णमदः पूर्णमिदं', en: 'Om Purnamadah Purnamidam', by: 'Isha Upanishad',
    text: `ॐ पूर्णमदः पूर्णमिदं पूर्णात्पूर्णमुदच्यते ।
पूर्णस्य पूर्णमादाय पूर्णमेवावशिष्यते ॥
ॐ शान्तिः शान्तिः शान्तिः ॥`,
    meaning: 'That is whole; this is whole. From the whole, the whole arises. Taking the whole from the whole, the whole alone remains. Om, peace, peace, peace.'
  },
  {
    id: 'gita-2-47', type: 'shlok', deity: 'krishna', tag: 'Bhagavad Gita', lang: 'sa', script: 'deva',
    title: 'कर्मण्येवाधिकारस्ते', en: 'Karmanye Vadhikaraste (Gita 2.47)', by: 'Bhagavad Gita 2.47',
    text: `कर्मण्येवाधिकारस्ते मा फलेषु कदाचन ।
मा कर्मफलहेतुर्भूर्मा ते सङ्गोऽस्त्वकर्मणि ॥`,
    meaning: 'You have a right to your actions alone, never to their fruits. Do not let the fruit of action be your motive, and do not be attached to inaction.'
  },
  {
    id: 'gita-4-7', type: 'shlok', deity: 'krishna', tag: 'Bhagavad Gita', lang: 'sa', script: 'deva',
    title: 'यदा यदा हि धर्मस्य', en: 'Yada Yada Hi Dharmasya (Gita 4.7-8)', by: 'Bhagavad Gita 4.7-4.8',
    text: `यदा यदा हि धर्मस्य ग्लानिर्भवति भारत ।
अभ्युत्थानमधर्मस्य तदात्मानं सृजाम्यहम् ॥

परित्राणाय साधूनां विनाशाय च दुष्कृताम् ।
धर्मसंस्थापनार्थाय सम्भवामि युगे युगे ॥`,
    meaning: 'Whenever there is a decline of dharma and a rise of adharma, O Bharata, I manifest Myself. To protect the good, to destroy the wicked and to establish dharma, I take birth in every age.'
  },
  {
    id: 'gita-2-20', type: 'shlok', deity: 'krishna', tag: 'Bhagavad Gita', lang: 'sa', script: 'deva',
    title: 'न जायते म्रियते वा', en: 'Na Jayate Mriyate Va (Gita 2.20)', by: 'Bhagavad Gita 2.20',
    text: `न जायते म्रियते वा कदाचिन्
नायं भूत्वा भविता वा न भूयः ।
अजो नित्यः शाश्वतोऽयं पुराणो
न हन्यते हन्यमाने शरीरे ॥`,
    meaning: 'The soul is never born and never dies; having come into being, it does not cease to be. It is unborn, eternal, everlasting and ancient; it is not slain when the body is slain.'
  },
  {
    id: 'gita-18-66', type: 'shlok', deity: 'krishna', tag: 'Bhagavad Gita', lang: 'sa', script: 'deva',
    title: 'सर्वधर्मान्परित्यज्य', en: 'Sarva Dharman Parityajya (Gita 18.66)', by: 'Bhagavad Gita 18.66',
    text: `सर्वधर्मान्परित्यज्य मामेकं शरणं व्रज ।
अहं त्वा सर्वपापेभ्यो मोक्षयिष्यामि मा शुचः ॥`,
    meaning: 'Giving up all other duties, take refuge in Me alone. I shall free you from all sins; do not grieve.'
  }
];
