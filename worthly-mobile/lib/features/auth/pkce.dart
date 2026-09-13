import 'dart:convert';
import 'dart:math';

import 'package:crypto/crypto.dart';

class PkcePair {
  const PkcePair({required this.verifier, required this.challenge});

  final String verifier;
  final String challenge;
}

class Pkce {
  Pkce({Random? random}) : _random = random ?? Random.secure();

  final Random _random;

  PkcePair generate() {
    final verifier = _base64Url(_randomBytes(32));
    final challenge = _base64Url(sha256.convert(utf8.encode(verifier)).bytes);
    return PkcePair(verifier: verifier, challenge: challenge);
  }

  String randomState() => _base64Url(_randomBytes(24));

  List<int> _randomBytes(int length) {
    return List<int>.generate(length, (_) => _random.nextInt(256));
  }

  static String _base64Url(List<int> bytes) {
    return base64Url.encode(bytes).replaceAll('=', '');
  }
}
