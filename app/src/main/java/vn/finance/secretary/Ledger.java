package vn.finance.secretary;

import java.time.*;
import java.util.*;
import org.json.*;

/** Integer VND, event replay, atomic batches. AI never supplies balances. */
public final class Ledger {
  public static final List<String> STANDARD_CATEGORIES =
      List.of(
          "Ăn uống/Ăn ngoài",
          "Ăn uống/Đi chợ",
          "Ăn uống/Đồ uống",
          "Nhà ở/Thuê nhà",
          "Nhà ở/Sửa chữa",
          "Nhà ở/Đồ gia dụng",
          "Sinh hoạt/Điện nước",
          "Sinh hoạt/Internet và điện thoại",
          "Sinh hoạt/Đồ dùng",
          "Sinh hoạt/Khác",
          "Di chuyển/Xăng",
          "Di chuyển/Vé xe và gọi xe",
          "Di chuyển/Bảo dưỡng và gửi xe",
          "Sức khỏe/Khám chữa bệnh",
          "Sức khỏe/Thuốc",
          "Sức khỏe/Thể thao",
          "Mua sắm/Quần áo",
          "Mua sắm/Điện tử",
          "Mua sắm/Chăm sóc cá nhân",
          "Mua sắm/Đồ dùng",
          "Giải trí/Dịch vụ",
          "Giải trí/Du lịch",
          "Subscription/Đăng ký",
          "Học tập/Học phí",
          "Học tập/Sách và khóa học",
          "Công việc/Công cụ và vật tư",
          "Gia đình/Con cái",
          "Gia đình/Hỗ trợ",
          "Quà tặng/Quà và hiếu hỉ",
          "Quà tặng/Từ thiện",
          "Phí tài chính/Phí",
          "Phí tài chính/Lãi vay",
          "Phí tài chính/Thuế",
          "Phí tài chính/Bảo hiểm",
          "Thu nhập/Lương",
          "Thu nhập/Thưởng",
          "Thu nhập/Làm thêm và kinh doanh",
          "Thu nhập/Lãi và cổ tức",
          "Thu nhập/Quà tặng",
          "Thu nhập/Khác");
  public JSONObject data;

  public Ledger() {
    this(new JSONObject());
  }

  public Ledger(JSONObject input) {
    data = input;
    for (String k :
        List.of(
            "accounts", "events", "rules", "audit", "chat", "reports", "categories", "statements"))
      if (!data.has(k)) put(data, k, new JSONArray());
    if (array("accounts").length() == 0) addAccount("Tiền mặt", "cash", "tiền mặt,cash");
    put(data, "categories", new JSONArray(STANDARD_CATEGORIES));
  }

  public static void put(JSONObject o, String k, Object v) {
    try {
      o.put(k, v);
    } catch (JSONException e) {
      throw new IllegalArgumentException(e);
    }
  }

  public static JSONObject obj(Object... fields) {
    JSONObject o = new JSONObject();
    for (int i = 0; i < fields.length; i += 2) put(o, (String) fields[i], fields[i + 1]);
    return o;
  }

  public JSONArray array(String key) {
    return data.optJSONArray(key);
  }

  public static String today() {
    return LocalDate.now().toString();
  }

  public static String money(long n) {
    return String.format(new Locale("vi", "VN"), "%,d ₫", n);
  }

  public static String typeLabel(String type) {
    return switch (type) {
      case "expense" -> "Chi tiêu";
      case "income" -> "Thu nhập";
      case "transfer" -> "Chuyển nội bộ";
      case "lend" -> "Cho vay";
      case "borrow" -> "Đi vay";
      case "collect" -> "Thu hồi nợ";
      case "repay" -> "Trả nợ gốc";
      case "card_purchase" -> "Chi bằng thẻ";
      case "card_payment" -> "Thanh toán thẻ";
      case "card_refund" -> "Hoàn tiền thẻ";
      default -> type;
    };
  }

  public JSONObject account(String name) {
    for (int i = 0; i < array("accounts").length(); i++) {
      JSONObject a = array("accounts").optJSONObject(i);
      if (a.optString("id").equals(name) || a.optString("name").equalsIgnoreCase(name)) return a;
      for (String alias : a.optString("aliases").split(","))
        if (!alias.isBlank() && alias.trim().equalsIgnoreCase(name)) return a;
    }
    throw new IllegalArgumentException(
        "Chưa có tài khoản: " + name + ". Hãy thêm ở mục Nguồn tiền.");
  }

