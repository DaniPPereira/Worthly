import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:worthly_mobile/features/auth/auth_config.dart';
import 'package:worthly_mobile/features/auth/auth_controller.dart';
import 'package:worthly_mobile/features/me/me_repository.dart';
import 'package:worthly_mobile/features/me/owner.dart';

final meRepositoryProvider = Provider<MeRepository>((ref) {
  return MeRepository(
    auth: ref.watch(authRepositoryProvider),
    config: AuthConfig.local,
  );
});

final meControllerProvider = FutureProvider<Owner?>((ref) {
  return ref.watch(meRepositoryProvider).current();
});
