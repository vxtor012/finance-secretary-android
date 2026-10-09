## v1.1.0

- Sửa chat OpenRouter: hiển thị lỗi cụ thể thay vì thông báo offline chung; xử lý câu trả lời hội thoại, JSON và phản hồi thiếu nội dung.
- Key và model tự lưu khi thay đổi; thêm kiểm tra kết nối gọi đúng model được chọn. Gemma 4 26B A4B miễn phí là mặc định mới.
- Giao diện Material 3: điều hướng dưới, bong bóng hội thoại, ô nhập cố định, thẻ nguồn tiền/báo cáo, bảng xác nhận giao dịch và chế độ sáng/tối.
- Giữ nguyên chữ ký APK để cài cập nhật trên bản cũ, giữ sổ và cấu hình đã lưu.

Android 8.0+. Tải `finance-secretary.apk` rồi cài đè bản cũ. Không gỡ app trước khi cập nhật.

Đã kiểm tra 25 unit tests và kiểm thử tích hợp Android 35 với phản hồi OpenRouter mô phỏng. Kiểm tra API key thực tế bằng nút **Kiểm tra kết nối** trên máy người dùng. Model miễn phí phụ thuộc khả dụng, hạn mức và chính sách của OpenRouter.
