package app.aaps.aod

import app.aaps.di.ExternalOptionsOverride
import app.aaps.implementations.ConfigImpl
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock

class AodBuildModeTest {
    @Test fun `custom AOD build preserves the original release feature gates`() {
        val config = ConfigImpl({ mock() }, ExternalOptionsOverride.NONE)
        assertFalse(config.isDev(), "An AOD version suffix must not enable development-only therapy plugins")
        assertTrue(config.isEngineeringModeOrRelease())
    }
}
