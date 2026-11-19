package day.q11.widget

import android.app.Activity
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognizerIntent
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import org.json.JSONObject
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * 위젯 ＋ → 홈 화면 위 작은 창(1.1 시안 WidgetGalaxyA `＋적기`). 앱은 열리지 않는다.
 * 적고 `넣기` → 앱 코드(Dart · q11WidgetBg 'add')를 화면 없이 실행 → 서버 /pc/send(AI 1회)로 할 일을 넣고
 * 그 일의 폰 알림까지 → 창이 닫히며 아래 `넣었어요 · 제목 · 언제 · 되돌리기`(5초) → 되돌리기 = /pc/undo.
 * 색 · 모서리 · 선 · 그림자는 앱이 넘긴 테마 토큰(확인 창 · 입력칸 · 보조 버튼 · 토스트와 같은 값).
 * 앱을 한 번도 안 열었으면 이 창 대신 앱의 새 할 일 칸을 연다.
 */
class QuickAddActivity : Activity() {

    private val main = Handler(Looper.getMainLooper())
    private lateinit var th: WidgetTheme
    private var en = false
    private var signedIn = false
    private var held = false
    private var micOpen = false
    private var showingError = false

    /** 0 적기 · 1 넣는 중 · 2 넣었어요(토스트) · 3 되돌리는 중 · 4 닫는 중 */
    private var phase = 0
    private var added: List<String> = emptyList()
    private var dot = 0

    private lateinit var root: View
    private lateinit var sheet: LinearLayout
    private lateinit var row: View
    private lateinit var input: EditText
    private lateinit var mic: ImageView
    private lateinit var send: TextView
    private lateinit var busyView: TextView
    private lateinit var help: TextView
    private lateinit var openBtn: TextView
    private lateinit var toast: LinearLayout
    private lateinit var toastText: TextView
    private lateinit var undo: TextView
    private var openAction: String? = null

