package search;

import static org.junit.Assert.assertEquals;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.Collections;
import java.util.TimeZone;

import wangdaye.com.geometricweather.common.basic.models.Location;
import wangdaye.com.geometricweather.common.basic.models.options.provider.WeatherSource;
import wangdaye.com.geometricweather.search.ui.adapter.location.LocationAdapter;

/**
 * The drag scroll bar asks the adapter for its indicator text from inside {@code onLayout}, passing
 * the layout manager's first visible position — which is -1 whenever nothing has been laid out yet.
 * An unguarded {@code get()} there throws during layout, i.e. fatally: a real-device search that
 * returned a single result took the app down this way.
 */
@RunWith(RobolectricTestRunner.class)
// Robolectric 4.12.2 ships no SDK 35 sandbox; targetSdk is 35, so pin the runtime to 34.
@Config(sdk = 34)
public class LocationAdapterTest {

    @Test
    public void anOutOfRangeElementYieldsNoIndicatorTextInsteadOfThrowing() {
        Context context = ApplicationProvider.getApplicationContext();
        LocationAdapter adapter = new LocationAdapter(
                context,
                Collections.singletonList(shucheng()),
                (view, formattedId) -> {}
        );

        assertEquals(1, adapter.getItemCount());
        assertEquals("nothing laid out yet", "", adapter.getCustomStringForElement(-1));
        assertEquals("past the end", "", adapter.getCustomStringForElement(1));
        assertEquals(WeatherSource.COMPOSITE.getSourceUrl(),
                adapter.getCustomStringForElement(0));
    }

    private static Location shucheng() {
        return new Location(
                "101221507",
                31.462849f, 116.94409f,
                TimeZone.getTimeZone("Asia/Shanghai"),
                "中国", "安徽", "六安", "舒城",
                null,
                WeatherSource.COMPOSITE,
                false, false, true
        );
    }
}
