package wangdaye.com.geometricweather.location;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;
import androidx.core.app.ActivityCompat;

import java.util.List;
import java.util.TimeZone;

import javax.inject.Inject;

import dagger.hilt.android.qualifiers.ApplicationContext;
import wangdaye.com.geometricweather.common.basic.models.ChineseCity;
import wangdaye.com.geometricweather.common.basic.models.Location;
import wangdaye.com.geometricweather.common.basic.models.options.provider.LocationProvider;
import wangdaye.com.geometricweather.common.basic.models.options.provider.WeatherSource;
import wangdaye.com.geometricweather.common.utils.NetworkUtils;
import wangdaye.com.geometricweather.common.utils.CoordinateUtils;
import wangdaye.com.geometricweather.common.utils.helpers.AsyncHelper;
import wangdaye.com.geometricweather.db.DatabaseHelper;
import wangdaye.com.geometricweather.location.services.AMapLocationService;
import wangdaye.com.geometricweather.location.services.AndroidLocationService;
import wangdaye.com.geometricweather.location.services.BaiduLocationService;
import wangdaye.com.geometricweather.location.services.LocationService;
import wangdaye.com.geometricweather.location.services.ip.BaiduIPLocationService;
import wangdaye.com.geometricweather.settings.SettingsManager;
import wangdaye.com.geometricweather.weather.WeatherServiceSet;
import wangdaye.com.geometricweather.weather.services.WeatherService;

/**
 * Location helper.
 * */

public class LocationHelper {

    private final LocationService[] mLocationServices;
    private final WeatherServiceSet mWeatherServiceSet;

    public interface OnRequestLocationListener {
        void requestLocationSuccess(Location requestLocation);
        void requestLocationFailed(Location requestLocation);
    }

    @Inject
    public LocationHelper(@ApplicationContext Context context,
                          BaiduIPLocationService baiduIPService,
                          WeatherServiceSet weatherServiceSet) {
        this(
                new LocationService[] {
                        new AndroidLocationService(),
                        new BaiduLocationService(context),
                        baiduIPService,
                        new AMapLocationService(context)
                },
                weatherServiceSet
        );
    }

    /**
     * The services are handed in rather than built here so a test can stand in for them — the real
     * ones pull in the Baidu and AMap SDKs, which cannot run off a device. The array order is the
     * one {@link #getLocationService} indexes into: native, Baidu, Baidu-IP, AMap.
     */
    @VisibleForTesting
    public LocationHelper(LocationService[] locationServices,
                          WeatherServiceSet weatherServiceSet) {
        mLocationServices = locationServices;
        mWeatherServiceSet = weatherServiceSet;
    }

    private LocationService getLocationService(LocationProvider provider) {
        switch (provider) {
            case BAIDU:
                return mLocationServices[1];

            case BAIDU_IP:
                return mLocationServices[2];

            case AMAP:
                return mLocationServices[3];

            default: // NATIVE
                return mLocationServices[0];
        }
    }

