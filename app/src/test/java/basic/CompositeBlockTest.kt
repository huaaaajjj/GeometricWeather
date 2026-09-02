package basic

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import wangdaye.com.geometricweather.R
import wangdaye.com.geometricweather.common.basic.models.Location
import wangdaye.com.geometricweather.common.basic.models.options.provider.CompositeBlock
import wangdaye.com.geometricweather.common.basic.models.options.provider.WeatherSource
import java.util.TimeZone

/**
 * The cards print which provider a block came from. The label is only honest as long as it is read
 * off the same table the request plan is built from, so what is locked here is that the table says
 * what the multi-source option actually does — and that a single-provider location gets no label at
 * all, since the credit line in the footer already names it.
 *
 * The locations here carry no weather, which is the cached case: nothing recorded who really led,
 * so the label falls back to the assignment. That the label follows the *actual* provider when the
 * merge did record one is locked in `WeatherMergerTest`, where real merged weathers exist.
 */
@RunWith(RobolectricTestRunner::class)
// Robolectric 4.12.2 ships no SDK 35 sandbox; targetSdk is 35, so pin the runtime to 34.
@Config(sdk = [34])
class CompositeBlockTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    private fun locationOn(source: WeatherSource) = Location(
        "54517_tj",
        39.113019f, 117.150738f,
        TimeZone.getTimeZone("Asia/Shanghai"),
        "中国", "天津市", "天津市", "南开区",
        null,
        source,
        false, false, true
    )

    @Test
    fun eachBlockNamesItsAssignedProvider() {
        assertEquals(WeatherSource.XIAOMI, CompositeBlock.HOURLY.source)
        assertEquals(WeatherSource.APIHZ, CompositeBlock.DAILY.source)
        assertEquals(WeatherSource.CAIYUN, CompositeBlock.CURRENT.source)
        assertEquals(WeatherSource.CAIYUN, CompositeBlock.AIR_QUALITY.source)
    }

    @Test
    fun aMultiSourceCardTitleCarriesTheProvider() {
        val title = CompositeBlock.title(
            context, locationOn(WeatherSource.COMPOSITE), CompositeBlock.DAILY,
            R.string.daily_overview
        )

        assertTrue(title, title.startsWith(context.getString(R.string.daily_overview)))
        assertTrue(title, title.contains(WeatherSource.APIHZ.getVoice(context)))
    }

    @Test
    fun aSingleProviderCardTitleIsLeftAlone() {
        val title = CompositeBlock.title(
            context, locationOn(WeatherSource.CAIYUN), CompositeBlock.AIR_QUALITY,
            R.string.air_quality
        )

        assertEquals(context.getString(R.string.air_quality), title)
    }
}
