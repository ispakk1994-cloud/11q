package day.q11.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Paint
import android.os.Build
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.RemoteViews
import org.json.JSONObject
import kotlin.math.roundToInt

/**
 * 0.7.0: 위젯이 넷(오늘 할 일 · 한 주 · 바로 적기 · 자료에 묻기)이 되어 같이 쓰는 것을 모음.
 * - 다시 그리기: 앱이 목록을 넘기거나 · 체크 · 15분 확인 · 글자 크기를 바꾸면 놓인 11Q 위젯 모두
 * - 줄 누르기: 오늘 할 일 · 한 주 목록이 같은 길(체크칸 = 끝냄/되돌림 · 줄 = 앱)
 * - 0.8.0 온전한 줄만(fit · applyFit): 위젯 높이로 들어가는 줄을 세어 목록 칸 높이를 정하고 넘치면 `+N개 더`
 */
internal object Q11Widgets {

    /** 놓인 11Q 위젯 모두 다시 그림. 돌려주는 값 = 놓인 위젯 수(앱 설정 `홈 화면 위젯` 줄이 씀). */
    fun refreshAll(context: Context): Int {
        var n = 0
        n += safe { TodayWidgetProvider.refreshAll(context) }
        n += safe { WeekWidgetProvider.refreshAll(context) }
        n += safe { QuickWidgetProvider.refreshAll(context) }
        n += safe { AskWidgetProvider.refreshAll(context) }
        return n
    }

    /** 목록 줄만 다시(체크칸을 누른 바로 뒤). */
    fun refreshLists(context: Context) {
        safe { TodayWidgetProvider.refreshList(context); 0 }
        safe { WeekWidgetProvider.refreshList(context); 0 }
    }

    /** 놓인 11Q 위젯 수(넷 모두). */
    fun count(context: Context): Int =
        TodayWidgetProvider.widgetIds(context).size +
            WeekWidgetProvider.widgetIds(context).size +
            QuickWidgetProvider.widgetIds(context).size +
            AskWidgetProvider.widgetIds(context).size

    /** 할 일을 보여 주는 위젯(오늘 · 한 주)이 있으면 15분 확인을 둔다. */
    fun needsPeriodic(context: Context): Boolean =
        TodayWidgetProvider.widgetIds(context).isNotEmpty() ||
            WeekWidgetProvider.widgetIds(context).isNotEmpty()

    private inline fun safe(block: () -> Int): Int =
        try {
            block()
        } catch (e: Exception) {
            0
        }

    /** 목록 줄 누르기(오늘 할 일 · 한 주 같은 길). action = 앱이 열릴 때 넘길 값(null = 그냥 열기). */
    fun handleItem(context: Context, intent: Intent, openAction: String?) {
        val kind = intent.getStringExtra(TodayWidgetProvider.EXTRA_KIND) ?: return
        val id = intent.getStringExtra(TodayWidgetProvider.EXTRA_ID) ?: ""
        when (kind) {
            "check" -> {
                if (id.isEmpty()) return
                val wasDone = WidgetStore.isPendingDone(context, id)
                if (wasDone) {
                    WidgetStore.setPending(context, id, null)
                    WidgetJobs.runNow(context, "undo", id)
                } else {
                    WidgetStore.setPending(context, id, true)
                    WidgetJobs.runNow(context, "done", id)
                }
                refreshLists(context)
            }
            "open" -> {
                try {
                    TodayWidgetProvider.openAppIntent(context, openAction)?.let { context.startActivity(it) }
                } catch (e: Exception) {
                    // 백그라운드에서 화면 열기가 막힌 기기: 아무 일도 하지 않음
                }
            }
        }
    }

