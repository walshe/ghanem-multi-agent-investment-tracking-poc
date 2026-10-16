import 'package:checkout_mobile/money.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  group('eurosToCents', () {
    final valid = <String, int>{
      '100.50': 10050,
      '100.5': 10050,
      '100': 10000,
      '1': 100,
      '0.07': 7,
      '0.1': 10,
      '0.29': 29, // 0.29 * 100 is 28.999999999999996 in floating point
      '1.15': 115, // 1.15 * 100 is 114.99999999999999 in floating point
      '19.99': 1999,
      '100,50': 10050,
      ' 42.00 ': 4200,
      '007.10': 710,
      '123456789.99': 12345678999,
    };
    valid.forEach((input, cents) {
      test('"$input" -> $cents', () => expect(eurosToCents(input), cents));
    });

    const invalid = [
      '', ' ', 'abc', '-1', '-1.00', '+1', '1.234', '1.', '.5', '1e3',
      '1,000.00', '1 000', '€10', '10€', 'NaN', 'Infinity', '1.2.3',
      '99999999999999999999',
    ];
    for (final input in invalid) {
      test('"$input" is rejected', () => expect(eurosToCents(input), isNull));
    }
  });

  group('formatMoney', () {
    test('formats cents with two decimals', () {
      expect(formatMoney(10050, 'EUR'), 'EUR 100.50');
      expect(formatMoney(7, 'EUR'), 'EUR 0.07');
      expect(formatMoney(10000, 'EUR'), 'EUR 100.00');
    });
  });
}
