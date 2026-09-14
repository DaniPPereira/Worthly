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
import 'package:worthly_mobile/widgets/sparkline.dart';
import 'package:worthly_mobile/widgets/ui.dart';

const _catColors = [
  WorthlyColors.pine,
  Color(0xFF2C6B5C),
  Color(0xFF4A8878),
  WorthlyColors.brass,
  Color(0xFFB8BFBC),
];

class HomeScreen extends ConsumerStatefulWidget {
  const HomeScreen({super.key});

  @override
  ConsumerState<HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends ConsumerState<HomeScreen> {
  _HomeModel? _model;
  String? _error;
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
      final month = Period.monthKey(owner.reportingTimezone);
      final range = Period.monthRange(month);
      final keys = Period.previousMonths(owner.reportingTimezone, 6);
      final wealth = await client.get('/analytics/summary', parseWealth);
      InvestmentSummary? investments;
      try {
        investments = await client.get('/investments/summary', parseInvestments);
      } catch (_) {
        investments = null;
      }
      final months = await Future.wait(keys.map((key) => client.get('/analytics/monthly?month=$key', parseMonthly)));
      final recent = await client.get('/transactions?size=5', parseTxPage);
      final expenses = await client.get(
        '/transactions?economicType=EXPENSE&from=${range.from}&to=${range.to}&size=200',
        parseTxPage,
      );
      if (!mounted) {
        return;
      }
      final first = wealth.totalsByCurrency.isEmpty ? owner.reportingCurrency : wealth.totalsByCurrency.first.currency;
      setState(() {
        _model = _HomeModel(
          wealth: wealth,
          investments: investments,
          months: months,
          recent: recent,
          expenses: expenses,
        );
        _currency = wealth.totalsByCurrency.any((row) => row.currency == _currency) ? _currency : first;
        _error = null;
      });
    } catch (_) {
      if (mounted) {
        setState(() => _error = 'Dashboard totals are unavailable.');
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final owner = ref.watch(sessionProvider).owner;
    final privacy = ref.watch(privacyProvider);
    final shell = ref.watch(shellDataProvider).asData?.value;
    if (owner == null) {
      return const LoadingBody();
    }
    if (_error != null) {
      return ListView(padding: const EdgeInsets.fromLTRB(18, 14, 18, 26), children: [EmptyState(title: 'Dashboard unavailable', body: _error!)]);
    }
    if (_model == null || _currency == null) {
      return const LoadingBody();
    }
    final model = _model!;
    final currency = _currency!;
    final row = model.wealth.totalsByCurrency.where((item) => item.currency == currency).firstOrNull;
    if (row == null) {
      return ListView(
        padding: const EdgeInsets.fromLTRB(18, 14, 18, 26),
        children: const [
          EmptyState(
            title: 'No balances yet',
            body: 'Connect a bank or Trading 212 to see cash and net worth. Totals stay grouped by currency.',
          ),
        ],
      );
    }
    final month = Period.monthKey(owner.reportingTimezone);
    final monthly = model.months.where((item) => item.month == month).firstOrNull?.totalsByCurrency.where((item) => item.currency == currency).firstOrNull;
    final liquid = (shell?.accounts ?? []).where((item) => item.includedInLiquidCash && item.currency == currency).toList();
    final investmentRow = model.investments?.totalsByCurrency.where((item) => item.currency == currency).firstOrNull;
    final brokerageCash = investmentRow?.cash ?? '0.00';
    final cashAvailable = MoneyFmt.add(row.liquidCash, brokerageCash);
    final hasBrokerageCash = MoneyFmt.cents(brokerageCash) != BigInt.zero;
    final reauth = shell?.notifications.where((item) => item.readAt == null && item.type == 'CONNECTION_REAUTH_REQUIRED').firstOrNull;
    final reauthConnection = shell?.connections.where((item) => item.status == 'REAUTH_REQUIRED').firstOrNull;
    final hasBank = shell?.connections.any((item) => item.provider == 'ENABLE_BANKING') ?? false;
    final categoryRows = _categoryRows(model.expenses.items, shell?.categories ?? [], currency);
    final maxCat = categoryRows.isEmpty ? BigInt.zero : categoryRows.first.cents;
    return ListView(
      padding: const EdgeInsets.fromLTRB(18, 14, 18, 26),
      children: [
        CurrencyPills(
          currencies: model.wealth.totalsByCurrency.map((item) => item.currency).toList(),
          selected: currency,
          onSelect: (value) => setState(() => _currency = value),
        ),
        Row(
          children: [
            Container(width: 6, height: 6, decoration: const BoxDecoration(color: WorthlyColors.gain, shape: BoxShape.circle)),
            const SizedBox(width: 7),
            Expanded(
              child: Text(
                'Last updated ${Period.instant(model.wealth.asOf, owner.reportingTimezone)} · bank data is not real-time',
                style: const TextStyle(fontSize: 11.5, color: WorthlyColors.faint),
              ),
            ),
          ],
        ),
        const SizedBox(height: 12),
        Container(
          padding: const EdgeInsets.fromLTRB(20, 22, 20, 20),
          decoration: BoxDecoration(color: WorthlyColors.pine, borderRadius: BorderRadius.circular(20)),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text('CASH AVAILABLE NOW', style: labelStyle(color: WorthlyColors.cream.withValues(alpha: 0.6))),
              const SizedBox(height: 8),
              Text(MoneyFmt.amount(cashAvailable, row.currency, privacy: privacy), style: serif(size: 46)),
              const SizedBox(height: 6),
              Text(
                [
                  '${liquid.length} bank account${liquid.length == 1 ? '' : 's'}',
                  if (hasBrokerageCash) 'includes Trading 212 cash',
                  'stocks shown under Invested',
                  row.currency,
                ].join(' · '),
                style: TextStyle(fontSize: 12, color: WorthlyColors.cream.withValues(alpha: 0.62)),
              ),
              const SizedBox(height: 16),
              Divider(color: WorthlyColors.cream.withValues(alpha: 0.16), height: 1),
              const SizedBox(height: 14),
              Text('NET WORTH', style: labelStyle(color: WorthlyColors.cream.withValues(alpha: 0.6))),
              const SizedBox(height: 5),
              Text(MoneyFmt.amount(row.netWorth, row.currency, privacy: privacy), style: serif(size: 26)),
            ],
          ),
        ),
        if (reauth != null) ...[
          const SizedBox(height: 12),
          Material(
            color: WorthlyColors.warnBg,
            shape: RoundedRectangleBorder(
              borderRadius: BorderRadius.circular(16),
              side: BorderSide(color: WorthlyColors.warn.withValues(alpha: 0.28)),
            ),
            child: ListTile(
              onTap: () {
                ref.read(tabIndexProvider.notifier).state = 3;
                ref.read(connectionsOpenProvider.notifier).state = true;
              },
              title: Text(
                reauthConnection == null ? 'A bank needs reauthorization' : '${reauthConnection.label} needs reauthorization',
                style: const TextStyle(fontWeight: FontWeight.w600, fontSize: 13),
              ),
              subtitle: const Text('Open Banking consent expired · balances may be stale', style: TextStyle(color: Color(0xFF6E5A2E), fontSize: 11.5)),
              trailing: const Icon(Icons.chevron_right, color: WorthlyColors.warn),
            ),
          ),
        ],
        const SizedBox(height: 12),
        WorthlyCard(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text('SAVINGS · 6 MONTHS', style: labelStyle()),
              const SizedBox(height: 12),
              Sparkline(
                values: model.months.map((item) => centsAsDouble(_row(item, currency)?.savings ?? '0')).toList(),
                color: WorthlyColors.pine,
                height: 96,
              ),
              const SizedBox(height: 10),
              const Text(
                'Historical net worth is not stored in v1. This chart is monthly savings in a single currency.',
                style: TextStyle(fontSize: 11.5, height: 1.5, color: WorthlyColors.muted),
              ),
            ],
          ),
        ),
        const SizedBox(height: 10),
        GridView.count(
          crossAxisCount: 2,
          shrinkWrap: true,
          physics: const NeverScrollableScrollPhysics(),
          crossAxisSpacing: 10,
          mainAxisSpacing: 10,
          childAspectRatio: 1.12,
          children: [
            _metric('Income', monthly == null ? '—' : MoneyFmt.amount(monthly.income, monthly.currency, privacy: privacy), 'This month', WorthlyColors.gain, model.months.map((m) => centsAsDouble(_row(m, currency)?.income ?? '0')).toList()),
            _metric('Expenses', monthly == null ? '—' : MoneyFmt.amount(monthly.expenses, monthly.currency, privacy: privacy), 'Transfers excluded', WorthlyColors.loss, model.months.map((m) => centsAsDouble(_row(m, currency)?.expenses ?? '0')).toList()),
            _metric('Invested', monthly == null ? '—' : MoneyFmt.amount(monthly.invested, monthly.currency, privacy: privacy), 'Funding, not spending', WorthlyColors.brass, model.months.map((m) => centsAsDouble(_row(m, currency)?.invested ?? '0')).toList()),
            _metric('Savings rate', monthly == null ? '—' : MoneyFmt.rate(monthly.savingsRate, privacy: privacy), monthly?.savingsRateReason ?? 'Income minus expenses', WorthlyColors.pine, model.months.map((m) => centsAsDouble(_row(m, currency)?.savingsRate ?? '0')).toList()),
          ],
        ),
        const SizedBox(height: 12),
        WorthlyCard(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text('WHERE IT WENT · ${Period.monthLabel(month).toUpperCase()}', style: labelStyle()),
              const SizedBox(height: 14),
              if (categoryRows.isEmpty)
                Text(
                  hasBank
                      ? 'No expenses in this currency this month.'
                      : 'Card and current-account purchases appear after you connect a bank. Trading 212 is investments only.',
                  style: const TextStyle(fontSize: 13, color: WorthlyColors.muted),
                )
              else
                for (var i = 0; i < categoryRows.length; i++) ...[
                  Row(
                    children: [
                      Expanded(child: Text(categoryRows[i].name, style: const TextStyle(fontWeight: FontWeight.w500, fontSize: 13))),
                      Text(MoneyFmt.amount(MoneyFmt.fromCents(categoryRows[i].cents), currency, privacy: privacy), style: mono(size: 12.5, color: const Color(0xFF3E4A47))),
                    ],
                  ),
                  const SizedBox(height: 6),
                  ClipRRect(
                    borderRadius: BorderRadius.circular(3),
                    child: LinearProgressIndicator(
                      value: maxCat == BigInt.zero ? 0 : categoryRows[i].cents.toDouble() / maxCat.toDouble(),
                      minHeight: 6,
                      backgroundColor: WorthlyColors.ink.withValues(alpha: 0.07),
                      color: _catColors[i % _catColors.length],
                    ),
                  ),
                  const SizedBox(height: 12),
                ],
            ],
          ),
        ),
        const SizedBox(height: 12),
        WorthlyCard(
          child: Column(
            children: [
              Row(
                children: [
                  Expanded(child: Text('RECENT MOVEMENTS', style: labelStyle())),
                  TextButton(
                    onPressed: () => ref.read(tabIndexProvider.notifier).state = 1,
                    child: const Text('See all', style: TextStyle(fontWeight: FontWeight.w600, fontSize: 11.5)),
                  ),
                ],
              ),
              if (model.recent.items.isEmpty)
                const Padding(
                  padding: EdgeInsets.only(bottom: 8),
                  child: Text('No movements yet.', style: TextStyle(fontSize: 13, color: WorthlyColors.muted)),
                )
              else
                ...model.recent.items.take(4).map((tx) {
                  final category = shell?.categories.where((item) => item.id == tx.categoryId).firstOrNull;
                  return _TxLine(owner: owner, privacy: privacy, tx: tx, category: category?.label ?? 'Uncategorized');
                }),
            ],
          ),
        ),
      ],
    );
  }
}

