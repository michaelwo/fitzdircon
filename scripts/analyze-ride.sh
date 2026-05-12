#!/usr/bin/env bash
# Post-ride log analysis. Reads adb logcat output (file or stdin), extracts FZ: events,
# and reports ride duration, telemetry/command counts, connection timeline, and anomalies.
#
# Usage:
#   adb logcat -d | ./scripts/analyze-ride.sh
#   ./scripts/analyze-ride.sh logcat.txt

if [[ $# -gt 0 ]]; then
    FZ_LINES=$(grep -E 'FZ:(Main|DirCon|Platform|Dispatch)' "$1" 2>/dev/null) || true
else
    INPUT=$(cat)
    FZ_LINES=$(echo "$INPUT" | grep -E 'FZ:(Main|DirCon|Platform|Dispatch)' 2>/dev/null) || true
fi

if [[ -z "$FZ_LINES" ]]; then
    echo "No FZ: log lines found."
    exit 0
fi

# Count occurrences of a fixed string in FZ_LINES. Returns 0 on no match (never exits nonzero).
count() {
    local n
    n=$(echo "$FZ_LINES" | grep -F "$1" 2>/dev/null | wc -l | tr -d ' ')
    echo "${n:-0}"
}

count_pat() {
    local n
    n=$(echo "$FZ_LINES" | grep -E "$1" 2>/dev/null | wc -l | tr -d ' ')
    echo "${n:-0}"
}

FIRST_TS=$(echo "$FZ_LINES" | head -1 | awk '{print $1, $2}')
LAST_TS=$(echo  "$FZ_LINES" | tail -1 | awk '{print $1, $2}')

TELEMETRY_COUNT=$(count 'telemetry ')
COMMAND_COUNT=$(count 'control: ')
TCP_CONNECTS=$(count 'client connected:')
TCP_DISCONNECTS=$(count 'client disconnected')
MDNS_REGS=$(count 'mDNS registered:')
GRPC_STARTS=$(count 'gRPC metric streams starting')
GRPC_STOPS=$(count 'gRPC metric streams inactive')
STREAM_ERRORS=$(count 'stream error:')

echo "=== fitzdircon ride log analysis ==="
echo "First event : $FIRST_TS"
echo "Last event  : $LAST_TS"
echo ""
echo "--- Counts ---"
printf "  Telemetry updates  : %s\n" "$TELEMETRY_COUNT"
printf "  Zwift commands     : %s\n" "$COMMAND_COUNT"
printf "  TCP connects       : %s\n" "$TCP_CONNECTS"
printf "  TCP disconnects    : %s\n" "$TCP_DISCONNECTS"
printf "  mDNS registrations : %s\n" "$MDNS_REGS"
printf "  gRPC stream starts : %s\n" "$GRPC_STARTS"
printf "  gRPC stream stops  : %s\n" "$GRPC_STOPS"
printf "  gRPC stream errors : %s\n" "$STREAM_ERRORS"
echo ""

echo "--- Connection timeline ---"
TIMELINE=$(echo "$FZ_LINES" | grep -E 'mDNS|client connected|client disconnected|gRPC (metric|telemetry|channel)' 2>/dev/null) || true
if [[ -n "$TIMELINE" ]]; then
    echo "$TIMELINE"
else
    echo "  (none)"
fi
echo ""

echo "--- Anomalies ---"
ANOMALIES=0

if [[ "$TELEMETRY_COUNT" -eq 0 ]]; then
    echo "  WARNING: no telemetry updates (iFit gRPC not sending data?)"
    ANOMALIES=$((ANOMALIES + 1))
fi

if [[ "$TCP_CONNECTS" -gt 1 ]]; then
    echo "  WARNING: $TCP_CONNECTS Zwift connections (expected 1); possible repeated reconnects"
    ANOMALIES=$((ANOMALIES + 1))
fi

if [[ "$STREAM_ERRORS" -gt 0 ]]; then
    echo "  WARNING: $STREAM_ERRORS gRPC stream error(s)"
    echo "$FZ_LINES" | grep 'stream error:' 2>/dev/null | sed 's/^/    /' || true
    ANOMALIES=$((ANOMALIES + 1))
fi

CRED_ERRORS=$(count_pat 'FZ:Platform.*(credentials unavailable|failed|Error)')
if [[ "$CRED_ERRORS" -gt 0 ]]; then
    echo "  WARNING: $CRED_ERRORS credential/platform error(s)"
    echo "$FZ_LINES" | grep -E 'FZ:Platform.*(credentials unavailable|failed|Error)' 2>/dev/null | sed 's/^/    /' || true
    ANOMALIES=$((ANOMALIES + 1))
fi

if [[ "$ANOMALIES" -eq 0 ]]; then
    echo "  None."
fi
