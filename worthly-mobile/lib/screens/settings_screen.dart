import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:worthly_mobile/api/csv_export.dart';
import 'package:worthly_mobile/api/models.dart';
import 'package:worthly_mobile/api/worthly_client.dart';
import 'package:worthly_mobile/features/auth/auth_config.dart';
import 'package:worthly_mobile/features/session/session.dart';
import 'package:worthly_mobile/features/session/shell_data.dart';
import 'package:worthly_mobile/theme/colors.dart';
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
  List<Device> _devices = [];
  int _uncategorized = 0;
  bool _saving = false;

  @override
  void initState() {
    super.initState();
    final owner = ref.read(sessionProvider).owner;
    _timezone = owner?.reportingTimezone ?? 'UTC';
    _currency = owner?.reportingCurrency ?? 'EUR';
    Future.microtask(_load);
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
        body: {'reportingTimezone': _timezone, 'reportingCurrency': _currency},
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
    final notifications = ref.watch(shellDataProvider).asData?.value.notifications ?? [];
    final unread = notifications.where((item) => item.readAt == null).toList();
    final host = _serverLabel(AuthConfig.local.issuer);
    return ListView(
      padding: const EdgeInsets.fromLTRB(18, 14, 18, 26),
      children: [
        _group('Reporting', [
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
          _valueRow('Uncategorized transactions', '$_uncategorized', warn: _uncategorized > 0, onTap: () => ref.read(tabIndexProvider.notifier).state = 1),
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
