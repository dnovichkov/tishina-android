package ru.dmdp.tishina

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import dagger.hilt.internal.GeneratedComponentManagerHolder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TishinaApplicationTest {

    @Test
    fun `Hilt rewrites TishinaApplication superclass at compile time`() {
        val superclassName = TishinaApplication::class.java.superclass.name
        assertTrue(
            "Expected Hilt-generated superclass, but TishinaApplication extends $superclassName",
            superclassName.contains("Hilt_TishinaApplication"),
        )
    }

    @Test
    fun `manifest wires TishinaApplication as the application class`() {
        val app: Application = ApplicationProvider.getApplicationContext()
        assertNotNull(app)
        assertEquals(TishinaApplication::class.java, app.javaClass)
    }

    @Test
    fun `Hilt SingletonComponent is reachable after onCreate`() {
        val app: Application = ApplicationProvider.getApplicationContext()
        assertTrue(
            "TishinaApplication must implement GeneratedComponentManagerHolder",
            app is GeneratedComponentManagerHolder,
        )
        val componentManager = (app as GeneratedComponentManagerHolder).componentManager()
        assertNotNull(componentManager)
        assertNotNull(componentManager.generatedComponent())
    }
}
