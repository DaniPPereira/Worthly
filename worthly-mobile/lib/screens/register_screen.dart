import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:worthly_mobile/features/session/session.dart';
import 'package:worthly_mobile/theme/colors.dart';
import 'package:worthly_mobile/theme/theme.dart';
import 'package:worthly_mobile/widgets/rising_w.dart';
import 'package:worthly_mobile/widgets/ui.dart';

class RegisterScreen extends ConsumerStatefulWidget {
  const RegisterScreen({super.key});

  @override
  ConsumerState<RegisterScreen> createState() => _RegisterScreenState();
}

class _RegisterScreenState extends ConsumerState<RegisterScreen> {
  final _email = TextEditingController();
  final _password = TextEditingController();
  String? _error;
  bool _busy = false;
  bool _showPassword = false;

  @override
  void dispose() {
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
      await ref.read(worthlyClientProvider).register(email: _email.text.trim(), password: _password.text);
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
            : 'Use a valid email and a password of at least 14 characters.';
      });
    } finally {
      if (mounted) {
        setState(() => _busy = false);
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: WorthlyColors.paper,
      body: SafeArea(
        child: Padding(
          padding: const EdgeInsets.fromLTRB(28, 28, 28, 28),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              IconButton(
                onPressed: () => Navigator.of(context).pop(),
                icon: const Icon(Icons.chevron_left),
                color: WorthlyColors.pine,
              ),
              const RisingW(size: 42, onDark: false),
              const SizedBox(height: 16),
              const Text(
                'Create your account',
                style: TextStyle(fontSize: 32, fontWeight: FontWeight.w600, letterSpacing: -1, color: WorthlyColors.ink),
              ),
              const SizedBox(height: 10),
              const Text(
                'Each account keeps its own banks and investments. Sign-in afterwards still happens in the system browser.',
                style: TextStyle(fontSize: 14, height: 1.55, color: WorthlyColors.muted),
              ),
              const SizedBox(height: 28),
              Text('EMAIL', style: labelStyle()),
              const SizedBox(height: 6),
              TextField(
                controller: _email,
                keyboardType: TextInputType.emailAddress,
                autofillHints: const [AutofillHints.username],
                decoration: _fieldDecoration(),
              ),
              const SizedBox(height: 16),
              Text('PASSWORD', style: labelStyle()),
              const SizedBox(height: 6),
              TextField(
                controller: _password,
                obscureText: !_showPassword,
                autofillHints: const [AutofillHints.newPassword],
                decoration: _fieldDecoration().copyWith(
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
              if (_error != null) ...[
                const SizedBox(height: 14),
                Text(_error!, style: const TextStyle(color: WorthlyColors.loss, fontSize: 13)),
              ],
              const SizedBox(height: 24),
              PineButton(label: 'Create account', onPressed: _busy ? null : _submit),
            ],
          ),
        ),
      ),
    );
  }

  InputDecoration _fieldDecoration() {
    return InputDecoration(
      filled: true,
      fillColor: Colors.white,
      contentPadding: const EdgeInsets.symmetric(horizontal: 14, vertical: 14),
      border: OutlineInputBorder(
        borderRadius: BorderRadius.circular(12),
        borderSide: BorderSide(color: WorthlyColors.ink.withValues(alpha: 0.12)),
      ),
      enabledBorder: OutlineInputBorder(
        borderRadius: BorderRadius.circular(12),
        borderSide: BorderSide(color: WorthlyColors.ink.withValues(alpha: 0.12)),
      ),
    );
  }
}
