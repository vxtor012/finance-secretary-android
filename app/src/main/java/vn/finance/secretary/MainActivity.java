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
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.datepicker.MaterialDatePicker;
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
  private boolean financeReport = false;
  private boolean hideBalance;
  private ScrollView bookScroll;
  private android.graphics.Typeface urbanist;
  private LocalDate reportStart = LocalDate.now().withDayOfMonth(1), reportEnd = LocalDate.now();
  private int historyPage = 0;
  private LocalDate historyStart, historyEnd;
  private String historyAccount = "", historyType = "", historyPeriod = "Tất cả";
  private boolean historyIncludeUndone = true;

  @Override
  public void onCreate(Bundle b) {
    String theme = getSharedPreferences("appearance", 0).getString("theme", "dark");
    AppCompatDelegate.setDefaultNightMode(
        theme.equals("dark")
            ? AppCompatDelegate.MODE_NIGHT_YES
            : theme.equals("light")
                ? AppCompatDelegate.MODE_NIGHT_NO
                : AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);

    super.onCreate(b);
    urbanist = androidx.core.content.res.ResourcesCompat.getFont(this, R.font.urbanist);
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
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      getWindow().setNavigationBarContrastEnforced(false);
    }
    toolbar = new MaterialToolbar(this);
    toolbar.setTitleTextAppearance(this, R.style.PageTitle);
    toolbar.setTitleTextColor(color(com.google.android.material.R.attr.colorOnSurface));
    toolbar.setSubtitleTextColor(color(com.google.android.material.R.attr.colorOnSurfaceVariant));
    root.addView(toolbar, new LinearLayout.LayoutParams(-1, -2));
    bookTabs = new LinearLayout(this);
    bookTabs.setPadding(dp(16), 0, dp(16), dp(8));
    bookTabs.setVisibility(View.GONE);
    root.addView(bookTabs, new LinearLayout.LayoutParams(-1, -2));
    body = new LinearLayout(this);
    body.setOrientation(LinearLayout.VERTICAL);
    LinearLayout.LayoutParams bodySize = new LinearLayout.LayoutParams(contentWidth(), 0, 1);
    bodySize.gravity = Gravity.CENTER_HORIZONTAL;
    root.addView(body, bodySize);
    int navigationColor = color(com.google.android.material.R.attr.colorSurfaceContainerLow);
    FrameLayout navigationFooter = new FrameLayout(this);
    navigationFooter.setBackgroundColor(navigationColor);
    navigation = new BottomNavigationView(this);
    navigation.setBackgroundColor(navigationColor);
    navigation.setLabelVisibilityMode(
        com.google.android.material.navigation.NavigationBarView.LABEL_VISIBILITY_LABELED);
    navigation.setItemIconSize(dp(22));
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
    navigationFooter.addView(navigation, new FrameLayout.LayoutParams(-1, -2));
    root.addView(navigationFooter, new LinearLayout.LayoutParams(-1, -2));
    navigation.setOnItemSelectedListener(
        item -> {
          if (connectionDraftSaver != null && currentScreen.equals("Cài đặt")) {
            draftHandler.removeCallbacks(connectionDraftSaver);
            connectionDraftSaver.run();
          }
          if (input != null && currentScreen.equals("Chat")) chatDraft = input.getText().toString();
          switch (item.getItemId()) {
            case 1 -> showChat();
            case 2 -> showReport(reportStart, reportEnd);
            case 3 -> accounts();
            default -> settings();
          }
          return true;
        });
    ViewCompat.setOnApplyWindowInsetsListener(
        root,
        (v, insets) -> {
          androidx.core.graphics.Insets bars =
              insets.getInsets(WindowInsetsCompat.Type.systemBars());
          androidx.core.graphics.Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());
          boolean keyboardVisible = ime.bottom > bars.bottom;
          root.setPadding(bars.left, bars.top, bars.right, keyboardVisible ? ime.bottom : 0);
          navigationFooter.setPadding(0, 0, 0, bars.bottom);
          navigationFooter.setVisibility(keyboardVisible ? View.GONE : View.VISIBLE);
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
      toolbar.getMenu().clear();
      navigation.getMenu().findItem(destination).setChecked(true);
      toolbar.setNavigationOnClickListener(null);
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
    LinearLayout group = segmentGroup(labels, actions, Arrays.asList(labels).indexOf(current), 13);
    bookTabs.addView(group, new LinearLayout.LayoutParams(0, -2, 1));

    String label = hideBalance ? "Hiện số tiền" : "Ẩn số tiền";
    int icon = hideBalance ? R.drawable.ic_ronas_eye_off : R.drawable.ic_ronas_eye;
    if (shortViewport()) {
      MaterialButton visibility = iconButton(label, icon, this::toggleBookAmounts);
      bookTabs.addView(visibility, new LinearLayout.LayoutParams(dp(48), dp(48)));
    } else {
      MenuItem visibility = toolbar.getMenu().add(label);
      visibility.setIcon(icon);
      visibility.setIconTintList(
          android.content.res.ColorStateList.valueOf(getColor(R.color.app_on_surface)));
      visibility.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
      visibility.setOnMenuItemClickListener(
          item -> {
            toggleBookAmounts();
            return true;
          });
    }
  }

  private void toggleBookAmounts() {
    int scrollY = bookScroll.getScrollY();
    hideBalance = !hideBalance;
    switch (currentScreen) {
      case "Giao dịch" -> history();
      case "Nhắc hạn" -> rules();
      default -> accounts();
    }
    ScrollView refreshed = bookScroll;
    refreshed.post(() -> refreshed.scrollTo(0, scrollY));
  }

  private String bookMoney(long amount) {
    return hideBalance ? "••••••" : Ledger.money(amount);
  }

  private LinearLayout segmentGroup(
      String[] labels, Runnable[] actions, int selected, int textSize) {
    LinearLayout group = new LinearLayout(this);
    group.setBackground(surfaceShape(getColor(R.color.app_surface_high), 20));
    for (int i = 0; i < labels.length; i++) {
      MaterialButton tab = (MaterialButton) centeredButton(labels[i], actions[i]);
      tab.setTextSize(textSize);
      tab.setPadding(dp(4), dp(8), dp(4), dp(8));
      styleSegment(tab, i == selected);
      LinearLayout.LayoutParams size = new LinearLayout.LayoutParams(0, -1, 1);
      size.setMargins(dp(2), 0, dp(2), 0);
      group.addView(tab, size);
    }
    return group;
  }

  private MaterialButton actionRow(String title, int icon, Runnable action) {
    MaterialButton control = (MaterialButton) button(title, action);
    control.setBackgroundTintList(
        android.content.res.ColorStateList.valueOf(getColor(R.color.app_surface_low)));
    control.setTextColor(getColor(R.color.app_on_surface));
    control.setCornerRadius(dp(16));
    control.setIconResource(icon);
    control.setIconTint(
        android.content.res.ColorStateList.valueOf(getColor(R.color.brand_primary)));
    control.setIconPadding(dp(8));
    control.setPadding(dp(14), dp(8), dp(14), dp(8));
    return control;
  }

  private int dp(int value) {
    return Math.round(value * getResources().getDisplayMetrics().density);
  }

  private TextView text(String value, int size) {
    TextView t = new TextView(this);
    t.setText(value);
    t.setTextSize(size);
    t.setTypeface(urbanist);
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
    b.setTypeface(urbanist);
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
    e.setTypeface(urbanist);
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
    f.addView(text("Bạn có thể ghi thu chi offline ngay. Nhập key nếu muốn dùng trợ lý AI.", 16));
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
    EditText key = field("Key AI Studio", "");
    key.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
    key.setTypeface(
        android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.NORMAL));
    f.addView(key);
    ScrollView scroll = new ScrollView(this);
    scroll.addView(f);
    AlertDialog setup =
        new MaterialAlertDialogBuilder(this)
            .setTitle("Bắt đầu")
            .setView(scroll)
            .setPositiveButton("Lưu key", null)
            .setNegativeButton("Dùng offline", null)
            .create();
    setup.setOnShowListener(
        dialog -> {
          setup
              .getButton(AlertDialog.BUTTON_POSITIVE)
              .setOnClickListener(v -> finishOnboarding(setup, key.getText().toString()));
          setup
              .getButton(AlertDialog.BUTTON_NEGATIVE)
              .setOnClickListener(v -> finishOnboarding(setup, ""));
        });
    setup.show();
  }

  private void finishOnboarding(AlertDialog setup, String key) {
    try {
      mutate(
          () -> {
            Ledger.put(ledger.data, "apiKey", GoogleAi.normalizeKey(key));
            Ledger.put(ledger.data, "onboarded", true);
          });
      setup.dismiss();
      showChat();
    } catch (Exception e) {
      error(e);
    }
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
                ? com.google.android.material.R.attr.colorSurfaceContainerHigh
                : com.google.android.material.R.attr.colorSurfaceContainerLow));
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
    for (int i = 0; i < ledger.array("chat").length(); i++)
      bubble(ledger.array("chat").optJSONObject(i));
    JSONObject pending = ledger.data.optJSONObject("pending");

    if (pending != null) {
      JSONArray actions = pending.optJSONArray("actions");
      if (actions != null && actions.length() > 0) {
        JSONObject first = actions.optJSONObject(0);
        String summary =
            actions.length() == 1 && first != null
                ? Ledger.typeLabel(first.optString("type"))
                    + " · "
                    + Ledger.money(first.optLong("amount"))
                : actions.length() + " giao dịch";
        listRow(chatList, summary, "Chờ xác nhận", "Xem lại", this::confirmPending);
      }
    }
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
    outer.setOrientation(LinearLayout.VERTICAL);
    outer.setPadding(dp(16), dp(8), dp(16), dp(8));
    LinearLayout composer = new LinearLayout(this);
    composer.setGravity(Gravity.CENTER_VERTICAL);
    composer.setPadding(dp(4), dp(4), dp(4), dp(4));
    android.graphics.drawable.GradientDrawable box =
        new android.graphics.drawable.GradientDrawable();
    box.setCornerRadius(dp(28));
    box.setColor(color(com.google.android.material.R.attr.colorSurfaceContainerLow));
    composer.setBackground(box);
    MaterialButton quick = iconButton("Ghi nhanh", R.drawable.ic_add, () -> quickEntry(null));
    composer.addView(quick, new LinearLayout.LayoutParams(dp(48), dp(48)));
    if (ledger.array("chat").length() > 0) {
      toolbar
          .getMenu()
          .add("Xóa hội thoại")
          .setOnMenuItemClickListener(
              item -> {
                chatDraft = input.getText().toString();
                mutate(() -> ChatHistory.clear(ledger));
                lastFailed = "";
                showChat();
                return true;
              });
    }
    input = field("Ghi một khoản…", chatDraft);
    input.setHintTextColor(getColor(R.color.app_secondary_text));
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
    if (ledger.array("chat").length() == 0 && pending == null && !busy) {
      Button example =
          button(
              "Thử: chi 45k cash ăn sáng",
              () -> {
                input.setText("chi 45k cash ăn sáng");
                input.setSelection(input.length());
                input.requestFocus();
                WindowCompat.getInsetsController(getWindow(), input)
                    .show(WindowInsetsCompat.Type.ime());
              });
      outer.addView(example, new LinearLayout.LayoutParams(-2, -2));
    }
    outer.addView(composer, new LinearLayout.LayoutParams(-1, -2));
    body.addView(outer);
    if (ledger.array("chat").length() > 0) scroll.post(() -> scroll.fullScroll(View.FOCUS_DOWN));
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
                financeReport = false;
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
    Runnable refresh = currentScreen.equals("Nhắc hạn") ? this::rules : this::showChat;
    JSONArray actions = pending.optJSONArray("actions");
    com.google.android.material.bottomsheet.BottomSheetDialog sheet =
        new com.google.android.material.bottomsheet.BottomSheetDialog(this);
    ScrollView scroll = new ScrollView(this);
    LinearLayout f = form();
    scroll.addView(f);
    f.addView(sectionText("Xác nhận giao dịch", 24, false));
    for (int i = 0; i < actions.length(); i++) {
      JSONObject a = actions.optJSONObject(i);
      MaterialCardView receipt = card(f, false);
      LinearLayout content = form();
      receipt.addView(content);
      content.addView(sectionText(Ledger.typeLabel(a.optString("type")), 14, true));
      TextView value = sectionText(Ledger.money(a.optLong("amount")), 32, false);
      value.setTypeface(urbanist, android.graphics.Typeface.BOLD);
      content.addView(value);
      String from = accountLabel(a.optString("account"));
      String to = accountLabel(a.optString("to"));
      String route = to.isBlank() ? from : from + " → " + to;
      String date = dateLabel(a.optString("date", Ledger.today()));
      String category = a.optString("category");
      receiptDetail(content, route);
      receiptDetail(content, category.isBlank() ? date : category + " · " + date);
      receiptDetail(content, a.optString("note"));
      if (a.optLong("fee") > 0) receiptDetail(content, "Phí " + Ledger.money(a.optLong("fee")));
      if (!a.optString("person").isBlank())
        receiptDetail(content, "Đối tượng · " + a.optString("person"));
      if (!a.optString("debtId").isBlank())
        receiptDetail(content, "Khoản nợ · " + a.optString("debtId"));
      if (!a.optString("due").isBlank())
        receiptDetail(content, "Hạn · " + dateLabel(a.optString("due")));
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
              refresh.run();
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
              refresh.run();
            }));
    sheet.setContentView(scroll);
    sheet.show();
    sheet.getBehavior().setPeekHeight((int) (getResources().getDisplayMetrics().heightPixels * .8));
  }

  private void receiptDetail(LinearLayout parent, String value) {
    if (!value.isBlank()) parent.addView(sectionText(value, 14, true));
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
    Spinner s = new Spinner(this);
    s.setId(View.generateViewId());
    TextView caption = sectionText(label, 14, true);
    caption.setLabelFor(s.getId());
    f.addView(caption);
    s.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, items));
    s.setMinimumHeight(dp(48));
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
      quickEntryForm(rule, "expense", null);
      return;
    }
    String[] labels = QuickEntry.TYPES.stream().map(Ledger::typeLabel).toArray(String[]::new);
    new MaterialAlertDialogBuilder(this)
        .setTitle("Chọn loại ghi chép")
        .setItems(
            labels,
            (dialog, selected) -> quickEntryForm(null, QuickEntry.TYPES.get(selected), null))
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

  private void quickEntryForm(JSONObject rule, String initialType, JSONObject fixedDebt) {
    LinearLayout content = form();
    List<String> labels =
        QuickEntry.TYPES.stream()
            .map(Ledger::typeLabel)
            .collect(java.util.stream.Collectors.toList());
    Spinner type = fixedDebt == null ? spinner(content, "Loại ghi chép", labels) : null;
    if (type != null) type.setSelection(QuickEntry.TYPES.indexOf(initialType));
    EditText amount =
        field("Số tiền (VND)", rule == null ? "" : String.valueOf(rule.optLong("amount")));
    amount.setTextSize(32);
    amount.setGravity(Gravity.CENTER);
    amount.setMinHeight(dp(72));
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
          String selected =
              type == null ? initialType : QuickEntry.TYPES.get(type.getSelectedItemPosition());
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
          if (fixedDebt != null) {
            fields.addView(sectionText("Khoản nợ gốc", 14, true));
            fields.addView(
                sectionText(
                    fixedDebt.optString("person") + " · " + dateLabel(fixedDebt.optString("date")),
                    16,
                    false));
            values.put("debtId", () -> fixedDebt.optString("id"));
          } else if (List.of("collect", "repay").contains(selected)) {
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
    if (type != null)
      type.setOnItemSelectedListener(
          new AdapterView.OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
              update.run();
            }

            public void onNothingSelected(AdapterView<?> parent) {}
          });
    update.run();
    dialog(
        fixedDebt == null ? "Ghi nhanh" : Ledger.typeLabel(initialType),
        content,
        "Đề xuất",
        () -> {
          String selected =
              type == null ? initialType : QuickEntry.TYPES.get(type.getSelectedItemPosition());
          JSONObject fields =
              Ledger.obj(
                  "amount",
                  amount.getText().toString(),
                  "note",
                  note.getText().toString(),
                  "date",
                  date.getTag().toString());
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
          if (fixedDebt == null) showChat();
          confirmPending();
        });
  }

  private void reconcileAccount(JSONObject account) {
    LinearLayout form = form();
    String label =
        switch (account.optString("type")) {
          case "card" -> "Dư nợ phải trả";
          case "saving" -> "Tiền tiết kiệm thực tế";
          default -> "Số dư thực tế";
        };
    EditText amount = field(label, "");
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
    bookScroll = scroll;
    LinearLayout f = form();
    scroll.addView(f);
    body.addView(scroll);
    f.addView(actionRow("Thêm nguồn tiền", R.drawable.ic_add, this::addAccountDialog));
    List<JSONObject> funds = new ArrayList<>(),
        savings = new ArrayList<>(),
        cards = new ArrayList<>();
    for (int i = 0; i < ledger.array("accounts").length(); i++) {
      JSONObject account = ledger.array("accounts").optJSONObject(i);
      if (account.optBoolean("archived")) continue;
      switch (account.optString("type")) {
        case "saving" -> savings.add(account);
        case "card" -> cards.add(account);
        default -> funds.add(account);
      }
    }
    accountTotal(f, funds);
    accountGroup(f, "Tiền chi tiêu", funds);
    accountGroup(f, "Tiết kiệm", savings);
    accountGroup(f, "Thẻ tín dụng", cards);
    if (funds.isEmpty() && savings.isEmpty() && cards.isEmpty())
      f.addView(sectionText("Chưa có nguồn tiền", 16, true));
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

  private void accountGroup(LinearLayout parent, String title, List<JSONObject> accounts) {
    if (accounts.isEmpty()) return;
    TextView heading = sectionText(title, 18, false);
    heading.setTypeface(urbanist, android.graphics.Typeface.BOLD);
    ViewCompat.setAccessibilityHeading(heading, true);
    parent.addView(heading);
    for (JSONObject account : accounts) {
      String kind = account.optString("type");
      boolean credit = kind.equals("card");
      String name = account.optString("name");
      MaterialCardView surface = card(parent, true);
      LinearLayout row = new LinearLayout(this);
      row.setGravity(Gravity.CENTER_VERTICAL);
      row.setPadding(dp(16), dp(10), dp(4), dp(10));
      surface.addView(row);
      ImageView icon = new ImageView(this);
      icon.setImageResource(
          switch (kind) {
            case "cash" -> R.drawable.ic_cash;
            case "bank" -> R.drawable.ic_bank;
            case "saving" -> R.drawable.ic_savings;
            case "card" -> R.drawable.ic_credit_card;
            default -> R.drawable.ic_account_balance_wallet_outline;
          });
      icon.setImageTintList(
          android.content.res.ColorStateList.valueOf(
              color(com.google.android.material.R.attr.colorPrimary)));
      icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
      row.addView(icon, new LinearLayout.LayoutParams(dp(24), dp(24)));
      LinearLayout labels = new LinearLayout(this);
      labels.setOrientation(LinearLayout.VERTICAL);
      labels.setPadding(dp(12), 0, 0, 0);
      row.addView(labels, new LinearLayout.LayoutParams(0, -2, 1));
      TextView accountName = sectionText(name, 15, false);
      accountName.setTypeface(urbanist, android.graphics.Typeface.BOLD);
      labels.addView(accountName);
      String state =
          switch (kind) {
            case "card" -> "Dư nợ phải trả";
            case "saving" ->
                account.has("checkpoint")
                    ? "Tiền tiết kiệm"
                    : "Biến động tiết kiệm · Chưa đối soát";
            default -> account.has("checkpoint") ? "Số dư" : "Biến động · Chưa đối soát";
          };
      labels.addView(sectionText(state, 12, true));
      TextView value = sectionText(bookMoney(ledger.balance(account.optString("id"))), 22, false);
      value.setTypeface(urbanist, android.graphics.Typeface.BOLD);
      labels.addView(value);
      MaterialButton options =
          iconButton(
              "Tùy chọn " + name,
              R.drawable.ic_more_vert,
              () -> {
                PopupMenu menu = new PopupMenu(this, row.getChildAt(row.getChildCount() - 1));
                menu.getMenu().add("Đối soát");
                if (credit) menu.getMenu().add("Sao kê");
                menu.getMenu().add("Xóa nguồn tiền");
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
    }
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
            + bookMoney(statement.optLong("openingDebt"))
            + "\nMua bằng thẻ: "
            + bookMoney(statement.optLong("purchases"))
            + "\nHoàn tiền: "
            + bookMoney(statement.optLong("refunds"))
            + "\nĐã trả trong kỳ: "
            + bookMoney(statement.optLong("payments"))
            + "\nNợ cuối kỳ theo giao dịch: "
            + bookMoney(statement.optLong("closingDebt"))
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

  private TextView sectionText(String value, int size, boolean secondary) {
    TextView view = text(value, size);
    view.setPadding(0, 0, 0, 0);
    view.setTextIsSelectable(false);
    if (secondary)
      view.setTextColor(color(com.google.android.material.R.attr.colorOnSurfaceVariant));
    return view;
  }

  private TextView compactText(String value, int size, boolean secondary) {
    TextView view = sectionText(value, size, secondary);
    view.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
    return view;
  }

  private void listRow(
      LinearLayout parent, String title, String subtitle, String amount, Runnable action) {
    detailRow(parent, title, subtitle, amount, null, false, action);
  }

  private void compactDetailRow(
      LinearLayout parent,
      String title,
      String subtitle,
      String amount,
      ProgressBar progress,
      Runnable action) {
    detailRow(parent, title, subtitle, amount, progress, true, action);
  }

  private void detailRow(
      LinearLayout parent,
      String title,
      String subtitle,
      String amount,
      ProgressBar progress,
      boolean compact,
      Runnable action) {
    LinearLayout row = new LinearLayout(this);
    boolean stacked = getResources().getConfiguration().fontScale > 1.3f;
    row.setOrientation(stacked ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL);
    row.setGravity(Gravity.CENTER_VERTICAL);
    int padding = compact ? 6 : 12;
    row.setPadding(dp(16), dp(padding), dp(16), dp(padding));
    row.setMinimumHeight(dp(compact ? 52 : 64));
    LinearLayout labels = new LinearLayout(this);
    labels.setOrientation(LinearLayout.VERTICAL);
    TextView heading = compactText(title, 16, false);
    heading.setTypeface(urbanist, android.graphics.Typeface.BOLD);
    heading.setMaxLines(stacked ? 2 : 1);
    heading.setEllipsize(android.text.TextUtils.TruncateAt.END);
    TextView supporting = compactText(subtitle, 13, true);
    labels.addView(heading, new LinearLayout.LayoutParams(-1, -2));
    if (!subtitle.isBlank()) labels.addView(supporting, new LinearLayout.LayoutParams(-1, -2));
    row.addView(
        labels,
        stacked ? new LinearLayout.LayoutParams(-1, -2) : new LinearLayout.LayoutParams(0, -2, 1));
    TextView value = compactText(amount, 16, false);
    value.setTypeface(urbanist, android.graphics.Typeface.BOLD);
    value.setFontFeatureSettings("tnum");
    if (!stacked) value.setPadding(dp(12), 0, 0, 0);
    row.addView(value, new LinearLayout.LayoutParams(-2, -2));
    if (!stacked) {
      ImageView more = new ImageView(this);
      more.setImageResource(R.drawable.ic_chevron_end);
      more.setImageTintList(
          android.content.res.ColorStateList.valueOf(
              color(com.google.android.material.R.attr.colorOnSurfaceVariant)));
      more.setPadding(dp(8), 0, 0, 0);
      more.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
      row.addView(more, new LinearLayout.LayoutParams(dp(28), dp(20)));
    }
    LinearLayout target = row;
    if (progress != null) {
      target = new LinearLayout(this);
      target.setOrientation(LinearLayout.VERTICAL);
      target.addView(row, new LinearLayout.LayoutParams(-1, -2));
      LinearLayout.LayoutParams barSize = new LinearLayout.LayoutParams(-1, dp(4));
      barSize.setMargins(dp(8), 0, dp(8), dp(8));
      target.addView(progress, barSize);
    }
    android.util.TypedValue background = new android.util.TypedValue();
    getTheme().resolveAttribute(android.R.attr.selectableItemBackground, background, true);
    target.setBackground(surfaceShape(getColor(R.color.app_surface_low), 16));
    target.setForeground(getDrawable(background.resourceId));
    target.setClipToOutline(true);
    target.setFocusable(true);
    target.setClickable(true);
    target.setContentDescription(
        title
            + (subtitle.isBlank() ? "" : ", " + subtitle)
            + (amount.isBlank() ? "" : ", " + amount)
            + ". Mở chi tiết");
    ViewCompat.setAccessibilityDelegate(
        target,
        new androidx.core.view.AccessibilityDelegateCompat() {
          @Override
          public void onInitializeAccessibilityNodeInfo(
              View host, androidx.core.view.accessibility.AccessibilityNodeInfoCompat info) {
            super.onInitializeAccessibilityNodeInfo(host, info);
            info.setClassName(Button.class.getName());
          }
        });
    target.setOnClickListener(
        v -> {
          try {
            action.run();
          } catch (Exception e) {
            error(e);
          }
        });
    LinearLayout.LayoutParams size = new LinearLayout.LayoutParams(-1, -2);
    size.bottomMargin = dp(8);
    parent.addView(target, size);
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

  private int contentWidth() {
    return getResources().getConfiguration().screenWidthDp >= 720
        ? dp(640)
        : ViewGroup.LayoutParams.MATCH_PARENT;
  }

  @Override
  public void onConfigurationChanged(android.content.res.Configuration config) {
    super.onConfigurationChanged(config);
    if (body == null) return;
    ViewGroup.LayoutParams size = body.getLayoutParams();
    size.width = contentWidth();
    body.setLayoutParams(size);
    if (currentScreen.equals("Giao dịch")) history();
    else if (currentScreen.equals("Nhắc hạn")) rules();
    else if (currentScreen.equals("Nguồn tiền")) accounts();
    else if (currentScreen.equals("Báo cáo")) showReport(reportStart, reportEnd);
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
    String filter = historyPeriod;
    if (historyStart != null && historyPeriod.equals("Tùy chọn"))
      filter = reportRange(historyStart, historyEnd);
    if (!historyAccount.isEmpty()) filter += " · " + accountLabel(historyAccount);
    if (!historyType.isEmpty()) filter += " · " + Ledger.typeLabel(historyType);
    if (!historyIncludeUndone) filter += " · Chưa hoàn tác";
    String filterLabel = "Bộ lọc · " + filter;
    MaterialButton filters = actionRow(filterLabel, R.drawable.ic_filter, this::historyFilters);
    filters.setSingleLine(true);
    filters.setEllipsize(android.text.TextUtils.TruncateAt.END);
    filters.setContentDescription(filterLabel);
    ScrollView scroll = new ScrollView(this);
    bookScroll = scroll;
    LinearLayout list = form();
    list.addView(filters);
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
          heading.setTypeface(urbanist, android.graphics.Typeface.BOLD);
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
            (event.optBoolean("active", true) && !hideBalance
                    ? switch (event.optString("type")) {
                      case "income", "card_refund" -> "+";
                      case "expense", "card_purchase" -> "−";
                      default -> "";
                    }
                    : "")
                + bookMoney(event.optLong("amount")),
            () -> transactionDetails(event));
      }
    }
    if (pages.size() <= 1) return;
    LinearLayout pager = new LinearLayout(this);
    pager.setPadding(dp(8), 0, dp(8), 0);
    pager.setGravity(Gravity.CENTER_VERTICAL);
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
    String pageSeparator =
        shortViewport() && getResources().getConfiguration().screenWidthDp >= 500 ? " · " : "\n";
    TextView position =
        text(
            "Trang "
                + (pages.isEmpty() ? 0 : historyPage + 1)
                + "/"
                + pages.size()
                + pageSeparator
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
    String type = Ledger.typeLabel(event.optString("type"));
    String note = event.optString("note");
    MaterialCardView receipt = card(details, true);
    LinearLayout heading = new LinearLayout(this);
    heading.setOrientation(LinearLayout.VERTICAL);
    heading.setPadding(dp(14), dp(12), dp(14), dp(12));
    receipt.addView(heading);
    heading.addView(
        sectionText(
            type + " · " + (event.optBoolean("active", true) ? "Đã ghi" : "Đã hoàn tác"),
            13,
            true));
    boolean fromBook = currentScreen.equals("Giao dịch");
    TextView amount =
        sectionText(
            fromBook ? bookMoney(event.optLong("amount")) : Ledger.money(event.optLong("amount")),
            28,
            false);
    amount.setTypeface(urbanist, android.graphics.Typeface.BOLD);
    heading.addView(amount);
    receiptDetail(heading, dateLabel(event.optString("date")));
    String from = accountLabel(event.optString("account"));
    String to = accountLabel(event.optString("to"));
    receiptDetail(details, to.isBlank() ? from : from + " → " + to);
    receiptDetail(details, event.optString("category"));
    if (!note.isBlank()) details.addView(sectionText(note, 16, false));
    if (event.optLong("fee") > 0)
      receiptDetail(
          details,
          "Phí · "
              + (fromBook ? bookMoney(event.optLong("fee")) : Ledger.money(event.optLong("fee"))));
    if (!event.optString("debtId").isBlank())
      details.addView(text("Khoản nợ · " + event.optString("debtId"), 13));
    details.addView(text("Mã giao dịch · " + event.optString("id"), 12));
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
    java.time.format.DateTimeFormatter format =
        java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy");
    EditText field = field(label, initial.format(format));
    field.setTag(initial);
    field.setFocusable(false);
    field.setOnClickListener(
        v -> {
          LocalDate selected = (LocalDate) field.getTag();
          new DatePickerDialog(
                  this,
                  (picker, year, month, day) -> {
                    LocalDate chosen = LocalDate.of(year, month + 1, day);
                    field.setTag(chosen);
                    field.setText(chosen.format(format));
                  },
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
              start = (LocalDate) from.getTag();
              end = (LocalDate) until.getTag();
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

  private record ReportPeriod(String label, LocalDate start, LocalDate end) {}

  private List<ReportPeriod> reportPeriods() {
    LocalDate now = LocalDate.now();
    return List.of(
        new ReportPeriod("Hôm nay", now, now),
        new ReportPeriod(
            "Tuần này",
            now.with(java.time.temporal.TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)),
            now),
        new ReportPeriod("Tháng này", now.withDayOfMonth(1), now),
        new ReportPeriod("Năm này", now.withDayOfYear(1), now));
  }

  private String reportRange(LocalDate start, LocalDate end) {
    java.time.format.DateTimeFormatter format =
        java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.ROOT);
    return start.equals(end)
        ? start.format(format)
        : start.format(format) + " – " + end.format(format);
  }

  private void reportDialog(LocalDate currentStart, LocalDate currentEnd) {
    BottomSheetDialog sheet = new BottomSheetDialog(this);
    ScrollView scroll = new ScrollView(this);
    LinearLayout content = form();
    scroll.addView(content);
    sectionHeading(content, "Kỳ báo cáo");
    List<ReportPeriod> periods = reportPeriods();
    int selected = -1;
    for (int i = 0; i < periods.size(); i++) {
      ReportPeriod period = periods.get(i);
      if (period.start().equals(currentStart) && period.end().equals(currentEnd)) {
        selected = i;
        break;
      }
    }
    for (int i = 0; i < periods.size(); i++) {
      ReportPeriod period = periods.get(i);
      reportPeriodOption(
          content,
          period.label(),
          reportRange(period.start(), period.end()),
          i == selected,
          () -> {
            sheet.dismiss();
            showReport(period.start(), period.end());
          });
    }
    reportPeriodOption(
        content,
        "Khoảng ngày tùy chọn",
        selected < 0 ? reportRange(currentStart, currentEnd) : "",
        selected < 0,
        () -> {
          sheet.dismiss();
          customReportPeriod(currentStart, currentEnd);
        });
    content.addView(button("Đóng", sheet::dismiss));
    sheet.setContentView(scroll);
    sheet.setOnShowListener(
        dialog ->
            sheet
                .getBehavior()
                .setState(
                    com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED));
    sheet.show();
  }

  private void reportPeriodOption(
      LinearLayout parent, String label, String range, boolean selected, Runnable action) {
    listRow(parent, label, range, "", action);
    if (!selected) return;
    View row = parent.getChildAt(parent.getChildCount() - 1);
    row.setSelected(true);
    row.setBackground(surfaceShape(getColor(R.color.brand_container), 16));
  }

  private void customReportPeriod(LocalDate start, LocalDate end) {
    MaterialDatePicker<androidx.core.util.Pair<Long, Long>> picker =
        MaterialDatePicker.Builder.dateRangePicker()
            .setTitleText("Khoảng ngày báo cáo")
            .setSelection(
                androidx.core.util.Pair.create(
                    start.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
                    end.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()))
            .setPositiveButtonText("Áp dụng")
            .setNegativeButtonText("Hủy")
            .build();
    picker.addOnPositiveButtonClickListener(
        range ->
            showReport(
                Instant.ofEpochMilli(range.first).atOffset(ZoneOffset.UTC).toLocalDate(),
                Instant.ofEpochMilli(range.second).atOffset(ZoneOffset.UTC).toLocalDate()));
    picker.show(getSupportFragmentManager(), "report_period");
  }

  private LinearLayout scrollPage(int top) {
    ScrollView scroll = new ScrollView(this);
    scroll.setVerticalScrollBarEnabled(false);
    LinearLayout content = new LinearLayout(this);
    content.setOrientation(LinearLayout.VERTICAL);
    content.setPadding(dp(16), dp(top), dp(16), dp(12));
    scroll.addView(content);
    body.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
    return content;
  }

  private void accountTotal(LinearLayout parent, List<JSONObject> funds) {
    if (funds.size() < 2) return;
    int reconciled = 0;
    for (JSONObject fund : funds) if (fund.has("checkpoint")) reconciled++;
    int included = reconciled == 0 ? funds.size() : reconciled;
    if (included < 2) return;
    long amount = 0;
    if (!hideBalance)
      for (JSONObject fund : funds)
        if (reconciled == 0 || fund.has("checkpoint"))
          amount = Math.addExact(amount, ledger.balance(fund.optString("id")));

    String label =
        reconciled == 0
            ? "Tổng biến động tiền chi tiêu"
            : reconciled < funds.size() ? "Tiền chi tiêu đã đối soát" : "Tổng số dư chi tiêu";
    MaterialCardView panel = card(parent, true);
    panel.setCardBackgroundColor(getColor(R.color.balance_card_green));
    LinearLayout content = new LinearLayout(this);
    content.setOrientation(LinearLayout.VERTICAL);
    content.setPadding(dp(16), dp(12), dp(16), dp(12));
    panel.addView(content);
    reportSummaryValue(content, label, bookMoney(amount), 28);
    TextView scope = sectionText("Gồm " + included + " nguồn tiền", 13, false);
    scope.setTextColor(getColor(R.color.balance_card_on));
    content.addView(scope);
  }

  private void showReport(LocalDate start, LocalDate end) {
    reportStart = start;
    reportEnd = end;
    screen("Báo cáo", 2);
    reportActions(start, end);
    body.removeAllViews();
    LinearLayout page = scrollPage(4);
    String[] labels = {"Tổng quan", "Quản lý tài chính"};
    Runnable[] actions = {
      () -> selectReportTab(false, start, end), () -> selectReportTab(true, start, end)
    };
    page.addView(
        segmentGroup(labels, actions, financeReport ? 1 : 0, 14),
        new LinearLayout.LayoutParams(-1, -2));

    MaterialButton period =
        actionRow(
            "Kỳ xem · " + reportRange(start, end),
            R.drawable.ic_calendar,
            () -> reportDialog(start, end));
    LinearLayout.LayoutParams periodSize = new LinearLayout.LayoutParams(-1, -2);
    periodSize.topMargin = dp(8);
    page.addView(period, periodSize);

    JSONObject totals = ledger.totals(start, end);
    if (financeReport) financeReport(page, start, end, totals);
    else reportOverview(page, start, end, totals);
  }

  private void selectReportTab(boolean finance, LocalDate start, LocalDate end) {
    if (financeReport == finance) return;
    financeReport = finance;
    showReport(start, end);
  }

  private void reportOverview(
      LinearLayout page, LocalDate start, LocalDate end, JSONObject totals) {
    int year = end.getYear();
    sectionHeading(page, "Thu chi theo tháng · " + year);
    MaterialCardView trend = card(page, false);
    LinearLayout chart = new LinearLayout(this);
    chart.setOrientation(LinearLayout.VERTICAL);
    chart.setPadding(dp(12), dp(8), dp(12), dp(12));
    trend.addView(chart);
    List<ReportChart.Amounts> monthly = monthlyReportTotals(year);
    if (monthly.stream().anyMatch(value -> value.expense() != 0 || value.income() != 0)) {
      chart.addView(new ReportChart(this, monthly, urbanist));
    } else {
      TextView empty = sectionText("Chưa có số liệu thu chi trong năm này.", 14, true);
      empty.setPadding(dp(4), dp(8), dp(4), dp(8));
      chart.addView(empty);
    }

    MaterialCardView panel = card(page, true);
    panel.setCardBackgroundColor(getColor(R.color.balance_card_green));
    LinearLayout summary = new LinearLayout(this);
    summary.setOrientation(LinearLayout.VERTICAL);
    summary.setPadding(dp(20), dp(16), dp(20), dp(16));
    panel.addView(summary);
    reportSummaryValue(summary, "Chi tiêu trong kỳ", Ledger.money(totals.optLong("expense")), 34);
    reportSummaryValue(summary, "Thu nhập trong kỳ", Ledger.money(totals.optLong("income")), 22);

    reportCategories(page, start, end, totals.optJSONObject("groups"), false);
    reportCategories(page, start, end, incomeCategories(start, end), true);
  }

  private void reportCategories(
      LinearLayout page, LocalDate start, LocalDate end, JSONObject categories, boolean income) {
    if (income && categories.length() == 0) return;
    sectionHeading(page, income ? "Thu nhập theo danh mục" : "Chi tiêu theo danh mục");
    List<String> names = new ArrayList<>();
    categories.keys().forEachRemaining(names::add);
    names.sort((a, b) -> Long.compare(categories.optLong(b), categories.optLong(a)));
    if (names.isEmpty()) {
      page.addView(sectionText("Chưa có giao dịch trong kỳ này.", 14, true));
      return;
    }
    double peak =
        names.stream()
            .mapToDouble(label -> Math.abs((double) categories.optLong(label)))
            .max()
            .orElse(1);
    for (String category : names) {
      long amount = categories.optLong(category);
      compactDetailRow(
          page,
          category,
          "",
          Ledger.money(amount),
          reportProgress(Math.abs((double) amount) / Math.max(1, peak), false),
          income
              ? () -> inPeriodIncome(start, end, category)
              : () -> categoryTransactions(start, end, category, 0));
    }
  }

  private void reportSummaryValue(LinearLayout parent, String label, String amount, int size) {
    TextView caption = sectionText(label, 14, false);
    caption.setTextColor(getColor(R.color.balance_card_on));
    if (parent.getChildCount() > 0) caption.setPadding(0, dp(12), 0, 0);
    parent.addView(caption);
    TextView value = sectionText(amount, size, false);
    value.setTypeface(urbanist, android.graphics.Typeface.BOLD);
    value.setFontFeatureSettings("tnum");
    value.setTextColor(getColor(R.color.balance_card_on));
    parent.addView(value);
  }

  private void styleSegment(MaterialButton tab, boolean selected) {
    tab.setSelected(selected);
    tab.setInsetTop(dp(4));
    tab.setInsetBottom(dp(4));
    tab.setCornerRadius(dp(16));
    tab.setTextColor(getColor(selected ? R.color.brand_on_container : R.color.app_secondary_text));
    if (selected)
      tab.setBackgroundTintList(
          android.content.res.ColorStateList.valueOf(getColor(R.color.brand_container)));
  }

  private List<ReportChart.Amounts> monthlyReportTotals(int year) {
    List<ReportChart.Amounts> monthly =
        new ArrayList<>(Collections.nCopies(12, ReportChart.Amounts.ZERO));
    JSONArray events = ledger.array("events");
    for (int i = 0; i < events.length(); i++) {
      JSONObject event = events.optJSONObject(i);
      if (!event.optBoolean("active", true)) continue;
      long expense = event.optLong("expense"), income = event.optLong("income");
      if (expense == 0 && income == 0) continue;
      LocalDate day = LocalDate.parse(event.optString("date"));
      if (day.getYear() != year) continue;
      int monthIndex = day.getMonthValue() - 1;
      ReportChart.Amounts previous = monthly.get(monthIndex);
      monthly.set(
          monthIndex,
          new ReportChart.Amounts(
              Math.addExact(previous.expense(), expense),
              Math.addExact(previous.income(), income)));
    }
    return monthly;
  }

  private List<JSONObject> incomeEvents(LocalDate start, LocalDate end) {
    List<JSONObject> events = new ArrayList<>();
    for (List<JSONObject> page :
        TransactionHistory.pages(ledger.array("events"), start, end, "", "", false))
      for (JSONObject event : page) if (event.optLong("income") != 0) events.add(event);
    return events;
  }

  private JSONObject incomeCategories(LocalDate start, LocalDate end) {
    JSONObject groups = new JSONObject();
    for (JSONObject event : incomeEvents(start, end)) {
      String label = event.optString("category");
      Ledger.put(groups, label, Math.addExact(groups.optLong(label), event.optLong("income")));
    }
    return groups;
  }

  private void inPeriodIncome(LocalDate start, LocalDate end, String category) {
    LinearLayout entries = form();
    for (JSONObject event : incomeEvents(start, end))
      if (category.equals(event.optString("category")))
        listRow(
            entries,
            event.optString("note").isBlank()
                ? Ledger.typeLabel(event.optString("type"))
                : event.optString("note"),
            dateLabel(event.optString("date")) + " · " + accountLabel(event.optString("account")),
            Ledger.money(event.optLong("income")),
            () -> transactionDetails(event));
    dialog(category, entries, "Đóng", () -> {});
  }

  private android.graphics.drawable.GradientDrawable surfaceShape(int fill, int radius) {
    android.graphics.drawable.GradientDrawable background =
        new android.graphics.drawable.GradientDrawable();
    background.setColor(fill);
    background.setCornerRadius(dp(radius));
    return background;
  }

  private void reportMetric(LinearLayout parent, String label, long amount, boolean stacked) {
    LinearLayout metric = new LinearLayout(this);
    metric.setOrientation(LinearLayout.VERTICAL);
    metric.setPadding(0, dp(4), dp(8), dp(4));
    metric.addView(sectionText(label, 13, true));
    TextView value = sectionText(Ledger.money(amount), 23, false);
    value.setTypeface(urbanist, android.graphics.Typeface.BOLD);
    metric.addView(value);
    parent.addView(
        metric,
        stacked ? new LinearLayout.LayoutParams(-1, -2) : new LinearLayout.LayoutParams(0, -2, 1));
  }

  private void sectionHeading(LinearLayout parent, String label) {
    TextView heading = sectionText(label, 17, false);
    heading.setTypeface(urbanist, android.graphics.Typeface.BOLD);
    heading.setPadding(0, dp(8), 0, dp(4));
    ViewCompat.setAccessibilityHeading(heading, true);
    parent.addView(heading);
  }

  private ProgressBar reportProgress(double share, boolean over) {
    ProgressBar bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
    bar.setMax(1000);
    bar.setProgress((int) Math.round(Math.max(0, Math.min(1, share)) * 1000));
    bar.setProgressTintList(
        android.content.res.ColorStateList.valueOf(
            color(
                over
                    ? com.google.android.material.R.attr.colorError
                    : com.google.android.material.R.attr.colorPrimary)));
    bar.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
    return bar;
  }

  private void reportActions(LocalDate start, LocalDate end) {
    Menu menu = toolbar.getMenu();
    menu.add("Chi tiết báo cáo")
        .setOnMenuItemClickListener(
            item -> {
              LinearLayout details = form();
              details.addView(text(ledger.report(start, end), 15));
              dialog("Chi tiết báo cáo", details, "Đóng", () -> {});
              return true;
            });
    menu.add("Báo cáo định kỳ")
        .setOnMenuItemClickListener(
            item -> {
              reportInbox();
              return true;
            });
    menu.add("Xuất file văn bản")
        .setOnMenuItemClickListener(
            item -> {
              exportDocument(
                  ledger.report(start, end).getBytes(java.nio.charset.StandardCharsets.UTF_8),
                  "text/plain",
                  "bao-cao-" + end + ".txt");
              return true;
            });
  }

  private void rules() {
    screen("Nhắc hạn", 3);
    body.removeAllViews();
    ScrollView scroll = new ScrollView(this);
    bookScroll = scroll;
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
    f.addView(actionRow("Thêm nghĩa vụ định kỳ", R.drawable.ic_add, this::addReminder));
    sectionHeading(f, "Định kỳ");
    if (reminders.isEmpty()) f.addView(sectionText("Chưa có nhắc định kỳ.", 14, true));
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
      compactDetailRow(
          f,
          rule.optString("name"),
          dueText + " · " + status,
          (rule.optBoolean("estimated") ? "≈ " : "") + bookMoney(rule.optLong("amount")),
          null,
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
    if (!debts.isEmpty()) sectionHeading(f, "Công nợ còn lại");
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
          bookMoney(ledger.debt(debt.optString("id"))),
          () -> debtDetails(debt, label, status));
    }
  }

  private void debtDetails(JSONObject debt, String label, String status) {
    String id = debt.optString("id");
    String type = debt.optString("type").equals("borrow") ? "repay" : "collect";
    LinearLayout details = form();
    details.addView(
        text(
            label
                + " · "
                + debt.optString("person")
                + "\nCòn lại: "
                + bookMoney(ledger.debt(id))
                + "\n"
                + status
                + "\nMã khoản nợ gốc: "
                + id,
            15));
    ScrollView scroll = new ScrollView(this);
    scroll.addView(details);
    new MaterialAlertDialogBuilder(this)
        .setTitle("Chi tiết công nợ")
        .setView(scroll)
        .setNegativeButton("Đóng", null)
        .setPositiveButton(
            Ledger.typeLabel(type),
            (dialog, button) -> {
              if (ledger.data.has("pending")) confirmPending();
              else if (ledger.debt(id) > 0) quickEntryForm(null, type, debt);
              else error(new IllegalStateException("Khoản nợ này đã tất toán."));
            })
        .show();
  }

  private void addReminder() {
    LinearLayout fields = form();
    EditText name = field("Tên nghĩa vụ", "");
    EditText amount = field("Số tiền", "");
    EditText firstDue = dateFilterField("Kỳ đầu", LocalDate.now());
    fields.addView(name);
    fields.addView(amount);
    fields.addView(firstDue);
    List<String> periods = List.of("month", "week", "year");
    Spinner period = spinner(fields, "Chu kỳ", List.of("Hàng tháng", "Hàng tuần", "Hàng năm"));
    CheckBox estimated = new CheckBox(this);
    estimated.setText("Số tiền ước tính");
    fields.addView(estimated);
    dialog(
        "Thêm nhắc hạn",
        fields,
        "Thêm",
        () -> {
          LocalDate date = (LocalDate) firstDue.getTag();
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
                              name.getText().toString().strip(),
                              "amount",
                              n,
                              "next",
                              date.toString(),
                              "day",
                              date.getDayOfMonth(),
                              "period",
                              periods.get(period.getSelectedItemPosition()),
                              "estimated",
                              estimated.isChecked(),
                              "active",
                              true)));
          rules();
        });
  }

  private void reminderActions(JSONObject rule) {
    boolean active = rule.optBoolean("active", true);
    List<String> actions = new ArrayList<>();
    actions.add("Chi tiết nhắc hạn");
    if (active) actions.add("Xác nhận thanh toán");
    actions.add(active ? "Tạm dừng" : "Kích hoạt lại");
    actions.add("Xóa nhắc hạn");
    new MaterialAlertDialogBuilder(this)
        .setTitle(rule.optString("name") + " · " + bookMoney(rule.optLong("amount")))
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
                                + bookMoney(rule.optLong("amount"))
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
    listRow(f, title, subtitle, "", action);
  }

  private void categoryTransactions(LocalDate start, LocalDate end, String category, int page) {
    List<JSONObject> events = FinanceMethod.transactions(ledger, start, end, category);
    LinearLayout content = form();
    long total = 0;
    for (JSONObject event : events) total = Math.addExact(total, event.optLong("expense"));
    content.addView(
        text(
            start + " → " + end + "\n" + events.size() + " giao dịch · " + Ledger.money(total),
            15));
    if (events.isEmpty()) content.addView(text("Chưa có giao dịch trong kỳ này.", 15));
    int size = TransactionHistory.ROWS_PER_PAGE;
    for (int i = page * size; i < Math.min(events.size(), (page + 1) * size); i++) {
      JSONObject event = events.get(i);
      listRow(
          content,
          event.optString("note").isBlank()
              ? Ledger.typeLabel(event.optString("type"))
              : event.optString("note"),
          dateLabel(event.optString("date")) + " · " + accountLabel(event.optString("account")),
          Ledger.money(event.optLong("expense")),
          () -> transactionDetails(event));
    }
    ScrollView scroll = new ScrollView(this);
    scroll.addView(content);
    MaterialAlertDialogBuilder builder =
        new MaterialAlertDialogBuilder(this)
            .setTitle(category)
            .setView(scroll)
            .setPositiveButton("Đóng", null);
    if (page > 0)
      builder.setNegativeButton(
          "Trước", (d, w) -> categoryTransactions(start, end, category, page - 1));
    if ((page + 1) * size < events.size())
      builder.setNeutralButton(
          "Tiếp", (d, w) -> categoryTransactions(start, end, category, page + 1));
    builder.show();
  }

  private void financeReport(
      LinearLayout parent, LocalDate start, LocalDate end, JSONObject totals) {
    String method = FinanceMethod.selected(ledger);
    long income = totals.optLong("income");
    long remaining = Math.subtractExact(income, totals.optLong("expense"));
    MaterialCardView overview = card(parent, true);
    LinearLayout summary = new LinearLayout(this);
    summary.setOrientation(LinearLayout.VERTICAL);
    summary.setPadding(dp(12), 0, dp(12), dp(8));
    overview.addView(summary);
    MaterialButton methodButton =
        (MaterialButton)
            button(FinanceMethod.name(method) + "  ▾", () -> chooseReportMethod(start, end));
    methodButton.setPadding(dp(4), 0, dp(4), 0);
    summary.addView(methodButton, new LinearLayout.LayoutParams(-1, -2));
    LinearLayout metrics = new LinearLayout(this);
    metrics.setPadding(dp(4), 0, 0, 0);
    boolean stacked =
        getResources().getConfiguration().fontScale > 1.2f
            || getResources().getConfiguration().screenWidthDp < 360;
    metrics.setOrientation(stacked ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL);
    summary.addView(metrics);
    reportMetric(metrics, "Thu nhập", income, stacked);
    reportMetric(metrics, "Còn lại sau thu–chi", remaining, stacked);
    if (income <= 0) summary.addView(sectionText("Cần thu nhập dương để tính hạn mức.", 13, true));

    sectionHeading(parent, "Phân bổ theo phương pháp");
    String[] labels = FinanceMethod.labels(method);
    int[] percentages = FinanceMethod.percentages(method);
    JSONObject groups = totals.optJSONObject("groups");
    long[] spent = FinanceMethod.spending(groups, method);
    FinanceMethod.Savings savings =
        FinanceMethod.SIX_JARS.equals(method) ? FinanceMethod.longTermSavings(ledger) : null;
    for (int i = 0; i < labels.length; i++) {
      String title = labels[i] + " · " + percentages[i] + "%";
      long target = FinanceMethod.target(income, percentages[i]);
      if (i < spent.length) {
        int group = i;
        long actual = spent[i];
        boolean over = income > 0 && actual > target;
        String status =
            income <= 0
                ? "Đã chi"
                : (over ? "Vượt " : "Còn ") + Ledger.money(Math.abs(target - actual));
        ProgressBar progress =
            income > 0
                ? reportProgress(target > 0 ? (double) actual / target : actual > 0 ? 1 : 0, over)
                : null;
        compactDetailRow(
            parent,
            title,
            status,
            Ledger.money(actual),
            progress,
            () -> budgetGroup(start, end, method, group, title, groups, actual, income, target));
      } else if (savings != null && i == labels.length - 1) {
        List<String> amounts = savingsAmounts(savings);
        String goal = income > 0 ? "Mục tiêu kỳ · " + Ledger.money(target) : "Chưa có mục tiêu kỳ";
        compactDetailRow(
            parent,
            title,
            goal + "\n" + String.join("\n", amounts),
            "",
            null,
            () -> longTermSavingsDetails(title, goal, amounts));
      } else {
        boolean showRemaining = FinanceMethod.FIFTY_THIRTY_TWENTY.equals(method);
        String status = income > 0 ? "Mục tiêu " + Ledger.money(target) : "Chưa có hạn mức";
        compactDetailRow(
            parent,
            title,
            status,
            "",
            null,
            () -> savingsDetails(title, target, income, remaining, showRemaining));
      }
    }
  }

  private void chooseReportMethod(LocalDate start, LocalDate end) {
    String[] methods = {FinanceMethod.FIFTY_THIRTY_TWENTY, FinanceMethod.SIX_JARS};
    new MaterialAlertDialogBuilder(this)
        .setTitle("Phương pháp phân bổ")
        .setSingleChoiceItems(
            new String[] {FinanceMethod.name(methods[0]), FinanceMethod.name(methods[1])},
            FinanceMethod.SIX_JARS.equals(FinanceMethod.selected(ledger)) ? 1 : 0,
            (dialog, index) -> {
              try {
                mutate(() -> Ledger.put(ledger.data, "financeMethod", methods[index]));
                dialog.dismiss();
                showReport(start, end);
              } catch (Exception e) {
                error(e);
              }
            })
        .setNegativeButton("Đóng", null)
        .show();
  }

  private void budgetGroup(
      LocalDate start,
      LocalDate end,
      String method,
      int group,
      String title,
      JSONObject groups,
      long actual,
      long income,
      long target) {
    LinearLayout content = form();
    content.addView(sectionText("Đã chi · " + Ledger.money(actual), 16, false));
    if (income > 0) content.addView(sectionText("Hạn mức · " + Ledger.money(target), 14, true));
    List<String> categories = new ArrayList<>();
    for (Iterator<String> it = groups.keys(); it.hasNext(); ) {
      String category = it.next();
      if (FinanceMethod.group(method, category) == group) categories.add(category);
    }
    categories.sort((a, b) -> Long.compare(groups.optLong(b), groups.optLong(a)));
    for (String category : categories)
      compactDetailRow(
          content,
          category,
          "",
          Ledger.money(groups.optLong(category)),
          null,
          () -> categoryTransactions(start, end, category, 0));
    if (categories.isEmpty())
      content.addView(sectionText("Chưa có chi tiêu trong nhóm.", 14, true));
    dialog(title, content, "Đóng", () -> {});
  }

  private List<String> savingsAmounts(FinanceMethod.Savings savings) {
    List<String> amounts = new ArrayList<>();
    if (savings.reconciledSources() > 0)
      amounts.add("Đang giữ (đã đối soát) · " + Ledger.money(savings.reconciledAmount()));
    if (savings.unreconciledSources() > 0)
      amounts.add("Biến động chưa đối soát · " + Ledger.money(savings.unreconciledMovement()));
    if (amounts.isEmpty()) amounts.add("Chưa có nguồn Tiết kiệm");
    return amounts;
  }

  private void longTermSavingsDetails(String title, String goal, List<String> amounts) {
    LinearLayout content = form();
    content.addView(sectionText(goal, 16, false));
    for (String amount : amounts) content.addView(sectionText(amount, 16, false));
    content.addView(
        sectionText(
            "Số tiền hiện tại lấy từ các nguồn Tiết kiệm đang dùng. Mục tiêu tính theo thu nhập kỳ"
                + " xem.",
            14,
            true));
    dialog(title, content, "Đóng", () -> {});
  }

  private void savingsDetails(
      String title, long target, long income, long remaining, boolean savings) {
    LinearLayout content = form();
    if (income > 0) content.addView(sectionText("Mục tiêu · " + Ledger.money(target), 16, false));
    if (savings) {
      content.addView(sectionText("Còn lại sau thu–chi · " + Ledger.money(remaining), 16, false));
      if (income > 0)
        content.addView(
            sectionText(
                remaining >= target
                    ? "Đạt mục tiêu trên thu–chi"
                    : "Thiếu "
                        + Ledger.money(Math.subtractExact(target, remaining))
                        + " so với mục tiêu",
                14,
                true));
      content.addView(
          sectionText(
              "Số còn lại chưa phản ánh khoản đã tiết kiệm, vay, trả gốc hoặc chuyển tiền.",
              14,
              true));
    } else
      content.addView(
          sectionText("Chưa theo dõi số tiền thực tế đã chuyển vào quỹ này.", 14, true));
    dialog(title, content, "Đóng", () -> {});
  }

  private void financeSettings() {
    LinearLayout f = settingsPage("Quản lý tài chính");
    f.addView(
        text(
            "Chọn phương pháp có sẵn. Danh mục được tự động phân nhóm, không cần thiết lập tỷ lệ.",
            15));
    String current = FinanceMethod.selected(ledger);
    String[] methods = {FinanceMethod.FIFTY_THIRTY_TWENTY, FinanceMethod.SIX_JARS};
    for (String method : methods) {
      String description =
          method.equals(FinanceMethod.FIFTY_THIRTY_TWENTY)
              ? "Thiết yếu 50% · Mong muốn 30% · Tiết kiệm 20%"
              : "Thiết yếu 55% · Hưởng thụ 10% · Giáo dục 10% · Cho đi 5% · Tự do tài chính 10% ·"
                  + " Tiết kiệm dài hạn 10%";
      settingRow(
          f,
          FinanceMethod.name(method) + (current.equals(method) ? " · Đang chọn" : ""),
          description,
          () -> {
            Ledger.put(ledger.data, "financeMethod", method);
            save();
            financeSettings();
          });
    }
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
    sectionHeading(f, "Ghi sổ và theo dõi");
    settingRow(
        f,
        "Kết nối AI",
        ledger.data.optString("apiKey").isBlank() ? "Chưa thiết lập" : "Đã lưu key",
        this::aiSettings);
    settingRow(
        f,
        "Quản lý tài chính",
        FinanceMethod.name(FinanceMethod.selected(ledger)),
        this::financeSettings);
    settingRow(f, "Nhắc nhở", "", this::notificationSettings);
    String theme = getSharedPreferences("appearance", 0).getString("theme", "system");
    String appearance =
        switch (theme) {
          case "dark" -> "Tối";
          case "light" -> "Sáng";
          default -> "Theo hệ thống";
        };
    sectionHeading(f, "Ứng dụng");
    settingRow(f, "Giao diện", appearance, this::appearanceSettings);
    settingRow(f, "Dữ liệu", "", this::dataSettings);
    settingRow(f, "Giới thiệu", "", this::about);
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
    String theme = getSharedPreferences("appearance", 0).getString("theme", "dark");
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
    f.addView(text(getString(R.string.app_name), 26));
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