MonthlyRow? _row(MonthlyAnalytics month, String currency) {
  return month.totalsByCurrency.where((item) => item.currency == currency).firstOrNull;
}

List<({String name, BigInt cents})> _categoryRows(List<Tx> items, List<Category> categories, String currency) {
  final byId = {for (final category in categories) category.id: category};
  final totals = <String, BigInt>{};
  for (final tx in items) {
    if (tx.money.currency != currency) {
      continue;
    }
    final name = byId[tx.categoryId]?.label ?? 'Uncategorized';
    totals[name] = (totals[name] ?? BigInt.zero) + MoneyFmt.cents(tx.money.amount).abs();
  }
  final rows = totals.entries.map((entry) => (name: entry.key, cents: entry.value)).toList()
    ..sort((a, b) => b.cents.compareTo(a.cents));
  return rows.take(5).toList();
}

Widget _metric(String label, String value, String note, Color color, List<double> spark) {
  return WorthlyCard(
    child: Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(label.toUpperCase(), style: labelStyle()),
        const SizedBox(height: 7),
        Text(value, style: serif(size: 23, color: color)),
        const SizedBox(height: 8),
        Sparkline(values: spark, color: color),
        const SizedBox(height: 4),
        Text(note, style: const TextStyle(fontSize: 11, color: WorthlyColors.faint)),
      ],
    ),
  );
}

