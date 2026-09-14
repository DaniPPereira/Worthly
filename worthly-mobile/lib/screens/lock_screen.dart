import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:worthly_mobile/features/session/session.dart';
import 'package:worthly_mobile/theme/colors.dart';
import 'package:worthly_mobile/widgets/rising_w.dart';

class LockScreen extends ConsumerWidget {
  const LockScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    return Scaffold(
      backgroundColor: WorthlyColors.pine,
      body: SafeArea(
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 40),
          child: Column(
            children: [
              const Spacer(),
              Container(
                width: 76,
                height: 76,
                decoration: BoxDecoration(
                  color: WorthlyColors.pineDeep,
                  borderRadius: BorderRadius.circular(20),
                  border: Border.all(color: WorthlyColors.cream.withValues(alpha: 0.18)),
                ),
                child: const Center(child: RisingW(size: 44)),
              ),
              const SizedBox(height: 24),
              const Text(
                'Worthly',
                style: TextStyle(
                  fontSize: 29,
                  fontWeight: FontWeight.w600,
                  color: WorthlyColors.cream,
                  letterSpacing: -0.8,
                ),
              ),
              const SizedBox(height: 8),
              Text(
                'Locked',
                style: TextStyle(fontSize: 13, color: WorthlyColors.cream.withValues(alpha: 0.6)),
              ),
              const SizedBox(height: 44),
              SizedBox(
                width: double.infinity,
                height: 54,
                child: FilledButton(
                  style: FilledButton.styleFrom(
                    backgroundColor: WorthlyColors.cream,
                    foregroundColor: WorthlyColors.pine,
                    shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(14)),
                  ),
                  onPressed: () => ref.read(sessionProvider.notifier).unlock(),
                  child: const Text('Unlock with Face ID', style: TextStyle(fontWeight: FontWeight.w600, fontSize: 15)),
                ),
              ),
              TextButton(
                onPressed: () => ref.read(sessionProvider.notifier).unlock(),
                child: Text('Use passcode instead', style: TextStyle(color: WorthlyColors.cream.withValues(alpha: 0.72))),
              ),
              const Spacer(),
              Padding(
                padding: const EdgeInsets.only(bottom: 24),
                child: Text(
                  'Face ID unlocks a local session only. Every request is still authorized by your Worthly API.',
                  textAlign: TextAlign.center,
                  style: TextStyle(fontSize: 11, height: 1.5, color: WorthlyColors.cream.withValues(alpha: 0.45)),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
