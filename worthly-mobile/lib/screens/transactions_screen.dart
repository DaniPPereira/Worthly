import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:worthly_mobile/api/csv_export.dart';
import 'package:worthly_mobile/api/models.dart';
import 'package:worthly_mobile/api/worthly_client.dart';
import 'package:worthly_mobile/features/session/session.dart';
import 'package:worthly_mobile/features/session/shell_data.dart';
import 'package:worthly_mobile/theme/colors.dart';
import 'package:worthly_mobile/theme/categories.dart';
import 'package:worthly_mobile/theme/merchant_rule.dart';
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
  static const _pageSize = 50;

  String _filter = 'All';
  String _debounced = '';
  late String _from;
  late String _to;
  TxPage? _page;
  Tx? _open;
  Timer? _timer;
  String? _exportError;
  bool _exporting = false;
  String? _categoryId;
  String? _categoryLabel;
  bool _loadingMore = false;
  final _scroll = ScrollController();

  @override
  void initState() {
    super.initState();
    final owner = ref.read(sessionProvider).owner;
    final range = Period.monthRange(Period.monthKey(owner?.reportingTimezone ?? 'UTC'));
    _from = range.from;
    _to = range.to;
    _scroll.addListener(_onScroll);
    Future.microtask(_load);
  }

  @override
  void dispose() {
    _timer?.cancel();
    _scroll.dispose();
    super.dispose();
  }

  void _onScroll() {
    if (!_scroll.hasClients) {
      return;
    }
    if (_scroll.position.pixels >= _scroll.position.maxScrollExtent - 480) {
      _load(more: true);
    }
  }

  void _onSearch(String value) {
    _timer?.cancel();
    _timer = Timer(const Duration(milliseconds: 250), () {
      setState(() => _debounced = value.trim());
      _load();
    });
  }

  Future<void> _load({bool more = false}) async {
    if (ref.read(sessionProvider).owner == null) {
      return;
    }
    if (more) {
      if (_loadingMore || !(_page?.hasMore ?? false)) {
        return;
      }
      setState(() => _loadingMore = true);
    }
    final nextPage = more ? (_page?.page ?? 0) + 1 : 0;
    final uncategorized = ref.read(shellDataProvider).asData?.value.categories.where((item) => item.code == 'uncategorized').firstOrNull?.id;
    final extra = _categoryId != null
        ? '&categoryId=$_categoryId&economicType=EXPENSE'
        : switch (_filter) {
      'Expenses' => '&economicType=EXPENSE',
      'Income' => '&economicType=INCOME',
      'Transfers' => '&economicType=INTERNAL_TRANSFER',
      'Uncategorized' => uncategorized == null ? '' : '&categoryId=$uncategorized',
      _ => '',
    };
    final q = _debounced.isEmpty ? '' : '&q=${Uri.encodeQueryComponent(_debounced)}';
    try {
      final page = await ref.read(worthlyClientProvider).get(
        '/transactions?from=$_from&to=$_to&page=$nextPage&size=$_pageSize$extra$q',
        parseTxPage,
      );
      if (mounted) {
        setState(() {
          _page = more && _page != null ? _page!.append(page) : page;
          _loadingMore = false;
        });
      }
    } catch (_) {
      if (mounted) {
        setState(() {
          if (!more) {
            _page = const TxPage(items: [], total: 0);
          }
          _loadingMore = false;
        });
      }
    }
  }

  void _applyMonth(String monthKey) {
    final range = Period.monthRange(monthKey);
    setState(() {
      _from = range.from;
      _to = range.to;
    });
    _load();
  }

  void _onFrom(String value) {
    setState(() {
      _from = value;
      if (value.compareTo(_to) > 0) {
        _to = value;
      }
    });
    _load();
  }

  void _onTo(String value) {
    setState(() {
      _to = value;
      if (value.compareTo(_from) < 0) {
        _from = value;
      }
    });
    _load();
  }

  Future<void> _export() async {
    setState(() {
      _exportError = null;
      _exporting = true;
    });
    try {
      await shareTransactionsCsv(ref.read(worthlyClientProvider), from: _from, to: _to);
    } catch (_) {
      if (mounted) {
        setState(() => _exportError = 'Could not download the CSV. Try again.');
      }
    } finally {
      if (mounted) {
        setState(() => _exporting = false);
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    ref.listen(transactionFocusProvider, (previous, next) {
      if (next == null) {
        return;
      }
      setState(() {
        _categoryId = next.categoryId;
        _categoryLabel = next.label;
        if (next.from != null) {
          _from = next.from!;
        }
        if (next.to != null) {
          _to = next.to!;
        }
        _filter = next.label == 'Uncategorized' ? 'Uncategorized' : 'All';
      });
      _load();
    });
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
          controller: _scroll,
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
            WorthlyCard(
              padding: const EdgeInsets.fromLTRB(6, 6, 6, 4),
              child: Column(
                children: [
                  Row(
                    children: [
                      IconButton(
                        tooltip: 'Previous month',
                        onPressed: () => _applyMonth(Period.shiftMonthKey(_from.substring(0, 7), -1)),
                        icon: const Icon(Icons.chevron_left),
                      ),
                      Expanded(
                        child: _DateField(label: 'From', value: _from, onPicked: _onFrom),
                      ),
                      const Padding(
                        padding: EdgeInsets.symmetric(horizontal: 6),
                        child: Text('to', style: TextStyle(fontSize: 12, color: WorthlyColors.faint)),
                      ),
                      Expanded(
                        child: _DateField(label: 'To', value: _to, onPicked: _onTo),
                      ),
                      IconButton(
                        tooltip: 'Next month',
                        onPressed: () => _applyMonth(Period.shiftMonthKey(_from.substring(0, 7), 1)),
                        icon: const Icon(Icons.chevron_right),
                      ),
                    ],
                  ),
                  Row(
                    children: [
                      TextButton(
                        onPressed: () => _applyMonth(Period.monthKey(owner.reportingTimezone)),
                        child: const Text('This month'),
                      ),
                      TextButton(
                        onPressed: () {
                          ref.read(connectionsOpenProvider.notifier).state = false;
                          ref.read(categoriesOpenProvider.notifier).state = true;
                        },
                        child: const Text('Categories'),
                      ),
                      const Spacer(),
                      TextButton.icon(
                        onPressed: _exporting ? null : _export,
                        icon: const Icon(Icons.ios_share, size: 16),
                        label: const Text('Export CSV'),
                      ),
                    ],
                  ),
                ],
              ),
            ),
            if (_exportError != null) ...[
              const SizedBox(height: 8),
              Text(_exportError!, style: const TextStyle(color: WorthlyColors.loss, fontSize: 13)),
            ],
            const SizedBox(height: 12),
            FilterChipBar(
              labels: [
                ..._filters,
                if (_categoryLabel != null && !_filters.contains(_categoryLabel)) _categoryLabel!,
              ],
              selected: _categoryLabel != null && !_filters.contains(_categoryLabel)
                  ? _categoryLabel!
                  : _filter,
              onSelect: (value) {
                setState(() {
                  _filter = value;
                  if (_filters.contains(value)) {
                    _categoryId = null;
                    _categoryLabel = null;
                  }
                });
                _load();
              },
            ),
            const SizedBox(height: 12),
            Text(
              _page == null
                  ? ''
                  : _page!.total <= _pageSize
                      ? '${_page!.total} transactions'
                      : '${_page!.items.length} of ${_page!.total} transactions',
              style: const TextStyle(fontSize: 11.5, color: WorthlyColors.faint),
            ),
            const SizedBox(height: 8),
            if (_page == null)
              const LoadingBody()
            else if (_page!.items.isEmpty)
              EmptyState(
                title: 'No transactions in this view',
                body: 'Try another filter or date range.',
              )
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
                    LoadMoreButton(
                      hasMore: _page?.hasMore ?? false,
                      loading: _loadingMore,
                      onPressed: () => _load(more: true),
                    ),
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
                    page: _page!.page,
                    size: _page!.size,
                  );
                }
              });
            },
            onRulesApplied: () {
              _load().then((_) {
                if (!mounted || _open == null) {
                  return;
                }
                final refreshed = _page?.items.where((item) => item.id == _open!.id).firstOrNull;
                setState(() => _open = refreshed ?? _open);
              });
            },
          ),
      ],
    );
  }
}

