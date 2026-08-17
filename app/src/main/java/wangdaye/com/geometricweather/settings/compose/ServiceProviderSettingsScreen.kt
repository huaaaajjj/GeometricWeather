package wangdaye.com.geometricweather.settings.compose

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import wangdaye.com.geometricweather.BuildConfig
import wangdaye.com.geometricweather.GeometricWeather.Companion.instance
import wangdaye.com.geometricweather.R
import wangdaye.com.geometricweather.common.basic.models.options.provider.LocationProvider
import wangdaye.com.geometricweather.common.basic.models.options.provider.WeatherSource
import wangdaye.com.geometricweather.common.utils.helpers.SnackbarHelper
import wangdaye.com.geometricweather.db.DatabaseHelper
import wangdaye.com.geometricweather.settings.SettingsManager
import wangdaye.com.geometricweather.settings.preference.bottomInsetItem
import wangdaye.com.geometricweather.settings.preference.clickablePreferenceItem
import wangdaye.com.geometricweather.settings.preference.composables.ListPreferenceView
import wangdaye.com.geometricweather.settings.preference.composables.PreferenceScreen
import wangdaye.com.geometricweather.settings.preference.composables.PreferenceView
import wangdaye.com.geometricweather.settings.preference.composables.SectionHeader
import wangdaye.com.geometricweather.settings.preference.listPreferenceItem
import wangdaye.com.geometricweather.settings.preference.sectionFooterItem
import wangdaye.com.geometricweather.settings.preference.sectionHeaderItem

@Composable
fun ServiceProviderSettingsScreen(
    context: Context,
    navController: NavHostController
) = PreferenceScreen {

    sectionHeaderItem(R.string.settings_category_weather_data)

    listPreferenceItem(R.string.settings_title_weather_source) { id ->
        val values = stringArrayResource(R.array.weather_source_values)
        val baseNames = stringArrayResource(R.array.weather_sources)

        // AccuWeather's bundled key expired and there is no replacement, so the source answers
        // nothing unless the user supplies their own key on the advanced screen. Mark it instead of
        // letting it read like any other choice — picking it blind just yields "no data", with
        // nothing on screen to explain why. The mark disappears once a key is set.
        val accuIndex = values.indexOfFirst { it == WeatherSource.ACCU.id }
        val accuNeedsKey = accuIndex >= 0
                && SettingsManager.getInstance(context).customAccuWeatherKey.isEmpty()
        val accuLabel = stringResource(
            R.string.settings_weather_source_needs_own_key,
            baseNames.getOrElse(accuIndex) { "" }
        )
        val names = Array(baseNames.size) { index ->
            if (accuNeedsKey && index == accuIndex) accuLabel else baseNames[index]
        }

        ListPreferenceView(
            title = stringResource(id),
            summary = { _, value -> names.getOrNull(values.indexOfFirst { it == value }) ?: value },
            selectedKey = SettingsManager.getInstance(context).weatherSource.id,
            valueArray = values,
            nameArray = names,
            onValueChanged = { sourceId ->
                val newSource = WeatherSource.getInstance(sourceId)
                // 持久化 + 发 SettingsChangedMessage（同步调用，保持 UI 选中态与现有刷新时序）
                SettingsManager.getInstance(context).weatherSource = newSource

                wangdaye.com.geometricweather.common.utils.helpers.AsyncHelper.runOnIO {
                    val db = DatabaseHelper.getInstance(context)
                    val oldList = db.readLocationList()

                    // 1) 清掉旧缓存：天气按 (cityId, 旧源) 存储，必须用旧 location 删除
                    oldList.forEach { db.deleteWeather(it) }

                    // 2) 所有地区改为新源（不只定位地区）。非定位地区 formattedId 随之变化，
                    //    writeLocationList 整表重写可处理；去重避免同城不同源条目塌缩到同一主键。
                    val seen = HashSet<String>()
                    val newList = oldList
                        .map { it.copy(weatherSource = newSource) }
                        .filter { seen.add(it.formattedId) }

                    // 只写 location 表；重载时各地区 readWeather 命中空 → 自动重新拉取
                    db.writeLocationList(newList)
                }
            }
        )
    }

    clickablePreferenceItem(R.string.settings_title_weather_source_status) {
        PreferenceView(
            titleId = it,
            summaryId = R.string.settings_summary_weather_source_status,
        ) {
            navController.navigate(SettingsScreenRouter.WeatherSourceStatus.route)
        }
    }

    sectionFooterItem(R.string.settings_category_weather_data)
    sectionHeaderItem(R.string.settings_category_location)

    listPreferenceItem(R.string.settings_title_location_service) { id ->
        var currentSelectedKey = SettingsManager.getInstance(context).locationProvider.id
        var valueList = stringArrayResource(R.array.location_service_values)
        var nameList = stringArrayResource(R.array.location_services)

        if (BuildConfig.FLAVOR.contains("fdroid")) {
            valueList = arrayOf(valueList[1], valueList[3])
            nameList = arrayOf(nameList[1], nameList[3])
        } else if (BuildConfig.FLAVOR.contains("gplay")) {
            valueList = arrayOf(valueList[0], valueList[1], valueList[3])
            nameList = arrayOf(nameList[0], nameList[1], nameList[3])
        }
        if (!valueList.contains(currentSelectedKey)) {
            currentSelectedKey = LocationProvider.NATIVE.id
        }

        ListPreferenceView(
            title = stringResource(id),
            summary = { _, key -> nameList[valueList.indexOfFirst { it == key }] },
            selectedKey = currentSelectedKey,
            valueArray = valueList,
            nameArray = nameList,
            onValueChanged = { sourceId ->
                SettingsManager
                    .getInstance(context)
                    .locationProvider = LocationProvider.getInstance(sourceId)

                SnackbarHelper.showSnackbar(
                    context.getString(R.string.feedback_restart),
                    context.getString(R.string.restart)
                ) {
                    instance.recreateAllActivities()
                }
            }
        )
    }

    sectionFooterItem(R.string.settings_category_location)
    bottomInsetItem()
}
