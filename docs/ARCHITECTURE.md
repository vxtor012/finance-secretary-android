# Kiến trúc và quy tắc nghiệp vụ

[Mục lục](README.md) · [Phát triển](DEVELOPMENT.md)

## Cấu trúc repo

```text
app/src/main/java/vn/finance/secretary/   Mã Java của ứng dụng
app/src/main/res/                       Giao diện, màu và icon
app/src/main/AndroidManifest.xml        Thành phần và quyền Android
app/src/test/                          Unit tests JVM
app/src/androidTest/                   Kiểm thử trên Android
gradle/wrapper/                        Gradle Wrapper
.github/workflows/android.yml          CI và phát hành
docs/                                  Tài liệu dự án
```

Ứng dụng Android Java với giao diện native, một module `app`, không có WebView, backend riêng hoặc database server. Trạng thái được biểu diễn bằng JSON và lưu dưới dạng file mã hóa.

## Trách nhiệm các lớp

| Lớp | Trách nhiệm |
|---|---|
| `MainActivity` | Điều hướng Chat/Sổ/Báo cáo/Cài đặt, nhập liệu, xác nhận và lưu trạng thái |
| `Ui` | Các tiện ích giao diện native |
| `Assistant` | Đọc số tiền/lệnh offline, tạo ngữ cảnh AI, phân tích đề xuất và danh sách model |
| `GoogleAi` | HTTPS, xác thực API key, xử lý trạng thái lỗi và che key trong thông báo lỗi |
| `Ledger` | Xác thực nghiệp vụ, event ledger, replay, báo cáo, công nợ, thẻ và nghĩa vụ |
| `SecureStore` | AES-GCM/Keystore, đọc/ghi nguyên tử, sao lưu và khôi phục bằng mật khẩu |
| `ChatHistory` | Giới hạn 5 tin và xóa hội thoại độc lập với sổ |
| `ReminderReceiver` | Lịch thông báo theo múi giờ, nhắc hạn và báo cáo kỳ đã hoàn tất |
| `Streams` | Tiện ích đọc stream |

## Luồng ghi giao dịch

1. Người dùng gửi tin nhắn hoặc điền Ghi nhanh.
2. Lệnh được xử lý tại chỗ hoặc gửi Google để trích xuất danh sách nghiệp vụ.
3. App lưu đề xuất chờ, trình bày cho người dùng kiểm tra/chỉnh sửa.
4. Khi xác nhận, `Ledger` kiểm tra cả nhóm, nguồn tiền, ngày, số tiền và quan hệ công nợ/thẻ.
5. Nhóm hợp lệ được áp dụng toàn bộ rồi lưu qua `SecureStore`; nhóm lỗi không được ghi một phần.

Request ID giúp chống ghi trùng. Sửa nhóm và hoàn tác có audit trail; thao tác không được làm mất quan hệ giữa khoản nợ gốc và thanh toán. AI không phải nguồn tính số dư.

## Các bất biến cần giữ khi đóng góp

- Số tiền là VND nguyên; không chấp nhận phần lẻ VND.
- Chuyển nội bộ không phải thu/chi; chỉ phí là chi phí.
- Cho vay/thu hồi gốc và đi vay/trả gốc thay đổi tiền và nợ, không tạo thu nhập/chi phí.
- Mua bằng thẻ ghi chi phí; trả thẻ không tính chi phí lần hai.
- Đến hạn không có nghĩa đã thanh toán; nhắc việc không tự ghi sổ.
- Tài khoản chưa đối soát chỉ có biến động ghi nhận, không được khẳng định số dư thực.
- Mốc đối soát là tại thời điểm ghi nhận; sửa giao dịch trước mốc không tự đổi số dư đã xác minh.
- Xóa chat độc lập với giao dịch/đề xuất; ngừng nguồn có lịch sử không xóa dữ liệu đó.

## Lưu trữ và tác vụ nền

`SecureStore` dùng khóa đồng bộ tĩnh cho các instance trong tiến trình. `AtomicFile` bảo vệ thay thế file nhưng bản thân không khóa reader/writer. Thay đổi cơ chế này cần kiểm thử trên Android; lỗi đọc trong lúc ghi từng được sửa ở 1.3.2.

`ReminderReceiver` đọc sổ để tạo nhắc/báo cáo, tránh tranh chấp ghi trạng thái tài chính với Activity. Metadata kỳ báo cáo không chứa số liệu tài chính. Lịch Android không bảo đảm chạy đúng phút; force-stop ảnh hưởng lịch cho tới khi mở lại app.

Xem [quyền riêng tư](PRIVACY.md) cho dữ liệu AI và định dạng sao lưu; xem [product one-page](PRODUCT_ONE_PAGE.md) cho phạm vi hiện tại.
