#!/usr/bin/env python3
"""
Smoke test for fitzdircon's Zwift Direct Connect server.

Connects to the TCP server, performs the GATT-over-TCP handshake,
subscribes to Indoor Bike Data notifications, reads telemetry, and
sends an incline command round-trip.

Usage:
    python3 scripts/dc_smoketest.py [HOST [PORT]]

Default HOST: 192.168.1.213   Default PORT: 36866

ADB port-forward shortcut:
    adb -s 192.168.1.213:5555 forward tcp:36866 tcp:36866
    python3 scripts/dc_smoketest.py 127.0.0.1
"""

import socket
import struct
import sys
import time

HOST = sys.argv[1] if len(sys.argv) > 1 else "192.168.1.213"
PORT = int(sys.argv[2]) if len(sys.argv) > 2 else 36866

# ── Protocol constants ────────────────────────────────────────────────────────

MSG_DISCOVER_SERVICES     = 0x01
MSG_DISCOVER_CHARS        = 0x02
MSG_WRITE_CHARACTERISTIC  = 0x04
MSG_ENABLE_NOTIFICATIONS  = 0x05
MSG_NOTIFICATION          = 0x06

RESP_SUCCESS = 0x00

UUID_FTMS          = 0x1826
UUID_CYCLING_POWER = 0x1818
UUID_CSC           = 0x1816

UUID_INDOOR_BIKE_DATA  = 0x2AD2
UUID_FTMS_CTRL_POINT   = 0x2AD9

FTMS_REQUEST_CONTROL    = 0x00
FTMS_SET_SIM_PARAMS     = 0x11
FTMS_RESPONSE_CODE      = 0x80
FTMS_SUCCESS            = 0x01

# BASE_UUID template: short UUID goes at bytes [2:4] big-endian
BASE_UUID = bytes([
    0x00, 0x00, 0x18, 0x26, 0x00, 0x00, 0x10, 0x00,
    0x80, 0x00, 0x00, 0x80, 0x5F, 0x9B, 0x34, 0xFB,
])

# ── Helpers ───────────────────────────────────────────────────────────────────

def uuid16(short):
    b = bytearray(BASE_UUID)
    b[2] = (short >> 8) & 0xFF
    b[3] = short & 0xFF
    return bytes(b)


def make_packet(msg_type, seq, payload=b""):
    return struct.pack(">BBBBH", 1, msg_type, seq, RESP_SUCCESS, len(payload)) + payload


def recvall(sock, n):
    buf = b""
    while len(buf) < n:
        chunk = sock.recv(n - len(buf))
        if not chunk:
            raise ConnectionError("connection closed by server")
        buf += chunk
    return buf


def recv_one(sock):
    """Read exactly one packet, returning (msg_type, seq, resp_code, payload)."""
    header = recvall(sock, 6)
    _, msg_type, seq, resp_code, length = struct.unpack(">BBBBH", header)
    payload = recvall(sock, length) if length else b""
    return msg_type, seq, resp_code, payload


def recv_until_seq(sock, target_seq, timeout=5.0):
    """
    Read packets until we get one with seq==target_seq.
    MSG_NOTIFICATION packets (seq=0) collected separately and returned alongside.
    Raises TimeoutError if the target response doesn't arrive in time.
    """
    sock.settimeout(timeout)
    notifications = []
    deadline = time.monotonic() + timeout
    while True:
        remaining = deadline - time.monotonic()
        if remaining <= 0:
            raise TimeoutError(f"timed out waiting for seq={target_seq}")
        sock.settimeout(remaining)
        msg_type, seq, resp_code, payload = recv_one(sock)
        if msg_type == MSG_NOTIFICATION or seq == 0:
            notifications.append((msg_type, seq, resp_code, payload))
        elif seq == target_seq:
            return resp_code, payload, notifications
        # unexpected packet — discard and keep waiting


def short_uuids_from_payload(payload):
    """Extract a list of 16-bit UUIDs from a payload containing 16-byte UUID entries."""
    return [(payload[i + 2] << 8) | payload[i + 3] for i in range(0, len(payload), 16)]


def decode_indoor_bike_data(data):
    """Parse 12-byte Indoor Bike Data payload."""
    if len(data) < 12:
        return None
    speed_raw, cadence, resistance, watts = struct.unpack_from("<HHHH", data, 2)
    hr = data[10]
    return speed_raw / 100.0, cadence, resistance, watts, hr


# ── Smoke test ────────────────────────────────────────────────────────────────

failures = []

def check(label, condition, detail=""):
    if condition:
        print(f"PASS: {label}")
    else:
        msg = f"FAIL: {label}" + (f" — {detail}" if detail else "")
        print(msg)
        failures.append(msg)


print(f"Connecting to {HOST}:{PORT}...")
try:
    sock = socket.create_connection((HOST, PORT), timeout=5)
    sock.setsockopt(socket.IPPROTO_TCP, socket.TCP_NODELAY, 1)
except OSError as e:
    print(f"FAIL: could not connect — {e}")
    sys.exit(1)

print(f"PASS: connected to {HOST}:{PORT}")
seq = 0