class _TxLine extends StatelessWidget {
  const _TxLine({required this.owner, required this.privacy, required this.tx, required this.category});

  final Owner owner;
  final bool privacy;
  final Tx tx;
  final String category;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 10),
      child: Row(
        children: [
          Container(
            width: 34,
            height: 34,
            decoration: BoxDecoration(
              color: tx.transfer ? WorthlyColors.pine.withValues(alpha: 0.1) : WorthlyColors.paper,
              borderRadius: BorderRadius.circular(10),
            ),
            alignment: Alignment.center,
            child: Text(
              tx.transfer ? '⇄' : tx.title.substring(0, 1).toUpperCase(),
              style: TextStyle(fontWeight: FontWeight.w600, fontSize: 13, color: tx.transfer ? WorthlyColors.pine : WorthlyColors.ink),
            ),
          ),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(tx.title, maxLines: 1, overflow: TextOverflow.ellipsis, style: const TextStyle(fontWeight: FontWeight.w600, fontSize: 13.5)),
                Text(
                  [category, if (tx.location != null && tx.location!.isNotEmpty) tx.location, Period.day(tx.reportingAt, owner.reportingTimezone)].join(' · '),
                  style: const TextStyle(fontSize: 11.5, color: WorthlyColors.faint),
                ),
              ],
            ),
          ),
          Text(
            MoneyFmt.signed(tx.money.amount, tx.money.currency, credit: tx.credit, privacy: privacy),
            style: mono(size: 13.5, color: tx.credit ? WorthlyColors.gain : WorthlyColors.ink),
          ),
        ],
      ),
    );
  }
}

class _HomeModel {
  const _HomeModel({
    required this.wealth,
    required this.investments,
    required this.months,
    required this.recent,
    required this.expenses,
  });

  final WealthSummary wealth;
  final InvestmentSummary? investments;
  final List<MonthlyAnalytics> months;
  final TxPage recent;
  final TxPage expenses;
}
