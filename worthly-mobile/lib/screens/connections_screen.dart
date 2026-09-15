import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:url_launcher/url_launcher.dart';
import 'package:worthly_mobile/api/models.dart';
import 'package:worthly_mobile/api/worthly_client.dart';
import 'package:worthly_mobile/features/session/session.dart';
import 'package:worthly_mobile/features/session/shell_data.dart';
import 'package:worthly_mobile/theme/colors.dart';
import 'package:worthly_mobile/theme/period.dart';
import 'package:worthly_mobile/theme/theme.dart';
import 'package:worthly_mobile/widgets/ui.dart';

class ConnectionsScreen extends ConsumerStatefulWidget {
  const ConnectionsScreen({super.key});

  @override
  ConsumerState<ConnectionsScreen> createState() => _ConnectionsScreenState();
}

class _ConnectionsScreenState extends ConsumerState<ConnectionsScreen> {
  List<BankChoice> _banks = [];
  String? _banksError;
  List<_LogRow> _log = [];
  Connection? _handoff;
  Connection? _purge;
  String? _busyId;
  String? _t212Error;
  String? _authError;
  String? _syncError;
  bool _t212Open = false;
  final _t212Key = TextEditingController();
  final _t212Secret = TextEditingController();

  @override
  void initState() {
    super.initState();
    Future.microtask(_load);
  }

  Future<void> _load() async {
    try {
      final banks = await ref.read(worthlyClientProvider).get('/connections/banks?country=PT', parseBanks);
      if (mounted) {
        setState(() {
          _banks = banks;
          _banksError = null;
        });
      }
    } catch (err) {
      if (mounted) {
        setState(() {
          _banks = [];
          _banksError = err is StateError ? err.message : 'provider_error';
        });
      }
    }
    await _loadLog();
  }

  Future<void> _loadLog() async {
    final connections = ref.read(shellDataProvider).asData?.value.connections ?? [];
    final rows = <_LogRow>[];
    for (final connection in connections) {
      try {
        final page = await ref.read(worthlyClientProvider).get('/connections/${connection.id}/sync-runs?size=5', parseSyncRuns);
        rows.addAll(page.items.map((item) => _LogRow(run: item, provider: connection.label)));
      } catch (_) {
        /* keep other rows */
      }
    }
    rows.sort((a, b) => b.run.startedAt.compareTo(a.run.startedAt));
    if (mounted) {
      setState(() => _log = rows.take(8).toList());
    }
  }

  Future<void> _authorize(String name, String country) async {
    setState(() => _authError = null);
    try {
      await ref.read(sessionProvider.notifier).runExternal(() async {
        final url = await ref.read(worthlyClientProvider).send(
          'POST',
          '/connections/enable-banking/authorize',
          body: {'name': name, 'country': country, 'returnClient': 'MOBILE'},
          parse: parseAuthUrl,
        );
        if (url != null) {
          await launchUrl(Uri.parse(url), mode: LaunchMode.externalApplication);
        }
      });
    } catch (err) {
      if (mounted) {
        setState(() {
          _authError = err is StateError && err.message == 'redirect_url_mismatch'
              ? 'The Enable Banking app redirect URL does not match Worthly.'
              : 'Enable Banking rejected the bank login start. Try again.';
        });
      }
    }
    if (mounted) {
      setState(() => _handoff = null);
    }
  }

  Future<void> _sync(Connection connection) async {
    setState(() {
      _busyId = connection.id;
      _syncError = null;
    });
    try {
      await ref.read(worthlyClientProvider).send('POST', '/connections/${connection.id}/sync');
      ref.invalidate(shellDataProvider);
      await _loadLog();
    } catch (_) {
      if (mounted) {
        setState(() {
          _syncError = connection.lastErrorCode == 'session_unreadable'
              ? 'This bank session can no longer be read. Disconnect and connect again.'
              : 'Sync did not finish. Try again, or disconnect and reconnect the bank.';
        });
      }
    } finally {
      if (mounted) {
        setState(() => _busyId = null);
      }
    }
  }

