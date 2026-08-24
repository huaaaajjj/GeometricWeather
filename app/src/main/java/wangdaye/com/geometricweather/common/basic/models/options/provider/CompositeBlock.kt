package wangdaye.com.geometricweather.common.basic.models.options.provider

import android.content.Context
import androidx.annotation.StringRes
import wangdaye.com.geometricweather.R
import wangdaye.com.geometricweather.common.basic.models.Location

/**
 * Which provider the multi-source ([WeatherSource.COMPOSITE]) option takes each block from.
 *
 * One table, read by both `CompositeWeatherService`, which turns it into the request plan, and the
 * cards, which print the provider next to their titles. Sharing it is the whole point: a label that
 * had drifted from where the data actually comes from would be worse than no label at all.
 *
 * It states the *preference*, not a guarantee — when the assigned provider fails or has nothing for
 * the block, the merge falls through to whoever does, and the label still names the preference.
 */
enum class CompositeBlock(val source: WeatherSource) {
    HOURLY(WeatherSource.XIAOMI),
    DAILY(WeatherSource.APIHZ),
    CURRENT(WeatherSource.CAIYUN),
    AIR_QUALITY(WeatherSource.CAIYUN);

    companion object {

        /**
         * A card title with its provider appended — but only for a location on the multi-source
         * option. Under a single provider every card would name the same one, which the credit line
         * in the footer already does.
         */
        @JvmStatic
        fun title(
            context: Context,
            location: Location,
            block: CompositeBlock,
            @StringRes titleId: Int
        ): String {
            val title = context.getString(titleId)
            if (location.weatherSource != WeatherSource.COMPOSITE) {
                return title
            }
            return context.getString(
                R.string.composite_block_credit, title, block.source.getVoice(context)
            )
        }
    }
}
