package basic;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import wangdaye.com.geometricweather.common.basic.models.Location;
import wangdaye.com.geometricweather.common.basic.models.options.provider.WeatherSource;

public class LocationTest {

    // A fresh current-position placeholder is not located yet.
    @Test
    public void buildLocal_isNotUsable() {
        assertFalse(Location.buildLocal(WeatherSource.COMPOSITE).isUsable());
    }

    // The reported bug: a coordinate-based source (COMPOSITE etc.) never assigns a city id, so a
    // reverse-geocoded "当前位置" kept cityId == NULL_ID and read as 尚未定位. Having an address must
    // make it usable.
    @Test
    public void geocodedCurrentPosition_isUsable() {
        Location located = Location.buildLocal(WeatherSource.COMPOSITE).copy(
                null, null, null, null,
                "中国", "天津市", null, "南开区",
                null, null, null, null, null
        );
        assertTrue(located.isUsable());
    }

    // A location carrying a real provider city id stays usable.
    @Test
    public void locationWithCityId_isUsable() {
        assertTrue(Location.buildDefaultLocation(WeatherSource.COMPOSITE).isUsable());
    }
}
