class AuthConfig {
  const AuthConfig({
    required this.issuer,
    required this.apiUrl,
    required this.redirectUri,
    this.clientId = 'worthly-mobile',
  });

  final String issuer;
  final String apiUrl;
  final String redirectUri;
  final String clientId;

  /// Public client: there is no client secret.
  String? get clientSecret => null;

  static const local = AuthConfig(
    issuer: 'http://localhost:8080',
    apiUrl: 'http://localhost:8080',
    redirectUri: 'http://localhost:3000/mobile-auth/callback',
  );
}
