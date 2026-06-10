package wangdaye.com.geometricweather.common.utils.helpers

import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.Executor

object AsyncHelper {

    private val sMainHandler = Handler(Looper.getMainLooper())

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
        val job = CoroutineScope(Dispatchers.IO).launch {
            task.execute(Emitter(callback))
        }
        return Controller(job)
    }

    @JvmStatic
    fun runOnIO(runnable: Runnable): Controller {
        val job = CoroutineScope(Dispatchers.IO).launch {
            runnable.run()
        }
        return Controller(job)
    }

    @JvmStatic
    fun <T> runOnExecutor(task: Task<T>, callback: Callback<T>, executor: Executor): Controller {
        val dispatcher: CoroutineDispatcher = executor.asCoroutineDispatcher()
        val job = CoroutineScope(dispatcher).launch {
            task.execute(Emitter(callback))
        }
        return Controller(job)
    }

    @JvmStatic
    fun runOnExecutor(runnable: Runnable, executor: Executor): Controller {
        val dispatcher: CoroutineDispatcher = executor.asCoroutineDispatcher()
        val job = CoroutineScope(dispatcher).launch {
            runnable.run()
        }
        return Controller(job)
    }

    @JvmStatic
    fun delayRunOnIO(runnable: Runnable, milliSeconds: Long): Controller {
        val job = CoroutineScope(Dispatchers.IO).launch {
            delay(milliSeconds)
            runnable.run()
        }
        return Controller(job)
    }

    @JvmStatic
    fun delayRunOnUI(runnable: Runnable, milliSeconds: Long): Controller {
        val job = CoroutineScope(Dispatchers.Main).launch {
            delay(milliSeconds)
            runnable.run()
        }
        return Controller(job)
    }

    @JvmStatic
    fun intervalRunOnUI(runnable: Runnable, intervalMilliSeconds: Long, initDelayMilliSeconds: Long): Controller {
        val job = CoroutineScope(Dispatchers.Main).launch {
            delay(initDelayMilliSeconds)
            while (isActive) {
                runnable.run()
                delay(intervalMilliSeconds)
            }
        }
        return Controller(job)
    }
}
