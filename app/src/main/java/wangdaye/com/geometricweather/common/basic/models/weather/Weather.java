package wangdaye.com.geometricweather.common.basic.models.weather;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;

import wangdaye.com.geometricweather.common.utils.DisplayUtils;

public class Weather
        implements Serializable {

    private static final long ONE_HOUR = 60 * 60 * 1000L;

    @NonNull private final Base base;
    @NonNull private final Current current;
    @Nullable private History yesterday;
    @NonNull private final List<Daily> dailyForecast;
    @NonNull private final List<Hourly> hourlyForecast;
    @NonNull private final List<Minutely> minutelyForecast;
    @NonNull private final List<Alert> alertList;

    public Weather(@NonNull Base base, @NonNull Current current, @Nullable History yesterday,
                   @NonNull List<Daily> dailyForecast,
                   @NonNull List<Hourly> hourlyForecast,
                   @NonNull List<Minutely> minutelyForecast,
                   @NonNull List<Alert> alertList) {
        this.base = base;
        this.current = current;
        this.yesterday = yesterday;
        this.dailyForecast = dailyForecast;
        this.hourlyForecast = hourlyForecast;
        this.minutelyForecast = minutelyForecast;
        this.alertList = alertList;
    }

    @NonNull
    public Base getBase() {
        return base;
    }

    @NonNull
    public Current getCurrent() {
        return current;
    }

    public void setYesterday(@Nullable History yesterday) {
        this.yesterday = yesterday;
    }

    @Nullable
    public History getYesterday() {
        return yesterday;
    }

    @NonNull
    public List<Daily> getDailyForecast() {
        return dailyForecast;
    }

    /**
     * The day at {@code index}, or null when the source answered with fewer days than the caller
     * wants. Widgets and notifications lay out a fixed 5 days, but the shortest source gives 3
     * (WeatherAPI's free tier), so a fixed index is an out-of-bounds crash waiting for a source
     * switch. Only "daily has at least one entry" is guaranteed (by WeatherHelper / DatabaseHelper).
     */
    @Nullable
    public Daily getDaily(int index) {
        return index >= 0 && index < dailyForecast.size() ? dailyForecast.get(index) : null;
    }

    @NonNull
    public List<Hourly> getHourlyForecast() {
        return hourlyForecast;
    }

    /**
     * The same weather with the hours that have already gone by dropped, so an hourly view opens at
     * the hour it is now. Providers answer in whole days, so without this the series still starts at
     * 00:00 when it is 11 pm — most of the card is then a log of hours nobody can act on.
     *
     * The hour containing {@code time} is kept: it is the one happening now, not a past one.
     *
     * Returns this weather untouched when nothing would be dropped, and also when *everything*
     * would be — a stale forecast is still worth showing, an empty card is not.
     */
    @NonNull
    public Weather withHoursFrom(long time) {
        List<Hourly> upcoming = new ArrayList<>();
        for (Hourly hourly : hourlyForecast) {
            if (hourly.getTime() + ONE_HOUR > time) {
                upcoming.add(hourly);
            }
        }
        if (upcoming.isEmpty() || upcoming.size() == hourlyForecast.size()) {
            return this;
        }
        return new Weather(
                base, current, yesterday, dailyForecast, upcoming, minutelyForecast, alertList);
    }

    /**
     * The same weather with the hours from {@code time} on dropped — a 16-day source answers 384
     * hourly points, and past day three an hourly view is scroll length, not information. The hour
     * starting exactly at {@code time} goes: it is already the far side of the horizon.
     *
     * Same two escapes as {@link #withHoursFrom(long)}: unchanged when nothing would go, and
     * unchanged when everything would.
     */
    @NonNull
    public Weather withHoursUntil(long time) {
        List<Hourly> upcoming = new ArrayList<>();
        for (Hourly hourly : hourlyForecast) {
            if (hourly.getTime() < time) {
                upcoming.add(hourly);
            }
        }
        if (upcoming.isEmpty() || upcoming.size() == hourlyForecast.size()) {
            return this;
        }
        return new Weather(
                base, current, yesterday, dailyForecast, upcoming, minutelyForecast, alertList);
    }

    /**
     * The same weather with the days that are already over dropped, so {@code dailyForecast[0]} is
     * today. Providers lag: a domestic source can still be serving yesterday as its first day for
     * hours after midnight, and the whole app — header, widgets, notifications — reads index 0 as
     * "today", so a stale leading day is not a cosmetic problem.
     *
     * Same two escapes as {@link #withHoursFrom(long)}: unchanged when nothing would go, and
     * unchanged when everything would.
     */
    @NonNull
    public Weather withDaysFrom(long time) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(time);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        long startOfToday = calendar.getTimeInMillis();

        List<Daily> upcoming = new ArrayList<>();
        for (Daily daily : dailyForecast) {
            if (daily.getTime() >= startOfToday) {
                upcoming.add(daily);
            }
        }
        if (upcoming.isEmpty() || upcoming.size() == dailyForecast.size()) {
            return this;
        }
        return new Weather(
                base, current, yesterday, upcoming, hourlyForecast, minutelyForecast, alertList);
    }

    @NonNull
    public List<Minutely> getMinutelyForecast() {
        return minutelyForecast;
    }

    @NonNull
    public List<Alert> getAlertList() {
        return alertList;
    }

    public boolean isValid(float pollingIntervalHours) {
        long updateTime = base.getUpdateTime();
        long currentTime = System.currentTimeMillis();
        return currentTime >= updateTime
                && currentTime - updateTime < pollingIntervalHours * 60 * 60 * 1000;
    }

    public boolean isDaylight(TimeZone timeZone) {
        if (getDailyForecast().isEmpty() || getDailyForecast().get(0).sun() == null) {
            return DisplayUtils.isDaylight(timeZone);
        }
        Date riseDate = getDailyForecast().get(0).sun().getRiseDate();
        Date setDate = getDailyForecast().get(0).sun().getSetDate();
        if (riseDate == null || setDate == null) {
            return DisplayUtils.isDaylight(timeZone);
        }

        Calendar calendar = Calendar.getInstance();
        calendar.setTimeZone(timeZone);
        long timestamp = calendar.getTime().getTime();

        return riseDate.getTime() < timestamp
                && timestamp < setDate.getTime();
    }
}
