package vn.finance.secretary;

import android.app.*;
import android.content.Intent;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.textfield.TextInputLayout;
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
      long deadline = System.currentTimeMillis() + 30000;
      while (System.currentTimeMillis() < deadline && !store.load().has("pending"))
        Thread.sleep(100);
      check(
          store.load().has("pending"),
          "Offline proposal persisted; chat=" + store.load().optJSONArray("chat"));
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
            findNavigation(relaunched.getWindow().getDecorView()).setSelectedItemId(3);
            check(
                findButton(relaunched.getWindow().getDecorView(), "Thêm nguồn tiền / thẻ") != null,
                "Account screen renders");
            findNavigation(relaunched.getWindow().getDecorView()).setSelectedItemId(4);
            check(
                findButton(relaunched.getWindow().getDecorView(), "Sao lưu mã hóa") != null,
                "Settings renders");
            EditText key = findLabeled(relaunched.getWindow().getDecorView(), "API key OpenRouter"),
                model = findLabeled(relaunched.getWindow().getDecorView(), "Model ID OpenRouter");
            check(key != null && model != null, "Labeled AI fields");
            key.setText("Bearer sk-or-v1-instrumentation-fixture");
            model.setText("google/gemma-4-31b-it:free");
            findNavigation(relaunched.getWindow().getDecorView()).setSelectedItemId(1);
          });
      JSONObject preferences = store.load();
      check(
          preferences.optString("model").equals("google/gemma-4-31b-it:free"),
          "Model auto-saved before leaving settings");
      check(
          preferences.optString("apiKey").equals("sk-or-v1-instrumentation-fixture"),
          "Pasted key normalized and auto-saved");
      Assistant.transport =
          (path, key, body) -> {
            check(key.equals("sk-or-v1-instrumentation-fixture"), "Persisted key used");
            check(
                body.optString("model").equals("google/gemma-4-31b-it:free"),
                "Selected model used");
            return Ledger.obj(
                "choices",
                new JSONArray()
                    .put(
                        Ledger.obj(
                            "finish_reason",
                            "stop",
                            "message",
                            Ledger.obj(
                                "content",
                                "{\"reply\":\"Chào bạn, tôi sẵn sàng giúp ghi"
                                    + " sổ.\",\"actions\":[]}"))));
          };
      runOnMainSync(
          () -> {
            findEdit(relaunched.getWindow().getDecorView()).setText("Xin chào");
            findButton(relaunched.getWindow().getDecorView(), "Gửi").performClick();
          });
      deadline = System.currentTimeMillis() + 30000;
      while (System.currentTimeMillis() < deadline
          && !store.load().optJSONArray("chat").toString().contains("sẵn sàng giúp"))
        Thread.sleep(100);
      check(
          store.load().optJSONArray("chat").toString().contains("sẵn sàng giúp"),
          "AI conversation displayed");
      Assistant.transport =
          (path, key, body) ->
              OpenRouter.decode(
                  403,
                  "{\"error\":{\"code\":403,\"message\":\"Privacy routing restriction\"}}",
                  key);
      waitForIdleSync();
      runOnMainSync(
          () -> {
            findEdit(relaunched.getWindow().getDecorView()).setText("Bạn giúp tôi nhé");
            findButton(relaunched.getWindow().getDecorView(), "Gửi").performClick();
          });
      deadline = System.currentTimeMillis() + 30000;
      while (System.currentTimeMillis() < deadline
          && !store.load().optJSONArray("chat").toString().contains("HTTP 403")) Thread.sleep(100);
      check(
          store.load().optJSONArray("chat").toString().contains("HTTP 403"),
          "Original API error visible instead of offline help");
      check(
          !store.load().optJSONArray("chat").toString().contains("AI không sẵn có. Offline:"),
          "Generic failure removed");
      check(store.load().optJSONArray("events").length() == 1, "API failure never changes ledger");
      runOnMainSync(relaunched::finish);
      Assistant.transport = OpenRouter::request;
      result.putString(
          "stream",
          "PASS: startup, chat proposal, confirmation boundary, encrypted persistence, backup,"
              + " wrong password, tamper rejection, navigation, relaunch, pasted key, saved model,"
              + " AI success, original API error");
      finish(Activity.RESULT_OK, result);
    } catch (Throwable failure) {
      java.io.StringWriter trace = new java.io.StringWriter();
      failure.printStackTrace(new java.io.PrintWriter(trace));
      result.putString("stream", "FAIL: " + trace);
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

  private BottomNavigationView findNavigation(View view) {
    if (view instanceof BottomNavigationView) return (BottomNavigationView) view;
    if (view instanceof ViewGroup) {
      ViewGroup group = (ViewGroup) view;
      for (int i = 0; i < group.getChildCount(); i++) {
        BottomNavigationView result = findNavigation(group.getChildAt(i));
        if (result != null) return result;
      }
    }
    return null;
  }

  private EditText findLabeled(View view, String label) {
    if (view instanceof TextInputLayout && label.contentEquals(((TextInputLayout) view).getHint()))
      return ((TextInputLayout) view).getEditText();
    if (view instanceof ViewGroup) {
      ViewGroup group = (ViewGroup) view;
      for (int i = 0; i < group.getChildCount(); i++) {
        EditText result = findLabeled(group.getChildAt(i), label);
        if (result != null) return result;
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
