package wangdaye.com.geometricweather.main.adapters;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.Locale;

import wangdaye.com.geometricweather.R;

/**
 * The detail tiles say the reading in words and put the number in the gauge, so the band a value
 * falls in is now load-bearing. This pins the edges of every band — in the model's own units
 * (percent, millibars, kilometres), which is what the tiles grade on whatever unit they display in.
 *
 * <p>Lives in the adapter's package to reach its package-private band functions.
 */
public class DetailsLevelTest {

    private Locale mDefaultLocale;

    /** The gauge number is formatted for the user's locale, so the assertions need a known one. */
    @Before
    public void pinLocale() {
        mDefaultLocale = Locale.getDefault();
        Locale.setDefault(Locale.US);
    }

    @After
    public void restoreLocale() {
        Locale.setDefault(mDefaultLocale);
    }

    @Test
    public void humidityBands() {
        Assert.assertEquals(R.string.humidity_level_1, DetailsAdapter.humidityLevel(0));
        Assert.assertEquals(R.string.humidity_level_1, DetailsAdapter.humidityLevel(30));
        Assert.assertEquals(R.string.humidity_level_2, DetailsAdapter.humidityLevel(31));
        Assert.assertEquals(R.string.humidity_level_2, DetailsAdapter.humidityLevel(60));
        Assert.assertEquals(R.string.humidity_level_3, DetailsAdapter.humidityLevel(61));
        Assert.assertEquals(R.string.humidity_level_3, DetailsAdapter.humidityLevel(80));
        Assert.assertEquals(R.string.humidity_level_4, DetailsAdapter.humidityLevel(81));
        Assert.assertEquals(R.string.humidity_level_4, DetailsAdapter.humidityLevel(100));
    }

    @Test
    public void pressureBands() {
        Assert.assertEquals(R.string.pressure_level_1, DetailsAdapter.pressureLevel(1008.9f));
        Assert.assertEquals(R.string.pressure_level_2, DetailsAdapter.pressureLevel(1009));
        // Standard pressure is normal, which is the whole point of the middle band.
        Assert.assertEquals(R.string.pressure_level_2, DetailsAdapter.pressureLevel(1013.25f));
        Assert.assertEquals(R.string.pressure_level_2, DetailsAdapter.pressureLevel(1022));
        Assert.assertEquals(R.string.pressure_level_3, DetailsAdapter.pressureLevel(1022.1f));
    }

    @Test
    public void visibilityBands() {
        Assert.assertEquals(R.string.visibility_level_1, DetailsAdapter.visibilityLevel(0));
        Assert.assertEquals(R.string.visibility_level_1, DetailsAdapter.visibilityLevel(0.9f));
        Assert.assertEquals(R.string.visibility_level_2, DetailsAdapter.visibilityLevel(1));
        Assert.assertEquals(R.string.visibility_level_2, DetailsAdapter.visibilityLevel(3.9f));
        Assert.assertEquals(R.string.visibility_level_3, DetailsAdapter.visibilityLevel(4));
        Assert.assertEquals(R.string.visibility_level_3, DetailsAdapter.visibilityLevel(9.9f));
        Assert.assertEquals(R.string.visibility_level_4, DetailsAdapter.visibilityLevel(10));
        Assert.assertEquals(R.string.visibility_level_4, DetailsAdapter.visibilityLevel(50));
    }

    @Test
    public void cloudCoverBands() {
        Assert.assertEquals(R.string.cloud_cover_level_1, DetailsAdapter.cloudCoverLevel(0));
        Assert.assertEquals(R.string.cloud_cover_level_1, DetailsAdapter.cloudCoverLevel(20));
        Assert.assertEquals(R.string.cloud_cover_level_2, DetailsAdapter.cloudCoverLevel(21));
        Assert.assertEquals(R.string.cloud_cover_level_2, DetailsAdapter.cloudCoverLevel(50));
        Assert.assertEquals(R.string.cloud_cover_level_3, DetailsAdapter.cloudCoverLevel(51));
        Assert.assertEquals(R.string.cloud_cover_level_3, DetailsAdapter.cloudCoverLevel(85));
        Assert.assertEquals(R.string.cloud_cover_level_4, DetailsAdapter.cloudCoverLevel(86));
        Assert.assertEquals(R.string.cloud_cover_level_4, DetailsAdapter.cloudCoverLevel(100));
    }

