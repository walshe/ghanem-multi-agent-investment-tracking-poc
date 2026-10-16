/**
 * Money helpers. Amounts are integer minor units (cents). No floating-point maths:
 * conversions work on the digits of the string.
 */

// Whole euros, optionally followed by "." or "," and one or two decimal digits.
const EUROS_PATTERN = /^(\d+)(?:[.,](\d{1,2}))?$/;

/**
 * Converts a euro amount typed by the user into integer cents.
 * "100.50" -> 10050, "100,5" -> 10050, "7" -> 700.
 * Returns null if the input is not a valid amount (e.g. "abc", "1.234", "-5", "").
 */
export function eurosToCents(input: string): number | null {
  const match = EUROS_PATTERN.exec(input.trim());
  if (!match) return null;
  const whole = match[1];
  const fraction = (match[2] ?? '').padEnd(2, '0');
  const digits = (whole + fraction).replace(/^0+(?=\d)/, '');
  const cents = Number(digits); // parses an integer string: exact while it is a safe integer
  if (!Number.isSafeInteger(cents)) return null;
  return cents;
}

/** Formats integer cents for display, e.g. (10050, "EUR") -> "EUR 100.50". */
export function formatCents(amountMinor: number, currency: string): string {
  if (!Number.isSafeInteger(amountMinor)) return `${currency} ?`;
  const negative = amountMinor < 0;
  const digits = String(Math.abs(amountMinor)).padStart(3, '0');
  return `${currency} ${negative ? '-' : ''}${digits.slice(0, -2)}.${digits.slice(-2)}`;
}
