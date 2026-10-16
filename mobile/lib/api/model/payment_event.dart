//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//
// @dart=2.18

// ignore_for_file: unused_element, unused_import
// ignore_for_file: always_put_required_named_parameters_first
// ignore_for_file: constant_identifier_names
// ignore_for_file: lines_longer_than_80_chars

part of openapi.api;

class PaymentEvent {
  /// Returns a new [PaymentEvent] instance.
  PaymentEvent({
    required this.eventId,
    required this.checkoutId,
    required this.paymentId,
    required this.status,
    required this.amountMinor,
    required this.currency,
    required this.occurredAt,
  });

  /// Unique per event. Used as the idempotency key.
  String eventId;

  String checkoutId;

  String paymentId;

  PaymentEventStatus status;

  int amountMinor;

  String currency;

  DateTime occurredAt;

  @override
  bool operator ==(Object other) => identical(this, other) || other is PaymentEvent &&
    other.eventId == eventId &&
    other.checkoutId == checkoutId &&
    other.paymentId == paymentId &&
    other.status == status &&
    other.amountMinor == amountMinor &&
    other.currency == currency &&
    other.occurredAt == occurredAt;

  @override
  int get hashCode =>
    // ignore: unnecessary_parenthesis
    (eventId.hashCode) +
    (checkoutId.hashCode) +
    (paymentId.hashCode) +
    (status.hashCode) +
    (amountMinor.hashCode) +
    (currency.hashCode) +
    (occurredAt.hashCode);

  @override
  String toString() => 'PaymentEvent[eventId=$eventId, checkoutId=$checkoutId, paymentId=$paymentId, status=$status, amountMinor=$amountMinor, currency=$currency, occurredAt=$occurredAt]';

  Map<String, dynamic> toJson() {
    final json = <String, dynamic>{};
      json[r'eventId'] = this.eventId;
      json[r'checkoutId'] = this.checkoutId;
      json[r'paymentId'] = this.paymentId;
      json[r'status'] = this.status;
      json[r'amountMinor'] = this.amountMinor;
      json[r'currency'] = this.currency;
      json[r'occurredAt'] = this.occurredAt.toUtc().toIso8601String();
    return json;
  }

  /// Returns a new [PaymentEvent] instance and imports its values from
  /// [value] if it's a [Map], null otherwise.
  // ignore: prefer_constructors_over_static_methods
  static PaymentEvent? fromJson(dynamic value) {
    if (value is Map) {
      final json = value.cast<String, dynamic>();

      // Ensure that the map contains the required keys.
      // Note 1: the values aren't checked for validity beyond being non-null.
      // Note 2: this code is stripped in release mode!
      assert(() {
        assert(json.containsKey(r'eventId'), 'Required key "PaymentEvent[eventId]" is missing from JSON.');
        assert(json[r'eventId'] != null, 'Required key "PaymentEvent[eventId]" has a null value in JSON.');
        assert(json.containsKey(r'checkoutId'), 'Required key "PaymentEvent[checkoutId]" is missing from JSON.');
        assert(json[r'checkoutId'] != null, 'Required key "PaymentEvent[checkoutId]" has a null value in JSON.');
        assert(json.containsKey(r'paymentId'), 'Required key "PaymentEvent[paymentId]" is missing from JSON.');
        assert(json[r'paymentId'] != null, 'Required key "PaymentEvent[paymentId]" has a null value in JSON.');
        assert(json.containsKey(r'status'), 'Required key "PaymentEvent[status]" is missing from JSON.');
        assert(json[r'status'] != null, 'Required key "PaymentEvent[status]" has a null value in JSON.');
        assert(json.containsKey(r'amountMinor'), 'Required key "PaymentEvent[amountMinor]" is missing from JSON.');
        assert(json[r'amountMinor'] != null, 'Required key "PaymentEvent[amountMinor]" has a null value in JSON.');
        assert(json.containsKey(r'currency'), 'Required key "PaymentEvent[currency]" is missing from JSON.');
        assert(json[r'currency'] != null, 'Required key "PaymentEvent[currency]" has a null value in JSON.');
        assert(json.containsKey(r'occurredAt'), 'Required key "PaymentEvent[occurredAt]" is missing from JSON.');
        assert(json[r'occurredAt'] != null, 'Required key "PaymentEvent[occurredAt]" has a null value in JSON.');
        return true;
      }());

      return PaymentEvent(
        eventId: mapValueOfType<String>(json, r'eventId')!,
        checkoutId: mapValueOfType<String>(json, r'checkoutId')!,
        paymentId: mapValueOfType<String>(json, r'paymentId')!,
        status: PaymentEventStatus.fromJson(json[r'status'])!,
        amountMinor: mapValueOfType<int>(json, r'amountMinor')!,
        currency: mapValueOfType<String>(json, r'currency')!,
        occurredAt: mapDateTime(json, r'occurredAt', r'')!,
      );
    }
    return null;
  }

  static List<PaymentEvent> listFromJson(dynamic json, {bool growable = false,}) {
    final result = <PaymentEvent>[];
    if (json is List && json.isNotEmpty) {
      for (final row in json) {
        final value = PaymentEvent.fromJson(row);
        if (value != null) {
          result.add(value);
        }
      }
    }
    return result.toList(growable: growable);
  }

  static Map<String, PaymentEvent> mapFromJson(dynamic json) {
    final map = <String, PaymentEvent>{};
    if (json is Map && json.isNotEmpty) {
      json = json.cast<String, dynamic>(); // ignore: parameter_assignments
      for (final entry in json.entries) {
        final value = PaymentEvent.fromJson(entry.value);
        if (value != null) {
          map[entry.key] = value;
        }
      }
    }
    return map;
  }

  // maps a json object with a list of PaymentEvent-objects as value to a dart map
  static Map<String, List<PaymentEvent>> mapListFromJson(dynamic json, {bool growable = false,}) {
    final map = <String, List<PaymentEvent>>{};
    if (json is Map && json.isNotEmpty) {
      // ignore: parameter_assignments
      json = json.cast<String, dynamic>();
      for (final entry in json.entries) {
        map[entry.key] = PaymentEvent.listFromJson(entry.value, growable: growable,);
      }
    }
    return map;
  }

  /// The list of required keys that must be present in a JSON.
  static const requiredKeys = <String>{
    'eventId',
    'checkoutId',
    'paymentId',
    'status',
    'amountMinor',
    'currency',
    'occurredAt',
  };
}

