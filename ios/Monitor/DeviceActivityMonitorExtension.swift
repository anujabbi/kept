// THROWAWAY SPIKE (issue #186). Runs with the app closed: turns the shield on and off on schedule.
import DeviceActivity
import Foundation

class DeviceActivityMonitorExtension: DeviceActivityMonitor {
    override func intervalDidStart(for activity: DeviceActivityName) {
        super.intervalDidStart(for: activity)
        SpikeLog.write("monitor", "intervalDidStart")
        Lock.apply("monitor")
    }

    override func intervalDidEnd(for activity: DeviceActivityName) {
        super.intervalDidEnd(for: activity)
        SpikeLog.write("monitor", "intervalDidEnd")
        Lock.clear("monitor")
    }
}
