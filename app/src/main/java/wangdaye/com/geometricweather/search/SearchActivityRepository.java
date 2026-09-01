package wangdaye.com.geometricweather.search;

import android.content.Context;

import java.util.List;

import javax.inject.Inject;

import wangdaye.com.geometricweather.common.basic.models.Location;
import wangdaye.com.geometricweather.common.utils.helpers.AsyncHelper;

public class SearchActivityRepository {

    private final LocationSearchHelper mSearchHelper;

    @Inject
    SearchActivityRepository(LocationSearchHelper searchHelper) {
        mSearchHelper = searchHelper;
    }

    public void searchLocationList(Context context, String query,
                                   AsyncHelper.Callback<List<Location>> callback) {
        mSearchHelper.search(context, query, new LocationSearchHelper.Callback() {
            @Override
            public void searchSucceeded(String query, List<Location> locationList) {
                callback.call(locationList, true);
            }

            @Override
            public void searchFailed(String query) {
                callback.call(null, true);
            }
        });
    }

    public void cancel() {
        mSearchHelper.cancel();
    }
}
