import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import App from './App';
import { CheckoutStatus } from './api';
import { CHECKOUT_ID, checkout } from './test/fixtures';

describe('App', () => {
  it('goes from the invest screen to the status screen for the new checkout', async () => {
    const api = {
      createCheckout: vi.fn().mockResolvedValue(checkout(CheckoutStatus.Pending)),
      getCheckout: vi.fn().mockResolvedValue(checkout(CheckoutStatus.Processing)),
    };
    const user = userEvent.setup();
    render(<App api={api} />);

    await user.type(screen.getByLabelText('Amount (EUR)'), '100.50');
    await user.click(screen.getByRole('button', { name: 'Invest' }));

    // The status screen shows the status from GET, not the one from the POST response.
    expect(await screen.findByRole('status')).toHaveTextContent('Your payment is being processed');
    expect(api.getCheckout).toHaveBeenCalledWith({ checkoutId: CHECKOUT_ID });
    expect(window.location.search).toBe(`?checkout=${CHECKOUT_ID}`);
  });
});
