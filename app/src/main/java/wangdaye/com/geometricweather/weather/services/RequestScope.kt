package wangdaye.com.geometricweather.weather.services

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.launch
import retrofit2.Call
import java.util.concurrent.CopyOnWriteArrayList

/**
 * The request lifetime of one weather service: where its calls run, and what cancelling them means.
 *
 * Cancellation needs both halves. A service issues its calls with a blocking {@code execute()},
 * which has no suspension point to resume at, so cancelling the coroutine alone leaves the thread
 * sitting on the socket and the callback still fires when it returns — the behaviour RxJava's
 * dispose() used to prevent. So the in-flight calls are tracked and cancelled directly, *and* the
 * caller re-checks {@code isActive} before delivering a result.
 *
 * [cancel] cancels the scope's children rather than the scope, because services are injected once
 * and reused for every refresh.
 */
internal class RequestScope {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val calls = CopyOnWriteArrayList<Call<*>>()

    fun launch(block: suspend CoroutineScope.() -> Unit) {
        scope.launch(block = block)
    }

    /** Runs one call to completion, degrading any failure to null so a joined call can go on. */
    fun <T> execute(call: Call<T>): T? {
        calls.add(call)
        return try {
            call.execute().body()
        } catch (e: Exception) {
            null
        } finally {
            // Removing as they finish is what keeps this from growing across refreshes.
            calls.remove(call)
        }
    }

    fun cancel() {
        calls.forEach { it.cancel() }
        calls.clear()
        scope.coroutineContext.cancelChildren()
    }
}