  public void addAccount(String name, String type, String aliases) {
    if (name.isBlank() || !List.of("cash", "bank", "wallet", "saving", "card").contains(type))
      throw new IllegalArgumentException("Tên hoặc loại tài khoản không hợp lệ");
    for (int i = 0; i < array("accounts").length(); i++)
      if (array("accounts").optJSONObject(i).optString("name").equalsIgnoreCase(name))
        throw new IllegalArgumentException("Tên đã tồn tại");
    array("accounts")
        .put(
            obj(
                "id",
                UUID.randomUUID().toString(),
                "name",
                name.trim(),
                "type",
                type,
                "aliases",
                aliases));
  }

  public boolean accountHasHistory(String id) {
    JSONObject a = account(id);
    if (a.has("checkpoint")) return true;
    for (int i = 0; i < array("events").length(); i++) {
      JSONObject e = array("events").optJSONObject(i);
      if (e.optString("account").equals(id)
          || e.optString("to").equals(id)
          || e.optJSONObject("effects").has(id)) return true;
    }
    for (int i = 0; i < array("statements").length(); i++)
      if (array("statements").optJSONObject(i).toString().contains(id)) return true;
    return false;
  }

  public void removeAccount(String id) {
    JSONObject a = account(id);
    int active = 0;
    for (int i = 0; i < array("accounts").length(); i++)
      if (!array("accounts").optJSONObject(i).optBoolean("archived")) active++;
    if (!a.optBoolean("archived") && active <= 1)
      throw new IllegalArgumentException(
          "Cần giữ ít nhất một nguồn tiền để ghi giao dịch. Hãy thêm nguồn khác trước.");
    JSONObject pending = data.optJSONObject("pending");
    if (pending != null) {
      JSONArray actions = pending.optJSONArray("actions");
      if (actions != null)
        for (int i = 0; i < actions.length(); i++) {
          JSONObject action = actions.optJSONObject(i);
          for (String key : List.of("account", "to")) {
            String value = action.optString(key);
            if (value.isBlank()) continue;
            try {
              if (account(value).optString("id").equals(id))
                throw new IllegalStateException(
                    "Nguồn đang có đề xuất chờ xác nhận. Hãy xử lý đề xuất trước.");
            } catch (IllegalArgumentException ignored) {
            }
          }
        }
    }
    if (accountHasHistory(id)) put(a, "archived", true);
    else
      for (int i = 0; i < array("accounts").length(); i++)
        if (array("accounts").optJSONObject(i).optString("id").equals(id)) {
          array("accounts").remove(i);
          break;
        }
    array("audit")
        .put(obj("action", "remove_account", "id", id, "at", java.time.Instant.now().toString()));
  }

  public long balance(String id) {
    JSONObject a = account(id);
    long value = a.optLong("checkpoint", 0);
    String since = a.optString("checkpointAt", "");
    for (int i = 0; i < array("events").length(); i++) {
      JSONObject e = array("events").optJSONObject(i);
      if (!e.optBoolean("active", true) || e.optString("createdAt").compareTo(since) <= 0) continue;
      value = Math.addExact(value, e.optJSONObject("effects").optLong(id, 0));
    }
    return value;
  }

  public long debt(String id) {
    long n = 0;
    for (int i = 0; i < array("events").length(); i++) {
      JSONObject e = array("events").optJSONObject(i);
      if (!e.optBoolean("active", true) || !e.optString("debtId").equals(id)) continue;
      String t = e.optString("type");
      n =
          Math.addExact(
              n,
              (t.equals("lend") || t.equals("borrow"))
                  ? e.optLong("amount")
                  : -e.optLong("amount"));
    }
    return n;
  }

