import { Page, APIRequestContext, expect } from '@playwright/test';

export interface UssdResponse {
  message: string;
  continueSession: boolean;
}

/**
 * Page object for the browser phone simulator (static/index.html).
 * The UI is a thin shell over POST /ussd/api, so each action waits for
 * the actual webhook response and returns its parsed JSON — robust
 * against the UI's 3-second post-END screen swap.
 */
export class UssdPhone {
  constructor(private readonly page: Page) {}

  async open(phone?: string) {
    await this.page.goto('/');
    await expect(this.page.locator('#screenContent')).toBeVisible();
    if (phone) await this.page.fill('#phoneNumber', phone);
  }

  async setPhone(phone: string) {
    await this.page.fill('#phoneNumber', phone);
  }

  private async capture(action: () => Promise<void>): Promise<UssdResponse> {
    const [resp] = await Promise.all([
      this.page.waitForResponse((r) => r.url().includes('/ussd/api')),
      action(),
    ]);
    expect(resp.status(), 'webhook HTTP status').toBe(200);
    return (await resp.json()) as UssdResponse;
  }

  /** Press the Dial button — starts a fresh session. */
  dial(): Promise<UssdResponse> {
    return this.capture(() => this.page.click('.btn-dial'));
  }

  /** Type a response and press Send. */
  send(input: string): Promise<UssdResponse> {
    return this.capture(async () => {
      await this.page.fill('#userInput', input);
      await this.page.click('.phone-input-area button');
    });
  }

  /** Walk a sequence of inputs after dialling; returns the final response. */
  async walk(...inputs: string[]): Promise<UssdResponse> {
    let last = await this.dial();
    for (const i of inputs) last = await this.send(i);
    return last;
  }

  /** What the phone screen currently shows. */
  screenText(): Promise<string> {
    return this.page.locator('#screenContent').innerText();
  }

  /** Register a fresh number (ends the session) and re-dial to the menu. */
  async register(phone: string, pin: string): Promise<UssdResponse> {
    await this.setPhone(phone);
    await this.dial(); // "Create a 4-digit PIN"
    await this.send(pin); // "Confirm your PIN"
    const done = await this.send(pin); // "Registration successful"
    expect(done.message).toContain('Registration successful');
    return done;
  }

  /** Deposit into the current phone (single step chain via the UI). */
  async deposit(phone: string, pin: string, amount: string): Promise<UssdResponse> {
    await this.setPhone(phone);
    await this.dial();
    await this.send('5'); // Deposit
    await this.send(amount);
    return this.send(pin); // END confirmed
  }
}

/** Drive the webhook directly (for hijack/contract probes the UI can't do). */
export async function postUssd(
  req: APIRequestContext,
  baseURL: string,
  body: { sessionId: string; phoneNumber: string; serviceCode?: string; input: string },
): Promise<{ status: number; json: UssdResponse }> {
  const r = await req.post(`${baseURL}/ussd/api`, {
    data: { serviceCode: '*384#', ...body },
    headers: { 'Content-Type': 'application/json' },
  });
  return { status: r.status(), json: (await r.json()) as UssdResponse };
}

export const ACCOUNTS = {
  alice: { phone: '+254700000001', pin: '1234' },
  bob: { phone: '+254700000002', pin: '5678' },
  carol: { phone: '+254700000003', pin: '4321' },
};

/** Unique throwaway number for write-heavy local registration tests. */
export function uniquePhone(): string {
  const n = (Date.now() % 1_000_000).toString().padStart(6, '0');
  const r = Math.floor(Math.random() * 900 + 100);
  return `+2547${r}${n}`;
}
