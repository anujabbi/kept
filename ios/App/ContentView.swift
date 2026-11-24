// THROWAWAY SPIKE (issue #186). One screen: permission, allowed apps, lock, done, log.
import DeviceActivity
import FamilyControls
import SwiftUI
import UserNotifications

struct ContentView: View {
    @State private var selection = Lock.loadSelection()
    @State private var showPicker = false
    @State private var status = "unknown"
    @State private var log = SpikeLog.read()

    private let ticker = Timer.publish(every: 2, on: .main, in: .common).autoconnect()

    var body: some View {
        NavigationStack {
            List {
                Section("Setup") {
                    LabeledContent("Screen Time access", value: status)
                    Button("1. Grant Screen Time access") { Task { await requestAccess() } }
                    Button("2. Allow notifications") { requestNotifications() }
                    Button("3. Pick apps that stay open (\(selection.applicationTokens.count))") {
                        showPicker = true
                    }
                }
                Section("Lock") {
                    Button("Lock now") {
                        Lock.apply("app")
                        refresh()
                    }
                    Button("Lock in 2 minutes (then close the app)") { scheduleLock() }
                }
                Section("Today's habit") {
                    Button("Read 10 pages: Done") { habitDone() }
                        .font(.headline)
                }
                Section("Log") {
                    Text(log)
                        .font(.system(.caption, design: .monospaced))
                        .textSelection(.enabled)
                    Button("Clear log", role: .destructive) {
                        SpikeLog.clear()
                        refresh()
                    }
                }
            }
            .navigationTitle("Lock spike")
        }
        .familyActivityPicker(isPresented: $showPicker, selection: $selection)
        .onChange(of: showPicker) { _, showing in
            if !showing {
                Lock.saveSelection(selection)
                SpikeLog.write("app", "allowed apps saved: \(selection.applicationTokens.count)")
                refresh()
            }
        }
        .onReceive(ticker) { _ in refresh() }
        .onAppear {
            SpikeLog.write("app", "opened")
            refresh()
        }
    }

    private func refresh() {
        status = String(describing: AuthorizationCenter.shared.authorizationStatus)
        log = SpikeLog.read()
    }

    private func requestAccess() async {
        do {
            try await AuthorizationCenter.shared.requestAuthorization(for: .individual)
            SpikeLog.write("app", "Screen Time access granted")
        } catch {
            SpikeLog.write("app", "Screen Time access FAILED: \(error)")
        }
        refresh()
    }

    private func requestNotifications() {
        UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .sound]) { granted, error in
            SpikeLog.write("app", "notifications granted=\(granted) error=\(String(describing: error))")
        }
    }

    private func scheduleLock() {
        let start = Date().addingTimeInterval(120)
        let end = start.addingTimeInterval(60 * 60)
        let parts: Set<Calendar.Component> = [.year, .month, .day, .hour, .minute, .second]
        let calendar = Calendar.current
        let schedule = DeviceActivitySchedule(
            intervalStart: calendar.dateComponents(parts, from: start),
            intervalEnd: calendar.dateComponents(parts, from: end),
            repeats: false
        )
        let center = DeviceActivityCenter()
        center.stopMonitoring([Shared.activityName])
        do {
            try center.startMonitoring(Shared.activityName, during: schedule)
            SpikeLog.write("app", "lock scheduled for \(start.formatted(date: .omitted, time: .standard)), 60 min")
        } catch {
            SpikeLog.write("app", "schedule FAILED: \(error)")
        }
        refresh()
    }

    private func habitDone() {
        DeviceActivityCenter().stopMonitoring([Shared.activityName])
        Lock.clear("app")
        SpikeLog.write("app", "habit done")
        refresh()
    }
}
