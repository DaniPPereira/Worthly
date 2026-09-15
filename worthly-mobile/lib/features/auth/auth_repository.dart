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

class AuthFailure implements Exception {
  const AuthFailure(this.code);

  final String code;

  @override
  String toString() => code;
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
  DateTime? _accessExpiresAt;
  String? _pendingVerifier;
  String? _pendingState;

  String? get accessToken => _accessToken;
  bool get hasSession => _accessToken != null;

  Future<AuthSession> passwordLogin({
    required String email,
    required String password,
    String? totpCode,
  }) async {
    final body = <String, String>{'email': email, 'password': password};
    if (totpCode != null && totpCode.isNotEmpty) {
      body['totpCode'] = totpCode;
    }
    final response = await _http.post(
      Uri.parse('${_config.apiUrl}/api/v1/auth/login'),
      headers: {'Content-Type': 'application/json', 'Accept': 'application/json'},
      body: jsonEncode(body),
    );
    if (response.statusCode == 401) {
      throw AuthFailure(_problemCode(response));
    }
    if (response.statusCode >= 400) {
      throw const AuthFailure('request_failed');
    }
    return _storeTokens(response);
  }

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
      'prompt': 'login',
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
    return _storeTokens(
      await _http.post(
        Uri.parse('${_config.issuer}/oauth2/token'),
        headers: {'Content-Type': 'application/x-www-form-urlencoded'},
        body: {
          'grant_type': 'authorization_code',
          'client_id': _config.clientId,
          'code': code,
          'redirect_uri': _config.redirectUri,
          'code_verifier': _pendingVerifier!,
        },
      ),
    );
  }

  Future<bool> restore() async {
    final refresh = await _storage.read(key: _refreshKey);
    if (refresh == null) {
      return false;
    }
    try {
      await _exchangeRefresh(refresh);
      return true;
    } catch (_) {
      await _storage.delete(key: _refreshKey);
      return false;
    }
  }

  Future<void> ensureFresh() async {
    if (_accessToken != null &&
        _accessExpiresAt != null &&
        DateTime.now().isBefore(_accessExpiresAt!.subtract(const Duration(seconds: 60)))) {
      return;
    }
    await refreshSession();
  }

  Future<void> refreshSession() async {
    final refresh = await _storage.read(key: _refreshKey);
    if (refresh == null) {
      throw StateError('unauthorized');
    }
    await _exchangeRefresh(refresh);
  }

  Future<void> _exchangeRefresh(String refresh) async {
    await _storeTokens(
      await _http.post(
        Uri.parse('${_config.issuer}/oauth2/token'),
        headers: {'Content-Type': 'application/x-www-form-urlencoded'},
        body: {
          'grant_type': 'refresh_token',
          'client_id': _config.clientId,
          'refresh_token': refresh,
        },
      ),
    );
  }

  Future<AuthSession> _storeTokens(http.Response response) async {
    if (response.statusCode != 200) {
      throw StateError('token_exchange_failed');
    }
    final json = jsonDecode(response.body) as Map<String, dynamic>;
    final access = json['access_token'] as String?;
    final refresh = json['refresh_token'] as String?;
    final expires = (json['expires_in'] as num?)?.toInt() ?? 600;
    if (access == null || refresh == null) {
      throw StateError('token_exchange_failed');
    }
    _accessToken = access;
    _accessExpiresAt = DateTime.now().add(Duration(seconds: expires));
    await _storage.write(key: _refreshKey, value: refresh);
    _pendingVerifier = null;
    _pendingState = null;
    return AuthSession(accessToken: access, refreshToken: refresh);
  }

  Future<void> logout() async {
    final token = _accessToken;
    final refresh = await _storage.read(key: _refreshKey);
    try {
      if (token != null) {
        await _http.post(
          Uri.parse('${_config.apiUrl}/api/v1/me/logout'),
          headers: {
            'Authorization': 'Bearer $token',
            'Content-Type': 'application/json',
          },
          body: jsonEncode({'refreshToken': refresh}),
        );
      }
    } finally {
      _accessToken = null;
      _accessExpiresAt = null;
      await _storage.delete(key: _refreshKey);
    }
  }
}

String _problemCode(http.Response response) {
  try {
    final json = jsonDecode(response.body);
    if (json is Map<String, dynamic>) {
      final detail = json['detail'];
      if (detail is String && detail.isNotEmpty && !detail.contains(' ')) {
        return detail;
      }
    }
  } catch (_) {
    /* fall through */
  }
  return 'invalid_credentials';
}
