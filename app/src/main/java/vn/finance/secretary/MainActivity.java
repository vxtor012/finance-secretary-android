package vn.finance.secretary;

import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.os.*;
import android.text.InputType;
import android.view.*;
import android.widget.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.json.*;

public class MainActivity extends Activity {
  private Ledger ledger;
  private SecureStore store;
  private LinearLayout root, body, chatList;
  private EditText input;
  private final ExecutorService executor = Executors.newSingleThreadExecutor();
  private boolean busy = false;
  private String backupPassword = "";
  private byte[] exportBytes;

  @Override
  public void onCreate(Bundle b) {
    super.onCreate(b);
    getWindow()
        .setFlags(
            android.view.WindowManager.LayoutParams.FLAG_SECURE,
            android.view.WindowManager.LayoutParams.FLAG_SECURE);
    KeyguardManager gate = (KeyguardManager) getSystemService(KEYGUARD_SERVICE);
    if (gate.isDeviceSecure()) {
      startActivityForResult(
          gate.createConfirmDeviceCredentialIntent(
              "Mở sổ tài chính", "Xác thực bằng khóa màn hình thiết bị"),
          43);
    } else initApp();
  }

  private void initApp() {
    try {
      store = new SecureStore(this);
      ledger = new Ledger(store.load());
      Ledger.put(ledger.data, "schemaVersion", 1);
      Ledger.put(ledger.data, "model", ledger.data.optString("model", Assistant.DEFAULT_MODEL));
    } catch (Exception e) {
      new AlertDialog.Builder(this)
          .setTitle("Không mở được dữ liệu")
          .setMessage(
              "Dữ liệu mã hóa không đọc được. Ứng dụng không ghi đè sổ hiện có. Hãy giữ bản sao lưu"
                  + " và thử lại.")
          .setPositiveButton("Đóng", (d, w) -> finish())
          .show();
      return;
    }
    layout();
    showChat();
    if (getIntent().getBooleanExtra("reports", false)) reportInbox();
    ReminderReceiver.schedule(this);
    if (Build.VERSION.SDK_INT >= 33
        && checkSelfPermission("android.permission.POST_NOTIFICATIONS")
            != android.content.pm.PackageManager.PERMISSION_GRANTED)
      requestPermissions(new String[] {"android.permission.POST_NOTIFICATIONS"}, 10);
    if (!ledger.data.has("onboarded")) onboarding();
  }

  private void layout() {
    root = new LinearLayout(this);
    root.setOrientation(LinearLayout.VERTICAL);
    root.setPadding(dp(12), dp(12), dp(12), dp(8));
    root.setBackgroundColor(Color.rgb(243, 247, 245));
    setContentView(root);
    root.setOnApplyWindowInsetsListener(
        (v, insets) -> {
          root.setPadding(
              dp(12),
              insets.getSystemWindowInsetTop() + dp(12),
              dp(12),
              insets.getSystemWindowInsetBottom() + dp(8));
          return insets;
        });
    TextView title = text("THƯ KÝ TÀI CHÍNH", 21);
    title.setTextColor(Color.rgb(8, 100, 88));
    root.addView(title);
    root.addView(text("Ghi sổ bằng hội thoại • Dữ liệu trên thiết bị", 12));
    LinearLayout nav = new LinearLayout(this);
    for (String label : List.of("Chat", "Báo cáo", "Nguồn tiền", "Cài đặt")) {
      Button button =
          button(
              label,
              () -> {
                switch (label) {
                  case "Chat":
                    showChat();
                    break;
                  case "Báo cáo":
                    reportDialog();
                    break;
                  case "Nguồn tiền":
                    accounts();
                    break;
                  default:
                    settings();
                }
              });
      button.setTextSize(11);
      nav.addView(button, new LinearLayout.LayoutParams(0, dp(56), 1));
    }
    root.addView(nav);
    body = new LinearLayout(this);
    body.setOrientation(LinearLayout.VERTICAL);
    root.addView(body, new LinearLayout.LayoutParams(-1, 0, 1));
  }

  private int dp(int value) {
    return Math.round(value * getResources().getDisplayMetrics().density);
  }

  private TextView text(String value, int size) {
    TextView t = new TextView(this);
    t.setText(value);
    t.setTextSize(size);
    t.setTextColor(Color.rgb(29, 48, 43));
    t.setPadding(dp(8), dp(8), dp(8), dp(8));
    t.setTextIsSelectable(true);
    return t;
  }

  private Button button(String title, Runnable action) {
    Button b = new Button(this);
    b.setText(title);
    b.setAllCaps(false);
    b.setOnClickListener(
        v -> {
          try {
            action.run();
          } catch (Exception e) {
            error(e);
          }
        });
    return b;
  }

  private EditText field(String hint, String value) {
    EditText e = new EditText(this);
    e.setHint(hint);
    e.setText(value);
    e.setTextSize(15);
    return e;
  }

