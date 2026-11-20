import Foundation
import UserNotifications

/// 바로 적기 결과 알림(시안 QuickIphone ④ `11Q · 할 일에 넣었어요` · 되돌리기 · 앱에서 보기)
/// 버튼은 알림을 꾹 누르면(아이폰 알림 방식) · 알림을 누르면 앱이 열림. 지난 결과 알림은 다음에 적을 때 · 앱을 열 때 지움.
enum Q11Notify {
    static let idPrefix = "q11.quick."
    static let actionUndo = "q11.undo"
    static let actionOpen = "q11.open"

    static func category(_ lang: String) -> String { "Q11_QUICK_\(lang == "en" ? "EN" : "KO")" }

    private static let ko: [String: String] = [
        "added": "11Q · 할 일에 넣었어요",
        "undo": "되돌리기",
        "open": "앱에서 보기",
        "undone": "되돌렸어요",
        "undoFail": "지우지 못했어요 · 앱에서 지워 주세요",
        "signin": "11Q에 로그인하면 바로 적을 수 있어요",
        "openOnce": "11Q를 한 번 열면 바로 적을 수 있어요",
        "limit": "오늘 AI 횟수를 다 썼어요 · 앱에서 직접 적을 수 있어요",
        "dup": "이미 있는 할 일이에요",
        "none": "할 일을 못 찾았어요 · 조금 더 자세히 적어 주세요",
        "question": "질문은 앱 대화에서 물어봐 주세요",
        "empty": "할 일을 적어 주세요",
        "fail": "보내지 못했어요 · 잠시 뒤 다시 해 주세요",
    ]

    private static let en: [String: String] = [
        "added": "11Q · Added to your tasks",
        "undo": "Undo",
        "open": "Open in app",
        "undone": "Undone",
        "undoFail": "Couldn't undo · please delete it in the app",
        "signin": "Sign in to 11Q to add tasks",
        "openOnce": "Open 11Q once to add tasks from here",
        "limit": "You've used today's AI · you can still add it in the app",
        "dup": "Already on your list",
        "none": "Couldn't find a task · try adding a little more",
        "question": "Ask questions in the app's Chat",
        "empty": "Type a task first",
        "fail": "Couldn't send · please try again in a moment",
    ]

    static func text(_ lang: String, _ key: String) -> String {
        (lang == "en" ? en : ko)[key] ?? key
    }

    private static func ensureCategory(_ lang: String) async {
        let c = UNUserNotificationCenter.current()
        let id = category(lang)
        let cats = await c.notificationCategories()
        if cats.contains(where: { $0.identifier == id }) { return }
        let undo = UNNotificationAction(identifier: actionUndo, title: text(lang, "undo"), options: [])
        let open = UNNotificationAction(identifier: actionOpen, title: text(lang, "open"), options: [.foreground])
        let cat = UNNotificationCategory(identifier: id, actions: [undo, open], intentIdentifiers: [], options: [])
        c.setNotificationCategories(cats.union([cat]))
    }

    /// 지난 바로 적기 알림 지우기(알림 센터에 쌓이지 않게)
    static func cleanup() async {
        let c = UNUserNotificationCenter.current()
        let list = await c.deliveredNotifications()
        let ids = list.map { $0.request.identifier }.filter { $0.hasPrefix(idPrefix) }
        if !ids.isEmpty { c.removeDeliveredNotifications(withIdentifiers: ids) }
    }

    static func post(_ body: String, title: String = "11Q", category: String? = nil, info: [String: Any] = [:]) async {
        let content = UNMutableNotificationContent()
        content.title = title
        content.body = body
        if let cat = category { content.categoryIdentifier = cat }
        var u = info
        u["q11"] = "quick"
        content.userInfo = u
        if #available(iOS 15.0, *) { content.interruptionLevel = .active }
        let id = idPrefix + String(Int(Date().timeIntervalSince1970 * 1000))
        let req = UNNotificationRequest(identifier: id, content: content, trigger: nil)
        do {
            try await UNUserNotificationCenter.current().add(req)
        } catch {
            NSLog("[11Q] 바로 적기 알림 실패: \(error.localizedDescription)")
        }
    }

    /// 앱 함수(_q11Add)가 돌려준 결과(JSON)로 알림
    static func added(_ raw: String?, lang: String) async {
        var o: [String: Any] = [:]
        if let r = raw, let d = r.data(using: .utf8),
           let m = (try? JSONSerialization.jsonObject(with: d)) as? [String: Any] {
            o = m
        }
        let items = (o["items"] as? [Any])?.compactMap { $0 as? [String: Any] } ?? []
        if (o["ok"] as? Bool) == true, let first = items.first {
            let ids = items.compactMap { $0["id"] as? String }.filter { !$0.isEmpty }
            let title = (first["title"] as? String) ?? ""
            let label = (first["label"] as? String) ?? ""
            var body = [title, label].filter { !$0.isEmpty }.joined(separator: " · ")
            let more = items.count - 1
            if more > 0 { body += lang == "en" ? " +\(more) more" : " 외 \(more)개" }
            await ensureCategory(lang)
            await post(
                body,
                title: text(lang, "added"),
                category: category(lang),
                info: ["ids": ids.joined(separator: ","), "title": title]
            )
            return
        }
        let kind = (o["kind"] as? String) ?? ""
        switch kind {
        case "auth":
            await post(text(lang, "signin"))
        case "limit":
            let m = ((o["message"] as? String) ?? "").trimmingCharacters(in: .whitespaces)
            await post(m.isEmpty ? text(lang, "limit") : m)
        case "dup":
            let dup = ((o["dup"] as? String) ?? "").trimmingCharacters(in: .whitespaces)
            await post(dup.isEmpty ? text(lang, "dup") : text(lang, "dup") + " · " + dup)
        case "none":
            await post(text(lang, "none"))
        case "question":
            await post(text(lang, "question"))
        case "empty":
            await post(text(lang, "empty"))
        default:
            await post(text(lang, "fail"))
        }
    }

    /// 알림의 `되돌리기`(앱 대리자가 넘김) · 우리 알림이면 true
    static func handle(_ response: UNNotificationResponse, done: @escaping () -> Void) -> Bool {
        let content = response.notification.request.content
        guard (content.userInfo["q11"] as? String) == "quick" else { return false }
        if response.actionIdentifier == actionUndo {
            let ids = (content.userInfo["ids"] as? String) ?? ""
            let title = (content.userInfo["title"] as? String) ?? ""
            Task {
                if !ids.isEmpty { await OneoneQWidget.undoAdd(ids: ids, title: title) }
                done()
            }
        } else {
            done()
        }
        return true
    }
}
