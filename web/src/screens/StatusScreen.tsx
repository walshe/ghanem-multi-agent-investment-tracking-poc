import { useEffect, useState } from 'react';
import { ResponseError, type Checkout, type CheckoutsApi } from '../api';
import { formatCents } from '../lib/money';
import { POLL_INTERVAL_MS, pollCheckout } from '../lib/poll';
import { STATUS_MESSAGES, isFinalStatus } from '../lib/status';

interface Props {
  api: Pick<CheckoutsApi, 'getCheckout'>;
  checkoutId: string;
  onStartAgain: () => void;
  pollIntervalMs?: number;
}

type LoadError = 'not-found' | 'unavailable' | null;

/**
 * Shows exactly the status the backend returns. It never decides or guesses a result:
 * if the backend can't be reached, the last known status stays on screen and polling
 * continues.
 */
export function StatusScreen({
  api,
  checkoutId,
  onStartAgain,
  pollIntervalMs = POLL_INTERVAL_MS,
}: Props) {
  const [checkout, setCheckout] = useState<Checkout | null>(null);
  const [loadError, setLoadError] = useState<LoadError>(null);

  // Render with key={checkoutId} so state starts fresh for each checkout.
  useEffect(() => {
    const stop = pollCheckout(
      () => api.getCheckout({ checkoutId }),
      {
        onCheckout: (c) => {
          setCheckout(c);
          setLoadError(null);
        },
        onError: (error) => {
          if (error instanceof ResponseError && error.response.status === 404) {
            setLoadError('not-found');
            stop();
          } else {
            setLoadError('unavailable');
          }
        },
      },
      pollIntervalMs,
    );
    return stop;
  }, [api, checkoutId, pollIntervalMs]);

  const final = checkout !== null && isFinalStatus(checkout.status);

  return (
    <section>
      <h1>Your investment</h1>
      {checkout && (
        <p data-testid="amount">{formatCents(checkout.amountMinor, checkout.currency)}</p>
      )}

      {loadError === 'not-found' ? (
        <p role="alert">We could not find this checkout.</p>
      ) : checkout ? (
        <p role="status" data-status={checkout.status}>
          {STATUS_MESSAGES[checkout.status]}
        </p>
      ) : (
        <p>Loading your checkout…</p>
      )}

      {loadError === 'unavailable' && (
        <p role="alert">We can't reach the server right now. Still checking…</p>
      )}

      {(final || loadError === 'not-found') && (
        <button type="button" onClick={onStartAgain}>
          Make another investment
        </button>
      )}
    </section>
  );
}
