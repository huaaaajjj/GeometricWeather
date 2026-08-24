package wangdaye.com.geometricweather.weather.apis;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Query;
import wangdaye.com.geometricweather.weather.json.metno.MetNoAirQualityResult;
import wangdaye.com.geometricweather.weather.json.metno.MetNoAlertResult;
import wangdaye.com.geometricweather.weather.json.metno.MetNoForecastResult;

/**
 * MET Norway (api.met.no) API. No key, no registration.
 *
 * Base URL: https://api.met.no/weatherapi/
 *
 * NOTE: api.met.no's terms of service require an identifying User-Agent on every request and block
 * clients that send none. It is not declared here with {@code @Headers} because it applies to all
 * four endpoints — {@code ApiModule} adds it with an interceptor on this provider's own OkHttp
 * client instead. Do not route these calls through the shared client.
 *
 * Only {@link #getForecast} is global; the other three are geographically limited (verified
 * 2026-08-23 against Beijing/Paris) and every one of those limits surfaces as an error or an empty
 * body, which {@code RequestScope.execute} degrades to null.
 */
public interface MetNoApi {

    /**
     * Global. ~90 points: hourly for the first ~2.5 days, then 6-hourly out to ~10 days. The final
     * point carries no forecast block at all (instant details only).
     */
    @GET("locationforecast/2.0/complete.json")
    Call<MetNoForecastResult> getForecast(@Query("lat") double lat,
                                         @Query("lon") double lon);

    /** Nordics only — HTTP 422 elsewhere. ~23 points at a 5-minute step, ~2 hours ahead. */
    @GET("nowcast/2.0/complete.json")
    Call<MetNoForecastResult> getNowcast(@Query("lat") double lat,
                                        @Query("lon") double lon);

    /** Norway only — HTTP 400 elsewhere (Paris included). Hourly, ~2.3 days ahead. */
    @GET("airqualityforecast/0.1/")
    Call<MetNoAirQualityResult> getAirQuality(@Query("lat") double lat,
                                             @Query("lon") double lon);

    /** Norwegian alerts only; outside Norway it answers 200 with an empty feature list. */
    @GET("metalerts/2.0/current.json")
    Call<MetNoAlertResult> getAlerts(@Query("lat") double lat,
                                     @Query("lon") double lon,
                                     @Query("lang") String lang);
}
