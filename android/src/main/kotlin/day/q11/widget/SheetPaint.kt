package day.q11.widget

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.os.Build
import android.util.TypedValue
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * 작은 창 · 위젯 설정 창을 앱 테마 토큰으로 칠하는 붓(0.3.0).
 * 창 = 확인 창 토큰(dlgFillHex · cardBorderHex × borderWidth · dlgRadius) · 보조 버튼 = ctl 토큰 ·
 * 주 버튼 = accentHex · accentInkHex · accentLineHex · 그림자 = shadow 토큰(투명한 테마는 앱 토스트처럼 검정 45% · 흐림 28).
 */
internal class SheetPaint(private val context: Context, val th: WidgetTheme) {

    fun dp(v: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, context.resources.displayMetrics)

    /** 칠한 네모(모서리 · 선). */
    fun box(fill: Int, stroke: Int, strokeW: Float, radius: Float): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(radius)
            setColor(fill)
            if (strokeW > 0f && Color.alpha(stroke) > 0) setStroke(max(1, dp(strokeW).roundToInt()), stroke)
        }

    /** 그림자에 흐림이 없는 테마(종이 · 다이어리) = 오른쪽 아래로 민 같은 모양. */
    fun hardShadow(): Boolean =
        Color.alpha(th.shadow) > 0 && th.shadowBlur <= 0.5f && (th.shadowX != 0f || th.shadowY != 0f)

    fun card(fill: Int, stroke: Int, radius: Float): Drawable {
        val face = box(fill, stroke, th.borderW, radius)
        if (!hardShadow()) return face
        val shade = box(th.shadow, 0, 0f, radius)
        val sx = dp(th.shadowX).roundToInt()
        val sy = dp(th.shadowY).roundToInt()
        val ld = LayerDrawable(arrayOf<Drawable>(shade, face))
        ld.setLayerInset(0, max(0, sx), max(0, sy), max(0, -sx), max(0, -sy))
        ld.setLayerInset(1, max(0, -sx), max(0, -sy), max(0, sx), max(0, sy))
        return ld
    }

    /** 바탕을 바꿔도 안쪽 여백은 그대로(LayerDrawable이 여백을 바꾸지 않게). */
    fun setBg(v: View, d: Drawable) {
        val l = v.paddingLeft
        val t = v.paddingTop
        val r = v.paddingRight
        val b = v.paddingBottom
        v.background = d
        v.setPadding(l, t, r, b)
    }

    /** 흐린 그림자(유리 · 네온 · 스위스 · 화이트). */
    fun lift(v: View) {
        if (hardShadow()) {
            v.elevation = 0f
            return
        }
        val clear = Color.alpha(th.shadow) == 0
        val c = if (clear) 0x73000000 else th.shadow
        val blur = if (clear) 28f else max(6f, th.shadowBlur)
        v.elevation = dp(blur / 2f)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            v.outlineSpotShadowColor = c
            v.outlineAmbientShadowColor = c
        }
    }

    /** 창(시트) 바탕 + 그림자. */
    fun sheet(v: View) {
        setBg(v, card(th.sheet, th.line, th.dlgRadius))
        lift(v)
    }

    /** 11Q 네모 로고. */
    fun logo(v: TextView) {
        setBg(v, box(th.accent, 0, 0f, th.tagRadius))
        v.setTextColor(th.accentInk)
    }

    /** 보조 버튼(닫기 · 말로 적기 · 고르지 않은 칸). */
    fun ctl(v: View) {
        setBg(v, box(th.ctl, th.ctlLine, th.borderW, th.ctlRadius))
        when (v) {
            is ImageView -> v.imageTintList = ColorStateList.valueOf(th.ctlInk)
            is TextView -> v.setTextColor(th.ctlInk)
        }
    }

    /** 주 버튼(넣기 · 완료 · 고른 칸). */
    fun primary(v: TextView) {
        setBg(v, box(th.accent, th.accentLine, th.borderW, th.ctlRadius))
        v.setTextColor(th.accentInk)
    }

    /** 강조 테두리 버튼(카메라): 선 = accentLineHex(없으면 accentHex) · 바탕 = accent 8% · 그림 = linkHex. */
    fun accentOutline(v: ImageView) {
        val stroke = if (Color.alpha(th.accentLine) > 0) th.accentLine else th.accent
        setBg(v, box(WidgetTheme.alpha(th.accent, 0.08f), stroke, max(1f, th.borderW), th.ctlRadius))
        v.imageTintList = ColorStateList.valueOf(th.link)
    }
}
