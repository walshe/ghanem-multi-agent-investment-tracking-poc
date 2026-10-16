import { useMemo, useState } from 'react';
import type { CheckoutsApi } from './api';
import { createCheckoutsApi } from './lib/config';
import { InvestScreen } from './screens/InvestScreen';
import { StatusScreen } from './screens/StatusScreen';

const CHECKOUT_PARAM = 'checkout';

function readCheckoutIdFromUrl(): string | null {
  return new URLSearchParams(window.location.search).get(CHECKOUT_PARAM);
}

function writeCheckoutIdToUrl(checkoutId: string | null) {
  const url = new URL(window.location.href);
  if (checkoutId) url.searchParams.set(CHECKOUT_PARAM, checkoutId);
  else url.searchParams.delete(CHECKOUT_PARAM);
  window.history.pushState(null, '', url);
}

interface Props {
  api?: Pick<CheckoutsApi, 'createCheckout' | 'getCheckout'>;
}

export default function App({ api: injectedApi }: Props) {
  const api = useMemo(() => injectedApi ?? createCheckoutsApi(), [injectedApi]);
  // The checkout id lives in the URL so a page refresh keeps showing its status.
  const [checkoutId, setCheckoutId] = useState<string | null>(readCheckoutIdFromUrl);

  const show = (id: string | null) => {
    writeCheckoutIdToUrl(id);
    setCheckoutId(id);
  };

  return (
    <main>
      {checkoutId ? (
        <StatusScreen
          key={checkoutId}
          api={api} checkoutId={checkoutId} onStartAgain={() => show(null)} />
      ) : (
        <InvestScreen api={api} onCreated={(checkout) => show(checkout.checkoutId)} />
      )}
    </main>
  );
}
