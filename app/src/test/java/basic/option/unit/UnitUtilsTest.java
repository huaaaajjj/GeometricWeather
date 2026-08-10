package basic.option.unit;

import org.junit.Assert;
import org.junit.Test;

import wangdaye.com.geometricweather.common.basic.models.options._basic.Utils;

// Was @RunWith(PowerMockRunner.class) — which does not run on JDK 17 — around local copies of
// formatFloat/formatInt, so it asserted nothing about the app. No runner is needed: point the same
// assertions at the real Utils instead.
public class UnitUtilsTest {

    @Test
    public void formatFloat() {
        Assert.assertEquals("14.34", Utils.INSTANCE.formatFloat(14.34234f));
        Assert.assertEquals("14.35", Utils.INSTANCE.formatFloat(14.34834f));
        Assert.assertEquals("14.348", Utils.INSTANCE.formatFloat(14.34834f, 3));
        Assert.assertEquals("14.349", Utils.INSTANCE.formatFloat(14.34864f, 3));
    }

    /** A whole value is printed without decimals — the part the old local copy did not have. */
    @Test
    public void formatFloatOfWholeValue() {
        Assert.assertEquals("14", Utils.INSTANCE.formatFloat(14f));
        Assert.assertEquals("14", Utils.INSTANCE.formatFloat(14f, 3));
    }

    @Test
    public void formatInt() {
        Assert.assertEquals("14", Utils.INSTANCE.formatInt(14));
        Assert.assertEquals("16", Utils.INSTANCE.formatInt(16));
    }
}
