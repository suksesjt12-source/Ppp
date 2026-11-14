package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.stormrevolution.launcher.StormApplication
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `app context resolves Storm Launcher name and container`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Storm Launcher", appName)
        val app = context as? StormApplication
        assertNotNull(app?.container)
    }
}
