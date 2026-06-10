package wangdaye.com.geometricweather.location.services.ip;

import android.content.Context;

import androidx.annotation.NonNull;

import javax.inject.Inject;

import wangdaye.com.geometricweather.common.utils.CoordinateUtils;
import wangdaye.com.geometricweather.common.utils.helpers.AsyncHelper;
import wangdaye.com.geometricweather.location.services.LocationService;
import wangdaye.com.geometricweather.settings.SettingsManager;

public class BaiduIPLocationService extends LocationService {

    private final BaiduIPLocationApi mApi;
    private AsyncHelper.Controller mController;

    @Inject
    public BaiduIPLocationService(BaiduIPLocationApi api) {
        mApi = api;
    }

    @Override
    public void requestLocation(Context context, @NonNull LocationCallback callback) {
        mController = AsyncHelper.runOnIO(() -> {
            try {
                BaiduIPLocationResult result = mApi.getLocation(
                        SettingsManager.getInstance(context).getProviderBaiduIpLocationAk(),
                        "gcj02"
                ).execute().body();
                if (result != null) {
                    double gcjLat = Double.parseDouble(result.getContent().getPoint().getY());
                    double gcjLon = Double.parseDouble(result.getContent().getPoint().getX());
                    double[] wgs84 = CoordinateUtils.gcj02ToWgs84(gcjLat, gcjLon);
                    callback.onCompleted(new Result(
                            (float) wgs84[0],
                            (float) wgs84[1]
                    ));
                } else {
                    callback.onCompleted(null);
                }
            } catch (Exception e) {
                callback.onCompleted(null);
            }
        });
    }

    @Override
    public void cancel() {
        if (mController != null) {
            mController.cancel();
        }
    }

    @Override
    public boolean hasPermissions(Context context) {
        return true;
    }

    @Override
    public String[] getPermissions() {
        return new String[0];
    }
}
