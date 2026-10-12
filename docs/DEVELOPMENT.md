# Build và kiểm thử từ mã nguồn

[Mục lục](README.md) · [Đóng góp](../CONTRIBUTING.md) · [Ký release](RELEASING.md)

## Môi trường

Các phiên bản dưới đây lấy từ cấu hình repo, không phải yêu cầu cài bản mới nhất:

| Thành phần | Phiên bản |
|---|---|
| JDK | 17 |
| Gradle Wrapper | 8.11.1 |
| Android Gradle Plugin | 8.9.2 |
| Android SDK Platform / compileSdk / targetSdk | 35 |
| Android SDK Build Tools | 35.0.0 |
| Android tối thiểu / minSdk | 26 (Android 8.0) |

Cài Git, JDK 17 và Android Studio có SDK Manager, hoặc Android command-line tools. Trong SDK Manager, cài **Android SDK Platform 35**, **Android SDK Build-Tools 35.0.0**, **Android SDK Platform-Tools**. Cài emulator/system image nếu muốn kiểm thử trên máy ảo.

Lần build đầu cần mạng để tải Gradle và dependency từ Google/Maven Central. Không cần backend, database, Node.js, Python hoặc key Google để build và chạy unit tests. Thư mục kỹ năng agent không phải dependency của ứng dụng.

## Lấy mã nguồn

```sh
git clone https://github.com/vxtor012/finance-secretary-android.git
cd finance-secretary-android
```

### Android Studio

Mở thư mục repo, chọn Gradle JDK 17 trong thiết lập Gradle rồi sync. Chọn module `app`, thiết bị Android 8.0+ và chạy cấu hình app. Android Studio thường tự tạo `local.properties` để chỉ vị trí SDK.

### Windows PowerShell

Thiết lập `JAVA_HOME` tới JDK 17 và `ANDROID_HOME` tới SDK của bạn. Ví dụ SDK ở vị trí mặc định:

```powershell
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
$env:Path = "$env:ANDROID_HOME\platform-tools;$env:Path"
java -version
.\gradlew.bat --version
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug
```

Nếu `java -version` không phải 17, sửa `JAVA_HOME`/PATH hoặc Gradle JDK trước khi build. Biến môi trường đặt trong cửa sổ PowerShell chỉ áp dụng cho phiên đó.

### macOS/Linux

Thiết lập `JAVA_HOME` tới JDK 17 và `ANDROID_HOME` tới Android SDK đã cài; thêm `$ANDROID_HOME/platform-tools` vào PATH.

```sh
chmod +x gradlew
java -version
./gradlew --version
./gradlew testDebugUnitTest lintDebug assembleDebug
```

Thay cho `ANDROID_HOME`, có thể tạo `local.properties` ở gốc repo với đường dẫn SDK thực tế, dùng dấu `/` trên Windows:

```properties
sdk.dir=C:/Users/YOUR_USER/AppData/Local/Android/Sdk
```

Không commit `local.properties`: đây là cấu hình riêng của máy.

## Kết quả build và cài debug

| Kết quả | Đường dẫn |
|---|---|
| Debug APK | `app/build/outputs/apk/debug/app-debug.apk` |
| Unit-test report | `app/build/reports/tests/testDebugUnitTest/index.html` |
| Lint report | `app/build/reports/lint-results-debug.html` |

Bật Developer options và USB debugging trên điện thoại, chấp nhận kết nối máy tính; hoặc khởi động emulator. Với một thiết bị đã kết nối:

```sh
adb devices
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Nếu có nhiều thiết bị, thêm `-s DEVICE_SERIAL` ngay sau `adb`. Dùng dữ liệu giả khi phát triển. App debug dùng cùng application ID `vn.finance.secretary` với release: APK khác chữ ký không thể cài đè. Sao lưu `.fsb` trước khi gỡ app hoặc đổi loại APK; ưu tiên emulator riêng cho debug.

## Kiểm thử

`testDebugUnitTest` kiểm tra nghiệp vụ sổ, nhóm giao dịch nguyên tử, chống trùng, công nợ/thẻ và xử lý phản hồi Google. `lintDebug` kiểm tra Android tĩnh. Các unit tests dùng phản hồi AI mô phỏng, không chứng minh model thật hoạt động với key của bạn.

Kiểm thử tích hợp dùng runner riêng `SmokeInstrumentation`, không phải runner AndroidX mặc định. Khởi động emulator Android 35, rồi chạy:

```powershell
.\gradlew.bat assembleDebug assembleDebugAndroidTest
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell pm grant vn.finance.secretary android.permission.POST_NOTIFICATIONS
adb shell am instrument -w vn.finance.secretary.test/vn.finance.secretary.SmokeInstrumentation
```

Trên macOS/Linux thay lệnh Gradle bằng `./gradlew`. Runner thao tác app và dữ liệu kiểm thử; chạy trên emulator/thiết bị chuyên dùng cho test. CI Android 35 kiểm tra kết quả có `PASS: startup`; xem toàn bộ đầu ra để chẩn đoán nếu runner thất bại.

[Workflow Android](../.github/workflows/android.yml) chạy khi push `main`, pull request và chạy thủ công. Các job `checks` và `device-test` lưu APK/report/log làm artifact. Không cần secrets ký release cho các job kiểm tra.

## Lỗi môi trường thường gặp

| Lỗi | Hướng xử lý |
|---|---|
| SDK location not found | Đặt `ANDROID_HOME` hoặc `sdk.dir` đúng thư mục SDK. |
| Thiếu platform/build tools | Cài Platform 35 và Build Tools 35.0.0, chấp nhận SDK licenses. |
| Sai phiên bản Java | Kiểm tra `gradlew --version`, `JAVA_HOME` và Gradle JDK của Android Studio. |
| Không tải được dependency | Kiểm tra mạng/proxy tới Google Maven, Maven Central và Gradle distributions. |
| Permission denied với gradlew | Chạy `chmod +x gradlew` trên macOS/Linux. |
| adb không nhận máy | Cài Platform-Tools, bật USB debugging, xác nhận quyền; kiểm tra `adb devices`. |
| INSTALL_FAILED_UPDATE_INCOMPATIBLE | APK khác chữ ký; sao lưu trước khi gỡ bản đang có hoặc dùng emulator riêng. |
| assembleRelease lỗi ký | Debug không cần keystore; release cần cấu hình trong [RELEASING](RELEASING.md). |
