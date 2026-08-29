package wangdaye.com.geometricweather.common.ui.widgets;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.ColorInt;
import androidx.annotation.Nullable;

import java.util.List;

import wangdaye.com.geometricweather.R;
import wangdaye.com.geometricweather.common.basic.models.weather.Minutely;

/**
 * The two-hour minute-by-minute precipitation window as a bar chart against an absolute scale:
 * one thin column per minute, its height the reported intensity (mm/min) against the 暴雨-grade
 * ceiling, with 大/中/小 guide lines inside the light frame and their labels OUTSIDE it, in a
 * gutter on the right. The frame sits centered — the same gutter width is inset on the left, so
 * the chart keeps to the middle of the card instead of hugging the label side. Thresholds are the
 * common hourly-rain grades (小 below 2.5, 中 2.5-8, 大 8-16, 暴雨 at or above 16 mm/h) converted
 * per minute, so the axis means the same thing in every window.
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
    private final Paint mFramePaint;
    private final Paint mAxisLinePaint;
    private final Paint mAxisTextPaint;
    private final String mLabelHeavy;
    private final String mLabelModerate;
    private final String mLabelLight;
    private final float mCornerRadius;
    @ColorInt private int mPrecipitationColor;
    @ColorInt private int mFrameColor;
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
        mFramePaint = new Paint();
        mAxisLinePaint = new Paint();
        mAxisLinePaint.setStyle(Paint.Style.STROKE);
        mAxisLinePaint.setStrokeWidth(getResources().getDisplayMetrics().density);
        mAxisLinePaint.setPathEffect(new DashPathEffect(new float[]{4f, 4f}, 0f));
        mAxisTextPaint = new Paint();
        mAxisTextPaint.setAntiAlias(true);
        mAxisTextPaint.setTextSize(getResources().getDisplayMetrics().scaledDensity * 10f);
        // Cached once: onDraw runs per frame during animations and must not touch resources.
        mLabelHeavy = context.getString(R.string.precipitation_level_heavy);
        mLabelModerate = context.getString(R.string.precipitation_level_moderate);
        mLabelLight = context.getString(R.string.precipitation_level_light);
        mCornerRadius = getResources().getDisplayMetrics().density * 6f;
        mAxisColor = mPrecipitationColor;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (mMinutelyList == null || mMinutelyList.size() == 0) {
            return;
        }

        // The 大/中/小 labels live in a gutter OUTSIDE the light frame; the same width is inset
        // on the left so the frame stays centered on the card.
        float gutter = axisGutter();
        float width = getMeasuredWidth();
        float height = getMeasuredHeight();
        float plotLeft = gutter;
        float plotWidth = width - gutter * 2;

        // The light frame, then the guide lines and columns clipped inside it.
        RectF frame = new RectF(plotLeft, 0, plotLeft + plotWidth, height);
        Path clip = new Path();
        clip.addRoundRect(frame, mCornerRadius, mCornerRadius, Path.Direction.CW);
        mFramePaint.setColor(mFrameColor);
        canvas.drawRoundRect(frame, mCornerRadius, mCornerRadius, mFramePaint);

        canvas.save();
        canvas.clipPath(clip);
        mAxisLinePaint.setColor(mAxisColor);
        drawThresholdLine(canvas, plotLeft, plotWidth, THRESHOLD_HEAVY);
        drawThresholdLine(canvas, plotLeft, plotWidth, THRESHOLD_MODERATE);
        drawThresholdLine(canvas, plotLeft, plotWidth, THRESHOLD_LIGHT);

        mBarPaint.setColor(mPrecipitationColor);
        float itemWidth = plotWidth / mMinutelyList.size();
        float barWidth = itemWidth * BAR_WIDTH_FRACTION;
        float barMargin = (itemWidth - barWidth) / 2f;
        if (getLayoutDirection() == View.LAYOUT_DIRECTION_RTL) {
            float right = plotLeft + plotWidth;
            for (Minutely m : mMinutelyList) {
                float left = right - itemWidth;
                if (m.isPrecipitation()) {
                    float top = columnTop(m);
                    canvas.drawRect(left + barMargin, top, right - barMargin, height, mBarPaint);
                }
                right = left;
            }
        } else {
            float x = plotLeft;
            for (Minutely m : mMinutelyList) {
                if (m.isPrecipitation()) {
                    float top = columnTop(m);
                    canvas.drawRect(x + barMargin, top, x + barMargin + barWidth, height, mBarPaint);
                }
                x += itemWidth;
            }
        }
        canvas.restore();

        drawAxisLabels(canvas, width);
    }

    private void drawThresholdLine(Canvas canvas, float plotLeft, float plotWidth,
                                   float threshold) {
        float y = pxOf(threshold);
        canvas.drawLine(plotLeft, y, plotLeft + plotWidth, y, mAxisLinePaint);
    }

    /** 大/中/小 right-aligned inside the right-hand gutter, at their threshold heights. */
    private void drawAxisLabels(Canvas canvas, float width) {
        mAxisTextPaint.setColor(mAxisColor);
        float right = width - mAxisTextPaint.getTextSize() / 4f;
        float textSize = mAxisTextPaint.getTextSize();
        drawLabel(canvas, mLabelHeavy, right, pxOf(THRESHOLD_HEAVY) + textSize / 2f);
        drawLabel(canvas, mLabelModerate, right, pxOf(THRESHOLD_MODERATE) + textSize / 2f);
        drawLabel(canvas, mLabelLight, right, pxOf(THRESHOLD_LIGHT) + textSize / 2f);
    }

    private void drawLabel(Canvas canvas, String text, float right, float baselineY) {
        float left = right - mAxisTextPaint.measureText(text);
        canvas.drawText(text, left, baselineY, mAxisTextPaint);
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

    /** Width of the right-hand gutter that holds the 大/中/小 labels, outside the frame. */
    private float axisGutter() {
        return mAxisTextPaint.measureText(mLabelHeavy) + mAxisTextPaint.getTextSize();
    }

    public void setMinutelyList(@Nullable List<Minutely> minutelyList) {
        mMinutelyList = minutelyList;
        invalidate();
    }

    public void setPrecipitationColor(@ColorInt int precipitationColor) {
        mPrecipitationColor = precipitationColor;
        invalidate();
    }

    /** Fill of the rounded frame the columns live in. */
    public void setFrameColor(@ColorInt int frameColor) {
        mFrameColor = frameColor;
        invalidate();
    }

    public void setAxisColor(@ColorInt int axisColor) {
        mAxisColor = axisColor;
        invalidate();
    }
}
