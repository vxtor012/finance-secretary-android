# Thư ký tài chính — Android

Ứng dụng chat tiếng Việt theo [product one-page](docs/PRODUCT_ONE_PAGE.md). Không cần backend, database server hay tài khoản ứng dụng. Người dùng tải APK, nhập API key OpenRouter và chọn model; mặc định `google/gemma-3-27b-it:free`.

## Cài đặt

1. Mở [Releases](https://github.com/vxtor012/finance-secretary-android/releases/latest), tải `finance-secretary.apk`.
2. Android 8.0 trở lên: cho phép cài ứng dụng từ nguồn tải APK khi Android hỏi.
3. Nhập API key OpenRouter. Model Gemma đã được chọn; danh sách model được tải trong Cài đặt.
4. Cho phép thông báo để nhận nhắc hạn và báo cáo. Thêm MB, MoMo, thẻ ở Nguồn tiền nếu cần. Tiền mặt đã có sẵn, không bắt buộc nhập số dư ban đầu.

## Sử dụng

- Chat “Sáng ăn phở 45k tiền mặt”, xem nguồn tiền/ngày/danh mục rồi xác nhận. Nếu thiếu dữ liệu, trợ lý hỏi lại. Nhiều giao dịch được ghi nguyên nhóm hoặc không ghi.
- Offline: `chi 50k cash ăn sáng`, `thu 10tr cash lương`. Ghi nhanh có đủ loại nghiệp vụ kể cả công nợ, chuyển khoản và thẻ.
- `báo cáo`, `tháng này tôi tiêu bao nhiêu`, `ai còn nợ tôi`, `hoàn tác`, `sửa cuối chi 70k cash ăn sáng`. Với sửa cuối, toàn bộ nhóm cuối được thay thế sau xác nhận. Lịch sử cho phép hoàn tác một nhóm cụ thể.
- Trả nợ cần mã khoản gốc trong Nhắc hạn để tránh phân bổ sai khi cùng người có nhiều khoản.
- Nghĩa vụ định kỳ chỉ trở thành giao dịch khi bạn bấm xác nhận và duyệt đề xuất. Nhắc hạn không tự ghi thanh toán. Có thể tạm dừng và kích hoạt lại.
- Đối soát số dư tại Nguồn tiền. Trước đó app chỉ báo biến động, không khẳng định tiền thực có. Mốc đối soát là số dư ở thời điểm ghi nhận; sửa giao dịch trước mốc thay đổi báo cáo nhưng không tự đổi số dư đã xác minh.
- Báo cáo theo ngày/tuần/tháng/năm hoặc kỳ tùy chọn có phân bổ chi phí, so sánh kỳ trước, dòng tiền, nợ và nghĩa vụ. Báo cáo tự động của kỳ hoàn tất có trong Cài đặt → Báo cáo tự động gần nhất.
- Sao lưu mã hóa `.fsb` bằng mật khẩu ít nhất 10 ký tự; khôi phục trên thiết bị mới. API key không có trong sao lưu. JSON xuất ra đọc được và có dữ liệu tài chính/hội thoại, cần lưu riêng tư.

## Build và release

Java 17, Android SDK platform 35 và build-tools 35.0.0. Gradle Wrapper 8.11.1 có sẵn.

```sh
./gradlew testDebugUnitTest lintDebug assembleDebug
```

Debug APK: `app/build/outputs/apk/debug/app-debug.apk`. Release cần khóa ký ổn định:

```sh
export RELEASE_STORE_FILE=/absolute/path/release.jks
export RELEASE_STORE_PASSWORD=your-password
export RELEASE_KEY_PASSWORD=your-password
export RELEASE_KEY_ALIAS=finance
./gradlew assembleRelease
```

GitHub Actions kiểm tra mỗi push/PR. Khi push tag `v*`, workflow build APK release đã ký và đính kèm APK cùng SHA256 vào GitHub Release. Repo cần hai Actions secrets: `ANDROID_KEYSTORE_BASE64` (keystore base64) và `ANDROID_KEYSTORE_PASSWORD`. Không commit khóa ký. Tăng `versionCode` và `versionName` trong `app/build.gradle` cho bản mới, giữ nguyên khóa ký để cài cập nhật.

Kiểm thử tích hợp Android thực tế: `./gradlew assembleDebug assembleDebugAndroidTest`, cài hai APK debug lên emulator Android 35 rồi chạy `adb shell am instrument -w vn.finance.secretary.test/vn.finance.secretary.SmokeInstrumentation`. GitHub Actions chạy tự động bộ kiểm tra này.

## Kiến trúc và quyền riêng tư

- Android Java, giao diện native, không có WebView/remote frontend.
- `Ledger`: VND số nguyên, event ledger, kiểm tra nghiệp vụ, replay, batch nguyên tử, chống trùng request ID, sửa/hoàn tác có audit trail.
- `SecureStore`: AES-GCM + Android Keystore; atomic file thay thế nguyên trạng thái. Android cloud backup bị tắt; không log API key hay dữ liệu tài chính. Sao lưu di chuyển dùng PBKDF2-HMAC-SHA256 210.000 vòng + AES-GCM.
- `Assistant`: HTTPS OpenRouter; model chỉ trích xuất đề xuất. App tự xác thực/tính toán. Chỉ gửi tin nhắn hiện tại, tên/bí danh tài khoản, danh mục và thông tin công nợ/nghĩa vụ; không gửi toàn bộ lịch sử/sổ hay mốc số dư.
- `ReminderReceiver`: lịch thông báo theo múi giờ máy và giờ người dùng chọn; báo cáo tuần/tháng/năm cho kỳ đã kết thúc. Receiver chỉ đọc sổ, tránh xung đột ghi với Activity; metadata kỳ báo cáo không chứa số liệu tài chính.

## Giới hạn hiện tại

Phiên bản đầu là app cho một người trên thiết bị Android, chưa đồng bộ nhiều thiết bị hay ngân hàng. Thẻ hiển thị dư nợ, sao kê theo giao dịch từng kỳ và ngày sao kê/hạn trả; không nhập hoặc khớp sao kê ngân hàng tự động. Số dư quá khứ trong báo cáo là dòng tiền kỳ; phần nguồn tiền thể hiện số dư hiện tại từ mốc đối soát, không giả làm số dư cuối kỳ cũ. Thông báo Android là lịch không chính xác tuyệt đối; force-stop app sẽ ngừng nhắc đến khi mở lại. App yêu cầu xác thực khóa màn hình khi mở nếu thiết bị đã đặt khóa; chưa có PIN riêng. AI miễn phí phụ thuộc OpenRouter; app không tự chuyển sang model tính phí.

Đặc tả gốc loại app native khỏi MVP; yêu cầu triển khai Android trong cuộc trao đổi này thay thế giới hạn đó. Các chức năng ngoài phạm vi khác giữ theo tài liệu gốc.
