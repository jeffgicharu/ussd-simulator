#!/usr/bin/env bash
# Drives the Africa's-Talking USSD webhook (POST /ussd/callback,
# form-encoded, cumulative '*'-joined text) directly. Local target is
# ephemeral (in-memory, reseeds on restart) so happy-path money flows are
# safe here. Idempotent; writes a markdown table to
# scripts/verify-endpoints-result.md.
set -uo pipefail
HOST="${1:-http://localhost:8082}"
OUT="$(dirname "$0")/verify-endpoints-result.md"
PASS=0; FAIL=0

call() { # phone text -> response body
  curl -fsS -X POST "$HOST/ussd/callback" \
    --data-urlencode "sessionId=verify-$RANDOM-$RANDOM" \
    --data-urlencode "phoneNumber=$1" \
    --data-urlencode "serviceCode=*384#" \
    --data-urlencode "text=$2" 2>/dev/null
}

{
  echo "# Endpoint Verification — local ($HOST)"
  echo
  echo "_$(date -u +%FT%TZ)_"
  echo
  echo "| Flow | Input chain | Expected | Shape | Result |"
  echo "|---|---|---|---|---|"
} > "$OUT"

check() { # name | phone | text | regex | expected-desc
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

A="+254700000001"   # alice / PIN 1234

check "Initial dial"        "$A" ""                          '^CON .*Welcome to M-Wallet'      "CON main menu"
check "Menu nav (Send)"     "$A" "1"                         '^CON .*recipient phone'          "CON sub-menu"
check "Invalid input"       "$A" "9"                         'Invalid choice'                  "menu re-display"
check "Send money"          "$A" "1*0700000002*100*1234"     '^END .*confirmed'                "END + ref"
check "Withdraw"            "$A" "2*12345*100*1234"          '^END .*(withdraw|confirmed)'     "END"
check "Deposit"             "$A" "5*500*1234"                '^END .*(deposit|confirmed)'      "END"
check "Buy airtime"         "$A" "3*1*50*1234"               '^END .*(airtime|confirmed)'      "END"
check "Check balance"       "$A" "4*1234"                    '^END .*KES'                      "END balance"
check "My account (phone)"  "$A" "6*1"                       '^END .*\+2547'                   "END"
check "Loans menu"          "$A" "7"                         '^CON .*Loans'                    "CON menu"

{
  echo
  echo "**Summary:** $PASS passed, $FAIL failed."
} >> "$OUT"

echo "verify-endpoints-local: $PASS passed, $FAIL failed -> $OUT"
[ "$FAIL" -eq 0 ]
