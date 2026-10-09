package vn.finance.secretary;

import static org.junit.Assert.*;

import org.json.*;
import org.junit.Test;

public class GoogleMigrationTest {
  @Test
  public void migrationReplacesOnlyProviderConfigurationAndPrunesChat() {
    Ledger l = new Ledger();
    l.commit(
        new JSONArray()
            .put(
                Ledger.obj(
                    "type",
                    "expense",
                    "amount",
                    50000,
                    "account",
                    "cash",
                    "date",
                    Ledger.today(),
                    "category",
                    "Sinh hoạt/Khác")),
        "fixture");
    String events = l.array("events").toString(), accounts = l.array("accounts").toString();
    Ledger.put(l.data, "apiKey", "sk-or-old");
    Ledger.put(l.data, "model", "google/gemma-4-31b-it:free");
    for (int i = 0; i < 12; i++)
      l.array("chat").put(Ledger.obj("role", "user", "text", "message " + i));
    Ledger.put(l.data, "pending", Ledger.obj("requestId", "pending-fixture"));
    Assistant.migrateProvider(l);
    assertFalse(l.data.has("apiKey"));
    assertEquals(Assistant.DEFAULT_MODEL, l.data.optString("model"));
    assertEquals(5, l.array("chat").length());
    assertEquals("message 7", l.array("chat").optJSONObject(0).optString("text"));
    assertEquals(events, l.array("events").toString());
    assertEquals(accounts, l.array("accounts").toString());
    ChatHistory.clear(l);
    assertEquals(0, l.array("chat").length());
    assertTrue(l.data.has("pending"));
    assertEquals(events, l.array("events").toString());
    Ledger.put(l.data, "apiKey", "AIza-fixture");
    Ledger.put(l.data, "model", "gemma-4-31b-it");
    Assistant.migrateProvider(l);
    assertEquals("AIza-fixture", l.data.optString("apiKey"));
    assertEquals("gemma-4-31b-it", l.data.optString("model"));
  }

  @Test
  public void modelCatalogUsesKeyAndPaginationAndFiltersNonChatModels() throws Exception {
    Assistant.Transport previous = Assistant.transport;
    try {
      Assistant.transport =
          (path, key, body) -> {
            assertEquals("AIza-fixture", key);
            assertNull(body);
            if (!path.contains("pageToken"))
              return Ledger.obj(
                  "models",
                  new JSONArray()
                      .put(
                          Ledger.obj(
                              "name",
                              "models/gemini-embedding-001",
                              "supportedGenerationMethods",
                              new JSONArray().put("embedContent")))
                      .put(
                          Ledger.obj(
                              "name",
                              "models/gemini-image",
                              "supportedGenerationMethods",
                              new JSONArray().put("generateContent"))),
                  "nextPageToken",
                  "next page");
            assertTrue(path.contains("next+page"));
            return Ledger.obj(
                "models",
                new JSONArray()
                    .put(
                        Ledger.obj(
                            "name",
                            "models/" + Assistant.DEFAULT_MODEL,
                            "supportedGenerationMethods",
                            new JSONArray().put("generateContent"))));
          };
      assertEquals(java.util.List.of(Assistant.DEFAULT_MODEL), Assistant.models("AIza-fixture"));
    } finally {
      Assistant.transport = previous;
    }
  }

  @Test
  public void thoughtPartsAreNotDisplayedAndBlockedOutputNeverCreatesActions() throws Exception {
    JSONObject response =
        Ledger.obj(
            "candidates",
            new JSONArray()
                .put(
                    Ledger.obj(
                        "finishReason",
                        "STOP",
                        "content",
                        Ledger.obj(
                            "parts",
                            new JSONArray()
                                .put(Ledger.obj("thought", true, "text", "private reasoning"))
                                .put(Ledger.obj("text", "Chào bạn"))))));
    assertEquals("Chào bạn", Assistant.parseCompletion(response).optString("reply"));
    response.optJSONArray("candidates").optJSONObject(0).put("finishReason", "SAFETY");
    try {
      Assistant.parseCompletion(response);
      fail();
    } catch (java.io.IOException e) {
      assertTrue(e.getMessage().contains("SAFETY"));
    }
    try {
      Assistant.modelId("model/../../other");
      fail();
    } catch (IllegalArgumentException expected) {
    }
    try {
      GoogleAi.normalizeKey("sk-or-old");
      fail();
    } catch (IllegalArgumentException expected) {
    }
  }
}
