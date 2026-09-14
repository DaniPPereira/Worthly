import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:worthly_mobile/features/session/session.dart';
import 'package:worthly_mobile/screens/lock_screen.dart';
import 'package:worthly_mobile/screens/sign_in_screen.dart';
import 'package:worthly_mobile/theme/theme.dart';

class _LockedSession extends SessionController {
  @override
  SessionState build() => const SessionState(phase: SessionPhase.locked);
}

class _SignedOutSession extends SessionController {
  @override
  SessionState build() => const SessionState(phase: SessionPhase.signedOut);
}

void main() {
  testWidgets('lock screen states Face ID is local-only', (tester) async {
    await tester.pumpWidget(
      ProviderScope(
        overrides: [sessionProvider.overrideWith(_LockedSession.new)],
        child: MaterialApp(theme: worthlyTheme(), home: const LockScreen()),
      ),
    );
    expect(find.text('Unlock with Face ID'), findsOneWidget);
    expect(find.textContaining('local session only'), findsOneWidget);
  });

  testWidgets('sign-in does not collect a password', (tester) async {
    await tester.pumpWidget(
      ProviderScope(
        overrides: [sessionProvider.overrideWith(_SignedOutSession.new)],
        child: MaterialApp(theme: worthlyTheme(), home: const SignInScreen()),
      ),
    );
    expect(find.text('Sign in'), findsOneWidget);
    expect(find.text('Password'), findsNothing);
    expect(find.textContaining('never collects your password'), findsOneWidget);
  });
}
