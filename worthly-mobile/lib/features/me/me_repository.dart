import 'dart:convert';

import 'package:http/http.dart' as http;
import 'package:worthly_mobile/features/auth/auth_config.dart';
import 'package:worthly_mobile/features/auth/auth_repository.dart';
import 'package:worthly_mobile/features/me/owner.dart';

class MeRepository {
  MeRepository({
    required AuthRepository auth,
    required AuthConfig config,
    http.Client? httpClient,
  })  : _auth = auth,
        _config = config,
        _http = httpClient ?? http.Client();

  final AuthRepository _auth;
  final AuthConfig _config;
  final http.Client _http;

  Future<Owner?> current() async {
    final token = _auth.accessToken;
    if (token == null) {
      return null;
    }
    final response = await _http.get(
      Uri.parse('${_config.apiUrl}/api/v1/me'),
      headers: {'Authorization': 'Bearer $token'},
    );
    if (response.statusCode != 200) {
      throw StateError('me_failed');
    }
    return Owner.fromJson(jsonDecode(response.body) as Map<String, dynamic>);
  }
}
