package com.nh97.advanced_call_log;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.os.Build;
import android.provider.CallLog;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import io.flutter.embedding.engine.plugins.FlutterPlugin;
import io.flutter.embedding.engine.plugins.activity.ActivityAware;
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding;
import io.flutter.plugin.common.BinaryMessenger;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.PluginRegistry;

@TargetApi(Build.VERSION_CODES.M)
public class AdvancedCallLogPlugin implements
    FlutterPlugin,
    ActivityAware,
    MethodChannel.MethodCallHandler,
    PluginRegistry.RequestPermissionsResultListener {

  private static final String TAG = "flutter/ADV_CALL_LOG";

  private static final String ALREADY_RUNNING = "ALREADY_RUNNING";
  private static final String PERMISSION_NOT_GRANTED = "PERMISSION_NOT_GRANTED";
  private static final String INTERNAL_ERROR = "INTERNAL_ERROR";
  private static final String NO_ACTIVITY = "NO_ACTIVITY";

  private static final String METHOD_GET = "get";
  private static final String METHOD_QUERY = "query";
  private static final String METHOD_HAS_PERMS = "hasPermissions";
  private static final String METHOD_REQUEST_PERMS = "requestPermissions";

  private static final String OPERATOR_LIKE = "LIKE";
  private static final String OPERATOR_GT = ">";
  private static final String OPERATOR_LT = "<";
  private static final String OPERATOR_EQUALS = "=";

  private static final int REQ_PERMS = 7001;

  private static final String[] REQUIRED_PERMISSIONS = new String[] {
      Manifest.permission.READ_CALL_LOG,
      Manifest.permission.READ_PHONE_STATE
  };

  private static final String[] CURSOR_PROJECTION = {
      CallLog.Calls.CACHED_FORMATTED_NUMBER, // 0
      CallLog.Calls.NUMBER,                 // 1
      CallLog.Calls.TYPE,                   // 2
      CallLog.Calls.DATE,                   // 3
      CallLog.Calls.DURATION,               // 4
      CallLog.Calls.CACHED_NAME,            // 5
      CallLog.Calls.CACHED_NUMBER_TYPE,     // 6
      CallLog.Calls.CACHED_NUMBER_LABEL,    // 7
      CallLog.Calls.CACHED_MATCHED_NUMBER,  // 8
      CallLog.Calls.PHONE_ACCOUNT_ID        // 9
  };

  private MethodChannel channel;

  // Pending call state (because permission requests are async)
  private MethodCall pendingCall;
  private MethodChannel.Result pendingResult;
  private boolean waitingForPermission = false;

  private ActivityPluginBinding activityPluginBinding;
  private Activity activity;
  private Context appContext;

  private void init(BinaryMessenger messenger, Context applicationContext) {
    Log.d(TAG, "init()");
    channel = new MethodChannel(messenger, "com.nh97.advanced_call_log");
    channel.setMethodCallHandler(this);
    appContext = applicationContext;
  }

  @Override
  public void onAttachedToEngine(@NonNull FlutterPluginBinding binding) {
    Log.d(TAG, "onAttachedToEngine");
    init(binding.getBinaryMessenger(), binding.getApplicationContext());
  }

  @Override
  public void onDetachedFromEngine(@NonNull FlutterPluginBinding binding) {
    Log.d(TAG, "onDetachedFromEngine");
    if (channel != null) channel.setMethodCallHandler(null);
    channel = null;
    appContext = null;
  }

  @Override
  public void onAttachedToActivity(@NonNull ActivityPluginBinding binding) {
    Log.d(TAG, "onAttachedToActivity");
    activityPluginBinding = binding;
    activity = binding.getActivity();
    binding.addRequestPermissionsResultListener(this);
  }

  @Override
  public void onDetachedFromActivityForConfigChanges() {
    Log.d(TAG, "onDetachedFromActivityForConfigChanges");
  }

  @Override
  public void onReattachedToActivityForConfigChanges(@NonNull ActivityPluginBinding binding) {
    Log.d(TAG, "onReattachedToActivityForConfigChanges");
    activityPluginBinding = binding;
    activity = binding.getActivity();
    binding.addRequestPermissionsResultListener(this);
  }

  @Override
  public void onDetachedFromActivity() {
    Log.d(TAG, "onDetachedFromActivity");
    if (activityPluginBinding != null) {
      activityPluginBinding.removeRequestPermissionsResultListener(this);
    }
    activityPluginBinding = null;
    activity = null;
  }

  @Override
  public void onMethodCall(MethodCall call, MethodChannel.Result result) {
    Log.d(TAG, "onMethodCall: " + call.method);

    if (pendingResult != null) {
      result.error(ALREADY_RUNNING, "One call is already running", null);
      return;
    }

    pendingCall = call;
    pendingResult = result;

    switch (call.method) {
      case METHOD_HAS_PERMS: {
        boolean ok = hasPermissions(REQUIRED_PERMISSIONS);
        pendingResult.success(ok);
        cleanup();
        return;
      }
      case METHOD_REQUEST_PERMS: {
        if (hasPermissions(REQUIRED_PERMISSIONS)) {
          pendingResult.success(true);
          cleanup();
        } else {
          requestPermissions();
        }
        return;
      }
      case METHOD_GET:
      case METHOD_QUERY: {
        if (hasPermissions(REQUIRED_PERMISSIONS)) {
          handleGetOrQuery();
        } else {
          requestPermissions();
        }
        return;
      }
      default: {
        pendingResult.notImplemented();
        cleanup();
      }
    }
  }

  private void requestPermissions() {
    if (activity == null) {
      pendingResult.error(
          NO_ACTIVITY,
          "Cannot request permissions without an Activity (call from foreground UI).",
          null
      );
      cleanup();
      return;
    }

    waitingForPermission = true;
    ActivityCompat.requestPermissions(activity, REQUIRED_PERMISSIONS, REQ_PERMS);
  }

  @Override
  public boolean onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
    if (requestCode != REQ_PERMS) return false;

    boolean granted = true;
    if (grantResults == null || grantResults.length == 0) {
      granted = false;
    } else {
      for (int res : grantResults) {
        if (res != PackageManager.PERMISSION_GRANTED) {
          granted = false;
          break;
        }
      }
    }

    if (!waitingForPermission) return false;
    waitingForPermission = false;

    if (!granted) {
      if (pendingResult != null) {
        pendingResult.error(PERMISSION_NOT_GRANTED, "User denied call log permissions", null);
      }
      cleanup();
      return true;
    }

    // Permissions granted:
    if (pendingCall != null && METHOD_REQUEST_PERMS.equals(pendingCall.method)) {
      pendingResult.success(true);
      cleanup();
      return true;
    }

    if (pendingCall != null && (METHOD_GET.equals(pendingCall.method) || METHOD_QUERY.equals(pendingCall.method))) {
      handleGetOrQuery();
      return true;
    }

    cleanup();
    return true;
  }

  private void handleGetOrQuery() {
    try {
      if (METHOD_GET.equals(pendingCall.method)) {
        queryLogs(null, null);
        return;
      }

      // METHOD_QUERY
      String dateFrom = pendingCall.argument("dateFrom");
      String dateTo = pendingCall.argument("dateTo");
      String durationFrom = pendingCall.argument("durationFrom");
      String durationTo = pendingCall.argument("durationTo");
      String name = pendingCall.argument("name");
      String number = pendingCall.argument("number");
      String type = pendingCall.argument("type");
      String cachedMatchedNumber = pendingCall.argument("cachedMatchedNumber");
      String phoneAccountId = pendingCall.argument("phoneAccountId");

      List<String> conditions = new ArrayList<>();
      List<String> args = new ArrayList<>();

      addPredicate(conditions, args, CallLog.Calls.DATE, OPERATOR_GT, dateFrom);
      addPredicate(conditions, args, CallLog.Calls.DATE, OPERATOR_LT, dateTo);
      addPredicate(conditions, args, CallLog.Calls.DURATION, OPERATOR_GT, durationFrom);
      addPredicate(conditions, args, CallLog.Calls.DURATION, OPERATOR_LT, durationTo);

      addPredicateLike(conditions, args, CallLog.Calls.CACHED_NAME, name);
      addPredicate(conditions, args, CallLog.Calls.TYPE, OPERATOR_EQUALS, type);

      addPredicateLike(conditions, args, CallLog.Calls.CACHED_MATCHED_NUMBER, cachedMatchedNumber);
      addPredicateLike(conditions, args, CallLog.Calls.PHONE_ACCOUNT_ID, phoneAccountId);

      if (!TextUtils.isEmpty(number)) {
        List<String> or = new ArrayList<>();
        addPredicateLike(or, args, CallLog.Calls.NUMBER, number);
        addPredicateLike(or, args, CallLog.Calls.CACHED_MATCHED_NUMBER, number);
        addPredicateLike(or, args, CallLog.Calls.PHONE_ACCOUNT_ID, number);
        conditions.add("(" + TextUtils.join(" OR ", or) + ")");
      }

      String selection = conditions.isEmpty() ? null : TextUtils.join(" AND ", conditions);
      String[] selectionArgs = args.isEmpty() ? null : args.toArray(new String[0]);

      queryLogs(selection, selectionArgs);

    } catch (Exception e) {
      pendingResult.error(INTERNAL_ERROR, e.getMessage(), null);
      cleanup();
    }
  }

  private void queryLogs(String selection, String[] selectionArgs) {
    SubscriptionManager subMgr = ContextCompat.getSystemService(appContext, SubscriptionManager.class);
    List<SubscriptionInfo> subs = null;
    if (subMgr != null) {
      try {
        subs = subMgr.getActiveSubscriptionInfoList();
      } catch (SecurityException se) {
        subs = null;
      }
    }

    try (Cursor cursor = appContext.getContentResolver().query(
        CallLog.Calls.CONTENT_URI,
        CURSOR_PROJECTION,
        selection,
        selectionArgs,
        CallLog.Calls.DATE + " DESC"
    )) {
      List<HashMap<String, Object>> entries = new ArrayList<>();

      while (cursor != null && cursor.moveToNext()) {
        HashMap<String, Object> map = new HashMap<>();
        map.put("formattedNumber", cursor.getString(0));
        map.put("number", cursor.getString(1));
        map.put("callType", cursor.getInt(2));
        map.put("timestamp", cursor.getLong(3));
        map.put("duration", cursor.getInt(4));
        map.put("name", cursor.getString(5));
        map.put("cachedNumberType", cursor.getInt(6));
        map.put("cachedNumberLabel", cursor.getString(7));
        map.put("cachedMatchedNumber", cursor.getString(8));

        String accountId = cursor.getString(9);
        map.put("phoneAccountId", accountId);
        map.put("simDisplayName", getSimDisplayName(subs, accountId));

        entries.add(map);
      }

      pendingResult.success(entries);
      cleanup();

    } catch (Exception e) {
      pendingResult.error(INTERNAL_ERROR, e.getMessage(), null);
      cleanup();
    }
  }

  private String getSimDisplayName(List<SubscriptionInfo> subscriptions, String accountId) {
    if (accountId != null && subscriptions != null) {
      for (SubscriptionInfo info : subscriptions) {
        try {
          if (Integer.toString(info.getSubscriptionId()).equals(accountId) ||
              (!TextUtils.isEmpty(info.getIccId()) && accountId.contains(info.getIccId()))) {
            return String.valueOf(info.getDisplayName());
          }
        } catch (Exception ignored) {}
      }
    }
    return null;
  }

  private boolean hasPermissions(String[] permissions) {
    if (appContext == null) return false;
    for (String perm : permissions) {
      if (ContextCompat.checkSelfPermission(appContext, perm) != PackageManager.PERMISSION_GRANTED) {
        return false;
      }
    }
    return true;
  }

  private void addPredicate(List<String> conditions, List<String> args, String field, String operator, String value) {
    if (TextUtils.isEmpty(value)) return;
    conditions.add(field + " " + operator + " ?");
    args.add(value);
  }

  private void addPredicateLike(List<String> conditions, List<String> args, String field, String value) {
    if (TextUtils.isEmpty(value)) return;
    conditions.add(field + " " + OPERATOR_LIKE + " ?");
    args.add("%" + value + "%");
  }

  private void cleanup() {
    pendingCall = null;
    pendingResult = null;
  }
}
