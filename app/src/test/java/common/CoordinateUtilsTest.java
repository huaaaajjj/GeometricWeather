package common;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import wangdaye.com.geometricweather.common.utils.CoordinateUtils;

/**
 * Guards the coordinate box that LocationHelper now uses to set Location.isChina for a GPS fix —
 * a false negative there is what made the domestic sources (APIHZ/CMA) reject the current location.
 */
public class CoordinateUtilsTest {

    @Test
    public void nankaiTianjin_isInChina() {
        // The 南开区 fix that surfaced the bug.
        assertTrue(CoordinateUtils.isInChina(39.11, 117.16));
    }

    @Test
    public void beijing_isInChina() {
        assertTrue(CoordinateUtils.isInChina(39.904, 116.391));
    }

    @Test
    public void tokyo_isNotInChina() {
        assertFalse(CoordinateUtils.isInChina(35.68, 139.69));
    }

    @Test
    public void nullIsland_isNotInChina() {
        assertFalse(CoordinateUtils.isInChina(0.0, 0.0));
    }
}
