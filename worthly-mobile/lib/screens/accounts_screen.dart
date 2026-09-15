import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:worthly_mobile/api/models.dart';
import 'package:worthly_mobile/api/worthly_client.dart';
import 'package:worthly_mobile/features/session/session.dart';
import 'package:worthly_mobile/features/session/shell_data.dart';
import 'package:worthly_mobile/theme/colors.dart';
import 'package:worthly_mobile/theme/money.dart';
import 'package:worthly_mobile/theme/period.dart';
import 'package:worthly_mobile/theme/theme.dart';
import 'package:worthly_mobile/widgets/ui.dart';

class AccountsScreen extends ConsumerStatefulWidget {
  const AccountsScreen({super.key});

  @override
  ConsumerState<AccountsScreen> createState() => _AccountsScreenState();
}

class _AccountsScreenState extends ConsumerState<AccountsScreen> {
  List<_AccountView>? _accounts;
  WealthSummary? _wealth;
  String? _currency;

  @override
  void initState() {
    super.initState();
    Future.microtask(_load);
  }

  Future<void> _load() async {
    final owner = ref.read(sessionProvider).owner;
    if (owner == null) {
      return;
    }
    try {
      final client = ref.read(worthlyClientProvider);
      final list = await client.get('/accounts', (json) => listOf(json, Account.fromJson));
      final wealth = await client.get('/analytics/summary', parseWealth);
      final withBalances = <_AccountView>[];
      for (final account in list) {
        try {
          final balances = await client.get('/accounts/${account.id}/balances', parseBalances);
          final selected = balances.where((item) => item.usedForLiquidCash).firstOrNull ?? balances.firstOrNull;
          withBalances.add(_AccountView(account: account, balance: selected));
        } catch (_) {
          withBalances.add(_AccountView(account: account, balance: null));
        }
      }
      if (!mounted) {
        return;
      }
      final first = wealth.totalsByCurrency.isEmpty ? owner.reportingCurrency : wealth.totalsByCurrency.first.currency;
      setState(() {
        _accounts = withBalances;
        _wealth = wealth;
        _currency = wealth.totalsByCurrency.any((row) => row.currency == _currency) ? _currency : first;
      });
    } catch (_) {
      if (mounted) {
        setState(() => _accounts = []);
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final owner = ref.watch(sessionProvider).owner;
    final privacy = ref.watch(privacyProvider);
    final connections = ref.watch(shellDataProvider).asData?.value.connections ?? [];
    if (owner == null || _accounts == null || _currency == null) {
      return const LoadingBody();
    }
    if (_accounts!.isEmpty) {
      return const SizedBox.shrink();
    }
    final currency = _currency!;
    final visible = _accounts!.where((item) => item.account.currency == currency).toList();
    final wealthRow = _wealth?.totalsByCurrency.where((item) => item.currency == currency).firstOrNull;
    final grouped = <String, List<_AccountView>>{};
    for (final item in visible) {
      grouped.putIfAbsent(item.account.provider, () => []).add(item);
    }
    return ListView(
      padding: const EdgeInsets.fromLTRB(18, 14, 18, 26),
      children: [
        CurrencyPills(
          currencies: _wealth?.totalsByCurrency.map((item) => item.currency).toList() ?? [currency],
          selected: currency,
          onSelect: (value) => setState(() => _currency = value),
        ),
        WorthlyCard(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text('TOTAL LIQUID CASH', style: labelStyle()),
              const SizedBox(height: 6),
              Text(wealthRow == null ? '—' : MoneyFmt.amount(wealthRow.liquidCash, currency, privacy: privacy), style: serif(size: 34, color: WorthlyColors.ink)),
            ],
          ),
        ),
        const SizedBox(height: 12),
        for (final entry in grouped.entries) ...[
          WorthlyCard(
            child: Column(
              children: [
                Row(
                  children: [
                    Container(
                      width: 30,
                      height: 30,
                      decoration: BoxDecoration(color: WorthlyColors.ink.withValues(alpha: 0.08), borderRadius: BorderRadius.circular(8)),
                      alignment: Alignment.center,
                      child: Text(entry.key.substring(0, 1), style: const TextStyle(fontWeight: FontWeight.w600, fontSize: 13)),
                    ),
                    const SizedBox(width: 10),
                    Expanded(
                      child: Text(_providerName(entry.key, connections), style: const TextStyle(fontWeight: FontWeight.w600, fontSize: 13.5)),
                    ),
                    StatusChip(status: _providerStatus(entry.key, connections)),
                  ],
                ),
                for (final item in entry.value)
                  Padding(
                    padding: const EdgeInsets.only(top: 12),
                    child: Row(
                      children: [
                        Expanded(
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              Text(item.account.displayName, style: const TextStyle(fontWeight: FontWeight.w500, fontSize: 13.5)),
                              Text(item.account.maskedIdentifier ?? '—', style: mono(size: 11, color: WorthlyColors.faint)),
                            ],
                          ),
                        ),
                        Column(
                          crossAxisAlignment: CrossAxisAlignment.end,
                          children: [
                            Text(
                              item.balance == null
                                  ? '—'
                                  : MoneyFmt.amount(item.balance!.money.amount, item.balance!.money.currency, privacy: privacy),
                              style: mono(size: 14),
                            ),
                            Text(
                              item.balance == null ? 'No snapshot' : 'Updated ${Period.instant(item.balance!.observedAt, owner.reportingTimezone)}',
                              style: const TextStyle(fontSize: 10.5, color: WorthlyColors.faint),
                            ),
                          ],
                        ),
                      ],
                    ),
                  ),
              ],
            ),
          ),
          const SizedBox(height: 12),
        ],
        InkWell(
          onTap: () {
            ref.read(categoriesOpenProvider.notifier).state = false;
            ref.read(connectionsOpenProvider.notifier).state = true;
          },
          borderRadius: BorderRadius.circular(14),
          child: Container(
            width: double.infinity,
            padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 15),
            decoration: BoxDecoration(
              color: Colors.white,
              borderRadius: BorderRadius.circular(14),
              border: Border.all(color: WorthlyColors.ink.withValues(alpha: 0.1)),
            ),
            child: const Row(
              children: [
                Expanded(child: Text('Manage connections', style: TextStyle(fontWeight: FontWeight.w600, fontSize: 13.5))),
                Icon(Icons.chevron_right, color: WorthlyColors.faint),
              ],
            ),
          ),
        ),
      ],
    );
  }

  String _providerName(String provider, List<Connection> connections) {
    if (provider == 'TRADING_212') {
      return 'Trading 212';
    }
    return connections.where((item) => item.provider == provider).map((item) => item.label).firstOrNull ?? provider;
  }

  String _providerStatus(String provider, List<Connection> connections) {
    return connections.where((item) => item.provider == provider).map((item) => item.status).firstOrNull ?? 'ACTIVE';
  }
}

class _AccountView {
  const _AccountView({required this.account, required this.balance});

  final Account account;
  final Balance? balance;
}
