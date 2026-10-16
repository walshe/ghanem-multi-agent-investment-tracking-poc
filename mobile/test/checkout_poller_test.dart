import 'package:checkout_mobile/api/api.dart';
import 'package:checkout_mobile/checkout_poller.dart';
import 'package:fake_async/fake_async.dart';
import 'package:flutter_test/flutter_test.dart';

import 'test_helpers.dart';

void main() {
  const interval = Duration(seconds: 2);

  /// Runs a poller whose backend returns [responses] in order (the last one repeats).
  ({int calls, List<CheckoutStatus> seen, bool running}) run(
    List<String> responses, {
    Duration elapse = const Duration(seconds: 30),
    String start = 'PENDING',
  }) {
    var calls = 0;
    final seen = <CheckoutStatus>[];
    late CheckoutPoller poller;
    fakeAsync((async) {
      poller = CheckoutPoller(
        interval: interval,
        fetch: () async {
          final status = responses[calls < responses.length ? calls : responses.length - 1];
          calls++;
          return checkout(status);
        },
        onCheckout: (c) => seen.add(c.status),
      );
      poller.start(CheckoutStatus.fromJson(start)!);
      async.elapse(elapse);
    });
    return (calls: calls, seen: seen, running: poller.isRunning);
  }

  for (final finalStatus in ['SUCCEEDED', 'FAILED', 'EXPIRED']) {
    test('stops polling once the backend returns $finalStatus', () {
      final r = run(['PENDING', 'PROCESSING', finalStatus]);
      expect(r.calls, 3);
      expect(r.seen.last.toString(), finalStatus);
      expect(r.running, isFalse);
    });

    test('does not poll at all if the checkout is already $finalStatus', () {
      final r = run([finalStatus], start: finalStatus);
      expect(r.calls, 0);
      expect(r.running, isFalse);
    });
  }

  test('keeps polling every 2 seconds while the status is not final', () {
    final r = run(['PENDING', 'PROCESSING'], elapse: const Duration(seconds: 10));
    expect(r.calls, 5); // at 2, 4, 6, 8 and 10 seconds
    expect(r.running, isTrue);
  });

  test('does not call the backend before the first interval has passed', () {
    final r = run(['PENDING'], elapse: const Duration(milliseconds: 1999));
    expect(r.calls, 0);
  });

  test('keeps polling after an error, without changing the status', () {
    var calls = 0;
    final seen = <CheckoutStatus>[];
    var errors = 0;
    fakeAsync((async) {
      final poller = CheckoutPoller(
        interval: interval,
        fetch: () async {
          calls++;
          if (calls == 1) throw Exception('network down');
          return checkout('SUCCEEDED');
        },
        onCheckout: (c) => seen.add(c.status),
        onError: (_) => errors++,
      );
      poller.start(CheckoutStatus.PENDING);
      async.elapse(const Duration(seconds: 20));
      expect(poller.isRunning, isFalse);
    });
    expect(errors, 1);
    expect(calls, 2);
    expect(seen, [CheckoutStatus.SUCCEEDED]);
  });

  test('stop() cancels polling', () {
    var calls = 0;
    fakeAsync((async) {
      final poller = CheckoutPoller(
        interval: interval,
        fetch: () async {
          calls++;
          return checkout('PENDING');
        },
        onCheckout: (_) {},
      );
      poller.start(CheckoutStatus.PENDING);
      async.elapse(const Duration(seconds: 4));
      poller.stop();
      async.elapse(const Duration(seconds: 20));
    });
    expect(calls, 2);
  });
}
