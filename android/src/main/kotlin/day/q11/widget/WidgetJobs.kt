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
 * - 30분마다: 앱 코드가 할 일을 다시 읽어 위젯을 새로 그림(PC에서 넣은 일 · 날이 바뀐 것)
 */
internal object WidgetJobs {
    private const val PERIODIC_ID = 71100
    private const val NOW_BASE = 71200
    private const val PERIOD_MS = 30 * 60 * 1000L

    private fun scheduler(context: Context): JobScheduler? =
        context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as? JobScheduler

    private fun component(context: Context) = ComponentName(context, WidgetJobService::class.java)

    fun schedulePeriodic(context: Context) {
        val js = scheduler(context) ?: return
        try {
            if (js.getPendingJob(PERIODIC_ID) != null) return
            val extras = PersistableBundle()
            extras.putString("action", "refresh")
            extras.putString("id", "")
            val info = JobInfo.Builder(PERIODIC_ID, component(context))
                .setPeriodic(PERIOD_MS)
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
        BgRunner.run(applicationContext, action, id) {
            try {
                jobFinished(params, false)
            } catch (e: Exception) {
            }
        }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean = false
}
