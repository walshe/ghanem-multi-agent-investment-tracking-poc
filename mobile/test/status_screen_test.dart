import 'package:checkout_mobile/api/api.dart';
import 'package:checkout_mobile/checkout_status_text.dart';
import 'package:checkout_mobile/screens/status_screen.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import 'test_helpers.dart';

const expectedMessages = {
  'PENDING': 'Waiting for your payment',
  'PROCESSING': 'Your payment is being processed',
  'SUCCEEDED': 'Payment received. Your investment is confirmed.',
  'FAILED': 'Payment failed. You have not been charged.',
  'EXPIRED': 'This checkout expired. If you were charged, you will be refunded automatically.',
};

void main() {
  test('every contract status has the agreed message', () {
    expect(CheckoutStatus.values.map((s) => s.toString()).toSet(),
        expectedMessages.keys.toSet());
    for (final status in CheckoutStatus.values) {
      expect(statusMessage(status), expectedMessages[status.toString()]);
    }
  });

  test('only SUCCEEDED, FAILED and EXPIRED are final', () {
    expect(CheckoutStatus.values.where(isFinalStatus).toSet(), {
      CheckoutStatus.SUCCEEDED,
      CheckoutStatus.FAILED,
      CheckoutStatus.EXPIRED,
    });
  });

  Future<int> pumpStatusScreen(
    WidgetTester tester, {
    required String initial,
    required List<String> backend,
  }) async {
    var calls = 0;
    final api = fakeApi((request) async {
      expect(request.method, 'GET');
      expect(request.url.path, '/checkouts/$checkoutId');
      final status = backend[calls < backend.length ? calls : backend.length - 1];
      calls++;
      return jsonResponse(checkoutJson(status));
    });
    await tester.pumpWidget(MaterialApp(
      home: StatusScreen(api: api, initial: checkout(initial)),
    ));
    return calls;
  }

  for (final entry in expectedMessages.entries) {
    testWidgets('shows the message for ${entry.key} returned by the backend', (tester) async {
      await pumpStatusScreen(tester, initial: 'PENDING', backend: [entry.key]);
      await tester.pump(const Duration(seconds: 2));
      await tester.pump();
      expect(find.text(entry.value), findsOneWidget);
      // Leave the screen so any remaining timer is cancelled.
      await tester.pumpWidget(const SizedBox());
    });
  }

  testWidgets('follows the backend from PENDING to PROCESSING to SUCCEEDED, then stops',
      (tester) async {
    var calls = 0;
    final responses = ['PENDING', 'PROCESSING', 'SUCCEEDED'];
    final api = fakeApi((request) async {
      final status = responses[calls < responses.length ? calls : responses.length - 1];
      calls++;
      return jsonResponse(checkoutJson(status));
    });
    await tester.pumpWidget(MaterialApp(home: StatusScreen(api: api, initial: checkout('PENDING'))));
    expect(find.text(expectedMessages['PENDING']!), findsOneWidget);

    await tester.pump(const Duration(seconds: 2));
    await tester.pump();
    expect(find.text(expectedMessages['PENDING']!), findsOneWidget);

    await tester.pump(const Duration(seconds: 2));
    await tester.pump();
    expect(find.text(expectedMessages['PROCESSING']!), findsOneWidget);

    await tester.pump(const Duration(seconds: 2));
    await tester.pump();
    expect(find.text(expectedMessages['SUCCEEDED']!), findsOneWidget);
    expect(calls, 3);

    await tester.pump(const Duration(seconds: 20));
    expect(calls, 3, reason: 'no polling after a final status');
  });

  testWidgets('a failed request keeps the last status and shows a connection note',
      (tester) async {
    var calls = 0;
    final api = fakeApi((request) async {
      calls++;
      if (calls == 1) return jsonResponse({'error': 'boom'}, 500);
      return jsonResponse(checkoutJson('FAILED'));
    });
    await tester.pumpWidget(MaterialApp(home: StatusScreen(api: api, initial: checkout('PROCESSING'))));

    await tester.pump(const Duration(seconds: 2));
    await tester.pump();
    expect(find.text(expectedMessages['PROCESSING']!), findsOneWidget);
    expect(find.byKey(const Key('connectionProblem')), findsOneWidget);

    await tester.pump(const Duration(seconds: 2));
    await tester.pump();
    expect(find.text(expectedMessages['FAILED']!), findsOneWidget);
    expect(find.byKey(const Key('connectionProblem')), findsNothing);
  });

  testWidgets('shows the refund note when the backend says a refund was requested',
      (tester) async {
    final api = fakeApi((_) async => throw StateError('must not poll a final checkout'));
    await tester.pumpWidget(MaterialApp(
      home: StatusScreen(api: api, initial: checkout('EXPIRED', refundRequested: true)),
    ));
    expect(find.text(expectedMessages['EXPIRED']!), findsOneWidget);
    expect(find.byKey(const Key('refundNote')), findsOneWidget);
    await tester.pump(const Duration(seconds: 10));
  });
}
