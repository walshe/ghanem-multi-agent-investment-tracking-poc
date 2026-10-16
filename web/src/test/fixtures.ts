import type { Checkout, CheckoutStatus } from '../api';

export const CHECKOUT_ID = '3fa85f64-5717-4562-b3fc-2c963f66afa6';

export function checkout(status: CheckoutStatus, overrides: Partial<Checkout> = {}): Checkout {
  return {
    checkoutId: CHECKOUT_ID,
    status,
    amountMinor: 10050,
    currency: 'EUR',
    paymentId: null,
    createdAt: new Date('2026-01-01T00:00:00Z'),
    updatedAt: new Date('2026-01-01T00:00:00Z'),
    ...overrides,
  };
}
