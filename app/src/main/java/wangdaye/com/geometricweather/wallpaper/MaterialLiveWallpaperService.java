package wangdaye.com.geometricweather.wallpaper;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Process;
import android.service.wallpaper.WallpaperService;
import android.text.TextUtils;
import android.view.OrientationEventListener;
import android.view.SurfaceHolder;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.Size;
import androidx.core.content.res.ResourcesCompat;

import java.util.List;

import wangdaye.com.geometricweather.common.basic.models.Location;
import wangdaye.com.geometricweather.common.basic.models.weather.WeatherCode;
import wangdaye.com.geometricweather.common.utils.DisplayUtils;
import wangdaye.com.geometricweather.common.utils.helpers.AsyncHelper;
import wangdaye.com.geometricweather.db.DatabaseHelper;
import wangdaye.com.geometricweather.settings.SettingsManager;
import wangdaye.com.geometricweather.theme.weatherView.WeatherView;
import wangdaye.com.geometricweather.theme.weatherView.WeatherViewController;
import wangdaye.com.geometricweather.theme.weatherView.materialWeatherView.DelayRotateController;
import wangdaye.com.geometricweather.theme.weatherView.materialWeatherView.IntervalComputer;
import wangdaye.com.geometricweather.theme.weatherView.materialWeatherView.MaterialWeatherView;
import wangdaye.com.geometricweather.theme.weatherView.materialWeatherView.WeatherImplementorFactory;

public class MaterialLiveWallpaperService extends WallpaperService {

    private enum DeviceOrientation {
        TOP, LEFT, BOTTOM, RIGHT
    }

    @Override
    public Engine onCreateEngine() {
        return new WeatherEngine();
    }

    private class WeatherEngine extends Engine {

        private SurfaceHolder mHolder;
        @Nullable private IntervalComputer mIntervalComputer;
        @Nullable private MaterialWeatherView.RotateController[] mRotators;

        @Nullable private MaterialWeatherView.WeatherAnimationImplementor mImplementor;
        @Nullable private Drawable mBackground;

        private boolean mOpenGravitySensor;
        @Nullable private SensorManager mSensorManager;
        @Nullable private Sensor mGravitySensor;

        @Size(2) private int[] mSizes;
        @Size(2) private int[] mAdaptiveSize;
        private float mRotation2D;
        private float mRotation3D;

        @WeatherView.WeatherKindRule private int mWeatherKind;
        private boolean mDaytime;

        private boolean mVisible;

        private DeviceOrientation mDeviceOrientation;

        @Nullable private AsyncHelper.Controller mIntervalController;
        private HandlerThread mHandlerThread;
        private Handler mHandler;
        private final Runnable mDrawableRunnable = new Runnable() {

            @Override
            public void run() {
                if (mIntervalComputer == null
                        || mImplementor == null
                        || mBackground == null
                        || mRotators == null
                        || mHandler == null) {
                    return;
                }
                // The surface can be released during teardown while a draw is still in flight;
                // lockCanvas/unlockCanvasAndPost would then throw "Surface has already been released".
                if (mHolder == null || mHolder.getSurface() == null || !mHolder.getSurface().isValid()) {
                    return;
                }

                mIntervalComputer.invalidate();

                mRotators[0].updateRotation(mRotation2D, mIntervalComputer.getInterval());
                mRotators[1].updateRotation(mRotation3D, mIntervalComputer.getInterval());

                Canvas canvas = null;
                try {
                    canvas = mHolder.lockCanvas();
                    if (canvas != null) {
                        if (mSizes[0] != canvas.getWidth()
                                || mSizes[1] != canvas.getHeight()) {
                            mSizes[0] = canvas.getWidth();
                            mSizes[1] = canvas.getHeight();

                            mAdaptiveSize[0] = DisplayUtils.getTabletListAdaptiveWidth(
                                    getApplicationContext(),
                                    mSizes[0]
                            );
                            mAdaptiveSize[1] = mSizes[1];

                            mBackground.setBounds(0, 0, mSizes[0], mSizes[1]);
                        }

                        mBackground.draw(canvas);

                        canvas.save();
                        canvas.translate(
                                (mSizes[0] - mAdaptiveSize[0]) / 2f,
                                (mSizes[1] - mAdaptiveSize[1]) / 2f
                        );
                        mImplementor.updateData(
                                mAdaptiveSize, (long) mIntervalComputer.getInterval(),
                                (float) mRotators[0].getRotation(), (float) mRotators[1].getRotation()
                        );
                        mImplementor.draw(
                                mAdaptiveSize,
                                canvas,
                                0,
                                (float) mRotators[0].getRotation(),
                                (float) mRotators[1].getRotation()
                        );
                        canvas.restore();
                    }
                } catch (Exception ignored) {
                    // surface released or draw failed — skip this frame.
                } finally {
                    if (canvas != null) {
                        try {
                            mHolder.unlockCanvasAndPost(canvas);
                        } catch (Exception ignored) {
                            // surface already released.
                        }
                    }
                }
            }
        };

