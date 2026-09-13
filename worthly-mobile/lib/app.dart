import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:worthly_mobile/features/auth/auth_controller.dart';
import 'package:worthly_mobile/features/me/me_controller.dart';

class WorthlyApp extends StatelessWidget {
  const WorthlyApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Worthly',
      home: const HomeScreen(),
    );
  }
}

class HomeScreen extends ConsumerWidget {
  const HomeScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final auth = ref.watch(authControllerProvider);
    final me = ref.watch(meControllerProvider);
    return Scaffold(
      appBar: AppBar(title: const Text('Worthly')),
      body: Padding(
        padding: const EdgeInsets.all(24),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Text('Phase 0 authentication skeleton'),
            const SizedBox(height: 16),
            if (auth.authorizationUrl != null)
              SelectableText(auth.authorizationUrl!),
            const SizedBox(height: 16),
            ElevatedButton(
              onPressed: () => ref.read(authControllerProvider.notifier).startLogin(),
              child: const Text('Start PKCE sign-in'),
            ),
            const SizedBox(height: 16),
            me.when(
              data: (owner) => owner == null
                  ? const Text('Not signed in')
                  : Text('Signed in as ${owner.email}'),
              loading: () => const Text('Loading profile…'),
              error: (error, _) => Text('Unable to load profile: $error'),
            ),
          ],
        ),
      ),
    );
  }
}
