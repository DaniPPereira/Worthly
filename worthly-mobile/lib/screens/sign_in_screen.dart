import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:worthly_mobile/features/auth/auth_repository.dart';
import 'package:worthly_mobile/features/session/session.dart';
import 'package:worthly_mobile/screens/register_screen.dart';
import 'package:worthly_mobile/theme/colors.dart';
import 'package:worthly_mobile/theme/theme.dart';
import 'package:worthly_mobile/widgets/public_landing.dart';

class SignInScreen extends ConsumerStatefulWidget {
  const SignInScreen({super.key});

  @override
  ConsumerState<SignInScreen> createState() => _SignInScreenState();
}

enum _AuthPanel { land, signIn, totp }

class _SignInScreenState extends ConsumerState<SignInScreen> {
  final _email = TextEditingController();
  final _password = TextEditingController();
  final _totp = TextEditingController();
  _AuthPanel _panel = _AuthPanel.land;
  String? _error;
  bool _registered = false;
  bool _busy = false;
  bool _showPassword = false;

  @override
  void dispose() {
    _email.dispose();
    _password.dispose();
    _totp.dispose();
    super.dispose();
  }

  Future<void> _openRegister() async {
    final created = await Navigator.of(context).push<bool>(
      MaterialPageRoute(builder: (_) => const RegisterScreen()),
    );
    if (!mounted || created != true) {
      return;
    }
    setState(() {
      _panel = _AuthPanel.land;
      _registered = true;
      _error = null;
    });
  }

  Future<void> _submit({bool totp = false}) async {
    setState(() {
      _error = null;
      _busy = true;
    });
    try {
      await ref.read(sessionProvider.notifier).signIn(
        email: _email.text.trim(),
        password: _password.text,
        totpCode: totp ? _totp.text.trim() : null,
      );
    } on AuthFailure catch (failure) {
      if (!mounted) {
        return;
      }
      if (failure.code == 'totp_required') {
        setState(() {
          _panel = _AuthPanel.totp;
          _totp.clear();
        });
        return;
      }
      setState(() {
        _error = failure.code == 'totp_invalid'
            ? 'That code is not valid.'
            : 'Check your email and password.';
      });
    } finally {
      if (mounted) {
        setState(() => _busy = false);
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final sessionError = ref.watch(sessionProvider).error;
    return PublicLanding(
      child: switch (_panel) {
        _AuthPanel.land => _land(sessionError),
        _AuthPanel.signIn => _signIn(),
        _AuthPanel.totp => _totpPanel(),
      },
    );
  }

  Widget _land(String? sessionError) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        const PublicPanelHeader(title: 'Start here.', subtitle: 'Create an account, or sign in.'),
        if (_registered) ...[
          const SizedBox(height: 12),
          const Text('Account created. Sign in to continue.', style: TextStyle(color: WorthlyColors.gain, fontSize: 13)),
        ],
        if (sessionError != null) ...[
          const SizedBox(height: 12),
          Text(sessionError, style: const TextStyle(color: WorthlyColors.loss, fontSize: 13)),
        ],
        const SizedBox(height: 18),
        PublicPrimaryButton(label: 'Create an account', onPressed: _busy ? null : _openRegister),
        const SizedBox(height: 18),
        PublicGhostButton(
          label: 'Sign in',
          onPressed: _busy
              ? null
              : () => setState(() {
                    _panel = _AuthPanel.signIn;
                    _error = null;
                  }),
        ),
        const PublicLegalLinks(),
      ],
    );
  }

  Widget _signIn() {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        const PublicPanelHeader(title: 'Sign in.', subtitle: 'Use the email and password for your account.'),
        if (_error != null) ...[
          const SizedBox(height: 12),
          Text(_error!, style: const TextStyle(color: WorthlyColors.loss, fontSize: 13)),
        ],
        const SizedBox(height: 18),
        Text('EMAIL', style: labelStyle()),
        const SizedBox(height: 6),
        TextField(
          controller: _email,
          keyboardType: TextInputType.emailAddress,
          autofillHints: const [AutofillHints.username],
          textInputAction: TextInputAction.next,
          decoration: publicFieldDecoration(),
        ),
        const SizedBox(height: 12),
        Text('PASSWORD', style: labelStyle()),
        const SizedBox(height: 6),
        TextField(
          controller: _password,
          obscureText: !_showPassword,
          autofillHints: const [AutofillHints.password],
          textInputAction: TextInputAction.done,
          onSubmitted: (_) {
            if (!_busy) {
              _submit();
            }
          },
          decoration: publicFieldDecoration().copyWith(
            suffixIcon: IconButton(
              tooltip: _showPassword ? 'Hide password' : 'Show password',
              onPressed: () => setState(() => _showPassword = !_showPassword),
              icon: Icon(
                _showPassword ? Icons.visibility_off_outlined : Icons.visibility_outlined,
                size: 20,
                color: WorthlyColors.faint,
              ),
            ),
          ),
        ),
        const SizedBox(height: 18),
        PublicPrimaryButton(label: _busy ? 'Signing in…' : 'Sign in', onPressed: _busy ? null : () => _submit()),
        const SizedBox(height: 16),
        Center(
          child: TextButton(
            onPressed: _busy
                ? null
                : () => setState(() {
                      _panel = _AuthPanel.land;
                      _error = null;
                    }),
            child: const Text('Back', style: TextStyle(color: WorthlyColors.muted, fontSize: 13)),
          ),
        ),
      ],
    );
  }

  Widget _totpPanel() {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        const PublicPanelHeader(title: 'Enter your code.', subtitle: 'Use the authenticator app for this account.'),
        if (_error != null) ...[
          const SizedBox(height: 12),
          Text(_error!, style: const TextStyle(color: WorthlyColors.loss, fontSize: 13)),
        ],
        const SizedBox(height: 18),
        Text('CODE', style: labelStyle()),
        const SizedBox(height: 6),
        TextField(
          controller: _totp,
          keyboardType: TextInputType.visiblePassword,
          textCapitalization: TextCapitalization.characters,
          autofillHints: const [AutofillHints.oneTimeCode],
          textInputAction: TextInputAction.done,
          onSubmitted: (_) {
            if (!_busy) {
              _submit(totp: true);
            }
          },
          decoration: publicFieldDecoration(),
        ),
        const SizedBox(height: 18),
        PublicPrimaryButton(label: _busy ? 'Signing in…' : 'Continue', onPressed: _busy ? null : () => _submit(totp: true)),
        const SizedBox(height: 16),
        Center(
          child: TextButton(
            onPressed: _busy
                ? null
                : () => setState(() {
                      _panel = _AuthPanel.signIn;
                      _totp.clear();
                      _error = null;
                    }),
            child: const Text('Back', style: TextStyle(color: WorthlyColors.muted, fontSize: 13)),
          ),
        ),
      ],
    );
  }
}
