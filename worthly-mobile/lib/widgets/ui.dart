import 'package:flutter/material.dart';
import 'package:worthly_mobile/theme/colors.dart';
import 'package:worthly_mobile/theme/period.dart';

class WorthlyCard extends StatelessWidget {
  const WorthlyCard({super.key, required this.child, this.padding = const EdgeInsets.all(16)});

  final Widget child;
  final EdgeInsets padding;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: padding,
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(18),
        border: Border.all(color: WorthlyColors.ink.withValues(alpha: 0.07)),
      ),
      child: child,
    );
  }
}

class EmptyState extends StatelessWidget {
  const EmptyState({super.key, required this.title, required this.body});

  final String title;
  final String body;

  @override
  Widget build(BuildContext context) {
    return WorthlyCard(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(title, style: const TextStyle(fontWeight: FontWeight.w600, fontSize: 15)),
          const SizedBox(height: 6),
          Text(body, style: const TextStyle(fontSize: 13, height: 1.5, color: WorthlyColors.muted)),
        ],
      ),
    );
  }
}

class LoadingBody extends StatelessWidget {
  const LoadingBody({super.key});

  @override
  Widget build(BuildContext context) {
    return const Center(child: CircularProgressIndicator(color: WorthlyColors.pine));
  }
}

class StatusChip extends StatelessWidget {
  const StatusChip({super.key, required this.status});

  final String status;

  @override
  Widget build(BuildContext context) {
    final tone = statusTone(status);
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 3),
      decoration: BoxDecoration(
        color: tone.bg,
        borderRadius: BorderRadius.circular(5),
        border: Border.all(color: tone.bd),
      ),
      child: Text(
        tone.label,
        style: TextStyle(
          fontFamily: 'IBM Plex Mono',
          fontSize: 9.5,
          letterSpacing: 0.8,
          color: tone.fg,
          fontWeight: FontWeight.w500,
        ),
      ),
    );
  }
}

({Color fg, Color bg, Color bd, String label}) statusTone(String status) {
  switch (status) {
    case 'ACTIVE':
      return (fg: WorthlyColors.gain, bg: const Color(0x1A14654A), bd: WorthlyColors.gain.withValues(alpha: 0.3), label: 'Active');
    case 'REAUTH_REQUIRED':
      return (fg: WorthlyColors.warn, bg: WorthlyColors.warnBg, bd: WorthlyColors.warn.withValues(alpha: 0.35), label: 'Reauth required');
    case 'CONFIGURATION_REQUIRED':
      return (fg: WorthlyColors.warn, bg: WorthlyColors.warnBg, bd: WorthlyColors.warn.withValues(alpha: 0.35), label: 'Configuration required');
    case 'ERROR':
      return (fg: WorthlyColors.loss, bg: const Color(0x1AA04A34), bd: WorthlyColors.loss.withValues(alpha: 0.3), label: 'Error');
    case 'DISABLED':
      return (fg: WorthlyColors.faint, bg: WorthlyColors.paper, bd: WorthlyColors.ink.withValues(alpha: 0.12), label: 'Disabled');
    default:
      return (fg: WorthlyColors.ink, bg: WorthlyColors.paper, bd: WorthlyColors.ink.withValues(alpha: 0.12), label: status);
  }
}

class CurrencyPills extends StatelessWidget {
  const CurrencyPills({super.key, required this.currencies, required this.selected, required this.onSelect});

  final List<String> currencies;
  final String selected;
  final ValueChanged<String> onSelect;

  @override
  Widget build(BuildContext context) {
    if (currencies.length < 2) {
      return const SizedBox.shrink();
    }
    return Row(
      children: [
        for (final currency in currencies)
          Padding(
            padding: const EdgeInsets.only(right: 8),
            child: ChoiceChip(
              label: Text(currency),
              selected: currency == selected,
              onSelected: (_) => onSelect(currency),
              selectedColor: WorthlyColors.pine,
              labelStyle: TextStyle(
                color: currency == selected ? WorthlyColors.cream : WorthlyColors.ink,
                fontSize: 12,
                fontWeight: FontWeight.w500,
              ),
              side: BorderSide(color: currency == selected ? WorthlyColors.pine : WorthlyColors.ink.withValues(alpha: 0.12)),
              backgroundColor: Colors.white,
              showCheckmark: false,
            ),
          ),
      ],
    );
  }
}

