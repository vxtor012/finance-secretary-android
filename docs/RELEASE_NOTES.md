## v1.2.0

- Chuyển hoàn toàn sang Google AI Studio/Gemini API, mặc định Gemma 4 26B A4B. Danh sách model dùng key Google và lọc model hỗ trợ chat.
- Cài đè giữ sổ, nguồn tiền và đề xuất đang chờ. Nhập key Google mới trong Cài đặt; key OpenRouter cũ được bỏ khỏi cấu hình.
- Giao diện tối giản: nền trung tính, trợ lý không có khung bong bóng, ô nhập liền khối, nút gửi biểu tượng; thao tác phụ gom vào nút +. Nguồn tiền dạng danh sách và báo cáo mở chi tiết khi cần.
- Chat chỉ giữ 5 tin gần nhất. + → Xóa hội thoại giữ nguyên dữ liệu tài chính và đề xuất đang chờ.

Android 8.0+. Cài đè `finance-secretary.apk`, không gỡ app cũ. Key và model tự lưu. Bấm Kiểm tra kết nối để xác nhận key/model trên máy bạn.

28 unit tests và kiểm thử tích hợp Android 35 với phản hồi Google mô phỏng. Không có key thật của người dùng để xác nhận chat trực tiếp; lỗi key giả được kiểm tra qua Google thật. Google AI Studio áp dụng hạn mức riêng cho từng key/model.
