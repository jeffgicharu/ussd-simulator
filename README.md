# USSD Simulator

A USSD menu engine modeled after M-Pesa's `*334#` experience. Handles session-based state management, multi-level menu navigation, tiered transaction fees, and PIN-secured operations. Compatible with [Africa's Talking](https://africastalking.com/) USSD gateway format and includes a web-based phone simulator for interactive testing.

USSD is how over 30 million M-Pesa users interact with financial services on basic phones — no internet required. This project implements the backend that powers that experience.

## Why This Architecture

USSD is fundamentally different from REST APIs: sessions are stateful, short-lived (180s timeout), and driven by single-digit user inputs across unreliable mobile networks. The architecture reflects these constraints:

| Constraint | Solution | Implementation |
|---|---|---|
| Sessions must survive across requests | In-memory session store with TTL | `SessionManager` — `ConcurrentHashMap` with 180s expiry and background cleanup |
| Users navigate via "1", "2", "3" | Screen-based state machine | Each screen implements `UssdScreen` with `render()` and `handleInput()` |
| Gateway sends cumulative input chain | Input chain parser | Engine splits `1*0712345678*500*1234` and replays against session state |
| Sessions expire mid-flow | Timeout detection | `isNearTimeout()` warns before the carrier drops the session |
| Africa's Talking format required | Dual API support | `CON`/`END` plain-text format + JSON API for the web simulator |

## Tech Stack

| Layer | Technology |
|---|---|
| Framework | Spring Boot 3.2 |
| Language | Java 17 |
| Architecture | Screen-based state machine |
| API Format | Africa's Talking compatible + JSON |
| Session Store | In-memory (`ConcurrentHashMap`) |
| Frontend | Vanilla HTML/CSS/JS phone simulator |

## Menu Structure

```
*384# → Welcome to M-Wallet
├── 1. Send Money
│   ├── Enter phone number (validates, normalizes 0712→+254712)
│   ├── Enter amount (tiered M-Pesa fee schedule)
│   └── Enter PIN → Transaction confirmation
├── 2. Withdraw Cash
│   ├── Enter agent number → Enter amount → Enter PIN
├── 3. Buy Airtime
│   ├── 1. My Phone → Amount → PIN
│   └── 2. Other Number → Phone → Amount → PIN
├── 4. Check Balance → Enter PIN → Balance display
├── 5. My Account
│   ├── 1. My Phone Number
│   ├── 2. Change PIN (old → new → confirm)
│   ├── 3. Language (English / Kiswahili)
│   ├── 4. Full Statement (SMS delivery)
│   └── 5. Mini Statement → Last 5 transactions
└── 6. Loans & Savings
    ├── 1. Request Loan → Amount → Accept/Cancel
    ├── 2. Repay Loan → Amount
    ├── 3. Check Loan Balance
    └── 4. Savings Account
```

24 screens across 6 menu flows, auto-discovered at startup via `@Component` scanning.

## API Endpoints

### Africa's Talking Callback (production gateway integration)

```
POST /ussd/callback
Content-Type: application/x-www-form-urlencoded

sessionId=abc123&phoneNumber=+254700000001&serviceCode=*384#&text=1*0700000002*500*1234
```

Response: `CON Enter amount:` or `END Transaction confirmed.`

### JSON API (web simulator)

```
POST /ussd/api
{"sessionId":"abc123","phoneNumber":"+254700000001","input":"1"}
```

### Metrics

```
GET /api/metrics   → {"activeSessions": 42, "registeredScreens": 24}
```

## Web Phone Simulator

Open [http://localhost:8181](http://localhost:8181) — a visual phone with green-screen display, keypad input, and a request log panel. Click "Dial *384#" and walk through any flow.

## Demo Accounts

| Phone | PIN | Balance |
|---|---|---|
| +254700000001 | 1234 | KES 75,000 |
| +254700000002 | 5678 | KES 12,500 |
| +254700000003 | 4321 | KES 3,200 |

## Running

```bash
mvn spring-boot:run   # http://localhost:8181
```

## Testing

```bash
mvn test   # 12 tests
```

Covers: main menu rendering, send money flow (phone validation, amount, fee, PIN, completion), balance check with correct/incorrect PIN, airtime purchase, account info, invalid input handling, Africa's Talking CON/END formatting, shortcode input chain processing.

## License

MIT
