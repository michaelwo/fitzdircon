package org.fitzdircon.telemetry;

public abstract class Telemetry {
    public final float value;

    protected Telemetry(float value) {
        this.value = value;
    }
}
