package wangdaye.com.geometricweather.location.services.ip;

import android.content.Context;

import androidx.annotation.NonNull;

import javax.inject.Inject;

import io.reactivex.disposables.CompositeDisposable;
import wangdaye.com.geometricweather.location.services.LocationService;
import wangdaye.com.geometricweather.common.rxjava.SchedulerTransformer;
import wangdaye.com.geometricweather.common.rxjava.BaseObserver;
import wangdaye.com.geometricweather.common.rxjava.ObserverContainer;
import wangdaye.com.geometricweather.common.utils.CoordinateUtils;
import wangdaye.com.geometricweather.settings.SettingsManager;

public class BaiduIPLocationService extends LocationService {

    private final BaiduIPLocationApi mApi;
    private final CompositeDisposable compositeDisposable;

    @Inject
    public BaiduIPLocationService(BaiduIPLocationApi api,
                                  CompositeDisposable disposable) {
        mApi = api;
        compositeDisposable = disposable;
    }

    @Override
    public void requestLocation(Context context, @NonNull LocationCallback callback) {
        mApi.getLocation(SettingsManager.getInstance(context).getProviderBaiduIpLocationAk(), "gcj02")
                .compose(SchedulerTransformer.create())
                .subscribe(new ObserverContainer<>(compositeDisposable, new BaseObserver<BaiduIPLocationResult>() {
                    @Override
                    public void onSucceed(BaiduIPLocationResult baiduIPLocationResult) {
                        try {
                            // Baidu IP API returns GCJ-02, convert to WGS-84 for weather APIs
                            double gcjLat = Double.parseDouble(baiduIPLocationResult.getContent().getPoint().getY());
                            double gcjLon = Double.parseDouble(baiduIPLocationResult.getContent().getPoint().getX());
                            double[] wgs84 = CoordinateUtils.gcj02ToWgs84(gcjLat, gcjLon);
                            Result result = new Result(
                                    (float) wgs84[0],
                                    (float) wgs84[1]
                            );
                            callback.onCompleted(result);
                        } catch (Exception ignore) {
                            callback.onCompleted(null);
                        }
                    }

                    @Override
                    public void onFailed() {
                        callback.onCompleted(null);
                    }
                }));
    }

    @Override
    public void cancel() {
        compositeDisposable.clear();
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
