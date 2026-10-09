package vn.finance.secretary;

import android.content.Context;
import android.security.keystore.*;
import android.util.AtomicFile;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;
import org.json.JSONObject;

/** Entire private state encrypted with a non-exportable device Keystore key. */
public final class SecureStore {
  private final AtomicFile file;
  private final javax.crypto.SecretKey key;

  public SecureStore(Context context) throws Exception {
    file = new AtomicFile(new File(context.getFilesDir(), "ledger.enc"));
    KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
    ks.load(null);
    if (!ks.containsAlias("finance-data")) {
      KeyGenerator g = KeyGenerator.getInstance("AES", "AndroidKeyStore");
      g.init(
          new KeyGenParameterSpec.Builder(
                  "finance-data", KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
              .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
              .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
              .build());
      g.generateKey();
    }
    key = (javax.crypto.SecretKey) ks.getKey("finance-data", null);
  }

  public JSONObject load() throws Exception {
    if (!file.getBaseFile().exists()) return new JSONObject();
    byte[] raw = file.readFully();
    if (raw.length < 29) throw new IOException("Dữ liệu hỏng");
    Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
    c.init(
        Cipher.DECRYPT_MODE,
        key,
        new GCMParameterSpec(128, java.util.Arrays.copyOfRange(raw, 0, 12)));
    return new JSONObject(new String(c.doFinal(raw, 12, raw.length - 12), StandardCharsets.UTF_8));
  }

  public void save(JSONObject data) throws Exception {
    Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
    c.init(Cipher.ENCRYPT_MODE, key);
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    bytes.write(c.getIV());
    bytes.write(c.doFinal(data.toString().getBytes(StandardCharsets.UTF_8)));
    FileOutputStream stream = null;
    try {
      stream = file.startWrite();
      stream.write(bytes.toByteArray());
      file.finishWrite(stream);
    } catch (Exception e) {
      if (stream != null) file.failWrite(stream);
      throw e;
    }
  }

  public static byte[] backup(JSONObject state, String password) throws Exception {
    if (password.length() < 10)
      throw new IllegalArgumentException("Mật khẩu sao lưu cần ít nhất 10 ký tự");
    JSONObject clean = new JSONObject(state.toString());
    clean.remove("apiKey");
    clean.remove("pending");
    byte[] salt = new byte[16];
    new java.security.SecureRandom().nextBytes(salt);
    Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
    c.init(Cipher.ENCRYPT_MODE, backupKey(password, salt));
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    out.write(new byte[] {70, 83, 66, 49});
    out.write(salt);
    out.write(c.getIV());
    out.write(c.doFinal(clean.toString().getBytes(StandardCharsets.UTF_8)));
    return out.toByteArray();
  }

  public static JSONObject restore(byte[] bytes, String password) throws Exception {
    if (bytes.length < 49 || bytes[0] != 70 || bytes[1] != 83 || bytes[2] != 66 || bytes[3] != 49)
      throw new IllegalArgumentException("Sai định dạng sao lưu");
    Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
    c.init(
        Cipher.DECRYPT_MODE,
        backupKey(password, java.util.Arrays.copyOfRange(bytes, 4, 20)),
        new GCMParameterSpec(128, java.util.Arrays.copyOfRange(bytes, 20, 32)));
    JSONObject state =
        new JSONObject(new String(c.doFinal(bytes, 32, bytes.length - 32), StandardCharsets.UTF_8));
    if (state.optInt("schemaVersion", 1) != 1)
      throw new IllegalArgumentException("Phiên bản sao lưu không hỗ trợ");
    return state;
  }

  private static javax.crypto.SecretKey backupKey(String password, byte[] salt) throws Exception {
    return new javax.crypto.spec.SecretKeySpec(
        SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            .generateSecret(
                new javax.crypto.spec.PBEKeySpec(password.toCharArray(), salt, 210000, 256))
            .getEncoded(),
        "AES");
  }
}
