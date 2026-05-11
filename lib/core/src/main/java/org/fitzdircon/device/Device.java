package org.fitzdircon.device;

import org.fitzdircon.command.Command;
import org.fitzdircon.telemetry.Telemetry;

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

    /** Returns a log label for confirmed telemetry this device handles, or null to suppress logging. */
    public String telemetryLabel(Telemetry t) { return null; }

    /** Handles a live telemetry update. */
    public void applyTelemetry(Telemetry telemetry) {}

    /** Applies a command to this device via gRPC. */
    public void applyCommand(Command cmd) {}

    /** Releases any resources held by this device (e.g. gRPC channel). */
    public void shutdown() {}
}
