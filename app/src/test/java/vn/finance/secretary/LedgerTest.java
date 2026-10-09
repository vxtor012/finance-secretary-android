package vn.finance.secretary;

import static org.junit.Assert.*;

import java.time.*;
import org.json.*;
import org.junit.Test;

public class LedgerTest {
  @Test public void recurringPaymentsAdvanceWithoutDuplicates(){Ledger l=ledger();JSONObject r=Ledger.obj("id","rent","name","Rent","next",LocalDate.now().minusMonths(1).toString(),"period","month","day",LocalDate.now().getDayOfMonth(),"active",true);l.array("rules").put(r);JSONObject a=action("expense",1000000,"MB");Ledger.put(a,"ruleId","rent");Ledger.put(a,"occurrence",r.optString("next"));commit(l,a);assertEquals(LocalDate.now(),l.nextDue(r));try{commit(l,a);fail();}catch(IllegalArgumentException expected){}assertEquals(1,l.array("events").length());}
  @Test public void undoCardPurchaseCannotOrphanPayment(){Ledger l=ledger();l.commit(new JSONArray().put(action("card_purchase",1000000,"Visa")),"purchase");JSONObject a=action("card_payment",1000000,"MB");Ledger.put(a,"to","Visa");commit(l,a);String before=l.data.toString();try{l.undo("purchase");fail();}catch(IllegalArgumentException expected){}assertEquals(before,l.data.toString());}
  @Test public void debtCannotBeSettledBeforeLoan(){Ledger l=ledger();JSONObject lend=action("lend",1000000,"MB");Ledger.put(lend,"person","Nam");commit(l,lend);JSONObject collect=action("collect",100000,"MB");Ledger.put(collect,"debtId",l.array("events").optJSONObject(0).optString("id"));Ledger.put(collect,"date",LocalDate.now().minusDays(1).toString());try{commit(l,collect);fail();}catch(IllegalArgumentException expected){}assertEquals(1,l.array("events").length());}
  private Ledger ledger() {
    Ledger l = new Ledger();
    l.addAccount("MB", "bank", "mb");
    l.addAccount("MoMo", "wallet", "ví");
    l.addAccount("Visa", "card", "thẻ");
    return l;
  }

  private JSONObject action(String type, long n, String account) {
    return Ledger.obj("type", type, "amount", n, "account", account, "date", Ledger.today());
  }

  private void commit(Ledger l, JSONObject... events) {
    JSONArray batch = new JSONArray();
    for (JSONObject e : events) batch.put(e);
    l.commit(batch, java.util.UUID.randomUUID().toString());
  }

  private long expense(Ledger l) {
    return l.totals(LocalDate.now().minusDays(1), LocalDate.now()).optLong("expense");
  }

  @Test
  public void transferCountsOnlyFee() {
    Ledger l = ledger();
    JSONObject e = action("transfer", 1000000, "MB");
    Ledger.put(e, "to", "MoMo");
    Ledger.put(e, "fee", 3000);
    commit(l, e);
    assertEquals(3000, expense(l));
    assertEquals(-1003000, l.balance(l.account("MB").optString("id")));
    assertEquals(1000000, l.balance(l.account("MoMo").optString("id")));
  }

  @Test
  public void cardPaymentDoesNotDoubleExpense() {
    Ledger l = ledger();
    commit(l, action("card_purchase", 12000000, "Visa"));
    JSONObject payment = action("card_payment", 5000000, "MB");
    Ledger.put(payment, "to", "Visa");
    commit(l, payment);
    assertEquals(12000000, expense(l));
    assertEquals(7000000, l.balance(l.account("Visa").optString("id")));
    commit(l, action("card_refund", 1000000, "Visa"));
    assertEquals(11000000, expense(l));
  }

  @Test
  public void partialDebtNoDueDate() {
    Ledger l = ledger();
    JSONObject lend = action("lend", 2000000, "MB");
    Ledger.put(lend, "person", "Nam");
    commit(l, lend);
    String id = l.array("events").optJSONObject(0).optString("id");
    JSONObject collect = action("collect", 500000, "MoMo");
    Ledger.put(collect, "debtId", id);
    commit(l, collect);
    assertEquals(1500000, l.debt(id));
    assertEquals(0, expense(l));
    assertTrue(l.obligations().contains("không có hạn"));
    assertEquals(0, l.totals(LocalDate.now(), LocalDate.now()).optLong("income"));
  }

  @Test
  public void borrowingAndRepayingNotIncomeExpense() {
    Ledger l = ledger();
    JSONObject borrow = action("borrow", 1000000, "MB");
    Ledger.put(borrow, "person", "Lan");
    commit(l, borrow);
    JSONObject pay = action("repay", 200000, "MB");
    Ledger.put(pay, "debtId", l.array("events").optJSONObject(0).optString("id"));
    commit(l, pay);
    assertEquals(0, expense(l));
    assertEquals(0, l.totals(LocalDate.now(), LocalDate.now()).optLong("income"));
  }

