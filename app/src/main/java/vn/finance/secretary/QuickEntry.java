package vn.finance.secretary;

import java.util.List;
import org.json.JSONObject;

/** Whitelist submitted fields so hidden controls can never change another operation. */
final class QuickEntry {
  static final List<String> TYPES =
      List.of(
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

  static boolean category(String type) {
    return List.of("expense", "income", "card_purchase", "card_refund").contains(type);
  }

  static JSONObject action(String type, JSONObject fields) {
    if (!TYPES.contains(type)) throw new IllegalArgumentException("Nghiệp vụ không được hỗ trợ");
    JSONObject result =
        Ledger.obj(
            "type",
            type,
            "amount",
            Assistant.amount(fields.optString("amount")),
            "account",
            fields.optString("account"),
            "date",
            fields.optString("date"),
            "note",
            fields.optString("note"));
    if (category(type)) Ledger.put(result, "category", fields.optString("category"));
    if (List.of("transfer", "card_payment").contains(type))
      Ledger.put(result, "to", fields.optString("to"));
    if (type.equals("transfer"))
      Ledger.put(
          result,
          "fee",
          Assistant.amount(fields.optString("fee", "0").isBlank() ? "0" : fields.optString("fee")));
    if (List.of("lend", "borrow").contains(type)) {
      Ledger.put(result, "person", fields.optString("person"));
      Ledger.put(result, "due", fields.optString("due"));
    }
    if (List.of("collect", "repay").contains(type))
      Ledger.put(result, "debtId", fields.optString("debtId"));
    return result;
  }
}
