# CallbacksApi

All URIs are relative to *http://localhost:8080*

| Method | HTTP request | Description |
|------------- | ------------- | -------------|
| [**receivePaymentCallback**](CallbacksApi.md#receivepaymentcallback) | **POST** /callbacks | Payment status callback from the provider |



## receivePaymentCallback

> receivePaymentCallback(xSignature, paymentEvent)

Payment status callback from the provider

Processed exactly as described in states.md.

### Example

```ts
import {
  Configuration,
  CallbacksApi,
} from '';
import type { ReceivePaymentCallbackRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const api = new CallbacksApi();

  const body = {
    // string | Lowercase hex HMAC-SHA256 of the raw request body, keyed with the shared signing secret.
    xSignature: xSignature_example,
    // PaymentEvent
    paymentEvent: ...,
  } satisfies ReceivePaymentCallbackRequest;

  try {
    const data = await api.receivePaymentCallback(body);
    console.log(data);
  } catch (error) {
    console.error(error);
  }
}

// Run the test
example().catch(console.error);
```

### Parameters


| Name | Type | Description  | Notes |
|------------- | ------------- | ------------- | -------------|
| **xSignature** | `string` | Lowercase hex HMAC-SHA256 of the raw request body, keyed with the shared signing secret. | [Defaults to `undefined`] |
| **paymentEvent** | [PaymentEvent](PaymentEvent.md) |  | |

### Return type

`void` (Empty response body)

### Authorization

No authorization required

### HTTP request headers

- **Content-Type**: `application/json`
- **Accept**: Not defined


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
| **200** | Received. Also returned for duplicates, stale events and flagged events, so the provider stops retrying. |  -  |
| **401** | Missing or invalid signature. Nothing was stored. |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)

