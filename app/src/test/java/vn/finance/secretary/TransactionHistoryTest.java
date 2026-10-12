package vn.finance.secretary;

import static org.junit.Assert.*;

import java.time.LocalDate;
import java.util.*;
import org.json.*;
import org.junit.Test;

public class TransactionHistoryTest {
  private JSONObject event(String id, String date) {
    return Ledger.obj("id", id, "date", date, "type", "expense", "account", "cash", "active", true);
  }

  @Test
  public void sortsRetrospectiveEntriesAndPaginatesThreePopulatedDates() {
    JSONArray events =
        new JSONArray()
            .put(event("recent-first", "2026-10-12"))
            .put(event("old", "2026-09-01"))
            .put(event("yesterday", "2026-10-11"))
            .put(event("recent-last", "2026-10-12"))
            .put(event("third", "2026-10-10"));
    List<List<JSONObject>> pages = TransactionHistory.pages(events, null, null, "", "", true);
    assertEquals(2, pages.size());
    assertEquals("recent-last", pages.get(0).get(0).optString("id"));
    assertEquals("recent-first", pages.get(0).get(1).optString("id"));
    assertEquals(4, pages.get(0).size());
    assertEquals("old", pages.get(1).get(0).optString("id"));
  }

  @Test
  public void busyDayIsSplitWithoutDroppingOrDuplicatingRows() {
    JSONArray events = new JSONArray();
    for (int i = 0; i < 75; i++) events.put(event("id-" + i, "2026-10-12"));
    List<List<JSONObject>> pages = TransactionHistory.pages(events, null, null, "", "", true);
    assertEquals(3, pages.size());
    assertEquals(30, pages.get(0).size());
    assertEquals(30, pages.get(1).size());
    assertEquals(15, pages.get(2).size());
    Set<String> ids = new HashSet<>();
    for (List<JSONObject> page : pages)
      for (JSONObject row : page) assertTrue(ids.add(row.optString("id")));
    assertEquals(75, ids.size());
  }

  @Test
  public void combinesInclusiveDatesAccountDestinationTypeAndUndoFilters() {
    JSONObject transfer = event("transfer", "2026-10-11");
    Ledger.put(transfer, "type", "transfer");
    Ledger.put(transfer, "to", "bank");
    JSONObject undone = event("undone", "2026-10-12");
    Ledger.put(undone, "active", false);
    JSONArray events =
        new JSONArray()
            .put(event("outside", "2026-10-10"))
            .put(transfer)
            .put(undone)
            .put(event("today", "2026-10-12"));
    LocalDate start = LocalDate.parse("2026-10-11"), end = LocalDate.parse("2026-10-12");
    List<List<JSONObject>> pages =
        TransactionHistory.pages(events, start, end, "bank", "transfer", false);
    assertEquals(1, pages.get(0).size());
    assertEquals("transfer", pages.get(0).get(0).optString("id"));
    assertEquals(2, TransactionHistory.pages(events, start, end, "", "", false).get(0).size());
    assertEquals(3, TransactionHistory.pages(events, start, end, "", "", true).get(0).size());
    assertTrue(TransactionHistory.pages(events, start, end, "missing", "", true).isEmpty());
    assertTrue(TransactionHistory.pages(new JSONArray(), null, null, "", "", true).isEmpty());
  }

  @Test
  public void destinationFilterResolvesRecordedAliasThroughLedgerEffects() {
    Ledger ledger = new Ledger();
    ledger.addAccount("Ngân hàng", "bank", "bank");
    ledger.commit(
        new JSONArray()
            .put(
                Ledger.obj(
                    "type",
                    "transfer",
                    "account",
                    "cash",
                    "to",
                    "bank",
                    "amount",
                    1000000,
                    "date",
                    Ledger.today())),
        "transfer-alias");
    String destinationId = ledger.account("bank").optString("id");
    List<List<JSONObject>> pages =
        TransactionHistory.pages(ledger.array("events"), null, null, destinationId, "", true);
    assertEquals(1, pages.size());
    assertEquals(1, pages.get(0).size());
  }
}