        private final SensorEventListener mGravityListener = new SensorEventListener() {

            @Override
            public void onSensorChanged(SensorEvent ev) {
                // x : (+) fall to the left / (-) fall to the right.
                // y : (+) stand / (-) head stand.
                // z : (+) look down / (-) look up.
                // rotation2D : (+) anticlockwise / (-) clockwise.
                // rotation3D : (+) look down / (-) look up.
                if (mOpenGravitySensor) {
                    float aX = ev.values[0];
                    float aY = ev.values[1];
                    float aZ = ev.values[2];
                    double g2D = Math.sqrt(aX * aX + aY * aY);
                    double g3D = Math.sqrt(aX * aX + aY * aY + aZ * aZ);
                    double cos2D = Math.max(Math.min(1, aY / g2D), -1);
                    double cos3D = Math.max(Math.min(1, g2D * (aY >= 0 ? 1 : -1) / g3D), -1);
                    mRotation2D = (float) Math.toDegrees(Math.acos(cos2D)) * (aX >= 0 ? 1 : -1);
                    mRotation3D = (float) Math.toDegrees(Math.acos(cos3D)) * (aZ >= 0 ? 1 : -1);

                    switch (mDeviceOrientation) {
                        case TOP:
                            break;

                        case LEFT:
                            mRotation2D -= 90;
                            break;

                        case RIGHT:
                            mRotation2D += 90;
                            break;

                        case BOTTOM:
                            if (mRotation2D > 0) {
                                mRotation2D -= 180;
                            } else {
                                mRotation2D += 180;
                            }
                            break;
                    }

                    if (60 < Math.abs(mRotation3D) && Math.abs(mRotation3D) < 120) {
                        mRotation2D *= Math.abs(Math.abs(mRotation3D) - 90) / 30.0;
                    }
                } else {
                    mRotation2D = 0;
                    mRotation3D = 0;
                }
            }

            @Override
            public void onAccuracyChanged(Sensor sensor, int i) {
                // do nothing.
            }
        };

        private final OrientationEventListener mOrientationListener = new OrientationEventListener(getApplicationContext()) {
            @Override
            public void onOrientationChanged(int orientation) {
                mDeviceOrientation = getDeviceOrientation(orientation);
            }

            private DeviceOrientation getDeviceOrientation(int orientation) {
                if (DisplayUtils.isLandscape(getApplicationContext())) {
                    return (0 < orientation && orientation < 180)
                            ? DeviceOrientation.RIGHT : DeviceOrientation.LEFT;
                } else {
                    return (270 < orientation || orientation < 90)
                            ? DeviceOrientation.TOP : DeviceOrientation.BOTTOM;
                }
            }
        };

        private void setWeather(@WeatherView.WeatherKindRule int weatherKind, boolean daytime) {
            mWeatherKind = weatherKind;
            mDaytime = daytime;
        }

        private void setWeatherImplementor() {
            mImplementor = WeatherImplementorFactory.getWeatherImplementor(
                    mWeatherKind,
                    mDaytime,
                    mAdaptiveSize
            );
            mRotators = new MaterialWeatherView.RotateController[] {
                    new DelayRotateController(mRotation2D),
                    new DelayRotateController(mRotation3D)
            };

            mBackground = ResourcesCompat.getDrawable(
                    getResources(),
                    WeatherImplementorFactory.getBackgroundId(mWeatherKind, mDaytime),
                    null
            );
            if (mBackground != null) {
                mBackground.setBounds(0, 0, mSizes[0], mSizes[1]);
            }
        }

        private void setIntervalComputer() {
            if (mIntervalComputer == null) {
                mIntervalComputer = new IntervalComputer();
            } else {
                mIntervalComputer.reset();
            }
        }

        private void setOpenGravitySensor(boolean openGravitySensor) {
            mOpenGravitySensor = openGravitySensor;
        }

