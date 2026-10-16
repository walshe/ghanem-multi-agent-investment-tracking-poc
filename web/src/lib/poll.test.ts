import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { CheckoutStatus, type Checkout } from '../api';
import { checkout } from '../test/fixtures';
import { POLL_INTERVAL_MS, pollCheckout } from './poll';

function sequence(...items: Array<Checkout | Error>) {
  const fn = vi.fn<() => Promise<Checkout>>();
  for (const item of items) {
    if (item instanceof Error) fn.mockRejectedValueOnce(item);
    else fn.mockResolvedValueOnce(item);
  }
  // Anything after the scripted responses: still pending.
  fn.mockResolvedValue(checkout(CheckoutStatus.Pending));
  return fn;
}

describe('pollCheckout', () => {
  beforeEach(() => vi.useFakeTimers());
  afterEach(() => vi.useRealTimers());

  it('polls every 2 seconds', async () => {
    expect(POLL_INTERVAL_MS).toBe(2000);
    const fetch = sequence();
    const stop = pollCheckout(fetch, { onCheckout: vi.fn(), onError: vi.fn() });

    await vi.advanceTimersByTimeAsync(0);
    expect(fetch).toHaveBeenCalledTimes(1);
    await vi.advanceTimersByTimeAsync(1999);
    expect(fetch).toHaveBeenCalledTimes(1);
    await vi.advanceTimersByTimeAsync(1);
    expect(fetch).toHaveBeenCalledTimes(2);
    await vi.advanceTimersByTimeAsync(2000);
    expect(fetch).toHaveBeenCalledTimes(3);
    stop();
  });

  it.each([CheckoutStatus.Succeeded, CheckoutStatus.Failed, CheckoutStatus.Expired])(
    'stops polling once the status is %s',
    async (finalStatus) => {
      const fetch = sequence(
        checkout(CheckoutStatus.Pending),
        checkout(CheckoutStatus.Processing),
        checkout(finalStatus),
      );
      const onCheckout = vi.fn();
      pollCheckout(fetch, { onCheckout, onError: vi.fn() });

      await vi.advanceTimersByTimeAsync(4000);
      expect(fetch).toHaveBeenCalledTimes(3);
      expect(onCheckout).toHaveBeenLastCalledWith(
        expect.objectContaining({ status: finalStatus }),
      );

      await vi.advanceTimersByTimeAsync(60_000);
      expect(fetch).toHaveBeenCalledTimes(3);
      expect(vi.getTimerCount()).toBe(0);
    },
  );

  it.each([CheckoutStatus.Pending, CheckoutStatus.Processing])(
    'keeps polling while the status is %s',
    async (status) => {
      const fetch = vi.fn().mockResolvedValue(checkout(status));
      const stop = pollCheckout(fetch, { onCheckout: vi.fn(), onError: vi.fn() });
      await vi.advanceTimersByTimeAsync(10_000);
      expect(fetch).toHaveBeenCalledTimes(6);
      stop();
    },
  );

  it('keeps polling after an error and reports it, without inventing a status', async () => {
    const fetch = sequence(new Error('network'), checkout(CheckoutStatus.Succeeded));
    const onCheckout = vi.fn();
    const onError = vi.fn();
    pollCheckout(fetch, { onCheckout, onError });

    await vi.advanceTimersByTimeAsync(0);
    expect(onError).toHaveBeenCalledTimes(1);
    expect(onCheckout).not.toHaveBeenCalled();

    await vi.advanceTimersByTimeAsync(2000);
    expect(onCheckout).toHaveBeenCalledWith(
      expect.objectContaining({ status: CheckoutStatus.Succeeded }),
    );
    await vi.advanceTimersByTimeAsync(10_000);
    expect(fetch).toHaveBeenCalledTimes(2);
  });

  it('does not start a new request while one is in flight', async () => {
    let resolve!: (c: Checkout) => void;
    const fetch = vi.fn(() => new Promise<Checkout>((r) => (resolve = r)));
    const stop = pollCheckout(fetch, { onCheckout: vi.fn(), onError: vi.fn() });

    await vi.advanceTimersByTimeAsync(10_000);
    expect(fetch).toHaveBeenCalledTimes(1);
    resolve(checkout(CheckoutStatus.Pending));
    await vi.advanceTimersByTimeAsync(2000);
    expect(fetch).toHaveBeenCalledTimes(2);
    stop();
  });

  it('stop() cancels polling and ignores a response that arrives afterwards', async () => {
    let resolve!: (c: Checkout) => void;
    const fetch = vi.fn(() => new Promise<Checkout>((r) => (resolve = r)));
    const onCheckout = vi.fn();
    const stop = pollCheckout(fetch, { onCheckout, onError: vi.fn() });

    stop();
    resolve(checkout(CheckoutStatus.Succeeded));
    await vi.advanceTimersByTimeAsync(10_000);
    expect(onCheckout).not.toHaveBeenCalled();
    expect(fetch).toHaveBeenCalledTimes(1);
  });
});
