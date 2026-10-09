package vn.finance.secretary;

import java.io.*;

final class Streams {
  static byte[] readLimited(InputStream input, int limit) throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    byte[] buffer = new byte[8192];
    int n;
    while ((n = input.read(buffer)) != -1) {
      if (out.size() + n > limit) throw new IOException("Dữ liệu vượt giới hạn");
      out.write(buffer, 0, n);
    }
    return out.toByteArray();
  }
}