  public JSONObject statement(String accountId, LocalDate closing) {
    JSONObject a = account(accountId);
    if (!a.optString("type").equals("card")) throw new IllegalArgumentException("Cần chọn thẻ");
    int day = a.optInt("statementDay", 25);
    closing = closing.withDayOfMonth(day);
    if (closing.isAfter(LocalDate.now())) closing = closing.minusMonths(1);
    LocalDate start = closing.minusMonths(1).plusDays(1),
        due = closing.withDayOfMonth(a.optInt("paymentDay", 5));
    if (!due.isAfter(closing)) due = due.plusMonths(1);
    long opening = 0, purchases = 0, refunds = 0, payments = 0;
    JSONArray ids = new JSONArray();
    for (int i = 0; i < array("events").length(); i++) {
      JSONObject e = array("events").optJSONObject(i);
      if (!e.optBoolean("active", true)) continue;
      long delta = e.optJSONObject("effects").optLong(accountId);
      if (delta == 0) continue;
      LocalDate date = LocalDate.parse(e.optString("date"));
      if (date.isBefore(start)) {
        opening = Math.addExact(opening, delta);
        continue;
      }
      if (date.isAfter(closing)) continue;
      ids.put(e.optString("id"));
      switch (e.optString("type")) {
        case "card_purchase":
          purchases = Math.addExact(purchases, e.optLong("amount"));
          break;
        case "card_refund":
          refunds = Math.addExact(refunds, e.optLong("amount"));
          break;
        case "card_payment":
          payments = Math.addExact(payments, e.optLong("amount"));
          break;
        default:
          break;
      }
    }
    return obj(
        "id",
        accountId + ":" + closing,
        "account",
        accountId,
        "start",
        start.toString(),
        "closing",
        closing.toString(),
        "due",
        due.toString(),
        "openingDebt",
        opening,
        "purchases",
        purchases,
        "refunds",
        refunds,
        "payments",
        payments,
        "closingDebt",
        opening + purchases - refunds - payments,
        "eventIds",
        ids,
        "generatedAt",
        Instant.now().toString());
  }

  public JSONObject debtOrigin(String id) {
    for (int i = 0; i < array("events").length(); i++) {
      JSONObject e = array("events").optJSONObject(i);
      if (e.optBoolean("active", true)
          && e.optString("id").equals(id)
          && List.of("lend", "borrow").contains(e.optString("type"))) return e;
    }
    throw new IllegalArgumentException("Không tìm thấy khoản nợ đang hiệu lực");
  }

  public void reconcile(String id, long value) {
    JSONObject a = account(id);
    put(a, "checkpoint", value);
    put(a, "checkpointAt", Instant.now().toString());
    array("audit")
        .put(
            obj(
                "action",
                "reconcile",
                "account",
                id,
                "value",
                value,
                "at",
                Instant.now().toString()));
  }

  public void commit(JSONArray batch, String requestId) {
    for (int i = 0; i < array("events").length(); i++)
      if (array("events").optJSONObject(i).optString("requestId").equals(requestId))
        throw new IllegalArgumentException("Tin nhắn này đã được ghi.");
    Ledger trial;
    try {
      trial = new Ledger(new JSONObject(data.toString()));
    } catch (JSONException e) {
      throw new IllegalArgumentException(e);
    }
    if (batch.length() == 0 || batch.length() > 20)
      throw new IllegalArgumentException("Mỗi lần ghi cần 1–20 nghiệp vụ.");
    for (int i = 0; i < batch.length(); i++) trial.apply(batch.optJSONObject(i), requestId);
    data = trial.data;
    array("audit")
        .put(
            obj(
                "action",
                "commit",
                "requestId",
                requestId,
                "at",
                Instant.now().toString(),
                "count",
                batch.length()));
  }

