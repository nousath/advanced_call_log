import 'package:advanced_call_log/advanced_call_log.dart';
import 'package:flutter/material.dart';

void main() {
  runApp(const MyApp());
}

class MyApp extends StatelessWidget {
  const MyApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'advanced_call_log example',
      debugShowCheckedModeBanner: false,
      theme: ThemeData(
        useMaterial3: true,
        colorSchemeSeed: const Color(0xFF2563EB),
      ),
      home: const CallLogHome(),
    );
  }
}

class CallLogHome extends StatefulWidget {
  const CallLogHome({super.key});

  @override
  State<CallLogHome> createState() => _CallLogHomeState();
}

class _CallLogHomeState extends State<CallLogHome> {
  bool _granted = false;
  bool _loading = false;
  String _status = 'Tap “Request permission” to begin.';
  List<CallLogEntry> _items = const [];

  Future<void> _checkPerms() async {
    final ok = await CallLog.hasPermissions();
    setState(() {
      _granted = ok;
      _status = ok ? 'Permissions granted.' : 'Permissions not granted.';
    });
  }

  Future<void> _requestPerms() async {
    setState(() {
      _loading = true;
      _status = 'Requesting permissions…';
    });
    final ok = await CallLog.requestPermissions();
    setState(() {
      _granted = ok;
      _loading = false;
      _status = ok ? 'Permissions granted.' : 'Permission denied.';
    });
  }

  Future<void> _loadRecent() async {
    setState(() {
      _loading = true;
      _status = 'Loading recent calls…';
    });

    final logs = await CallLog.query(
      dateTimeFrom: DateTime.now().subtract(const Duration(days: 7)),
    );

    setState(() {
      _items = logs.toList();
      _loading = false;
      _status = 'Loaded ${_items.length} call(s).';
    });
  }

  Future<void> _loadMissed() async {
    setState(() {
      _loading = true;
      _status = 'Loading missed calls…';
    });

    final logs = await CallLog.query(
      dateTimeFrom: DateTime.now().subtract(const Duration(days: 30)),
      type: CallType.missed,
    );

    setState(() {
      _items = logs.toList();
      _loading = false;
      _status = 'Loaded ${_items.length} missed call(s).';
    });
  }

  IconData _iconFor(CallType? t) {
    switch (t) {
      case CallType.incoming:
        return Icons.call_received_rounded;
      case CallType.outgoing:
        return Icons.call_made_rounded;
      case CallType.missed:
        return Icons.call_missed_rounded;
      case CallType.rejected:
        return Icons.call_end_rounded;
      case CallType.blocked:
        return Icons.block_rounded;
      case CallType.voiceMail:
        return Icons.voicemail_rounded;
      default:
        return Icons.call_rounded;
    }
  }

  String _typeLabel(CallType? t) {
    switch (t) {
      case CallType.incoming:
        return 'Incoming';
      case CallType.outgoing:
        return 'Outgoing';
      case CallType.missed:
        return 'Missed';
      case CallType.rejected:
        return 'Rejected';
      case CallType.blocked:
        return 'Blocked';
      case CallType.voiceMail:
        return 'Voicemail';
      default:
        return 'Unknown';
    }
  }

  @override
  void initState() {
    super.initState();
    _checkPerms();
  }

  @override
  Widget build(BuildContext context) {
    final cs = Theme.of(context).colorScheme;

    return Scaffold(
      appBar: AppBar(
        title: const Text('advanced_call_log'),
        actions: [
          IconButton(
            tooltip: 'Refresh permission state',
            onPressed: _loading ? null : _checkPerms,
            icon: const Icon(Icons.verified_user_rounded),
          ),
        ],
      ),
      body: Column(
        children: [
          Container(
            width: double.infinity,
            padding: const EdgeInsets.all(16),
            decoration: BoxDecoration(
              color: cs.primary.withOpacity(0.06),
              border: Border(
                bottom: BorderSide(color: cs.outlineVariant.withOpacity(0.6)),
              ),
            ),
            child: Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Icon(
                  _granted ? Icons.check_circle_rounded : Icons.lock_rounded,
                  color: _granted ? cs.tertiary : cs.primary,
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: Text(
                    _status,
                    style: const TextStyle(fontWeight: FontWeight.w700),
                  ),
                ),
                if (_loading)
                  const SizedBox(
                    width: 18,
                    height: 18,
                    child: CircularProgressIndicator(strokeWidth: 2),
                  ),
              ],
            ),
          ),

          Padding(
            padding: const EdgeInsets.fromLTRB(16, 14, 16, 8),
            child: Wrap(
              spacing: 10,
              runSpacing: 10,
              children: [
                FilledButton.icon(
                  onPressed: _loading ? null : _requestPerms,
                  icon: const Icon(Icons.security_rounded, size: 18),
                  label: const Text('Request permission'),
                ),
                FilledButton.tonalIcon(
                  onPressed: (!_granted || _loading) ? null : _loadRecent,
                  icon: const Icon(Icons.history_rounded, size: 18),
                  label: const Text('Last 7 days'),
                ),
                FilledButton.tonalIcon(
                  onPressed: (!_granted || _loading) ? null : _loadMissed,
                  icon: const Icon(Icons.call_missed_rounded, size: 18),
                  label: const Text('Missed (30d)'),
                ),
              ],
            ),
          ),

          Expanded(
            child: _items.isEmpty
                ? Center(
                    child: Text(
                      _granted ? 'No data yet.' : 'Grant permission to load call logs.',
                      style: TextStyle(color: cs.onSurfaceVariant),
                    ),
                  )
                : ListView.separated(
                    padding: const EdgeInsets.fromLTRB(16, 10, 16, 16),
                    itemBuilder: (context, i) {
                      final e = _items[i];
                      final ts = e.timestamp ?? 0;
                      final dt = DateTime.fromMillisecondsSinceEpoch(ts);

                      final title = (e.name?.trim().isNotEmpty ?? false)
                          ? e.name!.trim()
                          : (e.formattedNumber ?? e.number ?? 'Unknown');

                      final subtitle = '${_typeLabel(e.callType)} • ${e.duration ?? 0}s • ${dt.toLocal()}';

                      final sim = e.simDisplayName;
                      final simText = (sim != null && sim.trim().isNotEmpty)
                          ? 'SIM: ${sim.trim()}'
                          : (e.phoneAccountId != null ? 'Account: ${e.phoneAccountId}' : null);

                      return ListTile(
                        contentPadding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
                        leading: CircleAvatar(
                          backgroundColor: cs.primary.withOpacity(0.12),
                          child: Icon(_iconFor(e.callType), color: cs.primary),
                        ),
                        title: Text(title, maxLines: 1, overflow: TextOverflow.ellipsis),
                        subtitle: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text(subtitle, maxLines: 1, overflow: TextOverflow.ellipsis),
                            if (simText != null)
                              Text(simText, style: TextStyle(color: cs.onSurfaceVariant)),
                          ],
                        ),
                      );
                    },
                    separatorBuilder: (_, __) => Divider(color: cs.outlineVariant.withOpacity(0.6)),
                    itemCount: _items.length,
                  ),
          ),
        ],
      ),
    );
  }
}
