package wangdaye.com.geometricweather.main.fragments

import android.animation.Animator
import android.annotation.SuppressLint
import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.graphics.ColorUtils
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.RecyclerView
import wangdaye.com.geometricweather.R
import wangdaye.com.geometricweather.common.basic.GeoActivity
import wangdaye.com.geometricweather.common.basic.livedata.EqualtableLiveData
import wangdaye.com.geometricweather.common.basic.models.Location
import wangdaye.com.geometricweather.common.ui.widgets.SwipeSwitchLayout
import wangdaye.com.geometricweather.common.ui.widgets.SwipeSwitchLayout.OnSwitchListener
import wangdaye.com.geometricweather.databinding.FragmentHomeBinding
import wangdaye.com.geometricweather.main.MainActivityViewModel
import wangdaye.com.geometricweather.main.adapters.main.MainAdapter
import wangdaye.com.geometricweather.main.layouts.MainLayoutManager
import wangdaye.com.geometricweather.main.utils.MainModuleUtils
import wangdaye.com.geometricweather.main.utils.MainThemeColorProvider
import wangdaye.com.geometricweather.settings.SettingsManager
import wangdaye.com.geometricweather.theme.ThemeManager
import wangdaye.com.geometricweather.theme.resource.ResourcesProviderFactory
import wangdaye.com.geometricweather.theme.resource.providers.ResourceProvider
import wangdaye.com.geometricweather.theme.weatherView.WeatherView
import wangdaye.com.geometricweather.theme.weatherView.WeatherViewController

class HomeFragment : MainModuleFragment() {

    private lateinit var binding: FragmentHomeBinding
    private lateinit var viewModel: MainActivityViewModel
    private lateinit var weatherView: WeatherView

    private var adapter: MainAdapter? = null
    private var scrollListener: OnScrollListener? = null
    private var recyclerViewAnimator: Animator? = null
    private var resourceProvider: ResourceProvider? = null

    private val previewOffset = EqualtableLiveData(0)
    private var callback: Callback? = null

    interface Callback {
        fun onManageIconClicked()
        fun onSettingsIconClicked()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentHomeBinding.inflate(layoutInflater, container, false)

        initModel()

        // attach weather view.
        weatherView = ThemeManager
            .getInstance(requireContext())
            .weatherThemeDelegate
            .getWeatherView(requireContext())
        (binding.switchLayout.parent as CoordinatorLayout).addView(
            weatherView as View,
            0,
            CoordinatorLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        initView()
        setCallback(requireActivity() as Callback)

        return binding.root
    }

    override fun onResume() {
        super.onResume()
        weatherView.setDrawable(!isHidden)
    }

    override fun onPause() {
        super.onPause()
        weatherView.setDrawable(false)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        adapter = null
        binding.recyclerView.clearOnScrollListeners()
        scrollListener = null
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        weatherView.setDrawable(!hidden)
    }

    override fun setSystemBarStyle() {
        ThemeManager
            .getInstance(requireContext())
            .weatherThemeDelegate
            .setSystemBarStyle(
                requireContext(),
                requireActivity().window,
                statusShader = scrollListener?.topOverlap == true,
                lightStatus = false,
                navigationShader = true,
                lightNavigation = false
            )
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        updateDayNightColors()
        updateViews()
    }

    // init.

    private fun initModel() {
        viewModel = ViewModelProvider(requireActivity())[MainActivityViewModel::class.java]
    }

    @SuppressLint("ClickableViewAccessibility", "NonConstantResourceId", "NotifyDataSetChanged")
    private fun initView() {
        ensureResourceProvider()

        weatherView.setGravitySensorEnabled(
            SettingsManager.getInstance(requireContext()).isGravitySensorEnabled
        )

        binding.toolbar.setNavigationOnClickListener {
            if (callback != null) {
                callback!!.onManageIconClicked()
            }
        }
        binding.toolbar.inflateMenu(R.menu.activity_main)
        binding.toolbar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.action_manage -> if (callback != null) {
                    callback!!.onManageIconClicked()
                }
                R.id.action_settings -> if (callback != null) {
                    callback!!.onSettingsIconClicked()
                }
            }
            true
        }

