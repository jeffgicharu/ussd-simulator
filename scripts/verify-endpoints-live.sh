#!/usr/bin/env bash
# READ-ONLY / non-destructive verification against the live demo. No money
# operation is completed (no PIN submitted on transfer flows) so seed data
# is preserved. Rate-limited to ~2 req/s. Idempotent; appends a markdown
# table to scripts/verify-endpoints-result.md.
set -uo pipefail
HOST="${1:-https://ussd.jeffgicharu.com}"
OUT="$(dirname "$0")/verify-endpoints-result.md"
PASS=0; FAIL=0

call() {
  curl -fsS -X POST "$HOST/ussd/callback" \
    --data-urlencode "sessionId=liveverify-$RANDOM-$RANDOM" \
    --data-urlencode "phoneNumber=$1" \
    --data-urlencode "serviceCode=*384#" \
    --data-urlencode "text=$2" 2>/dev/null
  sleep 0.5   # ~2 req/s, gentle on the shared host
}

{
  echo
  echo "# Endpoint Verification — live ($HOST)"
  echo
  echo "_$(date -u +%FT%TZ)_ — READ-ONLY (no PIN submitted, no money moved)"
  echo
  echo "| Flow | Input chain | Expected | Shape | Result |"
  echo "|---|---|---|---|---|"
} >> "$OUT"

check() {
  local name="$1" phone="$2" text="$3" rx="$4" desc="$5"
  local body shape result
  body="$(call "$phone" "$text")"
  shape="$(printf '%s' "$body" | head -1 | cut -c1-3)"
  # USSD responses are multi-line; flatten so a single regex can span lines.
  if printf '%s' "$body" | tr '\n' ' ' | grep -qiE "$rx"; then
    result="PASS"; PASS=$((PASS+1))
  else
    result="FAIL"; FAIL=$((FAIL+1))
  fi
  printf '| %s | `%s` | %s | %s | %s |\n' \
    "$name" "${text:-（dial）}" "$desc" "$shape" "$result" >> "$OUT"
}

A="+254700000001"

check "Initial dial"          "$A" ""               '^CON .*Welcome to M-Wallet' "CON main menu"
check "Menu nav (Send)"       "$A" "1"              '^CON .*recipient phone'     "CON sub-menu"
check "Send walk (no PIN)"    "$A" "1*0700000002*100" '^CON .*PIN to confirm'    "CON pre-PIN stop"
check "Check balance"         "$A" "4*1234"         '^END .*KES'                 "END balance"
check "Invalid input"         "$A" "9"              'Invalid choice'             "menu re-display"

{
  echo
  echo "**Summary (live):** $PASS passed, $FAIL failed."
} >> "$OUT"

echo "verify-endpoints-live: $PASS passed, $FAIL failed -> $OUT"
[ "$FAIL" -eq 0 ]
