package ru.dmdp.tishina

import android.os.Build
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotEquals
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
        // @AndroidEntryPoint rewrites the superclass to a Hilt_-prefixed shim;
        // verifying the superclass keeps the test honest about Hilt being wired.
        val superclassName = MainActivity::class.java.superclass.name
        assertTrue(
            "MainActivity should extend Hilt-generated shim before ComponentActivity, " +
                "but extends $superclassName",
            superclassName.contains("Hilt_MainActivity"),
        )
    }
}
