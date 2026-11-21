package day.q11.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Build
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.RemoteViews
import kotlin.math.roundToInt

/**
 * 0.3.2(1.1용): 0.8.0 위젯 다듬기(시안 WidgetPolish) 중 오늘 할 일 위젯에 쓰는 것만 · 0.8.0과 같은 이름 · 같은 코드.
 * - 온전한 줄만(fit · applyFit): 위젯 높이로 들어가는 줄을 세어 목록 칸 높이를 정하고 넘치면 `+N개 더`
 */
internal object Q11Widgets {

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

    /** 목록 칸 높이(dp) = 위젯 높이 − 머리 · 여백(fixed) · 위젯 높이를 모르면 0. */
    fun listAvail(context: Context, widgetId: Int, fixed: Int): Int {
        val h = sizeDp(context, widgetId).second
        return if (h <= 0) 0 else (h - fixed).coerceAtLeast(0)
    }

    /**
     * 마지막 줄이 잘리지 않게(시안 WidgetPolish ②): 들어가는 만큼만 온전한 줄 · 넘치면 아래 `+N개 더`.
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
 * 버튼 바탕(앱 테마 토큰 그대로 · 0.8.0과 같음): 바깥 뷰 = 테두리 · 안쪽 뷰 = 바탕.
 * RemoteViews는 모양의 색 · 모서리를 코드로 못 바꾸므로, 흰 모양(모서리 4가지)에 색만 입힌다(안드로이드 12 이상).
 * - 바탕이 불투명하면 바깥 = 꽉 찬 모양(테두리 색) + 안쪽 여백 = 선 굵기
 * - 바탕이 반투명(유리 · 화이트 · 네온)이면 바깥 = 선만 있는 모양(1) → 바탕 밑으로 테두리 색이 비치지 않게
 * - 테두리가 투명하면 바깥은 비움
 * 안드로이드 11 이하는 네모 바탕색만(칩과 같음).
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
}