  Future<void> _disconnect(Connection connection) async {
    setState(() => _busyId = connection.id);
    try {
      await ref.read(worthlyClientProvider).send('DELETE', '/connections/${connection.id}');
      ref.invalidate(shellDataProvider);
    } finally {
      if (mounted) {
        setState(() => _busyId = null);
      }
    }
  }

  Future<void> _doPurge(Connection connection) async {
    setState(() => _busyId = connection.id);
    try {
      await ref.read(worthlyClientProvider).send('POST', '/connections/${connection.id}/purge', body: {'confirm': true});
      ref.invalidate(shellDataProvider);
      setState(() => _purge = null);
    } finally {
      if (mounted) {
        setState(() => _busyId = null);
      }
    }
  }

  Future<void> _connectTrading212() async {
    setState(() {
      _busyId = 'trading-212';
      _t212Error = null;
    });
    try {
      final connection = await ref.read(worthlyClientProvider).send(
        'POST',
        '/connections/trading-212',
        body: {'apiKey': _t212Key.text.trim(), 'apiSecret': _t212Secret.text, 'environment': 'LIVE'},
        parse: parseConnection,
      );
      ref.invalidate(shellDataProvider);
      if (connection?.status == 'ERROR' && mounted) {
        setState(() => _t212Error = 'Trading 212 rejected those credentials. Use a read-only Live key from Trading 212 Invest (or Stocks ISA) and try again.');
      } else {
        _t212Key.clear();
        _t212Secret.clear();
        if (mounted) {
          setState(() => _t212Open = false);
        }
      }
    } catch (_) {
      if (mounted) {
        setState(() => _t212Error = 'Could not connect Trading 212.');
      }
    } finally {
      if (mounted) {
        setState(() => _busyId = null);
      }
    }
  }

