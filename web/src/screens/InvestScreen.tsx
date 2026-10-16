import { useState, type FormEvent } from 'react';
import type { Checkout, CheckoutsApi } from '../api';
import { eurosToCents } from '../lib/money';

export const CURRENCY = 'EUR';
/** Contract minimum for CreateCheckoutRequest.amountMinor. */
export const MIN_AMOUNT_MINOR = 100;

interface Props {
  api: Pick<CheckoutsApi, 'createCheckout'>;
  onCreated: (checkout: Checkout) => void;
}

export function InvestScreen({ api, onCreated }: Props) {
  const [amount, setAmount] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const handleSubmit = async (event: FormEvent) => {
    event.preventDefault();
    if (submitting) return;

    const amountMinor = eurosToCents(amount);
    if (amountMinor === null) {
      setError('Enter an amount in euros, for example 100.50.');
      return;
    }
    if (amountMinor < MIN_AMOUNT_MINOR) {
      setError('The minimum investment is EUR 1.00.');
      return;
    }

    setError(null);
    setSubmitting(true);
    try {
      const checkout = await api.createCheckout({
        createCheckoutRequest: { amountMinor, currency: CURRENCY },
      });
      onCreated(checkout);
    } catch {
      setError('We could not start your investment. Please try again.');
      setSubmitting(false);
    }
  };

  return (
    <form onSubmit={handleSubmit} noValidate>
      <h1>Invest</h1>
      <label htmlFor="amount">Amount (EUR)</label>
      <input
        id="amount"
        name="amount"
        type="text"
        inputMode="decimal"
        autoComplete="off"
        placeholder="100.00"
        value={amount}
        onChange={(e) => setAmount(e.target.value)}
        disabled={submitting}
      />
      <button type="submit" disabled={submitting}>
        {submitting ? 'Starting…' : 'Invest'}
      </button>
      {error && <p role="alert">{error}</p>}
    </form>
  );
}
