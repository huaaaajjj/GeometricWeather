package wangdaye.com.geometricweather.weather.apis;

import io.reactivex.Observable;
import retrofit2.http.GET;
import retrofit2.http.Path;
import retrofit2.http.Query;
import wangdaye.com.geometricweather.weather.json.visualcrossing.VisualCrossingResult;

/**
 * Visual Crossing Weather API.
 * https://www.visualcrossing.com/resources/documentation/weather-api/timeline-weather-api/
 */

public interface VisualCrossingApi {

    @GET("VisualCrossingWebServices/rest/services/timeline/{location}")
    Observable<VisualCrossingResult> getTimeline(
            @Path("location") String location,
            @Query("key") String key,
            @Query("unitGroup") String unitGroup,
            @Query("include") String include,
            @Query("contentType") String contentType
    );

    @GET("VisualCrossingWebServices/rest/services/timeline/{location}/{date1}/{date2}")
    Observable<VisualCrossingResult> getTimelineDateRange(
            @Path("location") String location,
            @Path("date1") String date1,
            @Path("date2") String date2,
            @Query("key") String key,
            @Query("unitGroup") String unitGroup,
            @Query("include") String include,
            @Query("contentType") String contentType
    );
}
