package wangdaye.com.geometricweather.db.converters;

import androidx.room.TypeConverter;

import java.util.Date;
import java.util.TimeZone;

import wangdaye.com.geometricweather.common.basic.models.options.provider.WeatherSource;
import wangdaye.com.geometricweather.common.basic.models.weather.WeatherCode;
import wangdaye.com.geometricweather.common.basic.models.weather.WindDegree;

public class RoomTypeConverters {

    @TypeConverter
    public static Long dateToTimestamp(Date date) {
        return date == null ? null : date.getTime();
    }

    @TypeConverter
    public static Date timestampToDate(Long timestamp) {
        return timestamp == null ? null : new Date(timestamp);
    }

    @TypeConverter
    public static String weatherCodeToString(WeatherCode code) {
        return code == null ? null : code.name();
    }

    @TypeConverter
    public static WeatherCode stringToWeatherCode(String value) {
        return value == null ? WeatherCode.CLEAR : WeatherCode.getInstance(value);
    }

    @TypeConverter
    public static String windDegreeToString(WindDegree degree) {
        return degree == null ? null : degree.getDegree() + "," + degree.isNoDirection();
    }

    @TypeConverter
    public static WindDegree stringToWindDegree(String value) {
        if (value == null) return new WindDegree(0, true);
        String[] parts = value.split(",");
        float deg = parts.length > 0 ? Float.parseFloat(parts[0]) : 0;
        boolean noDir = parts.length > 1 && Boolean.parseBoolean(parts[1]);
        return new WindDegree(deg, noDir);
    }

    @TypeConverter
    public static String timeZoneToString(TimeZone tz) {
        return tz == null ? "UTC" : tz.getID();
    }

    @TypeConverter
    public static TimeZone stringToTimeZone(String value) {
        return TimeZone.getTimeZone(value != null ? value : "UTC");
    }

    @TypeConverter
    public static String weatherSourceToString(WeatherSource source) {
        return source == null ? null : source.getId();
    }

    @TypeConverter
    public static WeatherSource stringToWeatherSource(String value) {
        return value != null ? WeatherSource.getInstance(value) : WeatherSource.ACCU;
    }
}
