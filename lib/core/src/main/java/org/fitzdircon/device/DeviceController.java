package org.fitzdircon.device;

import org.fitzdircon.command.Command;
import org.fitzdircon.telemetry.Telemetry;
import org.fitzdircon.telemetry.TelemetryHub;

import java.io.IOException;

public class DeviceController {

    private static final String LOG_TAG = DeviceLogTags.DISPATCH;

    private final Device device;
    private final TelemetryHub.Subscription telemetrySubscription;

    public DeviceController(Device device) {
        this.device = device;
        this.telemetrySubscription = subscribeTelemetry();
    }

    public void enqueueCommand(Command cmd) {
        device.logger.log(Device.Logger.DEBUG, LOG_TAG, "command: " + cmd);
        device.applyCommand(cmd);
    }

    public void onTelemetry(Telemetry telemetry) {
        String label = device.telemetryLabel(telemetry);
        if (label != null) device.logger.log(Device.Logger.DEBUG, LOG_TAG, "telemetry: " + label);
        device.applyTelemetry(telemetry);
    }

    public void shutdown() {
        if (telemetrySubscription != null) telemetrySubscription.close();
        device.shutdown();
    }

    public Device device() { return device; }

    private TelemetryHub.Subscription subscribeTelemetry() {
        try {
            return TelemetryHub.shared().subscribe(this::onTelemetry);
        } catch (IOException e) {
            device.logger.log(Device.Logger.ERROR, LOG_TAG,
                    "telemetry subscription failed: " + e.getMessage());
            return null;
        }
    }
}
