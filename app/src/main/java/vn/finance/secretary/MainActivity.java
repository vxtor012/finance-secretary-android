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
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.json.*;

public class MainActivity extends AppCompatActivity {
  private Ledger ledger;
  private SecureStore store;
  private LinearLayout root, body, chatList, bookTabs;
  private EditText input;
  private final Handler draftHandler = new Handler(Looper.getMainLooper());
  private Runnable connectionDraftSaver;
  private MaterialToolbar toolbar;
  private BottomNavigationView navigation;
  private OnBackPressedCallback back;
  private String currentScreen = "Chat", lastFailed = "", chatDraft = "";
  private final ExecutorService executor = Executors.newSingleThreadExecutor();
  private boolean busy = false, settingsDetail = false;
  private String backupPassword = "";
  private byte[] exportBytes;
  private int historyPage = 0;
  private LocalDate historyStart, historyEnd;
  private String historyAccount = "", historyType = "", historyPeriod = "Tất cả";
  private boolean historyIncludeUndone = true;

  @Override
  public void onCreate(Bundle b) {
    String theme = getSharedPreferences("appearance", 0).getString("theme", "system");
    AppCompatDelegate.setDefaultNightMode(
        theme.equals("dark")
            ? AppCompatDelegate.MODE_NIGHT_YES
            : theme.equals("light")
                ? AppCompatDelegate.MODE_NIGHT_NO
                : AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);

    super.onCreate(b);
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
      Assistant.migrateProvider(ledger);
      store.save(ledger.data);
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
    bookTabs = new LinearLayout(this);
    bookTabs.setPadding(dp(8), 0, dp(8), dp(8));
    bookTabs.setVisibility(View.GONE);
    root.addView(bookTabs, new LinearLayout.LayoutParams(-1, -2));
    body = new LinearLayout(this);
    body.setOrientation(LinearLayout.VERTICAL);
    LinearLayout.LayoutParams bodySize =
        new LinearLayout.LayoutParams(
            Math.min(getResources().getDisplayMetrics().widthPixels, dp(640)), 0, 1);
    bodySize.gravity = Gravity.CENTER_HORIZONTAL;
    root.addView(body, bodySize);
    navigation = new BottomNavigationView(this);
    navigation.setBackgroundColor(color(com.google.android.material.R.attr.colorSurface));
    navigation.setLabelVisibilityMode(
        com.google.android.material.navigation.NavigationBarView.LABEL_VISIBILITY_LABELED);
    String[] labels = {"Chat", "Báo cáo", "Sổ", "Cài đặt"};
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
    int[] order = {0, 2, 1, 3};
    for (int position = 0; position < 4; position++) {
      int i = order[position];
      android.graphics.drawable.StateListDrawable icon =
          new android.graphics.drawable.StateListDrawable();
      icon.addState(new int[] {android.R.attr.state_checked}, getDrawable(filled[i]));
      icon.addState(new int[] {}, getDrawable(outline[i]));
      navigation.getMenu().add(0, i + 1, position, labels[i]).setIcon(icon);
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
            if (settingsDetail) {
              settings();
              return;
            }
            showChat();
          }
        };
    getOnBackPressedDispatcher().addCallback(this, back);
    ViewCompat.requestApplyInsets(root);
  }

  private void screen(String name, int destination) {
    currentScreen = name;
    settingsDetail = false;
    if (toolbar != null) {
      toolbar.setTitle(destination == 3 ? "Sổ" : name);
      toolbar.setSubtitle(null);
      toolbar.setVisibility(destination == 3 && shortViewport() ? View.GONE : View.VISIBLE);
      toolbar.setNavigationIcon(null);
      navigation.getMenu().findItem(destination).setChecked(true);
    }
    bookNavigation(destination == 3, name);
    if (back != null) back.setEnabled(!name.equals("Chat"));
    input = null;
  }

  private void bookNavigation(boolean visible, String current) {
    if (bookTabs == null) return;
    bookTabs.setVisibility(visible ? View.VISIBLE : View.GONE);
    if (!visible) return;
    bookTabs.removeAllViews();
    String[] labels = {"Nguồn tiền", "Giao dịch", "Nhắc hạn"};
    Runnable[] actions = {this::accounts, this::history, this::rules};
    for (int i = 0; i < labels.length; i++) {
      MaterialButton tab = (MaterialButton) button(labels[i], actions[i]);
      tab.setTextSize(13);
      tab.setGravity(Gravity.CENTER);
      tab.setPadding(dp(4), 0, dp(4), 0);
      boolean selected = current.equals(labels[i]);
      tab.setSelected(selected);
      tab.setContentDescription(labels[i] + (selected ? ", đang chọn" : ""));
      if (selected)
        tab.setBackgroundTintList(
            android.content.res.ColorStateList.valueOf(
                color(com.google.android.material.R.attr.colorPrimaryContainer)));
      LinearLayout.LayoutParams size = new LinearLayout.LayoutParams(0, -2, 1);
      size.setMargins(dp(2), 0, dp(2), 0);
      bookTabs.addView(tab, size);
    }
  }

  private int dp(int value) {
    return Math.round(value * getResources().getDisplayMetrics().density);
  }

  private TextView text(String value, int size) {
    TextView t = new TextView(this);
    t.setText(value);
    t.setTextSize(size);
    t.setTextColor(color(com.google.android.material.R.attr.colorOnSurface));
    t.setLineSpacing(dp(3), 1.05f);
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
                : com.google.android.material.R.attr.borderlessButtonStyle);
    b.setMinHeight(dp(48));
    if (!primary) {
      b.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
      b.setPadding(dp(16), 0, dp(16), 0);
    }
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

  private MaterialButton iconButton(String label, int icon, Runnable action) {
    MaterialButton b =
        new MaterialButton(this, null, com.google.android.material.R.attr.borderlessButtonStyle);
    b.setContentDescription(label);
    b.setIconResource(icon);
    b.setIconSize(dp(22));
    b.setIconPadding(0);
    b.setIconGravity(MaterialButton.ICON_GRAVITY_TEXT_START);
    b.setMinWidth(0);
    b.setMinimumWidth(0);
    b.setPadding(dp(13), 0, dp(13), 0);
    b.setInsetTop(0);
    b.setInsetBottom(0);
    b.setCornerRadius(dp(24));
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

  private Button centeredButton(String title, Runnable action) {
    MaterialButton result = (MaterialButton) button(title, action);
    result.setGravity(Gravity.CENTER);
    result.setPadding(dp(12), 0, dp(12), 0);
    result.setInsetTop(0);
    result.setInsetBottom(0);
    result.setMinHeight(dp(48));
    return result;
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
    MaterialAlertDialogBuilder builder =
        new MaterialAlertDialogBuilder(this)
            .setTitle(title)
            .setView(scroll)
            .setPositiveButton(ok, null);
    if (!ok.equals("Đóng")) builder.setNegativeButton("Đóng", null);
    AlertDialog d = builder.create();
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
    f.addView(text("Dán key Google AI Studio để bắt đầu. Bạn có thể bỏ qua và ghi offline.", 16));
    f.addView(
        button(
            "Dữ liệu gửi tới Google",
            () ->
                new MaterialAlertDialogBuilder(this)
                    .setMessage(
                        "Google nhận tối đa 5 tin nhắn gần nhất và ngữ cảnh tài khoản/công nợ. Sổ"
                            + " đầy đủ không được gửi; key được mã hóa trên máy.")
                    .setPositiveButton("Đóng", null)
                    .show()));
    settingsDetail = true;
    toolbar.setTitle("Kết nối AI");
    settingsBack();
    EditText key = field("Key AI Studio", "");
    key.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
    key.setTypeface(
        android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.NORMAL));
    f.addView(key);
    dialog(
        "Bắt đầu",
        f,
        "Lưu",
        () -> {
          mutate(
              () -> {
                Ledger.put(ledger.data, "apiKey", GoogleAi.normalizeKey(key.getText().toString()));
                Ledger.put(ledger.data, "onboarded", true);
              });
        });
  }

  private MaterialCardView card(LinearLayout parent, boolean accent) {
    MaterialCardView card = new MaterialCardView(this);
    card.setRadius(dp(16));
    card.setCardElevation(0);
    card.setStrokeWidth(0);
    card.setStrokeColor(color(com.google.android.material.R.attr.colorOutlineVariant));
    card.setCardBackgroundColor(
        color(
            accent
                ? com.google.android.material.R.attr.colorSurfaceContainerHigh
                : com.google.android.material.R.attr.colorSurface));
    LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
    p.setMargins(0, dp(8), 0, dp(8));
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
                ? com.google.android.material.R.attr.colorOnSurface
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
                ? com.google.android.material.R.attr.colorSurfaceContainerHigh
                : com.google.android.material.R.attr.colorSurface));
    if (user) t.setBackground(bg);
    LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-2, -2);
    p.setMargins(user ? dp(40) : 0, dp(6), user ? 0 : dp(32), dp(6));
    row.addView(t, p);
    chatList.addView(row, new LinearLayout.LayoutParams(-1, -2));
  }

  private void showChat() {
    if (currentScreen.equals("Cài đặt") && connectionDraftSaver != null) {
      draftHandler.removeCallbacks(connectionDraftSaver);
      connectionDraftSaver.run();
    }
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
      TextView heading = text("Ghi lại hôm nay.", 30);
      heading.setTypeface(
          android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL));
      LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(-1, -2);
      hp.topMargin = dp(56);
      chatList.addView(heading, hp);
      TextView example = text("Ăn sáng 45k tiền mặt", 16);
      example.setTextColor(color(com.google.android.material.R.attr.colorOnSurfaceVariant));
      chatList.addView(example);
      if (ledger.data.optString("apiKey").isBlank())
        chatList.addView(button("Kết nối AI Studio", this::settings));
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
    LinearLayout outer = new LinearLayout(this);
    outer.setPadding(dp(16), dp(8), dp(16), dp(12));
    LinearLayout composer = new LinearLayout(this);
    composer.setGravity(Gravity.CENTER_VERTICAL);
    composer.setPadding(dp(4), dp(4), dp(4), dp(4));
    android.graphics.drawable.GradientDrawable box =
        new android.graphics.drawable.GradientDrawable();
    box.setCornerRadius(dp(28));
    box.setColor(color(com.google.android.material.R.attr.colorSurfaceContainerLow));
    composer.setBackground(box);
    MaterialButton more =
        iconButton(
            "Thêm",
            R.drawable.ic_add,
            () -> {
              PopupMenu menu = new PopupMenu(this, outer);
              String[] labels = {"Ghi nhanh", "Xóa hội thoại"};
              for (String label : labels) menu.getMenu().add(label);
              menu.setOnMenuItemClickListener(
                  item -> {
                    switch (item.getTitle().toString()) {
                      case "Ghi nhanh":
                        quickEntry(null);
                        break;
                      default:
                        mutate(() -> ChatHistory.clear(ledger));
                        lastFailed = "";
                        showChat();
                    }
                    return true;
                  });
              menu.show();
            });
    composer.addView(more, new LinearLayout.LayoutParams(dp(48), dp(48)));
    input = field("Ghi một khoản…", chatDraft);
    input.setBackground(null);
    input.setPadding(dp(8), dp(8), dp(8), dp(8));
    input.setMinHeight(dp(48));
    input.setMaxLines(4);
    input.setInputType(
        InputType.TYPE_CLASS_TEXT
            | InputType.TYPE_TEXT_FLAG_MULTI_LINE
            | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
    composer.addView(input, new LinearLayout.LayoutParams(0, -2, 1));
    MaterialButton send = iconButton("Gửi", R.drawable.ic_arrow_upward, this::send);
    send.setTag("Gửi");
    send.setEnabled(!busy);
    send.setBackgroundTintList(
        android.content.res.ColorStateList.valueOf(
            color(com.google.android.material.R.attr.colorPrimary)));
    send.setIconTint(
        android.content.res.ColorStateList.valueOf(
            color(com.google.android.material.R.attr.colorOnPrimary)));
    composer.addView(send, new LinearLayout.LayoutParams(dp(48), dp(48)));
    outer.addView(composer, new LinearLayout.LayoutParams(-1, -2));
    body.addView(outer);
    scroll.post(() -> scroll.fullScroll(View.FOCUS_DOWN));
  }

  private void chat(String role, String value) {
    ledger
        .array("chat")
        .put(Ledger.obj("role", role, "text", value, "at", java.time.Instant.now().toString()));
    ChatHistory.prune(ledger);
  }

  private void reportInbox() {
    String raw = getSharedPreferences("notification_state", 0).getString("reportPeriods", "[]");
    try {
      JSONArray periods = new JSONArray(raw);
      if (periods.length() == 0) {
        new MaterialAlertDialogBuilder(this)
            .setTitle("Báo cáo định kỳ")
            .setMessage(
                "Chưa có báo cáo định kỳ. App tạo báo cáo tuần và tháng khi kỳ kết thúc. Bạn vẫn có"
                    + " thể xem mọi kỳ ở trang Báo cáo.")
            .setPositiveButton("Đóng", null)
            .show();
        return;
      }
      String[] names = new String[periods.length()];
      for (int i = 0; i < periods.length(); i++) {
        JSONObject p = periods.optJSONObject(i);
        names[i] =
            (p.optString("kind").equals("week") ? "Tuần" : "Tháng")
                + " · "
                + p.optString("start")
                + " → "
                + p.optString("end");
      }
      new MaterialAlertDialogBuilder(this)
          .setTitle("Báo cáo định kỳ")
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
                        + GoogleAi.safe(ai.getMessage(), copy.data.optString("apiKey"))
                        + "\n\n"
                        + "Đã đọc lệnh bằng chế độ offline. Kiểm tra đề xuất trước khi xác nhận.");
              } catch (Exception offline) {
                throw new IllegalArgumentException(
                    GoogleAi.safe(ai.getMessage(), copy.data.optString("apiKey")));
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
              Spinner category = spinner(f, "Danh mục", Ledger.STANDARD_CATEGORIES);
              int categoryIndex = Ledger.STANDARD_CATEGORIES.indexOf(action.optString("category"));
              category.setSelection(
                  categoryIndex >= 0
                      ? categoryIndex
                      : Ledger.STANDARD_CATEGORIES.indexOf("Sinh hoạt/Khác"));
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
                          Ledger.put(action, "category", category.getSelectedItem().toString());
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
      if (!ledger.array("accounts").optJSONObject(i).optBoolean("archived"))
        names.add(ledger.array("accounts").optJSONObject(i).optString("name"));
    return names;
  }

  private void quickEntry(JSONObject rule) {
    if (ledger.data.has("pending")) {
      confirmPending();
      return;
    }
    if (rule != null) {
      quickEntryForm(rule, "expense");
      return;
    }
    String[] labels = QuickEntry.TYPES.stream().map(Ledger::typeLabel).toArray(String[]::new);
    new MaterialAlertDialogBuilder(this)
        .setTitle("Chọn loại ghi chép")
        .setItems(
            labels, (dialog, selected) -> quickEntryForm(null, QuickEntry.TYPES.get(selected)))
        .setNegativeButton("Đóng", null)
        .show();
  }

  private Spinner entryAccounts(
      LinearLayout parent,
      String label,
      boolean cards,
      Map<String, java.util.function.Supplier<String>> values,
      String key,
      String previous) {
    List<String> ids = new ArrayList<>(), names = new ArrayList<>();
    for (int i = 0; i < ledger.array("accounts").length(); i++) {
      JSONObject account = ledger.array("accounts").optJSONObject(i);
      if (account.optBoolean("archived") || account.optString("type").equals("card") != cards)
        continue;
      ids.add(account.optString("id"));
      names.add(account.optString("name"));
    }
    Spinner choice =
        spinner(parent, label, names.isEmpty() ? List.of("Chưa có nguồn phù hợp") : names);
    choice.setEnabled(!ids.isEmpty());
    choice.setSelection(Math.max(0, ids.indexOf(previous)));
    if (ids.isEmpty())
      parent.addView(
          text(
              cards
                  ? "Thêm thẻ tín dụng trong Sổ → Nguồn tiền trước khi ghi."
                  : "Cần một nguồn tiền đang sử dụng để ghi giao dịch.",
              14));
    values.put(key, () -> ids.isEmpty() ? "" : ids.get(choice.getSelectedItemPosition()));
    return choice;
  }

  private void quickEntryForm(JSONObject rule, String initialType) {
    LinearLayout content = form();
    List<String> labels =
        QuickEntry.TYPES.stream()
            .map(Ledger::typeLabel)
            .collect(java.util.stream.Collectors.toList());
    Spinner type = spinner(content, "Loại ghi chép", labels);
    type.setSelection(QuickEntry.TYPES.indexOf(initialType));
    EditText amount =
        field("Số tiền: 50k / 1tr5", rule == null ? "" : String.valueOf(rule.optLong("amount")));
    content.addView(amount);
    LinearLayout specific = new LinearLayout(this);
    specific.setOrientation(LinearLayout.VERTICAL);
    content.addView(specific);
    EditText note = field("Nội dung", rule == null ? "" : rule.optString("name"));
    EditText date = dateFilterField("Ngày giao dịch", LocalDate.now());
    content.addView(note);
    content.addView(date);
    Map<String, java.util.function.Supplier<String>> values = new HashMap<>();
    Runnable update =
        () -> {
          String previous = values.containsKey("account") ? values.get("account").get() : "";
          values.clear();
          specific.removeAllViews();
          LinearLayout fields = form();
          fields.setPadding(0, 0, 0, 0);
          specific.addView(fields, new LinearLayout.LayoutParams(-1, -2));
          String selected = QuickEntry.TYPES.get(type.getSelectedItemPosition());
          boolean cards = List.of("card_purchase", "card_refund").contains(selected);
          String sourceLabel =
              switch (selected) {
                case "income", "borrow", "collect" -> "Nhận tiền vào";
                case "transfer" -> "Chuyển từ";
                case "repay", "card_payment" -> "Thanh toán từ";
                case "card_purchase", "card_refund" -> "Thẻ tín dụng";
                default -> "Nguồn tiền";
              };
          entryAccounts(fields, sourceLabel, cards, values, "account", previous);
          if (List.of("transfer", "card_payment").contains(selected))
            entryAccounts(
                fields,
                selected.equals("card_payment") ? "Thẻ cần thanh toán" : "Chuyển đến",
                selected.equals("card_payment"),
                values,
                "to",
                "");
          if (QuickEntry.category(selected)) {
            List<String> categories =
                Ledger.STANDARD_CATEGORIES.stream()
                    .filter(c -> c.startsWith("Thu nhập/") == selected.equals("income"))
                    .collect(java.util.stream.Collectors.toList());
            Spinner category = spinner(fields, "Danh mục", categories);
            category.setSelection(
                Math.max(
                    0,
                    categories.indexOf(
                        selected.equals("income") ? "Thu nhập/Khác" : "Sinh hoạt/Khác")));
            values.put("category", () -> category.getSelectedItem().toString());
          }
          if (selected.equals("transfer")) {
            EditText fee = field("Phí chuyển khoản (tùy chọn)", "0");
            fields.addView(fee);
            values.put("fee", () -> fee.getText().toString());
          }
          if (List.of("lend", "borrow").contains(selected)) {
            EditText person = field(selected.equals("lend") ? "Người vay" : "Người cho vay", "");
            EditText due = field("Hạn trả YYYY-MM-DD (tùy chọn)", "");
            fields.addView(person);
            fields.addView(due);
            values.put("person", () -> person.getText().toString());
            values.put("due", () -> due.getText().toString());
          }
          if (List.of("collect", "repay").contains(selected)) {
            List<String> ids = new ArrayList<>(), debts = new ArrayList<>();
            for (int i = 0; i < ledger.array("events").length(); i++) {
              JSONObject event = ledger.array("events").optJSONObject(i);
              if (!event.optBoolean("active", true)
                  || !event.optString("type").equals(selected.equals("collect") ? "lend" : "borrow")
                  || ledger.debt(event.optString("id")) <= 0) continue;
              ids.add(event.optString("id"));
              debts.add(
                  event.optString("person")
                      + " · "
                      + Ledger.money(ledger.debt(event.optString("id")))
                      + " · "
                      + dateLabel(event.optString("date")));
            }
            Spinner debt =
                spinner(
                    fields,
                    "Khoản nợ gốc",
                    debts.isEmpty() ? List.of("Không có khoản nợ phù hợp") : debts);
            debt.setEnabled(!ids.isEmpty());
            if (ids.isEmpty())
              fields.addView(text("Ghi khoản cho vay/đi vay trước khi thu hoặc trả nợ.", 14));
            values.put(
                "debtId", () -> ids.isEmpty() ? "" : ids.get(debt.getSelectedItemPosition()));
          }
        };
    type.setOnItemSelectedListener(
        new AdapterView.OnItemSelectedListener() {
          public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
            update.run();
          }

          public void onNothingSelected(AdapterView<?> parent) {}
        });
    update.run();
    dialog(
        "Ghi nhanh",
        content,
        "Đề xuất",
        () -> {
          String selected = QuickEntry.TYPES.get(type.getSelectedItemPosition());
          JSONObject fields =
              Ledger.obj(
                  "amount",
                  amount.getText().toString(),
                  "note",
                  note.getText().toString(),
                  "date",
                  date.getText().toString());
          values.forEach((key, value) -> Ledger.put(fields, key, value.get()));
          if (fields.optString("account").isEmpty()
              || List.of("transfer", "card_payment").contains(selected)
                  && fields.optString("to").isEmpty())
            throw new IllegalArgumentException("Thêm nguồn tiền hoặc thẻ phù hợp trước khi ghi.");
          if (List.of("collect", "repay").contains(selected)
              && fields.optString("debtId").isEmpty())
            throw new IllegalArgumentException("Chưa có khoản nợ phù hợp để thanh toán.");
          JSONObject action = QuickEntry.action(selected, fields);
          if (rule != null) {
            Ledger.put(action, "ruleId", rule.optString("id"));
            Ledger.put(action, "occurrence", ledger.nextDue(rule).toString());
          }
          // Validate before leaving the form; confirmation remains the only real ledger write.
          try {
            Ledger trial = new Ledger(new JSONObject(ledger.data.toString()));
            trial.commit(new JSONArray().put(action), UUID.randomUUID().toString());
          } catch (JSONException invalid) {
            throw new IllegalArgumentException(
                "Không đọc được dữ liệu để kiểm tra đề xuất.", invalid);
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

  private void reconcileAccount(JSONObject account) {
    LinearLayout form = form();
    EditText amount =
        field(account.optString("type").equals("card") ? "Dư nợ thẻ" : "Số dư thực tế", "");
    form.addView(amount);
    dialog(
        "Đối soát · " + account.optString("name"),
        form,
        "Xác nhận",
        () -> {
          mutate(
              () ->
                  ledger.reconcile(
                      account.optString("id"), Assistant.amount(amount.getText().toString())));
          accounts();
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
      JSONObject account = ledger.array("accounts").optJSONObject(i);
      if (account.optBoolean("archived")) continue;
      LinearLayout row = new LinearLayout(this);
      row.setGravity(Gravity.CENTER_VERTICAL);
      row.setPadding(0, dp(12), 0, dp(12));
      LinearLayout labels = new LinearLayout(this);
      labels.setOrientation(LinearLayout.VERTICAL);
      TextView name = text(account.optString("name"), 17);
      name.setMaxLines(1);
      name.setEllipsize(android.text.TextUtils.TruncateAt.END);
      name.setTypeface(
          android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL));
      labels.addView(name);
      TextView note =
          text(
              account.optString("type").equals("card")
                  ? "Dư nợ"
                  : account.has("checkpoint") ? "Đã đối soát" : "Biến động",
              12);
      note.setTextColor(color(com.google.android.material.R.attr.colorOnSurfaceVariant));
      labels.addView(note);
      row.addView(labels, new LinearLayout.LayoutParams(0, -2, 1));
      TextView amount = text(Ledger.money(ledger.balance(account.optString("id"))), 18);
      amount.setTypeface(
          android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL));
      row.addView(amount);
      MaterialButton options =
          iconButton(
              "Tùy chọn " + account.optString("name"),
              R.drawable.ic_more_vert,
              () -> {
                PopupMenu menu = new PopupMenu(this, row);
                menu.getMenu().add("Đối soát");
                menu.getMenu().add("Xóa nguồn tiền");
                if (account.optString("type").equals("card")) menu.getMenu().add("Sao kê");
                menu.setOnMenuItemClickListener(
                    item -> {
                      if (item.getTitle().equals("Sao kê")) cardStatement(account);
                      else if (item.getTitle().equals("Xóa nguồn tiền")) removeAccount(account);
                      else reconcileAccount(account);
                      return true;
                    });
                menu.show();
              });
      row.addView(options, new LinearLayout.LayoutParams(dp(48), dp(48)));
      f.addView(row);
    }
    f.addView(button("Thêm nguồn tiền", this::addAccountDialog));
    boolean archived = false;
    for (int i = 0; i < ledger.array("accounts").length(); i++)
      archived |= ledger.array("accounts").optJSONObject(i).optBoolean("archived");
    if (archived)
      f.addView(
          button(
              "Nguồn đã ngừng sử dụng",
              () -> {
                LinearLayout list = form();
                for (int i = 0; i < ledger.array("accounts").length(); i++) {
                  JSONObject account = ledger.array("accounts").optJSONObject(i);
                  if (account.optBoolean("archived"))
                    list.addView(
                        button(
                            "Khôi phục · " + account.optString("name"),
                            () -> {
                              mutate(() -> Ledger.put(account, "archived", false));
                              accounts();
                              Toast.makeText(this, "Đã khôi phục nguồn tiền", Toast.LENGTH_SHORT)
                                  .show();
                            }));
                }
                dialog("Nguồn đã ngừng sử dụng", list, "Đóng", () -> {});
              }));
  }

  private void removeAccount(JSONObject account) {
    boolean used = ledger.accountHasHistory(account.optString("id"));
    new MaterialAlertDialogBuilder(this)
        .setTitle(used ? "Ngừng sử dụng nguồn tiền?" : "Xóa nguồn tiền?")
        .setMessage(
            used
                ? "Nguồn này đã có dữ liệu. Lịch sử và số dư vẫn được giữ trong báo cáo; nguồn sẽ"
                    + " ẩn khỏi danh sách ghi mới. Bạn có thể khôi phục sau."
                : "Xóa " + account.optString("name") + " khỏi sổ. Nguồn này chưa có dữ liệu.")
        .setNegativeButton("Hủy", null)
        .setPositiveButton(
            used ? "Ngừng sử dụng" : "Xóa",
            (d, w) -> {
              try {
                mutate(() -> ledger.removeAccount(account.optString("id")));
                accounts();
              } catch (Exception e) {
                error(e);
              }
            })
        .show();
  }

  private void addAccountDialog() {
    LinearLayout f = form();
    EditText name = field("Tên nguồn tiền", ""),
        alias = field("Tên gọi khác khi chat (tùy chọn)", "");
    f.addView(name);
    f.addView(alias);
    f.addView(text("Ví dụ: vcb, bank — các tên cùng chỉ một nguồn tiền.", 13));
    Spinner type =
        spinner(
            f,
            "Loại nguồn tiền",
            List.of("Tiền mặt", "Ngân hàng", "Ví điện tử", "Tiết kiệm", "Thẻ tín dụng"));
    LinearLayout dates = form();
    EditText statement = field("Ngày chốt sao kê", "25"),
        payment = field("Ngày đến hạn thanh toán", "5");
    statement.setInputType(InputType.TYPE_CLASS_NUMBER);
    payment.setInputType(InputType.TYPE_CLASS_NUMBER);
    dates.addView(statement);
    dates.addView(payment);
    f.addView(dates);
    dates.setVisibility(View.GONE);
    type.setOnItemSelectedListener(
        new AdapterView.OnItemSelectedListener() {
          public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
            dates.setVisibility(position == 4 ? View.VISIBLE : View.GONE);
          }

          public void onNothingSelected(AdapterView<?> parent) {}
        });
    dialog(
        "Thêm nguồn tiền",
        f,
        "Thêm",
        () -> {
          boolean card = type.getSelectedItemPosition() == 4;
          int sd = card ? Integer.parseInt(statement.getText().toString()) : 0;
          int pd = card ? Integer.parseInt(payment.getText().toString()) : 0;
          if (card && (sd < 1 || sd > 28 || pd < 1 || pd > 28))
            throw new IllegalArgumentException("Ngày thẻ từ 1 đến 28");
          mutate(
              () -> {
                ledger.addAccount(
                    name.getText().toString(),
                    new String[] {"cash", "bank", "wallet", "saving", "card"}
                        [type.getSelectedItemPosition()],
                    alias.getText().toString());
                if (card) {
                  JSONObject account = ledger.account(name.getText().toString());
                  Ledger.put(account, "statementDay", sd);
                  Ledger.put(account, "paymentDay", pd);
                }
              });
          accounts();
        });
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
    boolean fromHistory = currentScreen.equals("Giao dịch");
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
                if (fromHistory) history();
                else showChat();
              } catch (Exception e) {
                error(e);
              }
            })
        .show();
  }

  private TextView compactText(String value, int size, boolean secondary) {
    TextView view = text(value, size);
    view.setPadding(0, 0, 0, 0);
    view.setTextIsSelectable(false);
    if (secondary)
      view.setTextColor(color(com.google.android.material.R.attr.colorOnSurfaceVariant));
    view.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
    return view;
  }

  private void listRow(
      LinearLayout parent, String title, String subtitle, String amount, Runnable action) {
    LinearLayout row = new LinearLayout(this);
    boolean stacked = getResources().getConfiguration().fontScale > 1.3f;
    row.setOrientation(stacked ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL);
    row.setGravity(Gravity.CENTER_VERTICAL);
    row.setPadding(dp(8), dp(12), dp(8), dp(12));
    row.setMinimumHeight(dp(72));
    LinearLayout labels = new LinearLayout(this);
    labels.setOrientation(LinearLayout.VERTICAL);
    TextView heading = compactText(title, 16, false);
    heading.setTypeface(null, android.graphics.Typeface.BOLD);
    heading.setMaxLines(1);
    heading.setEllipsize(android.text.TextUtils.TruncateAt.END);
    TextView supporting = compactText(subtitle, 13, true);
    supporting.setMaxLines(2);
    supporting.setEllipsize(android.text.TextUtils.TruncateAt.END);
    labels.addView(heading, new LinearLayout.LayoutParams(-1, -2));
    labels.addView(supporting, new LinearLayout.LayoutParams(-1, -2));
    row.addView(
        labels,
        stacked ? new LinearLayout.LayoutParams(-1, -2) : new LinearLayout.LayoutParams(0, -2, 1));
    TextView value = compactText(amount, 16, false);
    value.setTypeface(null, android.graphics.Typeface.BOLD);
    if (!stacked) value.setPadding(dp(12), 0, 0, 0);
    row.addView(value, new LinearLayout.LayoutParams(-2, -2));
    if (!stacked) {
      ImageView more = new ImageView(this);
      more.setImageResource(R.drawable.ic_more_vert);
      more.setImageTintList(
          android.content.res.ColorStateList.valueOf(
              color(com.google.android.material.R.attr.colorOnSurfaceVariant)));
      more.setPadding(dp(8), 0, 0, 0);
      more.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
      row.addView(more, new LinearLayout.LayoutParams(dp(28), dp(20)));
    }
    android.util.TypedValue background = new android.util.TypedValue();
    getTheme().resolveAttribute(android.R.attr.selectableItemBackground, background, true);
    row.setBackgroundResource(background.resourceId);
    row.setFocusable(true);
    row.setClickable(true);
    row.setContentDescription(title + ", " + subtitle + ", " + amount + ". Mở chi tiết");
    ViewCompat.setAccessibilityDelegate(
        row,
        new androidx.core.view.AccessibilityDelegateCompat() {
          @Override
          public void onInitializeAccessibilityNodeInfo(
              View host, androidx.core.view.accessibility.AccessibilityNodeInfoCompat info) {
            super.onInitializeAccessibilityNodeInfo(host, info);
            info.setClassName(Button.class.getName());
          }
        });
    row.setOnClickListener(
        v -> {
          try {
            action.run();
          } catch (Exception e) {
            error(e);
          }
        });
    parent.addView(row, new LinearLayout.LayoutParams(-1, -2));
    View divider = new View(this);
    divider.setBackgroundColor(color(com.google.android.material.R.attr.colorOutlineVariant));
    parent.addView(divider, new LinearLayout.LayoutParams(-1, dp(1)));
  }

  private String accountLabel(String id) {
    if (id.isEmpty()) return "";
    try {
      return ledger.account(id).optString("name");
    } catch (Exception ignored) {
      return id;
    }
  }

  private String dateLabel(String date) {
    LocalDate value = LocalDate.parse(date);
    String label = value.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"));
    if (value.equals(LocalDate.now())) return "Hôm nay · " + label;
    if (value.equals(LocalDate.now().minusDays(1))) return "Hôm qua · " + label;
    return label;
  }

  private boolean shortViewport() {
    return getResources().getConfiguration().screenHeightDp < 480;
  }

  @Override
  public void onConfigurationChanged(android.content.res.Configuration config) {
    super.onConfigurationChanged(config);
    if (body == null) return;
    ViewGroup.LayoutParams size = body.getLayoutParams();
    size.width = Math.min(getResources().getDisplayMetrics().widthPixels, dp(640));
    body.setLayoutParams(size);
    if (currentScreen.equals("Giao dịch")) history();
    else if (currentScreen.equals("Nhắc hạn")) rules();
  }

  private void history() {
    screen("Giao dịch", 3);
    body.removeAllViews();
    List<List<JSONObject>> pages =
        TransactionHistory.pages(
            ledger.array("events"),
            historyStart,
            historyEnd,
            historyAccount,
            historyType,
            historyIncludeUndone);
    historyPage = Math.max(0, Math.min(historyPage, pages.size() - 1));
    LinearLayout controls = new LinearLayout(this);
    controls.setGravity(Gravity.CENTER_VERTICAL);
    controls.setPadding(dp(16), 0, dp(16), 0);
    String filter = historyPeriod;
    if (historyStart != null && historyPeriod.equals("Tùy chọn"))
      filter = dateLabel(historyStart.toString()) + " – " + dateLabel(historyEnd.toString());
    if (!historyAccount.isEmpty()) filter += " · " + accountLabel(historyAccount);
    if (!historyType.isEmpty()) filter += " · " + Ledger.typeLabel(historyType);
    if (!historyIncludeUndone) filter += " · Chưa hoàn tác";
    Button filters = centeredButton("Bộ lọc", this::historyFilters);
    controls.addView(filters, new LinearLayout.LayoutParams(dp(96), dp(48)));
    TextView summary = text(filter, 13);
    summary.setTextColor(color(com.google.android.material.R.attr.colorOnSurfaceVariant));
    summary.setMaxLines(2);
    summary.setEllipsize(android.text.TextUtils.TruncateAt.END);
    controls.addView(summary, new LinearLayout.LayoutParams(0, -2, 1));
    if (!shortViewport()) body.addView(controls);
    ScrollView scroll = new ScrollView(this);
    LinearLayout list = form();
    list.setPadding(dp(16), 0, dp(16), dp(8));
    scroll.addView(list);
    body.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
    if (pages.isEmpty()) {
      list.addView(
          text(
              ledger.array("events").length() == 0
                  ? "Chưa có giao dịch. Ghi bằng Chat hoặc Ghi nhanh, rồi xác nhận để lưu vào sổ."
                  : "Không có giao dịch khớp bộ lọc. Thử đổi khoảng ngày hoặc nguồn tiền.",
              15));
    } else {
      String day = "";
      for (JSONObject event : pages.get(historyPage)) {
        if (!day.equals(event.optString("date"))) {
          day = event.optString("date");
          String continuation =
              historyPage > 0
                      && pages
                          .get(historyPage - 1)
                          .get(pages.get(historyPage - 1).size() - 1)
                          .optString("date")
                          .equals(day)
                  ? " · tiếp"
                  : "";
          TextView heading = text(dateLabel(day) + continuation, 14);
          heading.setTypeface(null, android.graphics.Typeface.BOLD);
          list.addView(heading);
        }
        String title =
            event.optString("note").isBlank()
                ? Ledger.typeLabel(event.optString("type"))
                : event.optString("note");
        String detail =
            Ledger.typeLabel(event.optString("type"))
                + " · "
                + accountLabel(event.optString("account"));
        if (!event.optString("to").isEmpty()) detail += " → " + accountLabel(event.optString("to"));
        if (!event.optBoolean("active", true)) detail += " · Đã hoàn tác";
        listRow(
            list,
            title,
            detail,
            Ledger.money(event.optLong("amount")),
            () -> transactionDetails(event));
      }
    }
    LinearLayout pager = new LinearLayout(this);
    pager.setPadding(dp(8), 0, dp(8), 0);
    pager.setGravity(Gravity.CENTER_VERTICAL);
    if (shortViewport()) {
      controls.removeView(filters);
      filters.setContentDescription("Bộ lọc: " + filter);
      pager.addView(filters, new LinearLayout.LayoutParams(-2, -2));
    }
    Button previous =
        centeredButton(
            "Trước",
            () -> {
              historyPage--;
              history();
            });
    previous.setEnabled(historyPage > 0);
    Button next =
        centeredButton(
            "Sau",
            () -> {
              historyPage++;
              history();
            });
    next.setEnabled(historyPage + 1 < pages.size());
    pager.addView(previous, new LinearLayout.LayoutParams(dp(88), dp(48)));
    int total = pages.stream().mapToInt(List::size).sum();
    TextView position =
        text(
            "Trang "
                + (pages.isEmpty() ? 0 : historyPage + 1)
                + "/"
                + pages.size()
                + "\n"
                + total
                + " giao dịch",
            13);
    position.setGravity(Gravity.CENTER);
    pager.addView(position, new LinearLayout.LayoutParams(0, -2, 1));
    pager.addView(next, new LinearLayout.LayoutParams(dp(88), dp(48)));
    body.addView(pager);
  }

  private void transactionDetails(JSONObject event) {
    LinearLayout details = form();
    details.addView(
        text(
            "Ngày: "
                + dateLabel(event.optString("date"))
                + "\nNghiệp vụ: "
                + Ledger.typeLabel(event.optString("type"))
                + "\nSố tiền: "
                + Ledger.money(event.optLong("amount"))
                + "\nNguồn: "
                + accountLabel(event.optString("account"))
                + (event.optString("to").isEmpty()
                    ? ""
                    : "\nĐích: " + accountLabel(event.optString("to")))
                + "\nPhí: "
                + Ledger.money(event.optLong("fee"))
                + "\nNội dung: "
                + event.optString("note")
                + "\nDanh mục: "
                + event.optString("category")
                + (event.optString("debtId").isEmpty()
                    ? ""
                    : "\nKhoản nợ: " + event.optString("debtId"))
                + "\nTrạng thái: "
                + (event.optBoolean("active", true) ? "Đã ghi" : "Đã hoàn tác")
                + "\nMã giao dịch: "
                + event.optString("id"),
            15));
    ScrollView scroll = new ScrollView(this);
    scroll.addView(details);
    MaterialAlertDialogBuilder builder =
        new MaterialAlertDialogBuilder(this)
            .setTitle("Chi tiết giao dịch")
            .setView(scroll)
            .setNegativeButton("Đóng", null);
    if (event.optBoolean("active", true))
      builder.setPositiveButton("Hoàn tác nhóm", (d, w) -> undo(event.optString("requestId")));
    builder.show();
  }

  private EditText dateFilterField(String label, LocalDate initial) {
    EditText field = field(label, initial.toString());
    field.setFocusable(false);
    field.setOnClickListener(
        v -> {
          LocalDate selected = LocalDate.parse(field.getText().toString());
          new DatePickerDialog(
                  this,
                  (picker, year, month, day) ->
                      field.setText(LocalDate.of(year, month + 1, day).toString()),
                  selected.getYear(),
                  selected.getMonthValue() - 1,
                  selected.getDayOfMonth())
              .show();
        });
    return field;
  }

  private void historyFilters() {
    LinearLayout content = form();
    List<String> periods = List.of("Tất cả", "Hôm nay", "7 ngày qua", "Tháng này", "Tùy chọn");
    Spinner period = spinner(content, "Khoảng thời gian", periods);
    period.setSelection(periods.indexOf(historyPeriod));
    LinearLayout custom = form();
    EditText from =
        dateFilterField("Từ ngày", historyStart == null ? LocalDate.now() : historyStart);
    EditText until = dateFilterField("Đến ngày", historyEnd == null ? LocalDate.now() : historyEnd);
    custom.addView(from);
    custom.addView(until);
    content.addView(custom);
    period.setOnItemSelectedListener(
        new AdapterView.OnItemSelectedListener() {
          public void onItemSelected(AdapterView<?> parent, View v, int position, long id) {
            custom.setVisibility(position == 4 ? View.VISIBLE : View.GONE);
          }

          public void onNothingSelected(AdapterView<?> parent) {}
        });
    List<String> accountIds = new ArrayList<>(List.of(""));
    List<String> accountNames = new ArrayList<>(List.of("Tất cả nguồn tiền"));
    for (int i = 0; i < ledger.array("accounts").length(); i++) {
      JSONObject account = ledger.array("accounts").optJSONObject(i);
      accountIds.add(account.optString("id"));
      accountNames.add(
          account.optString("name") + (account.optBoolean("archived") ? " (ngừng dùng)" : ""));
    }
    Spinner account = spinner(content, "Nguồn tiền", accountNames);
    account.setSelection(Math.max(0, accountIds.indexOf(historyAccount)));
    List<String> types =
        List.of(
            "",
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
    List<String> names = new ArrayList<>();
    for (String type : types)
      names.add(type.isEmpty() ? "Tất cả nghiệp vụ" : Ledger.typeLabel(type));
    Spinner type = spinner(content, "Nghiệp vụ", names);
    type.setSelection(Math.max(0, types.indexOf(historyType)));
    CheckBox undone = new CheckBox(this);
    undone.setText("Hiện cả giao dịch đã hoàn tác");
    undone.setChecked(historyIncludeUndone);
    content.addView(undone);
    content.addView(
        button(
            "Xóa bộ lọc",
            () -> {
              period.setSelection(0);
              account.setSelection(0);
              type.setSelection(0);
              undone.setChecked(true);
            }));
    dialog(
        "Lọc giao dịch",
        content,
        "Áp dụng",
        () -> {
          String chosen = period.getSelectedItem().toString();
          LocalDate start = null, end = null, now = LocalDate.now();
          switch (chosen) {
            case "Hôm nay":
              start = now;
              end = now;
              break;
            case "7 ngày qua":
              start = now.minusDays(6);
              end = now;
              break;
            case "Tháng này":
              start = now.withDayOfMonth(1);
              end = now;
              break;
            case "Tùy chọn":
              start = LocalDate.parse(from.getText().toString());
              end = LocalDate.parse(until.getText().toString());
              if (start.isAfter(end))
                throw new IllegalArgumentException(
                    "Ngày bắt đầu phải trước hoặc bằng ngày kết thúc.");
              break;
          }
          historyPeriod = chosen;
          historyStart = start;
          historyEnd = end;
          historyAccount = accountIds.get(account.getSelectedItemPosition());
          historyType = types.get(type.getSelectedItemPosition());
          historyIncludeUndone = undone.isChecked();
          historyPage = 0;
          history();
        });
  }

  private void reportDialog() {
    LinearLayout f = form();
    LocalDate now = LocalDate.now();
    Spinner period =
        spinner(
            f, "Kỳ báo cáo", List.of("Tháng này", "Hôm nay", "Tuần này", "Năm này", "Tùy chọn"));
    EditText start = field("Từ YYYY-MM-DD", now.withDayOfMonth(1).toString()),
        end = field("Đến YYYY-MM-DD", now.toString());
    LinearLayout custom = form();
    custom.addView(start);
    custom.addView(end);
    f.addView(custom);
    custom.setVisibility(View.GONE);
    period.setOnItemSelectedListener(
        new AdapterView.OnItemSelectedListener() {
          public void onItemSelected(AdapterView<?> p, View v, int position, long id) {
            custom.setVisibility(position == 4 ? View.VISIBLE : View.GONE);
          }

          public void onNothingSelected(AdapterView<?> p) {}
        });
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
    f.addView(button("Kỳ xem · " + start + " → " + end, this::reportDialog));
    JSONObject totals = ledger.totals(start, end);
    MaterialCardView summary = card(f, false);
    LinearLayout content = form();
    summary.addView(content);
    content.addView(text("Chi tiêu trong kỳ", 14));
    TextView spending = text(Ledger.money(totals.optLong("expense")), 30);
    spending.setTypeface(null, android.graphics.Typeface.BOLD);
    content.addView(spending);
    content.addView(text("Thu nhập trong kỳ\n" + Ledger.money(totals.optLong("income")), 14));
    f.addView(text("Chi tiêu theo danh mục", 18));
    JSONObject groups = ledger.totals(start, end).optJSONObject("groups");
    long max = 1;
    for (Iterator<String> it = groups.keys(); it.hasNext(); )
      max = Math.max(max, Math.abs(groups.optLong(it.next())));
    List<String> categories = new ArrayList<>();
    groups.keys().forEachRemaining(categories::add);
    categories.sort((a, b) -> Long.compare(groups.optLong(b), groups.optLong(a)));
    if (categories.isEmpty()) f.addView(text("Chưa có chi tiêu trong kỳ này.", 14));
    for (String label : categories) {
      f.addView(text(label + " · " + Ledger.money(groups.optLong(label)), 15));
      ProgressBar bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
      bar.setProgressTintList(
          android.content.res.ColorStateList.valueOf(
              color(com.google.android.material.R.attr.colorPrimary)));
      bar.setMax(1000);
      bar.setProgress((int) Math.min(1000, Math.abs(groups.optLong(label)) * 1000 / max));
      f.addView(bar);
    }
    f.addView(
        button(
            "Tùy chọn báo cáo",
            () -> {
              new MaterialAlertDialogBuilder(this)
                  .setTitle("Báo cáo")
                  .setItems(
                      new String[] {"Xem chi tiết", "Báo cáo định kỳ", "Xuất file văn bản"},
                      (d, w) -> {
                        if (w == 0) {
                          LinearLayout details = form();
                          details.addView(text(ledger.report(start, end), 15));
                          dialog("Chi tiết báo cáo", details, "Đóng", () -> {});
                        } else if (w == 1) reportInbox();
                        else
                          exportDocument(
                              ledger
                                  .report(start, end)
                                  .getBytes(java.nio.charset.StandardCharsets.UTF_8),
                              "text/plain",
                              "bao-cao-" + end + ".txt");
                      })
                  .show();
            }));
  }

  private void rules() {
    screen("Nhắc hạn", 3);
    body.removeAllViews();
    ScrollView scroll = new ScrollView(this);
    LinearLayout f = form();
    scroll.addView(f);
    body.addView(scroll);
    List<JSONObject> reminders = new ArrayList<>();
    for (int i = 0; i < ledger.array("rules").length(); i++) {
      JSONObject rule = ledger.array("rules").optJSONObject(i);
      if (!rule.optBoolean("deleted")) reminders.add(rule);
    }
    reminders.sort(
        Comparator.comparing((JSONObject r) -> !r.optBoolean("active", true))
            .thenComparing(r -> ledger.nextDue(r)));
    f.addView(text("Nghĩa vụ định kỳ · " + reminders.size(), 14));
    if (reminders.isEmpty())
      f.addView(text("Chưa có nhắc định kỳ. Thêm khoản cần theo dõi bên dưới.", 14));
    for (JSONObject rule : reminders) {
      boolean active = rule.optBoolean("active", true);
      String period =
          switch (rule.optString("period")) {
            case "week" -> "Hàng tuần";
            case "year" -> "Hàng năm";
            default -> "Hàng tháng";
          };
      LocalDate due = ledger.nextDue(rule);
      String status =
          !active
              ? "Tạm dừng"
              : due.isBefore(LocalDate.now())
                  ? "Quá hạn"
                  : due.equals(LocalDate.now()) ? "Hôm nay" : period;
      String dueText = due.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"));
      listRow(
          f,
          rule.optString("name"),
          "Hạn " + dueText + "\n" + status,
          Ledger.money(rule.optLong("amount")),
          () -> reminderActions(rule));
    }
    List<JSONObject> debts = new ArrayList<>();
    for (int i = 0; i < ledger.array("events").length(); i++) {
      JSONObject event = ledger.array("events").optJSONObject(i);
      if (event.optBoolean("active", true)
          && List.of("lend", "borrow").contains(event.optString("type"))
          && ledger.debt(event.optString("id")) > 0) debts.add(event);
    }
    debts.sort(
        Comparator.comparing(
            e -> e.optString("due").isEmpty() ? "9999-12-31" : e.optString("due")));
    if (!debts.isEmpty()) f.addView(text("Công nợ còn lại · " + debts.size(), 14));
    for (JSONObject debt : debts) {
      String due = debt.optString("due");
      String label = debt.optString("type").equals("lend") ? "Phải thu" : "Phải trả";
      String status =
          due.isEmpty()
              ? "Không có hạn"
              : "Hạn "
                  + dateLabel(due)
                  + (LocalDate.parse(due).isBefore(LocalDate.now()) ? " · Quá hạn" : "");
      listRow(
          f,
          debt.optString("person"),
          label + " · " + status,
          Ledger.money(ledger.debt(debt.optString("id"))),
          () -> {
            LinearLayout detail = form();
            detail.addView(
                text(
                    label
                        + " · "
                        + debt.optString("person")
                        + "\nCòn lại: "
                        + Ledger.money(ledger.debt(debt.optString("id")))
                        + "\n"
                        + status
                        + "\nMã khoản nợ gốc: "
                        + debt.optString("id")
                        + "\nDùng mã này khi thu hoặc trả nợ trong Chat / Ghi nhanh.",
                    15));
            dialog("Chi tiết công nợ", detail, "Đã hiểu", () -> {});
          });
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

  private void reminderActions(JSONObject rule) {
    boolean active = rule.optBoolean("active", true);
    List<String> actions = new ArrayList<>();
    actions.add("Chi tiết nhắc hạn");
    if (active) actions.add("Xác nhận thanh toán");
    actions.add(active ? "Tạm dừng" : "Kích hoạt lại");
    actions.add("Xóa nhắc hạn");
    new MaterialAlertDialogBuilder(this)
        .setTitle(rule.optString("name") + " · " + Ledger.money(rule.optLong("amount")))
        .setItems(
            actions.toArray(new String[0]),
            (dialog, index) -> {
              try {
                switch (actions.get(index)) {
                  case "Chi tiết nhắc hạn":
                    LinearLayout details = form();
                    String period =
                        switch (rule.optString("period")) {
                          case "week" -> "Hàng tuần";
                          case "year" -> "Hàng năm";
                          default -> "Hàng tháng";
                        };
                    details.addView(
                        text(
                            rule.optString("name")
                                + "\nSố tiền: "
                                + Ledger.money(rule.optLong("amount"))
                                + (rule.optBoolean("estimated") ? " (ước tính)" : "")
                                + "\nChu kỳ: "
                                + period
                                + "\nHạn kế tiếp: "
                                + dateLabel(ledger.nextDue(rule).toString())
                                + "\nTrạng thái: "
                                + (active ? "Đang theo dõi" : "Tạm dừng"),
                            15));
                    dialog("Chi tiết nhắc hạn", details, "Đã hiểu", () -> {});
                    break;
                  case "Xác nhận thanh toán":
                    quickEntry(rule);
                    break;
                  case "Xóa nhắc hạn":
                    new MaterialAlertDialogBuilder(this)
                        .setTitle("Xóa nhắc hạn này?")
                        .setMessage(
                            rule.optString("name")
                                + "\n"
                                + "Ngừng nhắc và xóa khỏi danh sách. Giao dịch đã thanh toán vẫn"
                                + " được giữ. Thao tác này không thể hoàn tác.")
                        .setNegativeButton("Hủy", null)
                        .setPositiveButton(
                            "Xóa",
                            (d, w) -> {
                              try {
                                mutate(() -> ledger.deleteRule(rule.optString("id")));
                                rules();
                              } catch (Exception e) {
                                error(e);
                              }
                            })
                        .show();
                    break;
                  default:
                    mutate(
                        () -> {
                          Ledger.put(rule, "active", !active);
                          ledger
                              .array("audit")
                              .put(
                                  Ledger.obj(
                                      "action",
                                      "toggle_rule",
                                      "id",
                                      rule.optString("id"),
                                      "at",
                                      java.time.Instant.now().toString()));
                        });
                    rules();
                }
              } catch (Exception e) {
                error(e);
              }
            })
        .setNegativeButton("Đóng", null)
        .show();
  }

  private void aiSettings() {
    LinearLayout f = settingsPage("Kết nối AI");
    EditText key = field("Key AI Studio", ledger.data.optString("apiKey"));
    key.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
    key.setTypeface(
        android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.NORMAL));
    EditText model = field("Model", ledger.data.optString("model", Assistant.DEFAULT_MODEL));
    f.addView(text("Google AI Studio", 14));
    f.addView(key);
    f.addView(model);
    TextView status = text("", 14);
    f.addView(status);
    connectionDraftSaver =
        () -> {
          try {
            String normalized = GoogleAi.normalizeKey(key.getText().toString());
            String selected = model.getText().toString().trim();
            if (selected.isEmpty()) selected = Assistant.DEFAULT_MODEL;
            String chosen = selected;
            mutate(
                () -> {
                  Ledger.put(ledger.data, "apiKey", normalized);
                  Ledger.put(ledger.data, "model", chosen);
                  Ledger.put(ledger.data, "onboarded", true);
                });
            status.setText(normalized.isEmpty() ? "Đã lưu · dùng offline" : "Đã lưu");
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
          status.setText("Đang kết nối…");
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
                        status.setText(GoogleAi.safe(e.getMessage(), enteredKey));
                        status.setTextColor(color(com.google.android.material.R.attr.colorError));
                      });
                }
              });
        });
    f.addView(check);
    f.addView(
        button(
            "Chọn model",
            () -> {
              executor.execute(
                  () -> {
                    try {
                      List<String> models = Assistant.models(key.getText().toString());
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
        button(
            "Quyền riêng tư & trợ giúp",
            () ->
                new MaterialAlertDialogBuilder(this)
                    .setTitle("Dữ liệu của bạn")
                    .setMessage(
                        "Key được mã hóa trên máy. Google nhận tin nhắn gần nhất và ngữ cảnh nguồn"
                            + " tiền/công nợ khi dùng AI. Giao dịch chỉ được ghi sau khi bạn xác"
                            + " nhận.\n\n"
                            + "AI Studio có quota riêng cho từng key/model. App không tự đổi model."
                            + " Sao lưu không chứa key.")
                    .setPositiveButton("Đóng", null)
                    .show()));
  }

  private void settingRow(LinearLayout f, String title, String subtitle, Runnable action) {
    f.addView(button(title + "  ›", action));
    TextView note = text(subtitle, 13);
    note.setTextColor(color(com.google.android.material.R.attr.colorOnSurfaceVariant));
    note.setPadding(dp(16), 0, dp(16), dp(12));
    f.addView(note);
  }

  private void settings() {
    if (connectionDraftSaver != null && currentScreen.equals("Cài đặt")) {
      draftHandler.removeCallbacks(connectionDraftSaver);
      connectionDraftSaver.run();
    }
    connectionDraftSaver = null;
    screen("Cài đặt", 4);
    body.removeAllViews();
    ScrollView scroll = new ScrollView(this);
    LinearLayout f = form();
    scroll.addView(f);
    body.addView(scroll);
    settingRow(f, "Kết nối AI", "Key Google AI Studio và model", this::aiSettings);
    settingRow(f, "Nhắc nhở", "Giờ nhắc hạn và quyền thông báo", this::notificationSettings);
    settingRow(f, "Giao diện", "Sáng, tối hoặc theo hệ thống", this::appearanceSettings);
    settingRow(f, "Dữ liệu", "Danh mục, sao lưu và khôi phục", this::dataSettings);
    settingRow(f, "Giới thiệu", "Tính năng, phiên bản và cách dùng", this::about);
  }

  private void settingsBack() {
    toolbar.setNavigationIcon(R.drawable.ic_arrow_back);
    toolbar.setNavigationIconTint(color(com.google.android.material.R.attr.colorOnSurface));
    toolbar.setNavigationContentDescription("Quay lại Cài đặt");
    toolbar.setNavigationOnClickListener(v -> settings());
  }

  private LinearLayout settingsPage(String title) {
    screen("Cài đặt", 4);
    settingsDetail = true;
    toolbar.setTitle(title);
    settingsBack();
    body.removeAllViews();
    ScrollView scroll = new ScrollView(this);
    LinearLayout f = form();
    scroll.addView(f);
    body.addView(scroll);
    return f;
  }

  private void notificationSettings() {
    LinearLayout f = settingsPage("Nhắc nhở");
    f.addView(
        text(
            "Nhắc các khoản sắp đến hạn và thông báo khi có báo cáo tuần, tháng. Không tự ghi giao"
                + " dịch thanh toán.",
            15));
    EditText hour =
        field(
            "Giờ nhắc mỗi ngày (0–23)", String.valueOf(ledger.data.optInt("notificationHour", 20)));
    hour.setInputType(InputType.TYPE_CLASS_NUMBER);
    f.addView(hour);
    f.addView(
        button(
            "Lưu giờ nhắc",
            () -> {
              int h = Integer.parseInt(hour.getText().toString());
              if (h < 0 || h > 23) throw new IllegalArgumentException("Chọn giờ từ 0 đến 23");
              mutate(() -> Ledger.put(ledger.data, "notificationHour", h));
              ReminderReceiver.schedule(this);
              Toast.makeText(this, "Đã lưu giờ nhắc", Toast.LENGTH_SHORT).show();
            }));
    f.addView(
        button(
            "Cho phép thông báo",
            () -> {
              if (Build.VERSION.SDK_INT >= 33
                  && checkSelfPermission("android.permission.POST_NOTIFICATIONS")
                      != android.content.pm.PackageManager.PERMISSION_GRANTED)
                requestPermissions(new String[] {"android.permission.POST_NOTIFICATIONS"}, 10);
              else Toast.makeText(this, "Thông báo đã được cho phép", Toast.LENGTH_SHORT).show();
            }));
  }

  private void appearanceSettings() {
    String theme = getSharedPreferences("appearance", 0).getString("theme", "system");
    new MaterialAlertDialogBuilder(this)
        .setTitle("Giao diện")
        .setSingleChoiceItems(
            new String[] {"Theo hệ thống", "Sáng", "Tối"},
            theme.equals("dark") ? 2 : theme.equals("light") ? 1 : 0,
            (d, w) -> {
              getSharedPreferences("appearance", 0)
                  .edit()
                  .putString("theme", new String[] {"system", "light", "dark"}[w])
                  .apply();
              d.dismiss();
              recreate();
            })
        .show();
  }

  private void dataSettings() {
    LinearLayout f = settingsPage("Dữ liệu");
    settingRow(f, "Danh mục", "Bộ danh mục có sẵn để phân loại thu chi", this::categorySettings);
    settingRow(
        f,
        "Sao lưu mã hóa",
        "Lưu sổ vào file có mật khẩu; không chứa API key",
        () -> passwordDialog(false));
    settingRow(
        f, "Khôi phục sao lưu", "Thay sổ hiện tại bằng bản sao đã lưu", () -> passwordDialog(true));
    settingRow(
        f,
        "Xuất dữ liệu JSON",
        "File đọc được, dành cho xử lý bằng công cụ khác",
        this::exportData);
  }

  private void categorySettings() {
    LinearLayout f = form();
    String previous = "";
    for (String category : Ledger.STANDARD_CATEGORIES) {
      String[] parts = category.split("/", 2);
      if (!parts[0].equals(previous)) {
        f.addView(text(parts[0], 18));
        previous = parts[0];
      }
      f.addView(text(parts[1], 14));
    }
    dialog("Danh mục có sẵn", f, "Đóng", () -> {});
  }

  private void exportData() {
    JSONObject clean;
    try {
      clean = new JSONObject(ledger.data.toString());
    } catch (Exception e) {
      throw new IllegalArgumentException(e);
    }
    clean.remove("apiKey");
    clean.remove("pending");
    new MaterialAlertDialogBuilder(this)
        .setTitle("Xuất dữ liệu JSON")
        .setMessage(
            "File chứa dữ liệu tài chính dạng đọc được, không có mật khẩu. Để khôi phục sổ trong"
                + " app, hãy dùng Sao lưu mã hóa.")
        .setNegativeButton("Hủy", null)
        .setPositiveButton(
            "Xuất",
            (d, w) ->
                exportDocument(
                    clean.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8),
                    "application/json",
                    "finance-export.json"))
        .show();
  }

  private void about() {
    LinearLayout f = settingsPage("Giới thiệu");
    f.addView(text("Thư ký tài chính", 26));
    f.addView(text("Phiên bản " + BuildConfig.VERSION_NAME, 14));
    f.addView(
        text(
            "Sổ thu chi cá nhân trên điện thoại. Ghi bằng chat hoặc biểu mẫu; bạn xem và xác nhận"
                + " trước khi giao dịch được lưu.",
            16));
    f.addView(text("Bạn có thể làm gì", 18));
    f.addView(
        text(
            "• Ghi thu, chi và chuyển tiền giữa các nguồn.\n"
                + "• Theo dõi tiền mặt, ngân hàng, ví, tiết kiệm và thẻ tín dụng.\n"
                + "• Theo dõi cho vay, đi vay, thu hồi và trả nợ.\n"
                + "• Xem báo cáo theo kỳ và danh mục.\n"
                + "• Đối soát số dư, xem sao kê thẻ và hoàn tác giao dịch.\n"
                + "• Nhắc các khoản đến hạn; xem báo cáo định kỳ.\n"
                + "• Sao lưu có mật khẩu và khôi phục sổ.",
            15));
    f.addView(text("Chat & AI", 18));
    f.addView(
        text(
            "Kết nối bằng key Google AI Studio của bạn và chọn model. Không có key vẫn có thể ghi"
                + " bằng biểu mẫu. Chat giữ 5 tin nhắn gần nhất; xóa chat không xóa thu chi.",
            15));
    f.addView(text("Dữ liệu của bạn", 18));
    f.addView(
        text(
            "Sổ được mã hóa trên máy. Khi dùng AI, tin nhắn và ngữ cảnh cần thiết được gửi tới"
                + " Google. Bản sao lưu không chứa API key. App cho phép chụp và quay màn hình.",
            15));
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
                          Assistant.migrateProvider(new Ledger(restored));
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
      if (currentScreen.equals("Cài đặt")) connectionDraftSaver.run();
    }
    executor.shutdown();
    super.onDestroy();
  }
}
