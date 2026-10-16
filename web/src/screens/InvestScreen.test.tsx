import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { CheckoutStatus } from '../api';
import { checkout } from '../test/fixtures';
import { InvestScreen } from './InvestScreen';

function setup(createCheckout = vi.fn().mockResolvedValue(checkout(CheckoutStatus.Pending))) {
  const onCreated = vi.fn();
  render(<InvestScreen api={{ createCheckout }} onCreated={onCreated} />);
  return { createCheckout, onCreated, user: userEvent.setup() };
}

describe('InvestScreen', () => {
  it('sends the amount as integer cents in EUR and reports the created checkout', async () => {
    const { createCheckout, onCreated, user } = setup();
    await user.type(screen.getByLabelText('Amount (EUR)'), '100.50');
    await user.click(screen.getByRole('button', { name: 'Invest' }));

    expect(createCheckout).toHaveBeenCalledWith({
      createCheckoutRequest: { amountMinor: 10050, currency: 'EUR' },
    });
    expect(onCreated).toHaveBeenCalledWith(expect.objectContaining({ checkoutId: checkout(CheckoutStatus.Pending).checkoutId }));
  });

  it('rejects an invalid amount without calling the backend', async () => {
    const { createCheckout, user } = setup();
    await user.type(screen.getByLabelText('Amount (EUR)'), '12.345');
    await user.click(screen.getByRole('button', { name: 'Invest' }));
    expect(screen.getByRole('alert')).toHaveTextContent('Enter an amount in euros');
    expect(createCheckout).not.toHaveBeenCalled();
  });

  it('rejects amounts below the EUR 1.00 minimum', async () => {
    const { createCheckout, user } = setup();
    await user.type(screen.getByLabelText('Amount (EUR)'), '0.99');
    await user.click(screen.getByRole('button', { name: 'Invest' }));
    expect(screen.getByRole('alert')).toHaveTextContent('minimum');
    expect(createCheckout).not.toHaveBeenCalled();
  });

  it('shows an error and stays on the screen if the backend call fails', async () => {
    const { onCreated, user } = setup(vi.fn().mockRejectedValue(new Error('boom')));
    await user.type(screen.getByLabelText('Amount (EUR)'), '50');
    await user.click(screen.getByRole('button', { name: 'Invest' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('could not start');
    expect(onCreated).not.toHaveBeenCalled();
    expect(screen.getByRole('button', { name: 'Invest' })).toBeEnabled();
  });
});
