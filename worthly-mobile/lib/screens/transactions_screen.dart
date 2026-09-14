import 'dart:async';

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

const _filters = ['All', 'Expenses', 'Income', 'Transfers', 'Uncategorized'];

class TransactionsScreen extends ConsumerStatefulWidget {
  const TransactionsScreen({super.key});

  @override
  ConsumerState<TransactionsScreen> createState() => _TransactionsScreenState();
}

class _TransactionsScreenState extends ConsumerState<TransactionsScreen> {
  String _filter = 'All';
  String _debounced = '';
  TxPage? _page;
  Tx? _open;
  Timer? _timer;

  @override
  void initState() {
    super.initState();
    Future.microtask(_load);
  }

  @override
  void dispose() {
    _timer?.cancel();
    super.dispose();
  }

  void _onSearch(String value) {
    _timer?.cancel();
    _timer = Timer(const Duration(milliseconds: 250), () {
      setState(() => _debounced = value.trim());
      _load();
    });
  }

  Future<void> _load() async {
    final owner = ref.read(sessionProvider).owner;
    if (owner == null) {
      return;
    }
    final month = Period.monthKey(owner.reportingTimezone);
    final range = Period.monthRange(month);
    final uncategorized = ref.read(shellDataProvider).asData?.value.categories.where((item) => item.code == 'uncategorized').firstOrNull?.id;
    final extra = switch (_filter) {
      'Expenses' => '&economicType=EXPENSE',
      'Income' => '&economicType=INCOME',
      'Transfers' => '&economicType=INTERNAL_TRANSFER',
      'Uncategorized' => uncategorized == null ? '' : '&categoryId=$uncategorized',
      _ => '',
    };
    final q = _debounced.isEmpty ? '' : '&q=${Uri.encodeQueryComponent(_debounced)}';
    try {
      final page = await ref.read(worthlyClientProvider).get(
        '/transactions?from=${range.from}&to=${range.to}&size=50$extra$q',
        parseTxPage,
      );
      if (mounted) {
        setState(() => _page = page);
      }
    } catch (_) {
      if (mounted) {
        setState(() => _page = const TxPage(items: [], total: 0));
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
    final groups = <String, List<Tx>>{};
    for (final tx in _page?.items ?? []) {
      final key = Period.day(tx.reportingAt, owner.reportingTimezone);
      groups.putIfAbsent(key, () => []).add(tx);
    }
    return Stack(
      children: [
        ListView(
          padding: const EdgeInsets.fromLTRB(18, 14, 18, 26),
          children: [
            Container(
              padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 4),
              decoration: BoxDecoration(
                color: Colors.white,
                borderRadius: BorderRadius.circular(13),
                border: Border.all(color: WorthlyColors.ink.withValues(alpha: 0.1)),
              ),
              child: TextField(
                onChanged: _onSearch,
                decoration: const InputDecoration(
                  icon: Icon(Icons.search, size: 16, color: WorthlyColors.faint),
                  hintText: 'Search merchant, note or amount',
                  border: InputBorder.none,
                ),
                style: const TextStyle(fontSize: 13.5),
              ),
            ),
            const SizedBox(height: 12),
            FilterChipBar(
              labels: _filters,
              selected: _filter,
              onSelect: (value) {
                setState(() => _filter = value);
                _load();
              },
            ),
            const SizedBox(height: 12),
            Text(
              _page == null ? '' : '${_page!.items.length} of ${_page!.total} transactions',
              style: const TextStyle(fontSize: 11.5, color: WorthlyColors.faint),
            ),
            const SizedBox(height: 8),
            if (_page == null)
              const LoadingBody()
            else if (_page!.items.isEmpty)
              const EmptyState(title: 'No transactions in this view', body: 'Try another filter or wait for the next successful sync.')
            else
              WorthlyCard(
                child: Column(
                  children: [
                    for (final entry in groups.entries) ...[
                      Align(
                        alignment: Alignment.centerLeft,
                        child: Padding(
                          padding: const EdgeInsets.only(top: 8, bottom: 2),
                          child: Text(entry.key.toUpperCase(), style: labelStyle()),
                        ),
                      ),
                      for (final tx in entry.value)
                        _TxRow(
                          tx: tx,
                          privacy: privacy,
                          category: shell?.categories.where((item) => item.id == tx.categoryId).firstOrNull?.label ?? 'Uncategorized',
                          onTap: () => setState(() => _open = tx),
                        ),
                    ],
                  ],
                ),
              ),
            const SizedBox(height: 14),
            const Text(
              'Card purchases can take days to settle. Pending rows may change amount or disappear.',
              style: TextStyle(fontSize: 11, height: 1.5, color: WorthlyColors.faint),
            ),
          ],
        ),
        if (_open != null)
          TransactionSheet(
            tx: _open!,
            accounts: shell?.accounts ?? [],
            categories: shell?.categories ?? [],
            owner: owner,
            privacy: privacy,
            onClose: () => setState(() => _open = null),
            onChanged: (next) {
              setState(() {
                _open = next;
                if (_page != null) {
                  _page = TxPage(
                    items: _page!.items.map((item) => item.id == next.id ? next : item).toList(),
                    total: _page!.total,
                  );
                }
              });
            },
          ),
      ],
    );
  }
}

class _TxRow extends StatelessWidget {
  const _TxRow({required this.tx, required this.privacy, required this.category, required this.onTap});

