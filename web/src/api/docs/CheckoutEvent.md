
# CheckoutEvent


## Properties

Name | Type
------------ | -------------
`eventId` | string
`status` | [PaymentEventStatus](PaymentEventStatus.md)
`amountMinor` | number
`outcome` | [EventOutcome](EventOutcome.md)
`receivedAt` | Date

## Example

```typescript
import type { CheckoutEvent } from ''

// TODO: Update the object below with actual values
const example = {
  "eventId": null,
  "status": null,
  "amountMinor": null,
  "outcome": null,
  "receivedAt": null,
} satisfies CheckoutEvent

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as CheckoutEvent
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


