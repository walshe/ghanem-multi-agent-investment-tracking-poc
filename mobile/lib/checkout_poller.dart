import 'dart:async';

import 'api/api.dart';
import 'checkout_status_text.dart';

/// Polls GET /checkouts/{id} every [interval] until the backend reports a final status.
///
/// Requests never overlap: the next one is scheduled only after the previous one has
/// finished, so an old response can never overwrite a newer one.
/// Errors are reported but do not change the status; polling simply carries on.
class CheckoutPoller {
  CheckoutPoller({
    required this.fetch,
    required this.onCheckout,
    this.onError,
    this.interval = const Duration(seconds: 2),
  });

  final Future<Checkout?> Function() fetch;
  final void Function(Checkout checkout) onCheckout;
  final void Function(Object error)? onError;
  final Duration interval;

  Timer? _timer;
  bool _running = false;

  bool get isRunning => _running;

  /// Starts polling. Does nothing if [current] is already final.
  void start(CheckoutStatus current) {
    if (_running || isFinalStatus(current)) return;
    _running = true;
    _schedule();
  }

  void stop() {
    _running = false;
    _timer?.cancel();
    _timer = null;
  }

  void _schedule() {
    _timer = Timer(interval, _tick);
  }

  Future<void> _tick() async {
    if (!_running) return;
    try {
      final checkout = await fetch();
      if (!_running) return;
      if (checkout != null) {
        onCheckout(checkout);
        if (isFinalStatus(checkout.status)) {
          stop();
          return;
        }
      }
    } catch (error) {
      if (!_running) return;
      onError?.call(error);
    }
    if (_running) _schedule();
  }
}
