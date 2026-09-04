package wangdaye.com.geometricweather.common.ui.widgets.insets

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.AppBarDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import wangdaye.com.geometricweather.R
import wangdaye.com.geometricweather.common.ui.widgets.getWidgetSurfaceColor
import kotlin.math.ln

private val topAppBarElevation = 6.dp

internal fun ColorScheme.applyTonalElevation(backgroundColor: Color, elevation: Dp): Color {
    return if (backgroundColor == surface) {
        surfaceColorAtElevation(elevation)
    } else {
        backgroundColor
    }
}
internal fun ColorScheme.surfaceColorAtElevation(elevation: Dp): Color {
    if (elevation == 0.dp) return surface
    val alpha = ((4.5f * ln(elevation.value + 1)) + 2f) / 100f
    return surfaceTint.copy(alpha = alpha).compositeOver(surface)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FitStatusBarTopAppBar(
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable (() -> Unit) = {},
    actions: @Composable RowScope.() -> Unit = {},
    scrollBehavior: TopAppBarScrollBehavior? = null,
) = Column {
    Spacer(
        modifier = Modifier
            .background(getWidgetSurfaceColor(topAppBarElevation))
            .windowInsetsTopHeight(WindowInsets.statusBars)
            .fillMaxWidth(),
    )
    TopAppBar(
        title = title,
        modifier = modifier,
        navigationIcon = navigationIcon,
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
            scrolledContainerColor = MaterialTheme.colorScheme.applyTonalElevation(
                backgroundColor = MaterialTheme.colorScheme.surface,
                elevation = topAppBarElevation,
            ),
            navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            actionIconContentColor = MaterialTheme.colorScheme.onSurface,
        ),
        scrollBehavior = scrollBehavior,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FitStatusBarTopAppBar(
    title: String,
    onBackPressed: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {},
    scrollBehavior: TopAppBarScrollBehavior? = null,
) = FitStatusBarTopAppBar(
    title = { Text(text = title) },
    navigationIcon = {
        IconButton(onClick = onBackPressed) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.content_desc_back),
                // No tint: the bar above already sets navigationIconContentColor to onSurface, which
                // is the colour that is guaranteed to contrast with the bar. Overriding it with
                // onPrimaryContainer — a colour defined against a *different* background — painted
                // this arrow #FFFFFF on a #FFF8F7 bar under the device's dynamic light scheme, i.e.
                // invisible.
            )
        }
    },
    actions = actions,
    scrollBehavior = scrollBehavior,
)

@Composable
fun FitNavigationBarBottomAppBar(
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.primaryContainer,
    contentColor: Color = contentColorFor(containerColor),
    tonalElevation: Dp = AppBarDefaults.BottomAppBarElevation,
    contentPadding: PaddingValues = BottomAppBarDefaults.ContentPadding,
    content: @Composable RowScope.() -> Unit
) {
    Box {
        Column {
            BottomAppBar(
                modifier = modifier,
                containerColor = containerColor,
                contentColor = contentColor,
                tonalElevation = tonalElevation,
                contentPadding = contentPadding,
                content = content,
            )
        }
        Spacer(
            modifier = Modifier
                .background(containerColor)
                .windowInsetsBottomHeight(WindowInsets.navigationBars)
                .fillMaxWidth()
                .align(Alignment.BottomCenter),
        )
    }
}

// `item` takes the key itself, not a producer of one, so a `{ … }` literal here hands the list a
// Function0 — and every lazy item key goes through SaveableStateHolder, which rejects anything
// Android cannot put in a Bundle. It slips through debug builds only because
// kotlin.jvm.internal.Lambda declares Serializable; R8 prunes that marker interface away (nothing
// in the program casts to it), so the minified build threw
// "Type of the key interface kotlin.jvm.functions.Function0 is not supported" on the first measure
// of every screen using this item. A plain String has no such interface to lose.
private const val BOTTOM_INSET_KEY = "bottom_inset"

fun LazyListScope.bottomInsetItem(
    extraHeight: Dp = 0.dp,
) = item(
    key = BOTTOM_INSET_KEY,
    contentType = BOTTOM_INSET_KEY,
) {
    Column {
        Spacer(modifier = Modifier.height(extraHeight))
        Spacer(modifier = Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
    }
}