import { test, expect } from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';

/**
 * Regression coverage for the recruiter UI walkthrough polish pass.
 *
 * Each assertion below maps to a bug that shipped on the live site:
 *  - the idle welcome line wrapped because the static markup sat inside a
 *    `white-space: pre-wrap` box and its source indentation was rendered;
 *  - the Request Log panel had no empty state and looked unfinished;
 *  - the Session panel carried a "Screen:" field nothing ever populated;
 *  - the log timestamp colour failed WCAG AA contrast once entries existed;
 *  - inputs set `outline: none`, leaving keyboard users no focus ring.
 */
test.describe('UI polish', () => {
  test('idle welcome line renders clean with no leading/trailing whitespace', async ({
    page,
  }) => {
    await page.goto('/');
    const raw = await page.locator('#screenContent').textContent();
    // Exact match — any HTML-source indentation would re-introduce the wrap.
    expect(raw).toBe('Dial *384# to start.');
  });

  test('Request Log shows a friendly empty state, then real entries, then empty again', async ({
    page,
  }) => {
    await page.goto('/');

    const empty = page.locator('#logEmpty');
    await expect(empty).toBeVisible();
    await expect(empty).toContainText('No requests yet');
    await expect(page.locator('.log-entry')).toHaveCount(0);

    await Promise.all([
      page.waitForResponse((r) => r.url().includes('/ussd/api')),
      page.click('.btn-dial'),
    ]);
    await expect(empty).toBeHidden();
    expect(await page.locator('.log-entry').count()).toBeGreaterThan(0);

    await page.click('.btn-clear');
    await expect(empty).toBeVisible();
    await expect(page.locator('.log-entry')).toHaveCount(0);
  });

  test('Session panel reflects real state and has no dead Screen field', async ({
    page,
  }) => {
    await page.goto('/');
    await expect(page.locator('#currentScreen')).toHaveCount(0);
    await expect(page.locator('#sessionStatus')).toHaveText('Idle');
    await expect(page.locator('#sessionId')).toHaveText('—');

    await Promise.all([
      page.waitForResponse((r) => r.url().includes('/ussd/api')),
      page.click('.btn-dial'),
    ]);
    await expect(page.locator('#sessionStatus')).toHaveText('Active');
    await expect(page.locator('#sessionId')).not.toHaveText('—');
    await expect(page.locator('#lastActivity')).not.toHaveText('—');
  });

  test('an inline favicon is declared and the page loads without console errors', async ({
    page,
  }) => {
    const errors: string[] = [];
    page.on('console', (m) => m.type() === 'error' && errors.push(m.text()));
    page.on('pageerror', (e) => errors.push(e.message));

    await page.goto('/');
    await expect(page.locator('link[rel="icon"]')).toHaveCount(1);
    expect(errors, `console errors: ${errors.join(' | ')}`).toEqual([]);
  });

  test('axe-core: no serious/critical violations once the log is populated', async ({
    page,
  }) => {
    await page.goto('/');
    // The contrast regression only surfaced after log entries existed.
    await Promise.all([
      page.waitForResponse((r) => r.url().includes('/ussd/api')),
      page.click('.btn-dial'),
    ]);
    const results = await new AxeBuilder({ page })
      .withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'])
      .analyze();
    const serious = results.violations.filter(
      (v) => v.impact === 'serious' || v.impact === 'critical',
    );
    expect(serious, serious.map((v) => `${v.id}: ${v.help}`).join('\n')).toEqual(
      [],
    );
  });

  test('keyboard focus is visible on inputs and buttons', async ({ page }) => {
    await page.goto('/');
    const seen: string[] = [];
    for (let i = 0; i < 6; i++) {
      await page.keyboard.press('Tab');
      const ring = await page.evaluate(() => {
        const el = document.activeElement as HTMLElement;
        if (!el || el === document.body) return null;
        const s = getComputedStyle(el);
        return `${el.tagName}:${s.outlineStyle}:${s.outlineWidth}`;
      });
      if (ring) seen.push(ring);
    }
    // Every focusable control we tabbed onto must carry a real outline.
    expect(seen.length).toBeGreaterThan(0);
    for (const r of seen) {
      expect(r, `focused element had no outline: ${r}`).not.toContain(':none:');
      expect(r).not.toContain(':0px');
    }
  });

  // The input row is a flex container; a flex item keeps its intrinsic
  // width unless min-width:0 is set, which previously pushed the Send
  // button ~20px outside the phone body at every desktop width.
  test('no control overflows the phone body across viewport widths', async ({
    page,
  }) => {
    for (const width of [1670, 1440, 1280, 1100, 1081, 1080, 900, 768, 375]) {
      await page.setViewportSize({ width, height: 900 });
      await page.goto('/');
      const box = await page.evaluate(() => {
        const r = (s: string) =>
          document.querySelector(s)!.getBoundingClientRect();
        const phone = r('.phone');
        const send = r('.phone-input-area button');
        const input = r('.phone-input-area input');
        const actions = r('.phone-actions');
        return {
          // px the control extends past the phone's right/left edge
          sendRight: Math.round(send.right - phone.right),
          sendLeft: Math.round(phone.left - send.left),
          inputLeft: Math.round(phone.left - input.left),
          actionsRight: Math.round(actions.right - phone.right),
        };
      });
      expect(
        box.sendRight,
        `Send button overflows phone body at ${width}px (${box.sendRight}px past edge)`,
      ).toBeLessThanOrEqual(0);
      expect(
        box.actionsRight,
        `action row overflows phone body at ${width}px`,
      ).toBeLessThanOrEqual(0);
      expect(box.sendLeft, `Send button overflows left at ${width}px`).toBeLessThanOrEqual(0);
      expect(box.inputLeft, `input overflows left at ${width}px`).toBeLessThanOrEqual(0);
    }
  });
});
