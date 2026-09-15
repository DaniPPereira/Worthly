class AuthConfig {
  const AuthConfig({
    required this.issuer,
    required this.apiUrl,
    required this.redirectUri,
    this.clientId = 'worthly-mobile',
    this.webOrigin = 'https://worthly.danielpereira6.pt',
  });

  final String issuer;
  final String apiUrl;
  final String redirectUri;
  final String clientId;
  final String webOrigin;

  /// Public client: there is no client secret.
  String? get clientSecret => null;

  String get callbackScheme => Uri.parse(redirectUri).scheme;

  static const local = AuthConfig(
    issuer: String.fromEnvironment('WORTHLY_API', defaultValue: 'http://localhost:8080'),
    apiUrl: String.fromEnvironment('WORTHLY_API', defaultValue: 'http://localhost:8080'),
    redirectUri: 'worthly://auth/callback',
    webOrigin: String.fromEnvironment('WORTHLY_WEB', defaultValue: 'https://worthly.danielpereira6.pt'),
  );
}

