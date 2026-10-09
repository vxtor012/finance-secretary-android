package vn.finance.secretary;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import org.json.*;

/**
 * HTTPS adapter with actionable, redacted errors, including error bodies returned with HTTP 200.
 */
public final class GoogleAi {
  public static final class ApiException extends IOException {
    public final int status;

    ApiException(int status, String message) {
      super(message);
      this.status = status;
    }
  }

  public static String normalizeKey(String input) {
    String key = input == null ? "" : input.strip().replace("\u200B", "").replace("\uFEFF", "");
    if (key.regionMatches(true, 0, "Bearer ", 0, 7)) key = key.substring(7).strip();
    if (key.contains("\n") || key.contains("\r") || key.contains(" "))
      throw new IllegalArgumentException(
          "API key có khoảng trắng hoặc xuống dòng ở giữa. Hãy dán lại toàn bộ key.");
    if (key.startsWith("sk-or-"))
      throw new IllegalArgumentException("Hãy nhập key Google AI Studio thay cho key OpenRouter.");
    return key;
  }

  public static String safe(String message, String key) {
    if (message == null || message.isBlank()) return "Không có chi tiết từ nhà cung cấp.";
    if (key != null && !key.isEmpty()) message = message.replace(key, "[key đã ẩn]");
    message = message.replaceAll("AIza[A-Za-z0-9_-]+", "[key đã ẩn]");
    return message.length() > 600 ? message.substring(0, 600) + "…" : message;
  }

  public static JSONObject decode(int status, String raw, String key) throws IOException {
    JSONObject response;
    try {
      response = new JSONObject(raw);
    } catch (JSONException e) {
      if (status < 200 || status >= 300) throw error(status, "", key);
      throw new IOException("Google trả phản hồi không phải JSON. Vui lòng thử lại.");
    }
    JSONObject problem = response.optJSONObject("error");
    if (status < 200 || status >= 300 || problem != null)
      throw error(
          problem == null ? status : problem.optInt("code", status),
          problem == null ? "" : problem.optString("message"),
          key);
    return response;
  }

  private static ApiException error(int code, String detail, String key) {
    String hint =
        switch (code) {
          case 400 -> "Key hoặc yêu cầu không hợp lệ. Kiểm tra key AI Studio và model đã chọn.";
          case 401 ->
              "API key không hợp lệ, đã bị thu hồi hoặc chưa được lưu. Dán lại key trong Cài đặt.";
          case 402 ->
              "Key hoặc tài khoản không đủ hạn mức. Kiểm tra hạn mức AI Studio hoặc chọn model"
                  + " miễn phí.";
          case 403 ->
              "Google từ chối quyền truy cập. Kiểm tra quyền Gemini API của key trên AI Studio.";
          case 404 -> "Model chưa khả dụng với key này. Tải lại danh sách model.";
          case 408, 504 -> "Model phản hồi quá chậm. Thử lại hoặc chọn model khác.";
          case 429 ->
              "Đã chạm hạn mức Google AI. Chờ rồi thử lại, hoặc kiểm tra quota trong AI Studio.";
          case 502, 503 ->
              "Provider của model đang lỗi hoặc không khả dụng. Thử lại hoặc đổi model.";
          default -> "Không hoàn thành được yêu cầu Google AI. Thử lại hoặc kiểm tra kết nối.";
        };
    return new ApiException(
        code,
        "Google AI HTTP "
            + code
            + "\n"
            + hint
            + (detail.isBlank() ? "" : "\nChi tiết: " + safe(detail, key)));
  }

  public static JSONObject request(String path, String inputKey, JSONObject body) throws Exception {
    String key = normalizeKey(inputKey);
    HttpURLConnection c =
        (HttpURLConnection)
            new URL("https://generativelanguage.googleapis.com/v1beta/" + path).openConnection();
    c.setConnectTimeout(15000);
    c.setReadTimeout(90000);
    c.setRequestProperty("Content-Type", "application/json; charset=utf-8");
    if (!key.isEmpty()) c.setRequestProperty("x-goog-api-key", key);
    try {
      if (body != null) {
        c.setRequestMethod("POST");
        c.setDoOutput(true);
        try (OutputStream out = c.getOutputStream()) {
          out.write(body.toString().getBytes(StandardCharsets.UTF_8));
        }
      }
      int status = c.getResponseCode();
      InputStream stream = status >= 200 && status < 300 ? c.getInputStream() : c.getErrorStream();
      String raw = "{}";
      if (stream != null)
        try (InputStream in = stream) {
          raw = new String(Streams.readLimited(in, 2_000_000), StandardCharsets.UTF_8);
        }
      return decode(status, raw, key);
    } catch (SocketTimeoutException e) {
      throw new IOException("Kết nối/model quá thời gian chờ. Kiểm tra mạng hoặc thử model khác.");
    } catch (UnknownHostException | ConnectException e) {
      throw new IOException("Không kết nối được Google. Kiểm tra internet hoặc VPN trên thiết bị.");
    } finally {
      c.disconnect();
    }
  }
}