  private LinearLayout form() {
    LinearLayout f = new LinearLayout(this);
    f.setOrientation(LinearLayout.VERTICAL);
    f.setPadding(dp(12), dp(10), dp(12), dp(10));
    return f;
  }

  private void dialog(String title, LinearLayout content, String ok, Runnable action) {
    ScrollView scroll = new ScrollView(this);
    scroll.addView(content);
    AlertDialog d =
        new AlertDialog.Builder(this)
            .setTitle(title)
            .setView(scroll)
            .setNegativeButton("Đóng", null)
            .setPositiveButton(ok, null)
            .create();
    d.setOnShowListener(
        v ->
            d.getButton(-1)
                .setOnClickListener(
                    w -> {
                      try {
                        action.run();
                        d.dismiss();
                      } catch (Exception e) {
                        error(e);
                      }
                    }));
    d.show();
  }

  private void error(Exception e) {
    new AlertDialog.Builder(this)
        .setTitle("Chưa thực hiện")
        .setMessage(e.getMessage() == null ? "Vui lòng thử lại" : e.getMessage())
        .setPositiveButton("Đã hiểu", null)
        .show();
  }

  private void save() {
    try {
      store.save(ledger.data);
    } catch (Exception e) {
      try {
        ledger = new Ledger(store.load());
      } catch (Exception ignored) {
      }
      throw new IllegalStateException("Không lưu được. Thao tác đã được hủy để bảo vệ sổ.");
    }
  }

  private void mutate(Runnable action) {
    String before = ledger.data.toString();
    try {
      action.run();
      save();
    } catch (Exception e) {
      try {
        ledger = new Ledger(new JSONObject(before));
      } catch (Exception ignored) {
      }
      throw e;
    }
  }

  private void onboarding() {
    LinearLayout f = form();
    f.addView(
        text(
            "Chỉ cần API key OpenRouter để chat tự nhiên. Model mặc định: Gemma. Bạn có thể bỏ qua"
                + " và dùng Ghi nhanh offline.\n\n"
                + "Khi chat AI, nội dung tin nhắn, tên nguồn tiền, danh mục và thông tin công nợ"
                + " được gửi tới OpenRouter/model. Sổ đầy đủ không được gửi. API key được mã hóa"
                + " trên máy.",
            15));
    EditText key = field("API key OpenRouter", "");
    key.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
    f.addView(key);
    dialog(
        "Bắt đầu",
        f,
        "Lưu",
        () -> {
          mutate(
              () -> {
                Ledger.put(ledger.data, "apiKey", key.getText().toString().trim());
                Ledger.put(ledger.data, "onboarded", true);
              });
        });
  }

  private void showChat() {
    body.removeAllViews();
    ScrollView scroll = new ScrollView(this);
    chatList = form();
    scroll.addView(chatList);
    body.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
    if (ledger.array("chat").length() == 0)
      chatList.addView(
          text(
              "Chào bạn! Ví dụ: “Sáng ăn phở 45k tiền mặt”.\n"
                  + "Tôi đề xuất nghiệp vụ để bạn xác nhận.\n\n"
                  + "Offline: chi 50k cash ăn sáng\n"
                  + "Lệnh: báo cáo, hoàn tác, sửa cuối <nội dung mới>\n"
                  + "Thêm MB/MoMo/thẻ ở Nguồn tiền.",
              16));
    for (int i = 0; i < ledger.array("chat").length(); i++) {
      JSONObject m = ledger.array("chat").optJSONObject(i);
      chatList.addView(
          text(
              (m.optString("role").equals("user") ? "Bạn: " : "Trợ lý: ") + m.optString("text"),
              15));
    }
    if (ledger.data.optJSONObject("pending") != null) {
      chatList.addView(button("Xem đề xuất chờ xác nhận", () -> confirmPending()));
    }
    LinearLayout shortcuts = new LinearLayout(this);
    shortcuts.addView(button("Ghi nhanh", () -> quickEntry(null)));
    shortcuts.addView(button("Nhắc hạn", () -> rules()));
    shortcuts.addView(button("Lịch sử", () -> history()));
    body.addView(shortcuts);
    input = field("Nhắn bằng tiếng Việt…", "");
    input.setMaxLines(4);
    body.addView(input);
    Button send = button(busy ? "Đang xử lý…" : "Gửi", () -> send());
    send.setEnabled(!busy);
    body.addView(send);
    scroll.post(() -> scroll.fullScroll(View.FOCUS_DOWN));
  }

  private void chat(String role, String value) {
    ledger
        .array("chat")
        .put(Ledger.obj("role", role, "text", value, "at", java.time.Instant.now().toString()));
    while (ledger.array("chat").length() > 150) ledger.array("chat").remove(0);
  }

