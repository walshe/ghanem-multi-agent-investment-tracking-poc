/// Money helpers. Money is always integer cents (minor units). No floating point here.
library;

final RegExp _amountPattern = RegExp(r'^(\d+)(?:[.,](\d{1,2}))?$');

/// Converts a euro amount typed by the user into integer cents using string
/// parsing only, so there is no floating-point rounding.
///
/// "100.50" -> 10050, "100.5" -> 10050, "100" -> 10000, "0.07" -> 7.
/// A comma is accepted as the decimal separator too ("100,50").
///
/// Returns null if the input is not a plain non-negative amount with at most
/// two decimals (e.g. "", "abc", "-1", "1.234", "1e3", "1,000.00").
int? eurosToCents(String input) {
  final match = _amountPattern.firstMatch(input.trim());
  if (match == null) return null;

  final euros = int.tryParse(match.group(1)!);
  if (euros == null) return null;

  final fraction = (match.group(2) ?? '').padRight(2, '0');
  final cents = int.parse(fraction);

  final total = euros * 100 + cents;
  // Guard against overflow / loss of precision (web ints are JS numbers).
  if (total < 0 || total > _maxSafeCents) return null;
  return total;
}

/// Largest integer exactly representable on every Flutter platform (2^53 - 1).
const int _maxSafeCents = 9007199254740991;

/// Formats integer cents for display, e.g. (10050, "EUR") -> "EUR 100.50".
String formatMoney(int amountMinor, String currency) {
  final negative = amountMinor < 0;
  final abs = amountMinor.abs();
  final euros = abs ~/ 100;
  final cents = (abs % 100).toString().padLeft(2, '0');
  return '$currency ${negative ? '-' : ''}$euros.$cents';
}
