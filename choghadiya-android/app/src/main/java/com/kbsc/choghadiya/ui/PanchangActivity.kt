package com.kbsc.choghadiya.ui

import android.app.DatePickerDialog
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.google.android.material.card.MaterialCardView
import com.kbsc.choghadiya.R
import com.kbsc.choghadiya.databinding.ActivityPanchangBinding
import com.kbsc.choghadiya.engine.Panchang
import com.kbsc.choghadiya.engine.PanchangCalculator
import com.kbsc.choghadiya.engine.Period
import java.time.Duration
import java.time.LocalDate

/** Full Panchang for one day: Gujarati + English date, five limbs, Sun/Moon and muhurat. */
class PanchangActivity : BaseActivity() {

    private lateinit var b: ActivityPanchangBinding
    private var date: LocalDate = LocalDate.now()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityPanchangBinding.inflate(layoutInflater)
        setContentView(b.root)
        setSupportActionBar(b.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        date = MainActivity.safeDate(intent.getLongExtra(MainActivity.EXTRA_EPOCH_DAY, LocalDate.now().toEpochDay()))

        b.prevDay.setOnClickListener { date = date.minusDays(1); render() }
        b.nextDay.setOnClickListener { date = date.plusDays(1); render() }
        b.todayButton.setOnClickListener { date = LocalDate.now(prefs.location.zoneId); render() }
        b.dateText.setOnClickListener {
            DatePickerDialog(this, { _, y, m, d -> date = LocalDate.of(y, m + 1, d); render() },
                date.year, date.monthValue - 1, date.dayOfMonth).show()
        }
        b.choghadiyaButton.setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(MainActivity.EXTRA_EPOCH_DAY, date.toEpochDay()))
        }
        b.calendarButton.setOnClickListener {
            startActivity(Intent(this, CalendarActivity::class.java).putExtra(MainActivity.EXTRA_EPOCH_DAY, date.toEpochDay()))
        }
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }

    override fun onResume() {
        super.onResume()
        reloadSettings()
    }

    override fun render() {
        b.toolbar.title = l.t("પંચાંગ", "Panchang")
        b.todayButton.text = l.t("આજે", "Today")
        b.choghadiyaButton.text = l.t("ચોઘડિયા", "Choghadiya")
        b.calendarButton.text = l.t("કેલેન્ડર", "Calendar")
        b.container.removeAllViews()

        val loc = prefs.location
        val p = PanchangCalculator.compute(date, loc, prefs.zenith)
        b.dateText.text = l.gregorian(date, com.kbsc.choghadiya.engine.Names.weekday[date.dayOfWeek.value % 7])
        if (p == null) {
            card(l.t("માહિતી", "Info")).add(l.t("સ્થિતિ", "Status"), l.t("આ તારીખે આ સ્થળે સૂર્યોદય/સૂર્યાસ્ત થતો નથી", "No sunrise/sunset at this place on this date"))
            return
        }
        val ln = p.lunar
        val gu = prefs.gujarati

        // ---- Header: Gujarati date
        val head = card(null)
        head.big(if (gu) ln.longGu() else ln.longEn())
        head.add(l.t("અંગ્રેજી તારીખ", "English date"), l.gregorian(p.date, p.weekday))
        head.add(l.t("વિક્રમ સંવત", "Vikram Samvat"), l.digits(ln.vikramSamvat.toString()) + l.t(" (ગુજરાતી, કારતકાદિ)", " (Gujarati, Kartik-based)"))
        head.add(l.t("શક સંવત", "Shaka Samvat"), l.digits(ln.shakaSamvat.toString()))
        head.add(l.t("ગુજરાતી માસ", "Gujarati month"), (if (ln.isAdhik) l.t("અધિક ", "Adhik ") else "") + l.n(ln.monthName) + l.t(" (અમાંત)", " (Amanta)"))
        head.add(l.t("પક્ષ", "Paksha"), l.n(ln.paksha) + l.t(" પક્ષ", " Paksha"))
        head.add(l.t("ઋતુ", "Ritu"), l.n(ln.ritu))
        head.add(l.t("અયન", "Ayana"), l.n(p.ayana))
        if (p.festivals.isNotEmpty()) head.add(l.t("વિશેષ", "Special"), p.festivals.joinToString(", ") { l.n(it) }, R.color.saffron_dark)

        // ---- Five limbs
        val pa = card(l.t("પંચાંગના પાંચ અંગ", "Five limbs of the Panchang"))
        pa.add(l.t("વાર", "Vaar"), l.n(p.weekday))
        pa.add(l.t("તિથિ", "Tithi"), periods(p.tithis, p) { l.n(com.kbsc.choghadiya.engine.Names.paksha(it.index)) + " " + l.n(it.name) })
        pa.add(l.t("નક્ષત્ર", "Nakshatra"), periods(p.nakshatras, p) { l.n(it.name) })
        pa.add(l.t("યોગ", "Yoga"), periods(p.yogas, p) { l.n(it.name) })
        pa.add(l.t("કરણ", "Karana"), periods(p.karanas, p) { l.n(it.name) })

        // ---- Sun & Moon
        val sm = card(l.t("સૂર્ય અને ચંદ્ર", "Sun and Moon"))
        sm.add(l.t("સૂર્યોદય", "Sunrise"), l.time(p.sunrise))
        sm.add(l.t("સૂર્યાસ્ત", "Sunset"), l.time(p.sunset))
        sm.add(l.t("ચંદ્રોદય", "Moonrise"), p.moonrise?.let { l.time(it) } ?: l.t("આ દિવસે નથી", "None on this day"))
        sm.add(l.t("ચંદ્રાસ્ત", "Moonset"), p.moonset?.let { l.time(it) } ?: l.t("આ દિવસે નથી", "None on this day"))
        sm.add(l.t("દિનમાન", "Day length"), l.duration(Duration.between(p.sunrise, p.sunset)))
        sm.add(l.t("રાત્રિમાન", "Night length"), l.duration(Duration.between(p.sunset, p.nextSunrise)))
        sm.add(l.t("સૂર્ય રાશિ", "Sun sign"), l.n(p.sunRashi) + (p.sankranti?.let {
            l.t("  → ${it.name.gu} સંક્રાંતિ ${l.time(it.end, p.date)}", "  → enters ${it.name.en} at ${l.time(it.end, p.date)}")
        } ?: ""))
        sm.add(l.t("ચંદ્ર રાશિ", "Moon sign"), periods(p.moonRashis, p) { l.n(it.name) })

        // ---- Muhurat
        val mu = card(l.t("શુભ / અશુભ સમય", "Auspicious / inauspicious times"))
        mu.add(l.t("અભિજિત મુહૂર્ત", "Abhijit Muhurat"),
            p.abhijit?.let { l.range(it.start, it.end) } ?: l.t("બુધવારે ગ્રાહ્ય નથી", "Not observed on Wednesday"), R.color.good)
        mu.add(l.t("રાહુ કાળ", "Rahu Kaal"), l.range(p.rahuKaal.start, p.rahuKaal.end), R.color.bad)
        mu.add(l.t("યમઘંટ (યમગંડ)", "Yamaganda"), l.range(p.yamaganda.start, p.yamaganda.end), R.color.bad)
        mu.add(l.t("ગુલિક કાળ", "Gulika Kaal"), l.range(p.gulikaKaal.start, p.gulikaKaal.end), R.color.bad)

        val note = TextView(this).apply {
            text = l.t(
                "📍 ${loc.name}. હિંદુ દિવસ સૂર્યોદયથી બીજા સૂર્યોદય સુધી; ⁺¹ = બીજા દિવસનો સમય. અયનાંશ: લાહિરી. ગુજરાતી પદ્ધતિ મુજબ અમાંત માસ.",
                "📍 ${loc.name}. The Hindu day runs from sunrise to the next sunrise; ⁺¹ = time on the next day. Ayanamsa: Lahiri. Months are Amanta, as in Gujarat.",
            )
            textSize = 12f
            alpha = 0.75f
            setPadding(0, dp(10), 0, dp(20))
        }
        b.container.addView(note)
    }

    /** "Name upto hh:mm → Next upto hh:mm" for the elements spanning this Hindu day. */
    private fun periods(list: List<Period>, p: Panchang, label: (Period) -> String): String =
        list.joinToString("\n") { per ->
            val end = l.time(per.end, p.date)
            l.t("${label(per)} — $end સુધી", "${label(per)} upto $end")
        }

    // ---------------------------------------------------------------- tiny view builders

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private inner class Card(val body: LinearLayout) {
        fun big(text: String) {
            body.addView(TextView(this@PanchangActivity).apply {
                this.text = text
                textSize = 22f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(ContextCompat.getColor(context, R.color.saffron_dark))
                gravity = Gravity.CENTER
                setPadding(0, 0, 0, dp(6))
            })
        }

        fun add(label: String, value: String, colorRes: Int? = null) {
            val row = LinearLayout(this@PanchangActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, dp(4), 0, dp(4))
            }
            row.addView(TextView(this@PanchangActivity).apply {
                text = label
                textSize = 14f
                setTypeface(typeface, Typeface.BOLD)
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.38f)
            })
            row.addView(TextView(this@PanchangActivity).apply {
                text = value
                textSize = 14f
                colorRes?.let { setTextColor(ContextCompat.getColor(context, it)) }
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.62f)
            })
            body.addView(row)
        }
    }

    private fun card(title: String?): Card {
        val cv = MaterialCardView(this).apply {
            radius = dp(12).toFloat()
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                .apply { topMargin = dp(10) }
        }
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(12))
        }
        if (title != null) body.addView(TextView(this).apply {
            text = title
            textSize = 16f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(ContextCompat.getColor(context, R.color.saffron))
            setPadding(0, 0, 0, dp(6))
        })
        cv.addView(body)
        b.container.addView(cv)
        return Card(body)
    }
}
