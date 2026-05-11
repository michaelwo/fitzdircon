package org.fitzdircon.device;

import org.fitzdircon.command.Command;

public class DeviceController {

    private static final String LOG_TAG = DeviceLogTags.DISPATCH;

    private final Device device;

    public DeviceController(Device device) {
        this.device = device;
    }

    public void enqueueCommand(Command cmd) {
        device.logger.log(Device.Logger.DEBUG, LOG_TAG, "command: " + cmd);
        device.applyCommand(cmd);
    }

    public void shutdown() {
        device.shutdown();
    }

    public Device device() { return device; }
}
