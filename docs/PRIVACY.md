# Dữ liệu, quyền riêng tư và sao lưu

[Mục lục](README.md) · [Hướng dẫn sử dụng](USER_GUIDE.md)

Tài liệu mô tả cơ chế trong mã nguồn hiện tại, không phải kết quả kiểm toán bảo mật độc lập.

## Dữ liệu lưu trên thiết bị

App lưu toàn bộ trạng thái riêng trong `ledger.enc`: nguồn tiền, giao dịch, công nợ, nghĩa vụ, mốc đối soát, lịch sử chỉnh sửa, hội thoại ngắn, cấu hình và API key. `SecureStore` mã hóa AES-GCM bằng khóa Android Keystore. Ghi file dùng `AtomicFile`; khóa đồng bộ chung bảo vệ đọc/ghi trong cùng tiến trình app.

Android cloud backup bị tắt trong manifest. Mã hóa tại chỗ không thay thế sao lưu chuyển máy: khóa Keystore gắn với thiết bị/app. Gỡ app hoặc xóa dữ liệu có thể khiến sổ không thể khôi phục nếu chưa xuất `.fsb`.

Chat chỉ giữ 5 tin gần nhất. Xóa hội thoại không xóa sổ hoặc đề xuất chờ. App cho phép chụp/quay màn hình; ảnh và file bạn chia sẻ có thể lộ dữ liệu.

## Dữ liệu gửi tới Google

Khi dùng AI, `Assistant` gọi Gemini API qua HTTPS tại `generativelanguage.googleapis.com`. Yêu cầu chứa:

- Tin nhắn hiện tại và ngữ cảnh tin nhắn người dùng gần đây trong lịch sử ngắn.
- Ngày hiện tại; ID, tên, bí danh và loại nguồn tiền đang dùng.
- Danh mục và dữ liệu công nợ/nghĩa vụ do `ledger.obligations()` tạo.

App không gửi toàn bộ lịch sử giao dịch hoặc mốc số dư trong ngữ cảnh AI. API key được gửi để xác thực yêu cầu, không chèn vào prompt. Kiểm tra kết nối và tải danh sách model cũng kết nối Google. Các lệnh/báo cáo xử lý tại chỗ và Ghi nhanh không cần gọi AI.

AI tạo đề xuất có cấu trúc; bộ máy nghiệp vụ kiểm tra và người dùng xác nhận trước khi ghi. Chính sách xử lý dữ liệu ở dịch vụ bên ngoài do Google quy định. Với dịch vụ không trả phí, điều khoản Google cho biết nội dung có thể được dùng để cải thiện sản phẩm/model và được người đánh giá xem; Google yêu cầu không gửi dữ liệu nhạy cảm, bí mật hoặc cá nhân vào các dịch vụ này. Điều kiện có ngoại lệ theo khu vực và loại dịch vụ; đọc [điều khoản Gemini API](https://ai.google.dev/gemini-api/terms) trước khi chọn cấu hình AI. Dùng Ghi nhanh offline nếu không muốn gửi nội dung tài chính ra ngoài.

## Quyền Android

| Quyền | Mục đích |
|---|---|
| `INTERNET` | Kết nối Google AI API |
| `POST_NOTIFICATIONS` | Nhắc hạn và báo cáo; cần cấp quyền runtime trên Android 13+ |
| `RECEIVE_BOOT_COMPLETED` | Thiết lập lại lịch nhắc sau khi máy khởi động |

File xuất/nhập được chọn qua giao diện chọn file Android. Manifest không yêu cầu đọc SMS, danh bạ hoặc truy cập ngân hàng.

## Sao lưu `.fsb` và xuất JSON

| Thuộc tính | Sao lưu `.fsb` | Xuất JSON |
|---|---|---|
| Mục đích | Khôi phục/chuyển máy | Đọc và phân tích dữ liệu |
| Bảo vệ | Mật khẩu ít nhất 10 ký tự, mã hóa | Văn bản đọc được, không có mật khẩu |
| Khôi phục trong app | Có; thay thế sổ hiện tại | Không qua luồng khôi phục sao lưu |

`SecureStore.backup()` loại API key và đề xuất chờ khỏi bản sao. Định dạng hiện tại có header `FSB1`, salt ngẫu nhiên 16 byte và IV AES-GCM; khóa được dẫn xuất bằng PBKDF2-HMAC-SHA256, 210.000 vòng, 256 bit. App chỉ hỗ trợ schema sao lưu hiện tại (`schemaVersion = 1`).

Giữ mật khẩu riêng với file, tạo sao lưu trước khi cập nhật hoặc thay thiết bị và thử khôi phục trên thiết bị thử nếu cần xác minh khả năng phục hồi. Không có chức năng lấy lại mật khẩu. Khôi phục thay thế sổ; tạo sao lưu sổ hiện tại trước khi thực hiện. Nhập lại API key sau chuyển máy.

JSON chứa dữ liệu tài chính/hội thoại dạng đọc được. Không commit file sao lưu, JSON thật, API key, keystore, mật khẩu hoặc log chứa thông tin cá nhân vào GitHub. Khi báo lỗi, dùng bộ dữ liệu giả.
