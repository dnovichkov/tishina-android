package ru.dmdp.tishina

import android.os.Build
import dagger.hilt.internal.GeneratedComponentManagerHolder
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.TIRAMISU])
class MainActivityTest {

    @Test
    fun `MainActivity starts and stays alive`() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()

        assertNotNull(activity)
        assertFalse("Activity should not finish during onCreate", activity.isFinishing)
    }

    @Test
    fun `splash theme is wired through the manifest`() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()

        val activityInfo = activity.packageManager.getActivityInfo(activity.componentName, 0)
        assertNotEquals(
            "Manifest must set android:theme on MainActivity (got default theme id 0)",
            0,
            activityInfo.themeResource,
        )
    }

    @Test
    fun `MainActivity is annotated for Hilt entry`() {
        // @AndroidEntryPoint makes the generated class implement GeneratedComponentManagerHolder
        // — a stable Hilt SPI — instead of relying on the internal Hilt_-prefix naming convention
        // which is not part of the public API and may be renamed across Hilt versions.
        //
        // We upcast to `android.app.Activity` so the static type does not declare the
        // GeneratedComponentManagerHolder relationship; the runtime check is what matters,
        // because Hilt inserts the SPI-bearing superclass via bytecode transformation that
        // is invisible to kotlinc's USELESS_IS_CHECK analysis.
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity: android.app.Activity = controller.get()

        assertTrue(
            "MainActivity must implement GeneratedComponentManagerHolder " +
                "(verifying @AndroidEntryPoint is wired)",
            activity is GeneratedComponentManagerHolder,
        )
        val componentManager = (activity as GeneratedComponentManagerHolder).componentManager()
        assertNotNull(componentManager)
        assertNotNull(componentManager.generatedComponent())
    }
}
