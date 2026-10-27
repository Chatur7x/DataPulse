"""Recalculate an exported method-v2 measurement using Python's standard library."""
import json
import math
import statistics
import sys
from pathlib import Path


def verify(path):
    result = json.loads(Path(path).read_text(encoding="utf-8"))
    raw = result["measurement"]
    assert raw and raw["methodVersion"] == 2, "A method-v2 raw record is required"
    assert len(raw["idleHttpMs"]) == 10
    assert all(math.isfinite(x) and x >= 0 for x in raw["idleHttpMs"])
    assert math.isclose(result["idleHttpMedianMs"], statistics.median(raw["idleHttpMs"]))
    jitter = statistics.mean(abs(b - a) for a, b in zip(raw["idleHttpMs"], raw["idleHttpMs"][1:]))
    assert math.isclose(result["idleSuccessiveJitterMs"], jitter)
    assert result["payloadBytes"] == raw["warmupBytes"] + raw["downloadBytes"] + raw["uploadAcknowledgedBytes"]
    assert 0 < result["payloadBytes"] <= raw["payloadCapBytes"]
    for side, byte_key in [("download", "downloadBytes"), ("upload", "uploadAcknowledgedBytes")]:
        assert raw[byte_key] > 0 and raw[side + "Nanos"] > 0
        assert math.isclose(result[side + "Mbps"], raw[byte_key] * 8000 / raw[side + "Nanos"])
        times = raw[side + "TraceElapsedNanos"]
        counts = raw[side + "TracePayloadBytes"]
        rates = raw[side + "MbpsTrace"]
        assert len(times) == len(counts) == len(rates)
        previous_time = previous_bytes = 0
        for timestamp, count, rate in zip(times, counts, rates):
            assert timestamp > previous_time and count >= previous_bytes
            assert math.isclose(rate, (count - previous_bytes) * 8000 / (timestamp - previous_time), abs_tol=1e-9)
            previous_time, previous_bytes = timestamp, count
    assert result["packetLoss"] is None, "HTTP failures must not be reported as packet loss"
    print(f"PASS: {path} — throughput, latency, jitter, payload cap and all trace points agree")
    print("This checks numerical consistency, not server attestation or a certified accuracy bound.")


if __name__ == "__main__":
    if len(sys.argv) < 2:
        raise SystemExit("Usage: python scripts/verify_speed_result.py measurement.json [more.json ...]")
    for filename in sys.argv[1:]:
        verify(filename)
