package wangdaye.com.geometricweather.weather.json.cma;

import com.google.gson.annotations.SerializedName;

import java.util.List;

/**
 * China Meteorological Administration (weather.cma.cn) station search response.
 *
 * Endpoint: GET api/autocomplete?q={pinyin/english}&limit={n}
 *
 * data is a list of pipe-delimited strings: "stationId|中文名|英文名|国家",
 * e.g. "54511|北京|Beijing|中国". Note the query matches pinyin/English, not Chinese.
 */
public class CmaLocationResult {

    @SerializedName("msg")
    public String msg;
    @SerializedName("code")
    public Integer code;
    @SerializedName("data")
    public List<String> data;
}
