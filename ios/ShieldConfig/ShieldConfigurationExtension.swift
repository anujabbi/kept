// THROWAWAY SPIKE (issue #186). Everything we are allowed to change on Apple's lock screen.
import ManagedSettings
import ManagedSettingsUI
import UIKit

class ShieldConfigurationExtension: ShieldConfigurationDataSource {
    private func make(_ name: String?) -> ShieldConfiguration {
        SpikeLog.write("shield-ui", "shown for \(name ?? "an app")")
        let ink = UIColor(red: 0.13, green: 0.16, blue: 0.24, alpha: 1)
        return ShieldConfiguration(
            backgroundBlurStyle: .systemThinMaterial,
            backgroundColor: UIColor(red: 1.0, green: 0.93, blue: 0.80, alpha: 1),
            icon: UIImage(named: "sprig.png"),
            title: ShieldConfiguration.Label(text: "Habits first. Phone after.", color: ink),
            subtitle: ShieldConfiguration.Label(
                text: "\(name ?? "This app") opens when today's habit is done.",
                color: ink
            ),
            primaryButtonLabel: ShieldConfiguration.Label(text: "Tick it off", color: .white),
            primaryButtonBackgroundColor: UIColor(red: 0.93, green: 0.45, blue: 0.25, alpha: 1),
            secondaryButtonLabel: ShieldConfiguration.Label(text: "Not now", color: ink)
        )
    }

    override func configuration(shielding application: Application) -> ShieldConfiguration {
        make(application.localizedDisplayName)
    }

    override func configuration(shielding application: Application, in category: ActivityCategory) -> ShieldConfiguration {
        make(application.localizedDisplayName)
    }

    override func configuration(shielding webDomain: WebDomain) -> ShieldConfiguration {
        make(webDomain.domain)
    }

    override func configuration(shielding webDomain: WebDomain, in category: ActivityCategory) -> ShieldConfiguration {
        make(webDomain.domain)
    }
}
