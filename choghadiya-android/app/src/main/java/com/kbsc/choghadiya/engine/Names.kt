package com.kbsc.choghadiya.engine

/** A name in both scripts. */
data class Name(val gu: String, val en: String)

private fun names(gu: String, en: String): List<Name> {
    val g = gu.split(",").map { it.trim() }
    val e = en.split(",").map { it.trim() }
    require(g.size == e.size)
    return g.zip(e).map { Name(it.first, it.second) }
}

object Names {
    /** Index 0..14 within a paksha (14 = Purnima in Shukla, Amavasya in Krishna). */
    val tithiGu = listOf("એકમ", "બીજ", "ત્રીજ", "ચોથ", "પાંચમ", "છઠ", "સાતમ", "આઠમ", "નોમ", "દશમ", "અગિયારસ", "બારસ", "તેરસ", "ચૌદસ")
    val tithiEn = listOf(
        "Pratipada", "Dwitiya", "Tritiya", "Chaturthi", "Panchami", "Shashthi", "Saptami",
        "Ashtami", "Navami", "Dashami", "Ekadashi", "Dwadashi", "Trayodashi", "Chaturdashi",
    )

    /** Tithi number 0..29 (0 = Shukla Pratipada, 14 = Purnima, 29 = Amavasya). */
    fun tithi(i: Int): Name = when {
        i == 14 -> Name("પૂનમ", "Purnima")
        i == 29 -> Name("અમાસ", "Amavasya")
        else -> Name(tithiGu[i % 15], tithiEn[i % 15])
    }

    fun paksha(i: Int): Name = if (i < 15) Name("સુદ", "Shukla") else Name("વદ", "Krishna")

    val nakshatra = names(
        "અશ્વિની, ભરણી, કૃત્તિકા, રોહિણી, મૃગશીર્ષ, આર્દ્રા, પુનર્વસુ, પુષ્ય, આશ્લેષા, મઘા, પૂર્વા ફાલ્ગુની, ઉત્તરા ફાલ્ગુની, હસ્ત, ચિત્રા, સ્વાતિ, વિશાખા, અનુરાધા, જ્યેષ્ઠા, મૂળ, પૂર્વાષાઢા, ઉત્તરાષાઢા, શ્રવણ, ધનિષ્ઠા, શતભિષા, પૂર્વા ભાદ્રપદ, ઉત્તરા ભાદ્રપદ, રેવતી",
        "Ashwini, Bharani, Krittika, Rohini, Mrigashira, Ardra, Punarvasu, Pushya, Ashlesha, Magha, Purva Phalguni, Uttara Phalguni, Hasta, Chitra, Swati, Vishakha, Anuradha, Jyeshtha, Mula, Purva Ashadha, Uttara Ashadha, Shravana, Dhanishta, Shatabhisha, Purva Bhadrapada, Uttara Bhadrapada, Revati",
    )

    val yoga = names(
        "વિષ્કુંભ, પ્રીતિ, આયુષ્માન, સૌભાગ્ય, શોભન, અતિગંડ, સુકર્મા, ધૃતિ, શૂળ, ગંડ, વૃદ્ધિ, ધ્રુવ, વ્યાઘાત, હર્ષણ, વજ્ર, સિદ્ધિ, વ્યતિપાત, વરીયાન, પરિઘ, શિવ, સિદ્ધ, સાધ્ય, શુભ, શુક્લ, બ્રહ્મ, ઇન્દ્ર, વૈધૃતિ",
        "Vishkambha, Priti, Ayushman, Saubhagya, Shobhana, Atiganda, Sukarma, Dhriti, Shula, Ganda, Vriddhi, Dhruva, Vyaghata, Harshana, Vajra, Siddhi, Vyatipata, Variyan, Parigha, Shiva, Siddha, Sadhya, Shubha, Shukla, Brahma, Indra, Vaidhriti",
    )

    private val movableKarana = names(
        "બવ, બાલવ, કૌલવ, તૈતિલ, ગર, વણિજ, વિષ્ટિ (ભદ્રા)",
        "Bava, Balava, Kaulava, Taitila, Gara, Vanija, Vishti (Bhadra)",
    )

    /** Karana number 0..59 = half-tithi index within the lunar month. */
    fun karana(k: Int): Name = when (k) {
        0 -> Name("કિંસ્તુઘ્ન", "Kimstughna")
        57 -> Name("શકુનિ", "Shakuni")
        58 -> Name("ચતુષ્પાદ", "Chatushpada")
        59 -> Name("નાગ", "Naga")
        else -> movableKarana[(k - 1) % 7]
    }

    val rashi = names(
        "મેષ, વૃષભ, મિથુન, કર્ક, સિંહ, કન્યા, તુલા, વૃશ્ચિક, ધન, મકર, કુંભ, મીન",
        "Mesha (Aries), Vrishabha (Taurus), Mithuna (Gemini), Karka (Cancer), Simha (Leo), Kanya (Virgo), Tula (Libra), Vrishchika (Scorpio), Dhanu (Sagittarius), Makara (Capricorn), Kumbha (Aquarius), Meena (Pisces)",
    )

    /** Lunar months, index 1 = Chaitra ... 12 = Phalguna. */
    private val monthList = names(
        "ચૈત્ર, વૈશાખ, જેઠ, અષાઢ, શ્રાવણ, ભાદરવો, આસો, કારતક, માગશર, પોષ, મહા, ફાગણ",
        "Chaitra, Vaishakha, Jyeshtha, Ashadha, Shravana, Bhadrapada, Ashwin, Kartik, Margashirsha, Pausha, Magha, Phalguna",
    )

    fun month(index1: Int): Name = monthList[index1 - 1]

    val ritu = names(
        "વસંત, ગ્રીષ્મ, વર્ષા, શરદ, હેમંત, શિશિર",
        "Vasant (Spring), Grishma (Summer), Varsha (Monsoon), Sharad (Autumn), Hemant (Pre-winter), Shishir (Winter)",
    )

    /** Sunday = 0. */
    val weekday = names(
        "રવિવાર, સોમવાર, મંગળવાર, બુધવાર, ગુરુવાર, શુક્રવાર, શનિવાર",
        "Sunday, Monday, Tuesday, Wednesday, Thursday, Friday, Saturday",
    )

    val gregorianMonthGu = listOf(
        "જાન્યુઆરી", "ફેબ્રુઆરી", "માર્ચ", "એપ્રિલ", "મે", "જૂન",
        "જુલાઈ", "ઑગસ્ટ", "સપ્ટેમ્બર", "ઑક્ટોબર", "નવેમ્બર", "ડિસેમ્બર",
    )
    val gregorianMonthEn = listOf(
        "January", "February", "March", "April", "May", "June",
        "July", "August", "September", "October", "November", "December",
    )
}
