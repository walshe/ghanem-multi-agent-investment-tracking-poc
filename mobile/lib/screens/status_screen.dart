import 'package:flutter/material.dart';

import '../api/api.dart';
import '../checkout_poller.dart';
import '../checkout_status_text.dart';
import '../money.dart';

/// Shows the checkout status exactly as the backend reports it, polling until final.
class StatusScreen extends StatefulWidget {
  const StatusScreen({
    super.key,
    required this.api,
    required this.initial,
    this.pollInterval = const Duration(seconds: 2),
  });

  final CheckoutsApi api;
  final Checkout initial;
  final Duration pollInterval;

  @override
  State<StatusScreen> createState() => _StatusScreenState();
}

class _StatusScreenState extends State<StatusScreen> {
  late Checkout _checkout = widget.initial;
  bool _connectionProblem = false;
  late final CheckoutPoller _poller;

  @override
  void initState() {
    super.initState();
    _poller = CheckoutPoller(
      interval: widget.pollInterval,
      fetch: () => widget.api.getCheckout(widget.initial.checkoutId),
      onCheckout: (checkout) => setState(() {
        _checkout = checkout;
        _connectionProblem = false;
      }),
      // A failed request says nothing about the payment. Keep the last status and retry.
      onError: (_) => setState(() => _connectionProblem = true),
    );
    _poller.start(_checkout.status);
  }

  @override
  void dispose() {
    _poller.stop();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final status = _checkout.status;
    final isFinal = isFinalStatus(status);
    final theme = Theme.of(context);

    return Scaffold(
      appBar: AppBar(title: const Text('Checkout status')),
      body: Center(
        child: ConstrainedBox(
          constraints: const BoxConstraints(maxWidth: 420),
          child: Padding(
            padding: const EdgeInsets.all(24),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                if (!isFinal) ...[
                  const CircularProgressIndicator(),
                  const SizedBox(height: 24),
                ],
                Text(
                  statusMessage(status),
                  key: const Key('statusMessage'),
                  style: theme.textTheme.titleLarge,
                  textAlign: TextAlign.center,
                ),
                const SizedBox(height: 12),
                Text(
                  formatMoney(_checkout.amountMinor, _checkout.currency),
                  style: theme.textTheme.bodyLarge,
                ),
                if (_checkout.refundRequested == true) ...[
                  const SizedBox(height: 12),
                  const Text('A refund has been requested.', key: Key('refundNote')),
                ],
                if (_connectionProblem && !isFinal) ...[
                  const SizedBox(height: 12),
                  Text(
                    'Trouble reaching the server. Still checking…',
                    key: const Key('connectionProblem'),
                    style: TextStyle(color: theme.colorScheme.error),
                  ),
                ],
                const SizedBox(height: 24),
                Text(
                  'Reference: ${_checkout.checkoutId}',
                  style: theme.textTheme.bodySmall,
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}
