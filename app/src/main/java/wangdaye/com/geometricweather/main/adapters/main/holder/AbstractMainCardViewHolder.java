package wangdaye.com.geometricweather.main.adapters.main.holder;

import android.annotation.SuppressLint;
import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;

import wangdaye.com.geometricweather.R;
import wangdaye.com.geometricweather.common.basic.models.Location;
import wangdaye.com.geometricweather.main.utils.MainThemeColorProvider;
import wangdaye.com.geometricweather.theme.ThemeManager;
import wangdaye.com.geometricweather.theme.resource.providers.ResourceProvider;
import wangdaye.com.geometricweather.theme.weatherView.WeatherThemeDelegate;

public abstract class AbstractMainCardViewHolder extends AbstractMainViewHolder {

    @SuppressLint("ObjectAnimatorBinding")
    public AbstractMainCardViewHolder(@NonNull View view) {
        super(view);
    }

    @CallSuper
    public void onBindView(Context context, @NonNull Location location,
                           @NonNull ResourceProvider provider,
                           boolean listAnimationEnabled, boolean itemAnimationEnabled) {
        super.onBindView(context, location, provider, listAnimationEnabled, itemAnimationEnabled);

        styleAsHomeCard((CardView) itemView, location);
    }

    /** Also used for the alert / minutely cards, which the header block hosts rather than the list. */
    public static void styleAsHomeCard(CardView card, @NonNull Location location) {
        Context context = card.getContext();
        WeatherThemeDelegate delegate = ThemeManager
                .getInstance(context)
                .getWeatherThemeDelegate();

        card.setRadius(delegate.getHomeCardRadius(context));
        card.setElevation(delegate.getHomeCardElevation(context));
        card.setCardBackgroundColor(
                MainThemeColorProvider.getColor(location, R.attr.colorMainCardBackground)
        );

        int margins = delegate.getHomeCardMargins(context);
        ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) card.getLayoutParams();
        params.setMargins(margins, 0, margins, margins);
        card.setLayoutParams(params);
    }
}
