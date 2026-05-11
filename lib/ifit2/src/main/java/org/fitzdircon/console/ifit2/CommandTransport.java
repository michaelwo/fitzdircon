package org.fitzdircon.console.ifit2;

import org.fitzdircon.command.Command;
import org.fitzdircon.device.Device;

public interface CommandTransport {
    boolean apply(Command command, Device.Logger logger);
    default void shutdown() {}
}
