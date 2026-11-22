package app.aaps.aod

import android.view.Display
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AodDisplayPolicyTest {
    @Test fun `supports Android ambient and screen-off states without a manufacturer restriction`() {
        listOf(Display.STATE_OFF, Display.STATE_DOZE, Display.STATE_DOZE_SUSPEND, Display.STATE_ON).forEach { state ->
            assertTrue(AodDisplayPolicy.mayShow(true, false, true, state))
        }
    }

    @Test fun `never overlays an interactive unlocked disabled or unknown display`() {
        listOf(Display.STATE_OFF, Display.STATE_DOZE, Display.STATE_DOZE_SUSPEND, Display.STATE_ON).forEach { state ->
            assertFalse(AodDisplayPolicy.mayShow(true, true, true, state))
            assertFalse(AodDisplayPolicy.mayShow(true, false, false, state))
            assertFalse(AodDisplayPolicy.mayShow(false, false, true, state))
        }
        assertFalse(AodDisplayPolicy.mayShow(true, false, true, null))
        assertFalse(AodDisplayPolicy.mayShow(true, false, true, Display.STATE_UNKNOWN))
    }
}
