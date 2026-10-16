
# PaymentEvent


## Properties

Name | Type
------------ | -------------
`eventId` | string
`checkoutId` | string
`paymentId` | string
`status` | [PaymentEventStatus](PaymentEventStatus.md)
`amountMinor` | number
`currency` | string
`occurredAt` | Date

## Example

```typescript
import type { PaymentEvent } from ''

// TODO: Update the object below with actual values
const example = {
  "eventId": null,
  "checkoutId": null,
  "paymentId": null,
  "status": null,
  "amountMinor": null,
  "currency": null,
  "occurredAt": null,
} satisfies PaymentEvent

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as PaymentEvent
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


