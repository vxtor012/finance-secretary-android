package vn.finance.secretary;

import static org.junit.Assert.*;

import org.json.*;
import org.junit.Test;

public class GoogleAiTest {
  private JSONObject completion(String text, String reason) {
    return Ledger.obj(
        "candidates",
        new JSONArray()
            .put(
                Ledger.obj(
                    "finishReason",
                    reason.equals("length") ? "MAX_TOKENS" : "STOP",
                    "content",
                    Ledger.obj("parts", new JSONArray().put(Ledger.obj("text", text))))));
  }

  @Test
  public void errorStatusRetainsReasonButRedactsSecret() throws Exception {
    for (int code : new int[] {401, 402, 403, 404, 429, 503}) {
      try {
        GoogleAi.decode(
            code,
            Ledger.obj(
                    "error",
                    Ledger.obj("code", code, "message", "Provider rejected AIzasecret-value"))
                .toString(),
            "AIzasecret-value");
        fail();
      } catch (GoogleAi.ApiException e) {
        assertEquals(code, e.status);
        assertTrue(e.getMessage().contains("HTTP " + code));
        assertFalse(e.getMessage().contains("secret-value"));
      }
    }
  }

  @Test
  public void errorInsideHttp200IsNotParsedAsAnswer() throws Exception {
    try {
      GoogleAi.decode(200, "{\"error\":{\"code\":429,\"message\":\"Rate limited\"}}", "");
      fail();
    } catch (GoogleAi.ApiException e) {
      assertEquals(429, e.status);
    }
  }

  @Test
  public void conversationWithoutJsonNeverCreatesActions() throws Exception {
    JSONObject out =
        Assistant.parseCompletion(completion("Chào bạn! Bạn muốn ghi khoản chi nào?", "stop"));
    assertTrue(out.optString("reply").contains("Chào"));
    assertEquals(0, out.optJSONArray("actions").length());
  }

  @Test
  public void validFencedJsonCanBeRead() throws Exception {
    JSONObject out =
        Assistant.parseCompletion(
            completion("```json\n{\"reply\":\"Đã hiểu\",\"actions\":[]}\n```", "stop"));
    assertEquals("Đã hiểu", out.optString("reply"));
  }

  @Test
  public void incompleteJsonAndTruncatedOutputFailSafely() throws Exception {
    for (String text :
        new String[] {
          "{\"reply\":\"partial", "{\"reply\":\"ok\"}", "{\"reply\":\"ok\",\"actions\":[false]}"
        }) {
      try {
        Assistant.parseCompletion(completion(text, "stop"));
        fail();
      } catch (java.io.IOException expected) {
        assertTrue(expected.getMessage().contains("Chưa ghi tiền"));
      }
    }
    try {
      Assistant.parseCompletion(completion("", "length"));
      fail();
    } catch (java.io.IOException e) {
      assertTrue(e.getMessage().contains("token"));
    }
  }

  @Test
  public void pastingBearerAndInvisibleCharactersIsNormalized() {
    assertEquals("AIzaexample", GoogleAi.normalizeKey(" \uFEFFBearer AIzaexample\u200B \n"));
    try {
      GoogleAi.normalizeKey("AIzaab cd");
      fail();
    } catch (IllegalArgumentException expected) {
    }
  }

  @Test
  public void selectedModelAndKeyActuallyReachTransport() throws Exception {
    Ledger l = new Ledger();
    Ledger.put(l.data, "apiKey", "Bearer AIzaexample");
    Ledger.put(l.data, "model", "gemma-4-31b-it");
    String before = l.data.toString();
    JSONObject out =
        Assistant.propose(
            "Ăn sáng 45k tiền mặt",
            l,
            (path, key, body) -> {
              assertEquals("models/gemma-4-31b-it:generateContent", path);
              assertEquals("AIzaexample", key);
              assertTrue(body.has("contents"));
              assertEquals(4096, body.optJSONObject("generationConfig").optInt("maxOutputTokens"));
              assertEquals(
                  "minimal",
                  body.optJSONObject("generationConfig")
                      .optJSONObject("thinkingConfig")
                      .optString("thinkingLevel"));
              return completion(
                  "{\"reply\":\"Đề"
                      + " xuất\",\"actions\":[{\"type\":\"expense\",\"amount\":45000,\"account\":\"Tiền"
                      + " mặt\"}]}",
                  "stop");
            });
    assertEquals(1, out.optJSONArray("actions").length());
    assertEquals(before, l.data.toString());
  }

  @Test
  public void providerFailureIsNeverChangedIntoOfflineHelp() throws Exception {
    Ledger l = new Ledger();
    Ledger.put(l.data, "apiKey", "AIzaexample");
    try {
      Assistant.propose(
          "xin chào",
          l,
          (path, key, body) ->
              GoogleAi.decode(
                  403,
                  "{\"error\":{\"code\":403,\"message\":\"Privacy routing restriction\"}}",
                  key));
      fail();
    } catch (GoogleAi.ApiException e) {
      assertTrue(e.getMessage().contains("Privacy routing restriction"));
      assertFalse(e.getMessage().contains("Offline:"));
    }
    assertEquals(0, l.array("events").length());
  }
}
