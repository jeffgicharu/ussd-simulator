# Configuration

Every runtime setting reads from an environment variable with a sensible
local-development default baked in, so `mvn spring-boot:run` works with zero
configuration while production overrides everything through the environment.

## Environment variables

| Variable | Default | Purpose |
|---|---|---|
| `SERVER_PORT` | `8181` | HTTP port the application listens on |
| `SPRING_PROFILES_ACTIVE` | _(none)_ | Set to `prod` for the hardened production profile |
| `SPRING_DATASOURCE_URL` | `jdbc:h2:mem:ussddb;DB_CLOSE_DELAY=-1` | JDBC URL for the session/transaction log store |
| `SPRING_DATASOURCE_DRIVER` | `org.h2.Driver` | JDBC driver class |
| `SPRING_DATASOURCE_USERNAME` | `sa` | Datasource username |
| `SPRING_DATASOURCE_PASSWORD` | _(empty)_ | Datasource password |
| `SPRING_JPA_DDL_AUTO` | `create-drop` | Hibernate schema mode |
| `SPRING_JPA_SHOW_SQL` | `false` | Log generated SQL |
| `H2_CONSOLE_ENABLED` | `true` | Expose the `/h2-console` web UI (forced **off** by the `prod` profile) |
| `USSD_SERVICE_CODE` | `*384#` | USSD short code the menu tree is rooted at |
| `USSD_SESSION_TIMEOUT_SECONDS` | `180` | Idle timeout before a session expires |
| `USSD_MAX_SESSIONS` | `10000` | Maximum concurrent in-memory sessions |
| `USSD_WALLET_API_BASE_URL` | `http://localhost:8080` | Optional external wallet API base URL |
| `USSD_WALLET_API_ENABLED` | `false` | Enable the optional external wallet API integration |

## Data store

Session logs and transaction logs are written to an **in-memory H2 database**
that is recreated on every start. Demo wallet balances and PINs are seeded in
code at startup, so a restart returns the simulator to a known clean state —
this is intentional for a demo/simulation tool and requires no external
database.

## Profiles

- **default** — local development: H2 console on, friendly error pages.
- **prod** — public deployment (`SPRING_PROFILES_ACTIVE=prod`): H2 console
  disabled, SQL logging off, error responses stripped of messages and stack
  traces.

## Example: production launch

```bash
export SPRING_PROFILES_ACTIVE=prod
export SERVER_PORT=8082
java -jar ussd-simulator.jar
```
