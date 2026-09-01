package wangdaye.com.geometricweather.common.basic.models.weather;

import android.annotation.SuppressLint;
import android.content.Context;

import androidx.annotation.Size;

import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;

import wangdaye.com.geometricweather.R;
import wangdaye.com.geometricweather.common.utils.helpers.LunarHelper;

/**
 * Daily.
 *
 * All properties are {@link androidx.annotation.NonNull}.
 * */
public class Daily implements Serializable {

    private final Date date;
    private final long time;

    @Size(2) private final HalfDay[] halfDays;
    @Size(2) private final Astro[] astros;
    private final MoonPhase moonPhase;
    private final AirQuality airQuality;
    private final Pollen pollen;
    private final UV uv;
    private final float hoursOfSun;

    /**
     * The zone of the place this day belongs to, so a weekday name and a date read as they do
     * <em>there</em>. Filled in at the two points every weather passes through (WeatherHelper for a
     * fresh answer, DatabaseHelper for a cached one); null means nobody did, and the formatters then
     * fall back to the device's zone — which is what they always used.
     */
    private TimeZone timeZone;

    public Daily(Date date, long time,
                 HalfDay day, HalfDay night, Astro sun, Astro moon,
                 MoonPhase moonPhase, AirQuality airQuality, Pollen pollen, UV uv,
                 float hoursOfSun) {
        this.date = date;
        this.time = time;
        this.halfDays = new HalfDay[] {day, night};
        // @NonNull is not runtime-enforced in Java; coordinate providers (Open-Meteo/OWM) leave
        // these null. Coerce to empty defaults so all consumers can rely on non-null values.
        this.astros = new Astro[] {
                sun != null ? sun : new Astro(null, null),
                moon != null ? moon : new Astro(null, null)
        };
        this.moonPhase = moonPhase != null ? moonPhase : new MoonPhase(null, null);
        this.airQuality = airQuality != null ? airQuality
                : new AirQuality(null, null, null, null, null, null, null, null);
        this.pollen = pollen != null ? pollen
                : new Pollen(null, null, null, null, null, null, null, null, null, null, null, null);
        this.uv = uv;
        this.hoursOfSun = hoursOfSun;
    }

    public HalfDay day() {
        return halfDays[0];
    }

    public HalfDay night() {
        return halfDays[1];
    }

    public Astro sun() {
        return astros[0];
    }

    public Astro moon() {
        return astros[1];
    }

    public Date getDate() {
        return date;
    }

    public long getTime() {
        return time;
    }

    public MoonPhase getMoonPhase() {
        return moonPhase;
    }

    public AirQuality getAirQuality() {
        return airQuality;
    }

    public Pollen getPollen() {
        return pollen;
    }

    public UV getUV() {
        return uv;
    }

    public float getHoursOfSun() {
        return hoursOfSun;
    }

    public void setTimeZone(TimeZone timeZone) {
        this.timeZone = timeZone;
    }

    /** The place's zone, or the device's when it was never filled in. */
    public TimeZone getTimeZone() {
        return timeZone != null ? timeZone : TimeZone.getDefault();
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

    public String getLunar() {
        return LunarHelper.getLunarDate(date);
    }

    public boolean isToday(TimeZone timeZone) {
        // Both calendars have to read the same clock: comparing "now there" against a date broken
        // down in the device's zone puts the boundary in the wrong place for a place a few hours off.
        Calendar current = Calendar.getInstance(timeZone);

        Calendar thisDay = Calendar.getInstance(timeZone);
        thisDay.setTime(date);

        return current.get(Calendar.YEAR) == thisDay.get(Calendar.YEAR)
                && current.get(Calendar.DAY_OF_YEAR) == thisDay.get(Calendar.DAY_OF_YEAR);
    }
}
