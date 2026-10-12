# Lịch sử phiên bản

[Mục lục](README.md) · [Tải APK](https://github.com/vxtor012/finance-secretary-android/releases) · [Hướng dẫn cập nhật](USER_GUIDE.md)

Các phiên bản được xếp từ mới đến cũ. Số liệu kiểm thử bên dưới là ghi nhận tại thời điểm phát hành, không phải kết quả kiểm tra lại trong lần sửa tài liệu. Cài đè APK cùng chữ ký để giữ dữ liệu; sao lưu `.fsb` trước khi gỡ app hoặc chuyển máy.

## v1.3.3

- Giao dịch hiển thị thành dòng gọn, nhóm theo ngày và phân trang tối đa 3 ngày/30 dòng; hỗ trợ lọc thời gian, nguồn tiền, nghiệp vụ và trạng thái hoàn tác.
- Chi tiết và hoàn tác mở từ từng dòng; hoàn tác giữ màn hình Giao dịch.
- Nhắc hạn tách nghĩa vụ định kỳ/công nợ, giảm nội dung lặp và gom thao tác vào menu. Có thể xóa nhắc định kỳ sau xác nhận mà vẫn giữ lịch sử thanh toán.
- Sửa màu nút Back trong các trang con Cài đặt để theo theme sáng/tối.

37 unit tests, lint và kiểm thử tích hợp Android 35 đã đạt cho thay đổi giao diện/nghiệp vụ. Đã kiểm tra theme sáng/tối, chữ lớn và màn hình ngang.

Cài đè APK đã ký cùng khóa lên bản cũ để giữ sổ và API key.

## v1.3.2

- Sửa lỗi đọc sổ trong lúc ghi có thể làm mất bản ghi mới, khiến đề xuất offline không được lưu và job device-test thất bại.
- Dùng khóa đồng bộ chung giữa các instance lưu trữ, bao gồm khởi tạo khóa mã hóa, đọc và ghi. Giữ nguyên khóa mã hóa và định dạng dữ liệu hiện có.
- Thêm test tái hiện đọc trong lúc file tạm đang mở, kiểm tra reader chờ write hoàn tất và dữ liệu mới tồn tại khi tải lại. Giữ đầy đủ test đề xuất offline và ranh giới xác nhận giao dịch.

Cài đè APK để giữ dữ liệu hiện có.

## v1.3.1

- Sửa tên app bị lỗi mã hóa tiếng Việt trong trình cài đặt và launcher.
- Nền icon xanh sáng hơn.
- Kiểm thử nhãn ứng dụng đã cài để ngăn lỗi mã hóa tái diễn.

Cài đè lên bản cũ để giữ dữ liệu.

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
