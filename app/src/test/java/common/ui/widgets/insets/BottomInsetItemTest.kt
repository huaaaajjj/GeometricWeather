package common.ui.widgets.insets

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import wangdaye.com.geometricweather.common.ui.widgets.insets.bottomInsetItem

/**
 * Every lazy list item key is handed to Compose's `SaveableStateHolder`, which throws
 * `IllegalArgumentException` for anything Android cannot store in a `Bundle`. This item's key used
 * to be written `key = { … }` — `item` takes the key itself, so that literal was a `Function0`,
 * savable only because `kotlin.jvm.internal.Lambda` declares `Serializable`. R8 prunes that marker
 * interface, so the release build crashed on the first measure of the alert, allergen and about
 * screens while every debug build was fine.
 *
 * A plain JVM test, no Compose runtime: the recording scope catches what the call site passes,
 * which is where the mistake lived.
 */
class BottomInsetItemTest {

    @OptIn(ExperimentalFoundationApi::class)
    private class RecordingLazyListScope : LazyListScope {

        var items = 0
        var key: Any? = null
        var contentType: Any? = null

        override fun item(
            key: Any?,
            contentType: Any?,
            content: @Composable LazyItemScope.() -> Unit
        ) {
            items ++
            this.key = key
            this.contentType = contentType
        }

        override fun items(
            count: Int,
            key: ((index: Int) -> Any)?,
            contentType: (index: Int) -> Any?,
            itemContent: @Composable LazyItemScope.(index: Int) -> Unit
        ) = throw UnsupportedOperationException()

        override fun stickyHeader(
            key: Any?,
            contentType: Any?,
            content: @Composable LazyItemScope.() -> Unit
        ) = throw UnsupportedOperationException()
    }

    @Test
    fun theKeyIsSomethingABundleCanStore() {
        val scope = RecordingLazyListScope()
        scope.bottomInsetItem()

        assertFalse(
            "The key is a lambda: SaveableStateHolder rejects it once R8 has pruned"
                    + " Serializable off kotlin.jvm.internal.Lambda",
            scope.key is Function<*>
        )
        assertTrue(
            "The key is a " + scope.key?.javaClass?.name + ", which a Bundle may refuse",
            scope.key is String
        )
    }

    @Test
    fun theItemIsRegisteredOnce() {
        val scope = RecordingLazyListScope()
        scope.bottomInsetItem()

        assertTrue(scope.items == 1)
        // Not Bundle-bound like the key, but a lambda here would defeat item-type reuse just as
        // silently, so it is held to the same shape.
        assertFalse(scope.contentType is Function<*>)
    }
}
