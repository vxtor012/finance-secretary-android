package vn.finance.secretary;

import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.*;
import org.json.*;

public final class Assistant {
  public static final String DEFAULT_MODEL = "google/gemma-3-27b-it:free";

  public static long amount(String input) {
    String s =
        input
            .toLowerCase(Locale.ROOT)
            .replace(" ", "")
            .replace("triệu", "tr")
            .replace("nghìn", "k")
            .replace("ngàn", "k")
            .replace("đ", "")
            .replace("₫", "");
    Matcher m = Pattern.compile("^(\\d+(?:[.,]\\d+)?)(tr|k)?(\\d+)?$").matcher(s);
    if (!m.matches())
      throw new IllegalArgumentException("Số tiền không rõ. Ví dụ: 50k, 1tr5, 1500000");
    java.math.BigDecimal n = new java.math.BigDecimal(m.group(1).replace(',', '.'));
    long mult = "tr".equals(m.group(2)) ? 1000000 : "k".equals(m.group(2)) ? 1000 : 1;
    if (m.group(3) != null) n = n.add(new java.math.BigDecimal("0." + m.group(3)));
    try {
      return n.multiply(java.math.BigDecimal.valueOf(mult)).longValueExact();
    } catch (Exception e) {
      throw new IllegalArgumentException("Số tiền phải là số nguyên VND");
    }
  }

  public static JSONObject offline(String text) {
    String[] p = text.trim().split("\\s+", 4);
    if (p.length >= 3 && (p[0].equalsIgnoreCase("chi") || p[0].equalsIgnoreCase("thu")))
      return Ledger.obj(
          "actions",
          new JSONArray()
              .put(
                  Ledger.obj(
                      "type",
                      p[0].equalsIgnoreCase("chi") ? "expense" : "income",
                      "amount",
                      amount(p[1]),
                      "account",
                      p[2],
                      "date",
                      Ledger.today(),
                      "note",
                      p.length > 3 ? p[3] : "",
                      "category",
                      p[0].equalsIgnoreCase("chi") ? "Sinh hoạt/Khác" : "Thu nhập/Khác")),
          "reply",
          "Đề xuất theo lệnh offline. Kiểm tra nguồn tiền và danh mục trước khi xác nhận.");
    throw new IllegalArgumentException(
        "Offline: chi 50k cash ăn sáng; thu 10tr cash lương; báo cáo; hoàn tác. Dùng biểu mẫu Ghi"
            + " nhanh cho chuyển tiền, công nợ và thẻ.");
  }

  public static JSONObject propose(String text, Ledger ledger) throws Exception {
    String key = ledger.data.optString("apiKey");
    if (key.isBlank()) return offline(text);
    JSONArray accounts = new JSONArray();
    for (int i = 0; i < ledger.array("accounts").length(); i++) {
      JSONObject a = ledger.array("accounts").optJSONObject(i);
      accounts.put(
          Ledger.obj(
              "id",
              a.optString("id"),
              "name",
              a.optString("name"),
              "aliases",
              a.optString("aliases"),
              "type",
              a.optString("type")));
    }
    JSONObject context =
        Ledger.obj(
            "today",
            Ledger.today(),
            "accounts",
            accounts,
            "categories",
            ledger.array("categories"),
            "debts",
            ledger.obligations());
    // Never send history, API keys or full ledger to the provider.
    String instruction =
        "Bạn là trợ lý tài chính Việt Nam. Chỉ trả JSON {reply:string,actions:array}. Không"
            + " markdown. Không tự tính số dư hay bịa dữ liệu. Nếu không rõ nguồn tiền, số tiền,"
            + " khoản nợ, hoặc ngày: actions=[] và hỏi lại. Hỗ trợ nhiều nghiệp vụ trong tin."
            + " actions có type"
            + " expense,income,transfer,lend,borrow,collect,repay,card_purchase,card_payment,card_refund;"
            + " amount số nguyên VND; account tên/id tài khoản; to cho chuyển/trả thẻ; fee; person"
            + " cho vay; debtId mã khoản gốc cho collect/repay; date YYYY-MM-DD; due tùy chọn;"
            + " category hai cấp; note. 1tr5=1500000, 50k=50000. Thu hồi/đi vay/trả nợ/chuyển tiền"
            + " không phải thu chi. Không thực hiện thao tác xóa/sửa, không tạo tài khoản tự động."
            + " Nếu người dùng hỏi báo cáo, hướng dẫn nút Báo cáo; không bịa số liệu. Context: "
            + context;
    JSONObject body =
        Ledger.obj(
            "model",
            ledger.data.optString("model", DEFAULT_MODEL),
            "temperature",
            0,
            "max_tokens",
            1800,
            "messages",
            new JSONArray()
                .put(
                    Ledger.obj(
                        "role",
                        "user",
                        "content",
                        instruction + "\nTin nhắn người dùng: " + text)));
    JSONObject response = request("chat/completions", key, body);
    String content =
        response
            .getJSONArray("choices")
            .getJSONObject(0)
            .getJSONObject("message")
            .getString("content")
            .trim();
    if (content.startsWith("```"))
      content = content.replaceFirst("^```(?:json)?\\s*", "").replaceFirst("\\s*```$", "");
    JSONObject out = new JSONObject(content);
    if (out.optJSONArray("actions") == null || out.optJSONArray("actions").length() > 20)
      throw new IllegalArgumentException("Model trả dữ liệu không hợp lệ");
    return out;
  }

  public static List<String> models() throws Exception {
    JSONArray items = request("models", "", null).getJSONArray("data");
    List<String> result = new ArrayList<>();
    result.add(DEFAULT_MODEL);
    for (int i = 0; i < items.length(); i++) {
      String id = items.getJSONObject(i).getString("id");
      if (!result.contains(id)) result.add(id);
    }
    return result;
  }

  private static JSONObject request(String path, String key, JSONObject body) throws Exception {
    HttpURLConnection c =
        (HttpURLConnection) new URL("https://openrouter.ai/api/v1/" + path).openConnection();
    c.setConnectTimeout(15000);
    c.setReadTimeout(60000);
    c.setRequestProperty("Content-Type", "application/json");
    if (!key.isBlank()) c.setRequestProperty("Authorization", "Bearer " + key);
    c.setRequestProperty("X-Title", "Finance Secretary Android");
    try {
      if (body != null) {
        c.setRequestMethod("POST");
        c.setDoOutput(true);
        try (var out = c.getOutputStream()) {
          out.write(body.toString().getBytes(StandardCharsets.UTF_8));
        }
      }
      int status = c.getResponseCode();
      if (status != 200)
        throw new java.io.IOException(
            "OpenRouter HTTP " + status + ". Kiểm tra key, hạn mức hoặc đổi model.");
      try (var in = c.getInputStream()) {
        return new JSONObject(
            new String(Streams.readLimited(in, 2_000_000), StandardCharsets.UTF_8));
      }
    } finally {
      c.disconnect();
    }
  }
}