    /** The WHO bands, which is what {@code UV}'s own colour thresholds already are. */
    @Test
    public void uvBands() {
        Assert.assertEquals(R.string.uv_level_1, DetailsAdapter.uvLevel(0));
        Assert.assertEquals(R.string.uv_level_1, DetailsAdapter.uvLevel(2));
        Assert.assertEquals(R.string.uv_level_2, DetailsAdapter.uvLevel(3));
        Assert.assertEquals(R.string.uv_level_2, DetailsAdapter.uvLevel(5));
        Assert.assertEquals(R.string.uv_level_3, DetailsAdapter.uvLevel(6));
        Assert.assertEquals(R.string.uv_level_3, DetailsAdapter.uvLevel(7));
        Assert.assertEquals(R.string.uv_level_4, DetailsAdapter.uvLevel(8));
        Assert.assertEquals(R.string.uv_level_4, DetailsAdapter.uvLevel(10));
        Assert.assertEquals(R.string.uv_level_5, DetailsAdapter.uvLevel(11));
        Assert.assertEquals(R.string.uv_level_5, DetailsAdapter.uvLevel(15));
    }

    /** Every band must name a distinct string, or the words stop telling readings apart. */
    @Test
    public void bandsAreDistinct() {
        int[] humidity = {
                DetailsAdapter.humidityLevel(0),
                DetailsAdapter.humidityLevel(45),
                DetailsAdapter.humidityLevel(70),
                DetailsAdapter.humidityLevel(95),
        };
        assertAllDifferent(humidity);

        int[] uv = {
                DetailsAdapter.uvLevel(0),
                DetailsAdapter.uvLevel(4),
                DetailsAdapter.uvLevel(7),
                DetailsAdapter.uvLevel(9),
                DetailsAdapter.uvLevel(12),
        };
        assertAllDifferent(uv);
    }

    /**
     * The number inside the gauge, across the units the settings actually offer. Pressure and
     * visibility are the two readings whose display unit can change the magnitude by 1000x, so
     * rounding to whole numbers (what the tiles used to do) erased atm entirely and rounding to one
     * decimal printed a meaningless tenth of a metre.
     */
    @Test
    public void gaugeNumberKeepsDecimalsOnlyWhereTheyCarry() {
        // Pressure at 1005.8 mb, as each unit shows it.
        Assert.assertEquals("1006", DetailsAdapter.gaugeNumber(1005.8f));     // mb / hPa
        Assert.assertEquals("100.6", DetailsAdapter.gaugeNumber(100.58f));    // kPa — a tenth is ~1 mb
        Assert.assertEquals("754.4", DetailsAdapter.gaugeNumber(754.4f));     // mmHg
        Assert.assertEquals("29.7", DetailsAdapter.gaugeNumber(29.7f));       // inHg
        Assert.assertEquals("0.99", DetailsAdapter.gaugeNumber(0.9926f));     // atm — was "1"
        Assert.assertEquals("1.03", DetailsAdapter.gaugeNumber(1.0259f));     // kgf/cm² — was "1"

        // Visibility at 10.6 km, as each unit shows it.
        Assert.assertEquals("10.6", DetailsAdapter.gaugeNumber(10.6f));       // km
        Assert.assertEquals("10600", DetailsAdapter.gaugeNumber(10600f));     // m — was "10600.0"
        Assert.assertEquals("6.59", DetailsAdapter.gaugeNumber(6.5858f));     // mi
        Assert.assertEquals("34777", DetailsAdapter.gaugeNumber(34776.9f));   // ft
    }

    /** Whole values stay whole — no "9.00" in a gauge. */
    @Test
    public void gaugeNumberDropsEmptyDecimals() {
        Assert.assertEquals("9", DetailsAdapter.gaugeNumber(9f));
        Assert.assertEquals("0", DetailsAdapter.gaugeNumber(0f));
        Assert.assertEquals("1013", DetailsAdapter.gaugeNumber(1013f));
        // One real decimal, not a padded pair.
        Assert.assertEquals("6.5", DetailsAdapter.gaugeNumber(6.5f));
    }

    /** Four digits and no thousands separator, so the number stays inside the circle. */
    @Test
    public void gaugeNumberStaysShort() {
        Assert.assertEquals("10600", DetailsAdapter.gaugeNumber(10600f));
        for (float v : new float[] {0f, 0.9926f, 9.98f, 29.7f, 100.58f, 1005.8f, 34776.9f}) {
            Assert.assertTrue(
                    "too wide: " + DetailsAdapter.gaugeNumber(v),
                    DetailsAdapter.gaugeNumber(v).length() <= 6
            );
        }
    }

    /** The separator follows the locale — the reason this is NumberFormat and not "%.1f". */
    @Test
    public void gaugeNumberFollowsTheLocaleSeparator() {
        Locale.setDefault(Locale.GERMANY);
        Assert.assertEquals("10,6", DetailsAdapter.gaugeNumber(10.6f));
    }

    private static void assertAllDifferent(int[] resIds) {
        for (int i = 0; i < resIds.length; i++) {
            for (int j = i + 1; j < resIds.length; j++) {
                Assert.assertNotEquals("bands " + i + " and " + j, resIds[i], resIds[j]);
            }
        }
    }
}
