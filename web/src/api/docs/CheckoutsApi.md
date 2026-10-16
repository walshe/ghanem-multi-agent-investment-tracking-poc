# CheckoutsApi

All URIs are relative to *http://localhost:8080*

| Method | HTTP request | Description |
|------------- | ------------- | -------------|
| [**createCheckout**](CheckoutsApi.md#createcheckoutoperation) | **POST** /checkouts | Start an investment checkout |
| [**getCheckout**](CheckoutsApi.md#getcheckout) | **GET** /checkouts/{checkoutId} | Get the current status of a checkout |



## createCheckout

> Checkout createCheckout(createCheckoutRequest)

Start an investment checkout

### Example

```ts
import {
  Configuration,
  CheckoutsApi,
} from '';
import type { CreateCheckoutOperationRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const api = new CheckoutsApi();

  const body = {
    // CreateCheckoutRequest
    createCheckoutRequest: ...,
  } satisfies CreateCheckoutOperationRequest;

  try {
    const data = await api.createCheckout(body);
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
| **createCheckoutRequest** | [CreateCheckoutRequest](CreateCheckoutRequest.md) |  | |

### Return type

[**Checkout**](Checkout.md)

### Authorization

No authorization required

### HTTP request headers

- **Content-Type**: `application/json`
- **Accept**: `application/json`


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
| **201** | Checkout created. Status is PENDING. |  -  |
| **400** | Invalid amount or currency. |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


## getCheckout

> Checkout getCheckout(checkoutId)

Get the current status of a checkout

### Example

```ts
import {
  Configuration,
  CheckoutsApi,
} from '';
import type { GetCheckoutRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const api = new CheckoutsApi();

  const body = {
    // string
    checkoutId: 38400000-8cf0-11bd-b23e-10b96e4ef00d,
  } satisfies GetCheckoutRequest;

  try {
    const data = await api.getCheckout(body);
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

[**Checkout**](Checkout.md)

### Authorization

No authorization required

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: `application/json`


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
| **200** | The checkout. |  -  |
| **404** | No checkout with this id. |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)

