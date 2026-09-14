import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:worthly_mobile/features/session/session.dart';

class AuthViewState {
  const AuthViewState({this.authorizationUrl});

  final String? authorizationUrl;
}

class AuthController extends Notifier<AuthViewState> {
  @override
  AuthViewState build() => const AuthViewState();

  void startLogin() {
    final url = ref.read(authRepositoryProvider).startLogin();
    state = AuthViewState(authorizationUrl: url.toString());
  }
}

final authControllerProvider = NotifierProvider<AuthController, AuthViewState>(AuthController.new);