class _DateField extends StatelessWidget {
  const _DateField({required this.label, required this.value, required this.onPicked});

  final String label;
  final String value;
  final ValueChanged<String> onPicked;

  @override
  Widget build(BuildContext context) {
    return InkWell(
      onTap: () async {
        final picked = await showDatePicker(
          context: context,
          initialDate: Period.parseYmd(value),
          firstDate: DateTime(2000),
          lastDate: DateTime(2100),
          helpText: label,
        );
        if (picked != null) {
          onPicked(Period.ymd(picked));
        }
      },
      borderRadius: BorderRadius.circular(8),
      child: Container(
        width: double.infinity,
        padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 8),
        decoration: BoxDecoration(
          border: Border.all(color: WorthlyColors.ink.withValues(alpha: 0.12)),
          borderRadius: BorderRadius.circular(8),
        ),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(label.toUpperCase(), style: const TextStyle(fontSize: 9, letterSpacing: 0.6, color: WorthlyColors.faint, fontWeight: FontWeight.w500)),
            const SizedBox(height: 2),
            Text(value, style: mono(size: 12.5)),
          ],
        ),
      ),
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
                      Text(
                        [category, if (tx.location != null && tx.location!.isNotEmpty) tx.location].join(' · '),
                        style: const TextStyle(fontSize: 11.5, color: WorthlyColors.faint),
                      ),
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
    required this.onRulesApplied,
  });

  final Tx tx;
  final List<Account> accounts;
  final List<Category> categories;
  final Owner owner;
  final bool privacy;
  final VoidCallback onClose;
  final ValueChanged<Tx> onChanged;
  final VoidCallback onRulesApplied;

  @override
  ConsumerState<TransactionSheet> createState() => _TransactionSheetState();
}

