package vn.finance.secretary;

import java.time.LocalDate;
import java.util.*;
import org.json.*;

/** Fixed reporting presets and read-only summaries of recorded amounts. */
final class FinanceMethod {
  static final String FIFTY_THIRTY_TWENTY = "50_30_20", SIX_JARS = "six_jars";

  record Savings(
      int reconciledSources,
      long reconciledAmount,
      int unreconciledSources,
      long unreconciledMovement) {}

  static Savings longTermSavings(Ledger ledger) {
    JSONArray accounts = ledger.array("accounts");
    int reconciled = 0, unreconciled = 0;
    long amount = 0, movement = 0;
    for (int i = 0; i < accounts.length(); i++) {
      JSONObject account = accounts.optJSONObject(i);
      if (account.optBoolean("archived") || !account.optString("type").equals("saving")) continue;
      long value = ledger.balance(account.optString("id"));
      if (account.has("checkpoint")) {
        reconciled++;
        amount = Math.addExact(amount, value);
      } else {
        unreconciled++;
        movement = Math.addExact(movement, value);
      }
    }
    return new Savings(reconciled, amount, unreconciled, movement);
  }

  static String selected(Ledger ledger) {
    return FIFTY_THIRTY_TWENTY.equals(ledger.data.optString("financeMethod"))
        ? FIFTY_THIRTY_TWENTY
        : SIX_JARS;
  }

  static String name(String method) {
    return SIX_JARS.equals(method) ? "6 chiếc lọ" : "50/30/20";
  }

  static String[] labels(String method) {
    return SIX_JARS.equals(method)
        ? new String[] {
          "Thiết yếu", "Hưởng thụ", "Giáo dục", "Cho đi", "Tự do tài chính", "Tiết kiệm dài hạn"
        }
        : new String[] {"Thiết yếu", "Mong muốn", "Tiết kiệm"};
  }

  static int[] percentages(String method) {
    return SIX_JARS.equals(method) ? new int[] {55, 10, 10, 5, 10, 10} : new int[] {50, 30, 20};
  }

  static int expenseGroups(String method) {
    return SIX_JARS.equals(method) ? 4 : 2;
  }

  static int group(String method, String category) {
    if (SIX_JARS.equals(method)) {
      if (category.startsWith("Học tập/")) return 2;
      if (category.startsWith("Quà tặng/")) return 3;
    }
    if (category.startsWith("Giải trí/")
        || category.startsWith("Mua sắm/")
        || category.startsWith("Subscription/")
        || category.startsWith("Quà tặng/")
        || category.equals("Ăn uống/Ăn ngoài")
        || category.equals("Ăn uống/Đồ uống")
        || category.equals("Sức khỏe/Thể thao")) return 1;
    return 0;
  }

  static long target(long income, int percent) {
    // Integer VND; avoid overflowing income * percent.
    return income <= 0 ? 0 : income / 100 * percent + income % 100 * percent / 100;
  }

  static List<JSONObject> transactions(
      Ledger ledger, LocalDate start, LocalDate end, String category) {
    List<JSONObject> result = new ArrayList<>();
    for (List<JSONObject> page :
        TransactionHistory.pages(ledger.array("events"), start, end, "", "", false))
      for (JSONObject event : page)
        if (event.optLong("expense") != 0 && category.equals(event.optString("category")))
          result.add(event);
    return result;
  }

  static long[] spending(JSONObject groups, String method) {
    long[] values = new long[expenseGroups(method)];
    for (Iterator<String> it = groups.keys(); it.hasNext(); ) {
      String category = it.next();
      int index = group(method, category);
      values[index] = Math.addExact(values[index], groups.optLong(category));
    }
    return values;
  }
}
