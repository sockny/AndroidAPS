package app.aaps.ui.compose.preferences

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.graphics.asAndroidBitmap
import android.graphics.Bitmap
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import app.aaps.core.interfaces.resources.TextRefIdRegistry
import app.aaps.core.keys.AodBooleanKey
import app.aaps.core.keys.AodIntKey
import app.aaps.core.keys.GlucoseNotificationKey
import app.aaps.core.keys.KeysStringIds
import app.aaps.core.keys.interfaces.AppPlatform
import app.aaps.ui.compose.testing.AapsScreenFixture
import app.aaps.ui.compose.testing.setAapsContent
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "en-rGB")
class GlucoseDisplaySettingsScreenTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var fixture: AapsScreenFixture

    @Before fun setup() {
        fixture = AapsScreenFixture()
        whenever(fixture.config.platform).thenReturn(AppPlatform.Android)
        TextRefIdRegistry.register("keys") { KeysStringIds.idOf(it) }
        whenever(fixture.preferences.get(AodBooleanKey.Enabled)).thenReturn(false)
        whenever(fixture.preferences.get(GlucoseNotificationKey.Enabled)).thenReturn(true)
        AodIntKey.entries.forEach { whenever(fixture.preferences.get(it)).thenReturn(it.defaultValue) }
    }

    @Test fun advancedControlsAreOnASeparateTabAndGeneralIsRestored() {
        compose.setAapsContent(fixture) { GlucoseDisplaySettingsScreen {} }
        compose.onNodeWithText("Coloured glucose notification").assertIsDisplayed()
        compose.onNodeWithText("AOD text size").assertDoesNotExist()
        preview("general")
        compose.onNodeWithText("Advanced").performClick()
        compose.onNodeWithText("AOD text size").assertIsDisplayed()
        compose.onNodeWithText("AOD text colour").assertDoesNotExist()
        compose.onNodeWithText("Coloured glucose notification").assertDoesNotExist()
        preview("advanced")
        compose.onNodeWithText("General").performClick()
        compose.onNodeWithText("Coloured glucose notification").assertIsDisplayed()
    }

    @Test fun enablingAodWritesOnlyItsOwnDisplayPreference() {
        compose.setAapsContent(fixture) { GlucoseDisplaySettingsScreen {} }
        compose.onNodeWithText("Always-on display").performClick()
        verify(fixture.preferences).put(AodBooleanKey.Enabled, true)
    }

    private fun preview(name: String) {
        val file = File("build/reports/glucose-display-settings/$name.png")
        requireNotNull(file.parentFile).mkdirs()
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
