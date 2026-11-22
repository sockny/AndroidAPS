package app.aaps.aod

import app.aaps.core.interfaces.iob.IobCobCalculator
import app.aaps.core.interfaces.concurrent.withLock
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventAutosensCalculationFinished
import app.aaps.core.interfaces.rx.events.EventBucketedDataCreated
import app.aaps.core.interfaces.rx.events.EventRefreshOverview
import app.aaps.core.interfaces.utils.TrendCalculator
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.withContext

/** Read-only boundary: the service can only observe changes and obtain display snapshots. */
@Inject
class AodDataSource(
    private val calculator: IobCobCalculator,
    private val profileUtil: ProfileUtil,
    private val trendCalculator: TrendCalculator,
    private val rxBus: RxBus
) {
    fun changes() = merge(
        rxBus.toFlow(EventBucketedDataCreated::class).map { Unit },
        rxBus.toFlow(EventAutosensCalculationFinished::class).map { Unit },
        rxBus.toFlow(EventRefreshOverview::class).map { Unit }
    )

    suspend fun read(): AodReading? = withContext(Dispatchers.Default) {
        val ads = calculator.ads
        val snapshot = ads.dataLock.withLock {
            ads.lastBg()?.copy()?.let { it to (trendCalculator.getTrendArrow(ads) ?: it.trendArrow) }
        } ?: return@withContext null
        val (bg, arrow) = snapshot
        if (!bg.recalculated.isFinite() || bg.recalculated <= 0 || bg.filledGap) return@withContext null
        val units = profileUtil.units
        AodReading(
            profileUtil.fromMgdlToStringInUnits(bg.recalculated, units),
            units.displayLabel,
            bg.timestamp,
            arrow,
            bg.recalculated
        )
    }
}
