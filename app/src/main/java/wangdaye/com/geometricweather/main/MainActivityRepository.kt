package wangdaye.com.geometricweather.main

import android.content.Context
import wangdaye.com.geometricweather.common.basic.models.Location
import wangdaye.com.geometricweather.common.utils.helpers.AsyncHelper
import wangdaye.com.geometricweather.db.DatabaseHelper
import wangdaye.com.geometricweather.location.LocationHelper
import wangdaye.com.geometricweather.weather.WeatherHelper
import wangdaye.com.geometricweather.weather.WeatherHelper.OnRequestWeatherListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors
import javax.inject.Inject

class MainActivityRepository @Inject constructor(
    private val locationHelper: LocationHelper,
    private val weatherHelper: WeatherHelper
) {
    private val singleThreadExecutor = Executors.newSingleThreadExecutor()

    interface WeatherRequestCallback {
        fun onCompleted(
            location: Location,
            locationFailed: Boolean?,
            weatherRequestFailed: Boolean
        )
    }

    fun destroy() {
        cancelWeatherRequest()
    }

    fun initLocations(context: Context, formattedId: String, callback: AsyncHelper.Callback<List<Location>>) {
        AsyncHelper.runOnIO({ emitter ->
            try {
                val list = DatabaseHelper.getInstance(context).readLocationList()

                if (list.isEmpty()) {
                    val defaultLocation = Location.buildLocal()
                    DatabaseHelper.getInstance(context).writeLocation(defaultLocation)
                    emitter.send(listOf(defaultLocation), true)
                    return@runOnIO
                }

                var index = 0
                for (i in list.indices) {
                    if (list[i].formattedId == formattedId) {
                        index = i
                        break
                    }
                }

                val weather = DatabaseHelper.getInstance(context).readWeather(list[index])
                list[index] = Location.copy(
                    src = list[index],
                    weather = weather
                )
                emitter.send(list, true)
            } catch (e: Exception) {
                android.util.Log.e("MainActivityRepo", "initLocations failed", e)
                emitter.send(emptyList(), true)
            }
        }, callback)
    }

    fun getWeatherCacheForLocations(
        context: Context,
        oldList: List<Location>,
        ignoredFormattedId: String,
        callback: AsyncHelper.Callback<List<Location>>
    ) {
        AsyncHelper.runOnExecutor({ emitter ->
            emitter.send(
                oldList.map {
                    if (it.formattedId == ignoredFormattedId) {
                        it
                    } else {
                        Location.copy(
                            src = it,
                            weather = DatabaseHelper.getInstance(context).readWeather(it)
                        )
                    }
                },
                true
            )
        }, callback, singleThreadExecutor)
    }

    fun writeLocationList(context: Context, locationList: List<Location>) {
        AsyncHelper.runOnExecutor({ 
            DatabaseHelper.getInstance(context).writeLocationList(locationList)
        }, singleThreadExecutor)
    }

    fun deleteLocation(context: Context, location: Location) {
        AsyncHelper.runOnExecutor({
            DatabaseHelper.getInstance(context).deleteLocation(location)
            DatabaseHelper.getInstance(context).deleteWeather(location)
        }, singleThreadExecutor)
    }

    fun getWeather(
        context: Context,
        location: Location,
        locate: Boolean,
        callback: WeatherRequestCallback,
    ) {
        if (locate) {
            ensureValidLocationInformation(context, location, callback)
        } else {
            getWeatherWithValidLocationInformation(context, location, null, callback)
        }
    }

    private fun ensureValidLocationInformation(
        context: Context,
        location: Location,
        callback: WeatherRequestCallback,
    ) {
        android.util.Log.d("Repo", "ensureValidLocation: loc=${location.city} id=${location.formattedId} isCur=${location.isCurrentPosition}")
        locationHelper.requestLocation(
            context,
            location,
            false,
            object : LocationHelper.OnRequestLocationListener {

                override fun requestLocationSuccess(requestLocation: Location) {
                    android.util.Log.d("Repo", "LOC OK: origId=${location.formattedId} newId=${requestLocation.formattedId} city=${requestLocation.city}")
                    if (requestLocation.formattedId != location.formattedId) {
                        android.util.Log.w("Repo", "LOC SKIP: fmtId mismatch!")
                        return
                    }
                    getWeatherWithValidLocationInformation(
                        context, requestLocation, false, callback
                    )
                }

                override fun requestLocationFailed(requestLocation: Location) {
                    android.util.Log.w("Repo", "LOC FAIL: origId=${location.formattedId} newId=${requestLocation.formattedId} city=${requestLocation.city} usable=${requestLocation.isUsable}")
                    if (requestLocation.formattedId != location.formattedId) {
                        android.util.Log.w("Repo", "LOC SKIP: fmtId mismatch!")
                        return
                    }
                    getWeatherWithValidLocationInformation(
                        context, requestLocation, true, callback
                    )
                }
            }
        )
    }

    private fun getWeatherWithValidLocationInformation(
        context: Context,
        location: Location,
        locationFailed: Boolean?,
        callback: WeatherRequestCallback,
    ) = weatherHelper.requestWeather(
        context,
        location,
        object : OnRequestWeatherListener {
            override fun requestWeatherSuccess(requestLocation: Location) {
                if (requestLocation.formattedId != location.formattedId) {
                    return
                }
                callback.onCompleted(
                    requestLocation,
                    locationFailed = locationFailed,
                    weatherRequestFailed = false
                )
            }

            override fun requestWeatherFailed(requestLocation: Location) {
                if (requestLocation.formattedId != location.formattedId) {
                    return
                }
                callback.onCompleted(
                    requestLocation,
                    locationFailed = locationFailed,
                    weatherRequestFailed = true
                )
            }
        }
    )

    fun getLocatePermissionList(context: Context) = locationHelper
        .getPermissions(context)
        .toList()

    fun cancelWeatherRequest() {
        locationHelper.cancel()
        weatherHelper.cancel()
    }
}