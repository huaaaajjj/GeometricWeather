package remoteviews;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import android.content.Context;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.RemoteViews;
import android.widget.TextView;

import androidx.test.core.app.ApplicationProvider;

import com.google.gson.Gson;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.TimeZone;

import wangdaye.com.geometricweather.common.basic.models.Location;
import wangdaye.com.geometricweather.common.basic.models.options.provider.WeatherSource;
import wangdaye.com.geometricweather.common.basic.models.weather.Weather;
import wangdaye.com.geometricweather.R;
import wangdaye.com.geometricweather.remoteviews.WidgetHelper;
import wangdaye.com.geometricweather.remoteviews.presenters.ClockDayWeekWidgetIMP;
import wangdaye.com.geometricweather.weather.converters.OpenMeteoResultConverter;
import wangdaye.com.geometricweather.weather.json.openmeteo.OpenMeteoResult;

/**
 * The week widgets and the expanded notification lay out a fixed five days, but the shortest source
 * answers with three (WeatherAPI's free tier), so every one of those slots was an out-of-bounds
 * crash the moment the user switched source — thrown from the IO thread that refreshes remote
 * views, which takes the whole app down on launch, not just the widget.
 *
 * What is pinned here is that a missing day reads as absent rather than throwing.
 */
@RunWith(RobolectricTestRunner.class)
// Robolectric 4.12.2 ships no SDK 35 sandbox; targetSdk is 35, so pin the runtime to 34.
@Config(sdk = 34)
public class ShortDailyForecastTest {

    private Context mContext;
    private Location mLocation;
    private Weather mThreeDays;

    @Before
    public void setUp() {
        mContext = ApplicationProvider.getApplicationContext();
        mLocation = new Location(
                "beijing",
                39.9042f, 116.4074f,
                TimeZone.getTimeZone("Asia/Shanghai"),
                "中国", "北京市", "北京市", "",
                null,
                WeatherSource.OPEN_METEO,
                false, false, true
        );
        mThreeDays = truncateToThreeDays(fixtureWeather());
    }

    @Test
    public void dailyBeyondTheListIsAbsentInsteadOfThrowing() {
        assertEquals(3, mThreeDays.getDailyForecast().size());
        assertNotNull(mThreeDays.getDaily(0));
        assertNotNull(mThreeDays.getDaily(2));
        assertNull("day 4 of a 3-day source must read as absent", mThreeDays.getDaily(3));
        assertNull(mThreeDays.getDaily(4));
        assertNull(mThreeDays.getDaily(-1));
    }

    /** The exact crash the user hit: widget slot 4 of a 3-day source. */
    @Test
    public void widgetWeekLabelIsBlankPastTheLastDay() {
        assertEquals("", WidgetHelper.getDailyWeek(mContext, mThreeDays, 3));
        assertEquals("", WidgetHelper.getDailyWeek(mContext, mThreeDays, 4));
    }

    /** …while the days the source did give still read the same as before. */
    @Test
    public void widgetWeekLabelStillNamesTheDaysThatExist() {
        assertEquals(
                mThreeDays.getDailyForecast().get(2).getWeek(mContext),
                WidgetHelper.getDailyWeek(mContext, mThreeDays, 2)
        );
        // Slots 0 and 1 go through the today/yesterday branch instead of the plain weekday name.
        for (int index : new int[] {0, 1}) {
            assertNotEquals("slot " + index + " must not be blank", "",
                    WidgetHelper.getDailyWeek(mContext, mThreeDays, index));
        }
    }

    /**
     * The widget the crash came from, rendered end to end: the three days the source gave keep
     * their temperature, the two slots it cannot fill go blank, and applying the views throws
     * nothing (a missing icon is a null Uri, which has to survive RemoteViews.apply).
     */
    @Test
    public void clockDayWeekWidgetFillsTheDaysItHasAndBlanksTheRest() {
        RemoteViews views = ClockDayWeekWidgetIMP.getRemoteViews(
                mContext,
                Location.copy(mLocation, mThreeDays),
                "auto", 100, "auto", 100, "light", false
        );
        View rendered = views.apply(mContext, new FrameLayout(mContext));

        int[] tempIds = new int[] {
                R.id.widget_clock_day_week_temp_1, R.id.widget_clock_day_week_temp_2,
                R.id.widget_clock_day_week_temp_3, R.id.widget_clock_day_week_temp_4,
                R.id.widget_clock_day_week_temp_5
        };
        for (int i = 0; i < 3; i ++) {
            assertNotEquals(
                    "slot " + (i + 1) + " is a day the source gave — it must still show a temperature",
                    "",
                    text(rendered, tempIds[i])
            );
        }
        assertEquals("", text(rendered, tempIds[3]));
        assertEquals("", text(rendered, tempIds[4]));
    }

    private static String text(View rendered, int id) {
        return ((TextView) rendered.findViewById(id)).getText().toString();
    }

    private Weather fixtureWeather() {
        InputStream in = getClass().getClassLoader()
                .getResourceAsStream("openmeteo/forecast.json");
        assertNotNull("fixture missing: openmeteo/forecast.json", in);
        OpenMeteoResult result = new Gson().fromJson(
                new InputStreamReader(in, StandardCharsets.UTF_8), OpenMeteoResult.class);
        Weather weather = OpenMeteoResultConverter.convert(mContext, mLocation, result);
        assertNotNull(weather);
        return weather;
    }

    /** What a WeatherAPI free-tier answer looks like next to a 16-day one. */
    private static Weather truncateToThreeDays(Weather weather) {
        return new Weather(
                weather.getBase(),
                weather.getCurrent(),
                weather.getYesterday(),
                new ArrayList<>(weather.getDailyForecast().subList(0, 3)),
                weather.getHourlyForecast(),
                weather.getMinutelyForecast(),
                weather.getAlertList()
        );
    }
}
