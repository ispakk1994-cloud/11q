import Flutter
import UIKit
#if canImport(WidgetKit)
import WidgetKit
#endif

/// 11Q 위젯 플러그인(아이폰 · 0.5.0): 앱(Dart)과 홈 화면 위젯 사이의 통로(안드로이드와 같은 통로 이름 · 같은 함수 이름).
/// - 목록(JSON)은 앱 그룹 저장소(group.com.sj1029.QUEST11)에 적고 위젯을 다시 그리게 한다. 위젯 확장(TodayWidget)이 같은 곳을 읽는다.
///   (앱 그룹이 아직 서명에 없으면 앱 안에만 적혀 위젯은 `11Q를 한 번 열면…`으로 보임)
/// - 위젯 ＋ = 주소 oneoneq://widget/add 로 앱이 열림 → 여기서 받아 takeAction · 'action'으로 넘긴다(Flutter 길 찾기로는 안 감).
/// - 아이폰은 앱에서 위젯을 바로 놓을 수 없고(pinSupported false) · 글자 크기는 위젯을 꾹 눌러 `위젯 편집`에서 고른다.
/// - 비밀 값(키 · 토큰 · 서버 주소)은 넣지 않는다.
public class OneoneQWidgetPlugin: NSObject, FlutterPlugin {
    static let appGroup = "group.com.sj1029.QUEST11"
    static let kind = "Q11Today"
    static let keyFeed = "q11.feed"
    /// 위젯이 마지막으로 그린 글자 크기(0 보통 · 1 크게 · 2 아주 크게 · 위젯 확장이 적음)
    static let keySize = "q11.sizeLevel"

    private var channel: FlutterMethodChannel?
    /// ＋로 열렸을 때 'add'. 앱이 takeAction으로 한 번 가져가면 지운다.
    private static var pendingAction: String?

    public static func register(with registrar: FlutterPluginRegistrar) {
        let channel = FlutterMethodChannel(name: "oneone_q_widget", binaryMessenger: registrar.messenger())
        let instance = OneoneQWidgetPlugin()
        instance.channel = channel
        registrar.addMethodCallDelegate(instance, channel: channel)
        registrar.addApplicationDelegate(instance)
    }

    static var store: UserDefaults { UserDefaults(suiteName: appGroup) ?? .standard }

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
                Self.store.set(json, forKey: Self.keyFeed)
            }
            Self.reload()
            result(true)
        case "clear":
            if let json = args?["json"] as? String, !json.isEmpty {
                Self.store.set(json, forKey: Self.keyFeed)
            } else {
                Self.store.removeObject(forKey: Self.keyFeed)
            }
            Self.reload()
            result(true)
        case "lastFeed":
            result(Self.store.string(forKey: Self.keyFeed))
        case "setHandles":
            // 아이폰은 앱 화면 없이 Dart를 돌리지 않음(체크로 끝내기는 다음 판에서 따로)
            result(false)
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
            WidgetCenter.shared.reloadTimelines(ofKind: kind)
        }
        #endif
    }

    /// 홈 화면에 놓인 11Q 위젯 수
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
        let v = (store.object(forKey: keySize) as? NSNumber)?.intValue ?? 1
        return min(max(v, 0), 2)
    }

    // ── 위젯 ＋ 로 앱이 열린 것 알기 ─────────────────────────

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
}
