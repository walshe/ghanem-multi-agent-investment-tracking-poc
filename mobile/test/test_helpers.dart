import 'dart:convert';

import 'package:checkout_mobile/api/api.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';

const checkoutId = '3fa85f64-5717-4562-b3fc-2c963f66afa6';

Map<String, dynamic> checkoutJson(
  String status, {
  int amountMinor = 10050,
  bool? refundRequested,
}) =>
    {
      'checkoutId': checkoutId,
      'status': status,
      'amountMinor': amountMinor,
      'currency': 'EUR',
      'paymentId': null,
      'refundRequested': ?refundRequested,
      'createdAt': '2026-10-16T10:00:00Z',
      'updatedAt': '2026-10-16T10:00:00Z',
    };

Checkout checkout(String status, {bool? refundRequested}) =>
    Checkout.fromJson(checkoutJson(status, refundRequested: refundRequested))!;

/// A CheckoutsApi backed by an in-memory HTTP handler.
CheckoutsApi fakeApi(Future<http.Response> Function(http.Request request) handler) {
  final client = ApiClient(basePath: 'http://backend.test');
  client.client = MockClient(handler);
  return CheckoutsApi(client);
}

http.Response jsonResponse(Object body, [int status = 200]) => http.Response(
      jsonEncode(body),
      status,
      headers: {'content-type': 'application/json'},
    );
