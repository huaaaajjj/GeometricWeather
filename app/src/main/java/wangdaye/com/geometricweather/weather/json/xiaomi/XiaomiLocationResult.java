package wangdaye.com.geometricweather.weather.json.xiaomi;

import com.google.gson.annotations.SerializedName;

/**
 * Xiaomi Weather location result — one item of the array returned by both
 * {@code location/city/geo} and {@code location/city/search}.
 *
 * The {@code locationKey} prefix is the only thing that says which backend will answer for this
 * place, and it decides the {@code isGlobal} flag on every later call:
 * {@code weathercn:101011600} inside China, {@code accu:1094121} outside it.
 *
 * {@code latitude}/{@code longitude} are the matched city's own centre, not an echo of the query,
 * and inside China they are GCJ-02. Nothing reads them: the service keeps the caller's WGS-84
 * position and only takes the key, so no coordinate conversion is needed.
 */
public class XiaomiLocationResult {

    /** "weathercn:<id>" in China, "accu:<id>" abroad. */
    @SerializedName("locationKey")
    public String locationKey;

    /** Same value as {@link #locationKey}. */
    @SerializedName("key")
    public String key;

    /** District/city name in the requested locale, e.g. "东城". */
    @SerializedName("name")
    public String name;

    /** Comma-separated parents, e.g. "北京, 中国". */
    @SerializedName("affiliation")
    public String affiliation;

    @SerializedName("latitude")
    public String latitude;

    @SerializedName("longitude")
    public String longitude;

    /** UTC offset in seconds, e.g. 28800. */
    @SerializedName("timeZoneShift")
    public Integer timeZoneShift;

    /** 0 on success. */
    @SerializedName("status")
    public Integer status;
}
