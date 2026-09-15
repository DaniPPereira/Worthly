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

class InvestmentsScreen extends ConsumerStatefulWidget {
  const InvestmentsScreen({super.key});

  @override
  ConsumerState<InvestmentsScreen> createState() => _InvestmentsScreenState();
}

class _InvestmentsScreenState extends ConsumerState<InvestmentsScreen> {
  static const _holdingsPageSize = 25;
  static const _historyPageSize = 20;
  static const _historyTypes = ['INVESTMENT_FUNDING', 'INVESTMENT_WITHDRAWAL', 'INCOME'];

  InvestmentSummary? _summary;
  List<Position> _positions = [];
  List<Tx> _history = [];
  int _historyPage = 0;
  bool _historyHasMore = false;
  bool _historyLoadingMore = false;
  int _holdingsShown = _holdingsPageSize;
  String? _currency;
  bool _failed = false;

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
      final summary = await client.get('/investments/summary', parseInvestments);
      final positions = await client.get('/investments/positions', parsePositions);
      final history = await _fetchHistory(0);
      if (!mounted) {
        return;
      }
      final first = summary.totalsByCurrency.isEmpty ? owner.reportingCurrency : summary.totalsByCurrency.first.currency;
      setState(() {
        _summary = summary;
        _positions = positions;
        _history = history.items;
        _historyPage = 0;
        _historyHasMore = history.hasMore;
        _currency = summary.totalsByCurrency.any((row) => row.currency == _currency) ? _currency : first;
        _holdingsShown = _holdingsPageSize;
        _failed = false;
      });
    } catch (_) {
      if (mounted) {
        setState(() {
          _summary = const InvestmentSummary(totalsByCurrency: []);
          _positions = [];
          _history = [];
          _historyHasMore = false;
          _failed = true;
        });
      }
    }
  }

  Future<({List<Tx> items, bool hasMore})> _fetchHistory(int page) async {
    final owner = ref.read(sessionProvider).owner!;
    final month = Period.monthKey(owner.reportingTimezone);
    final from = Period.monthRange(Period.shiftMonthKey(month, -5)).from;
    final to = Period.monthRange(month).to;
    final client = ref.read(worthlyClientProvider);
    final pages = await Future.wait(
      _historyTypes.map(
        (type) => client.get(
          '/transactions?economicType=$type&from=$from&to=$to&page=$page&size=$_historyPageSize',
          parseTxPage,
        ),
      ),
    );
    final items = pages.expand((row) => row.items).toList()
      ..sort((left, right) => right.reportingAt.compareTo(left.reportingAt));
    final hasMore = pages.any((row) => (page + 1) * _historyPageSize < row.total);
    return (items: items, hasMore: hasMore);
  }

  Future<void> _loadMoreHistory() async {
    if (_historyLoadingMore || !_historyHasMore) {
      return;
    }
    setState(() => _historyLoadingMore = true);
    try {
      final nextPage = _historyPage + 1;
      final next = await _fetchHistory(nextPage);
      if (!mounted) {
        return;
      }
      final seen = {for (final tx in _history) tx.id};
      setState(() {
        _history = [..._history, ...next.items.where((tx) => !seen.contains(tx.id))];
        _historyPage = nextPage;
        _historyHasMore = next.hasMore;
        _historyLoadingMore = false;
      });
    } catch (_) {
      if (mounted) {
        setState(() => _historyLoadingMore = false);
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final owner = ref.watch(sessionProvider).owner;
    final privacy = ref.watch(privacyProvider);
    final categories = ref.watch(shellDataProvider).asData?.value.categories ?? [];
    if (owner == null || _summary == null || _currency == null) {
      return const LoadingBody();
    }
    final currency = _currency!;
    final row = _summary!.totalsByCurrency.where((item) => item.currency == currency).firstOrNull;
    if (row == null) {
      return const SizedBox.shrink();
    }
    final visible = _positions.where((item) => item.marketValue?.currency == currency).toList();
    var portfolio = BigInt.zero;
    for (final position in visible) {
      portfolio += MoneyFmt.cents(position.marketValue!.amount);
    }
    final dividendIds = categories.where((item) => (item.code ?? '').contains('investment') || item.code == 'income.investment.dividend').map((item) => item.id).toSet();
    final events = _history.where((tx) {
      if (tx.money.currency != currency) {
        return false;
      }
      return tx.economicType == 'INVESTMENT_FUNDING' ||
          tx.economicType == 'INVESTMENT_WITHDRAWAL' ||
          (tx.categoryId != null && dividendIds.contains(tx.categoryId));
    }).toList();
    final shownHoldings = visible.take(_holdingsShown).toList();
    return ListView(
      padding: const EdgeInsets.fromLTRB(18, 14, 18, 26),
      children: [
        CurrencyPills(
          currencies: _summary!.totalsByCurrency.map((item) => item.currency).toList(),
          selected: currency,
          onSelect: (value) => setState(() {
            _currency = value;
            _holdingsShown = _holdingsPageSize;
          }),
        ),
        Row(
          children: [
            Container(width: 6, height: 6, decoration: const BoxDecoration(color: WorthlyColors.gain, shape: BoxShape.circle)),
            const SizedBox(width: 7),
            Expanded(
              child: Text(
                'Trading 212 · read-only · updated ${Period.instant(_summary!.observedAt, owner.reportingTimezone)}',
                style: const TextStyle(fontSize: 11.5, color: WorthlyColors.faint),
              ),
            ),
          ],
        ),
        const SizedBox(height: 12),
        Container(
          padding: const EdgeInsets.fromLTRB(20, 22, 20, 20),
          decoration: BoxDecoration(color: WorthlyColors.ink, borderRadius: BorderRadius.circular(20)),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text('PORTFOLIO VALUE', style: labelStyle(color: WorthlyColors.cream.withValues(alpha: 0.55))),
              const SizedBox(height: 8),
              Text(MoneyFmt.amount(row.portfolioValue, currency, privacy: privacy), style: serif(size: 42)),
              const SizedBox(height: 10),
              const Text(
                'Snapshot in this currency. Cost basis and return are not in the v1 API.',
                style: TextStyle(fontSize: 12, color: Color(0x8CF4F1EA)),
              ),
              const SizedBox(height: 16),
              Divider(color: WorthlyColors.cream.withValues(alpha: 0.14), height: 1),
              const SizedBox(height: 14),
              Row(
                children: [
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text('FREE CASH', style: labelStyle(color: WorthlyColors.cream.withValues(alpha: 0.55))),
                        const SizedBox(height: 4),
                        Text(MoneyFmt.amount(row.cash, currency, privacy: privacy), style: mono(size: 14, color: WorthlyColors.cream)),
                      ],
                    ),
                  ),
                  Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text('POSITIONS', style: labelStyle(color: WorthlyColors.cream.withValues(alpha: 0.55))),
                      const SizedBox(height: 4),
                      Text('${visible.length}', style: mono(size: 14, color: WorthlyColors.cream)),
                    ],
                  ),
                ],
              ),
              const SizedBox(height: 16),
              const Text(
                'Read through your read-only Trading 212 key, which never leaves your server. Worthly cannot place orders. Trading 212 Crypto is a separate account and is not in the Public API, so those balances cannot appear here.',
                style: TextStyle(fontSize: 11, height: 1.5, color: Color(0xA8F4F1EA)),
              ),
            ],
          ),
        ),
        const SizedBox(height: 12),
        WorthlyCard(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text('HOLDINGS IN $currency', style: labelStyle()),
              const SizedBox(height: 8),
              const Text('Weights are computed within this currency only.', style: TextStyle(fontSize: 12, color: WorthlyColors.muted)),
              if (visible.isEmpty)
                const Padding(
                  padding: EdgeInsets.only(top: 12),
                  child: Text('No positions in this currency.', style: TextStyle(color: WorthlyColors.faint)),
                )
              else
                for (final position in shownHoldings)
                  Padding(
                    padding: const EdgeInsets.only(top: 13),
                    child: Row(
                      children: [
                        Expanded(
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              Text(position.ticker ?? position.instrumentKey, style: const TextStyle(fontWeight: FontWeight.w600, fontSize: 13.5)),
                              Text(
                                '${position.quantity ?? '—'} · ${MoneyFmt.weight(position.marketValue!.amount, MoneyFmt.fromCents(portfolio))}',
                                style: const TextStyle(fontSize: 11.5, color: WorthlyColors.faint),
                              ),
                            ],
                          ),
                        ),
                        Text(MoneyFmt.amount(position.marketValue!.amount, currency, privacy: privacy), style: mono(size: 13.5)),
                      ],
                    ),
                  ),
              LoadMoreButton(
                hasMore: shownHoldings.length < visible.length,
                loading: false,
                onPressed: () => setState(() => _holdingsShown += _holdingsPageSize),
              ),
            ],
          ),
        ),
        const SizedBox(height: 12),
        WorthlyCard(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text('HISTORY', style: labelStyle()),
              if (events.isEmpty && !_historyHasMore)
                const Padding(
                  padding: EdgeInsets.only(top: 12),
                  child: Text('No funding, withdrawal or dividend movements in the last six months.', style: TextStyle(color: WorthlyColors.muted, fontSize: 13)),
                )
              else
                for (final tx in events)
                  Padding(
                    padding: const EdgeInsets.only(top: 13),
                    child: Row(
                      children: [
                        Expanded(
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              Text(_eventLabel(tx), style: const TextStyle(fontWeight: FontWeight.w600, fontSize: 13)),
                              Text(Period.day(tx.reportingAt, owner.reportingTimezone), style: const TextStyle(fontSize: 11.5, color: WorthlyColors.faint)),
                            ],
                          ),
                        ),
                        Text(
                          MoneyFmt.signed(tx.money.amount, tx.money.currency, credit: tx.credit, privacy: privacy),
                          style: mono(size: 13, color: tx.credit ? WorthlyColors.gain : WorthlyColors.ink),
                        ),
                      ],
                    ),
                  ),
              LoadMoreButton(
                hasMore: _historyHasMore,
                loading: _historyLoadingMore,
                onPressed: _loadMoreHistory,
              ),
            ],
          ),
        ),
        if (_failed) ...[
          const SizedBox(height: 12),
          const Text('Some investment data could not be loaded.', style: TextStyle(fontSize: 11.5, color: WorthlyColors.warn)),
        ],
        const SizedBox(height: 12),
        const Text(
          'Worthly cannot place orders. Positions are read through your read-only Trading 212 API key, which lives only on your server.',
          style: TextStyle(fontSize: 11, height: 1.5, color: WorthlyColors.faint),
        ),
      ],
    );
  }

  String _eventLabel(Tx tx) {
    switch (tx.economicType) {
      case 'INVESTMENT_FUNDING':
        return 'Funding';
      case 'INVESTMENT_WITHDRAWAL':
        return 'Withdrawal';
      default:
        return tx.title;
    }
  }
}
