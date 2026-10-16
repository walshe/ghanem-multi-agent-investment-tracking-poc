import type { Checkout } from '../api';
import { isFinalStatus } from './status';

export const POLL_INTERVAL_MS = 2000;

export interface PollCallbacks {
  onCheckout: (checkout: Checkout) => void;
  onError: (error: unknown) => void;
}

/**
 * Fetches the checkout now, then again intervalMs after each response, until the
 * backend reports a final status. Requests never overlap, so an older response can't
 * overwrite a newer one. Errors are reported and polling continues: the app never
 * assumes a payment result. Returns a function that stops polling.
 */
export function pollCheckout(
  fetchCheckout: () => Promise<Checkout>,
  { onCheckout, onError }: PollCallbacks,
  intervalMs: number = POLL_INTERVAL_MS,
): () => void {
  let stopped = false;
  let timer: ReturnType<typeof setTimeout> | undefined;

  const tick = async () => {
    try {
      const checkout = await fetchCheckout();
      if (stopped) return;
      onCheckout(checkout);
      if (isFinalStatus(checkout.status)) {
        stopped = true;
        return;
      }
    } catch (error) {
      if (stopped) return;
      onError(error);
    }
    if (!stopped) timer = setTimeout(tick, intervalMs);
  };

  void tick();

  return () => {
    stopped = true;
    if (timer !== undefined) clearTimeout(timer);
  };
}
