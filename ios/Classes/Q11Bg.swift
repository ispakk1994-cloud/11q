import Flutter
import Foundation

/// 앱 화면 없이 Dart를 잠깐 돌린다(아이폰 · 위젯 체크 · 바로 적기 · 되돌리기 · 뒤에서 확인). 갤럭시 BgRunner와 같은 길.
/// 앱(WidgetCatch)이 setHandles로 넘긴 함수 번호로 새 FlutterEngine(화면 없음)에서 q11WidgetDispatch → q11WidgetBg(action, id)를 부르고,
/// 일이 끝나고 5초 쉬면 엔진을 닫는다. 여러 번 눌러도 엔진 하나로 차례대로. 메인 스레드에서만.
final class Q11BgJob {
    let action: String
    let id: String
    let done: (String?) -> Void
    var finished = false

    init(_ action: String, _ id: String, _ done: @escaping (String?) -> Void) {
        self.action = action
        self.id = id
        self.done = done
    }
}

final class Q11BgRunner {
    static let shared = Q11BgRunner()

    private static let channelName = "oneone_q_widget/bg"
    private var engine: FlutterEngine?
    private var channel: FlutterMethodChannel?
    private var ready = false
    private var busy = false
    private var queue: [Q11BgJob] = []
    private var idleWork: DispatchWorkItem?
    private var startWork: DispatchWorkItem?

    /// 앱을 한 번이라도 열어 함수 번호가 있는지
    static var canRun: Bool {
        let (d, c) = Q11Store.handles()
        return d != 0 && c != 0
    }

    func run(_ action: String, _ id: String, done: @escaping (String?) -> Void) {
        let (dispatcher, callback) = Q11Store.handles()
        if dispatcher == 0 || callback == 0 {
            done(nil)
            return
        }
        queue.append(Q11BgJob(action, id, done))
        idleWork?.cancel()
        if engine == nil {
            start(dispatcher)
        } else {
            pump()
        }
    }

    private func start(_ dispatcher: Int64) {
        guard let info = FlutterCallbackCache.lookupCallbackInformation(dispatcher) else {
            failAll()
            return
        }
        let e = FlutterEngine(name: "q11_widget_bg", project: nil, allowHeadlessExecution: true)
        let ch = FlutterMethodChannel(name: Q11BgRunner.channelName, binaryMessenger: e.binaryMessenger)
        ch.setMethodCallHandler { [weak self] call, result in
            guard let self = self else {
                result(nil)
                return
            }
            if call.method == "ready" {
                self.ready = true
                self.startWork?.cancel()
                result(nil)
                self.pump()
            } else {
                result(FlutterMethodNotImplemented)
            }
        }
        engine = e
        channel = ch
        ready = false
        guard e.run(withEntrypoint: info.callbackName, libraryURI: info.callbackLibraryPath) else {
            failAll()
            return
        }
        Q11Plugins.register(on: e)
        // Dart가 'ready'를 못 보내면(앱 코드 오류 등) 기다리던 일을 모두 끝냄으로 돌리고 엔진을 닫는다
        let w = DispatchWorkItem { [weak self] in
            if self?.ready == false { self?.failAll() }
        }
        startWork = w
        DispatchQueue.main.asyncAfter(deadline: .now() + 20, execute: w)
    }

    private func pump() {
        guard ready, !busy, let ch = channel else { return }
        guard !queue.isEmpty else {
            scheduleIdle()
            return
        }
        let job = queue.removeFirst()
        busy = true
        let callback = Q11Store.handles().1
        let limit: TimeInterval = (job.action == "add" || job.action == "undoAdd") ? 45 : 30
        let timeout = DispatchWorkItem { [weak self] in self?.finish(job, nil) }
        DispatchQueue.main.asyncAfter(deadline: .now() + limit, execute: timeout)
        let args: [String: Any] = ["handle": NSNumber(value: callback), "action": job.action, "id": job.id]
        ch.invokeMethod("run", arguments: args) { [weak self] r in
            timeout.cancel()
            var out: String?
            if let s = r as? String {
                out = s
            } else if let n = r as? NSNumber {
                out = n.boolValue ? "true" : "false"
            }
            if r is FlutterError { out = nil }
            if let o = r as? NSObject, o === FlutterMethodNotImplemented { out = nil }
            self?.finish(job, out)
        }
    }

    private func finish(_ job: Q11BgJob, _ r: String?) {
        if job.finished { return }
        job.finished = true
        busy = false
        job.done(r)
        OneoneQWidgetPlugin.reload()
        pump()
    }

    private func scheduleIdle() {
        idleWork?.cancel()
        let w = DispatchWorkItem { [weak self] in self?.stop() }
        idleWork = w
        DispatchQueue.main.asyncAfter(deadline: .now() + 5, execute: w)
    }

    private func failAll() {
        let jobs = queue
        queue.removeAll()
        for j in jobs where !j.finished {
            j.finished = true
            j.done(nil)
        }
        busy = false
        stop()
    }

    private func stop() {
        if busy || !queue.isEmpty { return }
        startWork?.cancel()
        channel?.setMethodCallHandler(nil)
        engine?.destroyContext()
        engine = nil
        channel = nil
        ready = false
    }
}

