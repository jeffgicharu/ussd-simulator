# USSD Simulator

An M-Pesa-style USSD menu system built with Spring Boot. Simulates the full USSD experience — session management, multi-level menu navigation, tiered transaction fees, and PIN-secured operations. Compatible with [Africa's Talking](https://africastalking.com/) USSD gateway format.

Includes a web-based phone simulator UI for interactive testing.

## Features

- **Session-Based State Machine** - Each USSD session tracks its current screen, navigation history, and collected data with configurable timeouts
- **24 Menu Screens** - Send money, withdraw, buy airtime, check balance, loans & savings, account management, PIN change, mini statements
- **Africa's Talking API Compatible** - Drop-in callback endpoint matching the AT gateway format (`CON`/`END` responses)
- **JSON API** - Clean REST endpoint for the web simulator and custom integrations
- **Tiered Fee Structure** - M-Pesa-style transaction fees based on amount brackets
- **PIN Verification** - All sensitive operations require PIN entry
- **Shortcode Support** - Handles `*384*4*1234#` style input chains in a single request
- **Session Cleanup** - Scheduled background task evicts expired sessions
- **Web Phone Simulator** - Browser-based UI that looks and feels like a USSD phone session
- **Demo Accounts** - Pre-seeded accounts for instant testing

## Tech Stack

| Component | Technology |
|---|---|
| Framework | Spring Boot 3.2 |
| Language | Java 17 |
| Architecture | Screen-based state machine |
| API Format | Africa's Talking compatible + JSON |
| Session Store | In-memory (ConcurrentHashMap) |
| Build | Maven |
| Testing | JUnit 5 + Spring Boot Test |

## Getting Started

### Prerequisites

- Java 17+
- Maven 3.8+

### Run

```bash
mvn spring-boot:run
```

The app starts on `http://localhost:8181`.

### Web Simulator

Open [http://localhost:8181](http://localhost:8181) in your browser. You'll see a phone simulator where you can:
1. Click "Dial *384#" to start a session
2. Type menu choices (1, 2, 3...) and press Send
3. Walk through complete flows — send money, check balance, buy airtime, etc.

### Demo Accounts

| Phone | PIN | Balance |
|---|---|---|
| +254700000001 | 1234 | KES 75,000.00 |
| +254700000002 | 5678 | KES 12,500.00 |
| +254700000003 | 4321 | KES 3,200.00 |

## API Endpoints

### Africa's Talking Callback (for production gateway integration)

```
POST /ussd/callback
Content-Type: application/x-www-form-urlencoded

sessionId=abc123&phoneNumber=+254700000001&serviceCode=*384#&text=1*0700000002*500*1234
```

Response (plain text):
```
CON Enter recipient phone number:
```
or
```
END TXN0E841375 confirmed. KES 500.00 sent to +254700000002.
```

### JSON API (for web simulator and integrations)

```
POST /ussd/api
Content-Type: application/json

{
    "sessionId": "abc123",
    "phoneNumber": "+254700000001",
    "serviceCode": "*384#",
    "input": "1"
}
```

Response:
```json
{
    "message": "Enter recipient phone number:",
    "continueSession": true
}
```

## Menu Structure

```
*384# → Main Menu
├── 1. Send Money
│   ├── Enter phone number
│   ├── Enter amount (shows tiered fee)
│   └── Enter PIN → Transaction result
├── 2. Withdraw Cash
│   ├── Enter agent number
│   ├── Enter amount
│   └── Enter PIN → Withdrawal result
├── 3. Buy Airtime
│   ├── 1. My Phone → Enter amount → PIN
│   └── 2. Other Number → Enter phone → Enter amount → PIN
├── 4. Check Balance
│   └── Enter PIN → Balance display
├── 5. My Account
│   ├── 1. My Phone Number
│   ├── 2. Change PIN (old → new → confirm)
│   ├── 3. Language (English/Kiswahili)
│   ├── 4. Full Statement (SMS)
│   └── 5. Mini Statement → PIN → Last 5 transactions
└── 6. Loans & Savings
    ├── 1. Request Loan → Amount → Accept/Cancel
    ├── 2. Repay Loan → Amount
    ├── 3. Check Loan Balance
    └── 4. Savings Account
```

## Architecture

```
Request → UssdController → UssdEngine → Screen (state machine)
                              ↑              ↓
                        SessionManager   WalletService
```

### Key Components

- **`UssdScreen`** - Interface that each menu screen implements. Every screen knows how to render itself and process user input.
- **`UssdEngine`** - Routes requests to the correct screen based on session state. Handles both single-input and cumulative-chain (Africa's Talking) formats.
- **`SessionManager`** - Creates, retrieves, and expires USSD sessions. Runs a background cleanup task every 30 seconds.
- **`WalletService`** - Handles money operations (send, withdraw, airtime, balance). Uses in-memory simulation with pre-seeded accounts.
- **`UssdSession`** - Tracks current screen, navigation history (for back navigation), and collected form data (phone, amount, PIN).

### Adding a New Screen

1. Create a class implementing `UssdScreen`
2. Annotate with `@Component`
3. Return a unique ID from `getId()`
4. Implement `render()` and `handleInput()`
5. Navigate using `session.navigateTo("SCREEN_ID")`

The engine auto-discovers all `@Component` screens at startup.

## Configuration

| Property | Default | Description |
|---|---|---|
| `ussd.service-code` | *384# | USSD short code |
| `ussd.session-timeout-seconds` | 180 | Session TTL |
| `ussd.max-sessions` | 10000 | Max concurrent sessions |
| `ussd.wallet-api.base-url` | http://localhost:8080 | Wallet API URL |
| `ussd.wallet-api.enabled` | false | Enable Wallet API integration |

## Running Tests

```bash
mvn test
```

12 tests covering:
- Initial dial and main menu rendering
- Send money flow (phone validation, amount, fee calculation, PIN, completion)
- Balance check with correct and incorrect PIN
- Airtime purchase navigation
- My Account phone number display
- Invalid menu choice handling
- Africa's Talking response formatting (CON/END)
- Shortcode input chain processing

## License

MIT
