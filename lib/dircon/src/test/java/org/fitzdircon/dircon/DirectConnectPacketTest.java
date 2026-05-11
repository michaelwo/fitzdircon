package org.fitzdircon.dircon;

import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class DirectConnectPacketTest {

    // --- Existing tests ---

    @Test
    public void discoverServicesRoundTrip() {
        DirectConnectPacket request = new DirectConnectPacket();
        request.request = true;
        request.identifier = DirectConnectPacket.MSG_DISCOVER_SERVICES;

        byte[] encoded = request.encode(7);
        DirectConnectPacket.ParseResult result = DirectConnectPacket.parse(encoded, 0);

        assertEquals(encoded.length, result.consumed);
        assertTrue(result.packet.request);
        assertEquals(DirectConnectPacket.MSG_DISCOVER_SERVICES, result.packet.identifier);
    }

    @Test
    public void notificationEncodesCharacteristicPayload() {
        DirectConnectProfile profile = new DirectConnectProfile();

        byte[] encoded = profile.notification(DirectConnectProfile.CHAR_INDOOR_BIKE_DATA,
                new byte[] { 0x64, 0x02 });
        DirectConnectPacket.ParseResult result = DirectConnectPacket.parse(encoded, 0);

        assertEquals(DirectConnectPacket.MSG_NOTIFICATION, result.packet.identifier);
        assertEquals(DirectConnectProfile.CHAR_INDOOR_BIKE_DATA, result.packet.uuid);
        assertArrayEquals(new byte[] { 0x64, 0x02 }, result.packet.additionalData);
    }

    // --- Frame boundary / error handling ---

    @Test
    public void parseWaitsWhenBufferTooShort() {
        byte[] buffer = new byte[3];
        DirectConnectPacket.ParseResult result = DirectConnectPacket.parse(buffer, 0);
        assertEquals(DirectConnectPacket.ParseResult.WAIT, result.status);
    }

    @Test
    public void parseWaitsWhenPayloadIncomplete() {
        // Header declares length=10 but only 4 payload bytes follow
        byte[] buffer = new byte[] { 0x01, 0x01, 0x00, 0x00, 0x00, 0x0A, 0x00, 0x00, 0x00, 0x00 };
        DirectConnectPacket.ParseResult result = DirectConnectPacket.parse(buffer, 0);
        assertEquals(DirectConnectPacket.ParseResult.WAIT, result.status);
    }

    @Test
    public void parseErrorOnInvalidPayload() {
        // MSG_DISCOVER_SERVICES with length=3 (not a multiple of 16) is invalid
        byte[] buffer = new byte[] { 0x01, 0x01, 0x02, 0x00, 0x00, 0x03, 0x00, 0x00, 0x00 };
        DirectConnectPacket.ParseResult result = DirectConnectPacket.parse(buffer, 0);
        assertEquals(DirectConnectPacket.ParseResult.ERROR, result.status);
    }

    @Test
    public void parseAcceptsNonSuccessResponse() {
        // Non-SUCCESS response codes bypass payload parsing and are returned as-is
        byte[] buffer = new byte[] { 0x01, 0x01, 0x01, 0x03, 0x00, 0x00 }; // RESP_SERVICE_NOT_FOUND
        DirectConnectPacket.ParseResult result = DirectConnectPacket.parse(buffer, 0);
        assertEquals(6, result.consumed);
        assertEquals(DirectConnectPacket.RESP_SERVICE_NOT_FOUND, result.packet.responseCode);
        // status == consumed for OK (not -1 or -2)
        assertEquals(6, result.status);
    }

    // --- All message types round-trip ---

    @Test
    public void discoverCharacteristicsRequestRoundTrip() {
        DirectConnectPacket req = new DirectConnectPacket();
        req.request = true;
        req.identifier = DirectConnectPacket.MSG_DISCOVER_CHARACTERISTICS;
        req.uuid = DirectConnectProfile.UUID_FITNESS_MACHINE;

        byte[] encoded = req.encode(0);
        DirectConnectPacket.ParseResult result = DirectConnectPacket.parse(encoded, 0);

        assertEquals(DirectConnectPacket.MSG_DISCOVER_CHARACTERISTICS, result.packet.identifier);
        assertEquals(DirectConnectProfile.UUID_FITNESS_MACHINE, result.packet.uuid);
        assertTrue(result.packet.request);
    }

    @Test
    public void discoverCharacteristicsResponseRoundTrip() {
        DirectConnectPacket resp = new DirectConnectPacket();
        resp.request = false;
        resp.identifier = DirectConnectPacket.MSG_DISCOVER_CHARACTERISTICS;
        resp.uuid = DirectConnectProfile.UUID_FITNESS_MACHINE;
        resp.uuids.add(DirectConnectProfile.CHAR_FITNESS_MACHINE_FEATURE);
        resp.uuids.add(DirectConnectProfile.CHAR_INDOOR_BIKE_DATA);
        resp.additionalData = new byte[] { DirectConnectPacket.CHAR_READ, DirectConnectPacket.CHAR_NOTIFY };

        byte[] encoded = resp.encode(0);
        DirectConnectPacket.ParseResult result = DirectConnectPacket.parse(encoded, 0);

        assertEquals(DirectConnectProfile.UUID_FITNESS_MACHINE, result.packet.uuid);
        assertEquals(2, result.packet.uuids.size());
        assertEquals(DirectConnectProfile.CHAR_FITNESS_MACHINE_FEATURE, (int) result.packet.uuids.get(0));
        assertEquals(DirectConnectProfile.CHAR_INDOOR_BIKE_DATA, (int) result.packet.uuids.get(1));
        assertArrayEquals(new byte[] { DirectConnectPacket.CHAR_READ, DirectConnectPacket.CHAR_NOTIFY },
                result.packet.additionalData);
    }

    @Test
    public void readCharacteristicRequestRoundTrip() {
        DirectConnectPacket req = new DirectConnectPacket();
        req.request = true;
        req.identifier = DirectConnectPacket.MSG_READ_CHARACTERISTIC;
        req.uuid = DirectConnectProfile.CHAR_FITNESS_MACHINE_FEATURE;

        byte[] encoded = req.encode(0);
        DirectConnectPacket.ParseResult result = DirectConnectPacket.parse(encoded, 0);

        assertEquals(DirectConnectPacket.MSG_READ_CHARACTERISTIC, result.packet.identifier);
        assertEquals(DirectConnectProfile.CHAR_FITNESS_MACHINE_FEATURE, result.packet.uuid);
        assertTrue(result.packet.request);
        assertEquals(0, result.packet.additionalData.length);
    }

    @Test
    public void readCharacteristicResponseRoundTrip() {
        DirectConnectPacket resp = new DirectConnectPacket();
        resp.request = false;
        resp.identifier = DirectConnectPacket.MSG_READ_CHARACTERISTIC;
        resp.uuid = DirectConnectProfile.CHAR_FITNESS_MACHINE_FEATURE;
        resp.additionalData = new byte[] { (byte) 0x83, 0x14, 0x00, 0x00 };

        byte[] encoded = resp.encode(0);
        DirectConnectPacket.ParseResult result = DirectConnectPacket.parse(encoded, 0);

        assertEquals(DirectConnectProfile.CHAR_FITNESS_MACHINE_FEATURE, result.packet.uuid);
        assertArrayEquals(new byte[] { (byte) 0x83, 0x14, 0x00, 0x00 }, result.packet.additionalData);
        assertFalse(result.packet.request);
    }

    @Test
    public void writeCharacteristicRoundTrip() {
        DirectConnectPacket req = new DirectConnectPacket();
        req.request = true;
        req.identifier = DirectConnectPacket.MSG_WRITE_CHARACTERISTIC;
        req.uuid = DirectConnectProfile.CHAR_FTMS_CONTROL_POINT;
        req.additionalData = new byte[] { 0x00 };

        byte[] encoded = req.encode(0);
        DirectConnectPacket.ParseResult result = DirectConnectPacket.parse(encoded, 0);

        assertEquals(DirectConnectPacket.MSG_WRITE_CHARACTERISTIC, result.packet.identifier);
        assertEquals(DirectConnectProfile.CHAR_FTMS_CONTROL_POINT, result.packet.uuid);
        assertArrayEquals(new byte[] { 0x00 }, result.packet.additionalData);
        assertTrue(result.packet.request);
    }

    @Test
    public void enableNotificationsRoundTrip() {
        DirectConnectPacket req = new DirectConnectPacket();
        req.request = true;
        req.identifier = DirectConnectPacket.MSG_ENABLE_NOTIFICATIONS;
        req.uuid = DirectConnectProfile.CHAR_INDOOR_BIKE_DATA;
        req.additionalData = new byte[] { 0x01 };

        byte[] encoded = req.encode(0);
        DirectConnectPacket.ParseResult result = DirectConnectPacket.parse(encoded, 0);

        assertEquals(DirectConnectPacket.MSG_ENABLE_NOTIFICATIONS, result.packet.identifier);
        assertEquals(DirectConnectProfile.CHAR_INDOOR_BIKE_DATA, result.packet.uuid);
        assertTrue(result.packet.request);
        assertArrayEquals(new byte[] { 0x01 }, result.packet.additionalData);
    }

    @Test
    public void unknown0x07RoundTrip() {
        DirectConnectPacket req = new DirectConnectPacket();
        req.request = true;
        req.identifier = DirectConnectPacket.MSG_UNKNOWN_0X07;

        byte[] encoded = req.encode(0);
        DirectConnectPacket.ParseResult result = DirectConnectPacket.parse(encoded, 0);

        assertEquals(DirectConnectPacket.MSG_UNKNOWN_0X07, result.packet.identifier);
        assertTrue(result.packet.request);
    }

    // --- UUID encoding ---

    @Test
    public void standardBleUuidRoundTrip() {
        DirectConnectPacket req = new DirectConnectPacket();
        req.request = true;
        req.identifier = DirectConnectPacket.MSG_READ_CHARACTERISTIC;
        req.uuid = DirectConnectProfile.UUID_FITNESS_MACHINE; // 0x1826

        byte[] encoded = req.encode(0);
        DirectConnectPacket.ParseResult result = DirectConnectPacket.parse(encoded, 0);

        assertEquals(DirectConnectProfile.UUID_FITNESS_MACHINE, result.packet.uuid);
    }

    @Test
    public void zwiftPlayUuidRoundTrip() {
        // UUIDs 1–4 use a different base UUID; verify they round-trip correctly
        DirectConnectPacket req = new DirectConnectPacket();
        req.request = true;
        req.identifier = DirectConnectPacket.MSG_READ_CHARACTERISTIC;
        req.uuid = 2;

        byte[] encoded = req.encode(0);
        DirectConnectPacket.ParseResult result = DirectConnectPacket.parse(encoded, 0);

        assertEquals(2, result.packet.uuid);
    }

    // --- Sequence number / request detection ---

    @Test
    public void sameSequenceNumberIsResponse() {
        // seq == lastSequenceNumber → checkIsRequest returns false
        byte[] buffer = new byte[] { 0x01, 0x01, 0x05, 0x00, 0x00, 0x00 }; // seq=5
        DirectConnectPacket.ParseResult result = DirectConnectPacket.parse(buffer, 5);
        assertFalse(result.packet.request);
    }

    @Test
    public void differentSequenceNumberIsRequest() {
        byte[] buffer = new byte[] { 0x01, 0x01, 0x05, 0x00, 0x00, 0x00 }; // seq=5
        DirectConnectPacket.ParseResult result = DirectConnectPacket.parse(buffer, 7); // lastSeq=7 != 5
        assertTrue(result.packet.request);
    }

    @Test
    public void zeroLastSequenceIsRequest() {
        // lastSequenceNumber <= 0 is always treated as a request
        byte[] buffer = new byte[] { 0x01, 0x01, 0x05, 0x00, 0x00, 0x00 }; // seq=5
        DirectConnectPacket.ParseResult result = DirectConnectPacket.parse(buffer, 0);
        assertTrue(result.packet.request);
    }

    // --- MSG_ERROR encode ---

    @Test
    public void errorIdentifierEncodesEmpty() {
        DirectConnectPacket packet = new DirectConnectPacket(); // identifier defaults to MSG_ERROR
        byte[] encoded = packet.encode(0);
        assertEquals(0, encoded.length);
    }
}