with sock:
    # ── Step 1: Discover services ─────────────────────────────────────────────
    seq += 1
    sock.sendall(make_packet(MSG_DISCOVER_SERVICES, seq))
    resp_code, payload, _ = recv_until_seq(sock, seq)
    service_uuids = short_uuids_from_payload(payload)
    services_ok = all(u in service_uuids for u in (UUID_FTMS, UUID_CYCLING_POWER, UUID_CSC))
    check(
        f"services {[hex(u) for u in service_uuids]}",
        resp_code == RESP_SUCCESS and services_ok,
        "expected 0x1826 0x1818 0x1816" if not services_ok else f"resp={resp_code}",
    )

    # ── Step 2: Enable Indoor Bike Data notifications ─────────────────────────
    seq += 1
    sock.sendall(make_packet(
        MSG_ENABLE_NOTIFICATIONS, seq,
        uuid16(UUID_INDOOR_BIKE_DATA) + bytes([0x01]),
    ))
    resp_code, _, _ = recv_until_seq(sock, seq)
    check("notifications enabled for Indoor Bike Data", resp_code == RESP_SUCCESS,
          f"resp={resp_code}")

    # ── Step 3: Collect 2 telemetry notifications ─────────────────────────────
    print("Waiting for telemetry (up to 5 s)...")
    telemetry_count = 0
    sock.settimeout(5.0)
    deadline = time.monotonic() + 5.0
    while telemetry_count < 2 and time.monotonic() < deadline:
        sock.settimeout(max(0.1, deadline - time.monotonic()))
        try:
            msg_type, recv_seq, _, payload = recv_one(sock)
        except (TimeoutError, socket.timeout):
            break
        if msg_type != MSG_NOTIFICATION or len(payload) < 16:
            continue
        char_uuid = (payload[2] << 8) | payload[3]
        if char_uuid != UUID_INDOOR_BIKE_DATA:
            continue
        telemetry_count += 1
        decoded = decode_indoor_bike_data(payload[16:])
        if decoded:
            speed, cadence, resistance, watts, hr = decoded
            print(f"  Telemetry[{telemetry_count}]: "
                  f"speed={speed:.1f} km/h  cadence={cadence} rpm  "
                  f"resistance={resistance}  watts={watts} W  HR={hr} bpm")
    check(f"{telemetry_count}/2 telemetry notifications received",
          telemetry_count >= 2,
          "heartbeat fires every 1 s — check fitzdircon is running and Direct Connect is enabled")

    # ── Step 4: Set incline to 3.0% ──────────────────────────────────────────
    # The server sends: write ACK (seq=N) then immediately a CP notification (seq=0).
    # recv_until_seq returns on the write ACK; the CP notification may arrive just after.
    grade_raw = int(3.0 * 100)  # 300 = 0x012C
    grade_bytes = struct.pack("<h", grade_raw)  # signed int16 LE
    seq += 1
    sock.sendall(make_packet(
        MSG_WRITE_CHARACTERISTIC, seq,
        uuid16(UUID_FTMS_CTRL_POINT) + bytes([FTMS_SET_SIM_PARAMS, 0x00, 0x00]) + grade_bytes,
    ))
    resp_code, _, notifs = recv_until_seq(sock, seq)

    # CP notification payload layout: [16-byte UUID][0x80][opcode_echo][result]
    # So opcode_echo is at payload[17], result at payload[18].
    def find_cp_notif(packets):
        return next(
            (n for n in packets
             if n[0] == MSG_NOTIFICATION
             and len(n[3]) >= 19
             and n[3][16] == FTMS_RESPONSE_CODE
             and n[3][17] == FTMS_SET_SIM_PARAMS),
            None,
        )

    cp_notif = find_cp_notif(notifs)
    if cp_notif is None:
        # CP notification arrives just after the write ACK but may be preceded by
        # rapid telemetry notifications (especially during an active workout).
        # Keep draining until we find it or time out.
        deadline = time.monotonic() + 2.0
        while cp_notif is None and time.monotonic() < deadline:
            try:
                sock.settimeout(max(0.1, deadline - time.monotonic()))
                msg_type, recv_seq, _, payload = recv_one(sock)
                notifs.append((msg_type, recv_seq, 0, payload))
                cp_notif = find_cp_notif(notifs)
            except (TimeoutError, socket.timeout):
                break

    incline_ok = False
    if cp_notif:
        result_byte = cp_notif[3][18]
        incline_ok = result_byte == FTMS_SUCCESS
        result_str = "success" if incline_ok else f"result=0x{result_byte:02X}"
    else:
        result_str = "no control-point indication received"

    check(f"incline command accepted (3.0% → {result_str})", resp_code == RESP_SUCCESS and incline_ok,
          result_str if not incline_ok else "")

    # ── Step 5: Restore incline to 0% ────────────────────────────────────────
    seq += 1
    sock.sendall(make_packet(
        MSG_WRITE_CHARACTERISTIC, seq,
        uuid16(UUID_FTMS_CTRL_POINT) + bytes([FTMS_SET_SIM_PARAMS, 0x00, 0x00, 0x00, 0x00]),
    ))
    resp_code, _, _ = recv_until_seq(sock, seq)
    if resp_code == RESP_SUCCESS:
        print("Restored grade to 0%")

print("Disconnected")

if failures:
    print(f"\n{len(failures)} failure(s):")
    for f in failures:
        print(f"  {f}")
    sys.exit(1)
else:
    print("\nAll checks passed.")
