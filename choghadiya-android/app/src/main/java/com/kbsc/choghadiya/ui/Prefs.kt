package com.kbsc.choghadiya.ui

import android.content.Context
import android.content.SharedPreferences
import com.kbsc.choghadiya.engine.Cities
import com.kbsc.choghadiya.engine.Location
import com.kbsc.choghadiya.engine.Name
import com.kbsc.choghadiya.engine.SunCalculator
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * App settings, stored in private SharedPreferences (readable only by this app).
 * Values read back are validated, so a corrupted or tampered file cannot crash the app.
 */
class Prefs(context: Context) {
    private val sp: SharedPreferences = context.applicationContext.getSharedPreferences("choghadiya", Context.MODE_PRIVATE)

    var gujarati: Boolean
        get() = sp.getBoolean(KEY_GU, true)
        set(v) = sp.edit().putBoolean(KEY_GU, v).apply()

    var use24h: Boolean
        get() = sp.getBoolean(KEY_24H, false)
        set(v) = sp.edit().putBoolean(KEY_24H, v).apply()

    var discCentre: Boolean
        get() = sp.getBoolean(KEY_DISC, false)
        set(v) = sp.edit().putBoolean(KEY_DISC, v).apply()

    val zenith: Double get() = if (discCentre) SunCalculator.ZENITH_DISC_CENTRE else SunCalculator.ZENITH_UPPER_LIMB

    var location: Location
        get() {
            val name = sp.getString(KEY_NAME, null)?.take(MAX_NAME) ?: return Cities.default
            val lat = sp.getString(KEY_LAT, null)?.toDoubleOrNull() ?: return Cities.default
            val lon = sp.getString(KEY_LON, null)?.toDoubleOrNull() ?: return Cities.default
            if (lat !in -90.0..90.0 || lon !in -180.0..180.0 || lat.isNaN() || lon.isNaN()) return Cities.default
            val zone = runCatching { ZoneId.of(sp.getString(KEY_ZONE, null)) }.getOrDefault(ZoneId.systemDefault())
            return Location(name, lat, lon, zone)
        }
        set(loc) {
            sp.edit()
                .putString(KEY_NAME, loc.name.take(MAX_NAME))
                .putString(KEY_LAT, loc.latitude.toString())
                .putString(KEY_LON, loc.longitude.toString())
                .putString(KEY_ZONE, loc.zoneId.id)
                .apply()
        }

    companion object {
        const val MAX_NAME = 60
        private const val KEY_GU = "lang_gu"
        private const val KEY_24H = "use_24h"
        private const val KEY_DISC = "disc_centre"
        private const val KEY_NAME = "loc_name"
        private const val KEY_LAT = "loc_lat"
        private const val KEY_LON = "loc_lon"
        private const val KEY_ZONE = "loc_zone"
    }
}

/** Language and formatting helper: everything shown on screen goes through here. */
class L(private val gu: Boolean, private val h24: Boolean) {

    fun t(guText: String, enText: String): String = if (gu) digits(guText) else enText
    fun n(name: Name): String = if (gu) digits(name.gu) else name.en

    /** Converts ASCII digits to Gujarati digits in Gujarati mode. */
    fun digits(s: String): String {
        if (!gu) return s
        val sb = StringBuilder(s.length)
        for (ch in s) sb.append(if (ch in '0'..'9') ('૦' + (ch - '0')) else ch)
        return sb.toString()
    }

    /** Time rounded to the nearest minute; "⁺¹" marks a time on the day after [base]. */
    fun time(z: ZonedDateTime, base: LocalDate? = null): String {
        val r = z.plusSeconds(30).truncatedTo(ChronoUnit.MINUTES)
        val s = r.format(DateTimeFormatter.ofPattern(if (h24) "HH:mm" else "hh:mm a", Locale.ENGLISH))
        val marked = if (base != null && r.toLocalDate().isAfter(base)) "$s ⁺¹" else s
        return digits(marked)
    }

    fun range(a: ZonedDateTime, b: ZonedDateTime, base: LocalDate? = null) = "${time(a, base)} – ${time(b, base)}"

    fun duration(d: Duration): String {
        val m = (d.seconds + 30) / 60
        return if (gu) digits("${m / 60} કલાક ${m % 60} મિનિટ") else "${m / 60}h ${m % 60}m"
    }

    fun gregorian(d: LocalDate, weekday: Name): String =
        if (gu) digits("${d.dayOfMonth} ${com.kbsc.choghadiya.engine.Names.gregorianMonthGu[d.monthValue - 1]} ${d.year}, ${weekday.gu}")
        else "${weekday.en}, ${d.dayOfMonth} ${com.kbsc.choghadiya.engine.Names.gregorianMonthEn[d.monthValue - 1]} ${d.year}"
}
