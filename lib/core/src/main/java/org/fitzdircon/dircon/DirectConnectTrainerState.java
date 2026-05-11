package org.fitzdircon.dircon;

import org.fitzdircon.telemetry.CadenceTelemetry;
import org.fitzdircon.telemetry.HeartRateTelemetry;
import org.fitzdircon.telemetry.InclineTelemetry;
import org.fitzdircon.telemetry.ResistanceTelemetry;
import org.fitzdircon.telemetry.SpeedTelemetry;
import org.fitzdircon.telemetry.Telemetry;
import org.fitzdircon.telemetry.WattsTelemetry;

public final class DirectConnectTrainerState {
    public Float watts;
    public Float cadenceRpm;
    public Float resistance;
    public Float gradePct;
    public Float speedKph;
    public Float heartRate;

    public void apply(Telemetry telemetry) {
        if (telemetry instanceof WattsTelemetry) watts = telemetry.value;
        else if (telemetry instanceof CadenceTelemetry) cadenceRpm = telemetry.value;
        else if (telemetry instanceof ResistanceTelemetry) resistance = telemetry.value;
        else if (telemetry instanceof InclineTelemetry) gradePct = telemetry.value;
        else if (telemetry instanceof SpeedTelemetry) speedKph = telemetry.value;
        else if (telemetry instanceof HeartRateTelemetry) heartRate = telemetry.value;
    }
}