  @override
  void dispose() {
    _t212Key.dispose();
    _t212Secret.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final owner = ref.watch(sessionProvider).owner;
    final connections = ref.watch(shellDataProvider).asData?.value.connections ?? [];
    final result = ref.watch(connectionResultProvider);
    final names = connections.map((item) => item.institutionName).whereType<String>().toSet();
    final unused = _banks.where((bank) => !names.contains(bank.name)).toList();
    final hasTrading212 = connections.any((item) => item.provider == 'TRADING_212');
    return Stack(
      children: [
        ListView(
          padding: const EdgeInsets.fromLTRB(18, 14, 18, 26),
          children: [
            TextButton.icon(
              onPressed: () => ref.read(connectionsOpenProvider.notifier).state = false,
              icon: const Icon(Icons.chevron_left, size: 18),
              label: const Text('Accounts', style: TextStyle(fontWeight: FontWeight.w600, fontSize: 12.5)),
              style: TextButton.styleFrom(foregroundColor: WorthlyColors.pine, alignment: Alignment.centerLeft),
            ),
            if (result == 'ok')
              const _Banner(
                color: Color(0x1F14654A),
                border: Color(0x4D14654A),
                textColor: WorthlyColors.gain,
                text: 'Bank connection completed. Balances should appear on the dashboard shortly — tap Sync now if they are still empty.',
              ),
            if (result == 'error')
              const _Banner(
                color: WorthlyColors.warnBg,
                border: Color(0x4D8A6412),
                textColor: Color(0xFF6E5A2E),
                text: 'Bank authorization did not finish. You can try again — Worthly never sees your bank password.',
              ),
            if (_syncError != null)
              _Banner(
                color: WorthlyColors.warnBg,
                border: const Color(0x4D8A6412),
                textColor: const Color(0xFF6E5A2E),
                text: _syncError!,
              ),
            if (connections.isEmpty)
              const EmptyState(
                title: 'No providers yet',
                body: 'Connect a bank or Trading 212 from the section below.',
              )
            else
              for (final connection in connections) ...[
                _ConnectionCard(
                  connection: connection,
                  timezone: owner?.reportingTimezone ?? 'UTC',
                  busy: _busyId == connection.id,
                  onSync: () => _sync(connection),
                  onDisconnect: () => _disconnect(connection),
                  onPurge: () => setState(() => _purge = connection),
                  onReauth: () => setState(() => _handoff = connection),
                  onReplaceTrading212: connection.provider == 'TRADING_212' &&
                          (connection.status == 'CONFIGURATION_REQUIRED' || connection.status == 'ERROR')
                      ? () => setState(() {
                            _t212Error = null;
                            _t212Open = true;
                          })
                      : null,
                  t212Error: connection.provider == 'TRADING_212' ? _t212Error : null,
                ),
                const SizedBox(height: 12),
              ],
            WorthlyCard(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text('CONNECT A BANK', style: labelStyle()),
                  const SizedBox(height: 14),
                  if (unused.isNotEmpty || !hasTrading212)
                    Wrap(
                      spacing: 8,
                      runSpacing: 8,
                      children: [
                        for (final bank in unused)
                          FilledButton(
                            onPressed: () => _authorize(bank.name, bank.country),
                            style: FilledButton.styleFrom(backgroundColor: WorthlyColors.pine),
                            child: Text('Connect ${bank.name}'),
                          ),
                        if (!hasTrading212)
                          FilledButton(
                            onPressed: () => setState(() {
                              _t212Error = null;
                              _t212Open = true;
                            }),
                            style: FilledButton.styleFrom(backgroundColor: WorthlyColors.pine),
                            child: const Text('Connect Trading 212'),
                          ),
                      ],
                    )
                  else if (_banksError == 'configuration_required')
                    const Text(
                      'Santander and Revolut need an Enable Banking app on this server (application ID and RSA private key). Until that is configured, bank buttons cannot appear. You still log in at the bank — Worthly never sees that password.',
                      style: TextStyle(fontSize: 12, height: 1.5, color: WorthlyColors.muted),
                    )
                  else if (_banksError != null)
                    Text(
                      'Banks could not be loaded (${_banksError!.replaceAll('_', ' ')}).',
                      style: const TextStyle(fontSize: 12, height: 1.5, color: WorthlyColors.muted),
                    )
                  else if (_banks.isEmpty)
                    const Text(
                      'Enable Banking is configured, but this sandbox did not return Santander or Revolut. Add a Mock ASPSP in the Enable Banking control panel, then refresh. Real banks need a Production application.',
                      style: TextStyle(fontSize: 12, height: 1.5, color: WorthlyColors.muted),
                    )
                  else
                    const Text(
                      'All supported providers are already connected.',
                      style: TextStyle(fontSize: 12, color: WorthlyColors.muted),
                    ),
                  if (unused.isNotEmpty || !hasTrading212) ...[
                    const SizedBox(height: 12),
                    const Text(
                      'Banks open in the system browser for Open Banking. Trading 212 uses a read-only API key — Worthly never sees your login password.',
                      style: TextStyle(fontSize: 12, color: WorthlyColors.muted),
                    ),
                  ],
                  if (_authError != null) ...[
                    const SizedBox(height: 10),
                    Text(_authError!, style: const TextStyle(color: WorthlyColors.loss, fontSize: 12, height: 1.5)),
                  ],
                ],
              ),
            ),
            const SizedBox(height: 12),
            WorthlyCard(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text('SYNCHRONIZATION LOG', style: labelStyle()),
                  if (_log.isEmpty)
                    const Padding(
                      padding: EdgeInsets.only(top: 12),
                      child: Text('No sync runs yet.', style: TextStyle(color: WorthlyColors.muted)),
                    )
                  else
                    for (final row in _log)
                      Padding(
                        padding: const EdgeInsets.only(top: 12),
                        child: Row(
                          children: [
                            Expanded(
                              child: Column(
                                crossAxisAlignment: CrossAxisAlignment.start,
                                children: [
                                  Text(row.provider, style: const TextStyle(fontWeight: FontWeight.w500, fontSize: 12.5)),
                                  Text(_detail(row.run), style: const TextStyle(fontSize: 12, color: Color(0xFF5E6A67))),
                                ],
                              ),
                            ),
                            Text(row.run.status, style: mono(size: 9.5, color: _outcome(row.run.status))),
                          ],
                        ),
                      ),
                ],
              ),
            ),
            const SizedBox(height: 12),
            const Text(
              'Disconnecting stops future sync. Purging deletes locally stored provider data for that connection. Your own categories, notes and transfer matches are kept.',
              style: TextStyle(fontSize: 11, height: 1.55, color: WorthlyColors.faint),
            ),
          ],
        ),
        if (_t212Open)
          _Trading212Dialog(
            apiKey: _t212Key,
            apiSecret: _t212Secret,
            error: _t212Error,
            busy: _busyId == 'trading-212',
            onCancel: _busyId == 'trading-212'
                ? null
                : () => setState(() {
                      _t212Open = false;
                      _t212Error = null;
                    }),
            onSubmit: _connectTrading212,
          ),
        if (_handoff != null)
          _HandoffDialog(
            name: _handoff!.label,
            onCancel: () => setState(() => _handoff = null),
            onContinue: () => _authorize(_handoff!.institutionName ?? '', _handoff!.institutionCountry ?? 'PT'),
          ),
        if (_purge != null)
          _ConfirmDialog(
            title: 'Delete local provider data?',
            body: 'This removes imported data for ${_purge!.label}. Categories and notes you wrote stay.',
            confirmLabel: 'Delete data',
            onCancel: () => setState(() => _purge = null),
            onConfirm: () => _doPurge(_purge!),
          ),
      ],
    );
  }

  String _detail(SyncRun run) {
    if (run.errorCode != null) {
      return run.errorCode!.replaceAll('_', ' ');
    }
    return '${run.importedCount ?? 0} imported · ${run.updatedCount ?? 0} updated';
  }

  Color _outcome(String status) {
    switch (status) {
      case 'SUCCEEDED':
        return WorthlyColors.gain;
      case 'FAILED':
        return WorthlyColors.loss;
      case 'RATE_LIMITED':
        return WorthlyColors.faint;
      default:
        return WorthlyColors.warn;
    }
  }
}

