package vn.finance.secretary;

import java.time.LocalDate;
import java.util.*;
import org.json.*;

/** Fixed reporting presets; never changes ledger events or claims actual saving allocations. */
final class FinanceMethod {
  static final String DEFAULT = "50_30_20", SIX_JARS = "six_jars";

  static String selected(Ledger ledger) {
    return SIX_JARS.equals(ledger.data.optString("financeMethod")) ? SIX_JARS : DEFAULT;
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
