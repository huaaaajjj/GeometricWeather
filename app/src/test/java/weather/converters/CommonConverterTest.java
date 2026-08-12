package weather.converters;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import wangdaye.com.geometricweather.weather.converters.CommonConverter;

/**
 * The app is on the China AQI standard throughout — CaiYun takes aqi.chn rather than aqi.usa, and
 * AirQuality's per-pollutant colour thresholds are the GB 3095-2012 limits. Sources that hand back
 * a category number instead of an AQI (WeatherAPI's us-epa-index 1..6, OWM's main.aqi 1..5) used to
 * write that number straight into the model, where it is read as a 0..500 AQI: every category then
 * lands in the <=50 band and the card is green no matter how bad the air is. These tests pin the
 * concentration-based conversion that replaced it.
 */
@RunWith(RobolectricTestRunner.class)
// Robolectric 4.12.2 ships no SDK 35 sandbox; targetSdk is 35, so pin the runtime to 34.
@Config(sdk = 34)
public class CommonConverterTest {

    private static int aqi(Float pm25, Float pm10) {
        Integer index = CommonConverter.getAqiIndexFromConcentration(pm25, pm10);
        if (index == null) {
            throw new AssertionError("expected an index");
        }
        return index;
    }

    /** The breakpoints must be the ones AirQuality's own colour bands use, or the number and the
     * colour on a single card disagree. */
    @Test
    public void pm25SegmentsFollowTheChinaStandard() {
        // Segment edges map exactly onto the AQI band edges.
        assertEquals(0, aqi(0f, null));
        assertEquals(50, aqi(35f, null));
        assertEquals(100, aqi(75f, null));
        assertEquals(150, aqi(115f, null));
        assertEquals(200, aqi(150f, null));
        assertEquals(300, aqi(250f, null));
        assertEquals(400, aqi(350f, null));
        assertEquals(500, aqi(500f, null));

        // Inside a segment the value is interpolated, not snapped to the edge.
        assertEquals(41, aqi(29f, null));
        assertEquals(75, aqi(55f, null));
    }

    @Test
    public void pm10SegmentsFollowTheChinaStandard() {
        assertEquals(50, aqi(null, 50f));
        assertEquals(100, aqi(null, 150f));
        assertEquals(150, aqi(null, 250f));
        assertEquals(200, aqi(null, 350f));
        assertEquals(300, aqi(null, 420f));
        assertEquals(500, aqi(null, 600f));

        assertEquals(33, aqi(null, 32.6f));
    }

    /** AQI is the worst pollutant's sub-index, not an average. */
    @Test
    public void worstPollutantWins() {
        assertEquals(41, aqi(29f, 32.6f));   // pm2.5 41 vs pm10 33
        assertEquals(100, aqi(10f, 150f));   // pm2.5 14 vs pm10 100
    }

    /** A pollutant past the top of the table cannot push the index off the 0..500 scale. */
    @Test
    public void concentrationsAboveTheTableAreCapped() {
        assertEquals(500, aqi(9999f, null));
        assertEquals(500, aqi(null, 9999f));
    }

    /** No concentration means no index — the caller falls back to the provider's category. */
    @Test
    public void missingOrNegativeConcentrationsYieldNull() {
        assertNull(CommonConverter.getAqiIndexFromConcentration(null, null));
        assertNull(CommonConverter.getAqiIndexFromConcentration(-1f, null));
        assertNull(CommonConverter.getAqiIndexFromConcentration(-1f, -1f));
        // One good reading is enough.
        assertEquals(50, aqi(-1f, 50f));
    }

    /**
     * The us-epa-index fallback maps a category to its band midpoint. The midpoint matters: the
     * upper bound would push "moderate" to exactly 100, reading as worse than the source meant.
     */
    @Test
    public void usEpaCategoryFallsBackToBandMidpoints() {
        assertEquals(25, CommonConverter.getAqiIndexFromUsEpaCategory(1).intValue());
        assertEquals(75, CommonConverter.getAqiIndexFromUsEpaCategory(2).intValue());
        assertEquals(125, CommonConverter.getAqiIndexFromUsEpaCategory(3).intValue());
        assertEquals(175, CommonConverter.getAqiIndexFromUsEpaCategory(4).intValue());
        assertEquals(250, CommonConverter.getAqiIndexFromUsEpaCategory(5).intValue());
        assertEquals(400, CommonConverter.getAqiIndexFromUsEpaCategory(6).intValue());

        // Every category must escape the <=50 band except the first, which is the whole point.
        for (int category = 2; category <= 6; category++) {
            org.junit.Assert.assertTrue(
                    CommonConverter.getAqiIndexFromUsEpaCategory(category) > 50);
        }
    }

    @Test
    public void outOfRangeCategoriesYieldNull() {
        assertNull(CommonConverter.getAqiIndexFromUsEpaCategory(null));
        assertNull(CommonConverter.getAqiIndexFromUsEpaCategory(0));
        assertNull(CommonConverter.getAqiIndexFromUsEpaCategory(7));
    }
}
