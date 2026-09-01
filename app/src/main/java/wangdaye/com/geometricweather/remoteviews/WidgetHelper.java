package wangdaye.com.geometricweather.remoteviews;

import android.content.Context;
import android.text.TextPaint;

import java.util.Calendar;
import java.util.Date;
import java.util.List;

import wangdaye.com.geometricweather.R;
import wangdaye.com.geometricweather.common.basic.models.Location;
import wangdaye.com.geometricweather.common.basic.models.options.unit.TemperatureUnit;
import wangdaye.com.geometricweather.common.basic.models.weather.Daily;
import wangdaye.com.geometricweather.common.basic.models.weather.Weather;
import wangdaye.com.geometricweather.remoteviews.presenters.MaterialYouCurrentWidgetIMP;
import wangdaye.com.geometricweather.remoteviews.presenters.MaterialYouForecastWidgetIMP;
import wangdaye.com.geometricweather.remoteviews.presenters.ClockDayDetailsWidgetIMP;
import wangdaye.com.geometricweather.remoteviews.presenters.ClockDayHorizontalWidgetIMP;
import wangdaye.com.geometricweather.remoteviews.presenters.ClockDayVerticalWidgetIMP;
import wangdaye.com.geometricweather.remoteviews.presenters.ClockDayWeekWidgetIMP;
import wangdaye.com.geometricweather.remoteviews.presenters.DailyTrendWidgetIMP;
import wangdaye.com.geometricweather.remoteviews.presenters.DayWeekWidgetIMP;
import wangdaye.com.geometricweather.remoteviews.presenters.DayWidgetIMP;
import wangdaye.com.geometricweather.remoteviews.presenters.HourlyTrendWidgetIMP;
import wangdaye.com.geometricweather.remoteviews.presenters.MultiCityWidgetIMP;
import wangdaye.com.geometricweather.remoteviews.presenters.TextWidgetIMP;
import wangdaye.com.geometricweather.remoteviews.presenters.WeekWidgetIMP;

public class WidgetHelper {

    public static void updateWidgetIfNecessary(Context context, Location location) {
        if (DayWidgetIMP.isEnable(context)) {
            DayWidgetIMP.updateWidgetView(context, location);
        }
        if (WeekWidgetIMP.isEnable(context)) {
            WeekWidgetIMP.updateWidgetView(context, location);
        }
        if (DayWeekWidgetIMP.isEnable(context)) {
            DayWeekWidgetIMP.updateWidgetView(context, location);
        }
        if (ClockDayHorizontalWidgetIMP.isEnable(context)) {
            ClockDayHorizontalWidgetIMP.updateWidgetView(context, location);
        }
        if (ClockDayVerticalWidgetIMP.isEnable(context)) {
            ClockDayVerticalWidgetIMP.updateWidgetView(context, location);
        }
        if (ClockDayWeekWidgetIMP.isEnable(context)) {
            ClockDayWeekWidgetIMP.updateWidgetView(context, location);
        }
        if (ClockDayDetailsWidgetIMP.isEnable(context)) {
            ClockDayDetailsWidgetIMP.updateWidgetView(context, location);
        }
        if (TextWidgetIMP.isEnable(context)) {
            TextWidgetIMP.updateWidgetView(context, location);
        }
        if (DailyTrendWidgetIMP.isEnable(context)) {
            DailyTrendWidgetIMP.updateWidgetView(context, location);
        }
        if (HourlyTrendWidgetIMP.isEnable(context)) {
            HourlyTrendWidgetIMP.updateWidgetView(context, location);
        }

        // material you.
        if (MaterialYouForecastWidgetIMP.isEnable(context)) {
            MaterialYouForecastWidgetIMP.updateWidgetView(context, location);
        }
        if (MaterialYouCurrentWidgetIMP.isEnable(context)) {
            MaterialYouCurrentWidgetIMP.updateWidgetView(context, location);
        }
    }

    public static void updateWidgetIfNecessary(Context context, List<Location> locationList) {
        locationList = Location.excludeInvalidResidentLocation(context, locationList);
        if (MultiCityWidgetIMP.isEnable(context)) {
            MultiCityWidgetIMP.updateWidgetView(context, locationList);
        }
    }