class MonthNav extends StatelessWidget {
  const MonthNav({
    super.key,
    required this.month,
    required this.currentMonth,
    required this.onChange,
  });

  final String month;
  final String currentMonth;
  final ValueChanged<String> onChange;

  @override
  Widget build(BuildContext context) {
    final atLatest = month.compareTo(currentMonth) >= 0;
    return Row(
      children: [
        IconButton(
          tooltip: 'Previous month',
          onPressed: () => onChange(Period.shiftMonthKey(month, -1)),
          icon: const Icon(Icons.chevron_left),
        ),
        Expanded(
          child: Text(
            Period.monthTitle(month),
            textAlign: TextAlign.center,
            style: const TextStyle(fontWeight: FontWeight.w600, fontSize: 14),
          ),
        ),
        IconButton(
          tooltip: 'Next month',
          onPressed: atLatest ? null : () => onChange(Period.shiftMonthKey(month, 1)),
          icon: const Icon(Icons.chevron_right),
        ),
        if (month != currentMonth)
          TextButton(
            onPressed: () => onChange(currentMonth),
            child: const Text('This month'),
          ),
      ],
    );
  }
}

class FilterChipBar extends StatelessWidget {
  const FilterChipBar({super.key, required this.labels, required this.selected, required this.onSelect});

  final List<String> labels;
  final String selected;
  final ValueChanged<String> onSelect;

  @override
  Widget build(BuildContext context) {
    return SingleChildScrollView(
      scrollDirection: Axis.horizontal,
      child: Row(
        children: [
          for (final label in labels)
            Padding(
              padding: const EdgeInsets.only(right: 7),
              child: InkWell(
                onTap: () => onSelect(label),
                borderRadius: BorderRadius.circular(9),
                child: Container(
                  padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 7),
                  decoration: BoxDecoration(
                    color: label == selected ? WorthlyColors.pine : Colors.white,
                    borderRadius: BorderRadius.circular(9),
                    border: Border.all(
                      color: label == selected ? WorthlyColors.pine : WorthlyColors.ink.withValues(alpha: 0.12),
                    ),
                  ),
                  child: Text(
                    label,
                    style: TextStyle(
                      fontSize: 12,
                      fontWeight: FontWeight.w500,
                      color: label == selected ? WorthlyColors.cream : WorthlyColors.ink,
                    ),
                  ),
                ),
              ),
            ),
        ],
      ),
    );
  }
}

class HeaderIconButton extends StatelessWidget {
  const HeaderIconButton({super.key, required this.icon, required this.onPressed, this.active = false});

  final IconData icon;
  final VoidCallback onPressed;
  final bool active;

  @override
  Widget build(BuildContext context) {
    return InkWell(
      onTap: onPressed,
      borderRadius: BorderRadius.circular(10),
      child: Container(
        width: 34,
        height: 34,
        decoration: BoxDecoration(
          color: active ? WorthlyColors.pine : Colors.white,
          borderRadius: BorderRadius.circular(10),
          border: Border.all(color: active ? WorthlyColors.pine : WorthlyColors.ink.withValues(alpha: 0.12)),
        ),
        child: Icon(icon, size: 17, color: active ? WorthlyColors.cream : const Color(0xFF3E4A47)),
      ),
    );
  }
}

class PineButton extends StatelessWidget {
  const PineButton({super.key, required this.label, required this.onPressed});

  final String label;
  final VoidCallback? onPressed;

  @override
  Widget build(BuildContext context) {
    return SizedBox(
      width: double.infinity,
      height: 48,
      child: FilledButton(
        onPressed: onPressed,
        style: FilledButton.styleFrom(
          backgroundColor: WorthlyColors.pine,
          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
        ),
        child: Text(label, style: const TextStyle(fontWeight: FontWeight.w600, fontSize: 13.5)),
      ),
    );
  }
}
