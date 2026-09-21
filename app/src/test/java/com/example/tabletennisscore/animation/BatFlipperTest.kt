package com.example.tabletennisscore.animation

import android.app.Application
import android.content.Context
import android.widget.ImageView
import androidx.test.core.app.ApplicationProvider
import com.example.tabletennisscore.R
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BatFlipperTest {

    /** An ImageView that remembers which images it was given. */
    private class RecordingImageView(context: Context) : ImageView(context) {
        val shown = mutableListOf<Int>()

        override fun setImageResource(resId: Int) {
            shown.add(resId)
            super.setImageResource(resId)
        }
    }

    private val black = R.drawable.ic_table_tennis_bat_black
    private val red = R.drawable.ic_table_tennis_bat

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext<Application>()
    }

    private fun bat(scaleX: Float = 1f) = RecordingImageView(context).apply { this.scaleX = scaleX }

    private fun letTimePass(millis: Long) {
        shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofMillis(millis))
    }

    @Test
    fun aBatStartsOnItsForehandSideAndNothingIsShownYet() {
        val view = bat()
        BatFlipper(view, black, red)
        assertEquals(1f, view.scaleX, 0f)
        assertEquals(1f, view.scaleY, 0f)
        assertEquals(emptyList<Int>(), view.shown)
    }

    @Test
    fun flippingToBackhandMirrorsTheBatAndShowsTheOtherRubber() {
        val view = bat()
        val flipper = BatFlipper(view, black, red)
        flipper.setBackhand(true)
        letTimePass(500)
        assertEquals(-1f, view.scaleX, 0.001f)
        assertEquals("the handle points up", -1f, view.scaleY, 0.001f)
        assertEquals(listOf(red), view.shown)
    }

    @Test
    fun flippingBackRestoresTheForehandSideAndTheOriginalRubber() {
        val view = bat()
        val flipper = BatFlipper(view, black, red)
        flipper.setBackhand(true)
        letTimePass(500)
        flipper.setBackhand(false)
        letTimePass(500)
        assertEquals(1f, view.scaleX, 0.001f)
        assertEquals(1f, view.scaleY, 0.001f)
        assertEquals(listOf(red, black), view.shown)
    }

    @Test
    fun aMirroredBatFlipsFromItsMirroredStateAndBack() {
        // The right bat is mirrored in the layout so that it faces left.
        val view = bat(scaleX = -1f)
        val flipper = BatFlipper(view, red, black)
        flipper.setBackhand(true)
        letTimePass(500)
        assertEquals(1f, view.scaleX, 0.001f)
        assertEquals(-1f, view.scaleY, 0.001f)
        assertEquals(listOf(black), view.shown)

        flipper.setBackhand(false)
        letTimePass(500)
        assertEquals(-1f, view.scaleX, 0.001f)
        assertEquals(1f, view.scaleY, 0.001f)
        assertEquals(listOf(black, red), view.shown)
    }

    @Test
    fun askingForTheSameSideAgainDoesNothing() {
        val view = bat()
        val flipper = BatFlipper(view, black, red)
        flipper.setBackhand(false)
        letTimePass(500)
        assertEquals(1f, view.scaleX, 0f)
        assertEquals(1f, view.scaleY, 0f)

        flipper.setBackhand(true)
        flipper.setBackhand(true)
        letTimePass(500)
        assertEquals(listOf(red), view.shown)
    }

    @Test
    fun resetPutsTheBatBackOnItsForehandSideAtOnce() {
        val view = bat()
        val flipper = BatFlipper(view, black, red)
        flipper.setBackhand(true)
        letTimePass(500)

        flipper.reset()
        assertEquals(1f, view.scaleX, 0f)
        assertEquals(1f, view.scaleY, 0f)
        assertEquals(listOf(red, black), view.shown)
    }

    @Test
    fun resettingABatThatWasNeverFlippedChangesNothing() {
        val view = bat()
        val flipper = BatFlipper(view, black, red)
        flipper.reset()
        assertEquals(emptyList<Int>(), view.shown)
        assertEquals(1f, view.scaleX, 0f)
        assertEquals(1f, view.scaleY, 0f)
    }
}
