package wangdaye.com.geometricweather.weather.apis;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Query;
import wangdaye.com.geometricweather.weather.json.apihz.ApihzWeatherResult;

/**
 * apihz.cn ("接口盒子", labelled 中国天气网) weather forecast API.
 *
 * Base URL https://cn.apihz.cn/. Both endpoints return the same {@link ApihzWeatherResult} shape.
 * Docs: https://www.apihz.cn/api/tqtqyb.html (by place) and tqtqybip.html (by IP).
 *
 * day=7 -> 7-day daily, hourtype=1 -> 3-hourly periods, suntimetype=1 -> 7-day sun times.
 */
public interface ApihzApi {

    // By place name. place is required; sheng (province) optional and may be omitted (null) for a
    // looser, province-agnostic lookup. Retrofit UTF-8 url-encodes the Chinese params.
    // Note: a trailing 区 on place, or 市 on a municipality sheng, makes the lookup fail —
    // callers normalise the names first.
    @GET("api/tianqi/tqyb.php")
    Call<ApihzWeatherResult> getWeatherByPlace(@Query("id") String id,
                                               @Query("key") String key,
                                               @Query("sheng") String sheng,
                                               @Query("place") String place,
                                               @Query("day") int day,
                                               @Query("hourtype") int hourType,
                                               @Query("suntimetype") int sunTimeType);

    // By caller IP (no coordinate/name input). Used as a fallback when a place lookup fails.
    @GET("api/tianqi/tqybip.php")
    Call<ApihzWeatherResult> getWeatherByIp(@Query("id") String id,
                                            @Query("key") String key,
                                            @Query("day") int day,
                                            @Query("hourtype") int hourType,
                                            @Query("suntimetype") int sunTimeType);
}
