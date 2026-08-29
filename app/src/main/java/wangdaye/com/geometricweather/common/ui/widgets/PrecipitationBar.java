package wangdaye.com.geometricweather.common.ui.widgets;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Outline;
import android.graphics.Paint;
import android.os.Build;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewOutlineProvider;

import androidx.annotation.ColorInt;
import androidx.annotation.Nullable;

import java.util.List;

import wangdaye.com.geometricweather.R;
import wangdaye.com.geometricweather.common.basic.models.weather.Minutely;

/**
 * The two-hour minute-by-minute precipitation window as a bar chart against an absolute scale:
 * one thin column per minute, its height the reported intensity (mm/min) against the 暴雨-grade
 * ceiling, with 大/中/小 guide lines and labels on the right edge. Thresholds are the common
 * hourly-rain grades (小 below 2.5, 中 2.5-8, 大 8-16, 暴雨 at or above 16 mm/h) converted per
 * minute, so the axis means the same thing in every window.
 *
 * Wet minutes without an intensity (a cache read — intensity is not persisted) draw at half
 * height: on an absolute axis no fallback height is honest, and half reads as "unknown" rather
 * than as a fabricated 大.
 */
public class PrecipitationBar extends View {

    /** Column width as a fraction of its slot, so adjacent bars stay visually separate. */
    private static final float BAR_WIDTH_FRACTION = 0.7f;

    /** Hourly-rain grades converted to mm/min: the 小/中/大 boundaries and the chart ceiling. */
    private static final float THRESHOLD_LIGHT = 2.5f / 60f;
    private static final float THRESHOLD_MODERATE = 8f / 60f;
    private static final float THRESHOLD_HEAVY = 16f / 60f;
    private static final float SCALE_MAX = 24f / 60f;

    /** Fallback height for wet minutes whose intensity never reached us (a cache read). */
    private static final float UNKNOWN_COLUMN_FRACTION = 0.5f;

    @Nullable private List<Minutely> mMinutelyList;
    private final Paint mBarPaint;
    private final Paint mAxisLinePaint;
    private final Paint mAxisTextPaint;
    private final Paint mAxisStrokePaint;
    private final String mLabelHeavy;
    private final String mLabelModerate;
    private final String mLabelLight;
    @ColorInt private int mPrecipitationColor;
    @ColorInt private int mBackgroundColor;
    @ColorInt private int mAxisColor;

    public PrecipitationBar(Context context) {
        this(context, null);
    }

