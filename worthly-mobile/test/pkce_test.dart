import 'package:flutter_test/flutter_test.dart';
import 'package:worthly_mobile/features/auth/auth_config.dart';
import 'package:worthly_mobile/features/auth/pkce.dart';

void main() {
  test('PKCE S256 challenge differs from verifier', () {
    final pair = Pkce().generate();
    expect(pair.verifier.length, greaterThanOrEqualTo(43));
    expect(pair.challenge, isNot(pair.verifier));
  });

  test('mobile client has no client secret', () {
    expect(AuthConfig.local.clientSecret, isNull);
    expect(AuthConfig.local.clientId, 'worthly-mobile');
  });
}
