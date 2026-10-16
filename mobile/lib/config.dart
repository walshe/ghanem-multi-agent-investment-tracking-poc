/// Backend base URL. Override at build/run time:
///   flutter run -d chrome --web-port 5174 --dart-define=API_BASE_URL=http://127.0.0.1:4010
const String apiBaseUrl = String.fromEnvironment(
  'API_BASE_URL',
  defaultValue: 'http://localhost:8080',
);

/// How often the status screen asks the backend for the checkout status.
const Duration pollInterval = Duration(seconds: 2);

/// The app only offers euros for the PoC.
const String currency = 'EUR';

/// Smallest amount the contract allows (CreateCheckoutRequest.amountMinor minimum).
const int minAmountMinor = 100;
