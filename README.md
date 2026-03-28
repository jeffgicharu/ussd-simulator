# USSD Simulator

When you dial `*334#` on your phone and navigate through menus to send money or buy airtime, there's a backend managing your session, tracking which screen you're on, and processing your input one digit at a time. This project is that backend.

It simulates the full M-Pesa USSD experience — 24 menu screens across 6 flows (send money, withdraw, buy airtime, check balance, account management, loans). It's compatible with [Africa's Talking](https://africastalking.com/) USSD gateway format, so it can plug directly into a real telco callback. It also includes a browser-based phone simulator so you can test everything visually.

## How USSD Works (and Why It's Different from REST)

USSD isn't like a normal API. There's no JSON, no stateless requests. The user dials a short code, and a session opens. They type `1`, the server responds with a menu. They type `0712345678`, the server asks for the amount. Each request is just a number or short string, and the server has to remember the entire conversation.

Sessions time out after 180 seconds. If the user takes too long, the carrier drops the connection. The backend has to handle all of this.

This project uses a **screen-based state machine** — each screen in the menu tree is a self-contained component that knows how to display itself and process user input. The engine routes requests to the right screen based on session state.

## What You Can Do

- **Send money** — enter phone number, amount, see the fee breakdown, confirm with PIN
- **Withdraw cash** — enter agent number, amount, confirm with PIN
- **Buy airtime** — for your own phone or another number
- **Check balance** — PIN-protected
- **My Account** — view phone number, change PIN, switch language, get mini/full statement
- **Loans & Savings** — request loan, repay, check balance, savings account

Fees follow M-Pesa's tiered structure (KES 0 for amounts under 100, up to KES 108 for larger transfers).

## Quick Start

```bash
mvn spring-boot:run
```

Open [http://localhost:8181](http://localhost:8181) in your browser. You'll see a phone with a green screen. Click "Dial *384#" and start navigating.

## Two Ways to Talk to It

**Africa's Talking format** (how real telco gateways send requests):

```bash
curl -X POST http://localhost:8181/ussd/callback \
  -d "sessionId=abc123&phoneNumber=+254700000001&serviceCode=*384#&text=1"
```

Response: `CON Enter recipient phone number:` or `END Transaction confirmed.`

**JSON API** (for the web simulator and custom integrations):

```bash
curl -X POST http://localhost:8181/ussd/api \
  -H "Content-Type: application/json" \
  -d '{"sessionId":"abc123","phoneNumber":"+254700000001","input":"4"}'
```

You can also send the entire input chain at once — `text=4*1234` checks balance with PIN `1234` in a single request. The engine splits the chain and replays each input against the session.

## Demo Accounts

| Phone | PIN | Balance |
|---|---|---|
| +254700000001 | 1234 | KES 75,000 |
| +254700000002 | 5678 | KES 12,500 |
| +254700000003 | 4321 | KES 3,200 |

## Menu Tree

```
*384# → Main Menu
├── 1. Send Money → Phone → Amount (shows fee) → PIN → Done
├── 2. Withdraw → Agent number → Amount → PIN → Done
├── 3. Buy Airtime → My Phone / Other → Amount → PIN → Done
├── 4. Check Balance → PIN → Balance shown
├── 5. My Account → Phone / Change PIN / Language / Statements
└── 6. Loans → Request / Repay / Balance / Savings
```

## How Screens Work

Every screen implements a simple interface:

```java
public interface UssdScreen {
    String getId();
    UssdResponse render(UssdSession session);
    UssdResponse handleInput(UssdSession session, String input);
}
```

Screens are Spring `@Component`s — the engine auto-discovers all of them at startup. To add a new screen, you just create a class, implement the interface, and annotate it. No registration needed.

## Built With

Spring Boot 3.2, Java 17, vanilla HTML/CSS/JS for the phone simulator. No database needed — sessions and wallets are in-memory.

## Tests

```bash
mvn test   # 12 tests
```

Covers main menu rendering, the full send money flow, phone number validation, balance check with correct and wrong PIN, airtime navigation, account info, invalid input handling, Africa's Talking CON/END formatting, and shortcode input chain processing.

## License

MIT
