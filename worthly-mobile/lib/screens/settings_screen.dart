import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:worthly_mobile/api/csv_export.dart';
import 'package:worthly_mobile/api/models.dart';
import 'package:worthly_mobile/api/worthly_client.dart';
import 'package:worthly_mobile/features/auth/auth_config.dart';
import 'package:worthly_mobile/features/session/session.dart';
import 'package:worthly_mobile/features/session/shell_data.dart';
import 'package:worthly_mobile/theme/categories.dart';
import 'package:worthly_mobile/theme/colors.dart';
import 'package:worthly_mobile/theme/merchant_rule.dart';
import 'package:worthly_mobile/theme/period.dart';
import 'package:worthly_mobile/theme/theme.dart';
import 'package:worthly_mobile/widgets/ui.dart';

const _timezones = ['Europe/Lisbon', 'Europe/London', 'UTC'];
const _currencies = ['EUR', 'GBP', 'USD'];

class SettingsScreen extends ConsumerStatefulWidget {
  const SettingsScreen({super.key});

  @override
  ConsumerState<SettingsScreen> createState() => _SettingsScreenState();
}

class _SettingsScreenState extends ConsumerState<SettingsScreen> {
  late String _timezone;
  late String _currency;
  late final TextEditingController _name;
  List<Device> _devices = [];
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
    final owner = ref.read(sessionProvider).owner;
    _timezone = owner?.reportingTimezone ?? 'UTC';
    _currency = owner?.reportingCurrency ?? 'EUR';
    _name = TextEditingController(text: owner?.name ?? '');
    Future.microtask(_load);
  }

  @override
  void dispose() {
    _name.dispose();
    super.dispose();
  }

  Future<void> _load() async {
    final categories = ref.read(shellDataProvider).asData?.value.categories ?? [];
    final uncategorizedId = categories.where((item) => item.code == 'uncategorized').firstOrNull?.id;
    try {
      final devices = await ref.read(worthlyClientProvider).get('/devices', parseDevices);
      if (mounted) {
        setState(() => _devices = devices);
      }
    } catch (_) {
      if (mounted) {
        setState(() => _devices = []);
      }
    }
    try {
      final rules = await ref.read(worthlyClientProvider).get('/categorization-rules', parseCategorizationRules);
      final cats = ref.read(shellDataProvider).asData?.value.categories ?? [];
      if (mounted) {
        setState(() {
          _rules = rules;
          if (_newParentId.isEmpty) {
            _newParentId = defaultParentId(cats);
          }
          if (_phraseCategoryId.isEmpty) {
            _phraseCategoryId = defaultParentId(cats);
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

  Future<void> _savePrefs() async {
    setState(() => _saving = true);
    try {
      final owner = await ref.read(worthlyClientProvider).send(
        'PATCH',
        '/me',
        body: {
          if (_name.text.trim().isNotEmpty) 'name': _name.text.trim(),
          'reportingTimezone': _timezone,
          'reportingCurrency': _currency,
        },
        parse: parseOwner,
      );
      if (owner != null) {
        await ref.read(sessionProvider.notifier).refreshOwner();
      }
    } finally {
      if (mounted) {
        setState(() => _saving = false);
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

  Future<void> _export() async {
    try {
      await shareTransactionsCsv(ref.read(worthlyClientProvider));
    } catch (_) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('Could not download the CSV. Try again.')));
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final owner = ref.watch(sessionProvider).owner;
    final privacy = ref.watch(privacyProvider);
    final faceId = ref.watch(faceIdProvider);
    final obscure = ref.watch(obscureSwitcherProvider);
    final categories = ref.watch(shellDataProvider).asData?.value.categories ?? [];
    final notifications = ref.watch(shellDataProvider).asData?.value.notifications ?? [];
    final unread = notifications.where((item) => item.readAt == null).toList();
    final host = _serverLabel(AuthConfig.local.issuer);
    return ListView(
      padding: const EdgeInsets.fromLTRB(18, 14, 18, 26),
      children: [
        _group('Reporting', [
          Padding(
            padding: const EdgeInsets.fromLTRB(16, 12, 16, 4),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                const Text('Name', style: TextStyle(fontWeight: FontWeight.w500, fontSize: 13.5)),
                const Text('Shown on your account', style: TextStyle(fontSize: 11.5, color: WorthlyColors.faint)),
                TextField(
                  controller: _name,
                  textCapitalization: TextCapitalization.words,
                  decoration: const InputDecoration(isDense: true, border: InputBorder.none),
                ),
              ],
            ),
          ),
          _selectRow(
            'Timezone',
            'Used for the reporting month and timestamps',
            _timezone,
            {
              if (!_timezones.contains(_timezone)) _timezone,
              ..._timezones,
            }.toList(),
            (value) => setState(() => _timezone = value),
          ),
          _selectRow(
            'Reporting currency',
            'A preference only — it does not convert other currencies',
            _currency,
            {
              if (!_currencies.contains(_currency)) _currency,
              ..._currencies,
            }.toList(),
            (value) => setState(() => _currency = value),
          ),
          Padding(
            padding: const EdgeInsets.all(16),
            child: FilledButton(
              onPressed: _saving ? null : _savePrefs,
              style: FilledButton.styleFrom(backgroundColor: WorthlyColors.pine),
              child: const Text('Save preferences'),
            ),
          ),
        ]),
        const SizedBox(height: 14),
        _group('Security', [
          _toggle('Face ID', 'Unlocks a local session only. The API still authorizes every request.', faceId, (value) => ref.read(faceIdProvider.notifier).setEnabled(value)),
          _toggle('Obscure app switcher', 'Hide balances when Worthly is in the background.', obscure, (value) => ref.read(obscureSwitcherProvider.notifier).setEnabled(value)),
          _toggle('Privacy mode', 'Hide every monetary value on this device.', privacy, (_) => ref.read(privacyProvider.notifier).toggle()),
        ]),
        const SizedBox(height: 14),
        _group('Sessions & devices', [
          for (final device in _devices)
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
              child: Row(
                children: [
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(device.name ?? device.platform ?? 'Session', style: const TextStyle(fontWeight: FontWeight.w500, fontSize: 13)),
                        Text(
                          'Last seen ${Period.instant(device.lastSeenAt, owner?.reportingTimezone ?? 'UTC')}${device.revoked ? ' · revoked' : ''}',
                          style: const TextStyle(fontSize: 11.5, color: WorthlyColors.faint),
                        ),
                      ],
                    ),
                  ),
                  if (!device.revoked)
                    TextButton(
                      onPressed: () async {
                        await ref.read(worthlyClientProvider).send('DELETE', '/devices/${device.id}');
                        setState(() => _devices = _devices.where((item) => item.id != device.id).toList());
                      },
                      child: const Text('Revoke', style: TextStyle(color: WorthlyColors.loss)),
                    ),
                ],
              ),
            ),
        ]),
        const SizedBox(height: 14),
        _group('Categorization', [
          Padding(
            padding: const EdgeInsets.fromLTRB(16, 8, 16, 4),
            child: Row(
              children: [
                const Text('Categories', style: TextStyle(fontWeight: FontWeight.w500, fontSize: 13)),
                const Spacer(),
                Text('${customCategories(categories).length}', style: mono(size: 12, color: WorthlyColors.faint)),
              ],
            ),
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
          Padding(
            padding: const EdgeInsets.fromLTRB(16, 0, 16, 8),
            child: Row(
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
          ),
          Padding(
            padding: const EdgeInsets.fromLTRB(16, 4, 16, 4),
            child: Row(
              children: [
                const Text('Rules', style: TextStyle(fontWeight: FontWeight.w500, fontSize: 13)),
                const Spacer(),
                Text('${_rules.length}', style: mono(size: 12, color: WorthlyColors.faint)),
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
          _valueRow('Uncategorized', '$_uncategorized', warn: _uncategorized > 0, onTap: () => ref.read(tabIndexProvider.notifier).state = 1),
        ]),
        const SizedBox(height: 14),
        _group(
          'Notifications',
          unread.isEmpty
              ? [_valueRow('Inbox', '0', sub: 'Reauthorization and repeated sync failures appear here')]
              : [
                  for (final item in unread)
                    Padding(
                      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
                      child: Row(
                        children: [
                          Expanded(
                            child: Column(
                              crossAxisAlignment: CrossAxisAlignment.start,
                              children: [
                                Text(item.type.replaceAll('_', ' '), style: const TextStyle(fontWeight: FontWeight.w500, fontSize: 13.5)),
                                Text(Period.instant(item.createdAt, owner?.reportingTimezone ?? 'UTC'), style: const TextStyle(fontSize: 11.5, color: WorthlyColors.faint)),
                              ],
                            ),
                          ),
                          TextButton(
                            onPressed: () async {
                              try {
                                await ref.read(worthlyClientProvider).send('POST', '/notifications/${item.id}/read');
                                ref.invalidate(shellDataProvider);
                              } catch (_) {
                                /* Keep the inbox usable if the mark-read call fails. */
                              }
                            },
                            child: const Text('Mark read'),
                          ),
                        ],
                      ),
                    ),
                ],
        ),
        const SizedBox(height: 14),
        _group('Data', [
          ListTile(
            title: const Text('Export transactions', style: TextStyle(fontWeight: FontWeight.w500, fontSize: 13.5)),
            subtitle: const Text('Normalized CSV', style: TextStyle(fontSize: 11.5)),
            onTap: _export,
          ),
          ListTile(
            title: const Text('Sign out', style: TextStyle(fontWeight: FontWeight.w500, fontSize: 13.5, color: WorthlyColors.loss)),
            subtitle: const Text('Revokes this device session and clears cached financial data', style: TextStyle(fontSize: 11.5)),
            onTap: () => ref.read(sessionProvider.notifier).logout(),
          ),
        ]),
        const SizedBox(height: 16),
        Text(
          'Worthly 0.1.0 · connected to $host\nLogging out revokes this device\'s session on the server.',
          textAlign: TextAlign.center,
          style: const TextStyle(fontSize: 11, height: 1.55, color: WorthlyColors.faint),
        ),
      ],
    );
  }

  Widget _group(String title, List<Widget> children) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Padding(
          padding: const EdgeInsets.only(left: 4, bottom: 8),
          child: Text(title.toUpperCase(), style: labelStyle()),
        ),
        WorthlyCard(
          padding: EdgeInsets.zero,
          child: Column(children: children),
        ),
      ],
    );
  }

  Widget _toggle(String label, String sub, bool on, ValueChanged<bool> onChanged) {
    return SwitchListTile(
      value: on,
      onChanged: onChanged,
      activeTrackColor: WorthlyColors.pine,
      title: Text(label, style: const TextStyle(fontWeight: FontWeight.w500, fontSize: 13.5)),
      subtitle: Text(sub, style: const TextStyle(fontSize: 11.5, color: WorthlyColors.faint)),
    );
  }

  Widget _selectRow(String label, String sub, String value, List<String> options, ValueChanged<String> onChanged) {
    return Padding(
      padding: const EdgeInsets.fromLTRB(16, 12, 16, 12),
      child: Row(
        children: [
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(label, style: const TextStyle(fontWeight: FontWeight.w500, fontSize: 13.5)),
                Text(sub, style: const TextStyle(fontSize: 11.5, color: WorthlyColors.faint)),
              ],
            ),
          ),
          DropdownButton<String>(
            value: value,
            underline: const SizedBox.shrink(),
            items: options.map((item) => DropdownMenuItem(value: item, child: Text(item, style: mono(size: 12.5)))).toList(),
            onChanged: (next) {
              if (next != null) {
                onChanged(next);
              }
            },
          ),
        ],
      ),
    );
  }

  Widget _valueRow(String label, String value, {String? sub, bool warn = false, VoidCallback? onTap}) {
    return ListTile(
      onTap: onTap,
      title: Text(label, style: const TextStyle(fontWeight: FontWeight.w500, fontSize: 13.5)),
      subtitle: sub == null ? null : Text(sub, style: const TextStyle(fontSize: 11.5)),
      trailing: Text(value, style: mono(size: 12.5, color: warn ? WorthlyColors.warn : WorthlyColors.faint)),
    );
  }

  String _serverLabel(String issuer) {
    final uri = Uri.parse(issuer);
    if (uri.hasPort && uri.port != 80 && uri.port != 443) {
      return '${uri.host}:${uri.port}';
    }
    return uri.host;
  }
}
