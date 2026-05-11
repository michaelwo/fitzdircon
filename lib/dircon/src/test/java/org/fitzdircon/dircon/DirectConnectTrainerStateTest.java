package org.fitzdircon.dircon;

import org.fitzdircon.telemetry.CadenceTelemetry;
import org.fitzdircon.telemetry.GearTelemetry;
import org.fitzdircon.telemetry.HeartRateTelemetry;
import org.fitzdircon.telemetry.InclineTelemetry;
import org.fitzdircon.telemetry.ResistanceTelemetry;
import org.fitzdircon.telemetry.SpeedTelemetry;
import org.fitzdircon.telemetry.WattsTelemetry;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class DirectConnectTrainerStateTest {

    @Test
    public void fieldsStartNull() {
        DirectConnectTrainerState state = new DirectConnectTrainerState();
        assertNull(state.watts);
        assertNull(state.cadenceRpm);
        assertNull(state.resistance);
        assertNull(state.gradePct);
        assertNull(state.speedKph);
        assertNull(state.heartRate);
    }

    @Test
    public void applyWatts() {
        DirectConnectTrainerState state = new DirectConnectTrainerState();
        state.apply(new WattsTelemetry(150));
        assertEquals(150f, state.watts, 0.001f);
    }

    @Test
    public void applyCadence() {
        DirectConnectTrainerState state = new DirectConnectTrainerState();
        state.apply(new CadenceTelemetry(90));
        assertEquals(90f, state.cadenceRpm, 0.001f);
    }

    @Test
    public void applyResistance() {
        DirectConnectTrainerState state = new DirectConnectTrainerState();
        state.apply(new ResistanceTelemetry(5));
        assertEquals(5f, state.resistance, 0.001f);
    }

    @Test
    public void applyIncline() {
        DirectConnectTrainerState state = new DirectConnectTrainerState();
        state.apply(new InclineTelemetry(-2.5f));
        assertEquals(-2.5f, state.gradePct, 0.001f);
    }

    @Test
    public void applySpeed() {
        DirectConnectTrainerState state = new DirectConnectTrainerState();
        state.apply(new SpeedTelemetry(30));
        assertEquals(30f, state.speedKph, 0.001f);
    }

    @Test
    public void applyHeartRate() {
        DirectConnectTrainerState state = new DirectConnectTrainerState();
        state.apply(new HeartRateTelemetry(140));
        assertEquals(140f, state.heartRate, 0.001f);
    }

    @Test
    public void unknownTelemetryIgnored() {
        DirectConnectTrainerState state = new DirectConnectTrainerState();
        state.apply(new GearTelemetry(3)); // not handled by apply()
        assertNull(state.watts);
        assertNull(state.cadenceRpm);
        assertNull(state.resistance);
        assertNull(state.gradePct);
        assertNull(state.speedKph);
        assertNull(state.heartRate);
    }
}
