package vn.finance.secretary;

import org.json.JSONArray;

/** Chat is a short-lived view; financial events and pending proposals remain separate. */
final class ChatHistory {
  static final int LIMIT = 5;

  static void prune(Ledger ledger) {
    while (ledger.array("chat").length() > LIMIT) ledger.array("chat").remove(0);
  }

  static void clear(Ledger ledger) {
    Ledger.put(ledger.data, "chat", new JSONArray());
  }
}
