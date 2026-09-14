import 'dart:async';

import 'package:app_links/app_links.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:worthly_mobile/features/session/session.dart';
import 'package:worthly_mobile/features/session/shell_data.dart';
import 'package:worthly_mobile/screens/lock_screen.dart';
import 'package:worthly_mobile/screens/shell.dart';
import 'package:worthly_mobile/screens/sign_in_screen.dart';
import 'package:worthly_mobile/theme/colors.dart';
import 'package:worthly_mobile/theme/theme.dart';
import 'package:worthly_mobile/widgets/rising_w.dart';

class WorthlyApp extends ConsumerStatefulWidget {
  const WorthlyApp({super.key});

  @override
  ConsumerState<WorthlyApp> createState() => _WorthlyAppState();
}

class _WorthlyAppState extends ConsumerState<WorthlyApp> with WidgetsBindingObserver {
  StreamSubscription<Uri>? _links;
  bool _obscured = false;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    final appLinks = AppLinks();
    _links = appLinks.uriLinkStream.listen(_onLink);
    appLinks.getInitialLink().then((uri) {
      if (uri != null) {
        _onLink(uri);
      }
    });
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    _links?.cancel();
    super.dispose();
  }

  void _onLink(Uri uri) {
    if (uri.scheme != 'worthly') {
      return;
    }
    if (uri.host == 'connections' && uri.path.contains('result')) {
      ref.read(connectionResultProvider.notifier).state = uri.queryParameters['status'];
      ref.read(tabIndexProvider.notifier).state = 3;
      ref.read(connectionsOpenProvider.notifier).state = true;
      ref.invalidate(shellDataProvider);
    }
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.paused) {
      if (ref.read(obscureSwitcherProvider)) {
        setState(() => _obscured = true);
      }
      ref.read(sessionProvider.notifier).lockIfEnabled();
    }
    if (state == AppLifecycleState.resumed) {
      setState(() => _obscured = false);
      if (ref.read(sessionProvider).phase == SessionPhase.ready) {
        ref.invalidate(shellDataProvider);
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final session = ref.watch(sessionProvider);
    return MaterialApp(
      title: 'Worthly',
      debugShowCheckedModeBanner: false,
      theme: worthlyTheme(),
      home: Stack(
        children: [
          switch (session.phase) {
            SessionPhase.boot => const Scaffold(
              backgroundColor: WorthlyColors.paper,
              body: Center(child: CircularProgressIndicator(color: WorthlyColors.pine)),
            ),
            SessionPhase.signedOut => const SignInScreen(),
            SessionPhase.locked => const LockScreen(),
            SessionPhase.ready => const AppShell(),
          },
          if (_obscured)
            const Positioned.fill(
              child: ColoredBox(
                color: WorthlyColors.pine,
                child: Center(child: RisingW(size: 56)),
              ),
            ),
        ],
      ),
    );
  }
}
