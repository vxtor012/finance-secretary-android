package vn.finance.secretary;

import android.app.*;
import android.content.*;
import android.os.*;
import android.text.InputType;
import android.view.*;
import android.widget.*;
import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.color.DynamicColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.json.*;

public class MainActivity extends AppCompatActivity {
  private Ledger ledger;
  private SecureStore store;
  private LinearLayout root, body, chatList;
  private EditText input;
  private final Handler draftHandler = new Handler(Looper.getMainLooper());
  private Runnable connectionDraftSaver;
  private MaterialToolbar toolbar;
  private BottomNavigationView navigation;
  private OnBackPressedCallback back;
  private String currentScreen = "Chat", lastFailed = "", chatDraft = "";
  private final ExecutorService executor = Executors.newSingleThreadExecutor();
  private boolean busy = false;
  private String backupPassword = "";
  private byte[] exportBytes;

  @Override
  public void onCreate(Bundle b) {
    String theme = getSharedPreferences("appearance", 0).getString("theme", "system");
    AppCompatDelegate.setDefaultNightMode(
        theme.equals("dark")
            ? AppCompatDelegate.MODE_NIGHT_YES
            : theme.equals("light")
                ? AppCompatDelegate.MODE_NIGHT_NO
                : AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
    DynamicColors.applyToActivityIfAvailable(this);
    super.onCreate(b);
    if (!BuildConfig.DEBUG)
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
      String oldModel = ledger.data.optString("model", Assistant.DEFAULT_MODEL);
      if (oldModel.equals("google/gemma-3-27b-it:free") || oldModel.isBlank()) {
        Ledger.put(ledger.data, "model", Assistant.DEFAULT_MODEL);
        store.save(ledger.data);
      } else Ledger.put(ledger.data, "model", oldModel);
    } catch (Exception e) {
      new MaterialAlertDialogBuilder(this)
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
    if (!ledger.data.has("onboarded")) onboarding();
  }

  private int color(int attr) {
    return Ui.color(this, attr);
  }

  private void layout() {
    WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
    boolean light =
        (getResources().getConfiguration().uiMode
                & android.content.res.Configuration.UI_MODE_NIGHT_MASK)
            != android.content.res.Configuration.UI_MODE_NIGHT_YES;
    WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView())
        .setAppearanceLightStatusBars(light);
    WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView())
        .setAppearanceLightNavigationBars(light);
    root = new LinearLayout(this);
    root.setOrientation(LinearLayout.VERTICAL);
    root.setBackgroundColor(color(com.google.android.material.R.attr.colorSurface));
    setContentView(root);
    toolbar = new MaterialToolbar(this);
    toolbar.setTitle("Thư ký tài chính");
    toolbar.setSubtitle("Trò chuyện để hiểu tiền của bạn");
    root.addView(toolbar, new LinearLayout.LayoutParams(-1, -2));
    body = new LinearLayout(this);
    body.setOrientation(LinearLayout.VERTICAL);
    root.addView(body, new LinearLayout.LayoutParams(-1, 0, 1));
    navigation = new BottomNavigationView(this);
    navigation.setLabelVisibilityMode(
        com.google.android.material.navigation.NavigationBarView.LABEL_VISIBILITY_LABELED);
    String[] labels = {"Chat", "Báo cáo", "Nguồn tiền", "Cài đặt"};
    int[] outline = {
      R.drawable.ic_chat_outline,
      R.drawable.ic_bar_chart_outline,
      R.drawable.ic_account_balance_wallet_outline,
      R.drawable.ic_settings_outline
    };
    int[] filled = {
      R.drawable.ic_chat_filled,
      R.drawable.ic_bar_chart_filled,
      R.drawable.ic_account_balance_wallet_filled,
      R.drawable.ic_settings_filled
    };
    for (int i = 0; i < 4; i++) {
      android.graphics.drawable.StateListDrawable icon =
          new android.graphics.drawable.StateListDrawable();
      icon.addState(new int[] {android.R.attr.state_checked}, getDrawable(filled[i]));
      icon.addState(new int[] {}, getDrawable(outline[i]));
      navigation.getMenu().add(0, i + 1, i, labels[i]).setIcon(icon);
    }
    root.addView(navigation, new LinearLayout.LayoutParams(-1, -2));
    navigation.setOnItemSelectedListener(
        item -> {
          if (connectionDraftSaver != null && currentScreen.equals("Cài đặt")) {
            draftHandler.removeCallbacks(connectionDraftSaver);
            connectionDraftSaver.run();
          }
          if (input != null && currentScreen.equals("Chat")) chatDraft = input.getText().toString();
          switch (item.getItemId()) {
            case 1:
              showChat();
              break;
            case 2:
              showReport(LocalDate.now().withDayOfMonth(1), LocalDate.now());
              break;
            case 3:
              accounts();
              break;
            default:
              settings();
          }
          return true;
        });
    ViewCompat.setOnApplyWindowInsetsListener(
        root,
        (v, insets) -> {
          androidx.core.graphics.Insets bars =
              insets.getInsets(WindowInsetsCompat.Type.systemBars());
          androidx.core.graphics.Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());
          root.setPadding(bars.left, bars.top, bars.right, Math.max(bars.bottom, ime.bottom));
          navigation.setVisibility(ime.bottom > bars.bottom ? View.GONE : View.VISIBLE);
          return WindowInsetsCompat.CONSUMED;
        });
    back =
        new OnBackPressedCallback(false) {
          public void handleOnBackPressed() {
            showChat();
          }
        };
    getOnBackPressedDispatcher().addCallback(this, back);
    ViewCompat.requestApplyInsets(root);
  }

  private void screen(String name, int destination) {
    currentScreen = name;
    if (toolbar != null) {
      toolbar.setTitle(name.equals("Chat") ? "Thư ký tài chính" : name);
      toolbar.setSubtitle(
          name.equals("Chat")
              ? (ledger.data.optString("apiKey").isBlank()
                  ? "Ghi nhanh offline"
                  : "OpenRouter · "
                      + ledger
                          .data
                          .optString("model", Assistant.DEFAULT_MODEL)
                          .replace("google/", ""))
              : "Sổ cá nhân · dữ liệu trên thiết bị");
      navigation.getMenu().findItem(destination).setChecked(true);
    }
    if (back != null) back.setEnabled(!name.equals("Chat"));
    input = null;
  }

  private int dp(int value) {
    return Math.round(value * getResources().getDisplayMetrics().density);
  }

  private TextView text(String value, int size) {
    TextView t = new TextView(this);
    t.setText(value);
    t.setTextSize(size);
    t.setTextColor(color(com.google.android.material.R.attr.colorOnSurface));
    t.setLineSpacing(dp(4), 1.05f);
    t.setPadding(dp(8), dp(8), dp(8), dp(8));
    t.setTextIsSelectable(true);
    return t;
  }

  private Button button(String title, Runnable action) {
    boolean primary =
        title.equals("Gửi")
            || title.startsWith("Lưu")
            || title.startsWith("Kiểm tra kết nối")
            || title.startsWith("Xác nhận ghi");
    Button b =
        new MaterialButton(
            this,
            null,
            primary
                ? com.google.android.material.R.attr.materialButtonStyle
                : com.google.android.material.R.attr.materialButtonOutlinedStyle);
    b.setMinHeight(dp(48));
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
    EditText e = new TextInputEditText(this);
    e.setHint(hint);
    e.setText(value);
    e.setTextSize(15);
    return e;
  }

  private LinearLayout form() {
    return new Ui.Form(this);
  }

  private void dialog(String title, LinearLayout content, String ok, Runnable action) {
    ScrollView scroll = new ScrollView(this);
    scroll.addView(content);
    AlertDialog d =
        new MaterialAlertDialogBuilder(this)
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
    new MaterialAlertDialogBuilder(this)
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
                + "Khi chat AI, tin nhắn hiện tại và tối đa 5 tin nhắn gần nhất của bạn, tên nguồn"
                + " tiền, danh mục và thông tin công nợ được gửi tới OpenRouter/model. Sổ đầy đủ"
                + " không được gửi. API key được mã hóa trên máy.",
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
                Ledger.put(
                    ledger.data, "apiKey", OpenRouter.normalizeKey(key.getText().toString()));
                Ledger.put(ledger.data, "onboarded", true);
              });
        });
  }

  private MaterialCardView card(LinearLayout parent, boolean accent) {
    MaterialCardView card = new MaterialCardView(this);
    card.setRadius(dp(20));
    card.setCardElevation(0);
    card.setStrokeWidth(accent ? 0 : dp(1));
    card.setStrokeColor(color(com.google.android.material.R.attr.colorOutlineVariant));
    card.setCardBackgroundColor(
        color(
            accent
                ? com.google.android.material.R.attr.colorPrimaryContainer
                : com.google.android.material.R.attr.colorSurfaceContainerLow));
    LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
    p.setMargins(dp(16), dp(8), dp(16), dp(8));
    parent.addView(card, p);
    return card;
  }

  private void bubble(JSONObject m) {
    boolean user = m.optString("role").equals("user");
    LinearLayout row = new LinearLayout(this);
    row.setGravity(user ? Gravity.END : Gravity.START);
    TextView t = text(m.optString("text"), 16);
    t.setTextColor(
        color(
            user
                ? com.google.android.material.R.attr.colorOnPrimaryContainer
                : com.google.android.material.R.attr.colorOnSurface));
    t.setPadding(dp(16), dp(12), dp(16), dp(12));
    android.graphics.drawable.GradientDrawable bg =
        new android.graphics.drawable.GradientDrawable();
    bg.setCornerRadii(
        new float[] {
          dp(20),
          dp(20),
          dp(20),
          dp(20),
          dp(user ? 4 : 20),
          dp(user ? 4 : 20),
          dp(user ? 20 : 4),
          dp(user ? 20 : 4)
        });
    bg.setColor(
        color(
            user
                ? com.google.android.material.R.attr.colorPrimaryContainer
                : com.google.android.material.R.attr.colorSurfaceContainerLow));
    t.setBackground(bg);
    LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-2, -2);
    p.setMargins(user ? dp(40) : 0, dp(6), user ? 0 : dp(32), dp(6));
    row.addView(t, p);
    chatList.addView(row, new LinearLayout.LayoutParams(-1, -2));
  }

  private void showChat() {
    screen("Chat", 1);
    body.removeAllViews();
    ScrollView scroll = new ScrollView(this);
    scroll.setFillViewport(true);
    chatList = new LinearLayout(this);
    chatList.setOrientation(LinearLayout.VERTICAL);
    chatList.setPadding(dp(16), dp(16), dp(16), dp(16));
    scroll.addView(chatList);
    body.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
    if (ledger.array("chat").length() == 0) {
      TextView heading = text("Hôm nay, tiền của bạn\nđi đâu?", 28);
      heading.setTypeface(null, android.graphics.Typeface.BOLD);
      chatList.addView(heading);
      chatList.addView(
          text(
              "Ghi một khoản chi, hỏi về công nợ hoặc xem báo cáo. Tôi sẽ giúp bạn sắp xếp và xác"
                  + " nhận trước khi ghi sổ.",
              16));
      chatList.addView(
          button("Thử: Ăn phở 45k tiền mặt", () -> input.setText("Ăn phở 45k tiền mặt")));
      chatList.addView(
          button(
              "Xem chi tiêu tháng này",
              () -> {
                input.setText("Tháng này tôi tiêu bao nhiêu?");
                send();
              }));
    }
    for (int i = 0; i < ledger.array("chat").length(); i++)
      bubble(ledger.array("chat").optJSONObject(i));
    if (ledger.data.has("pending"))
      chatList.addView(button("Xem đề xuất chờ xác nhận", this::confirmPending));
    if (busy) {
      TextView waiting = text("Trợ lý đang suy nghĩ…", 14);
      chatList.addView(waiting);
      ProgressBar progress = new ProgressBar(this);
      chatList.addView(progress, new LinearLayout.LayoutParams(dp(32), dp(32)));
    }
    if (!lastFailed.isEmpty() && !busy)
      chatList.addView(
          button(
              "Thử lại tin nhắn",
              () -> {
                input.setText(lastFailed);
                send();
              }));
    HorizontalScrollView actions = new HorizontalScrollView(this);
    actions.setHorizontalScrollBarEnabled(false);
    LinearLayout shortcuts = new LinearLayout(this);
    shortcuts.setPadding(dp(16), 0, dp(16), 0);
    for (String name : List.of("Ghi nhanh", "Nhắc hạn", "Lịch sử")) {
      Button action =
          button(
              name,
              () -> {
                switch (name) {
                  case "Ghi nhanh":
                    quickEntry(null);
                    break;
                  case "Nhắc hạn":
                    rules();
                    break;
                  default:
                    history();
                }
              });
      LinearLayout.LayoutParams ap = new LinearLayout.LayoutParams(-2, dp(48));
      ap.rightMargin = dp(8);
      shortcuts.addView(action, ap);
    }
    actions.addView(shortcuts);
    body.addView(actions);
    LinearLayout composer = new LinearLayout(this);
    composer.setGravity(Gravity.CENTER_VERTICAL);
    composer.setPadding(dp(16), dp(8), dp(16), dp(12));
    input = field("Nhắn bằng tiếng Việt…", chatDraft);
    input.setBackgroundTintList(
        android.content.res.ColorStateList.valueOf(
            color(com.google.android.material.R.attr.colorPrimary)));
    input.setMinHeight(dp(56));
    input.setMaxLines(4);
    input.setInputType(
        InputType.TYPE_CLASS_TEXT
            | InputType.TYPE_TEXT_FLAG_MULTI_LINE
            | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
    composer.addView(input, new LinearLayout.LayoutParams(0, -2, 1));
    Button send = button("Gửi", this::send);
    send.setEnabled(!busy);
    LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-2, dp(52));
    sp.leftMargin = dp(8);
    composer.addView(send, sp);
    body.addView(composer);
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
      new MaterialAlertDialogBuilder(this)
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
    chatDraft = "";
    lastFailed = "";
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
                Ledger.put(
                    result,
                    "reply",
                    "Chưa kết nối AI: "
                        + OpenRouter.safe(ai.getMessage(), copy.data.optString("apiKey"))
                        + "\n\n"
                        + "Đã đọc lệnh bằng chế độ offline. Kiểm tra đề xuất trước khi xác nhận.");
              } catch (Exception offline) {
                throw new IllegalArgumentException(
                    OpenRouter.safe(ai.getMessage(), copy.data.optString("apiKey")));
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
                  lastFailed = message;
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
    JSONObject pending = ledger.data.optJSONObject("pending");
    if (pending == null) return;
    JSONArray actions = pending.optJSONArray("actions");
    com.google.android.material.bottomsheet.BottomSheetDialog sheet =
        new com.google.android.material.bottomsheet.BottomSheetDialog(this);
    ScrollView scroll = new ScrollView(this);
    LinearLayout f = form();
    scroll.addView(f);
    f.addView(text("Kiểm tra trước khi ghi", 24));
    f.addView(text("Sổ và số dư chỉ thay đổi sau khi bạn xác nhận.", 14));
    for (int i = 0; i < actions.length(); i++) {
      JSONObject a = actions.optJSONObject(i);
      MaterialCardView receipt = card(f, false);
      LinearLayout content = form();
      receipt.addView(content);
      content.addView(text(Ledger.typeLabel(a.optString("type")), 14));
      TextView value = text(Ledger.money(a.optLong("amount")), 28);
      value.setTypeface(null, android.graphics.Typeface.BOLD);
      content.addView(value);
      content.addView(
          text(
              "Nguồn: "
                  + a.optString("account")
                  + (a.optString("to").isBlank() ? "" : " → " + a.optString("to"))
                  + "\n"
                  + a.optString("category")
                  + " · "
                  + a.optString("date", Ledger.today())
                  + "\n"
                  + a.optString("note")
                  + (a.optLong("fee") > 0 ? "\nPhí " + Ledger.money(a.optLong("fee")) : "")
                  + (a.optString("person").isEmpty() ? "" : "\nĐối tượng: " + a.optString("person"))
                  + (a.optString("debtId").isEmpty() ? "" : "\nKhoản nợ: " + a.optString("debtId"))
                  + (a.optString("due").isEmpty() ? "" : "\nHạn: " + a.optString("due")),
              14));
    }
    f.addView(
        button(
            "Xác nhận ghi sổ",
            () -> {
              mutate(
                  () -> {
                    if (!pending.optString("replace").isEmpty())
                      ledger.replace(
                          pending.optString("replace"), actions, pending.optString("requestId"));
                    else ledger.commit(actions, pending.optString("requestId"));
                    ledger.data.remove("pending");
                    chat(
                        "assistant",
                        "Đã ghi "
                            + actions.length()
                            + " nghiệp vụ. Bạn có thể xem và hoàn tác ở Lịch sử.");
                  });
              sheet.dismiss();
              showChat();
            }));
    f.addView(
        button(
            "Chỉnh đề xuất",
            () -> {
              sheet.dismiss();
              editPending();
            }));
    f.addView(
        button(
            "Hủy đề xuất",
            () -> {
              mutate(() -> ledger.data.remove("pending"));
              sheet.dismiss();
              showChat();
            }));
    sheet.setContentView(scroll);
    sheet.show();
    sheet.getBehavior().setPeekHeight((int) (getResources().getDisplayMetrics().heightPixels * .8));
  }

  private void editPending() {
    JSONObject pending = ledger.data.optJSONObject("pending");
    if (pending == null) return;
    JSONArray actions = pending.optJSONArray("actions");
    String[] names = new String[actions.length()];
    for (int i = 0; i < names.length; i++) {
      JSONObject a = actions.optJSONObject(i);
      names[i] =
          (i + 1)
              + ". "
              + Ledger.typeLabel(a.optString("type"))
              + " · "
              + Ledger.money(a.optLong("amount"));
    }
    new MaterialAlertDialogBuilder(this)
        .setTitle("Chọn khoản cần chỉnh")
        .setItems(
            names,
            (d, index) -> {
              JSONObject action = actions.optJSONObject(index);
              LinearLayout f = form();
              EditText amount = field("Số tiền VND", String.valueOf(action.optLong("amount"))),
                  category = field("Danh mục", action.optString("category", "Sinh hoạt/Khác")),
                  note = field("Nội dung", action.optString("note")),
                  date = field("Ngày YYYY-MM-DD", action.optString("date", Ledger.today())),
                  fee = field("Phí VND", String.valueOf(action.optLong("fee")));
              f.addView(amount);
              Spinner source = spinner(f, "Nguồn tiền", accountNames());
              try {
                source.setSelection(
                    accountNames()
                        .indexOf(ledger.account(action.optString("account")).optString("name")));
              } catch (Exception ignored) {
              }
              Spinner to = spinner(f, "Đích (chuyển / trả thẻ)", accountNames());
              try {
                to.setSelection(
                    accountNames()
                        .indexOf(ledger.account(action.optString("to")).optString("name")));
              } catch (Exception ignored) {
              }
              f.addView(category);
              f.addView(note);
              f.addView(date);
              f.addView(fee);
              dialog(
                  "Chỉnh nghiệp vụ",
                  f,
                  "Lưu đề xuất",
                  () -> {
                    long n = Assistant.amount(amount.getText().toString()),
                        charge = Assistant.amount(fee.getText().toString());
                    mutate(
                        () -> {
                          Ledger.put(action, "amount", n);
                          Ledger.put(action, "fee", charge);
                          Ledger.put(action, "account", source.getSelectedItem().toString());
                          Ledger.put(action, "to", to.getSelectedItem().toString());
                          Ledger.put(action, "category", category.getText().toString());
                          Ledger.put(action, "note", note.getText().toString());
                          Ledger.put(action, "date", date.getText().toString());
                        });
                    showChat();
                    confirmPending();
                  });
            })
        .show();
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
    screen("Nguồn tiền", 3);
    body.removeAllViews();
    ScrollView scroll = new ScrollView(this);
    LinearLayout f = form();
    scroll.addView(f);
    body.addView(scroll);
    for (int i = 0; i < ledger.array("accounts").length(); i++) {
      JSONObject a = ledger.array("accounts").optJSONObject(i);
      String id = a.optString("id");
      MaterialCardView accountCard = card(f, false);
      LinearLayout accountContent = form();
      accountCard.addView(accountContent);
      accountContent.addView(text(a.optString("name"), 20));
      accountContent.addView(
          text(
              a.optString("type").equals("card")
                  ? "Dư nợ thẻ theo sổ"
                  : a.has("checkpoint") ? "Số dư từ mốc đối soát" : "Biến động · chưa đối soát",
              14));
      TextView value = text(Ledger.money(ledger.balance(id)), 28);
      value.setTypeface(null, android.graphics.Typeface.BOLD);
      accountContent.addView(value);
      if (a.optString("type").equals("card"))
        accountContent.addView(
            button("Sao kê theo sổ · " + a.optString("name"), () -> cardStatement(a)));
      accountContent.addView(
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
    new MaterialAlertDialogBuilder(this)
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
    new MaterialAlertDialogBuilder(this)
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
    screen("Lịch sử", 3);
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
    screen("Báo cáo", 2);
    body.removeAllViews();
    ScrollView scroll = new ScrollView(this);
    LinearLayout f = form();
    scroll.addView(f);
    body.addView(scroll);
    f.addView(button("Chọn kỳ báo cáo", this::reportDialog));
    JSONObject totals = ledger.totals(start, end);
    MaterialCardView summary = card(f, true);
    LinearLayout content = form();
    summary.addView(content);
    content.addView(text("Chi tiêu trong kỳ", 14));
    TextView spending = text(Ledger.money(totals.optLong("expense")), 30);
    spending.setTypeface(null, android.graphics.Typeface.BOLD);
    content.addView(spending);
    content.addView(
        text(
            "Thu nhập " + Ledger.money(totals.optLong("income")) + " · " + start + " → " + end,
            14));
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
    screen("Nhắc hạn", 3);
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
    screen("Cài đặt", 4);
    body.removeAllViews();
    ScrollView scroll = new ScrollView(this);
    LinearLayout f = form();
    scroll.addView(f);
    body.addView(scroll);
    EditText key = field("API key OpenRouter", ledger.data.optString("apiKey"));
    key.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
    EditText model =
        field("Model ID OpenRouter", ledger.data.optString("model", Assistant.DEFAULT_MODEL));
    f.addView(text("Kết nối AI", 24));
    f.addView(text("Key được mã hóa trên máy. Thay đổi key và model được lưu tự động.", 14));
    f.addView(key);
    f.addView(model);
    TextView status = text("", 14);
    f.addView(status);
    connectionDraftSaver =
        () -> {
          try {
            String normalized = OpenRouter.normalizeKey(key.getText().toString());
            String selected = model.getText().toString().trim();
            if (selected.isEmpty()) selected = Assistant.DEFAULT_MODEL;
            String chosen = selected;
            mutate(
                () -> {
                  Ledger.put(ledger.data, "apiKey", normalized);
                  Ledger.put(ledger.data, "model", chosen);
                  Ledger.put(ledger.data, "onboarded", true);
                });
            status.setText(
                normalized.isEmpty()
                    ? "Đã lưu · dùng offline"
                    : "Đã lưu key và model · chưa kiểm tra kết nối");
            key.setError(null);
          } catch (Exception e) {
            key.setError(e.getMessage());
            status.setText(e.getMessage());
          }
        };
    android.text.TextWatcher watcher =
        new android.text.TextWatcher() {
          public void beforeTextChanged(CharSequence a, int b, int c, int d) {}

          public void onTextChanged(CharSequence a, int b, int c, int d) {}

          public void afterTextChanged(android.text.Editable e) {
            status.setText("Đang lưu…");
            draftHandler.removeCallbacks(connectionDraftSaver);
            draftHandler.postDelayed(connectionDraftSaver, 500);
          }
        };
    key.addTextChangedListener(watcher);
    model.addTextChangedListener(watcher);
    Button check = button("Kiểm tra kết nối", () -> {});
    check.setOnClickListener(
        v -> {
          draftHandler.removeCallbacks(connectionDraftSaver);
          connectionDraftSaver.run();
          String enteredKey = key.getText().toString(),
              selectedModel = model.getText().toString().trim();
          if (selectedModel.isEmpty()) selectedModel = Assistant.DEFAULT_MODEL;
          String chosen = selectedModel;
          check.setEnabled(false);
          status.setText("Đang kiểm tra key và gọi model…");
          executor.execute(
              () -> {
                try {
                  String result = Assistant.checkConnection(enteredKey, chosen);
                  runOnUiThread(
                      () -> {
                        if (isDestroyed()) return;
                        check.setEnabled(true);
                        status.setText(result);
                        status.setTextColor(color(com.google.android.material.R.attr.colorPrimary));
                      });
                } catch (Exception e) {
                  runOnUiThread(
                      () -> {
                        if (isDestroyed()) return;
                        check.setEnabled(true);
                        status.setText(OpenRouter.safe(e.getMessage(), enteredKey));
                        status.setTextColor(color(com.google.android.material.R.attr.colorError));
                      });
                }
              });
        });
    f.addView(check);
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
                              new MaterialAlertDialogBuilder(this)
                                  .setTitle("Chọn model")
                                  .setItems(
                                      models.toArray(new String[0]),
                                      (d, w) -> {
                                        model.setText(models.get(w));
                                        if (connectionDraftSaver != null) {
                                          draftHandler.removeCallbacks(connectionDraftSaver);
                                          connectionDraftSaver.run();
                                        }
                                      })
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
            "Bật thông báo nhắc hạn",
            () -> {
              if (Build.VERSION.SDK_INT >= 33
                  && checkSelfPermission("android.permission.POST_NOTIFICATIONS")
                      != android.content.pm.PackageManager.PERMISSION_GRANTED)
                requestPermissions(new String[] {"android.permission.POST_NOTIFICATIONS"}, 10);
              else Toast.makeText(this, "Thông báo đã được cho phép", Toast.LENGTH_SHORT).show();
            }));
    f.addView(
        button(
            "Giao diện: theo hệ thống / sáng / tối",
            () ->
                new MaterialAlertDialogBuilder(this)
                    .setTitle("Giao diện")
                    .setSingleChoiceItems(
                        new String[] {"Theo hệ thống", "Sáng", "Tối"},
                        getSharedPreferences("appearance", 0)
                                .getString("theme", "system")
                                .equals("dark")
                            ? 2
                            : getSharedPreferences("appearance", 0)
                                    .getString("theme", "system")
                                    .equals("light")
                                ? 1
                                : 0,
                        (d, w) -> {
                          getSharedPreferences("appearance", 0)
                              .edit()
                              .putString("theme", new String[] {"system", "light", "dark"}[w])
                              .apply();
                          d.dismiss();
                          recreate();
                        })
                    .show()));
    f.addView(
        button(
            "Lưu cài đặt",
            () -> {
              int h = Integer.parseInt(hour.getText().toString());
              if (h < 0 || h > 23) throw new IllegalArgumentException("Giờ không hợp lệ");
              mutate(
                  () -> {
                    Ledger.put(
                        ledger.data, "apiKey", OpenRouter.normalizeKey(key.getText().toString()));
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
              new MaterialAlertDialogBuilder(this)
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
        new MaterialAlertDialogBuilder(this)
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
    if (connectionDraftSaver != null) {
      draftHandler.removeCallbacks(connectionDraftSaver);
      connectionDraftSaver.run();
    }
    executor.shutdown();
    super.onDestroy();
  }
}
