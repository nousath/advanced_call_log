import 'package:flutter/services.dart';

/// Android call log reader.
///
/// iOS is not supported.
class CallLog {
  CallLog._();

  static const MethodChannel _channel = MethodChannel('com.nh97.advanced_call_log');

  static const Iterable<CallLogEntry> _empty = Iterable<CallLogEntry>.empty();

  /// Check if READ_CALL_LOG + READ_PHONE_STATE are granted.
  static Future<bool> hasPermissions() async {
    final bool? ok = await _channel.invokeMethod<bool>('hasPermissions');
    return ok ?? false;
  }

  /// Request READ_CALL_LOG + READ_PHONE_STATE.
  /// Returns true if granted.
  ///
  /// NOTE: This requires a foreground Activity.
  static Future<bool> requestPermissions() async {
    final bool? ok = await _channel.invokeMethod<bool>('requestPermissions');
    return ok ?? false;
  }

  /// Get all call history log entries.
  ///
  /// If permissions are not granted, this will trigger an Android permission prompt.
  static Future<Iterable<CallLogEntry>> get() async {
    final Iterable<dynamic>? result = await _channel.invokeMethod('get');
    return result?.map((m) => CallLogEntry.fromMap(m)) ?? _empty;
  }

  /// Query call history log entries.
  ///
  /// - [dateFrom]/[dateTo]: unix timestamp in milliseconds
  /// - [dateTimeFrom]/[dateTimeTo]: DateTime alternative (millisecondsSinceEpoch)
  /// - [durationFrom]/[durationTo]: seconds
  /// - [type]: call type filter (mapped to Android CallLog TYPE values)
  static Future<Iterable<CallLogEntry>> query({
    int? dateFrom,
    int? dateTo,
    int? durationFrom,
    int? durationTo,
    DateTime? dateTimeFrom,
    DateTime? dateTimeTo,
    String? name,
    String? number,
    CallType? type,
    String? cachedMatchedNumber,
    String? phoneAccountId,
  }) async {
    assert(!(dateFrom != null && dateTimeFrom != null), 'use only one of dateTimeFrom/dateFrom');
    assert(!(dateTo != null && dateTimeTo != null), 'use only one of dateTimeTo/dateTo');

    final int? _dateFrom = dateFrom ?? dateTimeFrom?.millisecondsSinceEpoch;
    final int? _dateTo = dateTo ?? dateTimeTo?.millisecondsSinceEpoch;

    final params = <String, String?>{
      'dateFrom': _dateFrom?.toString(),
      'dateTo': _dateTo?.toString(),
      'durationFrom': durationFrom?.toString(),
      'durationTo': durationTo?.toString(),
      'name': name,
      'number': number,
      'type': _androidTypeValue(type)?.toString(),
      'cachedMatchedNumber': cachedMatchedNumber,
      'phoneAccountId': phoneAccountId,
    };

    final Iterable<dynamic>? records = await _channel.invokeMethod('query', params);
    return records?.map((m) => CallLogEntry.fromMap(m)) ?? _empty;
  }

  /// Android CallLog TYPE values are 1..8.
  static int? _androidTypeValue(CallType? t) {
    if (t == null) return null;
    switch (t) {
      case CallType.incoming:
        return 1;
      case CallType.outgoing:
        return 2;
      case CallType.missed:
        return 3;
      case CallType.voiceMail:
        return 4;
      case CallType.rejected:
        return 5;
      case CallType.blocked:
        return 6;
      case CallType.answeredExternally:
        return 7;
      case CallType.unknown:
        return 8;
      case CallType.wifiIncoming:
      case CallType.wifiOutgoing:
        // Non-standard. Avoid mapping for Android filter.
        return null;
    }
  }
}

/// Convert Android numeric call type to [CallType].
CallType getCallType(int n) {
  if (n == 100) return CallType.wifiOutgoing;
  if (n == 101) return CallType.wifiIncoming;
  if (n >= 1 && n <= 8) return CallType.values[n - 1];
  return CallType.unknown;
}

/// PODO for one call log entry.
class CallLogEntry {
  CallLogEntry({
    this.name,
    this.number,
    this.formattedNumber,
    this.callType,
    this.duration,
    this.timestamp,
    this.cachedNumberType,
    this.cachedNumberLabel,
    this.cachedMatchedNumber,
    this.simDisplayName,
    this.phoneAccountId,
  });

  CallLogEntry.fromMap(Map<dynamic, dynamic> m) {
    name = m['name'];
    number = m['number'];
    formattedNumber = m['formattedNumber'];
    callType = getCallType((m['callType'] ?? 0) as int);
    duration = m['duration'];
    timestamp = m['timestamp'];
    cachedNumberType = m['cachedNumberType'];
    cachedNumberLabel = m['cachedNumberLabel'];
    cachedMatchedNumber = m['cachedMatchedNumber'];
    simDisplayName = m['simDisplayName'];
    phoneAccountId = m['phoneAccountId'];
  }

  String? name;
  String? number;
  String? formattedNumber;
  CallType? callType;
  int? duration;
  int? timestamp;
  int? cachedNumberType;
  String? cachedNumberLabel;
  String? cachedMatchedNumber;
  String? simDisplayName;
  String? phoneAccountId;
}

/// All possible call types.
enum CallType {
  incoming,
  outgoing,
  missed,
  voiceMail,
  rejected,
  blocked,
  answeredExternally,
  unknown,
  wifiIncoming,
  wifiOutgoing,
}
