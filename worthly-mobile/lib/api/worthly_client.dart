import 'dart:convert';

import 'package:http/http.dart' as http;
import 'package:worthly_mobile/api/models.dart';
import 'package:worthly_mobile/features/auth/auth_config.dart';
import 'package:worthly_mobile/features/auth/auth_repository.dart';

class WorthlyClient {
  WorthlyClient({
    required AuthRepository auth,
    required AuthConfig config,
    http.Client? httpClient,
  })  : _auth = auth,
        _config = config,
        _http = httpClient ?? http.Client();

  final AuthRepository _auth;
  final AuthConfig _config;
  final http.Client _http;

  Future<T> get<T>(String path, T Function(dynamic json) parse) async {
    final response = await _send('GET', path);
    return parse(jsonDecode(response.body));
  }

  Future<List<int>> bytes(String path) async {
    final response = await _send('GET', path, accept: 'text/csv, */*');
    return response.bodyBytes;
  }

  Future<T?> send<T>(String method, String path, {Object? body, T Function(dynamic json)? parse}) async {
    final response = await _send(method, path, body: body);
    if (response.statusCode == 204 || response.body.isEmpty) {
      return null;
    }
    if (parse == null) {
      return null;
    }
    return parse(jsonDecode(response.body));
  }

  Future<http.Response> _send(String method, String path, {Object? body, bool retried = false, String accept = 'application/json'}) async {
    await _auth.ensureFresh();
    final token = _auth.accessToken;
    if (token == null) {
      throw StateError('unauthorized');
    }
    final uri = Uri.parse('${_config.apiUrl}/api/v1$path');
    final headers = {
      'Authorization': 'Bearer $token',
      'Accept': accept,
      if (body != null) 'Content-Type': 'application/json',
    };
    final encoded = body == null ? null : jsonEncode(body);
    late http.Response response;
    switch (method) {
      case 'GET':
        response = await _http.get(uri, headers: headers);
      case 'POST':
        response = await _http.post(uri, headers: headers, body: encoded);
      case 'PATCH':
        response = await _http.patch(uri, headers: headers, body: encoded);
      case 'DELETE':
        response = await _http.delete(uri, headers: headers, body: encoded);
      default:
        throw StateError('unsupported_method');
    }
    if (response.statusCode == 401 && !retried) {
      await _auth.refreshSession();
      return _send(method, path, body: body, retried: true, accept: accept);
    }
    if (response.statusCode >= 400) {
      throw StateError('request_failed');
    }
    return response;
  }
}

List<T> listOf<T>(dynamic json, T Function(Map<String, dynamic>) parse) {
  return (json as List<dynamic>).map((item) => parse(item as Map<String, dynamic>)).toList();
}

Owner parseOwner(dynamic json) => Owner.fromJson(json as Map<String, dynamic>);
WealthSummary parseWealth(dynamic json) => WealthSummary.fromJson(json as Map<String, dynamic>);
MonthlyAnalytics parseMonthly(dynamic json) => MonthlyAnalytics.fromJson(json as Map<String, dynamic>);
TxPage parseTxPage(dynamic json) => TxPage.fromJson(json as Map<String, dynamic>);
Tx parseTx(dynamic json) => Tx.fromJson(json as Map<String, dynamic>);
InvestmentSummary parseInvestments(dynamic json) => InvestmentSummary.fromJson(json as Map<String, dynamic>);
List<Position> parsePositions(dynamic json) => listOf(json, Position.fromJson);
List<Balance> parseBalances(dynamic json) => listOf(json, Balance.fromJson);
List<Device> parseDevices(dynamic json) => listOf(json, Device.fromJson);
List<BankChoice> parseBanks(dynamic json) => listOf(json, BankChoice.fromJson);
List<TransferMatch> parseTransferMatches(dynamic json) => listOf(json, TransferMatch.fromJson);
SyncRunPage parseSyncRuns(dynamic json) => SyncRunPage.fromJson(json as Map<String, dynamic>);
String parseAuthUrl(dynamic json) => (json as Map<String, dynamic>)['url'] as String;
