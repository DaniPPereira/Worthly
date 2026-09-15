import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:worthly_mobile/features/auth/auth_config.dart';
import 'package:worthly_mobile/features/auth/auth_repository.dart';
import 'package:worthly_mobile/features/auth/secure_storage.dart';
import 'package:worthly_mobile/features/me/me_repository.dart';

class MemoryStorage implements SecureStorage {
  final Map<String, String> values = {};

  @override
  Future<void> delete({required String key}) async {
    values.remove(key);
  }

  @override
  Future<String?> read({required String key}) async => values[key];

  @override
  Future<void> write({required String key, required String value}) async {
    values[key] = value;
  }
}

void main() {
  test('password login stores refresh material and calls /me with Bearer', () async {
    final storage = MemoryStorage();
    final authClient = MockClient((request) async {
      expect(request.url.path, '/api/v1/auth/login');
      expect(request.body, contains('"email":"owner@worthly.test"'));
      expect(request.body.contains('client_secret'), isFalse);
      return http.Response(
        '{"access_token":"access-1","refresh_token":"refresh-1","expires_in":600}',
        200,
        headers: {'Content-Type': 'application/json'},
      );
    });
    final auth = AuthRepository(
      storage: storage,
      config: AuthConfig.local,
      httpClient: authClient,
    );
    await auth.passwordLogin(email: 'owner@worthly.test', password: 'secret');
    expect(storage.values['refresh_token'], 'refresh-1');
    expect(auth.accessToken, 'access-1');

    final meClient = MockClient((request) async {
      expect(request.url.path, '/api/v1/me');
      expect(request.headers['Authorization'], 'Bearer access-1');
      return http.Response(
        '{"id":"00000000-0000-0000-0000-000000000001","email":"owner@worthly.test","reportingTimezone":"Europe/Lisbon","reportingCurrency":"EUR"}',
        200,
        headers: {'Content-Type': 'application/json'},
      );
    });
    final me = MeRepository(auth: auth, config: AuthConfig.local, httpClient: meClient);
    final owner = await me.current();
    expect(owner?.email, 'owner@worthly.test');
  });

  test('password login surfaces totp_required without storing tokens', () async {
    final storage = MemoryStorage();
    final auth = AuthRepository(
      storage: storage,
      config: AuthConfig.local,
      httpClient: MockClient((request) async {
        return http.Response(
          '{"detail":"totp_required"}',
          401,
          headers: {'Content-Type': 'application/problem+json'},
        );
      }),
    );
    expect(
      () => auth.passwordLogin(email: 'owner@worthly.test', password: 'secret'),
      throwsA(isA<AuthFailure>().having((error) => error.code, 'code', 'totp_required')),
    );
    expect(storage.values.containsKey('refresh_token'), isFalse);
  });

  test('logout deletes refresh material even if the API call fails', () async {
    final storage = MemoryStorage();
    final authClient = MockClient((request) async {
      if (request.url.path == '/api/v1/auth/login') {
        return http.Response(
          '{"access_token":"access-1","refresh_token":"refresh-1","expires_in":600}',
          200,
          headers: {'Content-Type': 'application/json'},
        );
      }
      expect(request.url.path, '/api/v1/me/logout');
      expect(request.headers['Authorization'], 'Bearer access-1');
      expect(request.body, contains('refresh-1'));
      return http.Response('{"detail":"unavailable"}', 500, headers: {'Content-Type': 'application/json'});
    });
    final auth = AuthRepository(
      storage: storage,
      config: AuthConfig.local,
      httpClient: authClient,
    );
    await auth.passwordLogin(email: 'owner@worthly.test', password: 'secret');
    expect(storage.values['refresh_token'], 'refresh-1');

    await auth.logout();
    expect(auth.accessToken, isNull);
    expect(storage.values.containsKey('refresh_token'), isFalse);
  });
}
