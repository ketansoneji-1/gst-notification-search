package com.kbsc.choghadiya.ui

import android.app.DatePickerDialog
import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.kbsc.choghadiya.R
import com.kbsc.choghadiya.databinding.ActivityMainBinding
import com.kbsc.choghadiya.databinding.ItemSlotBinding
import com.kbsc.choghadiya.engine.ChoghadiyaCalculator
import com.kbsc.choghadiya.engine.Cities
import com.kbsc.choghadiya.engine.DayChoghadiya
import com.kbsc.choghadiya.engine.Location
import com.kbsc.choghadiya.engine.Nature
import com.kbsc.choghadiya.engine.Slot
import com.kbsc.choghadiya.engine.SunCalculator
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var b: ActivityMainBinding
    private lateinit var prefs: SharedPreferences

    private var location: Location = Cities.default
    private var selectedDate: LocalDate = LocalDate.now()
    /** When true the screen follows the running Hindu day (auto-advances at sunrise). */
    private var followToday = true
    private var current: DayChoghadiya? = null

    private val handler = Handler(Looper.getMainLooper())
    private val ticker = object : Runnable {
        override fun run() {
            refresh(scrollToCurrent = false)
            handler.postDelayed(this, 30_000)
        }
    }

    private val gujaratiLocale = Locale("gu", "IN")
    private val gujaratiDate = DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", gujaratiLocale)
    private val englishDate = DateTimeFormatter.ofPattern("EEE, dd MMM yyyy", Locale.ENGLISH)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)
        setSupportActionBar(b.toolbar)

        prefs = getSharedPreferences("choghadiya", Context.MODE_PRIVATE)
        location = loadLocation()

        b.changeLocation.setOnClickListener { chooseLocation() }
        b.locationText.setOnClickListener { chooseLocation() }
        b.prevDay.setOnClickListener { followToday = false; selectedDate = selectedDate.minusDays(1); refresh(true) }
        b.nextDay.setOnClickListener { followToday = false; selectedDate = selectedDate.plusDays(1); refresh(true) }
        b.todayButton.setOnClickListener { followToday = true; refresh(true) }
        b.dateText.setOnClickListener { pickDate() }
    }

    override fun onResume() {
        super.onResume()
        refresh(scrollToCurrent = true)
        handler.postDelayed(ticker, 30_000)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(ticker)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_location -> chooseLocation()
            R.id.action_convention -> chooseConvention()
            R.id.action_time_format -> {
                prefs.edit().putBoolean(KEY_24H, !use24h()).apply()
                refresh(false)
            }
            R.id.action_about -> AlertDialog.Builder(this)
                .setTitle(R.string.menu_about)
                .setMessage(R.string.about_text)
                .setPositiveButton(R.string.ok, null)
                .show()
            else -> return super.onOptionsItemSelected(item)
        }
        return true
    }

    // ---------------------------------------------------------------- rendering

    private fun refresh(scrollToCurrent: Boolean) {
        val now = Instant.now()
        val zenith = zenith()
        if (followToday) {
            selectedDate = ChoghadiyaCalculator.hinduDayFor(now, location, zenith)?.date
                ?: now.atZone(location.zoneId).toLocalDate()
        }
        b.locationText.text = "📍 ${location.name}"
        b.dateText.text = "${selectedDate.format(gujaratiDate)}\n${selectedDate.format(englishDate)}"

        val d = ChoghadiyaCalculator.compute(selectedDate, location, zenith)
        current = d
        b.dayContainer.removeAllViews()
        b.nightContainer.removeAllViews()
        if (d == null) {
            b.sunText.text = getString(R.string.polar)
            b.nowCard.visibility = View.GONE
            return
        }

        b.sunText.text = buildString {
            append("🌅 સૂર્યોદય / Sunrise: ${fmt(d.sunrise)}    🌇 સૂર્યાસ્ત / Sunset: ${fmt(d.sunset)}\n")
            append("⛔ રાહુ કાળ / Rahu Kaal: ${fmt(d.rahuKaalStart)} – ${fmt(d.rahuKaalEnd)}\n")
            append("દિનમાન ${hm(Duration.between(d.sunrise, d.sunset))} · રાત્રિમાન ${hm(Duration.between(d.sunset, d.nextSunrise))}")
        }

        val running = d.slotAt(now)
        var currentView: View? = null
        for (slot in d.daySlots) addSlot(b.dayContainer, slot, d, slot == running)?.let { currentView = it }
        for (slot in d.nightSlots) addSlot(b.nightContainer, slot, d, slot == running)?.let { currentView = it }

        renderNowCard(d, running, now)

        if (scrollToCurrent) {
            val target = currentView
            b.scroll.post {
                if (target != null) {
                    val parent = target.parent as View
                    b.scroll.smoothScrollTo(0, (parent.top + target.top - 400).coerceAtLeast(0))
                } else {
                    b.scroll.scrollTo(0, 0)
                }
            }
        }
    }

    /** Adds one slot row; returns the view if it is the running choghadiya. */
    private fun addSlot(container: LinearLayout, slot: Slot, d: DayChoghadiya, isCurrent: Boolean): View? {
        val row = ItemSlotBinding.inflate(LayoutInflater.from(this), container, false)
        val color = ContextCompat.getColor(this, natureColor(slot.type.nature))
        row.strip.setBackgroundColor(color)
        row.slotIndex.text = slot.index.toString()
        row.slotName.text = "${slot.type.gujarati}  ·  ${slot.type.english}"
        row.slotName.setTextColor(color)
        row.slotNature.text = "${slot.type.nature.gujarati} / ${slot.type.nature.english}" +
            if (isCurrent) "   ●  હાલ ચાલુ / Running now" else ""
        row.slotTime.text = "${fmt(slot.start, d.date)}\n${fmt(slot.end, d.date)}"
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
        if (running == null) {
            b.nowCard.visibility = View.GONE
            return
        }
        b.nowCard.visibility = View.VISIBLE
        val color = ContextCompat.getColor(this, natureColor(running.type.nature))
        b.nowName.text = "${running.type.gujarati} · ${running.type.english}"
        b.nowName.setTextColor(color)
        val remaining = Duration.between(now, running.end.toInstant())
        val nextGood = nextAuspicious(d, now)
        b.nowDetail.text = buildString {
            append("${if (running.isDay) "દિવસ / Day" else "રાત્રિ / Night"} #${running.index}  ·  ")
            append("${fmt(running.start)} – ${fmt(running.end)}\n")
            append("${running.type.nature.gujarati} / ${running.type.nature.english} — ${running.type.suitableForGujarati}\n")
            append("બાકી સમય / Time left: ${hm(remaining)}")
            if (nextGood != null && nextGood != running) {
                append("\nઆગામી શુભ / Next auspicious: ${nextGood.type.gujarati} (${nextGood.type.english}) ")
                append("${fmt(nextGood.start)} – ${fmt(nextGood.end)}")
            }
            val civil = now.atZone(location.zoneId).toLocalDate()
            if (civil != d.date) append("\n(સૂર્યોદય પહેલાં — ગત વારની રાત્રિ ચાલુ / Before sunrise: previous day's night)")
        }
    }

    private fun nextAuspicious(d: DayChoghadiya, now: Instant): Slot? {
        d.allSlots.firstOrNull { it.type.isAuspicious && it.end.toInstant().isAfter(now) }?.let { return it }
        val next = ChoghadiyaCalculator.compute(d.date.plusDays(1), location, zenith()) ?: return null
        return next.allSlots.firstOrNull { it.type.isAuspicious }
    }

    private fun showSlotDetail(slot: Slot, d: DayChoghadiya) {
        val t = slot.type
        val msg = buildString {
            append("${if (slot.isDay) "દિવસ / Day" else "રાત્રિ / Night"} ચોઘડિયું #${slot.index}\n")
            append("સમય / Time: ${fmt(slot.start, d.date)} – ${fmt(slot.end, d.date)}\n")
            append("અવધિ / Duration: ${hm(Duration.between(slot.start, slot.end))}\n\n")
            append("ગ્રહ સ્વામી / Ruling planet: ${t.planetGujarati} (${t.planetEnglish})\n")
            append("ફળ / Nature: ${t.nature.gujarati} (${t.nature.english})\n\n")
            append("${t.suitableForGujarati}\n${t.suitableFor}")
        }
        AlertDialog.Builder(this)
            .setTitle("${t.gujarati} · ${t.english}")
            .setMessage(msg)
            .setPositiveButton(R.string.ok, null)
            .show()
    }

    // ---------------------------------------------------------------- dialogs

    private fun pickDate() {
        DatePickerDialog(this, { _, y, m, day ->
            followToday = false
            selectedDate = LocalDate.of(y, m + 1, day)
            refresh(true)
        }, selectedDate.year, selectedDate.monthValue - 1, selectedDate.dayOfMonth).show()
    }

    private fun chooseLocation() {
        val names = arrayOf(getString(R.string.custom_location)) + Cities.all.map { it.name }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle(R.string.menu_location)
            .setItems(names) { _, which ->
                if (which == 0) customLocation() else setLocation(Cities.all[which - 1])
            }
            .show()
    }

    private fun customLocation() {
        val pad = (16 * resources.displayMetrics.density).toInt()
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(pad, pad / 2, pad, 0) }
        val name = EditText(this).apply { hint = getString(R.string.name_hint) }
        val numeric = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL or InputType.TYPE_NUMBER_FLAG_SIGNED
        val lat = EditText(this).apply { hint = getString(R.string.lat_hint); inputType = numeric }
        val lon = EditText(this).apply { hint = getString(R.string.lon_hint); inputType = numeric }
        box.addView(name); box.addView(lat); box.addView(lon)
        AlertDialog.Builder(this)
            .setTitle(R.string.custom_location)
            .setView(box)
            .setPositiveButton(R.string.ok) { _, _ ->
                val la = lat.text.toString().trim().toDoubleOrNull()
                val lo = lon.text.toString().trim().toDoubleOrNull()
                if (la == null || lo == null || la !in -90.0..90.0 || lo !in -180.0..180.0) {
                    Toast.makeText(this, R.string.invalid_coords, Toast.LENGTH_LONG).show()
                } else {
                    val n = name.text.toString().trim().ifEmpty { "%.4f, %.4f".format(Locale.US, la, lo) }
                    setLocation(Location(n, la, lo, ZoneId.systemDefault()))
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun chooseConvention() {
        val options = arrayOf(
            "સૂર્યબિંબની ઉપલી કિનાર + વક્રીભવન (પંચાંગ પ્રમાણભૂત)\nUpper limb + refraction (standard)",
            "સૂર્યબિંબનું કેન્દ્ર (વક્રીભવન વગર)\nCentre of disc, no refraction",
        )
        val sel = if (zenith() == SunCalculator.ZENITH_DISC_CENTRE) 1 else 0
        AlertDialog.Builder(this)
            .setTitle(R.string.menu_convention)
            .setSingleChoiceItems(options, sel) { dlg, which ->
                prefs.edit().putBoolean(KEY_DISC_CENTRE, which == 1).apply()
                dlg.dismiss()
                refresh(false)
            }
            .show()
    }

    // ---------------------------------------------------------------- persistence / helpers

    private fun setLocation(loc: Location) {
        location = loc
        prefs.edit()
            .putString(KEY_NAME, loc.name)
            .putString(KEY_LAT, loc.latitude.toString())
            .putString(KEY_LON, loc.longitude.toString())
            .putString(KEY_ZONE, loc.zoneId.id)
            .apply()
        refresh(true)
    }

    private fun loadLocation(): Location {
        val name = prefs.getString(KEY_NAME, null) ?: return Cities.default
        val lat = prefs.getString(KEY_LAT, null)?.toDoubleOrNull() ?: return Cities.default
        val lon = prefs.getString(KEY_LON, null)?.toDoubleOrNull() ?: return Cities.default
        val zone = runCatching { ZoneId.of(prefs.getString(KEY_ZONE, null)) }.getOrDefault(ZoneId.systemDefault())
        return Location(name, lat, lon, zone)
    }

    private fun zenith() =
        if (prefs.getBoolean(KEY_DISC_CENTRE, false)) SunCalculator.ZENITH_DISC_CENTRE else SunCalculator.ZENITH_UPPER_LIMB

    private fun use24h() = prefs.getBoolean(KEY_24H, false)

    /** Formats to the nearest minute; marks times that fall on the next civil date. */
    private fun fmt(t: ZonedDateTime, baseDate: LocalDate? = null): String {
        val rounded = t.plusSeconds(30).truncatedTo(ChronoUnit.MINUTES)
        val pattern = if (use24h()) "HH:mm" else "hh:mm a"
        val s = rounded.format(DateTimeFormatter.ofPattern(pattern, Locale.ENGLISH))
        return if (baseDate != null && rounded.toLocalDate().isAfter(baseDate)) "$s ⁺¹" else s
    }

    private fun hm(d: Duration): String {
        val mins = (d.seconds + 30) / 60
        return "${mins / 60} કલાક ${mins % 60} મિનિટ (${mins / 60}h ${mins % 60}m)"
    }

    private fun natureColor(n: Nature) = when (n) {
        Nature.BEST -> R.color.best
        Nature.GOOD -> R.color.good
        Nature.NEUTRAL -> R.color.neutral
        Nature.BAD -> R.color.bad
    }

    private fun natureBg(n: Nature) = when (n) {
        Nature.BEST -> R.color.best_bg
        Nature.GOOD -> R.color.good_bg
        Nature.NEUTRAL -> R.color.neutral_bg
        Nature.BAD -> R.color.bad_bg
    }

    companion object {
        private const val KEY_NAME = "loc_name"
        private const val KEY_LAT = "loc_lat"
        private const val KEY_LON = "loc_lon"
        private const val KEY_ZONE = "loc_zone"
        private const val KEY_DISC_CENTRE = "disc_centre"
        private const val KEY_24H = "use_24h"
    }
}
