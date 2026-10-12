package vn.finance.secretary;

import static org.junit.Assert.*;

import org.json.*;
import org.junit.Test;

public class QuickEntryTest {
  private JSONObject fields(String category) {
    return Ledger.obj(
        "amount",
        "50k",
        "account",
        "cash",
        "date",
        Ledger.today(),
        "note",
        "fixture",
        "category",
        category,
        "to",
        "old-card",
        "fee",
        "not-a-number",
        "person",
        "old-person",
        "due",
        "not-a-date",
        "debtId",
        "old-debt");
  }

  @Test
  public void switchingFromLoanOrTransferToIncomeDiscardsHiddenInvalidValues() {
    JSONObject action = QuickEntry.action("income", fields("Thu nhập/Lương"));
    for (String hidden : new String[] {"to", "fee", "person", "due", "debtId"})
      assertFalse(action.has(hidden));
    Ledger ledger = new Ledger();
    ledger.commit(new JSONArray().put(action), "income-fixture");
    assertEquals(
        50000,
        ledger.totals(java.time.LocalDate.now(), java.time.LocalDate.now()).optLong("income"));
  }

  @Test
  public void cardPaymentIgnoresStaleTransferFeeAndLoanFields() {
    Ledger ledger = new Ledger();
    ledger.addAccount("Thẻ", "card", "card");
    JSONObject purchase =
        Ledger.obj(
            "type",
            "card_purchase",
            "amount",
            100000,
            "account",
            "card",
            "date",
            Ledger.today(),
            "category",
            "Mua sắm/Điện tử");
    ledger.commit(new JSONArray().put(purchase), "purchase");
    JSONObject fields = fields("Thu nhập/Lương");
    Ledger.put(fields, "to", "card");
    JSONObject payment = QuickEntry.action("card_payment", fields);
    assertFalse(payment.has("category"));
    assertFalse(payment.has("fee"));
    ledger.commit(new JSONArray().put(payment), "payment");
    assertEquals(50000, ledger.balance(ledger.account("card").optString("id")));
    assertEquals(
        100000,
        ledger.totals(java.time.LocalDate.now(), java.time.LocalDate.now()).optLong("expense"));
  }

  @Test
  public void transferOnlyCountsExplicitFeeAndBlankFeeIsZero() {
    Ledger ledger = new Ledger();
    ledger.addAccount("Bank", "bank", "bank");
    JSONObject fields = fields("Thu nhập/Lương");
    Ledger.put(fields, "to", "bank");
    Ledger.put(fields, "fee", "3k");
    JSONObject action = QuickEntry.action("transfer", fields);
    assertFalse(action.has("category"));
    assertFalse(action.has("person"));
    assertFalse(action.has("due"));
    ledger.commit(new JSONArray().put(action), "transfer");
    assertEquals(
        3000,
        ledger.totals(java.time.LocalDate.now(), java.time.LocalDate.now()).optLong("expense"));
    Ledger.put(fields, "fee", "");
    assertEquals(0, QuickEntry.action("transfer", fields).optLong("fee"));
  }
}
