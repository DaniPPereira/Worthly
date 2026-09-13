import 'dart:convert';

import 'package:http/http.dart' as http;
import 'package:worthly_mobile/features/auth/auth_config.dart';
import 'package:worthly_mobile/features/auth/pkce.dart';
import 'package:worthly_mobile/features/auth/secure_storage.dart';

class AuthSession {
  const AuthSession({required this.accessToken, required this.refreshToken});

  final String accessToken;
  final String refreshToken;
}

class AuthRepository {
  AuthRepository({
    required SecureStorage storage,
    required AuthConfig config,
    http.Client? httpClient,
    Pkce? pkce,
  })  : _storage = storage,
        _config = config,
        _http = httpClient ?? http.Client(),
        _pkce = pkce ?? Pkce();

  static const _refreshKey = 'refresh_token';
  static const _scopes = 'openid profile worthly.read worthly.write';

  final SecureStorage _storage;
  final AuthConfig _config;
  final http.Client _http;
  final Pkce _pkce;

  String? _accessToken;
  String? _pendingVerifier;
  String? _pendingState;

  String? get accessToken => _accessToken;

  Uri startLogin() {
    final pair = _pkce.generate();
    _pendingVerifier = pair.verifier;
    _pendingState = _pkce.randomState();
    final query = {
      'response_type': 'code',
      'client_id': _config.clientId,
      'redirect_uri': _config.redirectUri,
      'scope': _scopes,
      'code_challenge': pair.challenge,
      'code_challenge_method': 'S256',
      'state': _pendingState!,
    };
    final encoded = query.entries
        .map((entry) => '${Uri.encodeComponent(entry.key)}=${Uri.encodeComponent(entry.value)}')
        .join('&');
    return Uri.parse('${_config.issuer}/oauth2/authorize?$encoded');
  }

  Future<AuthSession> completeLogin(Uri callback) async {
    final error = callback.queryParameters['error'];
    if (error != null) {
      throw StateError('authorization_failed');
    }
    final code = callback.queryParameters['code'];
    final state = callback.queryParameters['state'];
    if (code == null || state == null || state != _pendingState || _pendingVerifier == null) {
      throw StateError('invalid_callback');
    }
    final response = await _http.post(
      Uri.parse('${_config.issuer}/oauth2/token'),
      headers: {'Content-Type': 'application/x-www-form-urlencoded'},
      body: {
        'grant_type': 'authorization_code',
        'client_id': _config.clientId,
        'code': code,
        'redirect_uri': _config.redirectUri,
        'code_verifier': _pendingVerifier!,
      },
    );
    if (response.statusCode != 200) {
      throw StateError('token_exchange_failed');
    }
    final json = jsonDecode(response.body) as Map<String, dynamic>;
    final access = json['access_token'] as String?;
    final refresh = json['refresh_token'] as String?;
    if (access == null || refresh == null) {
      throw StateError('token_exchange_failed');
    }
    _accessToken = access;
    await _storage.write(key: _refreshKey, value: refresh);
    _pendingVerifier = null;
    _pendingState = null;
    return AuthSession(accessToken: access, refreshToken: refresh);
  }

  Future<void> logout() async {
    final token = _accessToken;
    if (token != null) {
      await _http.post(
        Uri.parse('${_config.apiUrl}/api/v1/me/logout'),
        headers: {'Authorization': 'Bearer $token'},
      );
    }
    _accessToken = null;
    await _storage.delete(key: _refreshKey);
  }
}
