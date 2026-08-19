package wangdaye.com.geometricweather.main.adapters;

import org.junit.Assert;
import org.junit.Test;

import wangdaye.com.geometricweather.R;

/**
 * The detail tiles say the reading in words and put the number in the gauge, so the band a value
 * falls in is now load-bearing. This pins the edges of every band — in the model's own units
 * (percent, millibars, kilometres), which is what the tiles grade on whatever unit they display in.
 *
 * <p>Lives in the adapter's package to reach its package-private band functions.
 */
public class DetailsLevelTest {

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

    private static void assertAllDifferent(int[] resIds) {
        for (int i = 0; i < resIds.length; i++) {
            for (int j = i + 1; j < resIds.length; j++) {
                Assert.assertNotEquals("bands " + i + " and " + j, resIds[i], resIds[j]);
            }
        }
    }
}