  final Tx tx;
  final bool privacy;
  final String category;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return InkWell(
      onTap: onTap,
      child: Padding(
        padding: const EdgeInsets.symmetric(vertical: 12),
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
                style: TextStyle(fontWeight: FontWeight.w600, color: tx.transfer ? WorthlyColors.pine : WorthlyColors.ink),
              ),
            ),
            const SizedBox(width: 12),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(tx.title, maxLines: 1, overflow: TextOverflow.ellipsis, style: const TextStyle(fontWeight: FontWeight.w600, fontSize: 13.5)),
                  Wrap(
                    spacing: 6,
                    crossAxisAlignment: WrapCrossAlignment.center,
                    children: [
                      Text(category, style: const TextStyle(fontSize: 11.5, color: WorthlyColors.faint)),
                      if (tx.pending)
                        const _MiniBadge(label: 'Pending', color: WorthlyColors.warn),
                      if (tx.transfer)
                        const _MiniBadge(label: 'Transfer', color: WorthlyColors.pine),
                    ],
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
      ),
    );
  }
}

class _MiniBadge extends StatelessWidget {
  const _MiniBadge({required this.label, required this.color});

  final String label;
  final Color color;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 4, vertical: 1),
      decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(4),
        border: Border.all(color: color.withValues(alpha: 0.35)),
      ),
      child: Text(label.toUpperCase(), style: TextStyle(fontSize: 9.5, letterSpacing: 0.6, color: color, fontWeight: FontWeight.w500)),
    );
  }
}

class TransactionSheet extends ConsumerStatefulWidget {
  const TransactionSheet({
    super.key,
    required this.tx,
    required this.accounts,
    required this.categories,
    required this.owner,
    required this.privacy,
    required this.onClose,
    required this.onChanged,
  });

  final Tx tx;
  final List<Account> accounts;
  final List<Category> categories;
  final Owner owner;
  final bool privacy;
  final VoidCallback onClose;
  final ValueChanged<Tx> onChanged;

  @override
  ConsumerState<TransactionSheet> createState() => _TransactionSheetState();
}

class _TransactionSheetState extends ConsumerState<TransactionSheet> {
  late final TextEditingController _notes;
  List<TransferMatch> _suggestions = [];
  bool _saving = false;

  @override
  void initState() {
    super.initState();
    _notes = TextEditingController(text: widget.tx.notes ?? '');
    Future.microtask(_loadSuggestions);
  }

