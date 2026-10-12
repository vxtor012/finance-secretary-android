# Product one-page — Thư ký tài chính

**Phạm vi:** ứng dụng Android hiện tại, mã nguồn 1.3.4
**Đối tượng:** cá nhân quản lý tài chính bằng VND, ưu tiên người dùng tiếng Việt

[Mục lục](README.md) · [Sử dụng](USER_GUIDE.md) · [Kiến trúc](ARCHITECTURE.md)

## Mục tiêu

Một thư ký tài chính lấy hội thoại làm trung tâm: người dùng nói hoặc nhập ngắn để ghi giao dịch, theo dõi tiền và nghĩa vụ, nhận báo cáo dễ hiểu. Người dùng không cần biết kế toán; ứng dụng phải phân biệt đúng thu chi, chuyển tiền, công nợ và thanh toán thẻ.

Lời hứa sản phẩm: ghi sổ có kiểm tra và xác nhận, cho biết tiền đi đâu, nhắc nghĩa vụ mà không tự giả định đã thanh toán.

## Nguyên tắc

1. **Chính xác:** bộ máy nghiệp vụ xác thực và tính toán; AI không tự quyết định số dư hoặc ghi sổ.
2. **Có thể truy vết:** sửa và hoàn tác theo nhóm có dấu vết, không phá quan hệ công nợ/thẻ.
3. **Người dùng kiểm soát:** đề xuất cần xác nhận; nhắc hạn không tự ghi thanh toán.
4. **Riêng tư và di chuyển được:** dữ liệu tại chỗ được mã hóa, có sao lưu và xuất dữ liệu; nói rõ ngữ cảnh gửi tới AI.
5. **Dùng được khi thiếu AI:** lệnh thu/chi, biểu mẫu Ghi nhanh và báo cáo tại chỗ vẫn hoạt động.
6. **Ít thao tác:** ưu tiên chat, có giao diện bổ trợ cho nguồn tiền, lịch sử và báo cáo.

## Phạm vi đã triển khai

| Năng lực | Hành vi hiện tại |
|---|---|
| Hội thoại | Chat tiếng Việt qua Google AI; hiểu số tiền viết tắt, đề xuất nhiều nghiệp vụ và hỏi lại khi thiếu dữ liệu |
| Ghi offline | Lệnh thu/chi đơn giản; Ghi nhanh hỗ trợ các nghiệp vụ khác |
| Thu chi | VND nguyên, 40 danh mục cố định cho giao dịch mới, giữ phân loại lịch sử |
| Nguồn tiền | Tiền mặt, ngân hàng, ví, tiết kiệm, thẻ; bí danh, đối soát, ngừng sử dụng/khôi phục |
| Công nợ | Cho vay, đi vay, thu/trả từng phần, nhiều khoản cùng đối tượng, hạn tùy chọn |
| Định kỳ | Nghĩa vụ lặp, nhắc hạn, tạm dừng/kích hoạt lại, thanh toán sau xác nhận |
| Thẻ | Mua, trả và hoàn tiền thẻ; dư nợ, kỳ sao kê, ngày thanh toán |
| Báo cáo | Kỳ ngày/tuần/tháng/năm hoặc tùy chọn, phân bổ chi phí, so sánh kỳ, dòng tiền, nợ và nghĩa vụ |
| Dữ liệu | Nhóm giao dịch nguyên tử, chống trùng request ID, sửa/hoàn tác, sao lưu `.fsb`, xuất JSON |

Điều hướng gồm **Chat · Sổ · Báo cáo · Cài đặt**. Sổ gồm Nguồn tiền, Giao dịch và Nhắc hạn. Chat giữ 5 tin gần nhất; xóa chat không xóa sổ hoặc đề xuất đang chờ.

## Quy tắc tài chính bắt buộc

- Chuyển giữa nguồn của chính mình không phải thu hoặc chi; phí là chi phí riêng.
- Cho vay/thu hồi gốc thay đổi tiền và khoản phải thu; đi vay/trả gốc thay đổi tiền và khoản phải trả.
- Mua bằng thẻ ghi chi phí và tăng nợ; trả thẻ không ghi chi phí lần hai.
- Đến hạn không đồng nghĩa đã trả. Khoản không có hạn vẫn được báo cáo, không tự coi là quá hạn.
- Giao dịch mơ hồ phải được làm rõ. Thu/trả nợ cần mã khoản gốc để phân bổ đúng.
- Nhóm nghiệp vụ được ghi toàn bộ hoặc không ghi; không chấp nhận ghi một phần.

## Số dư và báo cáo

Không bắt buộc nhập số dư ban đầu. Trước khi đối soát, số liệu chỉ phản ánh biến động được ghi nhận, không khẳng định tiền thực trong tài khoản. Mốc đối soát lưu số dư tại thời điểm ghi nhận; sửa giao dịch trước mốc không tự đổi số dư đã xác minh.

Báo cáo cần phân biệt thu nhập/chi phí với dòng tiền, nói rõ kỳ, so sánh và các khoản nợ/nghĩa vụ. Phần nguồn tiền dùng số dư hiện tại; không trình bày nó như số dư cuối kỳ quá khứ. Báo cáo định kỳ dùng kỳ đã hoàn tất. Thông báo phụ thuộc lịch Android và múi giờ máy, không bảo đảm đúng phút.

## Giới hạn và định hướng

Hiện tại là sổ một người trên một thiết bị Android. Chưa có đồng bộ ngân hàng/nhiều thiết bị, sổ chung, đọc SMS, OCR, đa tiền tệ, quản lý đầu tư/thuế doanh nghiệp hoặc lãi vay/trả góp phức tạp. Không tự nhập/khớp sao kê ngân hàng. Chưa có PIN riêng ngoài xác thực khóa màn hình của thiết bị.

Định hướng dài hạn là giảm thao tác nhập liệu, tăng khả năng giải thích và di chuyển dữ liệu. Đồng bộ đa nền tảng, danh mục tùy biến và lớp AI có thể thay nhà cung cấp là hướng xem xét, không phải tính năng hiện có hoặc lịch phát hành đã cam kết.

Đặc tả gốc ngày 09/10/2026 độc lập công nghệ từng loại ứng dụng native khỏi MVP và đề xuất danh mục tùy biến. Bản Android đã thay đổi hai lựa chọn này: triển khai native và dùng danh mục cố định. Tài liệu này mô tả phạm vi thực tế để người dùng không nhầm đặc tả với tính năng đã có.

## Tiêu chí đánh giá thay đổi

Mỗi thay đổi cần giữ đúng bất biến tài chính, không ghi trùng/ghi một phần, không mất dữ liệu cũ và không tự thanh toán nghĩa vụ. Thay đổi số dư phải truy vết được; dữ liệu chưa đối soát phải được trình bày đúng giới hạn. Kiểm thử cần bao phủ tình huống thực tế và dữ liệu giả; giao diện/AI phải được kiểm tra riêng khi liên quan.

Xem [hướng dẫn đóng góp](../CONTRIBUTING.md) cho quy trình kiểm tra và gửi PR.
