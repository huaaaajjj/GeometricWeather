package basic.option.appearance;

import android.content.Context;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.List;

import wangdaye.com.geometricweather.common.basic.models.options.appearance.DailyTrendDisplay;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doReturn;

// See CardDisplayTest: PowerMock does not run on JDK 17, and Robolectric's real TextUtils removes
// the need for static mocking.
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class DailyTrendDisplayTest {

    @Test
    public void toDailyTrendDisplayList() {
        String value = "temperature&air_quality&wind&uv_index&precipitation";

        List<DailyTrendDisplay> list = DailyTrendDisplay.toDailyTrendDisplayList(value);

        Assert.assertEquals(list.get(0), DailyTrendDisplay.TAG_TEMPERATURE);
        Assert.assertEquals(list.get(1), DailyTrendDisplay.TAG_AIR_QUALITY);
        Assert.assertEquals(list.get(2), DailyTrendDisplay.TAG_WIND);
        Assert.assertEquals(list.get(3), DailyTrendDisplay.TAG_UV_INDEX);
        Assert.assertEquals(list.get(4), DailyTrendDisplay.TAG_PRECIPITATION);
    }

    /** The real TextUtils.isEmpty short-circuits an empty value to an empty list. */
    @Test
    public void toDailyTrendDisplayListOfEmptyValue() {
        Assert.assertTrue(DailyTrendDisplay.toDailyTrendDisplayList("").isEmpty());
    }

    @Test
    public void toValue() {
        List<DailyTrendDisplay> list = new ArrayList<>();
        list.add(DailyTrendDisplay.TAG_TEMPERATURE);
        list.add(DailyTrendDisplay.TAG_AIR_QUALITY);
        list.add(DailyTrendDisplay.TAG_WIND);
        list.add(DailyTrendDisplay.TAG_UV_INDEX);
        list.add(DailyTrendDisplay.TAG_PRECIPITATION);

        String value = "temperature&air_quality&wind&uv_index&precipitation";

        Assert.assertEquals(DailyTrendDisplay.toValue(list), value);
    }

    @Test
    public void getSummary() {
        Context context = Mockito.mock(Context.class);
        doReturn("Name").when(context).getString(anyInt());

        List<DailyTrendDisplay> list = new ArrayList<>();
        list.add(DailyTrendDisplay.TAG_TEMPERATURE);
        list.add(DailyTrendDisplay.TAG_AIR_QUALITY);
        list.add(DailyTrendDisplay.TAG_WIND);
        list.add(DailyTrendDisplay.TAG_UV_INDEX);
        list.add(DailyTrendDisplay.TAG_PRECIPITATION);

        Assert.assertEquals("Name, Name, Name, Name, Name",
                DailyTrendDisplay.getSummary(context, list));
    }
}