/// 화면 없는 엔진에 플러그인 붙이기: 앱 코드(q11WidgetBg)가 쓰는 것만(Firebase · 알림 · 폰 저장 · 이 플러그인).
/// 다른 플러그인까지 붙이면 앱 대리자(알림 · 주소)를 두 번 받는 것이 생겨서. 찾지 못하면 앱 전체 목록(GeneratedPluginRegistrant)으로.
enum Q11Plugins {
    /// 이 플러그인을 엔진에 다시 붙일 때 앱 대리자는 빼려고(메인 엔진 것만)
    static var registeringBackground = false

    private static let wanted: [(String, [String])] = [
        ("FLTFirebaseCorePlugin", ["FLTFirebaseCorePlugin"]),
        ("FLTFirebaseAuthPlugin", ["FLTFirebaseAuthPlugin"]),
        ("FLTFirebaseFirestorePlugin", ["FLTFirebaseFirestorePlugin"]),
        ("FlutterLocalNotificationsPlugin", ["FlutterLocalNotificationsPlugin"]),
        ("SharedPreferencesPlugin", ["SharedPreferencesPlugin", "shared_preferences_foundation.SharedPreferencesPlugin"]),
    ]

    static func register(on engine: FlutterEngine) {
        registeringBackground = true
        defer { registeringBackground = false }
        if let r = engine.registrar(forPlugin: "OneoneQWidgetPlugin") {
            OneoneQWidgetPlugin.register(with: r)
        }
        let sel = NSSelectorFromString("registerWithRegistrar:")
        var core = false
        for (key, names) in wanted {
            guard let cls = names.lazy.compactMap({ NSClassFromString($0) }).first else {
                NSLog("[11Q] 뒤 엔진: \(key) 없음")
                continue
            }
            let obj = cls as AnyObject
            guard obj.responds(to: sel), let r = engine.registrar(forPlugin: key) else { continue }
            _ = obj.perform(sel, with: r)
            if key == "FLTFirebaseCorePlugin" { core = true }
        }
        if !core {
            // 이름이 바뀐 판이면 앱 전체 목록으로(이 플러그인은 위에서 이미 붙였지만 두 번 붙어도 통로만 다시 잡음)
            let all = NSSelectorFromString("registerWithRegistry:")
            if let reg = NSClassFromString("GeneratedPluginRegistrant") as AnyObject?, reg.responds(to: all) {
                _ = reg.perform(all, with: engine)
            }
        }
    }
}

/// 앱(Runner)의 App Intent가 부르는 곳: 위젯 체크칸 · 바로 적기 · 되돌리기 · 뒤에서 확인
public enum OneoneQWidget {
    /// 뒤에서 Dart 한 번(메인 스레드로 넘겨서) · 앱 함수가 돌려준 글자(JSON) 또는 "true"/"false" · 못 하면 nil
    static func run(_ action: String, _ id: String) async -> String? {
        await withCheckedContinuation { (cont: CheckedContinuation<String?, Never>) in
            DispatchQueue.main.async {
                Q11BgRunner.shared.run(action, id) { cont.resume(returning: $0) }
            }
        }
    }

    /// 위젯 체크칸(아이폰 · LiveActivityIntent가 앱 안에서 부름): 끝냄 ↔ 되돌림(갤럭시 handleItem과 같음)
    public static func toggle(taskId: String) async {
        let id = taskId.trimmingCharacters(in: .whitespaces)
        if id.isEmpty { return }
        let was = Q11Store.isPendingDone(id)
        Q11Store.setPending(id, done: was ? nil : true)
        DispatchQueue.main.async { OneoneQWidgetPlugin.reload() }
        _ = await run(was ? "undo" : "done", id)
    }

    /// 바로 적기(App Shortcut · 액션 버튼 · Siri): 서버 /pc/send(앱 코드) → 결과는 알림(되돌리기 · 앱에서 보기)
    public static func quickAdd(text: String) async {
        let lang = Q11Store.lang()
        await Q11Notify.cleanup()
        let t = text.trimmingCharacters(in: .whitespacesAndNewlines)
        if t.isEmpty {
            await Q11Notify.post(Q11Notify.text(lang, "empty"))
            return
        }
        if !Q11BgRunner.canRun {
            await Q11Notify.post(Q11Notify.text(lang, "openOnce"))
            return
        }
        let raw = await run("add", t)
        await Q11Notify.added(raw, lang: lang)
    }

    /// 바로 적기 알림의 `되돌리기`
    static func undoAdd(ids: String, title: String) async {
        let lang = Q11Store.lang()
        let raw = await run("undoAdd", ids)
        var ok = false
        if let r = raw, let d = r.data(using: .utf8),
           let o = (try? JSONSerialization.jsonObject(with: d)) as? [String: Any] {
            ok = (o["ok"] as? Bool) ?? false
        }
        if ok {
            await Q11Notify.post(title.isEmpty ? Q11Notify.text(lang, "undone") : Q11Notify.text(lang, "undone") + " · " + title)
        } else {
            await Q11Notify.post(Q11Notify.text(lang, "undoFail"))
        }
    }

    /// 뒤에서 확인(BGAppRefresh · 갤럭시 15분 확인과 같은 앱 함수)
    static func refresh() async -> Bool {
        (await run("refresh", "")) != nil
    }
}
