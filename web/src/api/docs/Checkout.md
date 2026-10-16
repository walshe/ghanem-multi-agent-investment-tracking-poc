
# Checkout


## Properties

Name | Type
------------ | -------------
`checkoutId` | string
`status` | [CheckoutStatus](CheckoutStatus.md)
`amountMinor` | number
`currency` | string
`paymentId` | string
`refundRequested` | boolean
`createdAt` | Date
`updatedAt` | Date

## Example

```typescript
import type { Checkout } from ''

// TODO: Update the object below with actual values
const example = {
  "checkoutId": null,
  "status": null,
  "amountMinor": null,
  "currency": null,
  "paymentId": null,
  "refundRequested": null,
  "createdAt": null,
  "updatedAt": null,
} satisfies Checkout

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as Checkout
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


