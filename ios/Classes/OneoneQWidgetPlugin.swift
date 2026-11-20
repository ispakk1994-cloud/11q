import BackgroundTasks
import Flutter
import UIKit
import UserNotifications
#if canImport(WidgetKit)
import WidgetKit
#endif

/// 11Q 위젯 플러그인(아이폰 · 0.6.0): 앱(Dart)과 홈 화면 위젯 사이의 통로(안드로이드와 같은 통로 이름 · 같은 함수 이름).
/// - 목록(JSON)은 앱 그룹 저장소(group.com.sj1029.QUEST11)에 적고 위젯을 다시 그리게 한다. 위젯 확장(TodayWidget)이 같은 곳을 읽는다.
///   (앱 그룹이 아직 서명에 없으면 앱 안에만 적혀 위젯은 `11Q를 한 번 열면…`으로 보임)
/// - 위젯 ＋ = 주소 oneoneq://widget/add 로 앱이 열림 → 여기서 받아 takeAction · 'action'으로 넘긴다(Flutter 길 찾기로는 안 감).
/// - 0.6.0: 위젯 체크칸 · 바로 적기 · 되돌리기 · 뒤에서 확인 = 화면 없는 엔진으로 앱 코드(q11WidgetBg)를 돌림(Q11Bg) ·
///   setHandles로 함수 번호를 받아 둠 · 바로 적기 결과는 알림(Q11Notify) · 뒤에서 확인은 BGAppRefresh(Info.plist에 이름이 있을 때만).
/// - 아이폰은 앱에서 위젯을 바로 놓을 수 없고(pinSupported false) · 글자 크기는 위젯을 꾹 눌러 `위젯 편집`에서 고른다.
/// - 비밀 값(키 · 토큰 · 서버 주소)은 넣지 않는다.
public class OneoneQWidgetPlugin: NSObject, FlutterPlugin {
    static let kind = "Q11Today"
    static let refreshTask = "day.q11.widget.refresh"

    private var channel: FlutterMethodChannel?
    /// ＋로 열렸을 때 'add'. 앱이 takeAction으로 한 번 가져가면 지운다.
    private static var pendingAction: String?
    private static var refreshRegistered = false

    public static func register(with registrar: FlutterPluginRegistrar) {
        let channel = FlutterMethodChannel(name: "oneone_q_widget", binaryMessenger: registrar.messenger())
        let instance = OneoneQWidgetPlugin()
        instance.channel = channel
        registrar.addMethodCallDelegate(instance, channel: channel)
        // 화면 없는 엔진에 다시 붙일 때는 앱 대리자(주소 · 알림 · 뒤에서 확인)를 빼고 통로만
        if !Q11Plugins.registeringBackground {
            registrar.addApplicationDelegate(instance)
        }
    }

