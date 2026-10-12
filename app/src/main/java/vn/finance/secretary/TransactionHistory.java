package vn.finance.secretary;

import java.time.LocalDate;
import java.util.*;
import org.json.*;

/** Date-sorted bounded pages, independent of Android views and ledger mutations. */
final class TransactionHistory {
  static final int DAYS_PER_PAGE = 3, ROWS_PER_PAGE = 30;

  static List<List<JSONObject>> pages(
      JSONArray events,
      LocalDate start,
      LocalDate end,
      String account,
      String type,
      boolean includeUndone) {
    List<JSONObject> matching = new ArrayList<>();
    // Reverse insertion order within a date; dates may be entered retrospectively.
    for (int i = events.length() - 1; i >= 0; i--) {
      JSONObject event = events.optJSONObject(i);
      LocalDate date = LocalDate.parse(event.optString("date"));
      if (start != null && date.isBefore(start) || end != null && date.isAfter(end)) continue;
      if (!includeUndone && !event.optBoolean("active", true)) continue;
      if (!type.isEmpty() && !type.equals(event.optString("type"))) continue;
      if (!account.isEmpty()
          && !account.equals(event.optString("account"))
          && !account.equals(event.optString("to"))
          && (event.optJSONObject("effects") == null
              || !event.optJSONObject("effects").has(account))) continue;
      matching.add(event);
    }
    matching.sort(Comparator.comparing((JSONObject e) -> e.optString("date")).reversed());
    List<List<JSONObject>> pages = new ArrayList<>();
    List<JSONObject> page = new ArrayList<>();
    Set<String> dates = new HashSet<>();
    for (JSONObject event : matching) {
      String date = event.optString("date");
      if (page.size() == ROWS_PER_PAGE || dates.size() == DAYS_PER_PAGE && !dates.contains(date)) {
        pages.add(page);
        page = new ArrayList<>();
        dates.clear();
      }
      dates.add(date);
      page.add(event);
    }
    if (!page.isEmpty()) pages.add(page);
    return pages;
  }
}
