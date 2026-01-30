# 📞 advanced_call_log

<span style="color:#22c55e">●</span> **Android call history reader**  \
<span style="color:#3b82f6">●</span> **Built‑in runtime permission helpers**  \
<span style="color:#f59e0b">●</span> **Dual‑SIM metadata (best‑effort)**

> **Platforms:** ✅ Android  |  ❌ iOS (not supported by iOS APIs)

---

## ✨ Features

- 🔐 Permission helpers
  - `CallLog.hasPermissions()`
  - `CallLog.requestPermissions()`
- 📋 Read call logs
  - Incoming / Outgoing / Missed / Rejected / Blocked (device dependent)
- 🧾 Useful fields
  - `number`, `formattedNumber`, `name`, `timestamp`, `duration`
  - `phoneAccountId`, `simDisplayName` (best‑effort)

---

## 📦 Install

```yaml
dependencies:
  advanced_call_log: ^1.0.0
```

---

## 🔧 Android setup

### 1) Add permissions

Add the following to your **app** manifest: `android/app/src/main/AndroidManifest.xml`

```xml
<uses-permission android:name="android.permission.READ_CALL_LOG" />
<uses-permission android:name="android.permission.READ_PHONE_STATE" />
```

### 2) Google Play policy (important)

Google Play restricts **Call Log** permissions. If your app does not qualify for an allowed use‑case, your submission can be rejected.

- Policy: *Use of SMS or Call Log permission groups*

---

## 🔐 Runtime permissions

Android runtime permission model applies.

```dart
final granted = await CallLog.requestPermissions();
if (!granted) {
  // show message / open settings
  return;
}
```

---

## 🚀 Usage

### Get all logs

```dart
final logs = await CallLog.get();
for (final e in logs.take(10)) {
  print('${e.callType}  ${e.number}  ${e.duration}s');
}
```

### Query logs

```dart
final logs = await CallLog.query(
  dateTimeFrom: DateTime.now().subtract(const Duration(days: 7)),
  type: CallType.missed,
);

for (final e in logs) {
  final ts = e.timestamp ?? 0;
  print('MISSED: ${e.name ?? e.number} @ ${DateTime.fromMillisecondsSinceEpoch(ts)}');
}
```

### Filter by number / name

```dart
final byNumber = await CallLog.query(number: '98765');
final byName = await CallLog.query(name: 'Ahmed');
```

---

## 🧩 Returned fields

| Field | Meaning |
|------|---------|
| `name` | Cached contact name (if available) |
| `number` | Raw dialed number |
| `formattedNumber` | Locale formatted number |
| `callType` | Incoming/Outgoing/Missed/etc |
| `timestamp` | Call start time (ms epoch) |
| `duration` | Call duration (seconds) |
| `phoneAccountId` | Phone account id (best‑effort) |
| `simDisplayName` | SIM label (best‑effort) |

---

## 🛡️ Notes / limits

- iOS does not allow reading call logs.
- Some OEMs may return `null` for SIM fields.
- If you need a runnable example app, run this inside `example/`:

```bash
flutter create .
flutter run
```

---

## 🌐 Homepage

https://nh97.co.in/

---

## 📄 License

MIT
