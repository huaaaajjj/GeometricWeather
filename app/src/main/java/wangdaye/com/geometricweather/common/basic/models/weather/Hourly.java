package wangdaye.com.geometricweather.common.basic.models.weather;

import android.annotation.SuppressLint;
import android.content.Context;
import android.text.BidiFormatter;

import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;

import wangdaye.com.geometricweather.R;
import wangdaye.com.geometricweather.common.utils.DisplayUtils;

/**
 * Hourly.
 *
 * All properties are {@link androidx.annotation.NonNull}.
 * */
public class Hourly implements Serializable {

    private final Date date;
    private final long time;
    private final boolean daylight;

    private final String weatherText;
    private final WeatherCode weatherCode;

    private final Temperature temperature;
    private final Precipitation precipitation;
    private final PrecipitationProbability precipitationProbability;
    private final Wind wind;
    private final UV uv;

    /**
     * The zone of the place this hour belongs to, so "15时" is 15:00 there rather than on the phone.
     * Filled in at the two points every weather passes through (WeatherHelper for a fresh answer,
     * DatabaseHelper for a cached one); null falls back to the device's zone, as before.
     */
    private TimeZone timeZone;

    public Hourly(Date date, long time, boolean daylight,
                  String weatherText, WeatherCode weatherCode,
                  Temperature temperature,
                  Precipitation precipitation, PrecipitationProbability precipitationProbability,
                  Wind wind, UV uv) {
        this.date = date;
        this.time = time;
        this.daylight = daylight;
        this.weatherText = weatherText;
        this.weatherCode = weatherCode;
        this.temperature = temperature;
        this.precipitation = precipitation;
        this.precipitationProbability = precipitationProbability;
        this.wind = wind;
        this.uv = uv;
    }

    public Date getDate() {
        return date;
    }

    public long getTime() {
        return time;
    }

    public boolean isDaylight() {
        return daylight;
    }

    public String getWeatherText() {
        return weatherText;
    }

    public WeatherCode getWeatherCode() {
        return weatherCode;
    }

    public Temperature getTemperature() {
        return temperature;
    }

    public Precipitation getPrecipitation() {
        return precipitation;
    }

    public PrecipitationProbability getPrecipitationProbability() {
        return precipitationProbability;
    }

    public Wind getWind() {
        return wind;
    }

    public UV getUV() {
        return uv;
    }

    public void setTimeZone(TimeZone timeZone) {
        this.timeZone = timeZone;
    }

    /** The place's zone, or the device's when it was never filled in. */
    public TimeZone getTimeZone() {
        return timeZone != null ? timeZone : TimeZone.getDefault();
    }

    public int getHourIn24Format() {
        Calendar calendar = Calendar.getInstance(getTimeZone());
        calendar.setTime(date);
        return calendar.get(Calendar.HOUR_OF_DAY);
    }

    public String getHour(Context context) {
        return getHour(context, DisplayUtils.is12Hour(context), DisplayUtils.isRtl(context));
    }

    @SuppressLint("DefaultLocale")
    private String getHour(Context context, boolean twelveHour, boolean rtl) {
        Calendar calendar = Calendar.getInstance(getTimeZone());
        calendar.setTime(date);

        int hour;
        if (twelveHour) {
            hour = calendar.get(Calendar.HOUR);
            if (hour == 0) {
                hour = 12;
            }
        } else {
            hour = calendar.get(Calendar.HOUR_OF_DAY);
        }

        if (rtl) {
            return BidiFormatter.getInstance().unicodeWrap(String.format("%d", hour))
                    + context.getString(R.string.of_clock);
        } else {
            return hour + context.getString(R.string.of_clock);
        }
    }

    public String getLongDate(Context context) {
        return getDate(context.getString(R.string.date_format_long));
    }

    public String getShortDate(Context context) {
        return getDate(context.getString(R.string.date_format_short));
    }

    @SuppressLint("SimpleDateFormat")
    public String getDate(String format) {
        SimpleDateFormat df = new SimpleDateFormat(format);
        df.setTimeZone(getTimeZone());
        return df.format(date);
    }

    public String getWeek(Context context) {
        Calendar calendar = Calendar.getInstance(getTimeZone());
        calendar.setTime(date);

        int day = calendar.get(Calendar.DAY_OF_WEEK);
        if (day == 1){
            return context.getString(R.string.week_7);
        } else if (day == 2) {
            return context.getString(R.string.week_1);
        } else if (day == 3) {
            return context.getString(R.string.week_2);
        } else if (day == 4) {
            return context.getString(R.string.week_3);
        } else if (day == 5) {
            return context.getString(R.string.week_4);
        } else if (day == 6) {
            return context.getString(R.string.week_5);
        } else {
            return context.getString(R.string.week_6);
        }
    }

    public boolean isToday(TimeZone timeZone) {
        // Both calendars have to read the same clock — see Daily.isToday.
        Calendar current = Calendar.getInstance(timeZone);

        Calendar thisDay = Calendar.getInstance(timeZone);
        thisDay.setTime(date);

        return current.get(Calendar.YEAR) == thisDay.get(Calendar.YEAR)
                && current.get(Calendar.DAY_OF_YEAR) == thisDay.get(Calendar.DAY_OF_YEAR);
    }
}