        binding.switchLayout.setOnSwitchListener(switchListener)
        binding.switchLayout.reset()
        binding.indicator.setSwitchView(binding.switchLayout)
        binding.indicator.setCurrentIndicatorColor(Color.WHITE)
        binding.indicator.setIndicatorColor(
            ColorUtils.setAlphaComponent(Color.WHITE, (0.5 * 255).toInt())
        )

        binding.refreshLayout.setOnRefreshListener {
            viewModel.updateWithUpdatingChecking(
                triggeredByUser = true,
                checkPermissions = true
            )
        }

        val listAnimationEnabled = SettingsManager
            .getInstance(requireContext())
            .isListAnimationEnabled
        val itemAnimationEnabled = SettingsManager
            .getInstance(requireContext())
            .isItemAnimationEnabled
        adapter = MainAdapter(
            (requireActivity() as GeoActivity),
            binding.recyclerView,
            weatherView,
            null,
            resourceProvider!!,
            listAnimationEnabled,
            itemAnimationEnabled
        )
        binding.recyclerView.adapter = adapter
        binding.recyclerView.layoutManager = MainLayoutManager()
        binding.recyclerView.addOnScrollListener(OnScrollListener().also { scrollListener = it })

        viewModel.currentLocation.observe(viewLifecycleOwner) {
            it?.let { updateViews(it.location) }
        }

        viewModel.loading.observe(viewLifecycleOwner) { setRefreshing(it ?: false) }

        viewModel.indicator.observe(viewLifecycleOwner) {
            if (it == null) return@observe
            binding.switchLayout.isEnabled = it.total > 1

            if (binding.switchLayout.totalCount != it.total
                || binding.switchLayout.position != it.index) {
                binding.switchLayout.setData(it.index, it.total)
                binding.indicator.setSwitchView(binding.switchLayout)
            }

            binding.indicator.visibility = if (it.total > 1) View.VISIBLE else View.GONE
            // The dots used to fade in only while a finger was down; with several locations they
            // now simply stay, like the alert card's.
            binding.indicator.setDisplayState(it.total > 1)
        }

