package vn.finance.secretary;

import android.app.*;
import android.content.Intent;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import org.json.*;

/** Device integration checks without a third-party instrumentation runner. */
public class SmokeInstrumentation extends Instrumentation {
  @Override
  public void onCreate(Bundle args) {
    super.onCreate(args);
    start();
  }

  private void check(boolean condition, String message) {
    if (!condition) throw new AssertionError(message);
  }

  @Override
  public void onStart() {
    Bundle result = new Bundle();
    try {
      SecureStore store = new SecureStore(getTargetContext());
      Ledger ledger = new Ledger();
      Ledger.put(ledger.data, "onboarded", true);
      Ledger.put(ledger.data, "apiKey", "test-key-never-export");
      store.save(ledger.data);
      check(store.load().optString("apiKey").equals("test-key-never-export"), "Keystore roundtrip");
      byte[] backup = SecureStore.backup(ledger.data, "test-password-123");
      JSONObject restored = SecureStore.restore(backup, "test-password-123");
      check(!restored.has("apiKey"), "Backup excludes API key");
      boolean rejected = false;
      try {
        SecureStore.restore(backup, "wrong-password");
      } catch (Exception expected) {
        rejected = true;
      }
      check(rejected, "Wrong backup password rejected");
      backup[backup.length - 1] ^= 1;
      rejected = false;
      try {
        SecureStore.restore(backup, "test-password-123");
      } catch (Exception expected) {
        rejected = true;
      }
      check(rejected, "Tampering rejected");
      Ledger.put(ledger.data, "apiKey", "");
      store.save(ledger.data);
      Intent intent = new Intent(getTargetContext(), MainActivity.class);
      intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
      Activity activity = startActivitySync(intent);
      waitForIdleSync();
      runOnMainSync(
          () ->
              check(
                  findButton(activity.getWindow().getDecorView(), "Gửi") != null,
                  "Chat screen renders"));
      runOnMainSync(
          () -> {
            EditText input = findEdit(activity.getWindow().getDecorView());
            check(input != null, "Chat input exists");
            input.setText("chi 50k cash ăn sáng");
            findButton(activity.getWindow().getDecorView(), "Gửi").performClick();
          });
      long deadline = System.currentTimeMillis() + 10000;
      while (System.currentTimeMillis() < deadline && !store.load().has("pending"))
        Thread.sleep(100);
      check(store.load().has("pending"), "Offline proposal persisted");
      check(
          store.load().optJSONArray("events").length() == 0,
          "Proposal does not write ledger before confirmation");
      runOnMainSync(activity::finish);
      Ledger saved = new Ledger(store.load());
      JSONObject pending = saved.data.optJSONObject("pending");
      saved.commit(pending.optJSONArray("actions"), pending.optString("requestId"));
      saved.data.remove("pending");
      store.save(saved.data);
      check(
          new Ledger(store.load())
                  .totals(java.time.LocalDate.now(), java.time.LocalDate.now())
                  .optLong("expense")
              == 50000,
          "Committed ledger survives encrypted reload");
      Activity relaunched = startActivitySync(intent);
      waitForIdleSync();
      runOnMainSync(
          () -> {
            check(
                findButton(relaunched.getWindow().getDecorView(), "Gửi") != null, "Relaunch works");
            findButton(relaunched.getWindow().getDecorView(), "Nguồn tiền").performClick();
            check(
                findButton(relaunched.getWindow().getDecorView(), "Thêm nguồn tiền / thẻ") != null,
                "Account screen renders");
            findButton(relaunched.getWindow().getDecorView(), "Cài đặt").performClick();
            check(
                findButton(relaunched.getWindow().getDecorView(), "Sao lưu mã hóa") != null,
                "Settings renders");
            relaunched.finish();
          });
      result.putString(
          "stream",
          "PASS: startup, chat proposal, confirmation boundary, encrypted persistence, backup,"
              + " wrong password, tamper rejection, navigation, relaunch");
      finish(Activity.RESULT_OK, result);
    } catch (Throwable failure) {
      result.putString("stream", "FAIL: " + failure);
      finish(Activity.RESULT_CANCELED, result);
    }
  }

  private Button findButton(View v, String label) {
    if (v instanceof Button && ((Button) v).getText().toString().equals(label)) return (Button) v;
    if (v instanceof ViewGroup) {
      ViewGroup group = (ViewGroup) v;
      for (int i = 0; i < group.getChildCount(); i++) {
        Button b = findButton(group.getChildAt(i), label);
        if (b != null) return b;
      }
    }
    return null;
  }

  private EditText findEdit(View v) {
    if (v instanceof EditText) return (EditText) v;
    if (v instanceof ViewGroup) {
      ViewGroup group = (ViewGroup) v;
      for (int i = 0; i < group.getChildCount(); i++) {
        EditText e = findEdit(group.getChildAt(i));
        if (e != null) return e;
      }
    }
    return null;
  }
}
