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

import wangdaye.com.geometricweather.common.basic.models.options.appearance.CardDisplay;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doReturn;

// Was PowerMock, which cannot run on JDK 17 (InaccessibleObjectException: module java.base does not
// "opens java.lang"). Robolectric supplies a real TextUtils, so no static mocking is needed at all.
// Robolectric 4.12.2 ships no SDK 35 sandbox; targetSdk is 35, so pin the runtime to 34.
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class CardDisplayTest {

    @Test
    public void toCardDisplayList() {
        String value = "daily_overview&hourly_overview&air_quality&allergen&life_details&sunrise_sunset";

        List<CardDisplay> list = CardDisplay.toCardDisplayList(value);

        Assert.assertEquals(list.get(0), CardDisplay.CARD_DAILY_OVERVIEW);
        Assert.assertEquals(list.get(1), CardDisplay.CARD_HOURLY_OVERVIEW);
        Assert.assertEquals(list.get(2), CardDisplay.CARD_AIR_QUALITY);
        Assert.assertEquals(list.get(3), CardDisplay.CARD_ALLERGEN);
        Assert.assertEquals(list.get(4), CardDisplay.CARD_LIFE_DETAILS);
        Assert.assertEquals(list.get(5), CardDisplay.CARD_SUNRISE_SUNSET);
    }

    /** The real TextUtils.isEmpty short-circuits an empty value to an empty list. */
    @Test
    public void toCardDisplayListOfEmptyValue() {
        Assert.assertTrue(CardDisplay.toCardDisplayList("").isEmpty());
    }

    @Test
    public void toValue() {
        List<CardDisplay> list = new ArrayList<>();
        list.add(CardDisplay.CARD_DAILY_OVERVIEW);
        list.add(CardDisplay.CARD_HOURLY_OVERVIEW);
        list.add(CardDisplay.CARD_AIR_QUALITY);
        list.add(CardDisplay.CARD_ALLERGEN);
        list.add(CardDisplay.CARD_LIFE_DETAILS);
        list.add(CardDisplay.CARD_SUNRISE_SUNSET);

        String value = "daily_overview&hourly_overview&air_quality&allergen&life_details&sunrise_sunset";

        Assert.assertEquals(CardDisplay.toValue(list), value);
    }

    @Test
    public void getSummary() {
        Context context = Mockito.mock(Context.class);
        doReturn("Name").when(context).getString(anyInt());

        List<CardDisplay> list = new ArrayList<>();
        list.add(CardDisplay.CARD_DAILY_OVERVIEW);
        list.add(CardDisplay.CARD_HOURLY_OVERVIEW);
        list.add(CardDisplay.CARD_AIR_QUALITY);
        list.add(CardDisplay.CARD_ALLERGEN);
        list.add(CardDisplay.CARD_LIFE_DETAILS);
        list.add(CardDisplay.CARD_SUNRISE_SUNSET);

        Assert.assertEquals("Name, Name, Name, Name, Name, Name",
                CardDisplay.getSummary(context, list));
    }
}
