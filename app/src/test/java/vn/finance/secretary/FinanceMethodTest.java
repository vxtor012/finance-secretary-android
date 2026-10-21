package vn.finance.secretary;

import static org.junit.Assert.*;

import java.time.LocalDate;
import java.util.*;
import org.json.*;
import org.junit.Test;

public class FinanceMethodTest {
  @Test
  public void defaultsAndPersistsPresetInBackupData() throws Exception {
    Ledger ledger = new Ledger();
    assertEquals(FinanceMethod.DEFAULT, FinanceMethod.selected(ledger));
    Ledger.put(ledger.data, "financeMethod", FinanceMethod.SIX_JARS);
    assertEquals(
        FinanceMethod.SIX_JARS,
        FinanceMethod.selected(new Ledger(new JSONObject(ledger.data.toString()))));
    Ledger.put(ledger.data, "financeMethod", "unknown");
    assertEquals(FinanceMethod.DEFAULT, FinanceMethod.selected(ledger));
  }

  @Test
  public void everyExpenseIsCountedOnceInEachPreset() {
    JSONObject groups = new JSONObject();
    for (String category : Ledger.STANDARD_CATEGORIES)
      if (!category.startsWith("Thu nhập/")) Ledger.put(groups, category, 12345);
    Ledger.put(groups, "Danh mục cũ/Khác", 99);
    for (String method : List.of(FinanceMethod.DEFAULT, FinanceMethod.SIX_JARS)) {
      assertEquals(100, Arrays.stream(FinanceMethod.percentages(method)).sum());
      assertEquals(
          (Ledger.STANDARD_CATEGORIES.size() - 6) * 12345L + 99,
          Arrays.stream(FinanceMethod.spending(groups, method)).sum());
    }
    assertEquals(2, FinanceMethod.group(FinanceMethod.SIX_JARS, "Học tập/Học phí"));
    assertEquals(0, FinanceMethod.group(FinanceMethod.DEFAULT, "Học tập/Học phí"));
    assertEquals(3, FinanceMethod.group(FinanceMethod.SIX_JARS, "Quà tặng/Từ thiện"));
    assertEquals(1, FinanceMethod.group(FinanceMethod.DEFAULT, "Ăn uống/Ăn ngoài"));
  }

  @Test
  public void targetsHandleNoIncomeRoundingAndLargeAmounts() {
    assertEquals(0, FinanceMethod.target(-100, 50));
    assertEquals(0, FinanceMethod.target(0, 50));
    assertEquals(50, FinanceMethod.target(101, 50));
    assertEquals(Long.MAX_VALUE / 2, FinanceMethod.target(Long.MAX_VALUE, 50));
  }

  @Test
  public void drilldownMatchesPeriodCategoryAndExpenseIncludingFees() {
    Ledger ledger = new Ledger();
    String category = "Phí tài chính/Phí";
    JSONArray events = ledger.array("events");
    events.put(event("start", "2026-10-01", category, 100, true));
    events.put(event("end", "2026-10-21", category, 50, true));
    events.put(event("undone", "2026-10-15", category, 300, false));
    events.put(event("outside", "2026-09-30", category, 400, true));
    events.put(event("transfer", "2026-10-12", category, 0, true));
    events.put(event("other", "2026-10-12", "Sinh hoạt/Khác", 500, true));
    List<JSONObject> rows =
        FinanceMethod.transactions(
            ledger, LocalDate.parse("2026-10-01"), LocalDate.parse("2026-10-21"), category);
    assertEquals(2, rows.size());
    assertEquals("end", rows.get(0).optString("id"));
    assertEquals(150, rows.stream().mapToLong(e -> e.optLong("expense")).sum());
    assertTrue(
        FinanceMethod.transactions(
                ledger, LocalDate.parse("2027-01-01"), LocalDate.parse("2027-01-31"), category)
            .isEmpty());
  }

  private JSONObject event(String id, String date, String category, long expense, boolean active) {
    return Ledger.obj(
        "id", id, "date", date, "category", category, "expense", expense, "active", active);
  }
}
