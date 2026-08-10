package basic.option._utils;

import android.content.res.Resources;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import wangdaye.com.geometricweather.R;
import wangdaye.com.geometricweather.common.basic.models.options._basic.Utils;

// Was PowerMock, which cannot run on JDK 17. Resources is an ordinary class, so plain Mockito is
// enough — and no Android runtime is touched, since only getStringArray is called on the mock.
@RunWith(MockitoJUnitRunner.class)
public class UtilsTest {

    @Test
    public void getNameByValue() {
        Resources res = Mockito.mock(Resources.class);
        Mockito.when(res.getStringArray(R.array.dark_modes)).thenReturn(new String[] {
                "Automatic", "Follow system", "Always light", "Always dark"
        });
        Mockito.when(res.getStringArray(R.array.dark_mode_values)).thenReturn(new String[] {
                "auto", "system", "light", "dark"
        });
        Assert.assertEquals(
                "Automatic",
                Utils.INSTANCE.getNameByValue(
                        res, "auto", R.array.dark_modes, R.array.dark_mode_values)
        );
    }

    /** A value absent from the value array yields null, and callers fall back to the raw id. */
    @Test
    public void getNameByValueOfUnknownValue() {
        Resources res = Mockito.mock(Resources.class);
        Mockito.when(res.getStringArray(R.array.dark_modes)).thenReturn(new String[] {
                "Automatic", "Follow system"
        });
        Mockito.when(res.getStringArray(R.array.dark_mode_values)).thenReturn(new String[] {
                "auto", "system"
        });
        Assert.assertNull(
                Utils.INSTANCE.getNameByValue(
                        res, "nope", R.array.dark_modes, R.array.dark_mode_values)
        );
    }
}
