package vn.finance.secretary;

import java.net.*;
import java.util.*;
import java.util.regex.*;
import org.json.*;

public final class Assistant {
  public static final String DEFAULT_MODEL = "gemma-4-26b-a4b-it";

  static void migrateProvider(Ledger ledger) {
    if (!ledger.data.optString("aiProvider").equals("google")) {
      ledger.data.remove("apiKey");
      Ledger.put(ledger.data, "model", DEFAULT_MODEL);
      Ledger.put(ledger.data, "aiProvider", "google");
    }
    ChatHistory.prune(ledger);
  }

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

  interface Transport {
    JSONObject call(String path, String key, JSONObject body) throws Exception;
  }

  static Transport transport = GoogleAi::request;

  public static JSONObject propose(String text, Ledger ledger) throws Exception {
    return propose(text, ledger, transport);
  }

  static JSONObject propose(String text, Ledger ledger, Transport client) throws Exception {
    String key = GoogleAi.normalizeKey(ledger.data.optString("apiKey"));
    if (key.isBlank()) return offline(text);
    JSONArray accounts = new JSONArray();
    for (int i = 0; i < ledger.array("accounts").length(); i++) {
      JSONObject a = ledger.array("accounts").optJSONObject(i);
      if (a.optBoolean("archived")) continue;
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
    // Only bounded recent user context; never send API keys or the full ledger.
    String instruction =
        "Bạn là trợ lý tài chính Việt Nam. Chỉ trả JSON {reply:string,actions:array}. Không"
            + " markdown. Không tự tính số dư hay bịa dữ liệu. Nếu không rõ nguồn tiền, số tiền,"
            + " khoản nợ, hoặc ngày: actions=[] và hỏi lại. Hỗ trợ nhiều nghiệp vụ trong tin."
            + " actions có type"
            + " expense,income,transfer,lend,borrow,collect,repay,card_purchase,card_payment,card_refund;"
            + " amount số nguyên VND; account tên/id tài khoản; to cho chuyển/trả thẻ; fee; person"
            + " cho vay; debtId mã khoản gốc cho collect/repay; date YYYY-MM-DD; due tùy chọn;"
            + " category chỉ được chọn từ categories trong Context; không tạo danh mục mới; note."
            + " 1tr5=1500000, 50k=50000. Thu hồi/đi vay/trả nợ/chuyển tiền không phải thu chi."
            + " Không thực hiện thao tác xóa/sửa, không tạo tài khoản tự động. Nếu người dùng hỏi"
            + " báo cáo, hướng dẫn nút Báo cáo; không bịa số liệu. Context: "
            + context;
    JSONObject body =
        Ledger.obj(
            "contents",
            new JSONArray()
                .put(
                    Ledger.obj(
                        "role",
                        "user",
                        "parts",
                        new JSONArray()
                            .put(
                                Ledger.obj("text", instruction + "\nTin nhắn hiện tại: " + text)))),
            "generationConfig",
            Ledger.obj("temperature", 0, "maxOutputTokens", 4096));
    JSONArray history = new JSONArray();
    JSONArray chat = ledger.array("chat");
    for (int i = Math.max(0, chat.length() - 6); i < chat.length() - 1; i++) {
      JSONObject turn = chat.optJSONObject(i);
      if (turn.optString("role").equals("user"))
        history.put(Ledger.obj("role", "user", "content", turn.optString("text")));
    }
    if (history.length() > 0) {
      StringBuilder contextTurns = new StringBuilder();
      for (int i = 0; i < history.length(); i++)
        contextTurns.append(history.optJSONObject(i).optString("content")).append("\n");
      body.optJSONArray("contents")
          .optJSONObject(0)
          .optJSONArray("parts")
          .optJSONObject(0)
          .put(
              "text",
              instruction
                  + "\nNgữ cảnh tin nhắn trước (chỉ để hiểu câu trả lời tiếp nối):\n"
                  + contextTurns
                  + "\nTin nhắn hiện tại: "
                  + text);
    }
    String model = modelId(ledger.data.optString("model", DEFAULT_MODEL));
    if (model.startsWith("gemma-4"))
      body.optJSONObject("generationConfig")
          .put("thinkingConfig", Ledger.obj("thinkingLevel", "minimal"));
    return parseCompletion(client.call("models/" + model + ":generateContent", key, body));
  }

  static String modelId(String model) {
    String id = model.trim().replaceFirst("^models/", "");
    if (!id.matches("[A-Za-z0-9._-]+"))
      throw new IllegalArgumentException("Model ID không hợp lệ.");
    return id;
  }

  public static JSONObject parseCompletion(JSONObject response) throws Exception {
    if (response.optJSONObject("error") != null) GoogleAi.decode(200, response.toString(), "");
    JSONArray candidates = response.optJSONArray("candidates");
    if (candidates == null || candidates.length() == 0)
      throw new java.io.IOException(
          "Google chưa trả câu trả lời"
              + (response.optJSONObject("promptFeedback") == null
                  ? ". Thử lại hoặc đổi model."
                  : ": "
                      + response
                          .optJSONObject("promptFeedback")
                          .optString("blockReason", "nội dung bị chặn")));
    JSONObject choice = candidates.getJSONObject(0);
    String reason = choice.optString("finishReason");
    if (!reason.isEmpty() && !reason.equals("STOP"))
      throw new java.io.IOException(
          reason.equals("MAX_TOKENS")
              ? "Phản hồi bị cắt do giới hạn token. Chưa ghi tiền; hãy chia nhỏ tin nhắn."
              : "Google không hoàn tất câu trả lời: " + reason + ". Chưa ghi tiền.");
    JSONObject message = choice.optJSONObject("content");
    if (message == null) throw new java.io.IOException("Model trả phản hồi thiếu nội dung.");
    StringBuilder answer = new StringBuilder();
    JSONArray parts = message.optJSONArray("parts");
    if (parts != null)
      for (int i = 0; i < parts.length(); i++) {
        JSONObject part = parts.optJSONObject(i);
        if (part != null && !part.optBoolean("thought")) answer.append(part.optString("text"));
      }
    String content = answer.toString().trim();
    if (content.isEmpty() || content.equals("null"))
      throw new java.io.IOException(
          reason.equals("MAX_TOKENS")
              ? "Model đã dùng hết giới hạn token trước khi trả lời. Thử lại hoặc chọn model khác."
              : "Model trả nội dung rỗng. Thử lại hoặc đổi model.");
    if (reason.equals("MAX_TOKENS"))
      throw new java.io.IOException(
          "Phản hồi bị cắt do giới hạn token. Hãy chia tin nhắn thành ít giao dịch hơn.");
    if (content.startsWith("```"))
      content = content.replaceFirst("^```(?:json)?\\s*", "").replaceFirst("\\s*```$", "");
    int first = content.indexOf('{'), last = content.lastIndexOf('}');
    if (first >= 0 && last > first) {
      JSONObject out;
      try {
        out = new JSONObject(content.substring(first, last + 1));
      } catch (JSONException e) {
        throw new java.io.IOException(
            "Model trả cấu trúc giao dịch không hợp lệ. Chưa ghi tiền; hãy thử lại.");
      }
      if (!(out.opt("reply") instanceof String)
          || out.optString("reply").isBlank()
          || out.optJSONArray("actions") == null
          || out.optJSONArray("actions").length() > 20)
        throw new java.io.IOException(
            "Model trả cấu trúc thiếu reply/actions. Chưa ghi tiền; hãy thử lại.");
      for (int i = 0; i < out.optJSONArray("actions").length(); i++)
        if (out.optJSONArray("actions").optJSONObject(i) == null)
          throw new java.io.IOException("Đề xuất giao dịch sai định dạng. Chưa ghi tiền.");
      return out;
    }
    if (content.startsWith("{") || content.startsWith("["))
      throw new java.io.IOException("Phản hồi JSON chưa hoàn chỉnh. Chưa ghi tiền; hãy thử lại.");
    // A conversational answer is safe to display, but never inferred as a financial mutation.
    return Ledger.obj("reply", content, "actions", new JSONArray());
  }

  public static String checkConnection(String key, String model) throws Exception {
    key = GoogleAi.normalizeKey(key);
    if (key.isEmpty()) throw new IllegalArgumentException("Hãy nhập key AI Studio.");
    String id = modelId(model);
    JSONObject config = Ledger.obj("maxOutputTokens", 512);
    if (id.startsWith("gemma-4"))
      config.put("thinkingConfig", Ledger.obj("thinkingLevel", "minimal"));
    JSONObject body =
        Ledger.obj(
            "contents",
            new JSONArray()
                .put(
                    Ledger.obj(
                        "parts",
                        new JSONArray()
                            .put(Ledger.obj("text", "Chỉ trả lời: Kết nối thành công.")))),
            "generationConfig",
            config);
    parseCompletion(transport.call("models/" + id + ":generateContent", key, body));
    return "Đã kết nối · " + id;
  }

  public static List<String> models(String key) throws Exception {
    key = GoogleAi.normalizeKey(key);
    if (key.isEmpty()) throw new IllegalArgumentException("Nhập key AI Studio để tải model.");
    List<String> result = new ArrayList<>();
    String token = "";
    for (int page = 0; page < 20; page++) {
      JSONObject response =
          transport.call(
              "models?pageSize=1000"
                  + (token.isEmpty()
                      ? ""
                      : "&pageToken=" + java.net.URLEncoder.encode(token, "UTF-8")),
              key,
              null);
      JSONArray items = response.optJSONArray("models");
      if (items != null)
        for (int i = 0; i < items.length(); i++) {
          JSONObject m = items.getJSONObject(i);
          JSONArray methods = m.optJSONArray("supportedGenerationMethods");
          boolean supported = false;
          if (methods != null)
            for (int j = 0; j < methods.length(); j++)
              if (methods.optString(j).equals("generateContent")) supported = true;
          String id = m.optString("name").replaceFirst("^models/", "");
          if (supported
              && (id.startsWith("gemma-") || id.startsWith("gemini-"))
              && !id.matches(".*(image|audio|tts|live|robotics).*"))
            if (!result.contains(id)) result.add(id);
        }
      token = response.optString("nextPageToken");
      if (token.isEmpty()) break;
    }
    result.sort(
        (a, b) ->
            Integer.compare(
                a.equals(DEFAULT_MODEL) ? 0 : a.startsWith("gemma-") ? 1 : 2,
                b.equals(DEFAULT_MODEL) ? 0 : b.startsWith("gemma-") ? 1 : 2));
    if (result.isEmpty())
      throw new java.io.IOException("Không có model chat khả dụng với key này.");
    return result;
  }
}
