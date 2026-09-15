import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:worthly_mobile/api/models.dart';
import 'package:worthly_mobile/api/worthly_client.dart';
import 'package:worthly_mobile/features/session/session.dart';
import 'package:worthly_mobile/features/session/shell_data.dart';
import 'package:worthly_mobile/theme/categories.dart';
import 'package:worthly_mobile/theme/colors.dart';
import 'package:worthly_mobile/theme/merchant_rule.dart';
import 'package:worthly_mobile/theme/theme.dart';
import 'package:worthly_mobile/widgets/ui.dart';

class CategoriesScreen extends ConsumerStatefulWidget {
  const CategoriesScreen({super.key});

  @override
  ConsumerState<CategoriesScreen> createState() => _CategoriesScreenState();
}

class _CategoriesScreenState extends ConsumerState<CategoriesScreen> {
  List<CategorizationRule> _rules = [];
  int _uncategorized = 0;
  bool _saving = false;
  String _newLabel = '';
  String _newParentId = '';
  String _newPhrases = '';
  String _phraseCategoryId = '';
  String _phraseValue = '';
  bool _showNewCategory = false;

  @override
  void initState() {
    super.initState();
    Future.microtask(_load);
  }

  Future<void> _load() async {
    final categories = ref.read(shellDataProvider).asData?.value.categories ?? [];
    final uncategorizedId = categories.where((item) => item.code == 'uncategorized').firstOrNull?.id;
    try {
      final rules = await ref.read(worthlyClientProvider).get('/categorization-rules', parseCategorizationRules);
      if (mounted) {
        setState(() {
          _rules = rules;
          if (_newParentId.isEmpty) {
            _newParentId = defaultParentId(categories);
          }
          if (_phraseCategoryId.isEmpty) {
            _phraseCategoryId = defaultParentId(categories);
          }
        });
      }
    } catch (_) {
      if (mounted) {
        setState(() => _rules = []);
      }
    }
    if (uncategorizedId != null) {
      try {
        final page = await ref.read(worthlyClientProvider).get('/transactions?categoryId=$uncategorizedId&size=1', parseTxPage);
        if (mounted) {
          setState(() => _uncategorized = page.total);
        }
      } catch (_) {
        /* keep 0 */
      }
    }
  }

  Future<void> _createCategory() async {
    final label = _newLabel.trim();
    final parentId = _newParentId.isEmpty
        ? defaultParentId(ref.read(shellDataProvider).asData?.value.categories ?? [])
        : _newParentId;
    if (label.isEmpty || parentId.isEmpty) {
      return;
    }
    setState(() => _saving = true);
    try {
      final created = await ref.read(worthlyClientProvider).send(
        'POST',
        '/categories',
        body: {'label': label, 'parentId': parentId},
        parse: parseCategory,
      );
      final phrases = splitMatchPhrases(_newPhrases);
      if (created != null) {
        for (final phrase in phrases) {
          final rule = await ref.read(worthlyClientProvider).send(
            'POST',
            '/categorization-rules',
            body: {
              'priority': 0,
              'field': 'DESCRIPTION',
              'operator': 'CONTAINS',
              'matchValue': phrase,
              'targetCategoryId': created.id,
            },
            parse: parseCategorizationRule,
          );
          if (rule != null) {
            _rules = [..._rules.where((item) => item.id != rule.id), rule];
          }
        }
      }
      ref.invalidate(shellDataProvider);
      if (mounted) {
        setState(() {
          _newLabel = '';
          _newPhrases = '';
          _showNewCategory = false;
        });
      }
    } finally {
      if (mounted) {
        setState(() => _saving = false);
      }
    }
  }

  Future<void> _addDescription() async {
    final phrases = splitMatchPhrases(_phraseValue);
    final categoryId = _phraseCategoryId.isEmpty
        ? defaultParentId(ref.read(shellDataProvider).asData?.value.categories ?? [])
        : _phraseCategoryId;
    if (categoryId.isEmpty || phrases.isEmpty) {
      return;
    }
    setState(() => _saving = true);
    try {
      for (final phrase in phrases) {
        final rule = await ref.read(worthlyClientProvider).send(
          'POST',
          '/categorization-rules',
          body: {
            'priority': 0,
            'field': 'DESCRIPTION',
            'operator': 'CONTAINS',
            'matchValue': phrase,
            'targetCategoryId': categoryId,
          },
          parse: parseCategorizationRule,
        );
        if (rule != null) {
          _rules = [..._rules.where((item) => item.id != rule.id), rule];
        }
      }
      if (mounted) {
        setState(() => _phraseValue = '');
      }
    } finally {
      if (mounted) {
        setState(() => _saving = false);
      }
    }
  }

