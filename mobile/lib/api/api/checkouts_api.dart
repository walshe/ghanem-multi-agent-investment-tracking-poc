//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//
// @dart=2.18

// ignore_for_file: unused_element, unused_import
// ignore_for_file: always_put_required_named_parameters_first
// ignore_for_file: constant_identifier_names
// ignore_for_file: lines_longer_than_80_chars

part of openapi.api;


class CheckoutsApi {
  CheckoutsApi([ApiClient? apiClient]) : apiClient = apiClient ?? defaultApiClient;

  final ApiClient apiClient;

  /// Start an investment checkout
  ///
  /// Note: This method returns the HTTP [Response].
  ///
  /// Parameters:
  ///
  /// * [CreateCheckoutRequest] createCheckoutRequest (required):
  Future<Response> createCheckoutWithHttpInfo(CreateCheckoutRequest createCheckoutRequest, { Future<void>? abortTrigger, }) async {
    // ignore: prefer_const_declarations
    final path = r'/checkouts';

    // ignore: prefer_final_locals
    Object? postBody = createCheckoutRequest;

    final queryParams = <QueryParam>[];
    final headerParams = <String, String>{};
    final formParams = <String, String>{};

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

  /// Start an investment checkout
  ///
  /// Parameters:
  ///
  /// * [CreateCheckoutRequest] createCheckoutRequest (required):
  Future<Checkout?> createCheckout(CreateCheckoutRequest createCheckoutRequest, { Future<void>? abortTrigger, }) async {
    final response = await createCheckoutWithHttpInfo(createCheckoutRequest, abortTrigger: abortTrigger,);
    if (response.statusCode >= HttpStatus.badRequest) {
      throw ApiException(response.statusCode, await _decodeBodyBytes(response));
    }
    // When a remote server returns no body with a status of 204, we shall not decode it.
    // At the time of writing this, `dart:convert` will throw an "Unexpected end of input"
    // FormatException when trying to decode an empty string.
    if (response.body.isNotEmpty && response.statusCode != HttpStatus.noContent) {
      return await apiClient.deserializeAsync(await _decodeBodyBytes(response), 'Checkout',) as Checkout;
    
    }
    return null;
  }

  /// Get the current status of a checkout
  ///
  /// Note: This method returns the HTTP [Response].
  ///
  /// Parameters:
  ///
  /// * [String] checkoutId (required):
  Future<Response> getCheckoutWithHttpInfo(String checkoutId, { Future<void>? abortTrigger, }) async {
    // ignore: prefer_const_declarations
    final path = r'/checkouts/{checkoutId}'
      .replaceAll('{checkoutId}', checkoutId);

    // ignore: prefer_final_locals
    Object? postBody;

    final queryParams = <QueryParam>[];
    final headerParams = <String, String>{};
    final formParams = <String, String>{};

    const contentTypes = <String>[];


    return apiClient.invokeAPI(
      path,
      'GET',
      queryParams,
      postBody,
      headerParams,
      formParams,
      contentTypes.isEmpty ? null : contentTypes.first,
      abortTrigger: abortTrigger,
    );
  }

  /// Get the current status of a checkout
  ///
  /// Parameters:
  ///
  /// * [String] checkoutId (required):
  Future<Checkout?> getCheckout(String checkoutId, { Future<void>? abortTrigger, }) async {
    final response = await getCheckoutWithHttpInfo(checkoutId, abortTrigger: abortTrigger,);
    if (response.statusCode >= HttpStatus.badRequest) {
      throw ApiException(response.statusCode, await _decodeBodyBytes(response));
    }
    // When a remote server returns no body with a status of 204, we shall not decode it.
    // At the time of writing this, `dart:convert` will throw an "Unexpected end of input"
    // FormatException when trying to decode an empty string.
    if (response.body.isNotEmpty && response.statusCode != HttpStatus.noContent) {
      return await apiClient.deserializeAsync(await _decodeBodyBytes(response), 'Checkout',) as Checkout;
    
    }
    return null;
  }
}
