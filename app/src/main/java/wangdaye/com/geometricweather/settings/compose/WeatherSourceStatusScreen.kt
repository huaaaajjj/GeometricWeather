package wangdaye.com.geometricweather.settings.compose

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import wangdaye.com.geometricweather.common.basic.models.Location
import wangdaye.com.geometricweather.common.basic.models.options.provider.WeatherSource
import wangdaye.com.geometricweather.common.basic.models.weather.Weather
import wangdaye.com.geometricweather.weather.WeatherServiceSet
import wangdaye.com.geometricweather.weather.services.WeatherService
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

private val OK = Color(0xFF2E9E5B)
private val DOWN = Color(0xFFD13B3B)
private val MISS = Color(0xFFBDBDBD)
private val BUSY = Color(0xFFD9A000)

private const val CHECK_TIMEOUT_MS = 25_000L

// 源, 可用, 天, 时, 体感, UV, 气压, 湿度, 降水, AQI, 预警, 日出
private val COLUMNS = listOf(
    "源" to 104, "可用" to 56, "天" to 36, "时" to 44, "体感" to 48, "UV" to 40,
    "气压" to 48, "湿度" to 48, "降水" to 48, "AQI" to 48, "预警" to 48, "日出" to 48
)

// Display order = by availability (matches the source-picker order). France flag -> test with Paris.
private val SOURCES = listOf(
    Src(WeatherSource.WEATHERAPI, "WeatherAPI"),
    Src(WeatherSource.OPEN_METEO, "Open-Meteo"),
    Src(WeatherSource.CAIYUN, "彩云天气"),
    Src(WeatherSource.APIHZ, "中国天气网"),
    Src(WeatherSource.CMA, "中国气象局"),
    Src(WeatherSource.OWM, "OpenWeather"),
    Src(WeatherSource.MF, "Météo France", france = true),
    Src(WeatherSource.ACCU, "AccuWeather"),
)

private data class Src(val source: WeatherSource, val name: String, val france: Boolean = false)

private data class LiveData(
    val daily: Int, val hourly: Int,
    val feels: Boolean, val uv: Boolean, val pressure: Boolean, val humidity: Boolean,
    val precip: Boolean, val aqi: Boolean, val alertCount: Int, val sun: Boolean,
)

private sealed interface RowState
private object Idle : RowState
private object Checking : RowState
private object Failed : RowState
private data class Done(val d: LiveData) : RowState

private data class Txt(val s: String, val color: Color? = null, val bold: Boolean = false)

private fun chk(b: Boolean) = Txt(if (b) "✓" else "✗", if (b) OK else MISS, bold = true)

@EntryPoint
@InstallIn(SingletonComponent::class)
interface WeatherServiceSetEntryPoint {
    fun weatherServiceSet(): WeatherServiceSet
}

