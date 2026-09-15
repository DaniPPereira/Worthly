import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:local_auth/local_auth.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:worthly_mobile/api/models.dart';
import 'package:worthly_mobile/api/worthly_client.dart';
import 'package:worthly_mobile/features/auth/auth_config.dart';
import 'package:worthly_mobile/features/auth/auth_repository.dart';
import 'package:worthly_mobile/features/auth/keychain_secure_storage.dart';

enum SessionPhase { boot, signedOut, locked, ready }

class SessionState {
  const SessionState({required this.phase, this.owner, this.error, this.syncing = false});

  final SessionPhase phase;
  final Owner? owner;
  final String? error;
  final bool syncing;

  SessionState copyWith({SessionPhase? phase, Owner? owner, String? error, bool? syncing, bool clearError = false}) {
    return SessionState(
      phase: phase ?? this.phase,
      owner: owner ?? this.owner,
      error: clearError ? null : (error ?? this.error),
      syncing: syncing ?? this.syncing,
    );
  }
}

final authRepositoryProvider = Provider<AuthRepository>((ref) {
  return AuthRepository(storage: KeychainSecureStorage(), config: AuthConfig.local);
});

final worthlyClientProvider = Provider<WorthlyClient>((ref) {
  return WorthlyClient(auth: ref.watch(authRepositoryProvider), config: AuthConfig.local);
});

final privacyProvider = NotifierProvider<PrivacyController, bool>(PrivacyController.new);

class PrivacyController extends Notifier<bool> {
  static const _key = 'worthly.privacy';

  @override
  bool build() {
    Future.microtask(_load);
    return false;
  }

  Future<void> _load() async {
    final prefs = await SharedPreferences.getInstance();
    state = prefs.getBool(_key) ?? false;
  }

  Future<void> toggle() async {
    state = !state;
    final prefs = await SharedPreferences.getInstance();
    await prefs.setBool(_key, state);
  }
}

final faceIdProvider = NotifierProvider<FaceIdController, bool>(FaceIdController.new);

class FaceIdController extends Notifier<bool> {
  static const _key = 'worthly.faceid';

  @override
  bool build() {
    Future.microtask(_load);
    return true;
  }

  Future<void> _load() async {
    final prefs = await SharedPreferences.getInstance();
    state = prefs.getBool(_key) ?? true;
  }

  Future<void> setEnabled(bool value) async {
    state = value;
    final prefs = await SharedPreferences.getInstance();
    await prefs.setBool(_key, value);
  }
}

final obscureSwitcherProvider = NotifierProvider<ObscureSwitcherController, bool>(ObscureSwitcherController.new);

class ObscureSwitcherController extends Notifier<bool> {
  static const _key = 'worthly.obscure';

  @override
  bool build() {
    Future.microtask(_load);
    return true;
  }

  Future<void> _load() async {
    final prefs = await SharedPreferences.getInstance();
    state = prefs.getBool(_key) ?? true;
  }

  Future<void> setEnabled(bool value) async {
    state = value;
    final prefs = await SharedPreferences.getInstance();
    await prefs.setBool(_key, value);
  }
}

final sessionProvider = NotifierProvider<SessionController, SessionState>(SessionController.new);

class SessionController extends Notifier<SessionState> {
  bool _externalFlow = false;

  @override
  SessionState build() {
    Future.microtask(bootstrap);
    return const SessionState(phase: SessionPhase.boot);
  }

  void lockIfEnabled() {
    if (_externalFlow) {
      return;
    }
    if (state.phase != SessionPhase.ready || !ref.read(faceIdProvider)) {
      return;
    }
    state = SessionState(phase: SessionPhase.locked, owner: state.owner);
  }

  Future<void> bootstrap() async {
    final restored = await ref.read(authRepositoryProvider).restore();
    if (!restored) {
      state = const SessionState(phase: SessionPhase.signedOut);
      return;
    }
    final prefs = await SharedPreferences.getInstance();
    if (prefs.getBool('worthly.faceid') ?? true) {
      state = const SessionState(phase: SessionPhase.locked);
      return;
    }
    await _loadOwner();
  }

  Future<void> unlock() async {
    _externalFlow = true;
    try {
      final ok = await LocalAuthentication().authenticate(
        localizedReason: 'Unlock Worthly',
        persistAcrossBackgrounding: true,
      );
      if (ok) {
        await _ready();
      }
    } catch (_) {
      await _ready();
    } finally {
      _externalFlow = false;
    }
  }

  Future<void> signIn({required String email, required String password, String? totpCode}) async {
    try {
      await ref.read(authRepositoryProvider).passwordLogin(email: email, password: password, totpCode: totpCode);
      await _loadOwner();
    } on AuthFailure catch (failure) {
      if (failure.code != 'totp_required' && failure.code != 'totp_invalid') {
        state = const SessionState(phase: SessionPhase.signedOut, error: 'Check your email and password.');
      }
      rethrow;
    } catch (_) {
      state = const SessionState(phase: SessionPhase.signedOut, error: 'Sign-in did not complete.');
    }
  }

  Future<void> runExternal(Future<void> Function() action) async {
    _externalFlow = true;
    try {
      await action();
    } finally {
      _externalFlow = false;
    }
  }

  Future<void> syncNow() async {
    state = state.copyWith(syncing: true);
    try {
      final client = ref.read(worthlyClientProvider);
      final connections = await client.get('/connections', (json) => listOf(json, Connection.fromJson));
      for (final connection in connections) {
        if (connection.status == 'ACTIVE' || connection.status == 'ERROR') {
          await client.send('POST', '/connections/${connection.id}/sync');
        }
      }
    } finally {
      state = state.copyWith(syncing: false);
    }
  }

  Future<void> logout() async {
    await ref.read(authRepositoryProvider).logout();
    state = const SessionState(phase: SessionPhase.signedOut);
  }

  Future<void> _ready() async {
    if (state.owner != null) {
      state = SessionState(phase: SessionPhase.ready, owner: state.owner);
      return;
    }
    await _loadOwner();
  }

  Future<void> _loadOwner() async {
    try {
      final owner = await ref.read(worthlyClientProvider).get('/me', parseOwner);
      state = SessionState(phase: SessionPhase.ready, owner: owner);
    } catch (_) {
      state = const SessionState(phase: SessionPhase.signedOut, error: 'Unable to load your profile.');
    }
  }

  Future<void> refreshOwner() async {
    if (state.owner == null) {
      return;
    }
    try {
      final owner = await ref.read(worthlyClientProvider).get('/me', parseOwner);
      state = state.copyWith(owner: owner);
    } catch (_) {
      /* keep previous owner */
    }
  }
}
