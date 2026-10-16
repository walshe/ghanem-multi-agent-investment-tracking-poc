//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//
// @dart=2.18

// ignore_for_file: unused_element, unused_import
// ignore_for_file: always_put_required_named_parameters_first
// ignore_for_file: constant_identifier_names
// ignore_for_file: lines_longer_than_80_chars

part of openapi.api;

class CheckoutEvent {
  /// Returns a new [CheckoutEvent] instance.
  CheckoutEvent({
    required this.eventId,
    required this.status,
    this.amountMinor,
    required this.outcome,
    required this.receivedAt,
  });

  String eventId;

  PaymentEventStatus status;

  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  int? amountMinor;

  EventOutcome outcome;

  DateTime receivedAt;

  @override
  bool operator ==(Object other) => identical(this, other) || other is CheckoutEvent &&
    other.eventId == eventId &&
    other.status == status &&
    other.amountMinor == amountMinor &&
    other.outcome == outcome &&
    other.receivedAt == receivedAt;

  @override
  int get hashCode =>
    // ignore: unnecessary_parenthesis
    (eventId.hashCode) +
    (status.hashCode) +
    (amountMinor == null ? 0 : amountMinor!.hashCode) +
    (outcome.hashCode) +
    (receivedAt.hashCode);

  @override
  String toString() => 'CheckoutEvent[eventId=$eventId, status=$status, amountMinor=$amountMinor, outcome=$outcome, receivedAt=$receivedAt]';

  Map<String, dynamic> toJson() {
    final json = <String, dynamic>{};
      json[r'eventId'] = this.eventId;
      json[r'status'] = this.status;
    if (this.amountMinor != null) {
      json[r'amountMinor'] = this.amountMinor;
    } else {
      json[r'amountMinor'] = null;
    }
      json[r'outcome'] = this.outcome;
      json[r'receivedAt'] = this.receivedAt.toUtc().toIso8601String();
    return json;
  }

  /// Returns a new [CheckoutEvent] instance and imports its values from
  /// [value] if it's a [Map], null otherwise.
  // ignore: prefer_constructors_over_static_methods
  static CheckoutEvent? fromJson(dynamic value) {
    if (value is Map) {
      final json = value.cast<String, dynamic>();

      // Ensure that the map contains the required keys.
      // Note 1: the values aren't checked for validity beyond being non-null.
      // Note 2: this code is stripped in release mode!
      assert(() {
        assert(json.containsKey(r'eventId'), 'Required key "CheckoutEvent[eventId]" is missing from JSON.');
        assert(json[r'eventId'] != null, 'Required key "CheckoutEvent[eventId]" has a null value in JSON.');
        assert(json.containsKey(r'status'), 'Required key "CheckoutEvent[status]" is missing from JSON.');
        assert(json[r'status'] != null, 'Required key "CheckoutEvent[status]" has a null value in JSON.');
        assert(json.containsKey(r'outcome'), 'Required key "CheckoutEvent[outcome]" is missing from JSON.');
        assert(json[r'outcome'] != null, 'Required key "CheckoutEvent[outcome]" has a null value in JSON.');
        assert(json.containsKey(r'receivedAt'), 'Required key "CheckoutEvent[receivedAt]" is missing from JSON.');
        assert(json[r'receivedAt'] != null, 'Required key "CheckoutEvent[receivedAt]" has a null value in JSON.');
        return true;
      }());

      return CheckoutEvent(
        eventId: mapValueOfType<String>(json, r'eventId')!,
        status: PaymentEventStatus.fromJson(json[r'status'])!,
        amountMinor: mapValueOfType<int>(json, r'amountMinor'),
        outcome: EventOutcome.fromJson(json[r'outcome'])!,
        receivedAt: mapDateTime(json, r'receivedAt', r'')!,
      );
    }
    return null;
  }

  static List<CheckoutEvent> listFromJson(dynamic json, {bool growable = false,}) {
    final result = <CheckoutEvent>[];
    if (json is List && json.isNotEmpty) {
      for (final row in json) {
        final value = CheckoutEvent.fromJson(row);
        if (value != null) {
          result.add(value);
        }
      }
    }
    return result.toList(growable: growable);
  }

  static Map<String, CheckoutEvent> mapFromJson(dynamic json) {
    final map = <String, CheckoutEvent>{};
    if (json is Map && json.isNotEmpty) {
      json = json.cast<String, dynamic>(); // ignore: parameter_assignments
      for (final entry in json.entries) {
        final value = CheckoutEvent.fromJson(entry.value);
        if (value != null) {
          map[entry.key] = value;
        }
      }
    }
    return map;
  }

  // maps a json object with a list of CheckoutEvent-objects as value to a dart map
  static Map<String, List<CheckoutEvent>> mapListFromJson(dynamic json, {bool growable = false,}) {
    final map = <String, List<CheckoutEvent>>{};
    if (json is Map && json.isNotEmpty) {
      // ignore: parameter_assignments
      json = json.cast<String, dynamic>();
      for (final entry in json.entries) {
        map[entry.key] = CheckoutEvent.listFromJson(entry.value, growable: growable,);
      }
    }
    return map;
  }

  /// The list of required keys that must be present in a JSON.
  static const requiredKeys = <String>{
    'eventId',
    'status',
    'outcome',
    'receivedAt',
  };
}