  private void apply(JSONObject source, String requestId) {
    if (source == null) throw new IllegalArgumentException("Nghiệp vụ không hợp lệ");
    String t = source.optString("type"), date = source.optString("date", today());
    LocalDate.parse(date);
    if (LocalDate.parse(date).isAfter(LocalDate.now()))
      throw new IllegalArgumentException(
          "Giao dịch tương lai cần tạo nghĩa vụ, không ghi đã thanh toán.");
    long amount;
    try {
      amount = new java.math.BigDecimal(source.get("amount").toString()).longValueExact();
    } catch (Exception ex) {
      throw new IllegalArgumentException("Số tiền phải là số nguyên VND");
    }
    if (amount <= 0 || amount > 1_000_000_000_000L)
      throw new IllegalArgumentException("Số tiền ngoài phạm vi");
    long fee = 0;
    if (source.has("fee")) {
      try {
        fee = new java.math.BigDecimal(source.opt("fee").toString()).longValueExact();
      } catch (Exception ex) {
        throw new IllegalArgumentException("Phí phải là số nguyên VND");
      }
    }
    if (fee < 0 || fee > 1_000_000_000L) throw new IllegalArgumentException("Phí không hợp lệ");
    JSONObject a = account(source.optString("account"));
    if (a.optBoolean("archived"))
      throw new IllegalArgumentException(
          "Nguồn tiền đã ngừng sử dụng. Khôi phục nguồn trước khi ghi.");
    String aid = a.optString("id"), id = UUID.randomUUID().toString();
    JSONObject effects = new JSONObject();
    long income = 0, expense = 0;
    String debtId = source.optString("debtId"), person = source.optString("person").trim();
    switch (t) {
      case "expense":
        if (a.optString("type").equals("card"))
          throw new IllegalArgumentException("Chi bằng thẻ cần nghiệp vụ card_purchase");
        expense = amount;
        put(effects, aid, -amount);
        break;
      case "income":
        if (a.optString("type").equals("card"))
          throw new IllegalArgumentException("Thu nhập cần tài khoản tiền");
        income = amount;
        put(effects, aid, amount);
        break;
      case "transfer":
      case "card_payment":
        {
          JSONObject b = account(source.optString("to"));
          if (b.optBoolean("archived"))
            throw new IllegalArgumentException("Nguồn đích đã ngừng sử dụng.");
          String bid = b.optString("id");
          if (aid.equals(bid) || a.optString("type").equals("card"))
            throw new IllegalArgumentException("Nguồn chuyển không hợp lệ");
          if (t.equals("card_payment") && !b.optString("type").equals("card"))
            throw new IllegalArgumentException("Đích phải là thẻ");
          if (t.equals("transfer") && b.optString("type").equals("card"))
            throw new IllegalArgumentException("Trả thẻ dùng card_payment");
          if (t.equals("card_payment") && amount > Math.max(0, balance(bid)))
            throw new IllegalArgumentException("Khoản trả vượt nợ thẻ đang theo dõi");
          put(effects, aid, -Math.addExact(amount, fee));
          put(effects, bid, t.equals("card_payment") ? -amount : amount);
          expense = fee;
          break;
        }
      case "card_purchase":
      case "card_refund":
        if (!a.optString("type").equals("card"))
          throw new IllegalArgumentException("Cần chọn thẻ tín dụng");
        expense = t.equals("card_purchase") ? amount : -amount;
        put(effects, aid, expense);
        break;
      case "lend":
      case "borrow":
        if (person.isEmpty() || a.optString("type").equals("card"))
          throw new IllegalArgumentException("Cần tên đối tượng và tài khoản tiền");
        debtId = id;
        put(effects, aid, t.equals("lend") ? -amount : amount);
        break;
      case "collect":
      case "repay":
        {
          JSONObject origin = debtOrigin(debtId);
          if (LocalDate.parse(date).isBefore(LocalDate.parse(origin.optString("date"))))
            throw new IllegalArgumentException("Ngày trả nợ không thể trước ngày vay");
          if (a.optString("type").equals("card")
              || !origin.optString("type").equals(t.equals("collect") ? "lend" : "borrow"))
            throw new IllegalArgumentException("Loại trả nợ không khớp");
          if (amount > debt(debtId)) throw new IllegalArgumentException("Số tiền vượt dư nợ");
          person = origin.optString("person");
          put(effects, aid, t.equals("collect") ? amount : -amount);
          break;
        }
      default:
        throw new IllegalArgumentException("Loại nghiệp vụ không hỗ trợ: " + t);
    }
    String due = source.optString("due", "");
    if (!due.isEmpty()) LocalDate.parse(due);
    String category =
        source.optString("category", expense != 0 ? "Sinh hoạt/Khác" : "Thu nhập/Khác");
    if (List.of("transfer", "card_payment").contains(t))
      category = source.optString("feeCategory", "Phí tài chính/Phí");
    if (!category.contains("/") || category.startsWith("/") || category.endsWith("/"))
      throw new IllegalArgumentException("Danh mục cần dạng Nhóm/Danh mục con");
    if (!STANDARD_CATEGORIES.contains(category))
      throw new IllegalArgumentException("Chọn danh mục có sẵn trong bộ danh mục của app.");
    JSONObject e =
        obj(
            "id",
            id,
            "requestId",
            requestId,
            "type",
            t,
            "date",
            date,
            "createdAt",
            Instant.now().toString(),
            "account",
            aid,
            "to",
            source.optString("to"),
            "amount",
            amount,
            "fee",
            fee,
            "income",
            income,
            "expense",
            expense,
            "category",
            category,
            "person",
            person,
            "debtId",
            debtId,
            "due",
            due,
            "note",
            source.optString("note"),
            "effects",
            effects,
            "active",
            true,
            "ruleId",
            source.optString("ruleId"),
            "occurrence",
            source.optString("occurrence"));
    if (!e.optString("ruleId").isEmpty()) {
      boolean found = false;
      for (int i = 0; i < array("rules").length(); i++) {
        JSONObject r = array("rules").optJSONObject(i);
        if (r.optString("id").equals(e.optString("ruleId"))) {
          found = true;
          if (!r.optBoolean("active", true)
              || !nextDue(r).toString().equals(e.optString("occurrence")))
            throw new IllegalArgumentException("Kỳ nghĩa vụ không khớp hoặc đã tạm dừng");
        }
      }
      if (!found) throw new IllegalArgumentException("Nghĩa vụ không tồn tại");
      for (int i = 0; i < array("events").length(); i++) {
        JSONObject old = array("events").optJSONObject(i);
        if (old.optBoolean("active", true)
            && old.optString("ruleId").equals(e.optString("ruleId"))
            && old.optString("occurrence").equals(e.optString("occurrence")))
          throw new IllegalArgumentException("Kỳ nghĩa vụ đã thanh toán");
      }
    }
    array("events").put(e);
  }

