package wangdaye.com.geometricweather.weather.apis;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Headers;
import retrofit2.http.Path;
import retrofit2.http.Query;
import wangdaye.com.geometricweather.weather.json.cma.CmaLocationResult;
import wangdaye.com.geometricweather.weather.json.cma.CmaNationalResult;
import wangdaye.com.geometricweather.weather.json.cma.CmaWeatherResult;

/**
 * China Meteorological Administration (weather.cma.cn) API.
 *
 * No API key required. Base URL: https://weather.cma.cn/
 *
 * NOTE: the site's WAF rejects the default okhttp User-Agent with HTTP 403, so every
 * request must send a browser-like User-Agent.
 */
public interface CmaApi {

    String UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
            + "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

    // Station search. q matches pinyin/English (not Chinese characters).
    @Headers("User-Agent: " + UA)
    @GET("api/autocomplete")
    Call<CmaLocationResult> getLocation(@Query("q") String q, @Query("limit") int limit);

    // Current + 7-day daily + alerts. stationid empty -> nearest station by IP.
    @Headers("User-Agent: " + UA)
    @GET("api/weather/view")
    Call<CmaWeatherResult> getWeather(@Query("stationid") String stationId);

    // National station list (with coordinates); used to resolve the nearest station to a
    // GPS position, since CMA has no coordinates->station endpoint. days: 1 = today.
    @Headers("User-Agent: " + UA)
    @GET("api/map/weather/{days}")
    Call<CmaNationalResult> getNationalMap(@Path("days") int days, @Query("t") long timestamp);

    // Hourly forecast is only available as HTML (no JSON endpoint); scraped from the page.
    @Headers("User-Agent: " + UA)
    @GET("web/weather/{stationId}.html")
    Call<ResponseBody> getHourlyHtml(@Path("stationId") String stationId);
}
