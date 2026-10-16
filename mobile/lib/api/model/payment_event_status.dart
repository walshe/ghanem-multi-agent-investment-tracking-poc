//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//
// @dart=2.18

// ignore_for_file: unused_element, unused_import
// ignore_for_file: always_put_required_named_parameters_first
// ignore_for_file: constant_identifier_names
// ignore_for_file: lines_longer_than_80_chars

part of openapi.api;

/// Statuses the provider can report. PENDING and EXPIRED are ours, never the provider's.
enum PaymentEventStatus {
  PROCESSING._(r'PROCESSING'),
  SUCCEEDED._(r'SUCCEEDED'),
  FAILED._(r'FAILED'),
  ;

  /// Instantiate a new enum with the provided value.
  const PaymentEventStatus._(this._value);

  /// The underlying value of this enum member.
  final String _value;

  @override
  String toString() => _value;

  /// Encodes this enum as a value suitable for JSON.
  String toJson() => _value;

  /// Returns the instance of [PaymentEventStatus] that was successfully decoded
  /// from the passed [value] on success, null otherwise.
  static PaymentEventStatus? fromJson(dynamic value) => PaymentEventStatusTypeTransformer().decode(value);

  /// Returns a [List] containing instances of [PaymentEventStatus]
  /// that were successfully decoded from the passed [JSON][json].
  static List<PaymentEventStatus> listFromJson(dynamic json, {bool growable = false,}) {
    final result = <PaymentEventStatus>[];
    if (json is List && json.isNotEmpty) {
      for (final row in json) {
        final value = PaymentEventStatus.fromJson(row);
        if (value != null) {
          result.add(value);
        }
      }
    }
    return result.toList(growable: growable);
  }
}

/// Transformation class that can [encode] an instance of [PaymentEventStatus] to String,
/// and [decode] dynamic data back to [PaymentEventStatus].
class PaymentEventStatusTypeTransformer {
  factory PaymentEventStatusTypeTransformer() => _instance ??= const PaymentEventStatusTypeTransformer._();

  const PaymentEventStatusTypeTransformer._();

  /// Encodes this enum as a value suitable for JSON.
  String encode(PaymentEventStatus data) => data._value;

  /// Returns the instance of [PaymentEventStatus] that was successfully decoded
  /// from the passed [data] value on success, null otherwise.
  ///
  /// If [allowNull] is true and the [dynamic value][data] cannot be decoded successfully,
  /// then null is returned. However, if [allowNull] is false and the [dynamic value][data]
  /// cannot be decoded successfully, then an [UnimplementedError] is thrown.
  ///
  /// The [allowNull] is very handy when an API changes and a new enum value is added or removed,
  /// and users are still using an old app with the old code.
  PaymentEventStatus? decode(dynamic data, {bool allowNull = true}) {
    if (data is PaymentEventStatus) {
      return data;
    }
    if (data != null) {
      switch (data) {
        case r'PROCESSING': return PaymentEventStatus.PROCESSING;
        case r'SUCCEEDED': return PaymentEventStatus.SUCCEEDED;
        case r'FAILED': return PaymentEventStatus.FAILED;
        default:
          if (!allowNull) {
            throw ArgumentError('Unknown enum value to decode: $data');
          }
      }
    }
    return null;
  }

  /// The singleton instance of this transformer.
  static PaymentEventStatusTypeTransformer? _instance;
}