        previewOffset.observe(viewLifecycleOwner) {
            binding.root.post {
                if (isFragmentViewCreated) {
                    updatePreviewSubviews()
                }
            }
        }
    }

    private fun updateDayNightColors() {
        val loc = viewModel.currentLocation.value?.location ?: return
        binding.refreshLayout.setProgressBackgroundColorSchemeColor(
            MainThemeColorProvider.getColor(
                location = loc,
                id = R.attr.colorSurface
            )
        )
    }

    // control.

    @JvmOverloads
    fun updateViews(location: Location? = viewModel.currentLocation.value?.location) {
        if (location == null) return
        ensureResourceProvider()
        updateContentViews(location = location)
        binding.root.post {
            if (isFragmentViewCreated) {
                updatePreviewSubviews()
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility", "NotifyDataSetChanged")
    private fun updateContentViews(location: Location) {
        if (recyclerViewAnimator != null) {
            recyclerViewAnimator!!.cancel()
            recyclerViewAnimator = null
        }

        updateDayNightColors()

        binding.switchLayout.reset()

        if (location.weather == null) {
            adapter!!.setNullWeather()
            adapter!!.notifyDataSetChanged()
            binding.recyclerView.setOnTouchListener { _, event ->
                if (event.action == MotionEvent.ACTION_DOWN
                    && !binding.refreshLayout.isRefreshing) {

                        viewModel.updateWithUpdatingChecking(
                        triggeredByUser = true,
                        checkPermissions = true
                    )
                }
                false
            }
            return
        }

        binding.recyclerView.setOnTouchListener(null)

        val listAnimationEnabled = SettingsManager
            .getInstance(requireContext())
            .isListAnimationEnabled
        val itemAnimationEnabled = SettingsManager
            .getInstance(requireContext())
            .isItemAnimationEnabled
        adapter!!.update(
            (requireActivity() as GeoActivity),
            binding.recyclerView,
            weatherView,
            location,
            resourceProvider!!,
            listAnimationEnabled,
            itemAnimationEnabled
        )
        adapter!!.notifyDataSetChanged()

        scrollListener!!.postReset(binding.recyclerView)

        if (!listAnimationEnabled) {
            binding.recyclerView.alpha = 0f
            recyclerViewAnimator = MainModuleUtils.getEnterAnimator(
                binding.recyclerView,
                0
            )
            recyclerViewAnimator!!.startDelay = 150
            recyclerViewAnimator!!.start()
        }
    }

    private fun ensureResourceProvider() {
        val iconProvider = SettingsManager
            .getInstance(requireContext())
            .iconProvider
        if (resourceProvider == null
            || resourceProvider!!.packageName != iconProvider) {
            resourceProvider = ResourcesProviderFactory.getNewInstance()
        }
    }

    private fun updatePreviewSubviews() {
        val location = viewModel.getValidLocation(
            previewOffset.value ?: 0
        ) ?: return
        val daylight = location.isDaylight

        binding.toolbar.title = location.getCityName(requireContext())
        WeatherViewController.setWeatherCode(
            weatherView,
            location.weather,
            daylight,
            resourceProvider!!
        )
        binding.refreshLayout.setColorSchemeColors(
            ThemeManager
                .getInstance(requireContext())
                .weatherThemeDelegate
                .getThemeColors(
                    requireContext(),
                    WeatherViewController.getWeatherKind(location.weather),
                    daylight
                )[0]
        )
    }

    private fun setRefreshing(b: Boolean) {
        binding.refreshLayout.post {
            if (isFragmentViewCreated) {
                binding.refreshLayout.isRefreshing = b
            }
        }
    }

    // interface.

    private fun setCallback(callback: Callback?) {
        this.callback = callback
    }

    // on swipe listener (swipe switch layout).

    private val switchListener: OnSwitchListener = object : OnSwitchListener {

        override fun onSwiped(swipeDirection: Int, progress: Float) {
            if (progress >= 1) {
                previewOffset.setValue(
                    if (swipeDirection == SwipeSwitchLayout.SWIPE_DIRECTION_LEFT) 1 else -1
                )
            } else {
                previewOffset.setValue(0)
            }
        }

        override fun onSwitched(swipeDirection: Int) {
            viewModel.offsetLocation(
                if (swipeDirection == SwipeSwitchLayout.SWIPE_DIRECTION_LEFT) 1 else -1
            )
            previewOffset.setValue(0)
        }
    }

    // on scroll changed listener.

    private inner class OnScrollListener : RecyclerView.OnScrollListener() {

        private var mTopChanged: Boolean? = null
        var topOverlap = false
        private var mScrollY = 0
        private var mLastAppBarTranslationY = 0f

        fun postReset(recyclerView: RecyclerView) {
            recyclerView.post {
                if (!isFragmentViewCreated) {
                    return@post
                }
                mTopChanged = null
                topOverlap = false
                mScrollY = 0
                mLastAppBarTranslationY = 0f
                onScrolled(recyclerView, 0, 0)
            }
        }

        override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
            mScrollY = recyclerView.computeVerticalScrollOffset()
            mLastAppBarTranslationY = binding.appBar.translationY
            weatherView.onScroll(mScrollY)

            if (adapter != null) {
                adapter!!.onScroll()
                adapter!!.pinHeaderText(mScrollY)
            }

            // set translation y of toolbar. The text block stays pinned under the toolbar until a
            // card reaches it, so the toolbar holds still just as long, then rides up with the
            // text at the very same speed — the gap between them never changes. Once it is fully
            // out of the way it stays put (the header is gone by then, pin distance reads -1).
            val pinDistance = adapter?.headerPinDistance ?: -1
            if (pinDistance >= 0) {
                binding.appBar.translationY = when {
                    mScrollY <= pinDistance -> 0f
                    mScrollY <= pinDistance + binding.appBar.measuredHeight ->
                        (pinDistance - mScrollY).toFloat()
                    else -> -binding.appBar.measuredHeight.toFloat()
                }
                // The page dots anchor to the app bar's layout position, which a translation does
                // not move — carry them along by hand or they hang in mid-air over the pinned
                // text once the bar starts riding up.
                binding.indicator.translationY = binding.appBar.translationY
            }

            // set system bar style.
            mTopChanged = (binding.appBar.translationY != 0f) != (mLastAppBarTranslationY != 0f)
            topOverlap = binding.appBar.translationY != 0f
            if (mTopChanged!!) {
                checkToSetSystemBarStyle()
            }
        }
    }
}