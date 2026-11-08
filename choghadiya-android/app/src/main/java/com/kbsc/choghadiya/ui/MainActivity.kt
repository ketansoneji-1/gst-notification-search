package com.kbsc.choghadiya.ui

import android.app.DatePickerDialog
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import com.kbsc.choghadiya.R
import com.kbsc.choghadiya.databinding.ActivityMainBinding
import com.kbsc.choghadiya.databinding.ItemSlotBinding
import com.kbsc.choghadiya.engine.ChoghadiyaCalculator
import com.kbsc.choghadiya.engine.DayChoghadiya
import com.kbsc.choghadiya.engine.Names
import com.kbsc.choghadiya.engine.Nature
import com.kbsc.choghadiya.engine.PanchangCalculator
import com.kbsc.choghadiya.engine.Slot
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

/** Home screen: day and night Choghadiya for the selected date and place. */
class MainActivity : BaseActivity() {

    private lateinit var b: ActivityMainBinding
    private var selectedDate: LocalDate = LocalDate.now()
    /** When true the screen follows the running Hindu day (auto-advances at sunrise). */
    private var followToday = true
    private var scrollPending = true

    private val handler = Handler(Looper.getMainLooper())
    private val ticker = object : Runnable {
        override fun run() {
            render()
            handler.postDelayed(this, 30_000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)
        setSupportActionBar(b.toolbar)

        intent.getLongExtra(EXTRA_EPOCH_DAY, Long.MIN_VALUE).takeIf { it != Long.MIN_VALUE }?.let {
            selectedDate = safeDate(it); followToday = false
        }

        b.changeLocation.setOnClickListener { chooseLocation() }
        b.locationText.setOnClickListener { chooseLocation() }
        b.prevDay.setOnClickListener { followToday = false; selectedDate = selectedDate.minusDays(1); scrollPending = true; render() }
        b.nextDay.setOnClickListener { followToday = false; selectedDate = selectedDate.plusDays(1); scrollPending = true; render() }
        b.todayButton.setOnClickListener { followToday = true; scrollPending = true; render() }
        b.dateText.setOnClickListener { pickDate() }
        b.panchangButton.setOnClickListener {
            startActivity(Intent(this, PanchangActivity::class.java).putExtra(EXTRA_EPOCH_DAY, selectedDate.toEpochDay()))
        }
        b.calendarButton.setOnClickListener {
            startActivity(Intent(this, CalendarActivity::class.java).putExtra(EXTRA_EPOCH_DAY, selectedDate.toEpochDay()))
        }
        b.festivalsButton.setOnClickListener {
            startActivity(Intent(this, FestivalsActivity::class.java).putExtra(EXTRA_EPOCH_DAY, selectedDate.toEpochDay()))
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.getLongExtra(EXTRA_EPOCH_DAY, Long.MIN_VALUE).takeIf { it != Long.MIN_VALUE }?.let {
            selectedDate = safeDate(it); followToday = false; scrollPending = true
        }
    }

    override fun onResume() {
        super.onResume()
        reloadSettings()
        handler.postDelayed(ticker, 30_000)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(ticker)
    }

    override fun render() {
        val loc = prefs.location
        val zenith = prefs.zenith
        val now = Instant.now()
        if (followToday) {
            selectedDate = ChoghadiyaCalculator.hinduDayFor(now, loc, zenith)?.date ?: now.atZone(loc.zoneId).toLocalDate()
        }
        b.toolbar.title = l.t("ચોઘડિયા", "Choghadiya")
        b.changeLocation.text = l.t("બદલો", "Change")
        b.todayButton.text = l.t("આજે", "Today")
        b.panchangButton.text = l.t("📖 સંપૂર્ણ પંચાંગ", "📖 Full Panchang")
        b.calendarButton.text = l.t("📅 કેલેન્ડર", "📅 Calendar")
        b.festivalsButton.text = l.t("🪔 તહેવારો", "🪔 Festivals")
        b.nowTitle.text = l.t("હાલનું ચોઘડિયું", "Current Choghadiya")
        b.dayTitle.text = l.t("☀ દિવસના ચોઘડિયા", "☀ Day Choghadiya")
        b.nightTitle.text = l.t("☾ રાત્રિના ચોઘડિયા", "☾ Night Choghadiya")
        b.legendText.text = l.t(
            "🟢 અમૃત, શુભ, લાભ — શુભ   🔵 ચલ — સામાન્ય   🔴 ઉદ્વેગ, કાળ, રોગ — અશુભ",
            "🟢 Amrut, Shubh, Labh — auspicious   🔵 Char — neutral   🔴 Udveg, Kaal, Rog — inauspicious",
        )
        b.disclaimerText.text = l.t(
            "દિવસ અને રાત્રિના આઠ સરખા ભાગ પાડી ચોઘડિયા ગણાય છે. સમય પસંદ કરેલા સ્થળ માટે ગણાય છે; છાપેલા પંચાંગ સાથે ±1 મિનિટનો ફેર શક્ય છે.",
            "Day and night are each divided into eight equal parts. Times are for the selected place and may differ from printed panchangs by about ±1 minute.",
        )
        b.locationText.text = "📍 ${loc.name}"

        val summary = PanchangCalculator.summary(selectedDate, loc, zenith)
        val wd = Names.weekday[ChoghadiyaCalculator.weekdayIndex(selectedDate)]
        b.dateText.text = buildString {
            append(l.gregorian(selectedDate, wd))
            if (summary != null) {
                val ln = summary.lunar
                append("\n")
                append(if (prefs.gujarati) l.digits("${ln.longGu()}, વિ.સં. ${ln.vikramSamvat}") else "${ln.longEn()}, VS ${ln.vikramSamvat}")
                val main = summary.festivals.filter { it.category != com.kbsc.choghadiya.engine.FestivalCategory.MONTHLY }
                if (main.isNotEmpty()) append("\n🪔 ").append(main.joinToString(", ") { l.n(it.name) })
            }
        }

        val d = ChoghadiyaCalculator.compute(selectedDate, loc, zenith)
        b.dayContainer.removeAllViews()
        b.nightContainer.removeAllViews()
        if (d == null) {
            b.sunText.text = l.t("આ તારીખે આ સ્થળે સૂર્યોદય/સૂર્યાસ્ત થતો નથી", "No sunrise/sunset at this place on this date")
            b.nowCard.visibility = View.GONE
            return
        }
        b.sunText.text = buildString {
            append(l.t("🌅 સૂર્યોદય ", "🌅 Sunrise ")).append(l.time(d.sunrise))
            append(l.t("   🌇 સૂર્યાસ્ત ", "   🌇 Sunset ")).append(l.time(d.sunset)).append("\n")
            append(l.t("⛔ રાહુ કાળ ", "⛔ Rahu Kaal ")).append(l.range(d.rahuKaalStart, d.rahuKaalEnd))
        }

        val running = d.slotAt(now)
        var currentView: View? = null
        for (s in d.daySlots) addSlot(b.dayContainer, s, d, s == running)?.let { currentView = it }
        for (s in d.nightSlots) addSlot(b.nightContainer, s, d, s == running)?.let { currentView = it }
        renderNowCard(d, running, now)

        if (scrollPending) {
            scrollPending = false
            val target = currentView
            b.scroll.post {
                if (target != null) {
                    val parent = target.parent as View
                    b.scroll.smoothScrollTo(0, (parent.top + target.top - 400).coerceAtLeast(0))
                } else b.scroll.scrollTo(0, 0)
            }
        }
    }

    private fun addSlot(container: LinearLayout, slot: Slot, d: DayChoghadiya, isCurrent: Boolean): View? {
        val row = ItemSlotBinding.inflate(LayoutInflater.from(this), container, false)
        val color = ContextCompat.getColor(this, natureColor(slot.type.nature))
        row.strip.setBackgroundColor(color)
        row.slotIndex.text = l.digits(slot.index.toString())
        row.slotName.text = l.t(slot.type.gujarati, slot.type.english)
        row.slotName.setTextColor(color)
        row.slotNature.text = l.t(slot.type.nature.gujarati, slot.type.nature.english) +
            if (isCurrent) l.t("   ●  હાલ ચાલુ", "   ●  Running now") else ""
        row.slotTime.text = "${l.time(slot.start, d.date)}\n${l.time(slot.end, d.date)}"
        row.slotCard.setCardBackgroundColor(ContextCompat.getColor(this, natureBg(slot.type.nature)))
        if (isCurrent) {
            row.slotCard.strokeWidth = (3 * resources.displayMetrics.density).toInt()
            row.slotCard.strokeColor = ContextCompat.getColor(this, R.color.current_stroke)
        }
        row.root.setOnClickListener { showSlotDetail(slot, d) }
        container.addView(row.root)
        return if (isCurrent) row.root else null
    }

    private fun renderNowCard(d: DayChoghadiya, running: Slot?, now: Instant) {
        if (running == null) { b.nowCard.visibility = View.GONE; return }
        b.nowCard.visibility = View.VISIBLE
        val t = running.type
        b.nowName.text = l.t(t.gujarati, t.english)
        b.nowName.setTextColor(ContextCompat.getColor(this, natureColor(t.nature)))
        val next = nextAuspicious(d, now)
        b.nowDetail.text = buildString {
            append(if (running.isDay) l.t("દિવસ", "Day") else l.t("રાત્રિ", "Night"))
            append(" #").append(l.digits(running.index.toString())).append("  ·  ")
            append(l.range(running.start, running.end)).append("\n")
            append(l.t(t.nature.gujarati, t.nature.english)).append(" — ").append(l.t(t.suitableForGujarati, t.suitableFor)).append("\n")
            append(l.t("બાકી સમય: ", "Time left: ")).append(l.duration(Duration.between(now, running.end.toInstant())))
            if (next != null && next != running) {
                append(l.t("\nઆગામી શુભ ચોઘડિયું: ", "\nNext auspicious: ")).append(l.t(next.type.gujarati, next.type.english))
                append(" ").append(l.range(next.start, next.end))
            }
            if (now.atZone(d.location.zoneId).toLocalDate() != d.date) {
                append(l.t("\n(સૂર્યોદય પહેલાં — ગત વારની રાત્રિ ચાલુ છે)", "\n(Before sunrise: the previous day's night is still running)"))
            }
        }
    }

    private fun nextAuspicious(d: DayChoghadiya, now: Instant): Slot? {
        d.allSlots.firstOrNull { it.type.isAuspicious && it.end.toInstant().isAfter(now) }?.let { return it }
        return ChoghadiyaCalculator.compute(d.date.plusDays(1), d.location, prefs.zenith)?.allSlots?.firstOrNull { it.type.isAuspicious }
    }

    private fun showSlotDetail(slot: Slot, d: DayChoghadiya) {
        val t = slot.type
        val msg = buildString {
            append(if (slot.isDay) l.t("દિવસ", "Day") else l.t("રાત્રિ", "Night"))
            append(l.t(" ચોઘડિયું #", " Choghadiya #")).append(l.digits(slot.index.toString())).append("\n")
            append(l.t("સમય: ", "Time: ")).append(l.range(slot.start, slot.end, d.date)).append("\n")
            append(l.t("અવધિ: ", "Duration: ")).append(l.duration(Duration.between(slot.start, slot.end))).append("\n\n")
            append(l.t("ગ્રહ સ્વામી: ", "Ruling planet: ")).append(l.t(t.planetGujarati, t.planetEnglish)).append("\n")
            append(l.t("ફળ: ", "Nature: ")).append(l.t(t.nature.gujarati, t.nature.english)).append("\n\n")
            append(l.t(t.suitableForGujarati, t.suitableFor))
        }
        AlertDialog.Builder(this).setTitle(l.t(t.gujarati, t.english)).setMessage(msg)
            .setPositiveButton(l.t("બરાબર", "OK"), null).show()
    }

    private fun pickDate() {
        DatePickerDialog(this, { _, y, m, day ->
            followToday = false
            selectedDate = LocalDate.of(y, m + 1, day)
            scrollPending = true
            render()
        }, selectedDate.year, selectedDate.monthValue - 1, selectedDate.dayOfMonth).show()
    }

    companion object {
        const val EXTRA_EPOCH_DAY = "epoch_day"

        /** Accepts only dates in 1900-2200 so a malformed intent extra cannot crash the app. */
        fun safeDate(epochDay: Long): LocalDate {
            val min = LocalDate.of(1900, 1, 1).toEpochDay()
            val max = LocalDate.of(2200, 12, 31).toEpochDay()
            return if (epochDay in min..max) LocalDate.ofEpochDay(epochDay) else LocalDate.now()
        }

        fun natureColor(n: Nature) = when (n) {
            Nature.BEST -> R.color.best
            Nature.GOOD -> R.color.good
            Nature.NEUTRAL -> R.color.neutral
            Nature.BAD -> R.color.bad
        }

        fun natureBg(n: Nature) = when (n) {
            Nature.BEST -> R.color.best_bg
            Nature.GOOD -> R.color.good_bg
            Nature.NEUTRAL -> R.color.neutral_bg
            Nature.BAD -> R.color.bad_bg
        }
    }
}