class _ConnectionCard extends StatelessWidget {
  const _ConnectionCard({
    required this.connection,
    required this.timezone,
    required this.busy,
    required this.onSync,
    required this.onDisconnect,
    required this.onPurge,
    required this.onReauth,
    this.onReplaceTrading212,
    this.t212Error,
  });

  final Connection connection;
  final String timezone;
  final bool busy;
  final VoidCallback onSync;
  final VoidCallback onDisconnect;
  final VoidCallback onPurge;
  final VoidCallback onReauth;
  final VoidCallback? onReplaceTrading212;
  final String? t212Error;

  @override
  Widget build(BuildContext context) {
    final bank = connection.provider != 'TRADING_212';
    final needsAuth = connection.status == 'REAUTH_REQUIRED';
    final healthy = connection.status == 'ACTIVE';
    final consent = connection.provider == 'TRADING_212'
        ? 'Credentials'
        : (needsAuth ? 'Consent expired' : 'Consent expires');
    final consentValue = connection.consentExpiresAt == null
        ? (bank ? '—' : 'Encrypted on server')
        : Period.instant(connection.consentExpiresAt, timezone);
    return WorthlyCard(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Container(
                width: 32,
                height: 32,
                decoration: BoxDecoration(color: WorthlyColors.ink.withValues(alpha: 0.08), borderRadius: BorderRadius.circular(9)),
                alignment: Alignment.center,
                child: Text(connection.label.substring(0, 1).toUpperCase(), style: const TextStyle(fontWeight: FontWeight.w600)),
              ),
              const SizedBox(width: 10),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(connection.label, style: const TextStyle(fontWeight: FontWeight.w600, fontSize: 14)),
                    Text(bank ? 'Enable Banking · AIS' : 'Public API · read-only key', style: const TextStyle(fontSize: 11, color: WorthlyColors.faint)),
                  ],
                ),
              ),
              StatusChip(status: connection.status),
            ],
          ),
          const SizedBox(height: 14),
          _row('Last successful sync', Period.instant(connection.lastSuccessfulSyncAt, timezone)),
          _row(consent, consentValue, warn: needsAuth || connection.status == 'CONFIGURATION_REQUIRED'),
          _row('Access', 'Read-only'),
          if (connection.lastErrorCode != null && connection.lastErrorCode!.isNotEmpty)
            _row('Last error', connection.lastErrorCode!.replaceAll('_', ' '), warn: true),
          const SizedBox(height: 14),
          if (needsAuth && bank) ...[
            const Text(
              'Reauthorization opens your bank in the system browser. You confirm there; Worthly never sees your credentials.',
              style: TextStyle(fontSize: 11.5, height: 1.5, color: Color(0xFF6E5A2E)),
            ),
            const SizedBox(height: 10),
            PineButton(label: 'Reauthorize in browser', onPressed: busy ? null : onReauth),
          ] else if (onReplaceTrading212 != null) ...[
            if (t212Error != null)
              Text(t212Error!, style: const TextStyle(fontSize: 12, height: 1.5, color: WorthlyColors.loss)),
            const Text(
              'Add a read-only Trading 212 API key. Worthly encrypts it on the server.',
              style: TextStyle(fontSize: 12, height: 1.5, color: WorthlyColors.muted),
            ),
            const SizedBox(height: 10),
            PineButton(label: 'Replace API key', onPressed: busy ? null : onReplaceTrading212),
          ] else if (connection.status == 'CONFIGURATION_REQUIRED')
            const Text(
              'Add a read-only Trading 212 API key from this screen.',
              style: TextStyle(fontSize: 12, height: 1.5, color: WorthlyColors.muted),
            )
          else
            Row(
              children: [
                if (healthy || connection.status == 'ERROR')
                  Expanded(
                    child: OutlinedButton(onPressed: busy ? null : onSync, child: const Text('Sync now')),
                  ),
                if (healthy || connection.status == 'ERROR') const SizedBox(width: 8),
                Expanded(
                  child: OutlinedButton(
                    onPressed: busy ? null : (bank ? onDisconnect : onPurge),
                    style: OutlinedButton.styleFrom(foregroundColor: WorthlyColors.loss, side: BorderSide(color: WorthlyColors.loss.withValues(alpha: 0.3))),
                    child: Text(bank ? 'Disconnect' : 'Delete data'),
                  ),
                ),
              ],
            ),
        ],
      ),
    );
  }

  Widget _row(String k, String v, {bool warn = false}) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 7),
      child: Row(
        children: [
          Expanded(child: Text(k, style: const TextStyle(fontSize: 12, color: WorthlyColors.muted))),
          Text(v, style: mono(size: 12, color: warn ? WorthlyColors.warn : WorthlyColors.ink)),
        ],
      ),
    );
  }
}