  private void reportInbox() {
    String raw = getSharedPreferences("notification_state", 0).getString("reportPeriods", "[]");
    try {
      JSONArray periods = new JSONArray(raw);
      if (periods.length() == 0) {
        rules();
        return;
      }
      String[] names = new String[periods.length()];
      for (int i = 0; i < periods.length(); i++) {
        JSONObject p = periods.optJSONObject(i);
        names[i] = p.optString("kind") + " · " + p.optString("start") + " → " + p.optString("end");
      }
      new AlertDialog.Builder(this)
          .setTitle("Báo cáo tự động")
          .setItems(
              names,
              (d, w) -> {
                JSONObject p = periods.optJSONObject(w);
                showReport(
                    LocalDate.parse(p.optString("start")), LocalDate.parse(p.optString("end")));
              })
          .show();
    } catch (Exception e) {
      error(e);
    }
  }

  private void send() {
    String message = input.getText().toString().trim();
    if (message.isEmpty() || busy) return;
    if (ledger.data.has("pending")) {
      error(new IllegalArgumentException("Hãy xác nhận hoặc hủy đề xuất hiện có trước."));
      confirmPending();
      return;
    }
    String query =
        java.text.Normalizer.normalize(
                message.toLowerCase(Locale.ROOT), java.text.Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "")
            .replace('đ', 'd');
    if (query.contains("ai con no") || query.contains("cong no") || query.contains("sap den han")) {
      mutate(
          () -> {
            chat("user", message);
            chat(
                "assistant",
                ledger.obligations()
                    + "\n"
                    + "Nghĩa vụ chưa tự ghi thanh toán. Kiểm tra số dư đã đối soát trước khi"
                    + " trả.");
          });
      showChat();
      return;
    }
    if (query.contains("bao cao")
        || query.contains("tieu bao nhieu")
        || query.contains("tien giam")
        || query.contains("thay doi gi")
        || query.contains("du tien")) {
      LocalDate now = LocalDate.now(), start = now.withDayOfMonth(1), end = now;
      if (query.contains("hom nay")) start = now;
      else if (query.contains("hom qua")) {
        start = now.minusDays(1);
        end = start;
      } else if (query.contains("thang truoc")) {
        end = now.withDayOfMonth(1).minusDays(1);
        start = end.withDayOfMonth(1);
      } else if (query.contains("tuan"))
        start = now.with(java.time.temporal.TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
      else if (query.contains("nam")) start = now.withDayOfYear(1);
      LocalDate from = start, until = end;
      mutate(
          () -> {
            chat("user", message);
            chat("assistant", ledger.report(from, until));
          });
      showChat();
      return;
    }
    if (message.equalsIgnoreCase("hoàn tác")) {
      undo(ledger.lastRequest());
      return;
    }
    String replace =
        message.toLowerCase(Locale.ROOT).startsWith("sửa cuối ") ? ledger.lastRequest() : "";
    String request = UUID.randomUUID().toString();
    mutate(() -> chat("user", message));
    busy = true;
    showChat();
    String snapshot = ledger.data.toString();
    executor.execute(
        () -> {
          try {
            Ledger copy = new Ledger(new JSONObject(snapshot));
            JSONObject result;
            String prompt = replace.isEmpty() ? message : message.substring(9);
            try {
              result = Assistant.propose(prompt, copy);
            } catch (Exception ai) {
              try {
                result = Assistant.offline(prompt);
                Ledger.put(result, "reply", "AI không sẵn có. " + result.optString("reply"));
              } catch (Exception offline) {
                throw new IllegalArgumentException("AI không sẵn có. " + offline.getMessage());
              }
            }
            JSONObject output = result;
            runOnUiThread(
                () -> {
                  if (isFinishing() || isDestroyed()) return;
                  busy = false;
                  try {
                    mutate(
                        () -> {
                          chat("assistant", output.optString("reply"));
                          if (output.optJSONArray("actions").length() > 0)
                            Ledger.put(
                                ledger.data,
                                "pending",
                                Ledger.obj(
                                    "requestId",
                                    request,
                                    "actions",
                                    output.optJSONArray("actions"),
                                    "replace",
                                    replace));
                        });
                    showChat();
                    if (ledger.data.has("pending")) confirmPending();
                  } catch (Exception e) {
                    error(e);
                  }
                });
          } catch (Exception e) {
            runOnUiThread(
                () -> {
                  if (isFinishing() || isDestroyed()) return;
                  busy = false;
                  mutate(() -> chat("assistant", e.getMessage()));
                  showChat();
                });
          }
        });
  }

  private String preview(JSONArray batch) {
    StringBuilder b = new StringBuilder();
    for (int i = 0; i < batch.length(); i++) {
      JSONObject a = batch.optJSONObject(i);
      if (a == null) throw new IllegalArgumentException("Đề xuất sai định dạng");
      b.append(i + 1)
          .append(". ")
          .append(Ledger.typeLabel(a.optString("type")))
          .append(" · ")
          .append(Ledger.money(a.optLong("amount")))
          .append("\nNguồn: ")
          .append(a.optString("account"))
          .append(a.optString("to").isEmpty() ? "" : " → " + a.optString("to"))
          .append("\nNgày: ")
          .append(a.optString("date", Ledger.today()))
          .append(" · ")
          .append(a.optString("category"))
          .append("\n")
          .append(a.optString("person"))
          .append(" ")
          .append(a.optString("note"))
          .append("\nPhí: ")
          .append(Ledger.money(a.optLong("fee")))
          .append("\n");
      if (!a.optString("debtId").isEmpty())
        b.append("Khoản nợ: ").append(a.optString("debtId")).append("\n");
      if (!a.optString("due").isEmpty()) b.append("Hạn: ").append(a.optString("due")).append("\n");
    }
    return b.toString();
  }

  private void confirmPending() {
    JSONObject p = ledger.data.optJSONObject("pending");
    if (p == null) return;
    JSONArray actions = p.optJSONArray("actions");
    AlertDialog d =
        new AlertDialog.Builder(this)
            .setTitle("Kiểm tra trước khi ghi")
            .setMessage(preview(actions))
            .setNegativeButton(
                "Hủy",
                (a, w) -> {
                  mutate(() -> ledger.data.remove("pending"));
                  showChat();
                })
            .setNeutralButton("Chỉnh JSON", (a, w) -> editPending())
            .setPositiveButton("Xác nhận", null)
            .create();
    d.setOnShowListener(
        v ->
            d.getButton(-1)
                .setOnClickListener(
                    w -> {
                      try {
                        mutate(
                            () -> {
                              if (!p.optString("replace").isEmpty())
                                ledger.replace(
                                    p.optString("replace"), actions, p.optString("requestId"));
                              else ledger.commit(actions, p.optString("requestId"));
                              ledger.data.remove("pending");
                              chat(
                                  "assistant",
                                  "Đã ghi "
                                      + actions.length()
                                      + " nghiệp vụ. Mã nhóm: "
                                      + p.optString("requestId"));
                            });
                        d.dismiss();
                        showChat();
                      } catch (Exception e) {
                        error(e);
                      }
                    }));
    d.show();
  }

  private void editPending() {
    JSONObject p = ledger.data.optJSONObject("pending");
    LinearLayout f = form();
    EditText json = field("Danh sách nghiệp vụ", p.optJSONArray("actions").toString());
    f.addView(json);
    dialog(
        "Chỉnh đề xuất",
        f,
        "Lưu",
        () -> {
          try {
            JSONArray a = new JSONArray(json.getText().toString());
            mutate(() -> Ledger.put(p, "actions", a));
            showChat();
          } catch (JSONException e) {
            throw new IllegalArgumentException("JSON không hợp lệ");
          }
        });
  }

  private Spinner spinner(LinearLayout f, String label, List<String> items) {
    f.addView(text(label, 13));
    Spinner s = new Spinner(this);
    s.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, items));
    f.addView(s);
    return s;
  }

  private List<String> accountNames() {
    List<String> names = new ArrayList<>();
    for (int i = 0; i < ledger.array("accounts").length(); i++)
      names.add(ledger.array("accounts").optJSONObject(i).optString("name"));
    return names;
  }

  private void quickEntry(JSONObject rule) {
    if (ledger.data.has("pending")) {
      confirmPending();
      return;
    }
    LinearLayout f = form();
    List<String> types =
        List.of(
            "expense",
            "income",
            "transfer",
            "lend",
            "borrow",
            "collect",
            "repay",
            "card_purchase",
            "card_payment",
            "card_refund");
    List<String> labels = new ArrayList<>();
    for (String t : types) labels.add(Ledger.typeLabel(t));
    Spinner type =
        spinner(
            f,
            "Nghiệp vụ: chi, thu, chuyển, cho vay, vay, thu nợ, trả nợ, mua thẻ, trả thẻ, hoàn thẻ",
            labels);
    EditText amount =
        field("Số tiền: 50k / 1tr5", rule == null ? "" : String.valueOf(rule.optLong("amount")));
    f.addView(amount);
    Spinner source = spinner(f, "Nguồn tiền", accountNames()),
        to = spinner(f, "Nguồn đích (chuyển / trả thẻ)", accountNames());
    EditText category = field("Danh mục hai cấp", "Sinh hoạt/Khác"),
        note = field("Nội dung", rule == null ? "" : rule.optString("name")),
        date = field("Ngày YYYY-MM-DD", Ledger.today()),
        fee = field("Phí chuyển khoản", "0"),
        person = field("Đối tượng vay / cho vay", ""),
        debt = field("Mã khoản nợ gốc (xem Nhắc hạn)", ""),
        due = field("Hạn trả YYYY-MM-DD (có thể trống)", "");
    for (EditText e : List.of(category, note, date, fee, person, debt, due)) f.addView(e);
    dialog(
        "Ghi nhanh offline",
        f,
        "Đề xuất",
        () -> {
          JSONObject action =
              Ledger.obj(
                  "type",
                  types.get(type.getSelectedItemPosition()),
                  "amount",
                  Assistant.amount(amount.getText().toString()),
                  "account",
                  source.getSelectedItem().toString(),
                  "to",
                  to.getSelectedItem().toString(),
                  "category",
                  category.getText().toString(),
                  "note",
                  note.getText().toString(),
                  "date",
                  date.getText().toString(),
                  "fee",
                  Assistant.amount(fee.getText().toString()),
                  "person",
                  person.getText().toString(),
                  "debtId",
                  debt.getText().toString(),
                  "due",
                  due.getText().toString());
          if (rule != null) {
            Ledger.put(action, "ruleId", rule.optString("id"));
            Ledger.put(action, "occurrence", ledger.nextDue(rule).toString());
          }
          mutate(
              () ->
                  Ledger.put(
                      ledger.data,
                      "pending",
                      Ledger.obj(
                          "requestId",
                          UUID.randomUUID().toString(),
                          "actions",
                          new JSONArray().put(action))));
          showChat();
          confirmPending();
        });
  }

  private void accounts() {
    body.removeAllViews();
    ScrollView scroll = new ScrollView(this);
    LinearLayout f = form();
    scroll.addView(f);
    body.addView(scroll);
    for (int i = 0; i < ledger.array("accounts").length(); i++) {
      JSONObject a = ledger.array("accounts").optJSONObject(i);
      String id = a.optString("id");
      f.addView(
          text(
              a.optString("name")
                  + " · "
                  + a.optString("type")
                  + "\n"
                  + (a.has("checkpoint")
                      ? "Từ mốc đối soát " + a.optString("checkpointAt")
                      : "Biến động chưa đối soát")
                  + ": "
                  + Ledger.money(ledger.balance(id)),
              16));
      if (a.optString("type").equals("card"))
        f.addView(button("Sao kê theo sổ · " + a.optString("name"), () -> cardStatement(a)));
      f.addView(
          button(
              "Đối soát " + a.optString("name"),
              () -> {
                LinearLayout form = form();
                EditText n =
                    field(
                        a.optString("type").equals("card")
                            ? "Dư nợ thẻ hiện tại"
                            : "Số dư thực tế hiện tại",
                        "");
                form.addView(n);
                dialog(
                    "Đối soát",
                    form,
                    "Xác nhận",
                    () -> {
                      mutate(() -> ledger.reconcile(id, Assistant.amount(n.getText().toString())));
                      accounts();
                    });
              }));
    }
    f.addView(
        button(
            "Thêm nguồn tiền / thẻ",
            () -> {
              LinearLayout form = form();
              EditText name = field("Tên: MB, MoMo…", ""),
                  alias = field("Bí danh, ngăn cách dấu phẩy", ""),
                  statement = field("Ngày chốt sao kê (thẻ)", "25"),
                  payment = field("Ngày hạn thanh toán (thẻ)", "5");
              form.addView(name);
              form.addView(alias);
              Spinner type =
                  spinner(
                      form, "Loại nguồn tiền", List.of("cash", "bank", "wallet", "saving", "card"));
              form.addView(statement);
              form.addView(payment);
              dialog(
                  "Thêm nguồn tiền",
                  form,
                  "Thêm",
                  () -> {
                    int sd = Integer.parseInt(statement.getText().toString()),
                        pd = Integer.parseInt(payment.getText().toString());
                    if (sd < 1 || sd > 28 || pd < 1 || pd > 28)
                      throw new IllegalArgumentException("Ngày thẻ từ 1 đến 28");
                    mutate(
                        () -> {
                          ledger.addAccount(
                              name.getText().toString(),
                              type.getSelectedItem().toString(),
                              alias.getText().toString());
                          JSONObject a = ledger.account(name.getText().toString());
                          Ledger.put(a, "statementDay", sd);
                          Ledger.put(a, "paymentDay", pd);
                        });
                    accounts();
                  });
            }));
  }

  private void cardStatement(JSONObject account) {
    JSONObject statement = ledger.statement(account.optString("id"), LocalDate.now());
    String summary =
        "Kỳ "
            + statement.optString("start")
            + " → "
            + statement.optString("closing")
            + "\nHạn trả: "
            + statement.optString("due")
            + "\nNợ đầu kỳ theo giao dịch: "
            + Ledger.money(statement.optLong("openingDebt"))
            + "\nMua bằng thẻ: "
            + Ledger.money(statement.optLong("purchases"))
            + "\nHoàn tiền: "
            + Ledger.money(statement.optLong("refunds"))
            + "\nĐã trả trong kỳ: "
            + Ledger.money(statement.optLong("payments"))
            + "\nNợ cuối kỳ theo giao dịch: "
            + Ledger.money(statement.optLong("closingDebt"))
            + "\n\n"
            + "Đây là sao kê theo sổ đã ghi, chưa phải sao kê ngân hàng. Mốc đối soát hiện tại"
            + " không được giả làm dư nợ quá khứ. Mã giao dịch nguồn: "
            + statement.optJSONArray("eventIds");
    new AlertDialog.Builder(this)
        .setTitle(account.optString("name") + " · Sao kê")
        .setMessage(summary)
        .setNegativeButton("Đóng", null)
        .setPositiveButton(
            "Lưu bản sao kê",
            (d, w) -> {
              try {
                mutate(
                    () -> {
                      ledger.array("statements").put(statement);
                      ledger
                          .array("audit")
                          .put(
                              Ledger.obj(
                                  "action",
                                  "statement",
                                  "id",
                                  statement.optString("id"),
                                  "at",
                                  java.time.Instant.now().toString()));
                    });
                Toast.makeText(this, "Đã lưu trong sổ / sao lưu", Toast.LENGTH_SHORT).show();
              } catch (Exception e) {
                error(e);
              }
            })
        .show();
  }

  private void undo(String id) {
    new AlertDialog.Builder(this)
        .setTitle("Hoàn tác cả nhóm giao dịch?")
        .setMessage(id + "\nDấu vết được giữ trong nhật ký.")
        .setNegativeButton("Đóng", null)
        .setPositiveButton(
            "Hoàn tác",
            (d, w) -> {
              try {
                mutate(
                    () -> {
                      ledger.undo(id);
                      chat("assistant", "Đã hoàn tác nhóm " + id);
                    });
                showChat();
              } catch (Exception e) {
                error(e);
              }
            })
        .show();
  }

  private void history() {
    body.removeAllViews();
    ScrollView scroll = new ScrollView(this);
    LinearLayout f = form();
    scroll.addView(f);
    body.addView(scroll);
    for (int i = ledger.array("events").length() - 1; i >= 0; i--) {
      JSONObject e = ledger.array("events").optJSONObject(i);
      f.addView(
          text(
              e.optString("date")
                  + " · "
                  + Ledger.typeLabel(e.optString("type"))
                  + " · "
                  + Ledger.money(e.optLong("amount"))
                  + "\n"
                  + e.optString("note")
                  + " · "
                  + e.optString("category")
                  + "\n"
                  + (e.optBoolean("active", true) ? "Đang hiệu lực" : "Đã hoàn tác")
                  + " · "
                  + e.optString("id"),
              14));
      if (e.optBoolean("active", true))
        f.addView(button("Hoàn tác nhóm", () -> undo(e.optString("requestId"))));
    }
    f.addView(text("Nhật ký thay đổi\n" + ledger.array("audit").toString(), 11));
  }

  private void reportDialog() {
    LinearLayout f = form();
    LocalDate now = LocalDate.now();
    Spinner period =
        spinner(
            f, "Kỳ báo cáo", List.of("Tháng này", "Hôm nay", "Tuần này", "Năm này", "Tùy chọn"));
    EditText start = field("Từ YYYY-MM-DD", now.withDayOfMonth(1).toString()),
        end = field("Đến YYYY-MM-DD", now.toString());
    f.addView(start);
    f.addView(end);
    dialog(
        "Báo cáo",
        f,
        "Xem",
        () -> {
          LocalDate s =
              switch (period.getSelectedItemPosition()) {
                case 1 -> now;
                case 2 ->
                    now.with(java.time.temporal.TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
                case 3 -> now.withDayOfYear(1);
                case 4 -> LocalDate.parse(start.getText().toString());
                default -> now.withDayOfMonth(1);
              };
          LocalDate e =
              period.getSelectedItemPosition() == 4
                  ? LocalDate.parse(end.getText().toString())
                  : now;
          if (e.isBefore(s)) throw new IllegalArgumentException("Ngày cuối trước ngày đầu");
          showReport(s, e);
        });
  }

  private void showReport(LocalDate start, LocalDate end) {
    body.removeAllViews();
    ScrollView scroll = new ScrollView(this);
    LinearLayout f = form();
    scroll.addView(f);
    body.addView(scroll);
    f.addView(text(ledger.report(start, end), 15));
    JSONObject groups = ledger.totals(start, end).optJSONObject("groups");
    long max = 1;
    for (Iterator<String> it = groups.keys(); it.hasNext(); )
      max = Math.max(max, Math.abs(groups.optLong(it.next())));
    for (Iterator<String> it = groups.keys(); it.hasNext(); ) {
      String label = it.next();
      f.addView(text(label + " · " + Ledger.money(groups.optLong(label)), 12));
      ProgressBar bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
      bar.setMax(1000);
      bar.setProgress((int) Math.min(1000, Math.abs(groups.optLong(label)) * 1000 / max));
      f.addView(bar);
    }
    f.addView(
        button(
            "Xuất báo cáo TXT",
            () ->
                exportDocument(
                    ledger.report(start, end).getBytes(java.nio.charset.StandardCharsets.UTF_8),
                    "text/plain",
                    "bao-cao-" + end + ".txt")));
  }

  private void rules() {
    body.removeAllViews();
    ScrollView scroll = new ScrollView(this);
    LinearLayout f = form();
    scroll.addView(f);
    body.addView(scroll);
    f.addView(text(ledger.obligations(), 14));
    for (int i = 0; i < ledger.array("rules").length(); i++) {
      JSONObject r = ledger.array("rules").optJSONObject(i);
      f.addView(
          text(
              r.optString("name")
                  + " · "
                  + (r.optBoolean("active", true) ? "Đang theo dõi" : "Tạm dừng"),
              15));
      if (r.optBoolean("active", true))
        f.addView(button("Xác nhận thanh toán " + r.optString("name"), () -> quickEntry(r)));
      f.addView(
          button(
              r.optBoolean("active", true) ? "Tạm dừng / hủy" : "Kích hoạt lại",
              () -> {
                mutate(
                    () -> {
                      Ledger.put(r, "active", !r.optBoolean("active", true));
                      ledger
                          .array("audit")
                          .put(
                              Ledger.obj(
                                  "action",
                                  "toggle_rule",
                                  "id",
                                  r.optString("id"),
                                  "at",
                                  java.time.Instant.now().toString()));
                    });
                rules();
              }));
    }
    f.addView(
        button(
            "Thêm nghĩa vụ định kỳ",
            () -> {
              LinearLayout form = form();
              EditText name = field("Tên nghĩa vụ", ""),
                  amount = field("Số tiền", ""),
                  next = field("Kỳ đầu YYYY-MM-DD", Ledger.today());
              form.addView(name);
              form.addView(amount);
              form.addView(next);
              Spinner period = spinner(form, "Chu kỳ", List.of("month", "week", "year"));
              CheckBox estimated = new CheckBox(this);
              estimated.setText("Số tiền ước tính");
              form.addView(estimated);
              dialog(
                  "Nghĩa vụ",
                  form,
                  "Thêm",
                  () -> {
                    LocalDate date = LocalDate.parse(next.getText().toString());
                    long n = Assistant.amount(amount.getText().toString());
                    if (n <= 0 || name.getText().toString().isBlank())
                      throw new IllegalArgumentException("Cần tên và số tiền dương");
                    mutate(
                        () ->
                            ledger
                                .array("rules")
                                .put(
                                    Ledger.obj(
                                        "id",
                                        UUID.randomUUID().toString(),
                                        "name",
                                        name.getText().toString(),
                                        "amount",
                                        n,
                                        "next",
                                        date.toString(),
                                        "day",
                                        date.getDayOfMonth(),
                                        "period",
                                        period.getSelectedItem().toString(),
                                        "estimated",
                                        estimated.isChecked(),
                                        "active",
                                        true)));
                    rules();
                  });
            }));
  }

  private void settings() {
    body.removeAllViews();
    ScrollView scroll = new ScrollView(this);
    LinearLayout f = form();
    scroll.addView(f);
    body.addView(scroll);
    EditText key = field("API key OpenRouter", ledger.data.optString("apiKey"));
    key.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
    EditText model =
        field("Model ID OpenRouter", ledger.data.optString("model", Assistant.DEFAULT_MODEL));
    f.addView(key);
    f.addView(model);
    f.addView(
        button(
            "Tải danh sách model",
            () -> {
              executor.execute(
                  () -> {
                    try {
                      List<String> models = Assistant.models();
                      runOnUiThread(
                          () ->
                              new AlertDialog.Builder(this)
                                  .setTitle("Chọn model")
                                  .setItems(
                                      models.toArray(new String[0]),
                                      (d, w) -> model.setText(models.get(w)))
                                  .show());
                    } catch (Exception e) {
                      runOnUiThread(() -> error(e));
                    }
                  });
            }));
    f.addView(
        text(
            "Gemma miễn phí có hạn mức và có thể tạm ngừng. App không tự đổi sang model tính phí.\n"
                + "Tin nhắn và ngữ cảnh tài khoản/công nợ được gửi đến OpenRouter khi dùng AI.\n"
                + "Dữ liệu lưu mã hóa trên thiết bị; không có tài khoản hay server riêng.",
            14));
    EditText hour =
        field(
            "Giờ thông báo hằng ngày (0–23)",
            String.valueOf(ledger.data.optInt("notificationHour", 20)));
    f.addView(hour);
    f.addView(
        button(
            "Lưu cài đặt",
            () -> {
              int h = Integer.parseInt(hour.getText().toString());
              if (h < 0 || h > 23) throw new IllegalArgumentException("Giờ không hợp lệ");
              mutate(
                  () -> {
                    Ledger.put(ledger.data, "apiKey", key.getText().toString().trim());
                    Ledger.put(
                        ledger.data,
                        "model",
                        model.getText().toString().trim().isEmpty()
                            ? Assistant.DEFAULT_MODEL
                            : model.getText().toString().trim());
                    Ledger.put(ledger.data, "notificationHour", h);
                    Ledger.put(ledger.data, "onboarded", true);
                  });
              ReminderReceiver.schedule(this);
              Toast.makeText(this, "Đã lưu", Toast.LENGTH_SHORT).show();
            }));
    f.addView(
        button(
            "Danh mục tùy chỉnh",
            () -> {
              LinearLayout form = form();
              EditText category = field("Nhóm/Danh mục con", "");
              form.addView(text(ledger.array("categories").toString(), 13));
              form.addView(category);
              dialog(
                  "Thêm danh mục",
                  form,
                  "Thêm",
                  () -> {
                    String c = category.getText().toString().trim();
                    if (!c.contains("/") || c.startsWith("/") || c.endsWith("/"))
                      throw new IllegalArgumentException("Dùng Nhóm/Danh mục con");
                    mutate(() -> ledger.array("categories").put(c));
                  });
            }));
    f.addView(button("Báo cáo tự động gần nhất", () -> reportInbox()));
    f.addView(button("Sao lưu mã hóa", () -> passwordDialog(false)));
    f.addView(button("Khôi phục sao lưu", () -> passwordDialog(true)));
    f.addView(
        button(
            "Xuất toàn bộ JSON (không gồm API key)",
            () -> {
              JSONObject clean;
              try {
                clean = new JSONObject(ledger.data.toString());
              } catch (Exception e) {
                throw new IllegalArgumentException(e);
              }
              clean.remove("apiKey");
              clean.remove("pending");
              new AlertDialog.Builder(this)
                  .setTitle("Xuất dữ liệu riêng tư")
                  .setMessage(
                      "JSON chứa thông tin tài chính và hội thoại ở dạng đọc được. Chọn nơi lưu bạn"
                          + " kiểm soát.")
                  .setNegativeButton("Hủy", null)
                  .setPositiveButton(
                      "Xuất",
                      (d, w) ->
                          exportDocument(
                              clean.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8),
                              "application/json",
                              "finance-export.json"))
                  .show();
            }));
    f.addView(
        text(
            "Thông báo theo múi giờ thiết bị. Android có thể trì hoãn khi tiết kiệm pin. Cuối"
                + " tuần/tháng/năm tạo báo cáo của kỳ đã kết thúc; đến hạn không tự ghi thanh toán."
                + " Bản sao lưu không gồm API key. Không đồng bộ giữa thiết bị trong phiên bản"
                + " này.",
            13));
  }

  private void passwordDialog(boolean restore) {
    LinearLayout f = form();
    EditText p = field("Mật khẩu ít nhất 10 ký tự", "");
    p.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
    f.addView(p);
    dialog(
        restore ? "Khôi phục thay thế sổ hiện tại" : "Giữ mật khẩu để mở sao lưu",
        f,
        restore ? "Chọn bản sao" : "Sao lưu",
        () -> {
          backupPassword = p.getText().toString();
          if (backupPassword.length() < 10)
            throw new IllegalArgumentException("Cần ít nhất 10 ký tự");
          if (restore) {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.setType("application/octet-stream");
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            startActivityForResult(intent, 42);
          } else {
            try {
              exportDocument(
                  SecureStore.backup(ledger.data, backupPassword),
                  "application/octet-stream",
                  "finance-" + Ledger.today() + ".fsb");
              backupPassword = "";
            } catch (Exception e) {
              throw new IllegalArgumentException(e);
            }
          }
        });
  }

  private void exportDocument(byte[] bytes, String mime, String name) {
    exportBytes = bytes;
    Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
    intent.addCategory(Intent.CATEGORY_OPENABLE);
    intent.setType(mime);
    intent.putExtra(Intent.EXTRA_TITLE, name);
    startActivityForResult(intent, 41);
  }

  @Override
  protected void onActivityResult(int request, int result, Intent data) {
    super.onActivityResult(request, result, data);
    if (request == 43) {
      if (result == RESULT_OK) initApp();
      else finish();
      return;
    }
    if (result != RESULT_OK || data == null) {
      exportBytes = null;
      backupPassword = "";
      return;
    }
    try {
      if (request == 41) {
        try (var out = getContentResolver().openOutputStream(data.getData())) {
          out.write(exportBytes);
        }
        exportBytes = null;
        Toast.makeText(this, "Đã xuất", Toast.LENGTH_SHORT).show();
      } else if (request == 42) {
        byte[] bytes;
        try (var in = getContentResolver().openInputStream(data.getData())) {
          bytes = Streams.readLimited(in, 20_000_000);
        }
        if (bytes.length > 20_000_000) throw new IllegalArgumentException("Sao lưu quá lớn");
        JSONObject restored = SecureStore.restore(bytes, backupPassword);
        backupPassword = "";
        new Ledger(restored);
        new AlertDialog.Builder(this)
            .setTitle("Thay thế dữ liệu trên máy?")
            .setMessage("Nên sao lưu sổ hiện tại trước. API key hiện tại sẽ được giữ.")
            .setNegativeButton("Hủy", null)
            .setPositiveButton(
                "Khôi phục",
                (d, w) -> {
                  try {
                    mutate(
                        () -> {
                          Ledger.put(restored, "apiKey", ledger.data.optString("apiKey"));
                          ledger = new Ledger(restored);
                        });
                    showChat();
                  } catch (Exception e) {
                    error(e);
                  }
                })
            .show();
      }
    } catch (Exception e) {
      backupPassword = "";
      error(
          new IllegalArgumentException(
              "Không thể xuất/khôi phục. Kiểm tra mật khẩu, định dạng và nơi lưu."));
    }
  }

  @Override
  protected void onDestroy() {
    executor.shutdown();
    super.onDestroy();
  }
}
