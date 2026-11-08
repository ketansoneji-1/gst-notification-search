package com.kbsc.choghadiya.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.widget.ScrollView
import androidx.appcompat.app.AppCompatActivity
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate

/**
 * Launches every screen in Gujarati and English and renders it, so that layout or
 * binding mistakes fail the build. Screenshots go to app/build/screens/.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w400dp-h800dp-xhdpi")
class UiSmokeTest {

    private val ctx: Context = ApplicationProvider.getApplicationContext()
    private val day = LocalDate.of(2026, 11, 10).toEpochDay()

    private fun setLang(gu: Boolean) {
        Prefs(ctx).gujarati = gu
    }

    private fun <T : AppCompatActivity> shoot(cls: Class<T>, name: String) {
        val intent = Intent(ctx, cls).putExtra(MainActivity.EXTRA_EPOCH_DAY, day)
        val activity = Robolectric.buildActivity(cls, intent).setup().get()
        val root = activity.window.decorView
        // Render the whole scrollable content, not only the visible part.
        val scroll = findScroll(root)
        val content = scroll?.getChildAt(0) ?: root
        val w = root.width.takeIf { it > 0 } ?: 800
        content.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        content.layout(0, 0, content.measuredWidth, content.measuredHeight)
        val bmp = Bitmap.createBitmap(content.measuredWidth, content.measuredHeight.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        bmp.eraseColor(android.graphics.Color.WHITE)
        content.draw(Canvas(bmp))
        val dir = File("build/screens").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        assertTrue("$name rendered", content.measuredHeight > 200)
    }

    private fun findScroll(v: View): ScrollView? {
        if (v is ScrollView) return v
        if (v is android.view.ViewGroup) for (i in 0 until v.childCount) findScroll(v.getChildAt(i))?.let { return it }
        return null
    }

    @Test fun choghadiyaGujarati() { setLang(true); shoot(MainActivity::class.java, "choghadiya_gu") }
    @Test fun choghadiyaEnglish() { setLang(false); shoot(MainActivity::class.java, "choghadiya_en") }
    @Test fun panchangGujarati() { setLang(true); shoot(PanchangActivity::class.java, "panchang_gu") }
    @Test fun panchangEnglish() { setLang(false); shoot(PanchangActivity::class.java, "panchang_en") }
    @Test fun calendarGujarati() { setLang(true); shoot(CalendarActivity::class.java, "calendar_gu") }
    @Test fun calendarEnglish() { setLang(false); shoot(CalendarActivity::class.java, "calendar_en") }

    @Test fun malformedIntentExtraDoesNotCrash() {
        val intent = Intent(ctx, MainActivity::class.java).putExtra(MainActivity.EXTRA_EPOCH_DAY, Long.MAX_VALUE)
        Robolectric.buildActivity(MainActivity::class.java, intent).setup()
        val i2 = Intent(ctx, PanchangActivity::class.java).putExtra(MainActivity.EXTRA_EPOCH_DAY, -99999999L)
        Robolectric.buildActivity(PanchangActivity::class.java, i2).setup()
    }
}