    fun dp(context: Context, v: Float): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, context.resources.displayMetrics).roundToInt()

    /** 위젯 크기(dp) · 런처가 알려 준 값(세로 화면 기준 · 모르면 0). */
    fun sizeDp(context: Context, widgetId: Int): Pair<Int, Int> {
        val o = AppWidgetManager.getInstance(context).getAppWidgetOptions(widgetId) ?: return Pair(0, 0)
        return Pair(
            o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0),
            o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0)
        )
    }

    /** 0.8.0 목록 칸 높이(dp) = 위젯 높이 − 머리 · 띠 · 여백(fixed) · 위젯 높이를 모르면 0. */
    fun listAvail(context: Context, widgetId: Int, fixed: Int): Int {
        val h = sizeDp(context, widgetId).second
        return if (h <= 0) 0 else (h - fixed).coerceAtLeast(0)
    }

    /**
     * 0.8.0 마지막 줄이 잘리지 않게(시안 WidgetPolish ②): 들어가는 만큼만 온전한 줄 · 넘치면 아래 `+N개 더`.
     * items = 줄마다 (높이 dp, 세는 줄인지 = 묶음 머리가 아님).
     * 돌려주는 값 = (목록 칸 높이 dp, 더 있는 줄 수) · 다 들어가거나 높이를 모르면 (-1, 0) = 레이아웃 그대로.
     */
    fun fit(items: List<Pair<Int, Boolean>>, avail: Int, moreH: Int): Pair<Int, Int> {
        if (avail <= 0 || items.isEmpty()) return Pair(-1, 0)
        var sum = 0
        for (it in items) sum += it.first
        if (sum <= avail) return Pair(-1, 0)
        var k = 0
        var used = 0
        val room = avail - moreH
        while (k < items.size && used + items[k].first <= room) {
            used += items[k].first
            k++
        }
        // 묶음 머리만 남고 그 아래 줄이 안 보이면 머리도 뺌
        while (k > 0 && !items[k - 1].second) {
            k--
            used -= items[k].first
        }
        // 한 줄도 온전히 안 들어가는 아주 낮은 위젯은 레이아웃 그대로(`+N개 더`만 남지 않게)
        if (k == 0) return Pair(-1, 0)
        var more = 0
        for (i in k until items.size) if (items[i].second) more++
        return Pair(used, more)
    }

    /**
     * fit 결과를 그림에(안드로이드 12 이상). 런처는 같은 레이아웃이면 앞 그림에 다시 덮어 그리므로(reapply)
     * 목록 칸 높이를 늘 정한다: 넘치면 온전한 줄까지 · 그 밖은 칸 전체(MATCH_PARENT로 되돌림).
     * 그 아래 판은 레이아웃 그대로(목록이 칸을 다 채움 · `+N개 더` 없음).
     */
    fun applyFit(
        context: Context,
        rv: RemoteViews,
        box: Int,
        moreView: Int,
        fit: Pair<Int, Int>,
        en: Boolean,
        level: Int
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            rv.setViewVisibility(moreView, View.GONE)
            return
        }
        if (fit.first < 0) {
            rv.setViewLayoutHeight(box, ViewGroup.LayoutParams.MATCH_PARENT.toFloat(), TypedValue.COMPLEX_UNIT_PX)
            rv.setViewVisibility(moreView, View.GONE)
            return
        }
        rv.setViewLayoutHeight(box, fit.first.toFloat(), TypedValue.COMPLEX_UNIT_DIP)
        val more = fit.second
        rv.setViewVisibility(moreView, if (more > 0) View.VISIBLE else View.GONE)
        rv.setTextViewText(moreView, if (en) "+$more more" else "+${more}개 더")
        rv.setViewPadding(moreView, dp(context, WidgetSize.CHK_W[level].toFloat()), 0, 0, 0)
    }
}

/**
 * 버튼 · 칸 바탕(앱 테마 토큰 그대로): 바깥 뷰 = 테두리 · 안쪽 뷰 = 바탕.
 * RemoteViews는 모양의 색 · 모서리를 코드로 못 바꾸므로, 흰 모양(모서리 4가지)에 색만 입힌다(안드로이드 12 이상).
 * - 바탕이 불투명하면 바깥 = 꽉 찬 모양(테두리 색) + 안쪽 여백 = 선 굵기 → 종이 2 · 다이어리 1.5도 그대로
 * - 바탕이 반투명(유리 · 화이트 · 네온)이면 바깥 = 선만 있는 모양(1) → 바탕 밑으로 테두리 색이 비치지 않게
 * - 테두리가 투명하면 바깥은 비움
 * 안드로이드 11 이하는 네모 바탕색만(오늘 할 일 위젯 칩과 같음).
 */
internal object Q11Box {
    private val FILL = intArrayOf(R.drawable.q11_f_r0, R.drawable.q11_f_r6, R.drawable.q11_f_r12, R.drawable.q11_f_pill)
    private val LINE = intArrayOf(R.drawable.q11_l_r0, R.drawable.q11_l_r6, R.drawable.q11_l_r12, R.drawable.q11_l_pill)

    /** 모서리 묶음: 0 네모 · 1 작게(6) · 2 보통(12) · 3 알약 */
    fun bucket(r: Float): Int = when {
        r <= 0.5f -> 0
        r <= 8.5f -> 1
        r < 100f -> 2
        else -> 3
    }

