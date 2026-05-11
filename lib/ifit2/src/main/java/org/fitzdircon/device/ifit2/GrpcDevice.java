package org.fitzdircon.device.ifit2;

import org.fitzdircon.command.Command;
import org.fitzdircon.console.ifit2.CommandTransport;
import org.fitzdircon.device.Device;
import org.fitzdircon.device.DeviceLogTags;

public abstract class GrpcDevice extends Device {

    private static final String LOG_TAG = DeviceLogTags.DISPATCH;

    protected final CommandTransport transport;

    protected GrpcDevice(CommandTransport transport) { this.transport = transport; }

    @Override
    public void applyCommand(Command cmd) {
        if (!transport.apply(cmd, logger)) logger.log(Logger.WARN, LOG_TAG, "command rejected: " + cmd);
    }

    @Override public void shutdown() { transport.shutdown(); }
}
