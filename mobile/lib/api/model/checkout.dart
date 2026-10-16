//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//
// @dart=2.18

// ignore_for_file: unused_element, unused_import
// ignore_for_file: always_put_required_named_parameters_first
// ignore_for_file: constant_identifier_names
// ignore_for_file: lines_longer_than_80_chars

part of openapi.api;

class Checkout {
  /// Returns a new [Checkout] instance.
  Checkout({
    required this.checkoutId,
    required this.status,
    required this.amountMinor,
    required this.currency,
    this.paymentId,
    this.refundRequested,
    required this.createdAt,
    required this.updatedAt,
  });

  String checkoutId;

  CheckoutStatus status;

  int amountMinor;

  String currency;

  /// The provider's payment id. Null until the provider has accepted the payment.
  String? paymentId;

  /// True if a refund was requested because a payment succeeded after the checkout expired.
  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  bool? refundRequested;

  DateTime createdAt;

  DateTime updatedAt;

  @override
  bool operator ==(Object other) => identical(this, other) || other is Checkout &&
    other.checkoutId == checkoutId &&
    other.status == status &&
    other.amountMinor == amountMinor &&
    other.currency == currency &&
    other.paymentId == paymentId &&
    other.refundRequested == refundRequested &&
    other.createdAt == createdAt &&
    other.updatedAt == updatedAt;

  @override
  int get hashCode =>
    // ignore: unnecessary_parenthesis
    (checkoutId.hashCode) +
    (status.hashCode) +
    (amountMinor.hashCode) +
    (currency.hashCode) +
    (paymentId == null ? 0 : paymentId!.hashCode) +
    (refundRequested == null ? 0 : refundRequested!.hashCode) +
    (createdAt.hashCode) +
    (updatedAt.hashCode);

  @override
  String toString() => 'Checkout[checkoutId=$checkoutId, status=$status, amountMinor=$amountMinor, currency=$currency, paymentId=$paymentId, refundRequested=$refundRequested, createdAt=$createdAt, updatedAt=$updatedAt]';

  Map<String, dynamic> toJson() {
    final json = <String, dynamic>{};
      json[r'checkoutId'] = this.checkoutId;
      json[r'status'] = this.status;
      json[r'amountMinor'] = this.amountMinor;
      json[r'currency'] = this.currency;
    if (this.paymentId != null) {
      json[r'paymentId'] = this.paymentId;
    } else {
      json[r'paymentId'] = null;
    }
    if (this.refundRequested != null) {
      json[r'refundRequested'] = this.refundRequested;
    } else {
      json[r'refundRequested'] = null;
    }
      json[r'createdAt'] = this.createdAt.toUtc().toIso8601String();
      json[r'updatedAt'] = this.updatedAt.toUtc().toIso8601String();
    return json;
  }

  /// Returns a new [Checkout] instance and imports its values from
  /// [value] if it's a [Map], null otherwise.
  // ignore: prefer_constructors_over_static_methods
  static Checkout? fromJson(dynamic value) {
    if (value is Map) {
      final json = value.cast<String, dynamic>();

      // Ensure that the map contains the required keys.
      // Note 1: the values aren't checked for validity beyond being non-null.
      // Note 2: this code is stripped in release mode!
      assert(() {
        assert(json.containsKey(r'checkoutId'), 'Required key "Checkout[checkoutId]" is missing from JSON.');
        assert(json[r'checkoutId'] != null, 'Required key "Checkout[checkoutId]" has a null value in JSON.');
        assert(json.containsKey(r'status'), 'Required key "Checkout[status]" is missing from JSON.');
        assert(json[r'status'] != null, 'Required key "Checkout[status]" has a null value in JSON.');
        assert(json.containsKey(r'amountMinor'), 'Required key "Checkout[amountMinor]" is missing from JSON.');
        assert(json[r'amountMinor'] != null, 'Required key "Checkout[amountMinor]" has a null value in JSON.');
        assert(json.containsKey(r'currency'), 'Required key "Checkout[currency]" is missing from JSON.');
        assert(json[r'currency'] != null, 'Required key "Checkout[currency]" has a null value in JSON.');
        assert(json.containsKey(r'createdAt'), 'Required key "Checkout[createdAt]" is missing from JSON.');
        assert(json[r'createdAt'] != null, 'Required key "Checkout[createdAt]" has a null value in JSON.');
        assert(json.containsKey(r'updatedAt'), 'Required key "Checkout[updatedAt]" is missing from JSON.');
        assert(json[r'updatedAt'] != null, 'Required key "Checkout[updatedAt]" has a null value in JSON.');
        return true;
      }());

      return Checkout(
        checkoutId: mapValueOfType<String>(json, r'checkoutId')!,
        status: CheckoutStatus.fromJson(json[r'status'])!,
        amountMinor: mapValueOfType<int>(json, r'amountMinor')!,
        currency: mapValueOfType<String>(json, r'currency')!,
        paymentId: mapValueOfType<String>(json, r'paymentId'),
        refundRequested: mapValueOfType<bool>(json, r'refundRequested'),
        createdAt: mapDateTime(json, r'createdAt', r'')!,
        updatedAt: mapDateTime(json, r'updatedAt', r'')!,
      );
    }
    return null;
  }

  static List<Checkout> listFromJson(dynamic json, {bool growable = false,}) {
    final result = <Checkout>[];
    if (json is List && json.isNotEmpty) {
      for (final row in json) {
        final value = Checkout.fromJson(row);
        if (value != null) {
          result.add(value);
        }
      }
    }
    return result.toList(growable: growable);
  }

  static Map<String, Checkout> mapFromJson(dynamic json) {
    final map = <String, Checkout>{};
    if (json is Map && json.isNotEmpty) {
      json = json.cast<String, dynamic>(); // ignore: parameter_assignments
      for (final entry in json.entries) {
        final value = Checkout.fromJson(entry.value);
        if (value != null) {
          map[entry.key] = value;
        }
      }
    }
    return map;
  }

  // maps a json object with a list of Checkout-objects as value to a dart map
  static Map<String, List<Checkout>> mapListFromJson(dynamic json, {bool growable = false,}) {
    final map = <String, List<Checkout>>{};
    if (json is Map && json.isNotEmpty) {
      // ignore: parameter_assignments
      json = json.cast<String, dynamic>();
      for (final entry in json.entries) {
        map[entry.key] = Checkout.listFromJson(entry.value, growable: growable,);
      }
    }
    return map;
  }

  /// The list of required keys that must be present in a JSON.
  static const requiredKeys = <String>{
    'checkoutId',
    'status',
    'amountMinor',
    'currency',
    'createdAt',
    'updatedAt',
  };
}

