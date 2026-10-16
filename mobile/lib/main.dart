import 'package:flutter/material.dart';

import 'api/api.dart';
import 'config.dart' as config;
import 'screens/invest_screen.dart';

void main() {
  runApp(CheckoutApp(api: CheckoutsApi(ApiClient(basePath: config.apiBaseUrl))));
}

class CheckoutApp extends StatelessWidget {
  const CheckoutApp({super.key, required this.api, this.pollInterval = config.pollInterval});

  final CheckoutsApi api;
  final Duration pollInterval;

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Checkout',
      theme: ThemeData(colorSchemeSeed: Colors.indigo),
      home: InvestScreen(api: api, pollInterval: pollInterval),
    );
  }
}
