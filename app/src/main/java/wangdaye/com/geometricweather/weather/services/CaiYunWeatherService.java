package wangdaye.com.geometricweather.weather.services;

import android.content.Context;
import android.util.Log;

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

    private static final String TAG = "CaiYunService";

    private final CaiYunApi mApi;
    private AsyncHelper.Controller mController;

    @Inject
    public CaiYunWeatherService(CaiYunApi cyApi) {
        mApi = cyApi;
    }

    @Override
    public void requestWeather(Context context,
                               Location location, @NonNull RequestWeatherCallback callback) {
        mController = AsyncHelper.runOnIO(() -> {
            try {
                retrofit2.Response<CaiYunWeatherResult> response = mApi.getWeather(
                        BuildConfig.CAIYUN_WEATHER_KEY,
                        String.valueOf(location.getLongitude()),
                        String.valueOf(location.getLatitude()),
                        true
                ).execute();
                Log.i(TAG, "requestWeather: HTTP " + response.code() + " for "
                        + location.getLongitude() + "," + location.getLatitude());
                CaiYunWeatherResult result = response.body();
                if (result != null) {
                    WeatherResultWrapper wrapper =
                            CaiyunResultConverter.convert(context, location, result);
                    if (wrapper.result != null) {
                        Log.i(TAG, "requestWeather: conversion OK for cityId=" + location.getCityId());
                        callback.requestWeatherSuccess(
                                Location.copy(location, wrapper.result)
                        );
                    } else {
                        Log.w(TAG, "requestWeather: conversion returned null");
                        callback.requestWeatherFailed(location);
                    }
                } else {
                    Log.w(TAG, "requestWeather: body null, HTTP " + response.code()
                            + " " + response.message());
                    callback.requestWeatherFailed(location);
                }
            } catch (Exception e) {
                Log.e(TAG, "requestWeather exception", e);
                callback.requestWeatherFailed(location);
            }
        });
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

        mController = AsyncHelper.runOnIO(() -> {
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
        });
    }

    @Override
    public void cancel() {
        if (mController != null) {
            mController.cancel();
        }
    }
}
