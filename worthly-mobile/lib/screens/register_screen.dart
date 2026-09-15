import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:worthly_mobile/features/session/session.dart';
import 'package:worthly_mobile/theme/colors.dart';
import 'package:worthly_mobile/theme/theme.dart';
import 'package:worthly_mobile/widgets/public_landing.dart';

class RegisterScreen extends ConsumerStatefulWidget {
  const RegisterScreen({super.key});

  @override
  ConsumerState<RegisterScreen> createState() => _RegisterScreenState();
}

class _RegisterScreenState extends ConsumerState<RegisterScreen> {
  final _name = TextEditingController();
  final _email = TextEditingController();
  final _password = TextEditingController();
  String? _error;
  bool _busy = false;
  bool _showPassword = false;

  @override
  void dispose() {
    _name.dispose();
    _email.dispose();
    _password.dispose();
    super.dispose();
  }

  Future<void> _submit() async {
    setState(() {
      _error = null;
      _busy = true;
    });
    try {
      await ref.read(worthlyClientProvider).register(
        name: _name.text.trim(),
        email: _email.text.trim(),
        password: _password.text,
      );
      if (!mounted) {
        return;
      }
      Navigator.of(context).pop(true);
    } catch (error) {
      if (!mounted) {
        return;
      }
      setState(() {
        _error = error.toString().contains('email_taken')
            ? 'That email is already registered.'
            : 'Use a name, a valid email and a password of at least 8 characters.';
      });
    } finally {
      if (mounted) {
        setState(() => _busy = false);
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    return PublicLanding(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const PublicPanelHeader(title: 'Create your account.', subtitle: 'Then sign in.'),
          if (_error != null) ...[
            const SizedBox(height: 12),
            Text(_error!, style: const TextStyle(color: WorthlyColors.loss, fontSize: 13)),
          ],
          const SizedBox(height: 18),
          Text('NAME', style: labelStyle()),
          const SizedBox(height: 6),
          TextField(
            controller: _name,
            textCapitalization: TextCapitalization.words,
            autofillHints: const [AutofillHints.name],
            textInputAction: TextInputAction.next,
            decoration: publicFieldDecoration(),
          ),
          const SizedBox(height: 12),
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
            autofillHints: const [AutofillHints.newPassword],
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
          PublicPrimaryButton(label: _busy ? 'Creating…' : 'Create account', onPressed: _busy ? null : _submit),
          const SizedBox(height: 16),
          Center(
            child: TextButton(
              onPressed: _busy ? null : () => Navigator.of(context).pop(),
              child: const Text('Already have an account? Sign in', style: TextStyle(color: WorthlyColors.muted, fontSize: 13)),
            ),
          ),
          const PublicLegalLinks(),
        ],
      ),
    );
  }
}
