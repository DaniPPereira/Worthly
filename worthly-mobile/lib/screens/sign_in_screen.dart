import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:worthly_mobile/features/auth/auth_config.dart';
import 'package:worthly_mobile/features/session/session.dart';
import 'package:worthly_mobile/theme/colors.dart';
import 'package:worthly_mobile/theme/theme.dart';
import 'package:worthly_mobile/widgets/rising_w.dart';

class SignInScreen extends ConsumerWidget {
  const SignInScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final session = ref.watch(sessionProvider);
    final faceId = ref.watch(faceIdProvider);
    return Scaffold(
      backgroundColor: WorthlyColors.paper,
      body: SafeArea(
        child: Padding(
          padding: const EdgeInsets.fromLTRB(28, 72, 28, 28),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              const RisingW(size: 42, onDark: false),
              const SizedBox(height: 16),
              const Text(
                'Worthly',
                style: TextStyle(fontSize: 36, fontWeight: FontWeight.w600, letterSpacing: -1, color: WorthlyColors.ink),
              ),
              const SizedBox(height: 10),
              const Text(
                'Sign in to your own Worthly server. Your bank credentials are never entered here.',
                style: TextStyle(fontSize: 14, height: 1.55, color: WorthlyColors.muted),
              ),
              const SizedBox(height: 34),
              Text('SERVER', style: labelStyle()),
              const SizedBox(height: 6),
              Container(
                width: double.infinity,
                padding: const EdgeInsets.all(15),
                decoration: BoxDecoration(
                  color: Colors.white,
                  borderRadius: BorderRadius.circular(12),
                  border: Border.all(color: WorthlyColors.ink.withValues(alpha: 0.12)),
                ),
                child: Text(Uri.parse(AuthConfig.local.issuer).host, style: mono(size: 14)),
              ),
              const SizedBox(height: 20),
              GestureDetector(
                onTap: () => ref.read(faceIdProvider.notifier).setEnabled(!faceId),
                child: Row(
                  children: [
                    Container(
                      width: 22,
                      height: 22,
                      decoration: BoxDecoration(
                        color: faceId ? WorthlyColors.pine : Colors.white,
                        borderRadius: BorderRadius.circular(6),
                        border: Border.all(color: WorthlyColors.pine),
                      ),
                      child: faceId ? const Icon(Icons.check, size: 14, color: WorthlyColors.cream) : null,
                    ),
                    const SizedBox(width: 12),
                    const Expanded(
                      child: Text('Enable Face ID on this device', style: TextStyle(fontSize: 13.5)),
                    ),
                  ],
                ),
              ),
              const SizedBox(height: 26),
              if (session.error != null)
                Padding(
                  padding: const EdgeInsets.only(bottom: 12),
                  child: Text(session.error!, style: const TextStyle(color: WorthlyColors.loss, fontSize: 13)),
                ),
              SizedBox(
                width: double.infinity,
                height: 54,
                child: FilledButton(
                  onPressed: () => ref.read(sessionProvider.notifier).signIn(),
                  style: FilledButton.styleFrom(
                    backgroundColor: WorthlyColors.pine,
                    shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(14)),
                  ),
                  child: const Text('Sign in', style: TextStyle(fontWeight: FontWeight.w600, fontSize: 15)),
                ),
              ),
              const Spacer(),
              const Text(
                'Worthly holds no funds and cannot move money. Provider access is read-only. Sign-in uses Authorization Code + PKCE in the system browser — this app never collects your password.',
                textAlign: TextAlign.center,
                style: TextStyle(fontSize: 11, height: 1.5, color: WorthlyColors.faint),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
