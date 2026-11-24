// THROWAWAY SPIKE (issue #186). The lock screen's buttons. An extension cannot open the app,
// so "Tick it off" posts a notification the user taps to get there.
import Foundation
import ManagedSettings
import UserNotifications

class ShieldActionExtension: ShieldActionDelegate {
    private func respond(_ action: ShieldAction, _ completionHandler: @escaping (ShieldActionResponse) -> Void) {
        switch action {
        case .primaryButtonPressed:
            SpikeLog.write("shield-action", "primary pressed")
            let content = UNMutableNotificationContent()
            content.title = "Tick off today's habit"
            content.body = "Tap to open and mark it done."
            content.sound = .default
            let request = UNNotificationRequest(
                identifier: "kept.spike.tick",
                content: content,
                trigger: UNTimeIntervalNotificationTrigger(timeInterval: 1, repeats: false)
            )
            UNUserNotificationCenter.current().add(request) { error in
                SpikeLog.write("shield-action", "notification error=\(String(describing: error))")
                completionHandler(.close)
            }
        case .secondaryButtonPressed:
            SpikeLog.write("shield-action", "secondary pressed")
            completionHandler(.close)
        @unknown default:
            completionHandler(.close)
        }
    }

    override func handle(action: ShieldAction, for application: ApplicationToken, completionHandler: @escaping (ShieldActionResponse) -> Void) {
        respond(action, completionHandler)
    }

    override func handle(action: ShieldAction, for webDomain: WebDomainToken, completionHandler: @escaping (ShieldActionResponse) -> Void) {
        respond(action, completionHandler)
    }

    override func handle(action: ShieldAction, for category: ActivityCategoryToken, completionHandler: @escaping (ShieldActionResponse) -> Void) {
        respond(action, completionHandler)
    }
}