  Future<void> _editCategory(Category item) async {
    final categories = ref.read(shellDataProvider).asData?.value.categories ?? [];
    final labelController = TextEditingController(text: item.label);
    var parentId = item.parentId ?? defaultParentId(categories);
    final parents = parentCategories(categories);
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (context) {
        return StatefulBuilder(
          builder: (context, setDialogState) {
            return AlertDialog(
              title: const Text('Edit category'),
              content: Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  TextField(
                    controller: labelController,
                    maxLength: 80,
                    decoration: const InputDecoration(hintText: 'Name', isDense: true),
                  ),
                  DropdownButton<String>(
                    isExpanded: true,
                    value: parents.any((parent) => parent.id == parentId) ? parentId : defaultParentId(categories),
                    items: parents.map((parent) => DropdownMenuItem(value: parent.id, child: Text(parent.label))).toList(),
                    onChanged: (value) {
                      if (value != null) {
                        setDialogState(() => parentId = value);
                      }
                    },
                  ),
                ],
              ),
              actions: [
                TextButton(onPressed: () => Navigator.pop(context, false), child: const Text('Cancel')),
                TextButton(onPressed: () => Navigator.pop(context, true), child: const Text('Save')),
              ],
            );
          },
        );
      },
    );
    final label = labelController.text.trim();
    labelController.dispose();
    if (confirmed != true || label.isEmpty || !mounted) {
      return;
    }
    setState(() => _saving = true);
    try {
      await ref.read(worthlyClientProvider).send(
        'PATCH',
        '/categories/${item.id}',
        body: {'label': label, 'parentId': parentId},
        parse: parseCategory,
      );
      ref.invalidate(shellDataProvider);
    } finally {
      if (mounted) {
        setState(() => _saving = false);
      }
    }
  }

  Future<void> _deleteCategory(Category item) async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: Text('Delete ${item.label}?'),
        content: const Text('Transactions in this category become Uncategorized, and its rules are removed.'),
        actions: [
          TextButton(onPressed: () => Navigator.pop(context, false), child: const Text('Cancel')),
          TextButton(
            onPressed: () => Navigator.pop(context, true),
            child: const Text('Delete', style: TextStyle(color: WorthlyColors.loss)),
          ),
        ],
      ),
    );
    if (confirmed != true || !mounted) {
      return;
    }
    setState(() => _saving = true);
    try {
      await ref.read(worthlyClientProvider).send('DELETE', '/categories/${item.id}');
      ref.invalidate(shellDataProvider);
      if (mounted) {
        setState(() {
          _rules = _rules.where((rule) => rule.targetCategoryId != item.id).toList();
          if (_phraseCategoryId == item.id) {
            _phraseCategoryId = '';
          }
        });
      }
    } finally {
      if (mounted) {
        setState(() => _saving = false);
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final categories = ref.watch(shellDataProvider).asData?.value.categories ?? [];
    return ListView(
      padding: const EdgeInsets.fromLTRB(18, 14, 18, 26),
      children: [
        TextButton.icon(
          onPressed: () => ref.read(categoriesOpenProvider.notifier).state = false,
          icon: const Icon(Icons.chevron_left, size: 18),
          label: const Text('Transactions', style: TextStyle(fontWeight: FontWeight.w600, fontSize: 12.5)),
          style: TextButton.styleFrom(foregroundColor: WorthlyColors.pine, alignment: Alignment.centerLeft),
        ),
        WorthlyCard(
          padding: EdgeInsets.zero,
          child: Column(
            children: [
              if (_uncategorized > 0)
                ListTile(
                  onTap: () {
                    ref.read(categoriesOpenProvider.notifier).state = false;
                    ref.read(tabIndexProvider.notifier).state = 1;
                    ref.read(transactionFocusProvider.notifier).state = const TransactionFocus(label: 'Uncategorized');
                  },
                  title: const Text('Uncategorized', style: TextStyle(fontWeight: FontWeight.w500, fontSize: 13.5, color: WorthlyColors.warn)),
                  trailing: Text('$_uncategorized', style: mono(size: 12.5, color: WorthlyColors.warn)),
                ),
              for (final item in customCategories(categories))
                Padding(
                  padding: const EdgeInsets.fromLTRB(16, 4, 8, 4),
                  child: Row(
                    children: [
                      Expanded(
                        child: Text.rich(
                          TextSpan(
                            text: item.label,
                            style: const TextStyle(fontWeight: FontWeight.w500, fontSize: 13),
                            children: [
                              TextSpan(
                                text: ' · ${categories.where((category) => category.id == item.parentId).firstOrNull?.label ?? 'Other expense'}',
                                style: const TextStyle(fontWeight: FontWeight.w400, color: WorthlyColors.faint),
                              ),
                            ],
                          ),
                          overflow: TextOverflow.ellipsis,
                        ),
                      ),
                      TextButton(
                        style: TextButton.styleFrom(visualDensity: VisualDensity.compact, padding: const EdgeInsets.symmetric(horizontal: 8)),
                        onPressed: _saving ? null : () => _editCategory(item),
                        child: const Text('Edit', style: TextStyle(fontSize: 12)),
                      ),
                      TextButton(
                        style: TextButton.styleFrom(visualDensity: VisualDensity.compact, padding: const EdgeInsets.symmetric(horizontal: 8)),
                        onPressed: _saving ? null : () => _deleteCategory(item),
                        child: const Text('Delete', style: TextStyle(fontSize: 12, color: WorthlyColors.loss)),
                      ),
                    ],
                  ),
                ),
              if (_showNewCategory)
                Padding(
                  padding: const EdgeInsets.fromLTRB(16, 6, 16, 10),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      TextField(
                        onChanged: (value) => setState(() => _newLabel = value),
                        maxLength: 80,
                        decoration: const InputDecoration(hintText: 'Name, e.g. Pets', isDense: true, counterText: ''),
                      ),
                      DropdownButton<String>(
                        isExpanded: true,
                        isDense: true,
                        value: parentCategories(categories).any((item) => item.id == _newParentId) ? _newParentId : defaultParentId(categories),
                        items: parentCategories(categories).map((item) => DropdownMenuItem(value: item.id, child: Text(item.label))).toList(),
                        onChanged: (value) {
                          if (value != null) {
                            setState(() => _newParentId = value);
                          }
                        },
                      ),
                      TextField(
                        onChanged: (value) => setState(() => _newPhrases = value),
                        decoration: const InputDecoration(hintText: 'Optional bank phrases', isDense: true),
                      ),
                      Row(
                        mainAxisAlignment: MainAxisAlignment.end,
                        children: [
                          TextButton(onPressed: () => setState(() => _showNewCategory = false), child: const Text('Cancel')),
                          FilledButton(
                            onPressed: _saving || _newLabel.trim().isEmpty ? null : _createCategory,
                            style: FilledButton.styleFrom(backgroundColor: WorthlyColors.pine, visualDensity: VisualDensity.compact),
                            child: const Text('Create'),
                          ),
                        ],
                      ),
                    ],
                  ),
                )
              else
                Align(
                  alignment: Alignment.centerLeft,
                  child: TextButton(
                    onPressed: () => setState(() => _showNewCategory = true),
                    child: const Text('+ New category'),
                  ),
                ),
            ],
          ),
        ),
        const SizedBox(height: 14),
        WorthlyCard(
          padding: EdgeInsets.zero,
          child: Column(
            children: [
              Padding(
                padding: const EdgeInsets.fromLTRB(16, 12, 16, 8),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text('RULES', style: labelStyle()),
                    const SizedBox(height: 8),
                    Row(
                      children: [
                        Expanded(
                          flex: 4,
                          child: DropdownButton<String>(
                            isExpanded: true,
                            isDense: true,
                            value: categories.any((item) => item.id == _phraseCategoryId && item.code != 'uncategorized')
                                ? _phraseCategoryId
                                : defaultParentId(categories),
                            items: categories
                                .where((item) => item.code != 'uncategorized')
                                .map((item) => DropdownMenuItem(value: item.id, child: Text(item.label, overflow: TextOverflow.ellipsis)))
                                .toList(),
                            onChanged: (value) {
                              if (value != null) {
                                setState(() => _phraseCategoryId = value);
                              }
                            },
                          ),
                        ),
                        const SizedBox(width: 8),
                        Expanded(
                          flex: 5,
                          child: TextField(
                            onChanged: (value) => setState(() => _phraseValue = value),
                            decoration: const InputDecoration(hintText: 'Description contains…', isDense: true),
                          ),
                        ),
                        TextButton(
                          onPressed: _saving || splitMatchPhrases(_phraseValue).isEmpty ? null : _addDescription,
                          child: const Text('Add'),
                        ),
                      ],
                    ),
                  ],
                ),
              ),
              for (final rule in _rules)
                Padding(
                  padding: const EdgeInsets.fromLTRB(16, 2, 8, 2),
                  child: Row(
                    children: [
                      Expanded(
                        child: Text(
                          formatMerchantRule(
                            rule,
                            categories.where((item) => item.id == rule.targetCategoryId).firstOrNull?.label ?? 'category',
                          ),
                          style: const TextStyle(fontSize: 12.5),
                          overflow: TextOverflow.ellipsis,
                        ),
                      ),
                      TextButton(
                        style: TextButton.styleFrom(visualDensity: VisualDensity.compact, padding: const EdgeInsets.symmetric(horizontal: 8)),
                        onPressed: () async {
                          await ref.read(worthlyClientProvider).send('DELETE', '/categorization-rules/${rule.id}');
                          setState(() => _rules = _rules.where((item) => item.id != rule.id).toList());
                        },
                        child: const Text('Delete', style: TextStyle(fontSize: 12, color: WorthlyColors.loss)),
                      ),
                    ],
                  ),
                ),
            ],
          ),
        ),
      ],
    );
  }
}
