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
      check(
          getTargetContext()
              .getApplicationInfo()
              .loadLabel(getTargetContext().getPackageManager())
              .toString()
              .equals("Thư ký tài chính"),
          "Installed application label is valid Vietnamese");
      concurrentReadDuringWrite();
      SecureStore store = new SecureStore(getTargetContext());
      Ledger ledger = new Ledger();
      Ledger.put(ledger.data, "onboarded", true);
      Ledger.put(ledger.data, "aiProvider", "google");
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
                findButton(relaunched.getWindow().getDecorView(), "Thêm nguồn tiền") != null,
                "Account screen renders");
            findButton(relaunched.getWindow().getDecorView(), "Giao dịch").performClick();
            check(
                findNavigation(relaunched.getWindow().getDecorView()).getSelectedItemId() == 3,
                "Transactions belongs to Book");
            findButton(relaunched.getWindow().getDecorView(), "Nhắc hạn").performClick();
            check(
                findNavigation(relaunched.getWindow().getDecorView()).getSelectedItemId() == 3,
                "Reminders belongs to Book");
            findButton(relaunched.getWindow().getDecorView(), "Nguồn tiền").performClick();
            check(
                findButton(relaunched.getWindow().getDecorView(), "Thêm nguồn tiền") != null,
                "Book sections are mutually discoverable");
            findNavigation(relaunched.getWindow().getDecorView()).setSelectedItemId(4);
            check(
                findButton(relaunched.getWindow().getDecorView(), "Giới thiệu  ›") != null,
                "Settings renders");
            check(
                findEdit(relaunched.getWindow().getDecorView()) == null,
                "Settings overview has no configuration fields");
            check(
                (relaunched.getWindow().getAttributes().flags
                        & WindowManager.LayoutParams.FLAG_SECURE)
                    == 0,
                "Screen capture is enabled");
            findButton(relaunched.getWindow().getDecorView(), "Giới thiệu  ›").performClick();
            check(
                findText(
                    relaunched.getWindow().getDecorView(), "Phiên bản " + BuildConfig.VERSION_NAME),
                "About shows installed version");
            ((MainActivity) relaunched).getOnBackPressedDispatcher().onBackPressed();
            findButton(relaunched.getWindow().getDecorView(), "Dữ liệu  ›").performClick();
            check(
                findButton(relaunched.getWindow().getDecorView(), "Sao lưu mã hóa  ›") != null,
                "Data settings exposes encrypted backup");
            ((MainActivity) relaunched).getOnBackPressedDispatcher().onBackPressed();
            findButton(relaunched.getWindow().getDecorView(), "Kết nối AI  ›").performClick();
            EditText key = findLabeled(relaunched.getWindow().getDecorView(), "Key AI Studio"),
                model = findLabeled(relaunched.getWindow().getDecorView(), "Model");
            check(key != null && model != null, "Labeled AI fields");
            key.setText("Bearer AIza-instrumentation-fixture");
            model.setText("gemma-4-31b-it");
            findNavigation(relaunched.getWindow().getDecorView()).setSelectedItemId(1);
          });
      JSONObject preferences = store.load();
      check(
          preferences.optString("model").equals("gemma-4-31b-it"),
          "Model auto-saved before leaving settings");
      check(
          preferences.optString("apiKey").equals("AIza-instrumentation-fixture"),
          "Pasted key normalized and auto-saved");
      Assistant.transport =
          (path, key, body) -> {
            check(key.equals("AIza-instrumentation-fixture"), "Persisted key used");
            check(path.equals("models/gemma-4-31b-it:generateContent"), "Selected model used");
            return Ledger.obj(
                "candidates",
                new JSONArray()
                    .put(
                        Ledger.obj(
                            "finishReason",
                            "STOP",
                            "content",
                            Ledger.obj(
                                "parts",
                                new JSONArray()
                                    .put(
                                        Ledger.obj(
                                            "text",
                                            "{\"reply\":\"Chào bạn, tôi sẵn sàng giúp ghi"
                                                + " sổ.\",\"actions\":[]}"))))));
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
              GoogleAi.decode(
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
      waitForIdleSync();
      check(
          store.load().optJSONArray("chat").length() <= ChatHistory.LIMIT,
          "Bounded chat persisted");
      JSONObject oldState = store.load();
      String eventsBefore = oldState.optJSONArray("events").toString();
      oldState.remove("aiProvider");
      Ledger.put(oldState, "apiKey", "sk-or-old-fixture");
      Ledger.put(oldState, "model", "google/gemma-4-31b-it:free");
      Ledger.put(
          oldState,
          "pending",
          Ledger.obj(
              "requestId",
              "upgrade-proposal",
              "actions",
              new JSONArray()
                  .put(
                      Ledger.obj(
                          "type",
                          "income",
                          "amount",
                          20000,
                          "account",
                          "cash",
                          "date",
                          Ledger.today()))));
      for (int i = 0; i < 12; i++)
        oldState.optJSONArray("chat").put(Ledger.obj("role", "user", "text", "old " + i));
      store.save(oldState);
      Activity upgraded = startActivitySync(intent);
      waitForIdleSync();
      JSONObject migrated = store.load();
      check(
          migrated.optString("aiProvider").equals("google") && !migrated.has("apiKey"),
          "Old provider key retired");
      check(
          migrated.optJSONArray("events").toString().equals(eventsBefore),
          "Upgrade retains ledger");
      check(
          migrated.optJSONObject("pending").optString("requestId").equals("upgrade-proposal"),
          "Upgrade retains proposal");
      check(migrated.optJSONArray("chat").length() == 5, "Upgrade trims only chat");
      runOnMainSync(upgraded::finish);
      Assistant.transport = GoogleAi::request;
      compactScreens();
      result.putString(
          "stream",
          "PASS: startup, chat proposal, confirmation boundary, encrypted persistence, backup,"
              + " wrong password, tamper rejection, navigation, relaunch, pasted key, saved model,"
              + " AI success, original API error, provider migration, bounded chat, retained ledger"
              + " and proposal, settings hierarchy, About, screen capture, Book navigation,"
              + " serialized ledger read/write, compact history, paging, filters, reminder"
              + " deletion, theme back icon, API Back, single Close, operation-specific quick"
              + " entry");
      finish(Activity.RESULT_OK, result);
    } catch (Throwable failure) {
      java.io.StringWriter trace = new java.io.StringWriter();
      failure.printStackTrace(new java.io.PrintWriter(trace));
      result.putString("stream", "FAIL: " + trace);
      finish(Activity.RESULT_CANCELED, result);
    }
  }

  private void compactScreens() throws Exception {
    SecureStore store = new SecureStore(getTargetContext());
    Ledger fixture = new Ledger();
    Ledger.put(fixture.data, "onboarded", true);
    Ledger.put(fixture.data, "aiProvider", "google");
    for (int day = 0; day < 4; day++) {
      int count = day == 0 ? 35 : 2;
      for (int i = 0; i < count; i++) {
        String note = "Khoản " + day + "-" + i;
        fixture.commit(
            new JSONArray()
                .put(
                    Ledger.obj(
                        "type",
                        "expense",
                        "amount",
                        45000,
                        "account",
                        "cash",
                        "category",
                        "Ăn uống/Ăn ngoài",
                        "note",
                        note,
                        "date",
                        java.time.LocalDate.now().minusDays(day).toString())),
            "compact-" + day + "-" + i);
      }
    }
    fixture
        .array("rules")
        .put(
            Ledger.obj(
                "id",
                "compact-rent",
                "name",
                "Thuê nhà kiểm thử",
                "amount",
                2000000,
                "next",
                Ledger.today(),
                "period",
                "month",
                "active",
                true));
    store.save(fixture.data);
    Intent intent =
        new Intent(getTargetContext(), MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    Activity activity = startActivitySync(intent);
    waitForIdleSync();
    runOnMainSync(
        () -> {
          View decor = activity.getWindow().getDecorView();
          findNavigation(decor).setSelectedItemId(3);
          findButton(decor, "Giao dịch").performClick();
          check(findText(decor, "Trang 1/3\n41 giao dịch"), "Bounded first page");
          check(!findButton(decor, "Trước").isEnabled(), "First page disables previous");
          check(findDescription(decor, "Khoản 0-34,") != null, "Newest transaction rendered");
          check(
              findDescription(decor, "Khoản 0-0,") == null,
              "Busy-day overflow not rendered on first page");
          findButton(decor, "Sau").performClick();
          check(findText(decor, "Trang 2/3\n41 giao dịch"), "Next page updates position");
          check(findDescription(decor, "Khoản 0-0,") != null, "Busy-day continuation rendered");
          findButton(decor, "Sau").performClick();
          check(!findButton(decor, "Sau").isEnabled(), "Last page disables next");
          findButton(decor, "Bộ lọc").performClick();
        });
    waitForIdleSync();
    clickDialog("Tất cả");
    clickDialog("Hôm nay");
    clickDialog("Áp dụng");
    runOnMainSync(
        () -> {
          check(
              findText(activity.getWindow().getDecorView(), "Trang 1/2\n35 giao dịch"),
              "Today filter changes page count and resets page");
          findButton(activity.getWindow().getDecorView(), "Bộ lọc").performClick();
        });
    waitForIdleSync();
    clickDialog("Xóa bộ lọc");
    clickDialog("Áp dụng");
    runOnMainSync(
        () -> {
          check(
              findText(activity.getWindow().getDecorView(), "Trang 1/3\n41 giao dịch"),
              "Clearing filters restores all dates");
          findButton(activity.getWindow().getDecorView(), "Nhắc hạn").performClick();
          View row = findDescription(activity.getWindow().getDecorView(), "Thuê nhà kiểm thử,");
          check(row != null, "Compact reminder row");
          row.performClick();
        });
    waitForIdleSync();
    clickDialog("Xóa nhắc hạn");
    waitForIdleSync();
    clickDialog("Hủy");
    check(
        !store.load().optJSONArray("rules").optJSONObject(0).optBoolean("deleted"),
        "Cancel keeps reminder");
    runOnMainSync(
        () ->
            findDescription(activity.getWindow().getDecorView(), "Thuê nhà kiểm thử,")
                .performClick());
    waitForIdleSync();
    clickDialog("Xóa nhắc hạn");
    waitForIdleSync();
    clickDialog("Xóa");
    waitForIdleSync();
    check(
        store.load().optJSONArray("rules").optJSONObject(0).optBoolean("deleted"),
        "Delete persisted");
    check(store.load().optJSONArray("events").length() == 41, "Delete keeps transactions");
    runOnMainSync(
        () -> {
          check(
              findDescription(activity.getWindow().getDecorView(), "Thuê nhà kiểm thử,") == null,
              "Deleted reminder hidden");
          activity.finish();
        });
    waitForIdleSync();
    for (String theme : new String[] {"dark", "light"}) {
      getTargetContext()
          .getSharedPreferences("appearance", 0)
          .edit()
          .putString("theme", theme)
          .commit();
      Activity themed = startActivitySync(intent);
      waitForIdleSync();
      runOnMainSync(
          () -> {
            findNavigation(themed.getWindow().getDecorView()).setSelectedItemId(4);
            findButton(themed.getWindow().getDecorView(), "Giới thiệu  ›").performClick();
            com.google.android.material.appbar.MaterialToolbar toolbar =
                findToolbar(themed.getWindow().getDecorView());
            check(
                toolbar.getNavigationIconTint() != null
                    && toolbar.getNavigationIconTint()
                        == Ui.color(themed, com.google.android.material.R.attr.colorOnSurface),
                "Back icon uses theme contrast in " + theme);
            check(toolbar.getNavigationIcon() != null, "Back icon present");
            ((MainActivity) themed).getOnBackPressedDispatcher().onBackPressed();
            check(
                findButton(themed.getWindow().getDecorView(), "Giới thiệu  ›") != null,
                "Back returns to settings");
          });
      waitForIdleSync();
      quickEntryScreens(themed, theme.equals("light"));
      runOnMainSync(themed::finish);
      waitForIdleSync();
    }
    getTargetContext()
        .getSharedPreferences("appearance", 0)
        .edit()
        .putString("theme", "system")
        .commit();
  }

  private void quickEntryScreens(Activity activity, boolean fullFlow) {
    runOnMainSync(
        () -> {
          View decor = activity.getWindow().getDecorView();
          findButton(decor, "Kết nối AI  ›").performClick();
          check(findToolbar(decor).getNavigationIcon() != null, "API settings exposes Back");
          check(findToolbar(decor).getTitle().toString().equals("Kết nối AI"), "API page title");
          findDescription(decor, "Quay lại Cài đặt").performClick();
          check(findButton(decor, "Dữ liệu  ›") != null, "API Back returns to settings");
          findButton(decor, "Dữ liệu  ›").performClick();
          findButton(decor, "Danh mục  ›").performClick();
        });
    waitForIdleSync();
    check(dialogTextCount("Đóng") == 1, "Category dialog has one Close button");
    clickDialog("Đóng");
    runOnMainSync(
        () -> {
          findNavigation(activity.getWindow().getDecorView()).setSelectedItemId(1);
          check(
              findToolbar(activity.getWindow().getDecorView()).getTitle().toString().equals("Chat"),
              "Chat title matches tab");
          if (fullFlow) findButton(activity.getWindow().getDecorView(), "Thêm").performClick();
        });
    if (!fullFlow) return;
    waitForIdleSync();
    clickDialog("Ghi nhanh");
    clickDialog("Chi tiêu");
    String current = "Chi tiêu";
    String[] operations = {
      "Chi tiêu",
      "Thu nhập",
      "Chuyển nội bộ",
      "Cho vay",
      "Đi vay",
      "Thu hồi nợ",
      "Trả nợ gốc",
      "Chi bằng thẻ",
      "Thanh toán thẻ",
      "Hoàn tiền thẻ",
      "Chi tiêu"
    };
    for (int index = 0; index < operations.length; index++) {
      String selected = operations[index];
      if (index > 0) {
        clickDialog(current);
        clickDialog(selected);
      }
      waitForIdleSync();
      boolean category =
          selected.equals("Chi tiêu")
              || selected.equals("Thu nhập")
              || selected.equals("Chi bằng thẻ")
              || selected.equals("Hoàn tiền thẻ");
      check((dialogTextCount("Danh mục") > 0) == category, "Category visibility: " + selected);
      check(
          (dialogTextCount("Chuyển đến") > 0) == selected.equals("Chuyển nội bộ"),
          "Destination visibility: " + selected);
      check(
          (dialogTextCount("Phí chuyển khoản (tùy chọn)") > 0) == selected.equals("Chuyển nội bộ"),
          "Fee visibility: " + selected);
      check(
          (dialogTextCount("Người vay") > 0) == selected.equals("Cho vay"),
          "Borrower visibility: " + selected);
      check(
          (dialogTextCount("Người cho vay") > 0) == selected.equals("Đi vay"),
          "Lender visibility: " + selected);
      boolean settlement = selected.equals("Thu hồi nợ") || selected.equals("Trả nợ gốc");
      check(
          (dialogTextCount("Khoản nợ gốc") > 0) == settlement,
          "Debt picker visibility: " + selected);
      check(
          (dialogTextCount("Thẻ cần thanh toán") > 0) == selected.equals("Thanh toán thẻ"),
          "Card destination visibility: " + selected);
      current = selected;
    }
    clickDialog("Đóng");
  }

  private int dialogTextCount(String text) {
    android.view.accessibility.AccessibilityNodeInfo root =
        getUiAutomation().getRootInActiveWindow();
    if (root == null) return 0;
    return countNodeText(root, text);
  }

  private int countNodeText(android.view.accessibility.AccessibilityNodeInfo node, String text) {
    int count =
        text.contentEquals(node.getText() == null ? "" : node.getText())
                || text.contentEquals(node.getHintText() == null ? "" : node.getHintText())
            ? 1
            : 0;
    for (int i = 0; i < node.getChildCount(); i++) {
      android.view.accessibility.AccessibilityNodeInfo child = node.getChild(i);
      if (child != null) count += countNodeText(child, text);
    }
    return count;
  }

  private void clickDialog(String label) {
    long deadline = System.currentTimeMillis() + 5000;
    while (System.currentTimeMillis() < deadline) {
      android.view.accessibility.AccessibilityNodeInfo root =
          getUiAutomation().getRootInActiveWindow();
      if (root != null) {
        java.util.List<android.view.accessibility.AccessibilityNodeInfo> nodes =
            root.findAccessibilityNodeInfosByText(label);
        for (android.view.accessibility.AccessibilityNodeInfo node : nodes) {
          if (!label.contentEquals(node.getText())) continue;
          android.view.accessibility.AccessibilityNodeInfo target = node;
          while (target != null && !target.isClickable()) target = target.getParent();
          if (target != null
              && target.performAction(
                  android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK)) {
            waitForIdleSync();
            return;
          }
        }
      }
      android.os.SystemClock.sleep(100);
    }
    throw new AssertionError("Dialog action not found: " + label);
  }

  private View findDescription(View view, String prefix) {
    if (view.getContentDescription() != null
        && view.getContentDescription().toString().startsWith(prefix)) return view;
    if (view instanceof ViewGroup)
      for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) {
        View result = findDescription(((ViewGroup) view).getChildAt(i), prefix);
        if (result != null) return result;
      }
    return null;
  }

  private com.google.android.material.appbar.MaterialToolbar findToolbar(View view) {
    if (view instanceof com.google.android.material.appbar.MaterialToolbar)
      return (com.google.android.material.appbar.MaterialToolbar) view;
    if (view instanceof ViewGroup)
      for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) {
        com.google.android.material.appbar.MaterialToolbar result =
            findToolbar(((ViewGroup) view).getChildAt(i));
        if (result != null) return result;
      }
    return null;
  }

  private void concurrentReadDuringWrite() throws Exception {
    SecureStore reader = new SecureStore(getTargetContext());
    reader.save(Ledger.obj("revision", 0));
    java.util.concurrent.CountDownLatch writeOpened = new java.util.concurrent.CountDownLatch(1),
        releaseWrite = new java.util.concurrent.CountDownLatch(1),
        readerStarted = new java.util.concurrent.CountDownLatch(1);
    android.util.AtomicFile pausedFile =
        new android.util.AtomicFile(
            new java.io.File(getTargetContext().getFilesDir(), "ledger.enc")) {
          @Override
          public java.io.FileOutputStream startWrite() throws java.io.IOException {
            java.io.FileOutputStream stream = super.startWrite();
            writeOpened.countDown();
            try {
              if (!releaseWrite.await(10, java.util.concurrent.TimeUnit.SECONDS))
                throw new java.io.IOException("Writer was not released");
            } catch (InterruptedException e) {
              Thread.currentThread().interrupt();
              throw new java.io.IOException(e);
            }
            return stream;
          }
        };
    SecureStore writer = new SecureStore(getTargetContext(), pausedFile);
    java.util.concurrent.ExecutorService workers =
        java.util.concurrent.Executors.newFixedThreadPool(2);
    java.util.concurrent.Future<?> writing = null;
    java.util.concurrent.Future<JSONObject> reading = null;
    try {
      writing =
          workers.submit(
              () -> {
                writer.save(Ledger.obj("revision", 1));
                return null;
              });
      check(
          writeOpened.await(10, java.util.concurrent.TimeUnit.SECONDS),
          "Writer opened temporary ledger");
      reading =
          workers.submit(
              () -> {
                readerStarted.countDown();
                return reader.load();
              });
      check(
          readerStarted.await(10, java.util.concurrent.TimeUnit.SECONDS),
          "Concurrent reader started");
      try {
        reading.get(300, java.util.concurrent.TimeUnit.MILLISECONDS);
        throw new AssertionError("Reader accessed ledger while atomic write was open");
      } catch (java.util.concurrent.TimeoutException expected) {
      }
      releaseWrite.countDown();
      writing.get(10, java.util.concurrent.TimeUnit.SECONDS);
      check(
          reading.get(10, java.util.concurrent.TimeUnit.SECONDS).optInt("revision") == 1,
          "Reader sees completed write");
      check(
          new SecureStore(getTargetContext()).load().optInt("revision") == 1,
          "Concurrent read cannot discard saved ledger");
    } finally {
      releaseWrite.countDown();
      workers.shutdownNow();
    }
  }

  private boolean findText(View view, String label) {
    if (view instanceof TextView && ((TextView) view).getText().toString().equals(label))
      return true;
    if (view instanceof ViewGroup)
      for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++)
        if (findText(((ViewGroup) view).getChildAt(i), label)) return true;
    return false;
  }

  private Button findButton(View v, String label) {
    if (v instanceof Button
        && (((Button) v).getText().toString().equals(label)
            || label.contentEquals(
                v.getContentDescription() == null ? "" : v.getContentDescription())))
      return (Button) v;
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
