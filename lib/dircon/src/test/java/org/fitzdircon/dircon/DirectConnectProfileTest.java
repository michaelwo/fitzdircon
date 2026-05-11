package org.fitzdircon.dircon;

import org.fitzdircon.command.InclineCommand;
import org.fitzdircon.command.ResistanceCommand;
import org.fitzdircon.telemetry.CadenceTelemetry;
import org.fitzdircon.telemetry.HeartRateTelemetry;
import org.fitzdircon.telemetry.ResistanceTelemetry;
import org.fitzdircon.telemetry.SpeedTelemetry;
import org.fitzdircon.telemetry.WattsTelemetry;
import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class DirectConnectProfileTest {
    @Test
    public void writeResistanceDecodesFtmsCommand() {
        DirectConnectProfile profile = new DirectConnectProfile();
        DirectConnectPacket packet = writeControlPoint(new byte[] { 0x04, 120 });

        DirectConnectProfile.Result result = profile.process(packet, new HashSet<>());

        assertEquals(1, result.commands.size());
        assertTrue(result.commands.get(0) instanceof ResistanceCommand);
        assertEquals(12.0f, result.commands.get(0).value, 0.001f);
        assertEquals(DirectConnectPacket.RESP_SUCCESS, result.response.responseCode);
    }

    @Test
    public void writeSimulationGradeDecodesFtmsCommand() {
        DirectConnectProfile profile = new DirectConnectProfile();
        DirectConnectPacket packet = writeControlPoint(new byte[] { 0x11, 0, 0, (byte) 0xD4, (byte) 0xFE, 0x28, 0x19 });

        DirectConnectProfile.Result result = profile.process(packet, new HashSet<>());

        assertEquals(1, result.commands.size());
        assertTrue(result.commands.get(0) instanceof InclineCommand);
        assertEquals(-3.0f, result.commands.get(0).value, 0.001f);
    }

    @Test
    public void cyclingPowerMeasurementContainsWattsAndCrankData() {
        DirectConnectProfile profile = new DirectConnectProfile();
        DirectConnectTrainerState state = new DirectConnectTrainerState();
        state.apply(new WattsTelemetry(225));
        state.apply(new CadenceTelemetry(60));

        profile.cyclingPowerMeasurement(state, 1_000);
        byte[] data = profile.cyclingPowerMeasurement(state, 2_000);

        assertEquals(0x30, data[0] & 0xFF);
        assertEquals(225, (data[2] & 0xFF) | ((data[3] & 0xFF) << 8));
        assertEquals(1, (data[10] & 0xFF) | ((data[11] & 0xFF) << 8));
    }

    @Test
    public void cscMeasurementContainsCrankData() {
        DirectConnectProfile profile = new DirectConnectProfile();
        DirectConnectTrainerState state = new DirectConnectTrainerState();
        state.apply(new CadenceTelemetry(120));

        profile.cscMeasurement(state, 1_000);
        byte[] data = profile.cscMeasurement(state, 2_000);

        assertEquals(0x02, data[0] & 0xFF);
        assertEquals(2, (data[1] & 0xFF) | ((data[2] & 0xFF) << 8));
    }

    @Test
    public void targetPowerIsAcknowledgedAndRemembered() {
        DirectConnectProfile profile = new DirectConnectProfile();
        DirectConnectPacket packet = writeControlPoint(new byte[] { 0x05, (byte) 0xC8, 0x00 });

        DirectConnectProfile.Result result = profile.process(packet, new HashSet<>());

        assertTrue(result.commands.isEmpty());
        assertEquals(200.0f, profile.lastTargetPowerWatts(), 0.001f);
        assertEquals(0x80, profile.controlPointAnswer()[0] & 0xFF);
        assertEquals(0x05, profile.controlPointAnswer()[1] & 0xFF);
        assertEquals(0x01, profile.controlPointAnswer()[2] & 0xFF);
    }

    // --- Service discovery ---

    @Test
    public void discoverServicesReturnsAllThreeServices() {
        DirectConnectProfile profile = new DirectConnectProfile();
        DirectConnectPacket packet = new DirectConnectPacket();
        packet.request = true;
        packet.identifier = DirectConnectPacket.MSG_DISCOVER_SERVICES;

        DirectConnectProfile.Result result = profile.process(packet, new HashSet<>());

        assertEquals(DirectConnectPacket.RESP_SUCCESS, result.response.responseCode);
        assertEquals(3, result.response.uuids.size());
        assertTrue(result.response.uuids.contains(DirectConnectProfile.UUID_FITNESS_MACHINE));
        assertTrue(result.response.uuids.contains(DirectConnectProfile.UUID_CYCLING_POWER));
        assertTrue(result.response.uuids.contains(DirectConnectProfile.UUID_CSC));
    }

    // --- Characteristic discovery ---

    @Test
    public void discoverCharacteristicsForFitnessMachine() {
        DirectConnectProfile profile = new DirectConnectProfile();
        DirectConnectPacket packet = new DirectConnectPacket();
        packet.request = true;
        packet.identifier = DirectConnectPacket.MSG_DISCOVER_CHARACTERISTICS;
        packet.uuid = DirectConnectProfile.UUID_FITNESS_MACHINE;

        DirectConnectProfile.Result result = profile.process(packet, new HashSet<>());

        assertEquals(DirectConnectPacket.RESP_SUCCESS, result.response.responseCode);
        assertEquals(DirectConnectProfile.UUID_FITNESS_MACHINE, result.response.uuid);
        assertEquals(6, result.response.uuids.size());
        assertTrue(result.response.uuids.contains(DirectConnectProfile.CHAR_FITNESS_MACHINE_FEATURE));
        assertTrue(result.response.uuids.contains(DirectConnectProfile.CHAR_FTMS_CONTROL_POINT));
        assertTrue(result.response.uuids.contains(DirectConnectProfile.CHAR_INDOOR_BIKE_DATA));
    }

    @Test
    public void discoverCharacteristicsUnknownService() {
        DirectConnectProfile profile = new DirectConnectProfile();
        DirectConnectPacket packet = new DirectConnectPacket();
        packet.request = true;
        packet.identifier = DirectConnectPacket.MSG_DISCOVER_CHARACTERISTICS;
        packet.uuid = 0xDEAD;

        DirectConnectProfile.Result result = profile.process(packet, new HashSet<>());

        assertEquals(DirectConnectPacket.RESP_SERVICE_NOT_FOUND, result.response.responseCode);
    }

    // --- Read characteristic ---

    @Test
    public void readCharacteristicSuccess() {
        DirectConnectProfile profile = new DirectConnectProfile();
        DirectConnectPacket packet = new DirectConnectPacket();
        packet.request = true;
        packet.identifier = DirectConnectPacket.MSG_READ_CHARACTERISTIC;
        packet.uuid = DirectConnectProfile.CHAR_FITNESS_MACHINE_FEATURE;

        DirectConnectProfile.Result result = profile.process(packet, new HashSet<>());

        assertEquals(DirectConnectPacket.RESP_SUCCESS, result.response.responseCode);
        assertArrayEquals(new byte[] { (byte) 0x83, 0x14, 0x00, 0x00, 0x0C, (byte) 0xE0, 0x00, 0x00 },
                result.response.additionalData);
    }

    @Test
    public void readCharacteristicNotFound() {
        DirectConnectProfile profile = new DirectConnectProfile();
        DirectConnectPacket packet = new DirectConnectPacket();
        packet.request = true;
        packet.identifier = DirectConnectPacket.MSG_READ_CHARACTERISTIC;
        packet.uuid = 0xDEAD;

        DirectConnectProfile.Result result = profile.process(packet, new HashSet<>());

        assertEquals(DirectConnectPacket.RESP_CHARACTERISTIC_NOT_FOUND, result.response.responseCode);
    }

    @Test
    public void readCharacteristicNotReadable() {
        DirectConnectProfile profile = new DirectConnectProfile();
        DirectConnectPacket packet = new DirectConnectPacket();
        packet.request = true;
        packet.identifier = DirectConnectPacket.MSG_READ_CHARACTERISTIC;
        packet.uuid = DirectConnectProfile.CHAR_INDOOR_BIKE_DATA; // NOTIFY only

        DirectConnectProfile.Result result = profile.process(packet, new HashSet<>());

        assertEquals(DirectConnectPacket.RESP_OPERATION_NOT_SUPPORTED, result.response.responseCode);
    }

    // --- Write characteristic ---

    @Test
    public void writeNonWritableCharacteristicFails() {
        DirectConnectProfile profile = new DirectConnectProfile();
        DirectConnectPacket packet = new DirectConnectPacket();
        packet.request = true;
        packet.identifier = DirectConnectPacket.MSG_WRITE_CHARACTERISTIC;
        packet.uuid = DirectConnectProfile.CHAR_INDOOR_BIKE_DATA; // NOTIFY only
        packet.additionalData = new byte[] { 0x01 };

        DirectConnectProfile.Result result = profile.process(packet, new HashSet<>());

        assertEquals(DirectConnectPacket.RESP_OPERATION_NOT_SUPPORTED, result.response.responseCode);
    }

    // --- Enable notifications ---

    @Test
    public void enableNotificationsAddsToSet() {
        DirectConnectProfile profile = new DirectConnectProfile();
        DirectConnectPacket packet = new DirectConnectPacket();
        packet.request = true;
        packet.identifier = DirectConnectPacket.MSG_ENABLE_NOTIFICATIONS;
        packet.uuid = DirectConnectProfile.CHAR_INDOOR_BIKE_DATA;
        packet.additionalData = new byte[] { 0x01 };
        Set<Integer> notifications = new HashSet<>();

        profile.process(packet, notifications);

        assertTrue(notifications.contains(DirectConnectProfile.CHAR_INDOOR_BIKE_DATA));
    }

    @Test
    public void disableNotificationsRemovesFromSet() {
        DirectConnectProfile profile = new DirectConnectProfile();
        Set<Integer> notifications = new HashSet<>();
        notifications.add(DirectConnectProfile.CHAR_INDOOR_BIKE_DATA);

        DirectConnectPacket packet = new DirectConnectPacket();
        packet.request = true;
        packet.identifier = DirectConnectPacket.MSG_ENABLE_NOTIFICATIONS;
        packet.uuid = DirectConnectProfile.CHAR_INDOOR_BIKE_DATA;
        packet.additionalData = new byte[] { 0x00 };

        profile.process(packet, notifications);

        assertFalse(notifications.contains(DirectConnectProfile.CHAR_INDOOR_BIKE_DATA));
    }

    @Test
    public void enableNotificationsOnNonNotifiableChar() {
        DirectConnectProfile profile = new DirectConnectProfile();
        DirectConnectPacket packet = new DirectConnectPacket();
        packet.request = true;
        packet.identifier = DirectConnectPacket.MSG_ENABLE_NOTIFICATIONS;
        packet.uuid = DirectConnectProfile.CHAR_FITNESS_MACHINE_FEATURE; // READ only
        packet.additionalData = new byte[] { 0x01 };

        DirectConnectProfile.Result result = profile.process(packet, new HashSet<>());

        assertEquals(DirectConnectPacket.RESP_OPERATION_NOT_SUPPORTED, result.response.responseCode);
    }

    // --- Unknown 0x07 ---

    @Test
    public void unknown0x07ReturnsSuccess() {
        DirectConnectProfile profile = new DirectConnectProfile();
        DirectConnectPacket packet = new DirectConnectPacket();
        packet.request = true;
        packet.identifier = DirectConnectPacket.MSG_UNKNOWN_0X07;

        DirectConnectProfile.Result result = profile.process(packet, new HashSet<>());

        assertEquals(DirectConnectPacket.RESP_SUCCESS, result.response.responseCode);
        assertTrue(result.commands.isEmpty());
    }

    // --- Indoor bike data encoding ---

    @Test
    public void indoorBikeDataEncoding() {
        DirectConnectProfile profile = new DirectConnectProfile();
        DirectConnectTrainerState state = new DirectConnectTrainerState();
        state.apply(new SpeedTelemetry(30));       // 30 kph → 3000 (× 100)
        state.apply(new CadenceTelemetry(80));
        state.apply(new ResistanceTelemetry(5));
        state.apply(new WattsTelemetry(200));
        state.apply(new HeartRateTelemetry(140));

        byte[] data = profile.indoorBikeData(state);

        assertEquals(12, data.length);
        assertEquals(3000, (data[2] & 0xFF) | ((data[3] & 0xFF) << 8)); // speed
        assertEquals(80,   (data[4] & 0xFF) | ((data[5] & 0xFF) << 8)); // cadence
        assertEquals(5,    (data[6] & 0xFF) | ((data[7] & 0xFF) << 8)); // resistance
        assertEquals(200,  (data[8] & 0xFF) | ((data[9] & 0xFF) << 8)); // watts
        assertEquals(140,  data[10] & 0xFF);                             // heart rate
    }

    // --- FTMS control point edge cases ---

    @Test
    public void requestControlIsAcknowledged() {
        DirectConnectProfile profile = new DirectConnectProfile();
        DirectConnectProfile.Result result = profile.process(
                writeControlPoint(new byte[] { 0x00 }), new HashSet<>()); // FTMS_REQUEST_CONTROL

        assertTrue(result.commands.isEmpty());
        assertEquals(0x80, profile.controlPointAnswer()[0] & 0xFF);
        assertEquals(0x00, profile.controlPointAnswer()[1] & 0xFF);
        assertEquals(0x01, profile.controlPointAnswer()[2] & 0xFF); // FTMS_SUCCESS
    }

    @Test
    public void startResumeIsAcknowledged() {
        DirectConnectProfile profile = new DirectConnectProfile();
        profile.process(writeControlPoint(new byte[] { 0x07 }), new HashSet<>()); // FTMS_START_RESUME

        assertEquals(0x07, profile.controlPointAnswer()[1] & 0xFF);
        assertEquals(0x01, profile.controlPointAnswer()[2] & 0xFF); // FTMS_SUCCESS
    }

    @Test
    public void unsupportedOpcodeReturnsNotSupported() {
        DirectConnectProfile profile = new DirectConnectProfile();
        profile.process(writeControlPoint(new byte[] { (byte) 0xFF }), new HashSet<>());

        assertEquals(0x80, profile.controlPointAnswer()[0] & 0xFF);
        assertEquals((byte) 0xFF, profile.controlPointAnswer()[1]);
        assertEquals(0x02, profile.controlPointAnswer()[2] & 0xFF); // FTMS_NOT_SUPPORTED
    }

    // --- Non-request packet is ignored ---

    @Test
    public void nonRequestPacketProducesNoOutput() {
        DirectConnectProfile profile = new DirectConnectProfile();
        DirectConnectPacket packet = new DirectConnectPacket();
        packet.request = false;
        packet.identifier = DirectConnectPacket.MSG_DISCOVER_SERVICES;

        DirectConnectProfile.Result result = profile.process(packet, new HashSet<>());

        assertTrue(result.commands.isEmpty());
        assertEquals(0, result.response.uuids.size());
    }

    private static DirectConnectPacket writeControlPoint(byte[] data) {
        DirectConnectPacket packet = new DirectConnectPacket();
        packet.request = true;
        packet.identifier = DirectConnectPacket.MSG_WRITE_CHARACTERISTIC;
        packet.uuid = DirectConnectProfile.CHAR_FTMS_CONTROL_POINT;
        packet.additionalData = data;
        return packet;
    }
}
