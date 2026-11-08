package com.kbsc.choghadiya.ui

import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.kbsc.choghadiya.R
import com.kbsc.choghadiya.databinding.ActivityFestivalsBinding
import com.kbsc.choghadiya.engine.ChoghadiyaCalculator
import com.kbsc.choghadiya.engine.Festival
import com.kbsc.choghadiya.engine.FestivalCalculator
import com.kbsc.choghadiya.engine.FestivalCategory
import com.kbsc.choghadiya.engine.Names
import com.kbsc.choghadiya.engine.PanchangCalculator
import java.time.LocalDate

/** All festivals, vrats, Ekadashis, Sankrantis and national days of a year, month by month. */
class FestivalsActivity : BaseActivity() {

    private lateinit var b: ActivityFestivalsBinding
    private var year = LocalDate.now().year

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityFestivalsBinding.inflate(layoutInflater)
        setContentView(b.root)
        setSupportActionBar(b.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        year = MainActivity.safeDate(intent.getLongExtra(MainActivity.EXTRA_EPOCH_DAY, LocalDate.now().toEpochDay())).year
        b.prevYear.setOnClickListener { year--; render() }
        b.nextYear.setOnClickListener { year++; render() }
        b.filterGroup.addOnButtonCheckedListener { _, _, checked -> if (checked) render() }
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }

    override fun onResume() {
        super.onResume()
        reloadSettings()
    }

    override fun render() {
        year = year.coerceIn(1901, 2199)
        b.toolbar.title = l.t("તહેવારો અને મહત્ત્વના દિવસો", "Festivals & important days")
        b.yearTitle.text = l.digits(year.toString())
        b.filterMajor.text = l.t("મુખ્ય", "Main")
        b.filterVrat.text = l.t("વ્રત/એકાદશી", "Vrat/Ekadashi")
        b.filterAll.text = l.t("બધા", "All")

        val loc = prefs.location
        val all = FestivalCalculator.forYear(year, loc, prefs.zenith)
            .filter { !(it.category == FestivalCategory.PERIOD && it.name.en.contains("– Day")) }
        val shown = when (b.filterGroup.checkedButtonId) {
            R.id.filterMajor -> all.filter { it.category == FestivalCategory.MAJOR || it.category == FestivalCategory.NATIONAL }
            R.id.filterVrat -> all.filter { it.category == FestivalCategory.VRAT || it.category == FestivalCategory.EKADASHI || it.category == FestivalCategory.JAYANTI }
            else -> all
        }

        b.container.removeAllViews()
        val today = LocalDate.now(loc.zoneId)
        shown.groupBy { it.date.monthValue }.toSortedMap().forEach { (m, list) ->
            b.container.addView(TextView(this).apply {
                text = l.digits(if (prefs.gujarati) "${Names.gregorianMonthGu[m - 1]} $year" else "${Names.gregorianMonthEn[m - 1]} $year")
                textSize = 16f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(ContextCompat.getColor(context, R.color.saffron))
                setPadding(0, dp(14), 0, dp(4))
            })
            list.groupBy { it.date }.forEach { (date, fs) -> b.container.addView(row(date, fs, date == today)) }
        }
        b.container.addView(TextView(this).apply {
            text = l.t(
                "📍 ${loc.name} માટે ગણતરી. તહેવારની તારીખ ગુજરાતી (અમાંત) માસ, તિથિ અને પરંપરાગત નિયમ (ઉદયા તિથિ, મધ્યાહ્ન, અપરાહ્ન, પ્રદોષ, નિશીથ) પરથી નક્કી થાય છે. ઈદ જેવા ચંદ્રદર્શન આધારિત તહેવાર, જૈન તિથિ (પર્યુષણ) અને સરકારી રજાની યાદી માટે સ્થાનિક જાહેરાત જુઓ.",
                "📍 Calculated for ${loc.name}. Dates follow the Gujarati (Amanta) month, the tithi and the traditional rule (tithi at sunrise, midday, afternoon, Pradosh or midnight). Festivals based on moon sighting (e.g. Eid), Jain tithis (Paryushan) and official holiday lists should be checked with local announcements.",
            )
            textSize = 12f
            alpha = 0.75f
            setPadding(0, dp(14), 0, dp(20))
        })
    }

    private fun row(date: LocalDate, fs: List<Festival>, isToday: Boolean): LinearLayout {
        val wd = Names.weekday[ChoghadiyaCalculator.weekdayIndex(date)]
        val lunar = PanchangCalculator.summary(date, prefs.location, prefs.zenith)?.lunar
        val r = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(4), dp(6), dp(4), dp(6))
            if (isToday) setBackgroundColor(ContextCompat.getColor(context, R.color.cell_festival))
            setOnClickListener {
                startActivity(Intent(this@FestivalsActivity, PanchangActivity::class.java).putExtra(MainActivity.EXTRA_EPOCH_DAY, date.toEpochDay()))
            }
        }
        r.addView(TextView(this).apply {
            text = l.digits("${date.dayOfMonth}") + "\n" + (if (prefs.gujarati) wd.gu.take(2) else wd.en.take(3))
            textSize = 14f
            setTypeface(typeface, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(dp(48), ViewGroup.LayoutParams.WRAP_CONTENT)
        })
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        fs.forEach { f ->
            col.addView(TextView(this).apply {
                text = l.n(f.name)
                textSize = 15f
                if (f.category == FestivalCategory.MAJOR) {
                    setTypeface(typeface, Typeface.BOLD)
                    setTextColor(ContextCompat.getColor(context, R.color.saffron_dark))
                }
            })
        }
        if (lunar != null) col.addView(TextView(this).apply {
            text = if (prefs.gujarati) l.digits(lunar.longGu()) else lunar.longEn()
            textSize = 12f
            alpha = 0.7f
        })
        r.addView(col)
        return r
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