  @Test
  public void invalidBatchIsAtomic() {
    Ledger l = ledger();
    String before = l.data.toString();
    try {
      commit(l, action("expense", 50000, "MB"), action("expense", 30000, "missing"));
      fail();
    } catch (IllegalArgumentException expected) {
    }
    assertEquals(before, l.data.toString());
  }

  @Test
  public void duplicateRequestRejectedEvenAfterUndo() {
    Ledger l = ledger();
    JSONArray a = new JSONArray().put(action("expense", 50000, "MB"));
    l.commit(a, "same");
    l.undo("same");
    try {
      l.commit(a, "same");
      fail();
    } catch (IllegalArgumentException expected) {
    }
    assertEquals(0, expense(l));
    assertEquals(1, l.array("events").length());
  }

  @Test
  public void undoCannotOrphanSettlement() {
    Ledger l = ledger();
    JSONObject lend = action("lend", 1000000, "MB");
    Ledger.put(lend, "person", "Nam");
    l.commit(new JSONArray().put(lend), "loan");
    JSONObject collect = action("collect", 500000, "MB");
    Ledger.put(collect, "debtId", l.array("events").optJSONObject(0).optString("id"));
    commit(l, collect);
    String before = l.data.toString();
    try {
      l.undo("loan");
      fail();
    } catch (IllegalArgumentException expected) {
    }
    assertEquals(before, l.data.toString());
  }

  @Test
  public void settlementCannotExceedOrUseWrongDebt() {
    Ledger l = ledger();
    JSONObject lend = action("lend", 1000000, "MB");
    Ledger.put(lend, "person", "Nam");
    commit(l, lend);
    JSONObject repayment = action("collect", 2000000, "MB");
    Ledger.put(repayment, "debtId", l.array("events").optJSONObject(0).optString("id"));
    try {
      commit(l, repayment);
      fail();
    } catch (IllegalArgumentException expected) {
    }
    assertEquals(1, l.array("events").length());
    Ledger.put(repayment, "type", "repay");
    Ledger.put(repayment, "amount", 500000);
    try {
      commit(l, repayment);
      fail();
    } catch (IllegalArgumentException expected) {
    }
  }

  @Test
  public void reportNeverClaimsUnverifiedBalance() {
    Ledger l = ledger();
    commit(l, action("expense", 45000, "Tiền mặt"));
    String report = l.report(LocalDate.now(), LocalDate.now());
    assertTrue(report.contains("chưa đối soát"));
    l.reconcile(l.account("Tiền mặt").optString("id"), 200000);
    assertEquals(200000, l.balance(l.account("Tiền mặt").optString("id")));
    assertTrue(l.report(LocalDate.now(), LocalDate.now()).contains("mốc đối soát"));
  }

  @Test
  public void amountAbbreviationsAndFractionalVnd() {
    assertEquals(1500000, Assistant.amount("1tr5"));
    assertEquals(50000, Assistant.amount("50k"));
    assertEquals(1500000, Assistant.amount("1,5 triệu"));
    try {
      Assistant.amount("0.5");
      fail();
    } catch (IllegalArgumentException expected) {
    }
  }

  @Test
  public void replacementFailureKeepsOriginal() {
    Ledger l = ledger();
    l.commit(new JSONArray().put(action("expense", 50000, "MB")), "old");
    String before = l.data.toString();
    try {
      l.replace("old", new JSONArray().put(action("expense", -1, "MB")), "new");
      fail();
    } catch (IllegalArgumentException expected) {
    }
    assertEquals(before, l.data.toString());
    l.replace("old", new JSONArray().put(action("expense", 70000, "MB")), "new");
    assertEquals(70000, expense(l));
  }

  @Test
  public void remindersNeverAutoPay() {
    Ledger l = ledger();
    JSONObject r =
        Ledger.obj(
            "id",
            "rent",
            "name",
            "Thuê nhà",
            "amount",
            2000000,
            "next",
            LocalDate.now().minusMonths(1).toString(),
            "period",
            "month",
            "active",
            true);
    l.array("rules").put(r);
    assertTrue(l.obligations().contains("Thuê nhà"));
    assertEquals(0, l.array("events").length());
    assertEquals(0, expense(l));
  }

  @Test
  public void futureAndFractionalEventsRejected() {
    Ledger l = ledger();
    JSONObject a = action("expense", 10, "MB");
    Ledger.put(a, "date", LocalDate.now().plusDays(1).toString());
    try {
      commit(l, a);
      fail();
    } catch (IllegalArgumentException expected) {
    }
    Ledger.put(a, "date", Ledger.today());
    Ledger.put(a, "amount", 1.5);
    try {
      commit(l, a);
      fail();
    } catch (IllegalArgumentException expected) {
    }
  }
}
