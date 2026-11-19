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
 * 앱 화면 없이 Dart를 잠깐 돌린다(위젯 체크 · 15분 확인 · 작은 창 넣기).
 * 앱이 setBackgroundHandler로 넘긴 함수 번호(dispatcher · callback)를 써서
 * 새 FlutterEngine에서 oneoneQWidgetDispatcher → 앱 함수(action, id)를 부르고, 끝나면 엔진을 닫는다.
 * 여러 번 눌러도 엔진 하나로 차례대로. 앱 함수가 글자(JSON)를 돌려주면 done으로 넘긴다(작은 창 결과).
 * 작은 창이 열려 있는 동안은 hold로 엔진을 미리 켜 두고(넣기가 빠르게) release로 놓는다.
 */
internal object BgRunner {
    private const val CHANNEL = "oneone_q_widget/bg"
    private const val TASK_TIMEOUT_MS = 30_000L
    private const val SEND_TIMEOUT_MS = 45_000L
    private const val IDLE_STOP_MS = 5_000L
    private const val START_TIMEOUT_MS = 20_000L

    private class Task(val action: String, val id: String, val done: (String?) -> Unit) {
        val finished = AtomicBoolean(false)
    }

    private val main = Handler(Looper.getMainLooper())
    private val queue = ArrayDeque<Task>()
    private var engine: FlutterEngine? = null
    private var channel: MethodChannel? = null
    private var ready = false
    private var busy = false
    private var holds = 0
    private var appContext: Context? = null

    private val idleStop = Runnable { stopEngine() }

    /** Dart가 'ready'를 못 보내면(앱 코드 오류 등) 기다리던 일을 모두 끝냄으로 돌리고 엔진을 닫는다. */
    private val startWatch = Runnable {
        if (!ready) {
            failAll()
        }
    }

    /** 앱 함수 번호가 있는지(앱을 한 번이라도 열었는지). */
    fun canRun(context: Context): Boolean {
        val (d, c) = WidgetStore.handles(context.applicationContext)
        return d != 0L && c != 0L
    }

    fun run(context: Context, action: String, id: String, done: (String?) -> Unit) {
        val app = context.applicationContext
        main.post {
            appContext = app
            val (dispatcher, callback) = WidgetStore.handles(app)
            if (dispatcher == 0L || callback == 0L) {
                // 앱을 아직 한 번도 안 연 경우: 할 수 있는 게 없음
                done(null)
                return@post
            }
            queue.addLast(Task(action, id, done))
            main.removeCallbacks(idleStop)
            if (engine == null) startEngine(app, dispatcher) else pump()
        }
    }

    /** 작은 창을 여는 동안 엔진을 미리 켜 두기(넣기를 누르면 바로). */
    fun hold(context: Context) {
        val app = context.applicationContext
        main.post {
            appContext = app
            holds++
            main.removeCallbacks(idleStop)
            val (dispatcher, callback) = WidgetStore.handles(app)
            if (engine == null && dispatcher != 0L && callback != 0L) startEngine(app, dispatcher)
        }
    }

    fun release() {
        main.post {
            holds = (holds - 1).coerceAtLeast(0)
            if (holds == 0 && !busy && queue.isEmpty()) {
                main.removeCallbacks(idleStop)
                main.postDelayed(idleStop, IDLE_STOP_MS)
            }
        }
    }

    private fun startEngine(app: Context, dispatcher: Long) {
        try {
            val loader = FlutterInjector.instance().flutterLoader()
            loader.startInitialization(app)
            loader.ensureInitializationCompleteAsync(app, null, main) {
                if (engine != null) {
                    pump()
                    return@ensureInitializationCompleteAsync
                }
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
            if (holds == 0) main.postDelayed(idleStop, IDLE_STOP_MS)
            return
        }
        val app = appContext ?: return
        val callback = WidgetStore.handles(app).second
        busy = true
        val limit = if (t.action == "add" || t.action == "undoAdd") SEND_TIMEOUT_MS else TASK_TIMEOUT_MS
        val timeout = Runnable { finishTask(t, null) }
        main.postDelayed(timeout, limit)
        val args = HashMap<String, Any>()
        args["handle"] = callback
        args["action"] = t.action
        args["id"] = t.id
        channel?.invokeMethod("run", args, object : MethodChannel.Result {
            override fun success(result: Any?) {
                main.removeCallbacks(timeout)
                finishTask(t, result as? String)
            }

            override fun error(errorCode: String, errorMessage: String?, errorDetails: Any?) {
                main.removeCallbacks(timeout)
                finishTask(t, null)
            }

            override fun notImplemented() {
                main.removeCallbacks(timeout)
                finishTask(t, null)
            }
        }) ?: finishTask(t, null)
    }

    private fun finishTask(t: Task, result: String?) {
        if (!t.finished.compareAndSet(false, true)) return
        busy = false
        try {
            t.done(result)
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
                    t.done(null)
                } catch (e: Exception) {
                }
            }
        }
        // 엔진이 켜지지 못했으면 붙잡고 있어도 쓸 수 없다
        holds = 0
        stopEngine()
    }

    private fun stopEngine() {
        if (busy || queue.isNotEmpty() || holds > 0) return
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
