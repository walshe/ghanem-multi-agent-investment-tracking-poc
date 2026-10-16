import 'dart:convert';

import 'package:checkout_mobile/main.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;

import 'test_helpers.dart';

void main() {
  testWidgets('invest 100.50 posts 10050 cents and shows the backend status', (tester) async {
    final requests = <http.Request>[];
    var gets = 0;
    final api = fakeApi((request) async {
      requests.add(request);
      if (request.method == 'POST' && request.url.path == '/checkouts') {
        final body = jsonDecode(request.body) as Map<String, dynamic>;
        return jsonResponse(checkoutJson('PENDING', amountMinor: body['amountMinor'] as int), 201);
      }
      if (request.method == 'GET' && request.url.path == '/checkouts/$checkoutId') {
        gets++;
        return jsonResponse(checkoutJson(gets < 2 ? 'PROCESSING' : 'SUCCEEDED'));
      }
      return http.Response('not found', 404);
    });

    await tester.pumpWidget(CheckoutApp(api: api));
    await tester.enterText(find.byKey(const Key('amountField')), '100.50');
    await tester.tap(find.byKey(const Key('investButton')));
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 500)); // route transition

    final post = requests.firstWhere((r) => r.method == 'POST');
    expect(requests.where((r) => r.method == 'POST'), hasLength(1));
    expect(post.method, 'POST');
    expect(jsonDecode(post.body), {'amountMinor': 10050, 'currency': 'EUR'});

    expect(find.text('Waiting for your payment'), findsOneWidget);
    expect(find.text('EUR 100.50'), findsOneWidget);

    await tester.pump(const Duration(seconds: 2));
    await tester.pump();
    expect(find.text('Your payment is being processed'), findsOneWidget);

    await tester.pump(const Duration(seconds: 2));
    await tester.pump();
    expect(find.text('Payment received. Your investment is confirmed.'), findsOneWidget);
  });

  testWidgets('rejects an invalid amount without calling the backend', (tester) async {
    var calls = 0;
    final api = fakeApi((_) async {
      calls++;
      return http.Response('', 500);
    });
    await tester.pumpWidget(CheckoutApp(api: api));

    for (final input in ['abc', '1.234', '0.99']) {
      await tester.enterText(find.byKey(const Key('amountField')), input);
      await tester.tap(find.byKey(const Key('investButton')));
      await tester.pump();
    }
    expect(calls, 0);
    expect(find.textContaining('minimum'), findsOneWidget);
  });

  testWidgets('a backend error stays on the invest screen', (tester) async {
    final api = fakeApi((_) async => http.Response('', 400));
    await tester.pumpWidget(CheckoutApp(api: api));
    await tester.enterText(find.byKey(const Key('amountField')), '100');
    await tester.tap(find.byKey(const Key('investButton')));
    await tester.pumpAndSettle();
    expect(find.byKey(const Key('investButton')), findsOneWidget);
    expect(find.textContaining('not accepted'), findsOneWidget);
  });
}
