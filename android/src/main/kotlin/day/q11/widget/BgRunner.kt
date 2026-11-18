package day.q11.widget

import android.content.Context
import android.os.Handler
import android.os.Looper
import io.flutter.FlutterInjector
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.embedding.engine.dart.DartExecutor
import io.flutter.plugin.common.MethodChannel
import io.flutter.view.FlutterCallbackInformation
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 앱 화면 없이 Dart를 잠깐 돌린다(위젯 체크 · 30분 갱신).
 * 앱이 setBackgroundHandler로 넘긴 함수 번호(dispatcher · callback)를 써서
 * 새 FlutterEngine에서 oneoneQWidgetDispatcher → 앱 함수(action, id)를 부르고, 끝나면 엔진을 닫는다.
 * 여러 번 눌러도 엔진 하나로 차례대로.
 */
internal object BgRunner {
    private const val CHANNEL = "oneone_q_widget/bg"
    private const val TASK_TIMEOUT_MS = 30_000L
    private const val IDLE_STOP_MS = 5_000L
    private const val START_TIMEOUT_MS = 20_000L

    private class Task(val action: String, val id: String, val done: () -> Unit) {
        val finished = AtomicBoolean(false)
    }

    private val main = Handler(Looper.getMainLooper())
    private val queue = ArrayDeque<Task>()
    private var engine: FlutterEngine? = null
    private var channel: MethodChannel? = null
    private var ready = false
    private var busy = false
    private var appContext: Context? = null

    private val idleStop = Runnable { stopEngine() }

    /** Dart가 'ready'를 못 보내면(앱 코드 오류 등) 기다리던 일을 모두 끝냄으로 돌리고 엔진을 닫는다. */
    private val startWatch = Runnable {
        if (!ready) {
            failAll()
        }
    }

    fun run(context: Context, action: String, id: String, done: () -> Unit) {
        val app = context.applicationContext
        main.post {
            appContext = app
            val (dispatcher, callback) = WidgetStore.handles(app)
            if (dispatcher == 0L || callback == 0L) {
                // 앱을 아직 한 번도 안 연 경우: 할 수 있는 게 없음
                done()
                return@post
            }
            queue.addLast(Task(action, id, done))
            main.removeCallbacks(idleStop)
            if (engine == null) startEngine(app, dispatcher) else pump()
        }
    }

    private fun startEngine(app: Context, dispatcher: Long) {
        try {
            val loader = FlutterInjector.instance().flutterLoader()
            loader.startInitialization(app)
            loader.ensureInitializationCompleteAsync(app, null, main) {
                val info = FlutterCallbackInformation.lookupCallbackInformation(dispatcher)
                if (info == null) {
                    failAll()
                    return@ensureInitializationCompleteAsync
                }
                val e = FlutterEngine(app)
                val ch = MethodChannel(e.dartExecutor.binaryMessenger, CHANNEL)
                ch.setMethodCallHandler { call, result ->
                    if (call.method == "ready") {
                        ready = true
                        main.removeCallbacks(startWatch)
                        result.success(null)
                        pump()
                    } else {
                        result.notImplemented()
                    }
                }
                engine = e
                channel = ch
                ready = false
                main.postDelayed(startWatch, START_TIMEOUT_MS)
                e.dartExecutor.executeDartCallback(
                    DartExecutor.DartCallback(app.assets, loader.findAppBundlePath(), info)
                )
            }
        } catch (t: Throwable) {
            failAll()
        }
    }

    private fun pump() {
        if (!ready || busy) return
        val t = queue.removeFirstOrNull()
        if (t == null) {
            main.postDelayed(idleStop, IDLE_STOP_MS)
            return
        }
        val app = appContext ?: return
        val callback = WidgetStore.handles(app).second
        busy = true
        val timeout = Runnable { finishTask(t) }
        main.postDelayed(timeout, TASK_TIMEOUT_MS)
        val args = HashMap<String, Any>()
        args["handle"] = callback
        args["action"] = t.action
        args["id"] = t.id
        channel?.invokeMethod("run", args, object : MethodChannel.Result {
            override fun success(result: Any?) {
                main.removeCallbacks(timeout)
                finishTask(t)
            }

            override fun error(errorCode: String, errorMessage: String?, errorDetails: Any?) {
                main.removeCallbacks(timeout)
                finishTask(t)
            }

            override fun notImplemented() {
                main.removeCallbacks(timeout)
                finishTask(t)
            }
        }) ?: finishTask(t)
    }

    private fun finishTask(t: Task) {
        if (!t.finished.compareAndSet(false, true)) return
        busy = false
        try {
            t.done()
        } catch (e: Exception) {
        }
        appContext?.let { TodayWidgetProvider.refreshAll(it) }
        pump()
    }

    private fun failAll() {
        while (true) {
            val t = queue.removeFirstOrNull() ?: break
            if (t.finished.compareAndSet(false, true)) {
                try {
                    t.done()
                } catch (e: Exception) {
                }
            }
        }
        stopEngine()
    }

    private fun stopEngine() {
        if (busy || queue.isNotEmpty()) return
        main.removeCallbacks(startWatch)
        try {
            engine?.destroy()
        } catch (e: Exception) {
        }
        engine = null
        channel = null
        ready = false
    }
}
