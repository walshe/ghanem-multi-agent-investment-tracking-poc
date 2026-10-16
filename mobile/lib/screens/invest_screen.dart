import 'package:flutter/material.dart';

import '../api/api.dart';
import '../config.dart';
import '../money.dart';
import 'status_screen.dart';

/// Lets the customer enter an amount in euros and starts a checkout.
class InvestScreen extends StatefulWidget {
  const InvestScreen({super.key, required this.api, this.pollInterval = const Duration(seconds: 2)});

  final CheckoutsApi api;
  final Duration pollInterval;

  @override
  State<InvestScreen> createState() => _InvestScreenState();
}

class _InvestScreenState extends State<InvestScreen> {
  final _amountController = TextEditingController();
  String? _error;
  bool _submitting = false;

  @override
  void dispose() {
    _amountController.dispose();
    super.dispose();
  }

  Future<void> _submit() async {
    final cents = eurosToCents(_amountController.text);
    if (cents == null) {
      setState(() => _error = 'Enter an amount in euros, for example 100.50');
      return;
    }
    if (cents < minAmountMinor) {
      setState(() => _error = 'The minimum amount is ${formatMoney(minAmountMinor, currency)}');
      return;
    }

    setState(() {
      _error = null;
      _submitting = true;
    });

    try {
      final checkout = await widget.api.createCheckout(
        CreateCheckoutRequest(amountMinor: cents, currency: currency),
      );
      if (!mounted) return;
      if (checkout == null) {
        setState(() => _error = 'Could not start the checkout. Please try again.');
        return;
      }
      await Navigator.of(context).push(
        MaterialPageRoute<void>(
          builder: (_) => StatusScreen(
            api: widget.api,
            initial: checkout,
            pollInterval: widget.pollInterval,
          ),
        ),
      );
    } on ApiException catch (e) {
      if (!mounted) return;
      setState(() => _error = e.code == 400
          ? 'That amount was not accepted. Please check it and try again.'
          : 'Could not start the checkout. Please try again.');
    } catch (_) {
      if (!mounted) return;
      setState(() => _error = 'Could not start the checkout. Please try again.');
    } finally {
      if (mounted) setState(() => _submitting = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Invest')),
      body: Center(
        child: ConstrainedBox(
          constraints: const BoxConstraints(maxWidth: 420),
          child: Padding(
            padding: const EdgeInsets.all(24),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                TextField(
                  key: const Key('amountField'),
                  controller: _amountController,
                  enabled: !_submitting,
                  keyboardType: const TextInputType.numberWithOptions(decimal: true),
                  decoration: InputDecoration(
                    labelText: 'Amount',
                    prefixText: '€ ',
                    hintText: '100.50',
                    errorText: _error,
                  ),
                  onSubmitted: (_) => _submitting ? null : _submit(),
                ),
                const SizedBox(height: 16),
                FilledButton(
                  key: const Key('investButton'),
                  onPressed: _submitting ? null : _submit,
                  child: Text(_submitting ? 'Starting…' : 'Invest'),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}