  @override
  void didUpdateWidget(covariant TransactionSheet oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.tx.id != widget.tx.id || oldWidget.tx.notes != widget.tx.notes) {
      _notes.text = widget.tx.notes ?? '';
    }
  }

  @override
  void dispose() {
    _notes.dispose();
    super.dispose();
  }

  Future<void> _loadSuggestions() async {
    try {
      final all = await ref.read(worthlyClientProvider).get('/transfer-matches?status=SUGGESTED', parseTransferMatches);
      if (mounted) {
        setState(() {
          _suggestions = all.where((item) => item.leftTransactionId == widget.tx.id || item.rightTransactionId == widget.tx.id).toList();
        });
      }
    } catch (_) {
      if (mounted) {
        setState(() => _suggestions = []);
      }
    }
  }

  Future<void> _patch({String? categoryId, String? notes}) async {
    setState(() => _saving = true);
    try {
      final next = await ref.read(worthlyClientProvider).send(
        'PATCH',
        '/transactions/${widget.tx.id}',
        body: {if (categoryId != null) 'categoryId': categoryId, if (notes != null) 'notes': notes},
        parse: parseTx,
      );
      if (next != null) {
        widget.onChanged(next);
      }
    } finally {
      if (mounted) {
        setState(() => _saving = false);
      }
    }
  }

  List<Category> get _chips {
    const preferred = ['Groceries', 'Housing', 'Transport', 'Dining', 'Investing', 'Income', 'Uncategorized'];
    final ordered = <Category>[];
    for (final label in preferred) {
      final found = widget.categories.where((item) => item.label == label || item.label.toLowerCase().contains(label.toLowerCase())).firstOrNull;
      if (found != null && ordered.every((item) => item.id != found.id)) {
        ordered.add(found);
      }
    }
    for (final item in widget.categories) {
      if (ordered.length >= 8) {
        break;
      }
      if (ordered.every((existing) => existing.id != item.id)) {
        ordered.add(item);
      }
    }
    return ordered;
  }

  @override
  Widget build(BuildContext context) {
    final tx = widget.tx;
    final account = widget.accounts.where((item) => item.id == tx.accountId).firstOrNull;
    final category = widget.categories.where((item) => item.id == tx.categoryId).firstOrNull;
    return Material(
      color: WorthlyColors.ink.withValues(alpha: 0.42),
      child: Align(
        alignment: Alignment.bottomCenter,
        child: Container(
          constraints: BoxConstraints(maxHeight: MediaQuery.sizeOf(context).height * 0.86),
          decoration: const BoxDecoration(
            color: WorthlyColors.paper,
            borderRadius: BorderRadius.vertical(top: Radius.circular(24)),
          ),
          child: ListView(
            padding: const EdgeInsets.fromLTRB(20, 10, 20, 42),
            shrinkWrap: true,
            children: [
              Center(
                child: Container(width: 38, height: 4, decoration: BoxDecoration(color: WorthlyColors.ink.withValues(alpha: 0.18), borderRadius: BorderRadius.circular(2))),
              ),
              Align(
                alignment: Alignment.centerRight,
                child: IconButton(onPressed: widget.onClose, icon: const Icon(Icons.close, size: 18)),
              ),
              Text('${category?.label ?? 'Uncategorized'} · ${account?.displayName ?? 'Account'}', style: const TextStyle(fontSize: 12, color: WorthlyColors.faint)),
              Text(
                MoneyFmt.signed(tx.money.amount, tx.money.currency, credit: tx.credit, privacy: widget.privacy),
                style: serif(size: 38, color: tx.credit ? WorthlyColors.gain : WorthlyColors.ink),
              ),
              Text(tx.title, style: const TextStyle(fontWeight: FontWeight.w600, fontSize: 16)),
              const SizedBox(height: 16),
              WorthlyCard(
                padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 4),
                child: Column(
                  children: [
                    _kv('Booked date', Period.instant(tx.reportingAt, widget.owner.reportingTimezone)),
                    _kv('Account', account == null ? tx.accountId : '${account.displayName}${account.maskedIdentifier == null ? '' : ' · ${account.maskedIdentifier}'}'),
                    _kv('Status', tx.lifecycleStatus),
                    _kv('Type', tx.economicType.replaceAll('_', ' ')),
                    _kv('Currency', tx.money.currency),
                  ],
                ),
              ),
              Padding(
                padding: const EdgeInsets.only(top: 16, bottom: 8),
                child: Text('CATEGORY — YOUR CHOICE WINS', style: labelStyle()),
              ),
              Wrap(
                spacing: 7,
                runSpacing: 7,
                children: [
                  for (final chip in _chips)
                    InkWell(
                      onTap: _saving ? null : () => _patch(categoryId: chip.id),
                      child: Container(
                        padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
                        decoration: BoxDecoration(
                          color: tx.categoryId == chip.id ? WorthlyColors.pine : Colors.white,
                          borderRadius: BorderRadius.circular(9),
                          border: Border.all(color: tx.categoryId == chip.id ? WorthlyColors.pine : WorthlyColors.ink.withValues(alpha: 0.12)),
                        ),
                        child: Text(
                          chip.label,
                          style: TextStyle(fontSize: 12, fontWeight: FontWeight.w500, color: tx.categoryId == chip.id ? WorthlyColors.cream : WorthlyColors.ink),
                        ),
                      ),
                    ),
                ],
              ),
              const SizedBox(height: 14),
              WorthlyCard(
                padding: const EdgeInsets.all(14),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    const Text('Note', style: TextStyle(fontSize: 11, color: WorthlyColors.faint)),
                    TextField(
                      controller: _notes,
                      minLines: 1,
                      maxLines: 3,
                      enabled: !_saving,
                      onSubmitted: (value) => _patch(notes: value),
                      onEditingComplete: () => _patch(notes: _notes.text),
                      decoration: const InputDecoration(border: InputBorder.none, isDense: true),
                    ),
                  ],
                ),
              ),
              if (tx.transfer) ...[
                const SizedBox(height: 10),
                OutlinedButton(
                  onPressed: _saving
                      ? null
                      : () async {
                          if (tx.transferMatchId == null) {
                            return;
                          }
                          setState(() => _saving = true);
                          try {
                            await ref.read(worthlyClientProvider).send('DELETE', '/transfer-matches/${tx.transferMatchId}');
                            widget.onChanged(tx.copyWith(economicType: 'EXPENSE', clearTransfer: true));
                          } finally {
                            if (mounted) {
                              setState(() => _saving = false);
                            }
                          }
                        },
                  child: const Text('Unlink internal transfer'),
                ),
              ] else
                for (final match in _suggestions) ...[
                  const SizedBox(height: 10),
                  WorthlyCard(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        const Text('Suggested internal transfer', style: TextStyle(fontWeight: FontWeight.w600, fontSize: 13.5)),
                        Text('Confidence ${match.confidence}', style: const TextStyle(fontSize: 11.5, color: WorthlyColors.faint)),
                        const SizedBox(height: 10),
                        Row(
                          children: [
                            Expanded(
                              child: OutlinedButton(
                                onPressed: _saving
                                    ? null
                                    : () async {
                                        setState(() => _saving = true);
                                        try {
                                          await ref.read(worthlyClientProvider).send('DELETE', '/transfer-matches/${match.id}');
                                          setState(() => _suggestions = _suggestions.where((item) => item.id != match.id).toList());
                                        } finally {
                                          if (mounted) {
                                            setState(() => _saving = false);
                                          }
                                        }
                                      },
                                child: const Text('Reject'),
                              ),
                            ),
                            const SizedBox(width: 8),
                            Expanded(
                              child: FilledButton(
                                onPressed: _saving
                                    ? null
                                    : () async {
                                        setState(() => _saving = true);
                                        try {
                                          await ref.read(worthlyClientProvider).send(
                                            'POST',
                                            '/transfer-matches',
                                            body: {
                                              'leftTransactionId': match.leftTransactionId,
                                              'rightTransactionId': match.rightTransactionId,
                                            },
                                          );
                                          widget.onChanged(tx.copyWith(transferMatchId: match.id, economicType: 'INTERNAL_TRANSFER'));
                                        } finally {
                                          if (mounted) {
                                            setState(() => _saving = false);
                                          }
                                        }
                                      },
                                style: FilledButton.styleFrom(backgroundColor: WorthlyColors.pine),
                                child: const Text('Confirm'),
                              ),
                            ),
                          ],
                        ),
                      ],
                    ),
                  ),
                ],
              const SizedBox(height: 12),
              const Text(
                'Your edits are stored alongside the provider\'s record — the original is never altered and survives the next sync.',
                style: TextStyle(fontSize: 11, height: 1.5, color: WorthlyColors.faint),
              ),
            ],
          ),
        ),
      ),
    );
  }

  Widget _kv(String k, String v) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 11),
      child: Row(
        children: [
          Expanded(child: Text(k, style: const TextStyle(fontSize: 12.5, color: WorthlyColors.muted))),
          Flexible(child: Text(v, textAlign: TextAlign.right, style: mono(size: 12.5))),
        ],
      ),
    );
  }
}
