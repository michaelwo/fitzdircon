package org.fitzdircon.device;

import org.fitzdircon.command.Command;

public abstract class Device {
    /** Functional interface for log output. Level constants match android.util.Log values. */
    public interface Logger {
        int VERBOSE = 2;
        int DEBUG   = 3;
        int INFO    = 4;
        int WARN    = 5;
        int ERROR   = 6;
        void log(int level, String tag, String msg);
    }

    /** Logger for this device. No-op by default; set by MainActivity. */
    public Logger logger = (level, tag, msg) -> {};

    public abstract String displayName();

    /** Applies a command to this device via gRPC. */
    public void applyCommand(Command cmd) {}

    /** Releases any resources held by this device (e.g. gRPC channel). */
    public void shutdown() {}
}
