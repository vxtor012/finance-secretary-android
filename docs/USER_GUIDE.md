# Hướng dẫn sử dụng

[Mục lục](README.md) · [Dữ liệu và sao lưu](PRIVACY.md)

## Cài đặt và cập nhật

Cần Android 8.0 (API 26) trở lên. Tải `finance-secretary.apk` từ [Releases](https://github.com/vxtor012/finance-secretary-android/releases/latest), mở file và cho phép trình duyệt/trình quản lý file cài APK nếu Android hỏi.

Cập nhật bằng cách cài đè APK cùng chữ ký. Không gỡ app cũ trước khi sao lưu. APK debug tự build có thể khác chữ ký với APK release. App yêu cầu xác thực khóa màn hình khi mở nếu thiết bị đã đặt khóa; chưa có PIN riêng. Cho phép thông báo để nhận nhắc hạn.

## Ghi sổ không cần AI

Trong Chat, dùng `chi <số tiền> <nguồn> <nội dung>` hoặc `thu <số tiền> <nguồn> <nội dung>`:

```text
chi 45k cash ăn sáng
thu 10tr cash lương
```

`cash` là bí danh của nguồn Tiền mặt có sẵn. Lệnh offline lấy ngày hôm nay và danh mục mặc định `Sinh hoạt/Khác` hoặc `Thu nhập/Khác`. Kiểm tra và chỉnh đề xuất trước khi xác nhận. Số tiền hỗ trợ `50k`, `1tr5`, `1500000`; phải là VND nguyên. Với tên nguồn nhiều từ, dùng biểu mẫu để chọn chính xác.

**Chat → + → Ghi nhanh** hỗ trợ chi, thu, chuyển tiền, cho vay, đi vay, thu nợ, trả nợ, mua bằng thẻ, trả thẻ và hoàn tiền thẻ. Dùng biểu mẫu này khi không có mạng hoặc AI lỗi.

## Chat tự nhiên với AI

1. Tạo key của bạn tại [Google AI Studio](https://aistudio.google.com/apikey), theo [hướng dẫn Google](https://ai.google.dev/gemini-api/docs/api-key).
2. Vào **Cài đặt → Kết nối AI**, nhập key Google; key OpenRouter không được hỗ trợ.
3. Chọn model; key/model tự lưu khi chỉnh sửa hoặc rời trang.
4. Bấm **Kiểm tra kết nối**, sau đó thử “Sáng ăn phở 45k tiền mặt”.

Model mặc định trong mã nguồn là `gemma-4-26b-a4b-it`. Nếu không khả dụng, tải lại danh sách và chọn model mà key dùng được. App không tự đổi model. Chi phí và hạn mức phụ thuộc Google/tài khoản của bạn.

Sau khi thêm MB và MoMo trong Sổ, có thể thử:

```text
Sáng ăn phở 45k tiền mặt, đổ xăng 70k bằng MB.
Chuyển 1 triệu từ MB sang MoMo, phí 3k.
Cho Nam vay 2 triệu bằng tiền mặt, chưa hẹn ngày trả.
```

AI có thể hỏi lại nếu thiếu thông tin. Kiểm tra đề xuất và Sổ để biết giao dịch đã ghi hay chưa.

## Xác nhận, sửa và hoàn tác

Kiểm tra nghiệp vụ, số tiền, phí, ngày, danh mục, nguồn/đích và mã khoản nợ trước khi xác nhận. Nhóm nhiều giao dịch được ghi toàn bộ hoặc không ghi.

- `hoàn tác`: hoàn tác nhóm cuối; lịch sử giao dịch cho phép chọn nhóm cụ thể.
- `sửa cuối chi 70k cash ăn sáng`: thay **toàn bộ nhóm cuối** sau khi duyệt đề xuất hợp lệ.
- **+ → Xóa hội thoại**: giữ dữ liệu tài chính và đề xuất chờ. Chat chỉ giữ 5 tin gần nhất.

Sửa/hoàn tác có thể bị từ chối nếu làm sai quan hệ công nợ hoặc thẻ; cần xử lý giao dịch phụ thuộc trước. Xử lý đề xuất chờ trước khi tạo đề xuất khác hoặc xóa nguồn liên quan.

## Theo dõi danh sách giao dịch

Mở **Sổ → Giao dịch**. Danh sách nhóm theo ngày giao dịch, mới nhất trước; mỗi trang hiển thị tối đa **3 ngày có giao dịch và 30 dòng**. Nếu một ngày có nhiều hơn 30 giao dịch, ngày đó được tiếp tục ở trang sau. Dùng **Trước/Sau** ở cuối màn hình để chuyển trang.

Bấm **Bộ lọc** để chọn tất cả thời gian, hôm nay, 7 ngày qua, tháng này hoặc khoảng ngày tùy chọn; lọc thêm nguồn tiền và nghiệp vụ. Nguồn ngừng dùng vẫn có thể được chọn để xem lịch sử. Lọc nguồn tính cả nguồn đi và nguồn đích của chuyển tiền/trả thẻ. Có thể ẩn giao dịch đã hoàn tác. **Xóa bộ lọc → Áp dụng** đưa về danh sách đầy đủ.

Bấm một dòng để xem chi tiết và hoàn tác cả nhóm nếu cần. Hoàn tác từ đây giữ bạn ở màn hình Giao dịch.

## Nguồn tiền và số dư

Trong **Sổ → Nguồn tiền**, thêm tiền mặt, ngân hàng, ví, tiết kiệm hoặc thẻ. Bí danh khi chat là tùy chọn, ví dụ `vcb, bank`. Chỉ thẻ tín dụng có ngày sao kê và thanh toán.

Không cần nhập số dư ban đầu. Trước đối soát, app chỉ biết biến động đã ghi, không biết tiền thực trong tài khoản. Dùng thao tác đối soát để lưu số dư thực tại thời điểm ghi nhận. Sửa giao dịch trước mốc có thể thay đổi báo cáo nhưng không tự đổi số dư đã xác minh.

Nguồn chưa có dữ liệu có thể xóa; nguồn có giao dịch/mốc đối soát được ngừng sử dụng và có thể khôi phục. Phải giữ ít nhất một nguồn đang dùng. Giao dịch mới dùng 40 danh mục cố định; danh mục lịch sử được giữ nguyên.

## Công nợ, thẻ và nghĩa vụ

Cho vay/thu hồi gốc và đi vay/trả gốc không phải chi phí/thu nhập. Khi thu/trả nợ, lấy **mã khoản nợ gốc** ở **Sổ → Nhắc hạn**, đặc biệt khi cùng người có nhiều khoản. Hạn trả có thể trống; khoản không có hạn vẫn có trong báo cáo.

Mua bằng thẻ ghi chi phí và tăng nợ; trả thẻ giảm tiền và nợ, không ghi chi phí lần hai. App không tự nhập/khớp sao kê ngân hàng.

Nghĩa vụ định kỳ được hiển thị thành dòng gọn theo hạn kế tiếp; công nợ có nhóm riêng. Bấm dòng nhắc hạn để mở menu **Chi tiết nhắc hạn**, **Xác nhận thanh toán**, **Tạm dừng/Kích hoạt lại** hoặc **Xóa nhắc hạn**. Xóa cần xác nhận, ngừng các lần nhắc tiếp theo và giữ giao dịch thanh toán cũ; không thể hoàn tác việc xóa. Nếu có đề xuất thanh toán đang chờ, cần xử lý đề xuất trước khi xóa. Công nợ gốc không bị xóa qua thao tác xóa nhắc định kỳ.

Nghĩa vụ định kỳ có thể tạm dừng/kích hoạt lại. Đến hạn chỉ tạo nhắc; muốn ghi thanh toán, bấm xác nhận rồi duyệt đề xuất. Thông báo không tự ghi sổ.

## Báo cáo và thông báo

Mở **Báo cáo**, chọn ngày/tuần/tháng/năm hoặc kỳ tùy chọn. Trong Chat có thể thử `báo cáo`, `tháng này tôi tiêu bao nhiêu`, `ai còn nợ tôi`.

Báo cáo phân biệt thu chi với dòng tiền, có phân bổ danh mục, so sánh kỳ, nợ và nghĩa vụ. Phần nguồn tiền là số dư hiện tại từ mốc đối soát, không phải số dư cuối một kỳ quá khứ.

Cấu hình tại **Cài đặt → Nhắc nhở**; giờ nhắc lưu riêng với AI. Báo cáo định kỳ xem ở **Báo cáo → Tùy chọn báo cáo → Báo cáo định kỳ**. Lịch theo múi giờ thiết bị. Android có thể trì hoãn thông báo; force-stop ngừng nhắc đến khi mở lại app.

## Sao lưu và chuyển máy

1. Xử lý đề xuất chờ; sao lưu không mang theo đề xuất này.
2. Vào **Cài đặt → Dữ liệu → Sao lưu**, đặt mật khẩu ít nhất 10 ký tự và lưu `.fsb`.
3. Giữ file và mật khẩu riêng. Không có cơ chế lấy lại mật khẩu.
4. Trên máy mới, cài app, chọn **Khôi phục sao lưu**, mở `.fsb` và nhập mật khẩu.
5. Khôi phục **thay thế sổ hiện tại**, không gộp. Sao lưu sổ hiện tại trước khi thay thế.
6. Kiểm tra giao dịch/báo cáo, nhập lại API key và kiểm tra nhắc nhở/quyền thông báo.

JSON xuất ra dành cho đọc/phân tích; luồng khôi phục của app dùng `.fsb`. Xem [quyền riêng tư](PRIVACY.md).

## Xử lý lỗi

| Hiện tượng | Cách xử lý |
|---|---|
| APK không cài được | Kiểm tra Android 8.0+, quyền cài APK và chữ ký; sao lưu trước khi cân nhắc gỡ app cũ. |
| Key không hợp lệ/401 | Nhập lại key Google và kiểm tra kết nối; không gửi key trong issue. |
| 403/chính sách | Kiểm tra quyền dùng dịch vụ/model trong tài khoản Google và thông báo chi tiết. |
| Model không tìm thấy/404 | Tải lại danh sách, chọn model khả dụng với key. |
| Hết hạn mức/429 | Kiểm tra hạn mức Google, chờ hoặc chọn model khác; dùng Ghi nhanh. |
| Mất mạng, AI chậm, 502/503/504 | Thử sau; dùng lệnh offline hoặc Ghi nhanh. |
| Số dư khác kỳ vọng | Kiểm tra ngày, nguồn, phí và mốc đối soát; phân biệt dòng tiền với chi phí. |
| Không có thông báo | Kiểm tra quyền, giờ, múi giờ, hạn chế pin; mở app sau force-stop. |
| Khôi phục lỗi | Kiểm tra `.fsb`, mật khẩu, quyền đọc file; JSON không phải bản sao lưu mã hóa. |

Nếu vẫn lỗi, gửi [issue](https://github.com/vxtor012/finance-secretary-android/issues) theo [hướng dẫn đóng góp](../CONTRIBUTING.md) bằng dữ liệu giả.