    fun paint(
        context: Context,
        rv: RemoteViews,
        outer: Int,
        inner: Int,
        fill: Int,
        line: Int,
        bw: Float,
        radius: Float
    ) {
        val b = bucket(radius)
        val hasLine = Color.alpha(line) > 0 && bw > 0f
        val pad = if (hasLine) Q11Widgets.dp(context, bw.coerceIn(1f, 3f)) else 0
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (hasLine) {
                rv.setInt(outer, "setBackgroundResource", if (Color.alpha(fill) == 255) FILL[b] else LINE[b])
                rv.setColorStateList(outer, "setBackgroundTintList", ColorStateList.valueOf(line))
            } else {
                rv.setInt(outer, "setBackgroundResource", 0)
            }
            if (Color.alpha(fill) > 0) {
                rv.setInt(inner, "setBackgroundResource", FILL[b])
                rv.setColorStateList(inner, "setBackgroundTintList", ColorStateList.valueOf(fill))
            } else {
                rv.setInt(inner, "setBackgroundResource", 0)
            }
        } else {
            rv.setInt(outer, "setBackgroundColor", if (hasLine) line else 0)
            rv.setInt(inner, "setBackgroundColor", fill)
        }
        rv.setViewPadding(outer, pad, pad, pad, pad)
    }

    /** 보조 버튼(음성 · 사진 · 묻기 줄) = ctlFillHex · ctlBorderHex × borderWidth · ctlRadius */
    fun ctl(context: Context, rv: RemoteViews, outer: Int, inner: Int, th: WidgetTheme, radius: Float = th.ctlRadius) =
        paint(context, rv, outer, inner, th.ctl, th.ctlLine, th.borderW, radius)

    /** 주 버튼(적기 · ＋ · 고른 날) = accentHex · accentLineHex × borderWidth */
    fun accent(context: Context, rv: RemoteViews, outer: Int, inner: Int, th: WidgetTheme, radius: Float = th.ctlRadius) =
        paint(context, rv, outer, inner, th.accent, th.accentLine, th.borderW, radius)

    /** 비움(고르지 않은 날 칸). */
    fun clear(context: Context, rv: RemoteViews, outer: Int, inner: Int) =
        paint(context, rv, outer, inner, 0, 0, 0f, 0f)

    /** 0.8.0 테두리 없이 바탕 하나(종이 적기 버튼 그림자). */
    fun solid(rv: RemoteViews, view: Int, color: Int, radius: Float) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            rv.setInt(view, "setBackgroundResource", FILL[bucket(radius)])
            rv.setColorStateList(view, "setBackgroundTintList", ColorStateList.valueOf(color))
        } else {
            rv.setInt(view, "setBackgroundColor", color)
        }
    }
}

/** 목록 줄 모양(오늘 할 일 · 한 주 같은 줄). */
internal object Q11Rows {

    fun head(context: Context, theme: WidgetTheme, level: Int, r: JSONObject): RemoteViews {
        val v = RemoteViews(context.packageName, WidgetSize.HEAD[level])
        v.setTextViewText(R.id.q11_head_text, r.optString("text", ""))
        val color = when (r.optString("tone")) {
            "danger" -> theme.danger
            "overdue" -> theme.overdue
            else -> theme.ink
        }
        v.setTextColor(R.id.q11_head_text, color)
        return v
    }

