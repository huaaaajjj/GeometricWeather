package wangdaye.com.geometricweather.weather.json.cma;

import com.google.gson.JsonElement;
import com.google.gson.annotations.SerializedName;

import java.util.List;

/**
 * China Meteorological Administration national map response.
 *
 * Endpoint: GET api/map/weather/{days}?t={ms}
 *
 * data.city is a list of heterogeneous arrays; the leading fields are:
 * [0]=stationId, [1]=中文名, [2]=国家, [3]=icon, [4]=纬度, [5]=经度, ...
 * Used to resolve the nearest station to a set of coordinates (CMA has no coords->station API).
 */
public class CmaNationalResult {

    @SerializedName("msg")
    public String msg;
    @SerializedName("code")
    public Integer code;
    @SerializedName("data")
    public Data data;

    public static class Data {
        @SerializedName("city")
        public List<List<JsonElement>> city;
    }
}