        // Runs on the UI thread once location + weather have been read off the main thread.
        private void applyWeatherAndStartDrawing(@NonNull Location location) {
            // The wallpaper may have been hidden again while the DB read was in flight.
            if (!mVisible) {
                return;
            }

            LiveWallpaperConfigManager configManager = LiveWallpaperConfigManager.getInstance(
                    MaterialLiveWallpaperService.this
            );
            String weatherKind = configManager.getWeatherKind();
            if (weatherKind.equals("auto")) {
                weatherKind = location.getWeather() != null
                        ? location.getWeather().getCurrent().getWeatherCode().getId()
                        : "";
            }
            String dayNightType = configManager.getDayNightType();
            boolean daytime = true;
            switch (dayNightType) {
                case "auto":
                    daytime = location.isDaylight();
                    break;

                case "day":
                    daytime = true;
                    break;

                case "night":
                    daytime = false;
                    break;
            }

            if (!TextUtils.isEmpty(weatherKind)) {
                setWeather(
                        WeatherViewController.getWeatherKind(
                                WeatherCode.getInstance(weatherKind)
                        ),
                        daytime
                );
            }
            setWeatherImplementor();
            setIntervalComputer();
            setOpenGravitySensor(
                    SettingsManager.getInstance(getApplicationContext()).isGravitySensorEnabled());

            // getDisplay() throws UnsupportedOperationException on a WallpaperService (non-visual)
            // Context; DisplayManager works on any context.
            float screenRefreshRate = 60;
            try {
                android.hardware.display.DisplayManager dm =
                        (android.hardware.display.DisplayManager) getSystemService(Context.DISPLAY_SERVICE);
                android.view.Display display = (dm != null)
                        ? dm.getDisplay(android.view.Display.DEFAULT_DISPLAY) : null;
                if (display != null) {
                    screenRefreshRate = display.getRefreshRate();
                }
            } catch (Exception ignored) {
                screenRefreshRate = 60;
            }
            if (screenRefreshRate < 60) {
                screenRefreshRate = 60;
            }
            // Cancel any previous interval before starting a new one, and only post to the draw
            // thread while it is alive — otherwise a leaked/late interval floods logcat with
            // "sending message to a Handler on a dead thread".
            if (mIntervalController != null) {
                mIntervalController.cancel();
                mIntervalController = null;
            }
            mIntervalController = AsyncHelper.intervalRunOnUI(
                    () -> {
                        if (mHandler != null && mHandlerThread != null && mHandlerThread.isAlive()) {
                            mHandler.post(mDrawableRunnable);
                        }
                    },
                    (long) (1000.0 / screenRefreshRate),
                    0
            );
        }

        @Override
        public void onCreate(SurfaceHolder surfaceHolder) {
            mDeviceOrientation = DeviceOrientation.TOP;

            mHandlerThread = new HandlerThread(
                    String.valueOf(System.currentTimeMillis()),
                    Process.THREAD_PRIORITY_FOREGROUND
            );
            mHandlerThread.start();
            mHandler = new Handler(mHandlerThread.getLooper());

            mSizes = new int[] {0, 0};
            mAdaptiveSize = new int[] {0, 0};

            mHolder = surfaceHolder;
            mHolder.addCallback(new SurfaceHolder.Callback() {
                @Override
                public void surfaceCreated(SurfaceHolder holder) {

                }

                @Override
                public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
                    mSizes[0] = width;
                    mSizes[1] = height;

                    mAdaptiveSize[0] = DisplayUtils.getTabletListAdaptiveWidth(
                            getApplicationContext(),
                            mSizes[0]
                    );
                    mAdaptiveSize[1] = height;

                    setWeatherImplementor();
                }

                @Override
                public void surfaceDestroyed(SurfaceHolder holder) {

                }
            });
            mHolder.setFormat(PixelFormat.RGBA_8888);

            mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
            if (mSensorManager != null) {
                mOpenGravitySensor = true;
                mGravitySensor = mSensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY);
            }

            mVisible = false;
            setWeather(WeatherView.WEATHER_KING_NULL, true);
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            if (mVisible != visible) {
                mVisible = visible;
                if (visible) {
                    mRotation2D = 0;
                    mRotation3D = 0;
                    if (mSensorManager != null) {
                        mSensorManager.registerListener(
                                mGravityListener,
                                mGravitySensor,
                                SensorManager.SENSOR_DELAY_FASTEST
                        );
                    }
                    if (mOrientationListener.canDetectOrientation()) {
                        mOrientationListener.enable();
                    }

                    // Never touch Room on the main thread (onVisibilityChanged runs on the
                    // main thread): read location + weather on IO, then set up the renderer
                    // back on the UI thread.
                    AsyncHelper.runOnIO(() -> {
                        DatabaseHelper db = DatabaseHelper.getInstance(MaterialLiveWallpaperService.this);
                        List<Location> list = db.readLocationList();
                        final Location resolved = (list != null && !list.isEmpty())
                                ? Location.copy(list.get(0), db.readWeather(list.get(0)))
                                : Location.buildLocal();
                        AsyncHelper.delayRunOnUI(() -> applyWeatherAndStartDrawing(resolved), 0);
                    });
                } else {
                    if (mIntervalController != null) {
                        mIntervalController.cancel();
                        mIntervalController = null;
                    }
                    mHandler.removeCallbacksAndMessages(null);
                    if (mSensorManager != null) {
                        mSensorManager.unregisterListener(mGravityListener, mGravitySensor);
                    }
                    mOrientationListener.disable();
                }
            }
        }

        @Override
        public void onDestroy() {
            // Unconditional teardown: onVisibilityChanged(false) is a no-op when already hidden,
            // which would leave the draw interval posting to a quit HandlerThread (flooding
            // logcat with "sending message to a Handler on a dead thread"). Cancel everything
            // here regardless of mVisible, and stop the interval before quitting the thread.
            mVisible = false;
            if (mIntervalController != null) {
                mIntervalController.cancel();
                mIntervalController = null;
            }
            mOrientationListener.disable();
            if (mSensorManager != null) {
                mSensorManager.unregisterListener(mGravityListener, mGravitySensor);
            }
            if (mHandler != null) {
                mHandler.removeCallbacksAndMessages(null);
            }
            if (mHandlerThread != null) {
                mHandlerThread.quitSafely();
            }
        }
    }
}
