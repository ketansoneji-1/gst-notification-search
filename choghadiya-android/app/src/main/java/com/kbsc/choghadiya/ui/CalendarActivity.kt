package com.kbsc.choghadiya.ui

import android.content.Intent
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.kbsc.choghadiya.R
import com.kbsc.choghadiya.databinding.ActivityCalendarBinding
import com.kbsc.choghadiya.engine.DaySummary
import com.kbsc.choghadiya.engine.Names
import com.kbsc.choghadiya.engine.PanchangCalculator
import java.time.LocalDate
import java.time.YearMonth

/** Month view: English date with the Gujarati tithi (udaya tithi) of each day. */
class CalendarActivity : BaseActivity() {

    private lateinit var b: ActivityCalendarBinding
    private var month: YearMonth = YearMonth.now()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityCalendarBinding.inflate(layoutInflater)
        setContentView(b.root)
        setSupportActionBar(b.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        month = YearMonth.from(MainActivity.safeDate(intent.getLongExtra(MainActivity.EXTRA_EPOCH_DAY, LocalDate.now().toEpochDay())))
        b.prevMonth.setOnClickListener { month = month.minusMonths(1); render() }
        b.nextMonth.setOnClickListener { month = month.plusMonths(1); render() }
        b.thisMonth.setOnClickListener { month = YearMonth.now(prefs.location.zoneId); render() }
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }

    override fun onResume() {
        super.onResume()
        reloadSettings()
    }

    override fun render() {
        val loc = prefs.location
        val zenith = prefs.zenith
        b.toolbar.title = l.t("ગુજરાતી કેલેન્ડર", "Gujarati Calendar")
        b.thisMonth.text = l.t("ચાલુ મહિનો", "This month")

        val days = (1..month.lengthOfMonth()).map { month.atDay(it) }
        val sums: List<DaySummary?> = days.map { PanchangCalculator.summary(it, loc, zenith) }

        // Title: English month + Gujarati months and Vikram Samvat spanned.
        val lunarMonths = sums.filterNotNull().map { (if (it.lunar.isAdhik) l.t("અધિક ", "Adhik ") else "") + l.n(it.lunar.monthName) }.distinct()
        val vs = sums.filterNotNull().map { it.lunar.vikramSamvat }.distinct()
        val eng = if (prefs.gujarati) "${Names.gregorianMonthGu[month.monthValue - 1]} ${month.year}" else "${Names.gregorianMonthEn[month.monthValue - 1]} ${month.year}"
        b.monthTitle.text = l.digits(eng) + "\n" + lunarMonths.joinToString(" – ") + "  ·  " +
            l.t("વિ.સં. ", "VS ") + l.digits(vs.joinToString("–"))

        b.grid.removeAllViews()
        // Weekday header (Sunday first)
        val header = row()
        for (w in 0..6) header.addView(cellText(if (prefs.gujarati) Names.weekday[w].gu.take(2) else Names.weekday[w].en.take(3), bold = true))
        b.grid.addView(header)

        val today = LocalDate.now(loc.zoneId)
        val lead = month.atDay(1).dayOfWeek.value % 7
        var r = row()
        repeat(lead) { r.addView(cellText("", false)) }
        days.forEachIndexed { i, d ->
            r.addView(dayCell(d, sums[i], d == today))
            if ((lead + i + 1) % 7 == 0) { b.grid.addView(r); r = row() }
        }
        if (r.childCount > 0) {
            while (r.childCount < 7) r.addView(cellText("", false))
            b.grid.addView(r)
        }

        b.legend.text = l.t(
            "ખાનામાં: અંગ્રેજી તારીખ અને સૂર્યોદય સમયની ગુજરાતી તિથિ. 🟠 પૂનમ/અમાસ  🟢 એકાદશી  🔵 સંક્રાંતિ/અન્ય. તારીખ પર ટૅપ કરી સંપૂર્ણ પંચાંગ જુઓ.",
            "Each box: English date and the Gujarati tithi at sunrise. 🟠 Purnima/Amavasya  🟢 Ekadashi  🔵 Sankranti/other. Tap a date for the full Panchang.",
        )

        // List of special days in the month
        b.specialList.removeAllViews()
        sums.filterNotNull().filter { it.festivals.isNotEmpty() }.forEach { s ->
            b.specialList.addView(TextView(this).apply {
                text = l.digits("${s.date.dayOfMonth}") + "  " + (if (prefs.gujarati) s.lunar.longGu() else s.lunar.longEn()) +
                    " — " + s.festivals.joinToString(", ") { l.n(it) }
                textSize = 13f
                setPadding(0, dp(3), 0, dp(3))
                setOnClickListener { openPanchang(s.date) }
            })
        }
    }

    private fun dayCell(d: LocalDate, s: DaySummary?, isToday: Boolean): View {
        val cell = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, dp(64), 1f).apply { setMargins(dp(1), dp(1), dp(1), dp(1)) }
            setPadding(dp(2), dp(2), dp(2), dp(2))
            background = GradientDrawable().apply {
                cornerRadius = dp(6).toFloat()
                setColor(ContextCompat.getColor(context, cellColor(s)))
                if (isToday) setStroke(dp(2), ContextCompat.getColor(context, R.color.current_stroke))
            }
            setOnClickListener { openPanchang(d) }
        }
        cell.addView(TextView(this).apply {
            text = l.digits(d.dayOfMonth.toString())
            textSize = 17f
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
            if (d.dayOfWeek.value == 7) setTextColor(ContextCompat.getColor(context, R.color.bad))
        })
        if (s != null) {
            val ln = s.lunar
            val label = when {
                ln.tithi == 0 -> (if (ln.isAdhik) l.t("અ.", "Ad.") else "") + l.n(ln.monthName)
                ln.tithi == 14 || ln.tithi == 29 -> l.n(ln.tithiName)
                prefs.gujarati -> l.digits("${ln.paksha.gu} ${ln.dayInPaksha}")
                else -> "${if (ln.tithi < 15) "Shu" else "Kri"} ${ln.dayInPaksha}"
            } + (s.kshayaTithi?.let { "/" + l.digits((it % 15 + 1).toString()) } ?: "")
            cell.addView(TextView(this).apply {
                text = label
                textSize = 10.5f
                gravity = Gravity.CENTER
                maxLines = 1
            })
        }
        return cell
    }

    private fun cellColor(s: DaySummary?): Int {
        val t = s?.lunar?.tithi ?: return R.color.cell_plain
        return when {
            t == 14 || t == 29 -> R.color.cell_full_new
            t == 10 || t == 25 || s.kshayaTithi == 10 || s.kshayaTithi == 25 -> R.color.cell_ekadashi
            s.festivals.isNotEmpty() -> R.color.cell_special
            else -> R.color.cell_plain
        }
    }

    private fun openPanchang(d: LocalDate) {
        startActivity(Intent(this, PanchangActivity::class.java).putExtra(MainActivity.EXTRA_EPOCH_DAY, d.toEpochDay()))
    }

    private fun row() = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    private fun cellText(text: String, bold: Boolean) = TextView(this).apply {
        this.text = text
        gravity = Gravity.CENTER
        textSize = 13f
        if (bold) setTypeface(typeface, Typeface.BOLD)
        layoutParams = LinearLayout.LayoutParams(0, if (bold) ViewGroup.LayoutParams.WRAP_CONTENT else dp(64), 1f)
        setPadding(0, dp(4), 0, dp(4))
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
