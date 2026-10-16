# AuditApi

All URIs are relative to *http://localhost:8080*

| Method | HTTP request | Description |
|------------- | ------------- | -------------|
| [**getCheckoutEvents**](AuditApi.md#getcheckoutevents) | **GET** /checkouts/{checkoutId}/events | Every payment event received for this checkout, and what was done with it |



## getCheckoutEvents

> Array&lt;CheckoutEvent&gt; getCheckoutEvents(checkoutId)

Every payment event received for this checkout, and what was done with it

### Example

```ts
import {
  Configuration,
  AuditApi,
} from '';
import type { GetCheckoutEventsRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const api = new AuditApi();

  const body = {
    // string
    checkoutId: 38400000-8cf0-11bd-b23e-10b96e4ef00d,
  } satisfies GetCheckoutEventsRequest;

  try {
    const data = await api.getCheckoutEvents(body);
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
| **checkoutId** | `string` |  | [Defaults to `undefined`] |

### Return type

[**Array&lt;CheckoutEvent&gt;**](CheckoutEvent.md)

### Authorization

No authorization required

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: `application/json`


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
| **200** | Events, oldest first. |  -  |
| **404** | No checkout with this id. |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)

