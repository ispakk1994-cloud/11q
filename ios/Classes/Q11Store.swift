import Foundation

/// 위젯이 보여 줄 것(앱이 적어 둔 목록)과 위젯에서 막 체크한 일(앱 코드가 끝내기 전)을 앱 그룹에 둔다.
/// 위젯 확장(TodayWidget)이 같은 열쇠를 읽는다. 앱 함수 번호(뒤에서 Dart를 돌릴 때)는 앱에만.
enum Q11Store {
    static let appGroup = "group.com.sj1029.QUEST11"
    static let keyFeed = "q11.feed"
    static let keySize = "q11.sizeLevel"
    static let keyPending = "q11.pending"
    static let keyDispatcher = "q11.bg.dispatcher"
    static let keyCallback = "q11.bg.callback"
    /// 체크한 뒤 이만큼 지나도 목록이 안 바뀌면 체크 표시를 푼다(서버에 못 간 경우 · 갤럭시와 같음)
    static let pendingTTL: TimeInterval = 180

    static var group: UserDefaults { UserDefaults(suiteName: appGroup) ?? .standard }

    // ── 목록 ─────────────────────────────

    static func feedRaw() -> String? { group.string(forKey: keyFeed) }

    static func saveFeed(_ json: String) {
        group.set(json, forKey: keyFeed)
        prunePending(keep: rowIds(json))
    }

    static func clearFeed(_ json: String?) {
        group.removeObject(forKey: keyPending)
        if let j = json, !j.isEmpty {
            group.set(j, forKey: keyFeed)
        } else {
            group.removeObject(forKey: keyFeed)
        }
    }

    private static func feedObject() -> [String: Any]? {
        guard let s = feedRaw(), let d = s.data(using: .utf8) else { return nil }
        return (try? JSONSerialization.jsonObject(with: d)) as? [String: Any]
    }

    /// 목록의 언어(없으면 폰 언어)
    static func lang() -> String {
        if let l = feedObject()?["lang"] as? String { return l == "en" ? "en" : "ko" }
        return (Locale.preferredLanguages.first ?? "ko").hasPrefix("ko") ? "ko" : "en"
    }

    private static func rowIds(_ json: String) -> Set<String> {
        guard let d = json.data(using: .utf8),
              let o = (try? JSONSerialization.jsonObject(with: d)) as? [String: Any],
              let rows = o["rows"] as? [Any]
        else { return [] }
        var out = Set<String>()
        for r in rows {
            if let m = r as? [String: Any], let id = m["id"] as? String, !id.isEmpty { out.insert(id) }
        }
        return out
    }

    // ── 위젯에서 막 체크한 일 ─────────────────────────────

    private static func pendingAll() -> [String: [String: Any]] {
        guard let s = group.string(forKey: keyPending), let d = s.data(using: .utf8),
              let o = (try? JSONSerialization.jsonObject(with: d)) as? [String: Any]
        else { return [:] }
        var out: [String: [String: Any]] = [:]
        for (k, v) in o {
            if let m = v as? [String: Any] { out[k] = m }
        }
        return out
    }

    private static func savePending(_ all: [String: [String: Any]]) {
        if let d = try? JSONSerialization.data(withJSONObject: all), let s = String(data: d, encoding: .utf8) {
            group.set(s, forKey: keyPending)
        }
    }

    private static func nowMs() -> Double { Date().timeIntervalSince1970 * 1000 }

    /// 위젯에서 체크해 '끝냄'으로 보이는 중인지
    static func isPendingDone(_ id: String) -> Bool {
        guard let o = pendingAll()[id] else { return false }
        let at = (o["at"] as? NSNumber)?.doubleValue ?? 0
        if nowMs() - at > pendingTTL * 1000 { return false }
        return (o["done"] as? Bool) ?? false
    }

    static func setPending(_ id: String, done: Bool?) {
        var all = pendingAll()
        if let d = done {
            all[id] = ["done": d, "at": nowMs()]
        } else {
            all.removeValue(forKey: id)
        }
        savePending(all)
    }

    /// 새 목록에 없는 일(끝낸 일)과 오래된 표시는 지운다
    private static func prunePending(keep ids: Set<String>) {
        let now = nowMs()
        var keep: [String: [String: Any]] = [:]
        for (k, o) in pendingAll() {
            let at = (o["at"] as? NSNumber)?.doubleValue ?? 0
            if now - at > pendingTTL * 1000 { continue }
            if !ids.contains(k) { continue }
            keep[k] = o
        }
        savePending(keep)
    }

    // ── 뒤에서 돌 앱 함수 번호(앱에만) ─────────────────────────────

    static func handles() -> (Int64, Int64) {
        let d = UserDefaults.standard
        let a = (d.object(forKey: keyDispatcher) as? NSNumber)?.int64Value ?? 0
        let b = (d.object(forKey: keyCallback) as? NSNumber)?.int64Value ?? 0
        return (a, b)
    }

    static func saveHandles(_ dispatcher: Int64, _ callback: Int64) {
        let d = UserDefaults.standard
        d.set(NSNumber(value: dispatcher), forKey: keyDispatcher)
        d.set(NSNumber(value: callback), forKey: keyCallback)
    }
}