class _TransactionSheetState extends ConsumerState<TransactionSheet> {
  late final TextEditingController _notes;
  List<TransferMatch> _suggestions = [];
  List<CategorizationRule> _rules = [];
  bool _saving = false;

  @override
  void initState() {
    super.initState();
    _notes = TextEditingController(text: widget.tx.notes ?? '');
    Future.microtask(() async {
      await _loadSuggestions();
      await _loadRules();
    });
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

  Future<void> _loadRules() async {
    try {
      final rules = await ref.read(worthlyClientProvider).get('/categorization-rules', parseCategorizationRules);
      if (mounted) {
        setState(() => _rules = rules);
      }
    } catch (_) {
      if (mounted) {
        setState(() => _rules = []);
      }
    }
  }

  Future<void> _patch({String? categoryId, String? notes, bool offerRule = true}) async {
    setState(() => _saving = true);
    final previousCategoryId = widget.tx.categoryId;
    try {
      final next = await ref.read(worthlyClientProvider).send(
        'PATCH',
        '/transactions/${widget.tx.id}',
        body: {if (categoryId != null) 'categoryId': categoryId, if (notes != null) 'notes': notes},
        parse: parseTx,
      );
      if (next != null) {
        widget.onChanged(next);
        if (categoryId != null && offerRule) {
          await _offerMerchantRule(next, categoryId, previousCategoryId);
        }
      }
    } finally {
      if (mounted) {
        setState(() => _saving = false);
      }
    }
  }

  Future<void> _offerMerchantRule(Tx next, String categoryId, String? previousCategoryId, {Category? known}) async {
    final category = known ?? widget.categories.where((item) => item.id == categoryId).firstOrNull;
    final hint = merchantRuleSuggestion(
      merchant: next.merchant,
      description: next.description,
      categoryCode: category?.code,
      previousCategoryId: previousCategoryId,
      nextCategoryId: categoryId,
      rules: _rules,
    );
    if (hint == null || category == null || !mounted) {
      return;
    }
    final title = hint.field == 'DESCRIPTION'
        ? 'Always categorize descriptions containing “${hint.display}” as ${category.label}?'
        : hint.operator == 'EQUALS'
            ? 'Always categorize “${hint.display}” as ${category.label}?'
            : 'Always categorize merchants containing “${hint.display}” as ${category.label}?';
    final always = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: Text(title),
        content: Text(
          'Future and other non-manual transactions matching this ${hint.field == 'DESCRIPTION' ? 'description' : 'merchant'} get this category. This row stays a manual choice.',
        ),
        actions: [
          TextButton(onPressed: () => Navigator.pop(context, false), child: const Text('Only this one')),
          FilledButton(
            onPressed: () => Navigator.pop(context, true),
            style: FilledButton.styleFrom(backgroundColor: WorthlyColors.pine),
            child: const Text('Always'),
          ),
        ],
      ),
    );
    if (always != true || !mounted) {
      return;
    }
    await ref.read(worthlyClientProvider).send(
      'POST',
      '/categorization-rules',
      body: {
        'priority': 0,
        'field': hint.field,
        'operator': hint.operator,
        'matchValue': hint.matchValue,
        'targetCategoryId': category.id,
      },
      parse: parseCategorizationRule,
    );
    await _loadRules();
    widget.onRulesApplied();
  }

  Future<void> _createCategory() async {
    final parents = parentCategories(widget.categories);
    if (parents.isEmpty || !mounted) {
      return;
    }
    var parentId = defaultParentId(widget.categories);
    final labelController = TextEditingController();
    final created = await showDialog<Category>(
      context: context,
      builder: (context) => StatefulBuilder(
        builder: (context, setDialogState) => AlertDialog(
          title: const Text('New category'),
          content: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              TextField(
                controller: labelController,
                autofocus: true,
                maxLength: 80,
                decoration: const InputDecoration(labelText: 'Name', hintText: 'Pets'),
              ),
              DropdownButton<String>(
                isExpanded: true,
                value: parentId,
                items: parents.map((item) => DropdownMenuItem(value: item.id, child: Text('Under ${item.label}'))).toList(),
                onChanged: (value) {
                  if (value != null) {
                    setDialogState(() => parentId = value);
                  }
                },
              ),
            ],
          ),
          actions: [
            TextButton(onPressed: () => Navigator.pop(context), child: const Text('Cancel')),
            FilledButton(
              onPressed: () async {
                final label = labelController.text.trim();
                if (label.isEmpty) {
                  return;
                }
                final category = await ref.read(worthlyClientProvider).send(
                  'POST',
                  '/categories',
                  body: {'label': label, 'parentId': parentId},
                  parse: parseCategory,
                );
                if (context.mounted) {
                  Navigator.pop(context, category);
                }
              },
              style: FilledButton.styleFrom(backgroundColor: WorthlyColors.pine),
              child: const Text('Create and use'),
            ),
          ],
        ),
      ),
    );
    labelController.dispose();
    if (created == null || !mounted) {
      return;
    }
    final previous = widget.tx.categoryId;
    ref.invalidate(shellDataProvider);
    await _patch(categoryId: created.id, offerRule: false);
    await _offerMerchantRule(widget.tx, created.id, previous, known: created);
  }

  List<Category> get _chips {
    const preferred = ['Groceries', 'Housing', 'Transport', 'Dining', 'Investing', 'Income', 'Uncategorized'];
    final ordered = <Category>[
      ...customCategories(widget.categories),
    ];
    for (final label in preferred) {
      final found = widget.categories.where((item) => item.label == label || item.label.toLowerCase().contains(label.toLowerCase())).firstOrNull;
      if (found != null && ordered.every((item) => item.id != found.id)) {
        ordered.add(found);
      }
    }
    for (final item in widget.categories) {
      if (ordered.length >= 10) {
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
                    if (tx.location != null && tx.location!.isNotEmpty) _kv('Place', tx.location!),
                    if (tx.description != null && tx.description!.isNotEmpty && tx.description != tx.merchant)
                      _kv('Bank details', tx.description!),
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
              const SizedBox(height: 10),
              DropdownButton<String>(
                isExpanded: true,
                value: widget.categories.any((item) => item.id == tx.categoryId) ? tx.categoryId : null,
                hint: const Text('All categories'),
                items: widget.categories.map((item) => DropdownMenuItem(value: item.id, child: Text(item.label))).toList(),
                onChanged: _saving
                    ? null
                    : (id) {
                        if (id != null) {
                          _patch(categoryId: id);
                        }
                      },
              ),
              Align(
                alignment: Alignment.centerLeft,
                child: TextButton(
                  onPressed: _saving ? null : _createCategory,
                  child: const Text('New category'),
                ),
              ),
              const SizedBox(height: 4),
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
