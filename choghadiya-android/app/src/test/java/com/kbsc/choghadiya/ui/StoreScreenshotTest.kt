package com.kbsc.choghadiya.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.appcompat.app.AppCompatActivity
import androidx.test.core.app.ApplicationProvider
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate

/** Phone screenshots (1080 x 1920) for the Google Play listing, written to app/build/store/. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h640dp-xxhdpi")
class StoreScreenshotTest {
    private val ctx: Context = ApplicationProvider.getApplicationContext()
    private val day = LocalDate.of(2026, 11, 10).toEpochDay()

    private fun <T : AppCompatActivity> shot(cls: Class<T>, gu: Boolean, name: String) {
        Prefs(ctx).gujarati = gu
        val a = Robolectric.buildActivity(cls, Intent(ctx, cls).putExtra(MainActivity.EXTRA_EPOCH_DAY, day)).setup().visible().get()
        val v = a.window.decorView
        v.measure(android.view.View.MeasureSpec.makeMeasureSpec(1080, android.view.View.MeasureSpec.EXACTLY),
            android.view.View.MeasureSpec.makeMeasureSpec(1920, android.view.View.MeasureSpec.EXACTLY))
        v.layout(0, 0, 1080, 1920)
        val bmp = Bitmap.createBitmap(1080, 1920, Bitmap.Config.ARGB_8888)
        bmp.eraseColor(android.graphics.Color.WHITE)
        v.draw(Canvas(bmp))
        val dir = File("build/store").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun shots() {
        shot(MainActivity::class.java, true, "1_choghadiya_gu")
        shot(PanchangActivity::class.java, true, "2_panchang_gu")
        shot(CalendarActivity::class.java, true, "3_calendar_gu")
        shot(FestivalsActivity::class.java, true, "4_festivals_gu")
        shot(MainActivity::class.java, false, "5_choghadiya_en")
        shot(PanchangActivity::class.java, false, "6_panchang_en")
    }
}
