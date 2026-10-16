import 'api/api.dart';

/// Final statuses never change (contract/states.md). Polling stops on these.
bool isFinalStatus(CheckoutStatus status) => switch (status) {
      CheckoutStatus.SUCCEEDED || CheckoutStatus.FAILED || CheckoutStatus.EXPIRED => true,
      CheckoutStatus.PENDING || CheckoutStatus.PROCESSING => false,
    };

/// Customer-facing text for the status the backend returned.
/// The app never decides or guesses a payment result; it only shows this.
String statusMessage(CheckoutStatus status) => switch (status) {
      CheckoutStatus.PENDING => 'Waiting for your payment',
      CheckoutStatus.PROCESSING => 'Your payment is being processed',
      CheckoutStatus.SUCCEEDED => 'Payment received. Your investment is confirmed.',
      CheckoutStatus.FAILED => 'Payment failed. You have not been charged.',
      CheckoutStatus.EXPIRED =>
        'This checkout expired. If you were charged, you will be refunded automatically.',
    };
