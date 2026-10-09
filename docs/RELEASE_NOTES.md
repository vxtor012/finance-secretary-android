## v1.3.0

- Cho phép chụp/quay màn hình và thay icon app bằng hình sổ tối giản, hỗ trợ icon thích ứng và theo màu hệ thống.
- Ngày sao kê/thanh toán chỉ hiện khi thêm thẻ tín dụng. Loại nguồn tiền dùng tiếng Việt; giải thích tên gọi khác khi chat.
- Xóa nguồn chưa có dữ liệu; ngừng sử dụng nguồn đã có lịch sử và có thể khôi phục. Giữ nguyên giao dịch và báo cáo.
- Cài đặt gọn theo từng mục, thêm Giới thiệu với tính năng, cách dùng và phiên bản. Tách lưu giờ nhắc khỏi key/model tự lưu.
- Báo cáo phân biệt chi tiêu, thu nhập, phân bổ danh mục; thao tác phụ và báo cáo định kỳ gom vào menu. Không còn chuyển nhầm sang Nhắc hạn khi chưa có báo cáo định kỳ.
- Bỏ CRUD danh mục. Dùng 40 danh mục cố định; giao dịch cũ giữ nguyên phân loại.

31 unit tests, lint và kiểm thử tích hợp Android 35 đã đạt, gồm điều hướng Sổ, About, lưu key/model, giới hạn chat và bảo toàn dữ liệu.

Cài đè APK lên bản cũ để giữ sổ và key Google AI Studio.

## v1.2.0

- Chuyển hoàn toàn sang Google AI Studio/Gemini API, mặc định Gemma 4 26B A4B. Danh sách model dùng key Google và lọc model hỗ trợ chat.
- Cài đè giữ sổ, nguồn tiền và đề xuất đang chờ. Nhập key Google mới trong Cài đặt; key OpenRouter cũ được bỏ khỏi cấu hình.
- Giao diện tối giản: nền trung tính, trợ lý không có khung bong bóng, ô nhập liền khối, nút gửi biểu tượng; thao tác phụ gom vào nút +. Nguồn tiền dạng danh sách và báo cáo mở chi tiết khi cần.
- Chat chỉ giữ 5 tin gần nhất. + → Xóa hội thoại giữ nguyên dữ liệu tài chính và đề xuất đang chờ.

Android 8.0+. Cài đè `finance-secretary.apk`, không gỡ app cũ. Key và model tự lưu. Bấm Kiểm tra kết nối để xác nhận key/model trên máy bạn.

28 unit tests và kiểm thử tích hợp Android 35 với phản hồi Google mô phỏng. Không có key thật của người dùng để xác nhận chat trực tiếp; lỗi key giả được kiểm tra qua Google thật. Google AI Studio áp dụng hạn mức riêng cho từng key/model.

Điều hướng: Chat · Sổ · Báo cáo · Cài đặt. Sổ có Nguồn tiền, Giao dịch và Nhắc hạn. Nút + ở Chat chỉ có Ghi nhanh và Xóa hội thoại.
