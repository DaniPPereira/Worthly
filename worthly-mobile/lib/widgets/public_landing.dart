import 'package:flutter/material.dart';
import 'package:url_launcher/url_launcher.dart';
import 'package:worthly_mobile/features/auth/auth_config.dart';
import 'package:worthly_mobile/theme/colors.dart';
import 'package:worthly_mobile/theme/theme.dart';
import 'package:worthly_mobile/widgets/rising_w.dart';

class BrandMark extends StatelessWidget {
  const BrandMark({super.key, this.onDark = false, this.size = 22});

  final bool onDark;
  final double size;

  @override
  Widget build(BuildContext context) {
    return Container(
      width: 40,
      height: 40,
      decoration: BoxDecoration(
        color: onDark ? WorthlyColors.cream.withValues(alpha: 0.1) : WorthlyColors.pine,
        borderRadius: BorderRadius.circular(12),
      ),
      alignment: Alignment.center,
      child: RisingW(size: size, onDark: true),
    );
  }
}

class PublicLanding extends StatelessWidget {
  const PublicLanding({super.key, required this.child});

  final Widget child;

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: WorthlyColors.paper,
      body: SafeArea(
        child: LayoutBuilder(
          builder: (context, constraints) {
            return SingleChildScrollView(
              child: ConstrainedBox(
                constraints: BoxConstraints(minHeight: constraints.maxHeight),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  children: [
                    const _PublicHero(),
                    Padding(
                      padding: const EdgeInsets.fromLTRB(24, 28, 24, 40),
                      child: child,
                    ),
                  ],
                ),
              ),
            );
          },
        ),
      ),
    );
  }
}

class _PublicHero extends StatelessWidget {
  const _PublicHero();

  @override
  Widget build(BuildContext context) {
    return ColoredBox(
      color: WorthlyColors.pineDeep,
      child: Padding(
        padding: const EdgeInsets.fromLTRB(24, 28, 24, 24),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text('WORTHLY', style: labelStyle(color: WorthlyColors.gold)),
            const SizedBox(height: 14),
            Text('See what you have.', style: serif(size: 32)),
            const SizedBox(height: 12),
            const Text(
              'Banks, cash and investments.',
              style: TextStyle(fontSize: 16, height: 1.45, color: Color(0xB8F4F1EA)),
            ),
            const SizedBox(height: 22),
            Container(
              width: double.infinity,
              padding: const EdgeInsets.fromLTRB(18, 16, 18, 16),
              decoration: BoxDecoration(color: WorthlyColors.ink, borderRadius: BorderRadius.circular(16)),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text('YOUR ACCOUNT', style: labelStyle(color: const Color(0x8CF4F1EA))),
                  const SizedBox(height: 10),
                  Text('Private. Read-only.', style: serif(size: 22)),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class PublicPanelHeader extends StatelessWidget {
  const PublicPanelHeader({super.key, required this.title, this.subtitle});

  final String title;
  final String? subtitle;

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        const Row(
          children: [
            BrandMark(),
            SizedBox(width: 12),
            Text(
              'Worthly',
              style: TextStyle(fontSize: 22, fontWeight: FontWeight.w600, letterSpacing: -0.6, color: WorthlyColors.ink),
            ),
          ],
        ),
        const SizedBox(height: 28),
        Text(title, style: serif(size: 32, color: WorthlyColors.ink)),
        if (subtitle != null) ...[
          const SizedBox(height: 10),
          Text(subtitle!, style: const TextStyle(fontSize: 14, height: 1.45, color: WorthlyColors.muted)),
        ],
      ],
    );
  }
}

class PublicPrimaryButton extends StatelessWidget {
  const PublicPrimaryButton({super.key, required this.label, required this.onPressed});

  final String label;
  final VoidCallback? onPressed;

  @override
  Widget build(BuildContext context) {
    return SizedBox(
      width: double.infinity,
      height: 44,
      child: FilledButton(
        onPressed: onPressed,
        style: FilledButton.styleFrom(
          backgroundColor: WorthlyColors.pine,
          foregroundColor: WorthlyColors.cream,
          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(9)),
          textStyle: const TextStyle(fontWeight: FontWeight.w600, fontSize: 12.5),
        ),
        child: Text(label),
      ),
    );
  }
}

class PublicGhostButton extends StatelessWidget {
  const PublicGhostButton({super.key, required this.label, required this.onPressed});

  final String label;
  final VoidCallback? onPressed;

  @override
  Widget build(BuildContext context) {
    return SizedBox(
      width: double.infinity,
      height: 44,
      child: OutlinedButton(
        onPressed: onPressed,
        style: OutlinedButton.styleFrom(
          foregroundColor: WorthlyColors.ink,
          backgroundColor: WorthlyColors.white,
          side: BorderSide(color: WorthlyColors.ink.withValues(alpha: 0.12)),
          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(9)),
          textStyle: const TextStyle(fontWeight: FontWeight.w600, fontSize: 12.5),
        ),
        child: Text(label),
      ),
    );
  }
}

class PublicLegalLinks extends StatelessWidget {
  const PublicLegalLinks({super.key});

  @override
  Widget build(BuildContext context) {
    return const Padding(
      padding: EdgeInsets.only(top: 18),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          _LegalLink(label: 'Privacy', path: '/privacy'),
          Text(' · ', style: TextStyle(color: WorthlyColors.muted, fontSize: 12)),
          _LegalLink(label: 'Terms', path: '/terms'),
        ],
      ),
    );
  }
}

class _LegalLink extends StatelessWidget {
  const _LegalLink({required this.label, required this.path});

  final String label;
  final String path;

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: () => launchUrl(Uri.parse('${AuthConfig.local.webOrigin}$path'), mode: LaunchMode.externalApplication),
      child: Text(label, style: const TextStyle(color: WorthlyColors.muted, fontSize: 12)),
    );
  }
}

InputDecoration publicFieldDecoration() {
  return InputDecoration(
    filled: true,
    fillColor: WorthlyColors.white,
    contentPadding: const EdgeInsets.symmetric(horizontal: 12, vertical: 12),
    border: OutlineInputBorder(
      borderRadius: BorderRadius.circular(9),
      borderSide: BorderSide(color: WorthlyColors.ink.withValues(alpha: 0.12)),
    ),
    enabledBorder: OutlineInputBorder(
      borderRadius: BorderRadius.circular(9),
      borderSide: BorderSide(color: WorthlyColors.ink.withValues(alpha: 0.12)),
    ),
    focusedBorder: const OutlineInputBorder(
      borderRadius: BorderRadius.all(Radius.circular(9)),
      borderSide: BorderSide(color: WorthlyColors.pine),
    ),
  );
}
