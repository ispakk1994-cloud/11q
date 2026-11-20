package day.q11.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import io.flutter.plugin.common.PluginRegistry

/** 11Q 위젯 플러그인: 앱(Dart)과 홈 화면 위젯 사이의 통로. */
class OneoneQWidgetPlugin :
    FlutterPlugin,
    MethodCallHandler,
    ActivityAware,
    PluginRegistry.NewIntentListener {
    private lateinit var channel: MethodChannel
    private lateinit var context: Context
    private var activityBinding: ActivityPluginBinding? = null

    override fun onAttachedToEngine(flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
        context = flutterPluginBinding.applicationContext
        channel = MethodChannel(flutterPluginBinding.binaryMessenger, "oneone_q_widget")
        channel.setMethodCallHandler(this)
    }

    override fun onMethodCall(
        call: MethodCall,
        result: Result
    ) {
        try {
            when (call.method) {
                "ping" -> result.success("ok")
                // 0.7.0: 놓인 11Q 위젯 넷(오늘 할 일 · 한 주 · 바로 적기 · 자료에 묻기) 모두
                "count" -> result.success(Q11Widgets.count(context))
                "refresh" -> result.success(Q11Widgets.refreshAll(context))
                "requestPin" -> result.success(requestPin())
                // 0.4.0 앱 설정 `홈 화면 위젯` 줄: 바로 놓기가 되는 런처인지 · 글자 크기 읽기/바꾸기(위젯 `설정`과 같은 값)
                "pinSupported" -> result.success(pinSupported())
                "sizeLevel" -> result.success(WidgetStore.sizeLevel(context))
                "setSize" -> {
                    val level = (call.argument<Any>("level") as? Number)?.toInt() ?: 1
                    WidgetStore.setSizeLevel(context, level)
                    Q11Widgets.refreshAll(context)
                    result.success(WidgetStore.sizeLevel(context))
                }
                "save" -> {
                    val json = call.argument<String>("json") ?: ""
                    if (json.isNotBlank()) WidgetStore.saveFeed(context, json)
                    Q11Widgets.refreshAll(context)
                    if (Q11Widgets.needsPeriodic(context)) WidgetJobs.schedulePeriodic(context)
                    result.success(true)
                }
                "clear" -> {
                    WidgetStore.clearFeed(context, call.argument<String>("json"))
                    Q11Widgets.refreshAll(context)
                    result.success(true)
                }
                "lastFeed" -> result.success(WidgetStore.feedRaw(context))
                "setHandles" -> {
                    val d = (call.argument<Any>("dispatcher") as? Number)?.toLong() ?: 0L
                    val c = (call.argument<Any>("callback") as? Number)?.toLong() ?: 0L
                    if (d != 0L && c != 0L) WidgetStore.saveHandles(context, d, c)
                    if (Q11Widgets.needsPeriodic(context)) WidgetJobs.schedulePeriodic(context)
                    result.success(d != 0L && c != 0L)
                }
                "takeAction" -> {
                    val a = pendingAction
                    pendingAction = null
                    result.success(a)
                }
                else -> result.notImplemented()
            }
        } catch (e: Exception) {
            result.error("q11_widget", e.message, null)
        }
    }

    private fun pinSupported(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
        return AppWidgetManager.getInstance(context).isRequestPinAppWidgetSupported
    }

    private fun requestPin(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
        val manager = AppWidgetManager.getInstance(context)
        if (!manager.isRequestPinAppWidgetSupported) return false
        return manager.requestPinAppWidget(
            ComponentName(context, TodayWidgetProvider::class.java),
            null,
            null
        )
    }

    // ── 위젯 ＋ 로 앱이 열린 것 알기 ─────────────────────────

    private fun readIntent(intent: Intent?): Boolean {
        val a = intent?.getStringExtra(TodayWidgetProvider.EXTRA_ACTION) ?: return false
        intent.removeExtra(TodayWidgetProvider.EXTRA_ACTION)
        pendingAction = a
        return true
    }

    override fun onNewIntent(intent: Intent): Boolean {
        if (readIntent(intent)) {
            try {
                channel.invokeMethod("action", pendingAction)
            } catch (e: Exception) {
            }
        }
        return false
    }

    private fun attach(binding: ActivityPluginBinding) {
        activityBinding = binding
        binding.addOnNewIntentListener(this)
        val activity: Activity = binding.activity
        readIntent(activity.intent)
    }

    private fun detach() {
        activityBinding?.removeOnNewIntentListener(this)
        activityBinding = null
    }

    override fun onAttachedToActivity(binding: ActivityPluginBinding) = attach(binding)

    override fun onDetachedFromActivityForConfigChanges() = detach()

    override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) = attach(binding)

    override fun onDetachedFromActivity() = detach()

    override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        channel.setMethodCallHandler(null)
    }

    companion object {
        /** ＋로 열렸을 때 'add'. 앱이 takeAction으로 한 번 가져가면 지운다. */
        @Volatile
        private var pendingAction: String? = null
    }
}
