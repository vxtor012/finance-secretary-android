# Thư ký tài chính

Ứng dụng Android mã nguồn mở để ghi sổ tài chính cá nhân bằng tiếng Việt. Ghi thu chi qua chat hoặc biểu mẫu, quản lý nguồn tiền, công nợ và thẻ tín dụng, xem báo cáo và nhận nhắc hạn.

**Android 8.0+ · Java 17 · MIT · Không cần máy chủ riêng**

[Tải APK](https://github.com/vxtor012/finance-secretary-android/releases/latest) · [Hướng dẫn sử dụng](docs/USER_GUIDE.md) · [Build từ mã nguồn](docs/DEVELOPMENT.md) · [English](README.en.md)

## Bắt đầu trong vài phút

1. Tải `finance-secretary.apk` từ **Releases** và cài trên Android. Cho phép cài từ nguồn tải APK nếu Android yêu cầu.
2. Mở app. Có sẵn nguồn **Tiền mặt**; chưa cần nhập số dư ban đầu.
3. Trong **Chat**, thử `chi 45k cash ăn sáng` — không cần mạng hoặc API key.
4. Kiểm tra số tiền, ngày, nguồn tiền và danh mục trong đề xuất, rồi xác nhận. Giao dịch chỉ được ghi sau bước này.
5. Mở **Sổ → Giao dịch** và **Báo cáo** để xem kết quả.

Muốn chat tự nhiên như “Sáng ăn phở 45k tiền mặt”, vào **Cài đặt → Kết nối AI**, nhập key của bạn từ [Google AI Studio](https://aistudio.google.com/apikey), chọn model và bấm **Kiểm tra kết nối**. Xem [hướng dẫn API key của Google](https://ai.google.dev/gemini-api/docs/api-key). Key/model tự lưu; không đưa key vào mã nguồn. Model mặc định trong repo là `gemma-4-26b-a4b-it`; khả năng sử dụng phụ thuộc key và dịch vụ Google.

> Khi cập nhật, cài đè APK có cùng chữ ký để giữ dữ liệu. Trước khi gỡ app, xóa dữ liệu hoặc đổi điện thoại, tạo sao lưu `.fsb` và giữ mật khẩu.

## Tính năng

| Nhu cầu | Cách hỗ trợ |
|---|---|
| Ghi thu chi | Chat tiếng Việt có AI; lệnh thu/chi và biểu mẫu Ghi nhanh dùng được offline |
| Nguồn tiền | Tiền mặt, ngân hàng, ví, tiết kiệm, thẻ; bí danh và đối soát số dư |
| Chuyển tiền | Chuyển nội bộ không tính là thu/chi; phí tính riêng |
| Công nợ | Cho vay, đi vay, thu/trả từng phần, khoản không có hạn |
| Thẻ tín dụng | Mua bằng thẻ, trả thẻ, hoàn tiền, ngày sao kê và hạn thanh toán |
| Nhắc hạn | Công nợ và nghĩa vụ định kỳ; đến hạn không tự ghi thanh toán |
| Báo cáo | Ngày/tuần/tháng/năm hoặc kỳ tùy chọn; phân bổ chi phí, so sánh kỳ, dòng tiền và nợ |
| Dữ liệu | Xác nhận trước khi ghi, sửa/hoàn tác theo nhóm, sao lưu mã hóa và xuất JSON |

App dùng VND số nguyên, 40 danh mục cố định và sổ cá nhân trên một thiết bị. Chưa đồng bộ ngân hàng/nhiều thiết bị, đọc SMS, OCR hóa đơn hoặc hỗ trợ sổ chung. Giao diện hiện dùng tiếng Việt. AI và hạn mức miễn phí phụ thuộc nhà cung cấp; repo không bảo đảm API luôn miễn phí.

## Dữ liệu và AI

Sổ được mã hóa trên thiết bị bằng AES-GCM với khóa trong Android Keystore; Android cloud backup bị tắt. Khi dùng AI, app gửi tin nhắn và ngữ cảnh cần thiết như nguồn tiền, danh mục, công nợ/nghĩa vụ tới Google. AI tạo đề xuất; app xác thực và tính toán nghiệp vụ.

Sao lưu `.fsb` có mật khẩu, không chứa API key hoặc đề xuất chưa xác nhận. JSON xuất ra là dữ liệu đọc được. Xem [quyền riêng tư và sao lưu](docs/PRIVACY.md).

## Build nhanh

Cần JDK 17, Android SDK Platform 35 và Build Tools 35.0.0. Repo có Gradle Wrapper 8.11.1; không cần cài Gradle riêng hoặc API key để build debug.

```powershell
git clone https://github.com/vxtor012/finance-secretary-android.git
cd finance-secretary-android
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug
```

Trên macOS/Linux, dùng `./gradlew` thay cho `.\gradlew.bat` (chạy `chmod +x gradlew` nếu cần). APK: `app/build/outputs/apk/debug/app-debug.apk`.

Xem [thiết lập môi trường và kiểm thử](docs/DEVELOPMENT.md), [kiến trúc](docs/ARCHITECTURE.md) và [phát hành APK đã ký](docs/RELEASING.md).

## Tài liệu và cộng đồng

- [Mục lục tài liệu](docs/README.md)
- [Hướng dẫn sử dụng và xử lý lỗi](docs/USER_GUIDE.md)
- [Phạm vi sản phẩm](docs/PRODUCT_ONE_PAGE.md)
- [Lịch sử phiên bản](docs/RELEASE_NOTES.md)
- [Hướng dẫn đóng góp](CONTRIBUTING.md)

Gửi lỗi hoặc đề xuất qua [GitHub Issues](https://github.com/vxtor012/finance-secretary-android/issues). Dùng dữ liệu giả và che thông tin cá nhân/API key khi báo lỗi.

## Giấy phép

Dự án sử dụng [MIT License](LICENSE). Bạn có thể sử dụng, chỉnh sửa và phân phối theo các điều kiện của giấy phép.