    fun task(context: Context, theme: WidgetTheme, level: Int, r: JSONObject): RemoteViews {
        val v = RemoteViews(context.packageName, WidgetSize.ROW[level])
        val id = r.optString("id", "")
        val done = id.isNotEmpty() && WidgetStore.isPendingDone(context, id)

        // 체크칸
        v.setImageViewResource(R.id.q11_chk_box, if (theme.square) R.drawable.q11_chk_square else R.drawable.q11_chk_round)
        TodayWidgetProvider.tintImage(v, R.id.q11_chk_box, theme.chkBorder)
        v.setImageViewResource(R.id.q11_chk_fill, if (theme.square) R.drawable.q11_chk_square_fill else R.drawable.q11_chk_round_fill)
        TodayWidgetProvider.tintImage(v, R.id.q11_chk_fill, theme.chkDone)
        TodayWidgetProvider.tintImage(v, R.id.q11_chk_mark, theme.chkDoneInk)
        v.setViewVisibility(R.id.q11_chk_fill, if (done) View.VISIBLE else View.GONE)
        v.setViewVisibility(R.id.q11_chk_mark, if (done) View.VISIBLE else View.GONE)
        v.setViewVisibility(R.id.q11_chk_box, if (done) View.GONE else View.VISIBLE)

        // 지금 칩
        val now = r.optBoolean("now", false) && !done
        v.setViewVisibility(R.id.q11_now, if (now) View.VISIBLE else View.GONE)
        if (now) {
            v.setTextViewText(R.id.q11_now, r.optString("nowText", "지금"))
            TodayWidgetProvider.tint(v, R.id.q11_now, theme.accent, TodayWidgetProvider.chipShape(theme))
            v.setTextColor(R.id.q11_now, theme.accentInk)
        }

        // 긴급 · 중요 칩
        val flag = r.optString("flag", "")
        val showFlag = !done && (flag == "urgent" || flag == "important")
        v.setViewVisibility(R.id.q11_flag, if (showFlag) View.VISIBLE else View.GONE)
        if (showFlag) {
            val c = if (flag == "urgent") theme.danger else theme.warn
            v.setTextViewText(R.id.q11_flag, r.optString("flagText", ""))
            v.setTextColor(R.id.q11_flag, c)
            TodayWidgetProvider.tint(v, R.id.q11_flag, WidgetTheme.alpha(c, if (flag == "urgent") 0.18f else 0.16f), TodayWidgetProvider.chipShape(theme))
        }

        // 제목 · (0.7.0 한 주) 프로젝트 단계 칩 · 오른쪽 날짜
        v.setTextViewText(R.id.q11_title, r.optString("title", ""))
        v.setTextColor(R.id.q11_title, if (done) theme.doneTitle else theme.ink)
        val flags = Paint.ANTI_ALIAS_FLAG or (if (done) Paint.STRIKE_THRU_TEXT_FLAG else 0)
        v.setInt(R.id.q11_title, "setPaintFlags", flags)
        val chip = r.optString("chip", "")
        v.setViewVisibility(R.id.q11_step, if (chip.isNotEmpty()) View.VISIBLE else View.GONE)
        if (chip.isNotEmpty()) {
            v.setTextViewText(R.id.q11_step, chip)
            v.setTextColor(R.id.q11_step, theme.stepInk)
            TodayWidgetProvider.tint(v, R.id.q11_step, theme.step, TodayWidgetProvider.chipShape(theme))
        }
        val meta = r.optString("meta", "")
        v.setTextViewText(R.id.q11_meta, meta)
        v.setViewVisibility(R.id.q11_meta, if (meta.isEmpty()) View.GONE else View.VISIBLE)
        v.setTextColor(R.id.q11_meta, if (r.optBoolean("over", false) && !done) theme.overdue else theme.meta)

        // 누르기: 체크칸 = 끝냄/되돌림 · 나머지 = 앱 열기
        v.setOnClickFillInIntent(
            R.id.q11_chk,
            Intent().putExtra(TodayWidgetProvider.EXTRA_KIND, "check").putExtra(TodayWidgetProvider.EXTRA_ID, id)
        )
        v.setOnClickFillInIntent(
            R.id.q11_body,
            Intent().putExtra(TodayWidgetProvider.EXTRA_KIND, "open").putExtra(TodayWidgetProvider.EXTRA_ID, id)
        )
        return v
    }

    /** 한 주 · 프로젝트 마감 날 맨 위 ◆ 줄(시안: ◆ 국감 대응 자료 마감 · 단계 2/4). */
    fun project(context: Context, theme: WidgetTheme, level: Int, r: JSONObject): RemoteViews {
        val v = RemoteViews(context.packageName, R.layout.q11_widget_prow)
        v.setTextViewText(R.id.q11_p_title, r.optString("title", ""))
        v.setTextColor(R.id.q11_p_title, theme.projInk)
        v.setTextColor(R.id.q11_p_dia, theme.proj)
        val late = r.optBoolean("late", false)
        v.setTextViewText(R.id.q11_p_meta, r.optString("meta", ""))
        v.setTextColor(R.id.q11_p_meta, if (late) theme.warn else theme.meta)
        v.setTextViewTextSize(R.id.q11_p_title, TypedValue.COMPLEX_UNIT_SP, WidgetSize.ROW_TITLE[level])
        v.setTextViewTextSize(R.id.q11_p_meta, TypedValue.COMPLEX_UNIT_SP, WidgetSize.ROW_META[level])
        v.setTextViewTextSize(R.id.q11_p_dia, TypedValue.COMPLEX_UNIT_SP, WidgetSize.ROW_META[level])
        v.setOnClickFillInIntent(
            R.id.q11_p_row,
            Intent().putExtra(TodayWidgetProvider.EXTRA_KIND, "open").putExtra(TodayWidgetProvider.EXTRA_ID, r.optString("id", ""))
        )
        return v
    }

    /** 점(남은 일 · 긴급은 첫 점 빨강) · 4개까지. */
    fun dots(n: Int, urgent: Boolean, dotColor: Int, urgentColor: Int): CharSequence {
        val sb = SpannableStringBuilder()
        val k = n.coerceIn(0, 4)
        for (i in 0 until k) {
            val start = sb.length
            sb.append("●")
            val c = if (i == 0 && urgent) urgentColor else dotColor
            sb.setSpan(ForegroundColorSpan(c), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        return sb
    }
}
