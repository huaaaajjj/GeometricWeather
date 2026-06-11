package wangdaye.com.geometricweather.weather.services;

import android.content.Context;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

import wangdaye.com.geometricweather.BuildConfig;
import wangdaye.com.geometricweather.common.basic.models.ChineseCity;
import wangdaye.com.geometricweather.common.basic.models.Location;
import wangdaye.com.geometricweather.common.utils.LanguageUtils;
import wangdaye.com.geometricweather.common.utils.helpers.AsyncHelper;
import wangdaye.com.geometricweather.db.DatabaseHelper;
import wangdaye.com.geometricweather.weather.apis.CaiYunApi;
import wangdaye.com.geometricweather.weather.converters.CaiyunResultConverter;
import wangdaye.com.geometricweather.weather.json.caiyun.CaiYunWeatherResult;

public class CaiYunWeatherService extends WeatherService {

    private final CaiYunApi mApi;
    private final List<AsyncHelper.Controller> mControllers = new ArrayList<>();

    @Inject
    public CaiYunWeatherService(CaiYunApi cyApi) {
        mApi = cyApi;
    }

    @Override
    public void requestWeather(Context context,
                               Location location, @NonNull RequestWeatherCallback callback) {
        mControllers.add(AsyncHelper.runOnIO(() -> {
            try {
                android.util.Log.d("CaiYunService", "Requesting weather for " + location.getLatitude() + "," + location.getLongitude());
                retrofit2.Response<CaiYunWeatherResult> response = mApi.getWeather(
                        BuildConfig.CAIYUN_WEATHER_KEY,
                        String.valueOf(location.getLongitude()),
                        String.valueOf(location.getLatitude()),
                        true
                ).execute();
                
                android.util.Log.d("CaiYunService", "Response code: " + response.code());
                
                if (response.isSuccessful()) {
                    CaiYunWeatherResult result = response.body();
                    if (result != null) {
                        android.util.Log.d("CaiYunService", "Response status: " + result.status);
                        WeatherResultWrapper wrapper =
                                CaiyunResultConverter.convert(context, location, result);
                        if (wrapper.result != null) {
                            android.util.Log.d("CaiYunService", "Weather conversion successful");
                            callback.requestWeatherSuccess(
                                    Location.copy(location, wrapper.result)
                            );
                        } else {
                            android.util.Log.w("CaiYunService", "Weather conversion returned null");
                            callback.requestWeatherFailed(location);
                        }
                    } else {
                        android.util.Log.w("CaiYunService", "Response body is null, error: " + response.errorBody());
                        callback.requestWeatherFailed(location);
                    }
                } else {
                    android.util.Log.w("CaiYunService", "HTTP error: " + response.code() + ", " + response.message());
                    callback.requestWeatherFailed(location);
                }
            } catch (Exception e) {
                android.util.Log.e("CaiYunService", "Exception requesting weather", e);
                callback.requestWeatherFailed(location);
            }
        }));
    }

    @NonNull
    @Override
    public List<Location> requestLocation(Context context, String query) {
        if (!LanguageUtils.isChinese(query)) {
            return new ArrayList<>();
        }

        DatabaseHelper.getInstance(context).ensureChineseCityList(context);

        List<Location> locationList = new ArrayList<>();
        List<ChineseCity> cityList = DatabaseHelper.getInstance(context).readChineseCityList(query);
        for (ChineseCity c : cityList) {
            locationList.add(c.toLocation());
        }

        return locationList;
    }

    @Override
    public void requestLocation(Context context, Location location,
                                @NonNull RequestLocationCallback callback) {
        final boolean hasGeocodeInformation = location.hasGeocodeInformation();

        mControllers.add(AsyncHelper.runOnIO(() -> {
            DatabaseHelper.getInstance(context).ensureChineseCityList(context);
            List<Location> locationList = new ArrayList<>();

            if (hasGeocodeInformation) {
                ChineseCity chineseCity = DatabaseHelper.getInstance(context).readChineseCity(
                        formatLocationString(convertChinese(location.getProvince())),
                        formatLocationString(convertChinese(location.getCity())),
                        formatLocationString(convertChinese(location.getDistrict()))
                );
                if (chineseCity != null) {
                    locationList.add(chineseCity.toLocation());
                }
            }
            if (locationList.size() > 0) {
                callback.requestLocationSuccess(location.getFormattedId(), locationList);
                return;
            }

            ChineseCity chineseCity = DatabaseHelper.getInstance(context).readChineseCity(
                    location.getLatitude(), location.getLongitude());
            if (chineseCity != null) {
                locationList.add(chineseCity.toLocation());
            }

            if (locationList.size() > 0) {
                callback.requestLocationSuccess(location.getFormattedId(), locationList);
            } else {
                callback.requestLocationFailed(location.getFormattedId());
            }
        }));
    }

    @Override
    public void cancel() {
        for (AsyncHelper.Controller c : mControllers) {
            c.cancel();
        }
        mControllers.clear();
    }
}