    public func handle(_ call: FlutterMethodCall, result: @escaping FlutterResult) {
        let args = call.arguments as? [String: Any]
        switch call.method {
        case "ping":
            result("ok")
        case "count":
            Self.widgetCount { result($0) }
        case "refresh":
            Self.reload()
            Self.widgetCount { result($0) }
        case "requestPin", "pinSupported":
            result(false)
        case "sizeLevel", "setSize":
            // 아이폰: 위젯마다 `위젯 편집`에서 고름(앱에서 바꾸지 않음) · 마지막으로 그린 값만 알려 줌
            result(Self.sizeLevel())
        case "save":
            let json = (args?["json"] as? String) ?? ""
            if !json.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                Q11Store.saveFeed(json)
            }
            Self.reload()
            result(true)
        case "clear":
            Q11Store.clearFeed(args?["json"] as? String)
            Self.reload()
            result(true)
        case "lastFeed":
            result(Q11Store.feedRaw())
        case "setHandles":
            let d = (args?["dispatcher"] as? NSNumber)?.int64Value ?? 0
            let c = (args?["callback"] as? NSNumber)?.int64Value ?? 0
            if d != 0 && c != 0 {
                Q11Store.saveHandles(d, c)
                Self.scheduleRefresh()
            }
            result(d != 0 && c != 0)
        case "takeAction":
            let a = Self.pendingAction
            Self.pendingAction = nil
            result(a)
        default:
            result(FlutterMethodNotImplemented)
        }
    }

    static func reload() {
        #if canImport(WidgetKit)
        if #available(iOS 14.0, *) {
            WidgetCenter.shared.reloadAllTimelines()
        }
        #endif
    }

    /// 홈 화면 · 잠금 화면에 놓인 11Q 위젯 수(오늘 할 일 · ＋)
    static func widgetCount(_ done: @escaping (Int) -> Void) {
        #if canImport(WidgetKit)
        if #available(iOS 14.0, *) {
            WidgetCenter.shared.getCurrentConfigurations { res in
                var n = 0
                if case .success(let list) = res {
                    n = list.filter { $0.kind == kind }.count
                }
                DispatchQueue.main.async { done(n) }
            }
            return
        }
        #endif
        done(0)
    }

    static func sizeLevel() -> Int {
        let v = (Q11Store.group.object(forKey: Q11Store.keySize) as? NSNumber)?.intValue ?? 1
        return min(max(v, 0), 2)
    }

    // ── 뒤에서 확인(BGAppRefresh · Info.plist BGTaskSchedulerPermittedIdentifiers에 이름이 있을 때만) ──

    private static var refreshAllowed: Bool {
        let ids = Bundle.main.object(forInfoDictionaryKey: "BGTaskSchedulerPermittedIdentifiers") as? [String] ?? []
        return ids.contains(refreshTask)
    }

    private static func registerRefresh() {
        guard refreshAllowed, !refreshRegistered else { return }
        refreshRegistered = BGTaskScheduler.shared.register(forTaskWithIdentifier: refreshTask, using: nil) { task in
            guard let t = task as? BGAppRefreshTask else {
                task.setTaskCompleted(success: false)
                return
            }
            OneoneQWidgetPlugin.scheduleRefresh()
            let work = Task {
                let ok = await OneoneQWidget.refresh()
                t.setTaskCompleted(success: ok)
            }
            t.expirationHandler = { work.cancel() }
        }
    }

    /// 다음 확인을 15분 뒤로 부탁(언제 할지는 아이폰이 정함 · 앱을 한 번 열어 함수 번호가 있을 때만)
    static func scheduleRefresh() {
        guard refreshRegistered, Q11BgRunner.canRun else { return }
        let req = BGAppRefreshTaskRequest(identifier: refreshTask)
        req.earliestBeginDate = Date(timeIntervalSinceNow: 15 * 60)
        do {
            try BGTaskScheduler.shared.submit(req)
        } catch {
            NSLog("[11Q] 뒤에서 확인 예약 실패: \(error.localizedDescription)")
        }
    }

    // ── 앱 대리자: 위젯 ＋ 주소 · 바로 적기 알림 · 뒤에서 확인 ─────────────────────────

    /// oneoneq://widget/<할 일> 이면 받는다(그 밖의 oneoneq:// 주소는 앱 길 찾기로 그대로).
    private static func take(_ url: URL) -> Bool {
        guard url.scheme?.lowercased() == "oneoneq", url.host?.lowercased() == "widget" else { return false }
        let a = url.path.trimmingCharacters(in: CharacterSet(charactersIn: "/"))
        if !a.isEmpty { pendingAction = a }
        return true
    }

    public func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [AnyHashable: Any] = [:]
    ) -> Bool {
        Self.registerRefresh()
        let u = launchOptions[UIApplication.LaunchOptionsKey.url] ?? launchOptions[UIApplication.LaunchOptionsKey.url.rawValue]
        if let url = u as? URL {
            _ = Self.take(url)
        }
        return true
    }

    public func application(
        _ application: UIApplication,
        open url: URL,
        options: [UIApplication.OpenURLOptionsKey: Any] = [:]
    ) -> Bool {
        guard Self.take(url) else { return false }
        if let a = Self.pendingAction {
            // 앱이 켜져 있으면 바로 · 막 켜지는 중이면 Flutter가 이 알림을 잠시 들고 있다가 WidgetCatch에 넘김
            channel?.invokeMethod("action", arguments: a)
        }
        return true
    }

    public func applicationDidEnterBackground(_ application: UIApplication) {
        Self.scheduleRefresh()
    }

    public func applicationDidBecomeActive(_ application: UIApplication) {
        Task { await Q11Notify.cleanup() }
    }

    /// 앱이 앞에 있을 때도 바로 적기 결과 알림을 띄움(우리 알림만 · 다른 알림은 그 플러그인이)
    public func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        willPresent notification: UNNotification,
        withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void
    ) {
        guard (notification.request.content.userInfo["q11"] as? String) == "quick" else { return }
        if #available(iOS 14.0, *) {
            completionHandler([.banner, .list])
        } else {
            completionHandler([.alert])
        }
    }

    /// 바로 적기 알림의 `되돌리기` · `앱에서 보기`(우리 알림만 · 다른 알림은 그 플러그인이)
    public func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        didReceive response: UNNotificationResponse,
        withCompletionHandler completionHandler: @escaping () -> Void
    ) {
        _ = Q11Notify.handle(response, done: completionHandler)
    }
}
