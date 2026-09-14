import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:worthly_mobile/features/session/session.dart';
import 'package:worthly_mobile/features/session/shell_data.dart';
import 'package:worthly_mobile/screens/accounts_screen.dart';
import 'package:worthly_mobile/screens/connections_screen.dart';
import 'package:worthly_mobile/screens/home_screen.dart';
import 'package:worthly_mobile/screens/investments_screen.dart';
import 'package:worthly_mobile/screens/settings_screen.dart';
import 'package:worthly_mobile/screens/transactions_screen.dart';
import 'package:worthly_mobile/theme/colors.dart';
import 'package:worthly_mobile/widgets/ui.dart';

const _titles = ['Worthly', 'Transactions', 'Investments', 'Accounts', 'Settings'];

class AppShell extends ConsumerWidget {
  const AppShell({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final tab = ref.watch(tabIndexProvider);
    final connectionsOpen = ref.watch(connectionsOpenProvider);
    final privacy = ref.watch(privacyProvider);
    final syncing = ref.watch(sessionProvider).syncing;
    final title = connectionsOpen && tab == 3 ? 'Connections' : _titles[tab];
    return Scaffold(
      backgroundColor: WorthlyColors.paper,
      body: Column(
        children: [
          SafeArea(
            bottom: false,
            child: Container(
              padding: const EdgeInsets.fromLTRB(18, 8, 18, 10),
              decoration: BoxDecoration(
                color: WorthlyColors.paper.withValues(alpha: 0.92),
                border: Border(bottom: BorderSide(color: WorthlyColors.ink.withValues(alpha: 0.07))),
              ),
              child: Row(
                children: [
                  Expanded(
                    child: Text(
                      title,
                      style: const TextStyle(fontWeight: FontWeight.w600, fontSize: 19, letterSpacing: -0.4, color: WorthlyColors.ink),
                    ),
                  ),
                  HeaderIconButton(
                    icon: privacy ? Icons.visibility_off_outlined : Icons.visibility_outlined,
                    active: privacy,
                    onPressed: () => ref.read(privacyProvider.notifier).toggle(),
                  ),
                  const SizedBox(width: 8),
                  HeaderIconButton(
                    icon: Icons.sync,
                    onPressed: syncing
                        ? () {}
                        : () async {
                            await ref.read(sessionProvider.notifier).syncNow();
                            ref.invalidate(shellDataProvider);
                          },
                  ),
                ],
              ),
            ),
          ),
          Expanded(
            child: IndexedStack(
              index: connectionsOpen && tab == 3 ? 5 : tab,
              children: const [
                HomeScreen(),
                TransactionsScreen(),
                InvestmentsScreen(),
                AccountsScreen(),
                SettingsScreen(),
                ConnectionsScreen(),
              ],
            ),
          ),
          Container(
            padding: const EdgeInsets.fromLTRB(8, 8, 8, 8),
            decoration: BoxDecoration(
              color: WorthlyColors.paper.withValues(alpha: 0.94),
              border: Border(top: BorderSide(color: WorthlyColors.ink.withValues(alpha: 0.08))),
            ),
            child: SafeArea(
              top: false,
              child: Row(
                children: [
                  _TabButton(icon: Icons.home_outlined, label: 'Home', selected: tab == 0, onTap: () => _go(ref, 0)),
                  _TabButton(icon: Icons.swap_horiz, label: 'Transactions', selected: tab == 1, onTap: () => _go(ref, 1)),
                  _TabButton(icon: Icons.show_chart, label: 'Investments', selected: tab == 2, onTap: () => _go(ref, 2)),
                  _TabButton(icon: Icons.credit_card_outlined, label: 'Accounts', selected: tab == 3, onTap: () => _go(ref, 3, keepConnections: connectionsOpen)),
                  _TabButton(icon: Icons.settings_outlined, label: 'Settings', selected: tab == 4, onTap: () => _go(ref, 4)),
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }

  void _go(WidgetRef ref, int index, {bool keepConnections = false}) {
    ref.read(tabIndexProvider.notifier).state = index;
    if (index != 3 || !keepConnections) {
      ref.read(connectionsOpenProvider.notifier).state = false;
    }
  }
}

class _TabButton extends StatelessWidget {
  const _TabButton({required this.icon, required this.label, required this.selected, required this.onTap});

  final IconData icon;
  final String label;
  final bool selected;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final color = selected ? WorthlyColors.pine : WorthlyColors.faint;
    return Expanded(
      child: InkWell(
        onTap: onTap,
        child: Padding(
          padding: const EdgeInsets.only(top: 7, bottom: 3),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Icon(icon, size: 21, color: color),
              const SizedBox(height: 4),
              Text(label, style: TextStyle(fontSize: 9.5, fontWeight: FontWeight.w500, color: color)),
            ],
          ),
        ),
      ),
    );
  }
}