    public PrecipitationBar(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public PrecipitationBar(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        mBarPaint = new Paint();
        mAxisLinePaint = new Paint();
        mAxisLinePaint.setStyle(Paint.Style.STROKE);
        mAxisLinePaint.setStrokeWidth(getResources().getDisplayMetrics().density);
        mAxisLinePaint.setPathEffect(new DashPathEffect(new float[]{4f, 4f}, 0f));
        mAxisTextPaint = new Paint();
        mAxisTextPaint.setAntiAlias(true);
        mAxisTextPaint.setTextSize(getResources().getDisplayMetrics().scaledDensity * 10f);
        // The labels sit on top of the columns, so each one gets a background-coloured stroke
        // first — grey on a full-height heavy-rain column would otherwise disappear.
        mAxisStrokePaint = new Paint(mAxisTextPaint);
        mAxisStrokePaint.setStyle(Paint.Style.STROKE);
        // Cached once: onDraw runs per frame during animations and must not touch resources.
        mLabelHeavy = context.getString(R.string.precipitation_level_heavy);
        mLabelModerate = context.getString(R.string.precipitation_level_moderate);
        mLabelLight = context.getString(R.string.precipitation_level_light);
        mAxisColor = mPrecipitationColor;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            setClipToOutline(true);
            setOutlineProvider(new ViewOutlineProvider() {
                @Override
                public void getOutline(View view, Outline outline) {
                    outline.setRoundRect(
                            0,
                            0,
                            view.getMeasuredWidth(),
                            view.getMeasuredHeight(),
                            view.getMeasuredHeight() / 12f
                    );
                }
            });
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (mMinutelyList == null || mMinutelyList.size() == 0) {
            return;
        }

        float width = getMeasuredWidth();
        float height = getMeasuredHeight();
        float itemWidth = width / mMinutelyList.size();
        float barWidth = itemWidth * BAR_WIDTH_FRACTION;
        float barMargin = (itemWidth - barWidth) / 2f;

        canvas.drawColor(mBackgroundColor);

        // Guide lines first, so columns with real data draw over them.
        mAxisLinePaint.setColor(mAxisColor);
        drawThresholdLine(canvas, width, THRESHOLD_HEAVY);
        drawThresholdLine(canvas, width, THRESHOLD_MODERATE);
        drawThresholdLine(canvas, width, THRESHOLD_LIGHT);

        mBarPaint.setColor(mPrecipitationColor);
        if (getLayoutDirection() == View.LAYOUT_DIRECTION_RTL) {
            float right = width;
            for (Minutely m : mMinutelyList) {
                float left = right - itemWidth;
                if (m.isPrecipitation()) {
                    float top = columnTop(m);
                    canvas.drawRect(left + barMargin, top, right - barMargin, height, mBarPaint);
                }
                right = left;
            }
        } else {
            float x = 0;
            for (Minutely m : mMinutelyList) {
                if (m.isPrecipitation()) {
                    float top = columnTop(m);
                    canvas.drawRect(x + barMargin, top, x + barMargin + barWidth, height, mBarPaint);
                }
                x += itemWidth;
            }
        }

        drawAxisLabels(canvas, width);
    }

    private void drawThresholdLine(Canvas canvas, float width, float threshold) {
        float y = pxOf(threshold);
        canvas.drawLine(0, y, width, y, mAxisLinePaint);
    }

    /** 大/中/小 right-aligned at their threshold heights, nudged to sit on the line. */
    private void drawAxisLabels(Canvas canvas, float width) {
        mAxisTextPaint.setColor(mAxisColor);
        float padding = mAxisTextPaint.getTextSize() / 4f;
        float textSize = mAxisTextPaint.getTextSize();
        drawLabel(canvas, mLabelHeavy, width - padding, pxOf(THRESHOLD_HEAVY) + textSize / 2f);
        drawLabel(canvas, mLabelModerate, width - padding, pxOf(THRESHOLD_MODERATE) + textSize / 2f);
        drawLabel(canvas, mLabelLight, width - padding, pxOf(THRESHOLD_LIGHT) + textSize / 2f);
    }

    private void drawLabel(Canvas canvas, String text, float right, float baselineY) {
        float left = right - mAxisTextPaint.measureText(text) - padding();
        mAxisStrokePaint.setStrokeWidth(mAxisTextPaint.getTextSize() / 5f);
        mAxisStrokePaint.setColor(mBackgroundColor);
        canvas.drawText(text, left, baselineY, mAxisStrokePaint);
        canvas.drawText(text, left, baselineY, mAxisTextPaint);
    }

    private float padding() {
        return mAxisTextPaint.getTextSize() / 4f;
    }

    /** Top of a column for one minute, on the absolute scale. */
    private float columnTop(Minutely m) {
        Float intensity = m.getIntensity();
        float t = intensity == null ? SCALE_MAX * UNKNOWN_COLUMN_FRACTION
                : Math.min(intensity, SCALE_MAX);
        return getMeasuredHeight() * (1f - t / SCALE_MAX);
    }

    /** Height of a threshold line above the bottom, in px — intensity grows upward, like the columns. */
    private float pxOf(float t) {
        return getMeasuredHeight() * (1f - Math.min(t, SCALE_MAX) / SCALE_MAX);
    }

    public void setMinutelyList(@Nullable List<Minutely> minutelyList) {
        mMinutelyList = minutelyList;
        invalidate();
    }

    public void setPrecipitationColor(@ColorInt int precipitationColor) {
        mPrecipitationColor = precipitationColor;
        invalidate();
    }

    public void setAxisColor(@ColorInt int axisColor) {
        mAxisColor = axisColor;
        invalidate();
    }

    @Override
    public void setBackgroundColor(@ColorInt int backgroundColor) {
        mBackgroundColor = backgroundColor;
        invalidate();
    }
}
