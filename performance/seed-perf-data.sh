#!/usr/bin/env bash
# The three demo accounts are seeded in code on every app start, so there
# is nothing to insert — this script just verifies the target answers and
# the demo accounts behave, before a load run.
#
#   performance/seed-perf-data.sh [host]   # default http://localhost:8082
set -euo pipefail
HOST="${1:-http://localhost:8082}"

echo "[seed-check] target: $HOST"
curl -fsS "$HOST/api/metrics" >/dev/null && echo "[seed-check] /api/metrics OK"

resp=$(curl -fsS -X POST "$HOST/ussd/api" \
  -H 'Content-Type: application/json' \
  -d '{"sessionId":"seed-check","phoneNumber":"+254700000001","input":""}')
echo "[seed-check] dial +254700000001 -> ${resp}"
case "$resp" in
  *"Welcome to M-Wallet"*) echo "[seed-check] demo account registered — OK" ;;
  *) echo "[seed-check] WARNING: demo account did not reach the main menu"; exit 1 ;;
esac
echo "[seed-check] ready for load"
