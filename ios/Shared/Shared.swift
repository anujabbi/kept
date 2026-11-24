// THROWAWAY SPIKE (issue #186). Compiled into the app and all three extensions.
import DeviceActivity
import FamilyControls
import Foundation
import ManagedSettings

enum Shared {
    static let groupID = "group.com.zenai.kept.ios"
    static let selectionKey = "allowedSelection"
    static let storeName = ManagedSettingsStore.Name("kept.spike")
    static let activityName = DeviceActivityName("kept.spike.lock")

    static var defaults: UserDefaults? { UserDefaults(suiteName: groupID) }
    static var container: URL? {
        FileManager.default.containerURL(forSecurityApplicationGroupIdentifier: groupID)
    }
}

/// Stands in for device logs, which we cannot read without a Mac: every process appends
/// to one file in the App Group container and the app shows it on screen.
enum SpikeLog {
    private static var url: URL? { Shared.container?.appendingPathComponent("spike.log") }

    static func write(_ who: String, _ message: String) {
        guard let url else { return }
        let formatter = DateFormatter()
        formatter.dateFormat = "HH:mm:ss"
        let line = "\(formatter.string(from: Date())) [\(who)] \(message)\n"
        guard let data = line.data(using: .utf8) else { return }
        if let handle = try? FileHandle(forWritingTo: url) {
            defer { try? handle.close() }
            _ = try? handle.seekToEnd()
            try? handle.write(contentsOf: data)
        } else {
            try? data.write(to: url, options: .atomic)
        }
    }

    static func read() -> String {
        guard let url else { return "(no App Group container: the app group is not provisioned)" }
        return (try? String(contentsOf: url, encoding: .utf8)) ?? "(log is empty)"
    }

    static func clear() {
        guard let url else { return }
        try? FileManager.default.removeItem(at: url)
    }
}

enum Lock {
    static func loadSelection() -> FamilyActivitySelection {
        guard let data = Shared.defaults?.data(forKey: Shared.selectionKey),
              let selection = try? PropertyListDecoder().decode(FamilyActivitySelection.self, from: data)
        else { return FamilyActivitySelection() }
        return selection
    }

    static func saveSelection(_ selection: FamilyActivitySelection) {
        guard let data = try? PropertyListEncoder().encode(selection) else { return }
        Shared.defaults?.set(data, forKey: Shared.selectionKey)
    }

    /// Shield every app and website except the ones the user picked as allowed.
    static func apply(_ who: String) {
        let selection = loadSelection()
        let store = ManagedSettingsStore(named: Shared.storeName)
        store.shield.applicationCategories = .all(except: selection.applicationTokens)
        store.shield.webDomainCategories = .all(except: selection.webDomainTokens)
        SpikeLog.write(who, "shield ON, \(selection.applicationTokens.count) apps allowed")
    }

    static func clear(_ who: String) {
        ManagedSettingsStore(named: Shared.storeName).clearAllSettings()
        SpikeLog.write(who, "shield OFF")
    }
}