class _Banner extends StatelessWidget {
  const _Banner({required this.color, required this.border, required this.textColor, required this.text});

  final Color color;
  final Color border;
  final Color textColor;
  final String text;

  @override
  Widget build(BuildContext context) {
    return Container(
      margin: const EdgeInsets.only(bottom: 12),
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 13),
      decoration: BoxDecoration(color: color, borderRadius: BorderRadius.circular(12), border: Border.all(color: border)),
      child: Text(text, style: TextStyle(color: textColor, fontSize: 13, height: 1.45)),
    );
  }
}

class _Trading212Dialog extends StatelessWidget {
  const _Trading212Dialog({
    required this.apiKey,
    required this.apiSecret,
    required this.error,
    required this.busy,
    required this.onCancel,
    required this.onSubmit,
  });

  static final _helpUri = Uri.parse(
    'https://helpcentre.trading212.com/hc/en-us/articles/14584770928157-Trading-212-API-key',
  );

  final TextEditingController apiKey;
  final TextEditingController apiSecret;
  final String? error;
  final bool busy;
  final VoidCallback? onCancel;
  final VoidCallback onSubmit;

  @override
  Widget build(BuildContext context) {
    return Material(
      color: WorthlyColors.ink.withValues(alpha: 0.46),
      child: Center(
        child: SingleChildScrollView(
          padding: const EdgeInsets.all(24),
          child: Container(
            width: 460,
            padding: const EdgeInsets.all(26),
            decoration: BoxDecoration(color: WorthlyColors.paper, borderRadius: BorderRadius.circular(18)),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                const Text('Connect Trading 212', style: TextStyle(fontWeight: FontWeight.w600, fontSize: 16)),
                const SizedBox(height: 8),
                const Text(
                  'Trading 212 is not Open Banking. Worthly uses a read-only Live API key from Invest or Stocks ISA — not Crypto, and not your login password.',
                  style: TextStyle(fontSize: 12.5, height: 1.55, color: WorthlyColors.muted),
                ),
                const SizedBox(height: 12),
                const Text(
                  '1. In Trading 212, open Settings → API (Beta) → Generate API key.\n'
                  '2. Enable read access only (account, portfolio, history). Do not enable orders.\n'
                  '3. Copy the secret immediately — Trading 212 shows it once.',
                  style: TextStyle(fontSize: 13, height: 1.55, color: WorthlyColors.muted),
                ),
                TextButton(
                  onPressed: () => launchUrl(_helpUri, mode: LaunchMode.externalApplication),
                  style: TextButton.styleFrom(padding: EdgeInsets.zero, alignment: Alignment.centerLeft),
                  child: const Text('How to get your Trading 212 API key'),
                ),
                if (error != null) ...[
                  const SizedBox(height: 4),
                  Text(error!, style: const TextStyle(color: WorthlyColors.loss, fontSize: 12, height: 1.5)),
                ],
                const SizedBox(height: 12),
                _Trading212Fields(apiKey: apiKey, apiSecret: apiSecret),
                const SizedBox(height: 16),
                Row(
                  children: [
                    Expanded(child: OutlinedButton(onPressed: onCancel, child: const Text('Cancel'))),
                    const SizedBox(width: 9),
                    Expanded(
                      child: FilledButton(
                        onPressed: busy ? null : onSubmit,
                        style: FilledButton.styleFrom(backgroundColor: WorthlyColors.pine),
                        child: const Text('Save read-only key'),
                      ),
                    ),
                  ],
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

class _HandoffDialog extends StatelessWidget {
  const _HandoffDialog({required this.name, required this.onCancel, required this.onContinue});

  final String name;
  final VoidCallback onCancel;
  final VoidCallback onContinue;

  @override
  Widget build(BuildContext context) {
    return Material(
      color: WorthlyColors.ink.withValues(alpha: 0.5),
      child: Center(
        child: Container(
          margin: const EdgeInsets.all(32),
          padding: const EdgeInsets.all(24),
          decoration: BoxDecoration(color: WorthlyColors.paper, borderRadius: BorderRadius.circular(20)),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Container(
                width: 46,
                height: 46,
                decoration: BoxDecoration(color: WorthlyColors.pine, borderRadius: BorderRadius.circular(14)),
                child: const Icon(Icons.open_in_new, color: WorthlyColors.cream),
              ),
              const SizedBox(height: 14),
              Text('Opening $name', style: const TextStyle(fontWeight: FontWeight.w600, fontSize: 15.5)),
              const SizedBox(height: 7),
              const Text(
                'You\'ll confirm access in your bank\'s own app or site, then return here automatically through a verified link.',
                textAlign: TextAlign.center,
                style: TextStyle(fontSize: 12.5, height: 1.55, color: WorthlyColors.muted),
              ),
              const SizedBox(height: 18),
              PineButton(label: 'Continue', onPressed: onContinue),
              TextButton(onPressed: onCancel, child: const Text('Not now')),
            ],
          ),
        ),
      ),
    );
  }
}

class _ConfirmDialog extends StatelessWidget {
  const _ConfirmDialog({
    required this.title,
    required this.body,
    required this.confirmLabel,
    required this.onCancel,
    required this.onConfirm,
  });

  final String title;
  final String body;
  final String confirmLabel;
  final VoidCallback onCancel;
  final VoidCallback onConfirm;

  @override
  Widget build(BuildContext context) {
    return Material(
      color: WorthlyColors.ink.withValues(alpha: 0.46),
      child: Center(
        child: Container(
          margin: const EdgeInsets.all(32),
          padding: const EdgeInsets.all(26),
          decoration: BoxDecoration(color: WorthlyColors.paper, borderRadius: BorderRadius.circular(18)),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(title, style: const TextStyle(fontWeight: FontWeight.w600, fontSize: 16)),
              const SizedBox(height: 8),
              Text(body, style: const TextStyle(fontSize: 13, height: 1.5, color: WorthlyColors.muted)),
              const SizedBox(height: 20),
              Row(
                children: [
                  Expanded(child: OutlinedButton(onPressed: onCancel, child: const Text('Cancel'))),
                  const SizedBox(width: 9),
                  Expanded(
                    child: FilledButton(
                      onPressed: onConfirm,
                      style: FilledButton.styleFrom(backgroundColor: WorthlyColors.loss),
                      child: Text(confirmLabel),
                    ),
                  ),
                ],
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _LogRow {
  const _LogRow({required this.run, required this.provider});

  final SyncRun run;
  final String provider;
}

class _Trading212Fields extends StatefulWidget {
  const _Trading212Fields({
    required this.apiKey,
    required this.apiSecret,
  });

  final TextEditingController apiKey;
  final TextEditingController apiSecret;

  @override
  State<_Trading212Fields> createState() => _Trading212FieldsState();
}

class _Trading212FieldsState extends State<_Trading212Fields> {
  bool _showSecret = false;

  @override
  Widget build(BuildContext context) {
    return Column(
      children: [
        TextField(
          controller: widget.apiKey,
          obscureText: false,
          autocorrect: false,
          enableSuggestions: false,
          decoration: _decoration('API key'),
        ),
        const SizedBox(height: 8),
        TextField(
          controller: widget.apiSecret,
          obscureText: !_showSecret,
          autocorrect: false,
          enableSuggestions: false,
          decoration: _decoration('API secret').copyWith(
            suffixIcon: IconButton(
              tooltip: _showSecret ? 'Hide secret' : 'Show secret',
              onPressed: () => setState(() => _showSecret = !_showSecret),
              icon: Icon(
                _showSecret ? Icons.visibility_off_outlined : Icons.visibility_outlined,
                size: 20,
                color: WorthlyColors.faint,
              ),
            ),
          ),
        ),
      ],
    );
  }

  InputDecoration _decoration(String hint) {
    return InputDecoration(
      hintText: hint,
      filled: true,
      fillColor: Colors.white,
      contentPadding: const EdgeInsets.symmetric(horizontal: 12, vertical: 12),
      border: OutlineInputBorder(
        borderRadius: BorderRadius.circular(10),
        borderSide: BorderSide(color: WorthlyColors.ink.withValues(alpha: 0.12)),
      ),
      enabledBorder: OutlineInputBorder(
        borderRadius: BorderRadius.circular(10),
        borderSide: BorderSide(color: WorthlyColors.ink.withValues(alpha: 0.12)),
      ),
    );
  }
}
