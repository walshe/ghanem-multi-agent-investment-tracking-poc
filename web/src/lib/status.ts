import { CheckoutStatus } from '../api';

/** Customer-facing message for each status the backend can return. */
export const STATUS_MESSAGES: Record<CheckoutStatus, string> = {
  [CheckoutStatus.Pending]: 'Waiting for your payment',
  [CheckoutStatus.Processing]: 'Your payment is being processed',
  [CheckoutStatus.Succeeded]: 'Payment received. Your investment is confirmed.',
  [CheckoutStatus.Failed]: 'Payment failed. You have not been charged.',
  [CheckoutStatus.Expired]:
    'This checkout expired. If you were charged, you will be refunded automatically.',
};

/** Final statuses never change (contract/states.md). */
const FINAL_STATUSES: ReadonlySet<CheckoutStatus> = new Set<CheckoutStatus>([
  CheckoutStatus.Succeeded,
  CheckoutStatus.Failed,
  CheckoutStatus.Expired,
]);

export function isFinalStatus(status: CheckoutStatus): boolean {
  return FINAL_STATUSES.has(status);
}
