package vn.finance.secretary;

import android.app.*;
import android.content.*;
import android.os.Build;
import java.time.*;
import org.json.*;

public class ReminderReceiver extends BroadcastReceiver {
  public static void schedule(Context context) {
    int hour = 20;
    try {
      hour = new SecureStore(context).load().optInt("notificationHour", 20);
    } catch (Exception ignored) {
    }
    ZonedDateTime now = ZonedDateTime.now(),
        next = now.withHour(hour).withMinute(0).withSecond(0).withNano(0);
    if (!next.isAfter(now)) next = next.plusDays(1);
    AlarmManager alarms = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
    PendingIntent pi =
        PendingIntent.getBroadcast(
            context,
            0,
            new Intent(context, ReminderReceiver.class),
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.toInstant().toEpochMilli(), pi);
  }

  @Override
  public void onReceive(Context context, Intent intent) {
    if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
      schedule(context);
      return;
    }
    PendingResult pending = goAsync();
    new Thread(
            () -> {
              try {
                SecureStore store = new SecureStore(context);
                Ledger ledger = new Ledger(store.load());
                LocalDate now = LocalDate.now();
                StringBuilder message = new StringBuilder();
                String obligations = ledger.obligations();
                boolean due = false;
                for (int i = 0; i < ledger.array("rules").length(); i++) {
                  JSONObject r = ledger.array("rules").optJSONObject(i);
                  if (r.optBoolean("active", true) && !ledger.nextDue(r).isAfter(now.plusDays(3)))
                    due = true;
                }
                for (int i = 0; i < ledger.array("events").length(); i++) {
                  JSONObject e = ledger.array("events").optJSONObject(i);
                  if (e.optBoolean("active", true)
                      && java.util.List.of("lend", "borrow").contains(e.optString("type"))
                      && !e.optString("due").isEmpty()
                      && ledger.debt(e.optString("id")) > 0
                      && !LocalDate.parse(e.optString("due")).isAfter(now.plusDays(3))) due = true;
                }
                for (int i = 0; i < ledger.array("accounts").length(); i++) {
                  JSONObject a = ledger.array("accounts").optJSONObject(i);
                  if (a.optString("type").equals("card") && ledger.balance(a.optString("id")) > 0) {
                    int pay = a.optInt("paymentDay", 5);
                    LocalDate deadline = now.withDayOfMonth(pay);
                    if (deadline.isBefore(now)) deadline = deadline.plusMonths(1);
                    if (!deadline.isAfter(now.plusDays(3))) due = true;
                  }
                }
                // Receiver writes separate inbox; the activity remains the sole writer of the
                // encrypted ledger.
                org.json.JSONArray reports = new org.json.JSONArray();
                android.content.SharedPreferences prefs =
                    context.getSharedPreferences("notification_state", 0);
                LocalDate last =
                    LocalDate.parse(prefs.getString("checked", now.minusDays(1).toString()));
                if (!now.with(java.time.temporal.TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                    .equals(
                        last.with(
                            java.time.temporal.TemporalAdjusters.previousOrSame(
                                DayOfWeek.MONDAY)))) {
                  LocalDate end =
                      now.with(
                              java.time.temporal.TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                          .minusDays(1);
                  reports.put(
                      Ledger.obj(
                          "kind",
                          "week",
                          "start",
                          end.minusDays(6).toString(),
                          "end",
                          end.toString(),
                          "text",
                          ledger.report(end.minusDays(6), end)));
                  message.append("Báo cáo tuần đã sẵn sàng. ");
                }
                if (now.getMonthValue() != last.getMonthValue()
                    || now.getYear() != last.getYear()) {
                  LocalDate end = now.withDayOfMonth(1).minusDays(1);
                  reports.put(
                      Ledger.obj(
                          "kind",
                          "month",
                          "start",
                          end.withDayOfMonth(1).toString(),
                          "end",
                          end.toString(),
                          "text",
                          ledger.report(end.withDayOfMonth(1), end)));
                  message.append("Báo cáo tháng đã sẵn sàng. ");
                }
                if (now.getYear() != last.getYear()) {
                  LocalDate end = LocalDate.of(now.getYear() - 1, 12, 31);
                  reports.put(
                      Ledger.obj(
                          "kind",
                          "year",
                          "start",
                          end.withDayOfYear(1).toString(),
                          "end",
                          end.toString(),
                          "text",
                          ledger.report(end.withDayOfYear(1), end)));
                  message.append("Báo cáo năm đã sẵn sàng. ");
                }
                // Store report periods only, no financial contents outside the encrypted file.
                if (reports.length() > 0) {
                  JSONArray periods = new JSONArray();
                  for (int i = 0; i < reports.length(); i++) {
                    JSONObject r = reports.optJSONObject(i);
                    r.remove("text");
                    periods.put(r);
                  }
                  prefs.edit().putString("reportPeriods", periods.toString()).apply();
                }
                prefs.edit().putString("checked", now.toString()).apply();
                if (due)
                  message.append(
                      "Có nghĩa vụ gần đến hạn hoặc quá hạn. Hãy kiểm tra và xác nhận thanh toán.");
                if (message.length() > 0) {
                  NotificationManager nm =
                      (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
                  nm.createNotificationChannel(
                      new NotificationChannel(
                          "finance",
                          "Báo cáo và nhắc hạn",
                          NotificationManager.IMPORTANCE_DEFAULT));
                  Intent open = new Intent(context, MainActivity.class);
                  open.putExtra("reports", true);
                  PendingIntent pi =
                      PendingIntent.getActivity(
                          context,
                          1,
                          open,
                          PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
                  Notification n =
                      new Notification.Builder(context, "finance")
                          .setSmallIcon(android.R.drawable.ic_dialog_info)
                          .setContentTitle("Thư ký tài chính")
                          .setContentText(message.toString())
                          .setStyle(new Notification.BigTextStyle().bigText(message.toString()))
                          .setContentIntent(pi)
                          .setAutoCancel(true)
                          .build();
                  if (Build.VERSION.SDK_INT < 33
                      || context.checkSelfPermission("android.permission.POST_NOTIFICATIONS")
                          == android.content.pm.PackageManager.PERMISSION_GRANTED) nm.notify(1, n);
                }
              } catch (Exception ignored) {
                /* No financial data or secrets in logs. */
              } finally {
                schedule(context);
                pending.finish();
              }
            })
        .start();
  }
}
