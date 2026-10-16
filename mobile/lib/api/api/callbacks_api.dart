//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//
// @dart=2.18

// ignore_for_file: unused_element, unused_import
// ignore_for_file: always_put_required_named_parameters_first
// ignore_for_file: constant_identifier_names
// ignore_for_file: lines_longer_than_80_chars

part of openapi.api;


class CallbacksApi {
  CallbacksApi([ApiClient? apiClient]) : apiClient = apiClient ?? defaultApiClient;

  final ApiClient apiClient;

  /// Payment status callback from the provider
  ///
  /// Processed exactly as described in states.md.
  ///
  /// Note: This method returns the HTTP [Response].
  ///
  /// Parameters:
  ///
  /// * [String] xSignature (required):
  ///   Lowercase hex HMAC-SHA256 of the raw request body, keyed with the shared signing secret.
  ///
  /// * [PaymentEvent] paymentEvent (required):
  Future<Response> receivePaymentCallbackWithHttpInfo(String xSignature, PaymentEvent paymentEvent, { Future<void>? abortTrigger, }) async {
    // ignore: prefer_const_declarations
    final path = r'/callbacks';

    // ignore: prefer_final_locals
    Object? postBody = paymentEvent;

    final queryParams = <QueryParam>[];
    final headerParams = <String, String>{};
    final formParams = <String, String>{};

    headerParams[r'X-Signature'] = parameterToString(xSignature);

    const contentTypes = <String>['application/json'];


    return apiClient.invokeAPI(
      path,
      'POST',
      queryParams,
      postBody,
      headerParams,
      formParams,
      contentTypes.isEmpty ? null : contentTypes.first,
      abortTrigger: abortTrigger,
    );
  }

  /// Payment status callback from the provider
  ///
  /// Processed exactly as described in states.md.
  ///
  /// Parameters:
  ///
  /// * [String] xSignature (required):
  ///   Lowercase hex HMAC-SHA256 of the raw request body, keyed with the shared signing secret.
  ///
  /// * [PaymentEvent] paymentEvent (required):
  Future<void> receivePaymentCallback(String xSignature, PaymentEvent paymentEvent, { Future<void>? abortTrigger, }) async {
    final response = await receivePaymentCallbackWithHttpInfo(xSignature, paymentEvent, abortTrigger: abortTrigger,);
    if (response.statusCode >= HttpStatus.badRequest) {
      throw ApiException(response.statusCode, await _decodeBodyBytes(response));
    }
  }
}