    public void requestLocation(Context context, Location location, boolean background,
                                @NonNull OnRequestLocationListener l) {
        final OnRequestLocationListener usableCheckListener = new OnRequestLocationListener() {
            @Override
            public void requestLocationSuccess(Location requestLocation) {
                l.requestLocationSuccess(requestLocation);
            }

            @Override
            public void requestLocationFailed(Location requestLocation) {
                if (requestLocation.isUsable()) {
                    // Hop to the main thread like the other two outcomes below: this one is reached
                    // from the weather service's IO thread, and callers set LiveData in here.
                    wangdaye.com.geometricweather.common.utils.helpers.AsyncHelper.delayRunOnUI(
                            () -> l.requestLocationFailed(requestLocation), 0);
                } else {
                    Location finalLocation = Location.copy(
                            Location.buildDefaultLocation(
                                    SettingsManager.getInstance(context).getWeatherSource()
                            ),
                            true,
                            false
                    );
                    wangdaye.com.geometricweather.common.utils.helpers.AsyncHelper.runOnIO(() -> {
                        DatabaseHelper.getInstance(context).writeLocation(finalLocation);
                        wangdaye.com.geometricweather.common.utils.helpers.AsyncHelper.delayRunOnUI(
                                () -> l.requestLocationFailed(finalLocation), 0);
                    });
                }
            }
        };

        final LocationProvider provider = SettingsManager.getInstance(context).getLocationProvider();
        final LocationService service = getLocationService(provider);
        if (service.getPermissions().length != 0) {
            // if needs any location permission.
            if (!NetworkUtils.isAvailable(context) || (ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED)) {
                usableCheckListener.requestLocationFailed(location);
                return;
            }
            if (background) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && ActivityCompat.checkSelfPermission(
                        context,
                        Manifest.permission.ACCESS_BACKGROUND_LOCATION
                ) != PackageManager.PERMISSION_GRANTED) {
                    usableCheckListener.requestLocationFailed(location);
                    return;
                }
            }
        }

        // 1. get location by location service.
        // 2. get available location by weather service.

        service.requestLocation(
                context,
                result -> {
                    if (result == null) {
                        usableCheckListener.requestLocationFailed(location);
                        return;
                    }

                    // The native service (the default provider) and the IP fallback return bare
                    // coordinates, AMap now and then an empty address, and most weather sources
                    // echo the location back unnamed — so nothing named the place and the header
                    // read 「当前位置」. Ask the platform geocoder then; on IO, since it blocks and
                    // the SDK services all call back on the main thread.
                    AsyncHelper.runOnIO(() -> {
                        LocationService.Result named = hasAddress(result)
                                ? result
                                : reverseGeocode(context, result);
                        // Still unnamed and the slot has no name to keep: name it offline from the
                        // bundled China city list. Only in that case — a slot that already has a name
                        // keeps it (the null-means-keep copy below), so a re-locate that drops the
                        // address does not get overwritten; abroad the list can't help either.
                        if (!hasAddress(named)
                                && !location.hasGeocodeInformation()
                                && CoordinateUtils.isInChina(
                                        result.getLatitude(), result.getLongitude())) {
                            named = nameFromChineseCityList(context, result);
                        }
                        boolean hasAddress = hasAddress(named);
                        requestAvailableWeatherLocation(
                                context,
                                location.copy(
                                        null,
                                        result.getLatitude(),
                                        result.getLongitude(),
                                        TimeZone.getDefault(),
                                        // An address replaces the old one whole: copy() reads null
                                        // as "keep", which would pair a new city with the old
                                        // district. No address at all keeps the last-known name.
                                        hasAddress ? orEmpty(named.getCountry()) : null,
                                        hasAddress ? orEmpty(named.getProvince()) : null,
                                        hasAddress ? orEmpty(named.getCity()) : null,
                                        hasAddress ? orEmpty(named.getDistrict()) : null,
                                        null, // weather — keep
                                        null, // weatherSource — keep
                                        null, // isCurrentPosition — keep
                                        null, // isResidentPosition — keep
                                        // isChina was never set for a GPS fix — it stayed false
                                        // from buildLocal(), so the domestic sources (APIHZ/CMA)
                                        // rejected the current location outright. Derive it from the
                                        // coordinates (locale-independent, works even without a
                                        // geocoded address), same box used for GCJ-02 conversion.
                                        CoordinateUtils.isInChina(
                                                result.getLatitude(), result.getLongitude())
                                ),
                                usableCheckListener
                        );
                    });
                }
        );
    }

    /**
     * The platform geocoder's address for a bare fix, or the fix unchanged when it has none: no
     * geocoder on the device, no network, or no answer. Blocks.
     */
    @WorkerThread
    @SuppressWarnings("deprecation") // the listener overload is API 33+
    private static LocationService.Result reverseGeocode(Context context,
                                                         LocationService.Result fix) {
        try {
            if (Geocoder.isPresent()) {
                List<Address> list = new Geocoder(context).getFromLocation(
                        fix.getLatitude(), fix.getLongitude(), 1);
                if (list != null && !list.isEmpty()) {
                    Address a = list.get(0);
                    return new LocationService.Result(
                            fix.getLatitude(),
                            fix.getLongitude(),
                            a.getCountryName(),
                            a.getAdminArea(),
                            a.getLocality(),
                            a.getSubLocality()
                    );
                }
            }
        } catch (Exception ignored) {
            // IOException: offline, or the provider gave up.
        }
        return fix;
    }

    /**
     * The nearest place in the bundled China city list, or the fix unchanged if the list can't name
     * it. Offline last resort for a fix the platform geocoder left unnamed — a GMS-less phone or an
     * emulator has no geocoder backend, and without a name the current position sticks on
     * 「当前位置 / 尚未定位」. Same coord→name lookup CaiYun already falls back on. Blocks.
     */
    @WorkerThread
    private static LocationService.Result nameFromChineseCityList(Context context,
                                                                 LocationService.Result fix) {
        DatabaseHelper db = DatabaseHelper.getInstance(context);
        db.ensureChineseCityList(context);
        ChineseCity city = db.readChineseCity(fix.getLatitude(), fix.getLongitude());
        if (city == null) {
            return fix;
        }
        return new LocationService.Result(
                fix.getLatitude(),
                fix.getLongitude(),
                "中国",
                city.getProvince(),
                city.getCity(),
                "无".equals(city.getDistrict()) ? "" : city.getDistrict()
        );
    }

    private static boolean hasAddress(LocationService.Result r) {
        return !orEmpty(r.getCountry()).isEmpty()
                || !orEmpty(r.getProvince()).isEmpty()
                || !orEmpty(r.getCity()).isEmpty()
                || !orEmpty(r.getDistrict()).isEmpty();
    }

    private static String orEmpty(String s) {
        return s == null ? "" : s;
    }

    private void requestAvailableWeatherLocation(Context context,
                                                 @NonNull Location location,
                                                 @NonNull OnRequestLocationListener l) {
        WeatherSource source = SettingsManager.getInstance(context).getWeatherSource();

        final WeatherService service = mWeatherServiceSet.get(source);
        service.requestLocation(context, location, new WeatherService.RequestLocationCallback() {
            @Override
            public void requestLocationSuccess(String query, List<Location> locationList) {
                if (locationList.size() > 0) {
                    Location src = locationList.get(0);
                    Location result = Location.copy(src, true, src.isResidentPosition());
                    wangdaye.com.geometricweather.common.utils.helpers.AsyncHelper.runOnIO(() -> {
                        DatabaseHelper.getInstance(context).writeLocation(result);
                        wangdaye.com.geometricweather.common.utils.helpers.AsyncHelper.delayRunOnUI(
                                () -> l.requestLocationSuccess(result), 0);
                    });
                } else {
                    requestLocationFailed(query);
                }
            }

            @Override
            public void requestLocationFailed(String query) {
                l.requestLocationFailed(location);
            }
        });
    }

    public void cancel() {
        for (LocationService s : mLocationServices) {
            s.cancel();
        }
        for (WeatherService s : mWeatherServiceSet.getAll()) {
            s.cancel();
        }
    }

    public String[] getPermissions(Context context) {
        // if IP:    none.
        // else:
        //      R:   foreground location. (set background location enabled manually)
        //      Q:   foreground location + background location.
        //      K-P: foreground location.

        final LocationProvider provider = SettingsManager.getInstance(context).getLocationProvider();
        final LocationService service = getLocationService(provider);

        String[] permissions = service.getPermissions();
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || permissions.length == 0) {
            // device has no background location permission or locate by IP.
            return permissions;
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            String[] qPermissions = new String[permissions.length + 1];
            System.arraycopy(permissions, 0, qPermissions, 0, permissions.length);
            qPermissions[qPermissions.length - 1] = Manifest.permission.ACCESS_BACKGROUND_LOCATION;
            return qPermissions;
        }

        return permissions;
    }
}