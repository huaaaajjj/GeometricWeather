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
        ListPreferenceView(
            titleId = id,
            valueArrayId = R.array.weather_source_values,
            nameArrayId = R.array.weather_sources,
            selectedKey = SettingsManager.getInstance(context).weatherSource.id,
            onValueChanged = { sourceId ->
                SettingsManager
                    .getInstance(context)
                    .weatherSource = WeatherSource.getInstance(sourceId)

                wangdaye.com.geometricweather.common.utils.helpers.AsyncHelper.runOnIO {
                    val locationList = DatabaseHelper.getInstance(context).readLocationList()
                    val index = locationList.indexOfFirst { it.isCurrentPosition }
                    if (index >= 0) {
                        locationList[index] = locationList[index].copy(
                            weather = null,
                            weatherSource = SettingsManager.getInstance(context).weatherSource
                        ).copy()
                        DatabaseHelper.getInstance(context).deleteWeather(locationList[index])
                        DatabaseHelper.getInstance(context).writeLocationList(locationList)
                    }
                }
            }
        )
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

    sectionHeaderItem(R.string.settings_category_advanced)

    clickablePreferenceItem(R.string.settings_title_service_provider_advanced) {
        PreferenceView(
            titleId = it,
            summaryId = R.string.settings_summary_service_provider_advanced,
        ) {
            navController.navigate(SettingsScreenRouter.ServiceProviderAdvanced.route)
        }
    }

    sectionFooterItem(R.string.settings_category_advanced)
    bottomInsetItem()
}
