package com.kbsc.choghadiya.ui

import android.os.Bundle
import android.text.InputFilter
import android.text.InputType
import android.view.Menu
import android.view.MenuItem
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.kbsc.choghadiya.R
import com.kbsc.choghadiya.engine.Cities
import com.kbsc.choghadiya.engine.Location
import java.time.ZoneId
import java.util.Locale

/** Common menu (language, place, time format, sunrise method, about) shared by all screens. */
abstract class BaseActivity : AppCompatActivity() {

    protected lateinit var prefs: Prefs
    protected lateinit var l: L

    /** Re-draws the screen after any setting changes. */
    abstract fun render()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        l = L(prefs.gujarati, prefs.use24h)
    }

    protected fun reloadSettings() {
        l = L(prefs.gujarati, prefs.use24h)
        invalidateOptionsMenu()
        render()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        menu.findItem(R.id.action_language).title = if (prefs.gujarati) "English" else "ગુજરાતી"
        menu.findItem(R.id.action_location).title = l.t("સ્થળ બદલો", "Change place")
        menu.findItem(R.id.action_time_format).title = l.t("12 / 24 કલાક સમય", "12 / 24 hour time")
        menu.findItem(R.id.action_convention).title = l.t("સૂર્યોદય પદ્ધતિ", "Sunrise method")
        menu.findItem(R.id.action_about).title = l.t("માહિતી", "About")
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_language -> { prefs.gujarati = !prefs.gujarati; reloadSettings() }
            R.id.action_location -> chooseLocation()
            R.id.action_time_format -> { prefs.use24h = !prefs.use24h; reloadSettings() }
            R.id.action_convention -> chooseConvention()
            R.id.action_about -> AlertDialog.Builder(this)
                .setTitle(l.t("માહિતી", "About"))
                .setMessage(if (prefs.gujarati) getString(R.string.about_gu) else getString(R.string.about_en))
                .setPositiveButton(l.t("બરાબર", "OK"), null)
                .show()
            else -> return super.onOptionsItemSelected(item)
        }
        return true
    }

    protected fun chooseLocation() {
        val custom = l.t("➕ અન્ય સ્થળ (અક્ષાંશ / રેખાંશ)", "➕ Other place (latitude / longitude)")
        val names = arrayOf(custom) + Cities.all.map { it.name }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle(l.t("સ્થળ પસંદ કરો", "Choose place"))
            .setItems(names) { _, which ->
                if (which == 0) customLocation() else { prefs.location = Cities.all[which - 1]; render() }
            }
            .show()
    }

    private fun customLocation() {
        val pad = (16 * resources.displayMetrics.density).toInt()
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(pad, pad / 2, pad, 0) }
        val name = EditText(this).apply {
            hint = l.t("સ્થળનું નામ", "Place name")
            filters = arrayOf(InputFilter.LengthFilter(Prefs.MAX_NAME))
            inputType = InputType.TYPE_CLASS_TEXT
        }
        val numeric = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL or InputType.TYPE_NUMBER_FLAG_SIGNED
        val lat = EditText(this).apply { hint = l.t("અક્ષાંશ (ઉદા. 23.0225)", "Latitude (e.g. 23.0225)"); inputType = numeric; filters = arrayOf(InputFilter.LengthFilter(12)) }
        val lon = EditText(this).apply { hint = l.t("રેખાંશ (ઉદા. 72.5714)", "Longitude (e.g. 72.5714)"); inputType = numeric; filters = arrayOf(InputFilter.LengthFilter(12)) }
        box.addView(name); box.addView(lat); box.addView(lon)
        AlertDialog.Builder(this)
            .setTitle(l.t("અન્ય સ્થળ", "Other place"))
            .setView(box)
            .setPositiveButton(l.t("બરાબર", "OK")) { _, _ ->
                val la = lat.text.toString().trim().toDoubleOrNull()
                val lo = lon.text.toString().trim().toDoubleOrNull()
                if (la == null || lo == null || la.isNaN() || lo.isNaN() || la !in -66.0..66.0 || lo !in -180.0..180.0) {
                    Toast.makeText(this, l.t("અમાન્ય અક્ષાંશ/રેખાંશ (અક્ષાંશ ±66° સુધી)", "Invalid latitude/longitude (latitude up to ±66°)"), Toast.LENGTH_LONG).show()
                } else {
                    val n = name.text.toString().trim().filter { !it.isISOControl() }
                        .ifEmpty { "%.4f, %.4f".format(Locale.US, la, lo) }
                    prefs.location = Location(n, la, lo, ZoneId.systemDefault())
                    render()
                }
            }
            .setNegativeButton(l.t("રદ", "Cancel"), null)
            .show()
    }

    private fun chooseConvention() {
        val options = arrayOf(
            l.t("સૂર્યબિંબની ઉપલી કિનાર + વક્રીભવન (પંચાંગ પ્રમાણભૂત)", "Upper limb + refraction (Panchang standard)"),
            l.t("સૂર્યબિંબનું કેન્દ્ર, વક્રીભવન વગર", "Centre of disc, no refraction"),
        )
        AlertDialog.Builder(this)
            .setTitle(l.t("સૂર્યોદય પદ્ધતિ", "Sunrise method"))
            .setSingleChoiceItems(options, if (prefs.discCentre) 1 else 0) { dlg, which ->
                prefs.discCentre = which == 1
                dlg.dismiss()
                render()
            }
            .show()
    }
}