    public static String[] buildWidgetDayStyleText(Context context, Weather weather, TemperatureUnit unit) {
        String[] texts = new String[] {
                weather.getCurrent().getWeatherText(),
                weather.getCurrent().getTemperature().getTemperature(context, unit),
                weather.getDailyForecast().get(0).day().getTemperature().getShortTemperature(context, unit),
                weather.getDailyForecast().get(0).night().getTemperature().getShortTemperature(context, unit)
        };

        TextPaint paint = new TextPaint();

        float[] widths = new float[4];
        for (int i = 0; i < widths.length; i ++) {
            widths[i] = paint.measureText(texts[i]);
        }

        float maxiWidth = widths[0];
        for (float w : widths) {
            if (w > maxiWidth) {
                maxiWidth = w;
            }
        }

        while (true) {
            boolean[] flags = new boolean[] {false, false, false, false};

            for (int i = 0; i < 2; i ++) {
                if (widths[i] < maxiWidth) {
                    texts[i] = texts[i] + " ";
                    widths[i] = paint.measureText(texts[i]);
                } else {
                    flags[i] = true;
                }
            }
            for (int i = 2; i < 4; i ++) {
                if (widths[i] < maxiWidth) {
                    texts[i] = " " + texts[i];
                    widths[i] = paint.measureText(texts[i]);
                } else {
                    flags[i] = true;
                }
            }

            int n = 0;
            for (boolean flag : flags) {
                if (flag) {
                    n ++;
                }
            }
            if (n == 4) {
                break;
            }
        }

        return new String[] {
                texts[0] + "\n" + texts[1],
                texts[2] + "\n" + texts[3]
        };
    }

    public static String getWeek(Context context) {
        Calendar c = Calendar.getInstance();
        int week = c.get(Calendar.DAY_OF_WEEK);
        switch (week) {
            case Calendar.SUNDAY:
                return context.getString(R.string.week_7);

            case Calendar.MONDAY:
                return context.getString(R.string.week_1);

            case Calendar.TUESDAY:
                return context.getString(R.string.week_2);

            case Calendar.WEDNESDAY:
                return context.getString(R.string.week_3);

            case Calendar.THURSDAY:
                return context.getString(R.string.week_4);

            case Calendar.FRIDAY:
                return context.getString(R.string.week_5);

            case Calendar.SATURDAY:
                return context.getString(R.string.week_6);

            default:
                return "";
        }
    }

    public static String getDailyWeek(Context context, Weather weather, int index) {
        Daily daily = weather.getDaily(index);
        if (daily == null) {
            // The source gave fewer days than the widget has slots (WeatherAPI: 3). Leave it blank.
            return "";
        }
        if (index > 1) {
            return daily.getWeek(context);
        }

        // The place's own calendar: for a location a few hours off, the device's would call the
        // wrong day "today". Every day carries its zone (WeatherHelper / DatabaseHelper fill it in).
        Calendar today = Calendar.getInstance(daily.getTimeZone());
        today.setTime(new Date());

        Calendar publish = Calendar.getInstance(daily.getTimeZone());
        publish.setTime(weather.getDailyForecast().get(0).getDate());

        boolean sameYear = today.get(Calendar.YEAR) == publish.get(Calendar.YEAR);
        boolean publishedToday = sameYear
                && today.get(Calendar.DAY_OF_YEAR) == publish.get(Calendar.DAY_OF_YEAR);
        boolean publishedYesterday = sameYear
                && today.get(Calendar.DAY_OF_YEAR) == publish.get(Calendar.DAY_OF_YEAR) + 1;

        if (index == 0) {
            if (publishedToday) {
                return context.getString(R.string.today);
            }
            if (publishedYesterday) {
                return context.getString(R.string.yesterday);
            }
            return daily.getWeek(context);
        }
        // index == 1.
        return publishedYesterday ? context.getString(R.string.today) : daily.getWeek(context);
    }

    public static float getNonNullValue(Float value, float defaultValue) {
        return value == null ? defaultValue : value;
    }

    public static int getNonNullValue(Integer value, int defaultValue) {
        return value == null ? defaultValue : value;
    }
}