  public void undo(String requestId) {
    Ledger trial;
    try {
      trial = new Ledger(new JSONObject(data.toString()));
    } catch (Exception e) {
      throw new IllegalArgumentException(e);
    }
    boolean found = false;
    for (int i = 0; i < trial.array("events").length(); i++) {
      JSONObject e = trial.array("events").optJSONObject(i);
      if (e.optString("requestId").equals(requestId) && e.optBoolean("active", true)) {
        put(e, "active", false);
        found = true;
      }
    }
    if (!found) throw new IllegalArgumentException("Không có giao dịch để hoàn tác");
    for (int i = 0; i < array("events").length(); i++) {
      JSONObject old = array("events").optJSONObject(i);
      if (!old.optBoolean("active", true)
          || !old.optString("requestId").equals(requestId)
          || !old.optString("type").equals("card_purchase")) continue;
      for (int j = i + 1; j < trial.array("events").length(); j++) {
        JSONObject later = trial.array("events").optJSONObject(j);
        if (later.optBoolean("active", true)
            && (later.optString("type").equals("card_payment")
                    && later.optJSONObject("effects").has(old.optString("account"))
                || later.optString("type").equals("card_refund")
                    && later.optString("account").equals(old.optString("account"))))
          throw new IllegalArgumentException(
              "Hãy hoàn tác các lần trả/hoàn thẻ sau giao dịch mua này trước");
      }
    }
    for (int i = 0; i < trial.array("events").length(); i++) {
      JSONObject e = trial.array("events").optJSONObject(i);
      if (e.optBoolean("active", true)
          && List.of("collect", "repay").contains(e.optString("type"))) {
        trial.debtOrigin(e.optString("debtId"));
        if (trial.debt(e.optString("debtId")) < 0)
          throw new IllegalArgumentException("Hãy hoàn tác các lần trả nợ liên quan trước");
      }
    }
    data = trial.data;
    array("audit")
        .put(obj("action", "undo", "requestId", requestId, "at", Instant.now().toString()));
  }

  public void replace(String requestId, JSONArray batch, String newId) {
    Ledger trial;
    try {
      trial = new Ledger(new JSONObject(data.toString()));
    } catch (Exception e) {
      throw new IllegalArgumentException(e);
    }
    trial.undo(requestId);
    trial.commit(batch, newId);
    data = trial.data;
    array("audit")
        .put(
            obj(
                "action",
                "replace",
                "old",
                requestId,
                "new",
                newId,
                "at",
                Instant.now().toString()));
  }

  public String lastRequest() {
    for (int i = array("events").length() - 1; i >= 0; i--) {
      JSONObject e = array("events").optJSONObject(i);
      if (e.optBoolean("active", true)) return e.optString("requestId");
    }
    throw new IllegalArgumentException("Chưa có giao dịch");
  }

  public LocalDate nextDue(JSONObject r) {
    LocalDate next = LocalDate.parse(r.optString("next"));
    while (isPaid(r.optString("id"), next.toString())) {
      next =
          switch (r.optString("period")) {
            case "week" -> next.plusWeeks(1);
            case "year" -> next.plusYears(1);
            default ->
                next.plusMonths(1)
                    .withDayOfMonth(
                        Math.min(
                            r.optInt("day", next.getDayOfMonth()),
                            next.plusMonths(1).lengthOfMonth()));
          };
    }
    return next;
  }

