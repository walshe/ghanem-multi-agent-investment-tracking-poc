import { act, render, screen } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { CheckoutStatus, ResponseError, type Checkout } from '../api';
import { CHECKOUT_ID, checkout } from '../test/fixtures';
import { StatusScreen } from './StatusScreen';

const EXPECTED_MESSAGES: Array<[CheckoutStatus, string]> = [
  [CheckoutStatus.Pending, 'Waiting for your payment'],
  [CheckoutStatus.Processing, 'Your payment is being processed'],
  [CheckoutStatus.Succeeded, 'Payment received. Your investment is confirmed.'],
  [CheckoutStatus.Failed, 'Payment failed. You have not been charged.'],
  [
    CheckoutStatus.Expired,
    'This checkout expired. If you were charged, you will be refunded automatically.',
  ],
];

function renderWith(getCheckout: () => Promise<Checkout>) {
  const api = { getCheckout: vi.fn(getCheckout) };
  render(<StatusScreen api={api} checkoutId={CHECKOUT_ID} onStartAgain={vi.fn()} />);
  return api;
}

const flush = () => act(() => vi.advanceTimersByTimeAsync(0));
const advance = (ms: number) => act(() => vi.advanceTimersByTimeAsync(ms));

describe('StatusScreen', () => {
  beforeEach(() => vi.useFakeTimers());
  afterEach(() => vi.useRealTimers());

  it.each(EXPECTED_MESSAGES)('shows the message for %s', async (status, message) => {
    renderWith(() => Promise.resolve(checkout(status)));
    await flush();
    expect(screen.getByRole('status').textContent).toBe(message);
  });

  it('requests the checkout by id and shows its amount', async () => {
    const api = renderWith(() => Promise.resolve(checkout(CheckoutStatus.Pending)));
    await flush();
    expect(api.getCheckout).toHaveBeenCalledWith({ checkoutId: CHECKOUT_ID });
    expect(screen.getByTestId('amount')).toHaveTextContent('EUR 100.50');
  });

  it('follows the backend from PENDING through PROCESSING to SUCCEEDED, then stops', async () => {
    const responses = [CheckoutStatus.Pending, CheckoutStatus.Processing, CheckoutStatus.Succeeded];
    let i = 0;
    const api = renderWith(() => Promise.resolve(checkout(responses[Math.min(i++, 2)])));

    await flush();
    expect(screen.getByRole('status')).toHaveTextContent('Waiting for your payment');
    await advance(2000);
    expect(screen.getByRole('status')).toHaveTextContent('Your payment is being processed');
    await advance(2000);
    expect(screen.getByRole('status')).toHaveTextContent('Payment received.');
    expect(screen.getByRole('button', { name: 'Make another investment' })).toBeInTheDocument();

    await advance(20_000);
    expect(api.getCheckout).toHaveBeenCalledTimes(3);
  });

  it('shows whatever the backend returns, even PENDING straight to FAILED', async () => {
    const responses = [CheckoutStatus.Pending, CheckoutStatus.Failed];
    let i = 0;
    renderWith(() => Promise.resolve(checkout(responses[Math.min(i++, 1)])));
    await flush();
    await advance(2000);
    expect(screen.getByRole('status')).toHaveTextContent('Payment failed.');
  });

  it('keeps the last known status and keeps polling when the backend is unreachable', async () => {
    let call = 0;
    const api = renderWith(() => {
      call++;
      if (call === 1) return Promise.resolve(checkout(CheckoutStatus.Processing));
      if (call === 2) return Promise.reject(new TypeError('Failed to fetch'));
      return Promise.resolve(checkout(CheckoutStatus.Succeeded));
    });

    await flush();
    await advance(2000);
    expect(screen.getByRole('status')).toHaveTextContent('Your payment is being processed');
    expect(screen.getByRole('alert')).toHaveTextContent("can't reach the server");
    expect(screen.queryByRole('button')).not.toBeInTheDocument();

    await advance(2000);
    expect(screen.getByRole('status')).toHaveTextContent('Payment received.');
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
    expect(api.getCheckout).toHaveBeenCalledTimes(3);
  });

  it('shows no status before the backend has answered', async () => {
    renderWith(() => new Promise<Checkout>(() => {}));
    await flush();
    expect(screen.queryByRole('status')).not.toBeInTheDocument();
    expect(screen.getByText('Loading your checkout…')).toBeInTheDocument();
  });

  it('stops polling on 404', async () => {
    const api = renderWith(() =>
      Promise.reject(new ResponseError(new Response(null, { status: 404 }))),
    );
    await flush();
    expect(screen.getByRole('alert')).toHaveTextContent('could not find this checkout');
    await advance(20_000);
    expect(api.getCheckout).toHaveBeenCalledTimes(1);
  });
});