@Composable
fun WeatherSourceStatusScreen(context: Context) {
    val serviceSet = remember {
        EntryPointAccessors
            .fromApplication(context.applicationContext, WeatherServiceSetEntryPoint::class.java)
            .weatherServiceSet()
    }
    val states = remember { mutableStateMapOf<String, RowState>().apply {
        SOURCES.forEach { put(it.name, Idle) }
    } }
    // Generation guard: results from a superseded run are ignored.
    var generation by remember { mutableStateOf(0) }
    var running by remember { mutableStateOf(false) }
    var stamp by remember { mutableStateOf<String?>(null) }

    val doneCount = states.values.count { it is Done || it is Failed }

    fun refresh() {
        val gen = generation + 1
        generation = gen
        running = true
        stamp = null
        SOURCES.forEach { states[it.name] = Checking }
        SOURCES.forEach { src ->
            checkOne(context, serviceSet, src) { result ->
                if (gen != generation) return@checkOne
                states[src.name] = result
                if (SOURCES.all { states[it.name] is Done || states[it.name] is Failed }) {
                    running = false
                    stamp = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
                }
            }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(16.dp)
    ) {
        item {
            Text(
                text = "天气源可用性（点「实测刷新」联网检测）",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Row(
                modifier = Modifier.padding(top = 8.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Button(onClick = { refresh() }, enabled = !running) {
                    Text("实测刷新")
                }
                if (running) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Text(
                        text = "检测中 $doneCount/${SOURCES.size}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else if (stamp != null) {
                    Text(
                        text = "实测于 $stamp（北京坐标 · MF用巴黎）",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        item {
            val scroll = rememberScrollState()
            Column(modifier = Modifier.horizontalScroll(scroll)) {
                TableRow(COLUMNS.map { Txt(it.first, MaterialTheme.colorScheme.onSurfaceVariant, bold = true) }, header = true)
                HorizontalDivider()
                SOURCES.forEachIndexed { i, src ->
                    TableRow(buildCells(src.name, states[src.name] ?: Idle), header = false)
                    if (i < SOURCES.lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }

        item {
            Spacer(Modifier.height(12.dp))
            Text(
                text = "✓=有数据  ✗=无  天/时=预报天数/逐小时点数  预警=当前生效条数。每次刷新都会真实联网调用各源接口（会消耗对应配额）；坐标型源按 GPS 精确，APIHZ/CMA 为城市/站点级，Météo France 仅法国。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun buildCells(name: String, state: RowState): List<Txt> {
    val head = Txt(name, bold = true)
    return when (state) {
        is Idle -> listOf(head, Txt("—", MISS)) + List(10) { Txt("—", MISS) }
        is Checking -> listOf(head, Txt("…", BUSY, bold = true)) + List(10) { Txt("…", BUSY) }
        is Failed -> listOf(head, Txt("不可用", DOWN, bold = true)) + List(10) { Txt("—", MISS) }
        is Done -> {
            val d = state.d
            listOf(
                head,
                Txt("可用", OK, bold = true),
                Txt(d.daily.toString()),
                Txt(d.hourly.toString()),
                chk(d.feels), chk(d.uv), chk(d.pressure), chk(d.humidity),
                chk(d.precip), chk(d.aqi),
                Txt(d.alertCount.toString(), if (d.alertCount > 0) OK else MISS),
                chk(d.sun),
            )
        }
    }
}

// ---- live check pipeline (reuses the app's WeatherServiceSet) ----

private fun testLocation(src: Src): Location {
    return if (src.france) {
        Location("", 48.8566f, 2.3522f, TimeZone.getTimeZone("Europe/Paris"),
            "France", "Île-de-France", "Paris", "", null, src.source, false, false, false)
    } else {
        Location("", 39.9042f, 116.4074f, TimeZone.getTimeZone("Asia/Shanghai"),
            "中国", "北京市", "北京市", "", null, src.source, false, false, true)
    }
}

private fun checkOne(
    context: Context,
    serviceSet: WeatherServiceSet,
    src: Src,
    onResult: (RowState) -> Unit,
) {
    val service = serviceSet.get(src.source)
    val loc = testLocation(src)
    val reported = java.util.concurrent.atomic.AtomicBoolean(false)
    val main = Handler(Looper.getMainLooper())

    fun finish(state: RowState) {
        if (reported.compareAndSet(false, true)) {
            onResult(state)
        }
    }
    // Timeout so a hanging source still resolves (runs on the main thread).
    main.postDelayed({ finish(Failed) }, CHECK_TIMEOUT_MS)

    fun requestWeatherStep(resolved: Location) {
        service.requestWeather(context, resolved, object : WeatherService.RequestWeatherCallback {
            override fun requestWeatherSuccess(requestLocation: Location) {
                val w = requestLocation.weather
                finish(if (w != null) extract(w) else Failed)
            }
            override fun requestWeatherFailed(requestLocation: Location) = finish(Failed)
        })
    }

    // Step 1: resolve (ACCU needs a real cityId/key; coordinate sources just echo).
    try {
        service.requestLocation(context, loc, object : WeatherService.RequestLocationCallback {
            override fun requestLocationSuccess(query: String?, locationList: MutableList<Location>?) {
                requestWeatherStep(locationList?.firstOrNull() ?: loc)
            }
            override fun requestLocationFailed(query: String?) {
                // coordinate sources can still serve weather from lat/lon even if "resolution" fails.
                requestWeatherStep(loc)
            }
        })
    } catch (e: Exception) {
        finish(Failed)
    }
}

private fun extract(w: Weather): RowState {
    return try {
        val dailyList = w.dailyForecast
        val hourlyList = w.hourlyForecast
        val dailyCount = dailyList?.size ?: 0
        val hourlyCount = hourlyList?.size ?: 0
        val cur = w.current
        val data = LiveData(
            daily = dailyCount,
            hourly = hourlyCount,
            feels = cur?.temperature?.realFeelTemperature != null,
            // UV/AQI/sunrise often live in daily or hourly rather than the current block.
            uv = cur?.uv?.isValidIndex == true
                || dailyList?.any { it.uv?.isValidIndex == true } == true
                || hourlyList?.any { it.uv?.isValidIndex == true } == true,
            pressure = cur?.pressure != null,
            humidity = cur?.relativeHumidity != null,
            precip = cur?.precipitation?.total != null,
            aqi = cur?.airQuality?.isValid == true
                || dailyList?.any { it.airQuality?.isValid == true } == true,
            alertCount = w.alertList?.size ?: 0,
            sun = dailyList?.any { it.sun()?.isValid == true } == true,
        )
        if (dailyCount > 0 || hourlyCount > 0) Done(data) else Failed
    } catch (e: Exception) {
        Failed
    }
}

@Composable
private fun TableRow(cells: List<Txt>, header: Boolean) {
    Row(
        modifier = Modifier
            .height(if (header) 36.dp else 40.dp)
            .background(
                if (header) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                else Color.Transparent
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        cells.forEachIndexed { i, cell ->
            Box(
                modifier = Modifier
                    .width(COLUMNS[i].second.dp)
                    .padding(horizontal = 2.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = cell.s,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    fontWeight = if (cell.bold) FontWeight.Bold else FontWeight.Normal,
                    color = cell.color ?: MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}
