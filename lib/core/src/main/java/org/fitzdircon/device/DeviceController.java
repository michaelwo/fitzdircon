package org.fitzdircon.device;

import org.fitzdircon.command.Command;
import org.fitzdircon.command.CommandDispatcher;
import org.fitzdircon.telemetry.Telemetry;
import org.fitzdircon.telemetry.TelemetryHub;

import java.io.IOException;

public class DeviceController {

    private static final String LOG_TAG = DeviceLogTags.DISPATCH;

    private final Device device;
    private final CommandDispatcher dispatcher;
    private final TelemetryHub.Subscription telemetrySubscription;

    public DeviceController(Device device) {
        this.device      = device;
        this.dispatcher  = new CommandDispatcher(this::executeCommand);
        this.telemetrySubscription = subscribeTelemetry();
    }

    /** Test constructor: injectable clock, no background drain thread. */
    public DeviceController(Device device, CommandDispatcher.Clock clock) {
        this.device      = device;
        this.dispatcher  = new CommandDispatcher(this::executeCommand, clock);
        this.telemetrySubscription = null;
    }

    private void executeCommand(Command cmd) {
        device.logger.log(Device.Logger.DEBUG, LOG_TAG, "drain: " + cmd);
        device.applyCommand(cmd);
    }

    public int enqueueCommand(Command cmd) {
        int depth = dispatcher.enqueue(cmd);
        if (depth >= 0)
            device.logger.log(Device.Logger.DEBUG, LOG_TAG,
                    "enqueue: " + cmd + " depth=" + depth + "/" + CommandDispatcher.QUEUE_CAPACITY);
        else
            device.logger.log(Device.Logger.WARN, LOG_TAG,
                    "drop: " + cmd + " (queue full at " + CommandDispatcher.QUEUE_CAPACITY + ")");
        return depth;
    }

    public void onTelemetry(Telemetry telemetry) {
        String label = device.telemetryLabel(telemetry);
        if (label != null) device.logger.log(Device.Logger.DEBUG, LOG_TAG, "telemetry: " + label);
        device.applyTelemetry(telemetry);
    }

    public void shutdown() {
        if (telemetrySubscription != null) telemetrySubscription.close();
        device.shutdown();
        dispatcher.shutdown();
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