  private boolean isPaid(String rule, String occurrence) {
    for (int i = 0; i < array("events").length(); i++) {
      JSONObject e = array("events").optJSONObject(i);
      if (e.optBoolean("active", true)
          && e.optString("ruleId").equals(rule)
          && e.optString("occurrence").equals(occurrence)) return true;
    }
    return false;
  }

  public String obligations() {
    StringBuilder out = new StringBuilder();
    for (int i = 0; i < array("rules").length(); i++) {
      JSONObject r = array("rules").optJSONObject(i);
      if (!r.optBoolean("active", true)) continue;
      out.append(r.optString("name"))
          .append(" · ")
          .append(money(r.optLong("amount")))
          .append(r.optBoolean("estimated") ? " (ước tính)" : "")
          .append(" · hạn ")
          .append(nextDue(r))
          .append("\n");
    }
    for (int i = 0; i < array("events").length(); i++) {
      JSONObject e = array("events").optJSONObject(i);
      if (e.optBoolean("active", true)
          && List.of("lend", "borrow").contains(e.optString("type"))
          && debt(e.optString("id")) > 0)
        out.append(e.optString("type").equals("lend") ? "Phải thu " : "Phải trả ")
            .append(e.optString("person"))
            .append(" · ")
            .append(money(debt(e.optString("id"))))
            .append(" · ")
            .append(e.optString("due").isEmpty() ? "không có hạn" : e.optString("due"))
            .append(" · mã ")
            .append(e.optString("id"))
            .append("\n");
    }
    return out.toString();
  }

  public JSONObject totals(LocalDate start, LocalDate end) {
    long income = 0, expense = 0;
    JSONObject groups = new JSONObject(), flows = new JSONObject();
    for (int i = 0; i < array("events").length(); i++) {
      JSONObject e = array("events").optJSONObject(i);
      LocalDate date = LocalDate.parse(e.optString("date"));
      if (!e.optBoolean("active", true) || date.isBefore(start) || date.isAfter(end)) continue;
      income = Math.addExact(income, e.optLong("income"));
      expense = Math.addExact(expense, e.optLong("expense"));
      if (e.optLong("expense") != 0)
        put(
            groups,
            e.optString("category"),
            Math.addExact(groups.optLong(e.optString("category")), e.optLong("expense")));
      JSONObject fx = e.optJSONObject("effects");
      for (Iterator<String> it = fx.keys(); it.hasNext(); ) {
        String id = it.next();
        if (!account(id).optString("type").equals("card"))
          put(flows, id, Math.addExact(flows.optLong(id), fx.optLong(id)));
      }
    }
    return obj("income", income, "expense", expense, "groups", groups, "flows", flows);
  }

