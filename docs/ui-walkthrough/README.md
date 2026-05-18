# UI walkthrough — visual evidence

Before/after captures from an end-to-end browser walkthrough of the live
simulator at https://ussd.jeffgicharu.com, driven with Playwright.

| File | State |
|------|-------|
| `before-01-landing.png` | Landing before: idle line wrapped (`Dial` pushed to the right, `*384# to start` on the next row); Request Log empty with no placeholder. |
| `before-02-mobile.png`  | Mobile (375px) before: three fixed-width columns never stacked; Settings panel pushed off-screen, ~340px horizontal overflow. |
| `after-01-landing.png`  | Landing after: idle line on one row; Request Log shows a friendly empty state; Session panel reports honest Idle/—/— state. |
| `after-02-dialed-log.png` | After dialing: Request Log populated and readable; Session panel shows Active + id + activity time. |
| `after-03-mobile.png`   | Mobile (375px) after: panels stack into a single column, no overflow, every control usable. |

These are regenerated on demand and not wired into CI; the behavioural
guarantees behind them live in `e2e/local/17-ui-polish.spec.ts`,
`e2e/local/18-mobile-layout.spec.ts`, and
`e2e/live-smoke/07-live-ui-polish.spec.ts`.
