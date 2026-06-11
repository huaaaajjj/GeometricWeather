package wangdaye.com.geometricweather.common.utils.helpers

import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.Executor

object AsyncHelper {

    private val sMainHandler = Handler(Looper.getMainLooper())

    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        Log.e("AsyncHelper", "Uncaught coroutine exception", throwable)
    }

    class Controller internal constructor(
        private val job: Job
    ) {
        fun cancel() {
            job.cancel()
        }
    }

    class Emitter<T> internal constructor(
        private val callback: Callback<T>
    ) {
        fun send(t: T?, done: Boolean) {
            if (Looper.myLooper() == Looper.getMainLooper()) {
                callback.call(t, done)
            } else {
                sMainHandler.post { callback.call(t, done) }
            }
        }
    }

    fun interface Task<T> {
        fun execute(emitter: Emitter<T>)
    }

    fun interface Callback<T> {
        fun call(t: T?, done: Boolean)
    }

    @JvmStatic
    fun <T> runOnIO(task: Task<T>, callback: Callback<T>): Controller {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO + exceptionHandler)
        val job = scope.launch {
            try {
                task.execute(Emitter(callback))
            } catch (e: Exception) {
                Log.e("AsyncHelper", "runOnIO task failed", e)
            }
        }
        return Controller(job)
    }

    @JvmStatic
    fun runOnIO(runnable: Runnable): Controller {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO + exceptionHandler)
        val job = scope.launch {
            try {
                runnable.run()
            } catch (e: Exception) {
                Log.e("AsyncHelper", "runOnIO runnable failed", e)
            }
        }
        return Controller(job)
    }

    @JvmStatic
    fun <T> runOnExecutor(task: Task<T>, callback: Callback<T>, executor: Executor): Controller {
        val dispatcher: CoroutineDispatcher = executor.asCoroutineDispatcher()
        val scope = CoroutineScope(SupervisorJob() + dispatcher + exceptionHandler)
        val job = scope.launch {
            try {
                task.execute(Emitter(callback))
            } catch (e: Exception) {
                Log.e("AsyncHelper", "runOnExecutor task failed", e)
            }
        }
        return Controller(job)
    }

    @JvmStatic
    fun runOnExecutor(runnable: Runnable, executor: Executor): Controller {
        val dispatcher: CoroutineDispatcher = executor.asCoroutineDispatcher()
        val scope = CoroutineScope(SupervisorJob() + dispatcher + exceptionHandler)
        val job = scope.launch {
            try {
                runnable.run()
            } catch (e: Exception) {
                Log.e("AsyncHelper", "runOnExecutor runnable failed", e)
            }
        }
        return Controller(job)
    }

    @JvmStatic
    fun delayRunOnIO(runnable: Runnable, milliSeconds: Long): Controller {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO + exceptionHandler)
        val job = scope.launch {
            delay(milliSeconds)
            try {
                runnable.run()
            } catch (e: Exception) {
                Log.e("AsyncHelper", "delayRunOnIO failed", e)
            }
        }
        return Controller(job)
    }

    @JvmStatic
    fun delayRunOnUI(runnable: Runnable, milliSeconds: Long): Controller {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main + exceptionHandler)
        val job = scope.launch {
            delay(milliSeconds)
            try {
                runnable.run()
            } catch (e: Exception) {
                Log.e("AsyncHelper", "delayRunOnUI failed", e)
            }
        }
        return Controller(job)
    }

    @JvmStatic
    fun intervalRunOnUI(runnable: Runnable, intervalMilliSeconds: Long, initDelayMilliSeconds: Long): Controller {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main + exceptionHandler)
        val job = scope.launch {
            delay(initDelayMilliSeconds)
            while (isActive) {
                try {
                    runnable.run()
                } catch (e: Exception) {
                    Log.e("AsyncHelper", "intervalRunOnUI iteration failed", e)
                }
                delay(intervalMilliSeconds)
            }
        }
        return Controller(job)
    }
}
