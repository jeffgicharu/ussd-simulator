# USSD Simulator

When you dial `*334#` on your phone and navigate through menus to send money or buy airtime, there's a backend managing your session, tracking which screen you're on, and processing your input one digit at a time. This project is that backend.

It simulates the full M-Pesa USSD experience with 28 menu screens across 7 flows (send money, withdraw, deposit, buy airtime, check balance, account management, loans). It works with [Africa's Talking](https://africastalking.com/) USSD gateway format out of the box, and includes a browser-based phone simulator for testing.

But beyond the menu navigation, it also handles the things a production USSD system needs: persistent session logging for compliance, transaction logging for dispute resolution, PIN lockout after failed attempts, analytics on which screens users drop off at, and self-service account registration for new phone numbers.

## What It Does

**USSD menu flows:**
- Send money with tiered M-Pesa-style fee calculation
- Withdraw cash at agent
- Deposit to wallet
- Buy airtime (own phone or another number)
- Check balance
- My Account (phone number, change PIN, language, mini/full statement)
- Loans and savings

**Account management:**
- Unregistered phone numbers are automatically redirected to a self-service registration flow
- Users create a 4-digit PIN during registration
- 3 pre-seeded demo accounts for immediate testing

**Security:**
- PIN lockout after 3 consecutive failed attempts, with 15-minute cooldown
- Locked accounts reject all PIN-protected operations until the lockout expires
- Failed attempt counter resets on successful PIN entry

**Persistent logging:**
- Every USSD session is logged to the database: session ID, phone number, screens visited, duration, and outcome (completed vs timed out)
- Every financial transaction is logged: type, amount, fee, counterparty, reference, status, and resulting balance
- Customer support can query any user's session and transaction history

**Analytics:**
- Total sessions per hour and per day
- Average session duration
- Drop-off rates per screen (which screens do users abandon?)
- Transaction volume by type and total KES processed
- Per-customer session and transaction history

## How It Works

USSD isn't like a normal API. Sessions are stateful, short-lived (180 seconds), and driven by single-digit inputs. The project uses a screen-based state machine. Each screen is a self-contained Spring `@Component` that knows how to display itself and process user input. The engine auto-discovers all screens at startup and routes requests based on session state.

When an unregistered phone number dials in, the engine detects they're not in the system and redirects them to the registration flow instead of the main menu. Once registered, they see the full menu on their next dial.

## Quick Start

```bash
mvn spring-boot:run
```

Open [http://localhost:8181](http://localhost:8181) for the web phone simulator. Or use the API directly.

## Demo Accounts

| Phone | PIN | Balance |
|---|---|---|
| +254700000001 | 1234 | KES 75,000 |
| +254700000002 | 5678 | KES 12,500 |
| +254700000003 | 4321 | KES 3,200 |

Any other phone number will be treated as unregistered and sent to the registration flow.

## API Endpoints

### USSD (production gateway integration)

| Method | Endpoint | Format | Description |
|---|---|---|---|
| POST | `/ussd/callback` | form-encoded | Africa's Talking compatible callback |
| POST | `/ussd/api` | JSON | Web simulator and custom integrations |

### Analytics

| Method | Endpoint | Description |
|---|---|---|
| GET | `/api/analytics/sessions` | Session stats, drop-off rates, duration averages |
| GET | `/api/analytics/transactions` | Transaction volume by type |
| GET | `/api/analytics/customer/{phone}` | Session and transaction history for a customer |
| GET | `/api/metrics` | Active sessions and registered screen count |

## Menu Tree

```
*384# (registered users)
├── 1. Send Money
├── 2. Withdraw Cash
├── 3. Buy Airtime
├── 4. Check Balance
├── 5. Deposit
├── 6. My Account
└── 7. Loans & Savings

*384# (unregistered users)
└── Create PIN → Confirm PIN → Registration complete
```

## Try It Out

```bash
# Africa's Talking format (how real telcos send requests)
curl -X POST http://localhost:8181/ussd/callback \
  -d "sessionId=demo1&phoneNumber=+254700000001&text=4*1234"
# Returns: END Your M-Wallet balance is: KES 75000.00

# JSON format
curl -X POST http://localhost:8181/ussd/api \
  -H "Content-Type: application/json" \
  -d '{"sessionId":"demo2","phoneNumber":"+254700000001","input":""}'

# Check analytics
curl http://localhost:8181/api/analytics/sessions

# Look up customer history
curl http://localhost:8181/api/analytics/customer/+254700000001
```

## Built With

Spring Boot 3.2, Java 17, Spring Data JPA, H2 (in-memory database for session and transaction logs), Docker, GitHub Actions CI.

## Tests

```bash
mvn test   # 23 tests
```

**Unit tests (12):** main menu rendering, send money flow, phone validation, balance with correct/wrong PIN, airtime, account info, invalid input, AT CON/END formatting, shortcode chain.

**Integration tests (11):** AT callback format, AT balance check, JSON API main menu, deposit flow through HTTP, unregistered user redirect, registration flow, metrics endpoint, session analytics, transaction analytics, customer history, wrong PIN through HTTP.

## License

MIT
