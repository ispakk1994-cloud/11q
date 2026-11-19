package day.q11.widget

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.os.PersistableBundle

/**
 * 위젯 일을 뒤에서 돌린다(JobScheduler · 안드로이드 기본 · 따로 받는 라이브러리 없음).
 * - 체크칸: 바로(안드로이드 12 이상은 급한 일로) → 앱 코드(Dart)가 Firestore에 끝냄 + 그 일의 폰 알림 지움
 * - 15분마다(0.2.0 · 안드로이드가 허락하는 가장 짧은 주기): 앱 코드가 PC에서 바뀐 일만 가볍게 확인 →
 *   바뀐 게 있거나 날이 바뀌었거나 1시간이 지났으면 할 일을 다시 읽어 위젯을 새로 그림(+ 그 일의 폰 알림)
 */
internal object WidgetJobs {
    private const val PERIODIC_ID = 71100
    private const val NOW_BASE = 71200
    private const val PERIOD_MS = 15 * 60 * 1000L
    /** 15분 중 마지막 5분 안에 돌게(간격이 고르게 · 화면이 꺼져 있으면 안드로이드가 알아서 미룸). */
    private const val FLEX_MS = 5 * 60 * 1000L

    private fun scheduler(context: Context): JobScheduler? =
        context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as? JobScheduler

    private fun component(context: Context) = ComponentName(context, WidgetJobService::class.java)

    fun schedulePeriodic(context: Context) {
        val js = scheduler(context) ?: return
        try {
            // 이미 같은 주기로 걸려 있으면 그대로(예전 30분 판이면 15분으로 다시 건다)
            val cur = js.getPendingJob(PERIODIC_ID)
            if (cur != null && cur.intervalMillis == PERIOD_MS) return
            val extras = PersistableBundle()
            extras.putString("action", "refresh")
            extras.putString("id", "")
            val info = JobInfo.Builder(PERIODIC_ID, component(context))
                .setPeriodic(PERIOD_MS, FLEX_MS)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setPersisted(true)
                .setExtras(extras)
                .build()
            js.schedule(info)
        } catch (e: Exception) {
            // 예약 실패: 앱을 열 때 · 위젯을 다시 그릴 때 또 시도
        }
    }

    fun cancelPeriodic(context: Context) {
        try {
            scheduler(context)?.cancel(PERIODIC_ID)
        } catch (e: Exception) {
        }
    }

    /** 같은 할 일의 끝냄/되돌림은 같은 번호 → 아직 안 돈 것은 새것으로 바뀜. */
    fun runNow(context: Context, action: String, id: String) {
        val js = scheduler(context) ?: return
        try {
            val extras = PersistableBundle()
            extras.putString("action", action)
            extras.putString("id", id)
            val b = JobInfo.Builder(NOW_BASE + (id.hashCode() and 0x3FF), component(context))
                .setExtras(extras)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                b.setExpedited(true)
            } else {
                b.setOverrideDeadline(0)
            }
            js.schedule(b.build())
        } catch (e: Exception) {
        }
    }
}

class WidgetJobService : JobService() {
    override fun onStartJob(params: JobParameters): Boolean {
        val action = params.extras.getString("action") ?: "refresh"
        val id = params.extras.getString("id") ?: ""
        BgRunner.run(applicationContext, action, id) { _ ->
            try {
                jobFinished(params, false)
            } catch (e: Exception) {
            }
        }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean = false
}