  public String report(LocalDate start, LocalDate end) {
    JSONObject total = totals(start, end),
        previous =
            totals(
                start.minusDays(java.time.temporal.ChronoUnit.DAYS.between(start, end) + 1),
                start.minusDays(1));
    StringBuilder s =
        new StringBuilder(
            "BÁO CÁO "
                + start
                + " → "
                + end
                + (end.compareTo(LocalDate.now()) >= 0 ? " (tạm tính)" : "")
                + "\nChỉ gồm giao dịch đã ghi; chưa bao gồm hoạt động ngoài sổ.\n");
    s.append("Thu nhập: ")
        .append(money(total.optLong("income")))
        .append("\nChi phí: ")
        .append(money(total.optLong("expense")))
        .append("\nChênh lệch: ")
        .append(money(total.optLong("income") - total.optLong("expense")))
        .append("\nChi phí so với kỳ trước cùng số ngày: ")
        .append(money(total.optLong("expense") - previous.optLong("expense")))
        .append("\n\nPHÂN BỔ CHI PHÍ\n");
    JSONObject groups = total.optJSONObject("groups");
    for (Iterator<String> it = groups.keys(); it.hasNext(); ) {
      String c = it.next();
      s.append(c).append(": ").append(money(groups.optLong(c))).append("\n");
    }
    s.append("\nNGUỒN TIỀN / THẺ\n");
    for (int i = 0; i < array("accounts").length(); i++) {
      JSONObject a = array("accounts").optJSONObject(i);
      String id = a.optString("id");
      s.append(a.optString("name"))
          .append(
              a.optString("type").equals("card")
                  ? " · nợ thẻ theo sổ: "
                  : a.has("checkpoint")
                      ? " · số dư suy tính từ mốc đối soát: "
                      : " · biến động (chưa đối soát): ")
          .append(money(balance(id)))
          .append("\n");
      if (!a.optString("type").equals("card"))
        s.append("  Dòng tiền kỳ: ")
            .append(money(total.optJSONObject("flows").optLong(id)))
            .append("\n");
      else
        s.append("  Chốt sao kê ngày ")
            .append(a.optInt("statementDay", 25))
            .append("; hạn trả ngày ")
            .append(a.optInt("paymentDay", 5))
            .append(". Đối chiếu sao kê ngân hàng trước khi trả.\n");
    }
    s.append("\nDÒNG TIỀN THEO NGHIỆP VỤ\n");
    JSONObject causes = new JSONObject();
    for (int i = 0; i < array("events").length(); i++) {
      JSONObject event = array("events").optJSONObject(i);
      LocalDate d = LocalDate.parse(event.optString("date"));
      if (!event.optBoolean("active", true) || d.isBefore(start) || d.isAfter(end)) continue;
      long cash = 0;
      JSONObject fx = event.optJSONObject("effects");
      for (Iterator<String> it = fx.keys(); it.hasNext(); ) {
        String id = it.next();
        if (!account(id).optString("type").equals("card"))
          cash = Math.addExact(cash, fx.optLong(id));
      }
      put(
          causes,
          event.optString("type"),
          Math.addExact(causes.optLong(event.optString("type")), cash));
    }
    for (Iterator<String> it = causes.keys(); it.hasNext(); ) {
      String t = it.next();
      s.append(typeLabel(t)).append(": ").append(money(causes.optLong(t))).append("\n");
    }
    s.append("\nĐỐI CHIẾU CÔNG NỢ ĐẦU / CUỐI KỲ\n");
    for (int i = 0; i < array("events").length(); i++) {
      JSONObject origin = array("events").optJSONObject(i);
      if (!origin.optBoolean("active", true)
          || !List.of("lend", "borrow").contains(origin.optString("type"))
          || LocalDate.parse(origin.optString("date")).isAfter(end)) continue;
      String id = origin.optString("id");
      long
          opening =
              LocalDate.parse(origin.optString("date")).isBefore(start)
                  ? origin.optLong("amount")
                  : 0,
          closing = origin.optLong("amount");
      for (int j = 0; j < array("events").length(); j++) {
        JSONObject settlement = array("events").optJSONObject(j);
        if (!settlement.optBoolean("active", true)
            || !settlement.optString("debtId").equals(id)
            || !List.of("collect", "repay").contains(settlement.optString("type"))) continue;
        LocalDate d = LocalDate.parse(settlement.optString("date"));
        if (d.isBefore(start)) opening -= settlement.optLong("amount");
        if (!d.isAfter(end)) closing -= settlement.optLong("amount");
      }
      s.append(typeLabel(origin.optString("type")))
          .append(" · ")
          .append(origin.optString("person"))
          .append(": ")
          .append(money(opening))
          .append(" → ")
          .append(money(closing))
          .append(" · ")
          .append(origin.optString("due").isEmpty() ? "không có hạn" : origin.optString("due"))
          .append(" · mã ")
          .append(id)
          .append("\n");
    }
    s.append("\nCÔNG NỢ / NGHĨA VỤ HIỆN TẠI\n")
        .append(obligations())
        .append("\nGỢI Ý\n")
        .append(
            total.optLong("expense") > total.optLong("income")
                ? "Chi phí vượt thu nhập đã ghi. Xem nhóm chi lớn và các khoản đến hạn trước khi"
                    + " chi thêm."
                : "Theo dõi các nghĩa vụ đến hạn; khoản vay và trả thẻ không được tính vào thu"
                    + " nhập/chi phí lần nữa.")
        .append(
            "\n"
                + "Chưa thể kết luận đủ tiền trả nợ nếu nguồn tiền chưa đối soát.\n"
                + "Nguồn: sổ giao dịch và mốc đối soát trên thiết bị; các kỳ cũ phản ánh sửa đổi"
                + " khi tạo lại báo cáo.");
    return s.toString();
  }
}
