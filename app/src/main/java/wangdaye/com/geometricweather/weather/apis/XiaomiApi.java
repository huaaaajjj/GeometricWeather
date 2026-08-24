package wangdaye.com.geometricweather.weather.apis;

import java.util.List;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Query;
import wangdaye.com.geometricweather.weather.json.xiaomi.XiaomiForecastResult;
import wangdaye.com.geometricweather.weather.json.xiaomi.XiaomiLocationResult;
import wangdaye.com.geometricweather.weather.json.xiaomi.XiaomiMinutelyResult;

/**
 * Xiaomi Weather (weatherapi.market.xiaomi.com/wtr-v3/) API — the endpoints the MIUI weather app
 * uses. No account and no registration: {@code appKey}/{@code sign} are Xiaomi's own fixed pair,
 * carried in the query string (see BuildConfig.XIAOMI_APP_KEY / XIAOMI_SIGN).
 *
 * Data comes from 彩云天气 and 中国环境监测总站 inside China, and from AccuWeather abroad.
 *
 * <p><b>Two calls, always in this order.</b> {@code weather/all} and {@code weather/xm/forecast/minutely}
 * both demand a {@code locationKey}, which only {@code location/city/geo} can produce, so a refresh
 * is a resolve step followed by the data calls. The app has nowhere to cache that key — the Room
 * schema is frozen at v63 and {@code Location} has no per-source parameter map — so the resolve runs
 * every time. It is one small request, and it keeps the source stateless.
 *
 * <p>Xiaomi picks the backend from the key's prefix, and {@code isGlobal} has to agree with it:
 * {@code weathercn:} keys are China ({@code isGlobal=false}), {@code accu:} keys are everywhere else
 * ({@code isGlobal=true}). Sending the wrong flag returns an empty forecast rather than an error.
 */
public interface XiaomiApi {

    /**
     * Reverse geocoding. Takes WGS-84 (verified against 舒城/北京), returns one candidate whose
     * {@code locationKey} prefix also tells you which backend will serve it.
     */
    @GET("location/city/geo")
    Call<List<XiaomiLocationResult>> getLocation(@Query("latitude") double latitude,
                                                @Query("longitude") double longitude,
                                                @Query("locale") String locale);

    /** Current + daily + hourly + air quality + alerts. 15 days in China, 5 abroad. */
    @GET("weather/all")
    Call<XiaomiForecastResult> getForecast(@Query("latitude") double latitude,
                                          @Query("longitude") double longitude,
                                          @Query("isLocated") boolean isLocated,
                                          @Query("locationKey") String locationKey,
                                          @Query("days") int days,
                                          @Query("appKey") String appKey,
                                          @Query("sign") String sign,
                                          @Query("isGlobal") boolean isGlobal,
                                          @Query("locale") String locale);

    /** Two hours of minute-by-minute precipitation. Only some Chinese cities are covered. */
    @GET("weather/xm/forecast/minutely")
    Call<XiaomiMinutelyResult> getMinutely(@Query("latitude") double latitude,
                                          @Query("longitude") double longitude,
                                          @Query("locale") String locale,
                                          @Query("isGlobal") boolean isGlobal,
                                          @Query("appKey") String appKey,
                                          @Query("locationKey") String locationKey,
                                          @Query("sign") String sign);
}