    private val autoClose = Runnable { close() }
    private val dots = object : Runnable {
        override fun run() {
            if (phase != 1) return
            dot = dot % 3 + 1
            busyView.text = t("busy") + "  " + "●".repeat(dot)
            main.postDelayed(this, 380L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val feed = WidgetStore.feed(this)
        if (feed == null || !BgRunner.canRun(this)) {
            // 앱을 한 번도 안 열었으면: 작은 창 대신 앱의 새 할 일 칸
            openApp("add")
            return
        }
        en = feed.optString("lang", "ko") == "en"
        signedIn = feed.optString("uid", "").isNotEmpty()
        th = WidgetTheme(feed.optJSONObject("theme"))

        setContentView(R.layout.q11_quick_add)
        window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        window.setGravity(Gravity.BOTTOM)
        setFinishOnTouchOutside(true)

        bind()
        paint()
        texts()
        insets()
        if (signedIn) {
            BgRunner.hold(this)
            held = true
            input.requestFocus()
        } else {
            needSignIn()
        }
    }

    // ── 그리기 ─────────────────────────────────────────

    private fun bind() {
        root = findViewById(R.id.q11_qa_root)
        sheet = findViewById(R.id.q11_qa_sheet)
        row = findViewById(R.id.q11_qa_row)
        input = findViewById(R.id.q11_qa_input)
        mic = findViewById(R.id.q11_qa_mic)
        send = findViewById(R.id.q11_qa_send)
        busyView = findViewById(R.id.q11_qa_busy)
        help = findViewById(R.id.q11_qa_help)
        openBtn = findViewById(R.id.q11_qa_open)
        toast = findViewById(R.id.q11_qa_toast)
        toastText = findViewById(R.id.q11_qa_ttext)
        undo = findViewById(R.id.q11_qa_undo)
    }

    private fun dp(v: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics)

    /** 칠한 네모(모서리 · 선). 선 굵기는 테마 borderWidth. */
    private fun box(fill: Int, stroke: Int, strokeW: Float, radius: Float): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(radius)
            setColor(fill)
            if (strokeW > 0f && Color.alpha(stroke) > 0) setStroke(max(1, dp(strokeW).roundToInt()), stroke)
        }

    /** 그림자에 흐림이 없는 테마(종이 · 다이어리) = 오른쪽 아래로 민 같은 모양. */
    private fun hardShadow(): Boolean =
        Color.alpha(th.shadow) > 0 && th.shadowBlur <= 0.5f && (th.shadowX != 0f || th.shadowY != 0f)

    private fun card(fill: Int, stroke: Int, radius: Float): Drawable {
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
    private fun setBg(v: View, d: Drawable) {
        val l = v.paddingLeft
        val t = v.paddingTop
        val r = v.paddingRight
        val b = v.paddingBottom
        v.background = d
        v.setPadding(l, t, r, b)
    }

    /** 흐린 그림자(유리 · 네온 · 스위스 · 화이트). 토큰이 투명하면 앱 토스트와 같게 검정 45% · 흐림 28. */
    private fun lift(v: View) {
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

    private fun paint() {
        setBg(sheet, card(th.sheet, th.line, th.dlgRadius))
        lift(sheet)
        for (id in intArrayOf(R.id.q11_qa_logo, R.id.q11_qa_tlogo)) {
            val logo = findViewById<TextView>(id)
            setBg(logo, box(th.accent, 0, 0f, th.tagRadius))
            logo.setTextColor(th.accentInk)
        }
        findViewById<TextView>(R.id.q11_qa_title).setTextColor(th.ink)
        val close = findViewById<ImageView>(R.id.q11_qa_close)
        setBg(close, box(th.ctl, th.ctlLine, th.borderW, th.ctlRadius))
        close.imageTintList = ColorStateList.valueOf(th.ctlInk)
        setBg(mic, box(th.ctl, th.ctlLine, th.borderW, th.ctlRadius))
        mic.imageTintList = ColorStateList.valueOf(th.ctlInk)

        // 입력칸: 적는 중이라 테두리는 accentHex(시안) · 힌트 metaHex
        setBg(input, box(th.field, th.accent, th.borderW, th.ctlRadius))
        input.setTextColor(th.ink)
        input.setHintTextColor(th.meta)
        input.highlightColor = WidgetTheme.alpha(th.accent, 0.35f)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            input.textCursorDrawable = GradientDrawable().apply {
                setColor(th.accent)
                setSize(dp(2f).roundToInt(), dp(22f).roundToInt())
            }
        }
        setBg(send, box(th.accent, th.accentLine, th.borderW, th.ctlRadius))
        send.setTextColor(th.accentInk)
        busyView.setTextColor(WidgetTheme.alpha(th.ink, 0.8f))
        help.setTextColor(th.meta)
        setBg(openBtn, box(th.ctl, th.ctlLine, th.borderW, th.ctlRadius))
        openBtn.setTextColor(th.ctlInk)

        // 토스트 = 앱 토스트와 같은 토큰(alertFillHex · ctlBorderHex × borderWidth · pillRadius · 되돌리기 linkHex)
        setBg(toast, card(th.alertFill, th.ctlLine, th.pillRadius))
        lift(toast)
        toastText.setTextColor(th.ink)
        undo.setTextColor(th.link)
    }

    private fun texts() {
        findViewById<TextView>(R.id.q11_qa_title).text = t("title")
        findViewById<View>(R.id.q11_qa_close).contentDescription = t("close")
        mic.contentDescription = t("mic")
        input.hint = t("hint")
        send.text = t("send")
        help.text = t("help")
        undo.text = t("undo")

        // 여러 줄로 보이되 키보드에는 `보내기` 단추(엔터 = 넣기)
        input.setHorizontallyScrolling(false)
        input.maxLines = 3
        input.imeOptions = EditorInfo.IME_ACTION_SEND or EditorInfo.IME_FLAG_NO_EXTRACT_UI
        input.setRawInputType(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES)
        input.setOnEditorActionListener { _, actionId, ev ->
            val enter = ev != null && ev.keyCode == KeyEvent.KEYCODE_ENTER && ev.action == KeyEvent.ACTION_DOWN
            if (actionId == EditorInfo.IME_ACTION_SEND || enter) {
                submit()
                true
            } else {
                false
            }
        }
        input.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (showingError && phase == 0) clearError()
                sendState()
            }
        })
        send.setOnClickListener { submit() }
        mic.setOnClickListener { listen() }
        findViewById<View>(R.id.q11_qa_close).setOnClickListener { close() }
        undo.setOnClickListener { undoAdd() }
        openBtn.setOnClickListener { openApp(openAction) }
        mic.visibility = if (canListen()) View.VISIBLE else View.GONE
        sendState()
    }

    private fun sendState() {
        send.alpha = if (input.text.isNullOrBlank()) 0.5f else 1f
    }

    /** 이 창이 화면 아래 · 키보드 아래로 그려지면(안드로이드 15 이상 꽉 찬 화면) 그만큼 올린다. */
    private fun insets() {
        val base = root.paddingBottom
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            root.setOnApplyWindowInsetsListener { v, ins ->
                val b = ins.getInsets(WindowInsets.Type.ime() or WindowInsets.Type.navigationBars()).bottom
                v.setPadding(v.paddingLeft, v.paddingTop, v.paddingRight, base + b)
                ins
            }
        }
    }

    // ── 키보드 ─────────────────────────────────────────

    /** 키보드를 띄울지만 바꾸고, 창을 키보드 위로 올리는 방식(매니페스트 adjustResize)은 그대로. */
    private fun softInput(state: Int) {
        val adjust = window.attributes.softInputMode and WindowManager.LayoutParams.SOFT_INPUT_MASK_ADJUST
        window.setSoftInputMode(state or adjust)
    }

    private fun imm(): InputMethodManager? = getSystemService(INPUT_METHOD_SERVICE) as? InputMethodManager

    private fun showIme() {
        if (phase != 0 || !signedIn) return
        input.requestFocus()
        input.post { imm()?.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT) }
    }

    private fun hideIme() {
        try {
            imm()?.hideSoftInputFromWindow(input.windowToken, 0)
        } catch (e: Exception) {
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && phase == 0 && signedIn) showIme()
    }

    // ── 넣기 ──────────────────────────────────────────

    private fun submit() {
        if (phase != 0 || !signedIn) return
        val text = input.text.toString().trim()
        if (text.isEmpty()) {
            fail(t("empty"), offerApp = false)
            return
        }
        phase = 1
        clearError()
        row.visibility = View.GONE
        busyView.visibility = View.VISIBLE
        dot = 0
        main.removeCallbacks(dots)
        main.post(dots)
        BgRunner.run(this, "add", text) { res -> onAdded(res) }
    }

    private fun onAdded(res: String?) {
        if (isFinishing || isDestroyed) return
        main.removeCallbacks(dots)
        val o = try {
            if (res.isNullOrEmpty()) null else JSONObject(res)
        } catch (e: Exception) {
            null
        }
        val items = o?.optJSONArray("items")
        if (o != null && o.optBoolean("ok", false) && items != null && items.length() > 0) {
            val ids = ArrayList<String>()
            for (i in 0 until items.length()) {
                val id = items.optJSONObject(i)?.optString("id", "") ?: ""
                if (id.isNotEmpty()) ids.add(id)
            }
            val first = items.optJSONObject(0)
            val sb = StringBuilder(t("added"))
            val title = first?.optString("title", "") ?: ""
            if (title.isNotEmpty()) sb.append(" · ").append(title)
            val label = first?.optString("label", "") ?: ""
            if (label.isNotEmpty()) sb.append(" · ").append(label)
            val more = items.length() - 1
            if (more > 0) sb.append(if (en) " +$more more" else " 외 ${more}개")
            showToast(sb.toString(), ids)
            return
        }
        phase = 0
        busyView.visibility = View.GONE
        row.visibility = View.VISIBLE
        when (o?.optString("kind", "") ?: "") {
            "auth" -> {
                needSignIn()
                return
            }
            "limit" -> fail(o?.optString("message", "").orEmpty().ifBlank { t("limit") }, offerApp = true)
            "dup" -> {
                val d = o?.optString("dup", "").orEmpty()
                fail(if (d.isBlank()) t("dup") else t("dup") + " · " + d, offerApp = false)
            }
            "none" -> fail(t("none"), offerApp = false)
            "question" -> fail(t("question"), offerApp = true, action = null)
            "empty" -> fail(t("empty"), offerApp = false)
            else -> fail(t("fail"), offerApp = true)
        }
        showIme()
    }

    private fun fail(msg: String, offerApp: Boolean, action: String? = "add") {
        showingError = true
        help.text = msg
        help.setTextColor(th.danger)
        if (offerApp) {
            openAction = action
            openBtn.text = if (action == null) t("openApp") else t("addInApp")
            openBtn.visibility = View.VISIBLE
        } else {
            openBtn.visibility = View.GONE
        }
    }

    private fun clearError() {
        if (!showingError) return
        showingError = false
        help.text = t("help")
        help.setTextColor(th.meta)
        openBtn.visibility = View.GONE
    }

    private fun needSignIn() {
        signedIn = false
        row.visibility = View.GONE
        busyView.visibility = View.GONE
        help.text = t("signin")
        help.setTextColor(th.ink)
        openAction = null
        openBtn.text = t("openApp")
        openBtn.visibility = View.VISIBLE
        hideIme()
        softInput(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
    }

    // ── 넣었어요 · 되돌리기 ───────────────────────────────

    private fun showToast(msg: String, ids: List<String>) {
        phase = 2
        added = ids
        hideIme()
        sheet.visibility = View.GONE
        toastText.text = msg
        undo.visibility = if (ids.isEmpty()) View.GONE else View.VISIBLE
        toast.visibility = View.VISIBLE
        // 토스트만 남을 땐 홈 화면을 가리지 않게: 어둡게 끔 · 바깥을 누르면 그대로 홈 화면에 닿고 창은 닫힘
        window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        window.addFlags(
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
        )
        softInput(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
        main.removeCallbacks(autoClose)
        main.postDelayed(autoClose, TOAST_MS)
    }

    private fun undoAdd() {
        if (phase != 2 || added.isEmpty()) return
        phase = 3
        main.removeCallbacks(autoClose)
        undo.visibility = View.GONE
        toastText.text = t("undoing")
        BgRunner.run(this, "undoAdd", added.joinToString(",")) { res ->
            if (!isFinishing && !isDestroyed) {
                val ok = try {
                    JSONObject(res ?: "").optBoolean("ok", false)
                } catch (e: Exception) {
                    false
                }
                toastText.text = if (ok) t("undone") else t("undoFail")
                main.postDelayed(autoClose, if (ok) 1_400L else 2_600L)
            }
        }
    }

    private fun reset() {
        main.removeCallbacks(autoClose)
        phase = 0
        added = emptyList()
        toast.visibility = View.GONE
        sheet.visibility = View.VISIBLE
        busyView.visibility = View.GONE
        row.visibility = View.VISIBLE
        clearError()
        input.setText("")
        window.clearFlags(
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
        )
        window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        softInput(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
        showIme()
    }

    // ── 말로 적기(폰의 음성 입력 창) ─────────────────────────

    private fun micIntent(): Intent =
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, if (en) "en-US" else "ko-KR")
            .putExtra(RecognizerIntent.EXTRA_PROMPT, t("micPrompt"))
            .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)

    @Suppress("DEPRECATION")
    private fun canListen(): Boolean =
        try {
            packageManager.queryIntentActivities(micIntent(), 0).isNotEmpty()
        } catch (e: Exception) {
            false
        }

    @Suppress("DEPRECATION")
    private fun listen() {
        if (phase != 0) return
        try {
            micOpen = true
            startActivityForResult(micIntent(), REQ_MIC)
        } catch (e: Exception) {
            micOpen = false
            mic.visibility = View.GONE
        }
    }

    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQ_MIC) return
        micOpen = false
        if (resultCode == RESULT_OK) {
            val said = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.trim().orEmpty()
            if (said.isNotEmpty()) {
                val cur = input.text.toString().trim()
                val next = if (cur.isEmpty()) said else "$cur $said"
                input.setText(next)
                input.setSelection(next.length)
            }
        }
        showIme()
    }

    // ── 닫기 ──────────────────────────────────────────

    private fun openApp(action: String?) {
        phase = 4
        try {
            TodayWidgetProvider.openAppIntent(this, action)?.let { startActivity(it) }
        } catch (e: Exception) {
        }
        finish()
    }

    private fun close() {
        phase = 4
        hideIme()
        finish()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (!::th.isInitialized) return
        if (phase >= 2) reset() else showIme()
    }

    override fun onStop() {
        super.onStop()
        // 홈 키 · 다른 앱으로 가면 창을 닫는다(넣는 중이면 넣기는 뒤에서 마저 함)
        if (!isChangingConfigurations && !micOpen && !isFinishing) finish()
    }

    override fun onDestroy() {
        main.removeCallbacksAndMessages(null)
        if (held) {
            held = false
            BgRunner.release()
        }
        super.onDestroy()
    }

    private fun t(k: String): String = (if (en) EN else KO)[k] ?: k

    companion object {
        private const val REQ_MIC = 7101
        private const val TOAST_MS = 5_000L

        private val KO = mapOf(
            "title" to "할 일 적기",
            "hint" to "말하듯 적어요 · 5시 거래처 전화",
            "send" to "넣기",
            "help" to "앱은 안 열려요 · 말하듯 적으면 AI가 날짜 · 시각까지 정리해요",
            "busy" to "AI가 날짜를 정리하는 중",
            "added" to "넣었어요",
            "undo" to "되돌리기",
            "undoing" to "되돌리는 중",
            "undone" to "되돌렸어요",
            "undoFail" to "지우지 못했어요 · 앱에서 지워 주세요",
            "signin" to "11Q에 로그인하면 적을 수 있어요",
            "openApp" to "앱 열기",
            "addInApp" to "앱에서 적기",
            "limit" to "오늘 AI 횟수를 다 썼어요 · 앱에서 직접 적을 수 있어요",
            "dup" to "이미 있는 할 일이에요",
            "none" to "할 일을 못 찾았어요 · 조금 더 자세히 적어 주세요",
            "question" to "질문은 앱 대화에서 물어봐 주세요",
            "empty" to "할 일을 적어 주세요",
            "fail" to "보내지 못했어요 · 잠시 뒤 다시 눌러 주세요",
            "micPrompt" to "할 일을 말해 주세요",
            "close" to "닫기",
            "mic" to "말로 적기"
        )

        private val EN = mapOf(
            "title" to "Add a task",
            "hint" to "Just type it · Call the client at 5",
            "send" to "Add",
            "help" to "11Q stays closed · AI sorts out the date and time",
            "busy" to "AI is sorting out the date",
            "added" to "Added",
            "undo" to "Undo",
            "undoing" to "Undoing",
            "undone" to "Undone",
            "undoFail" to "Couldn't undo · please delete it in the app",
            "signin" to "Sign in to 11Q to add tasks",
            "openApp" to "Open 11Q",
            "addInApp" to "Add in the app",
            "limit" to "You've used today's AI · you can still add it in the app",
            "dup" to "Already on your list",
            "none" to "Couldn't find a task · try adding a little more",
            "question" to "Ask questions in the app's Chat",
            "empty" to "Type a task first",
            "fail" to "Couldn't send · please try again in a moment",
            "micPrompt" to "Say your task",
            "close" to "Close",
            "mic" to "Speak"
        )
    }
}
