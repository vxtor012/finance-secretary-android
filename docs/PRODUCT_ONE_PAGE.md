# PRODUCT ONE-PAGER — TRỢ LÝ TÀI CHÍNH CÁ NHÂN

**Phiên bản:** 1.0 (Product Baseline)  
**Ngày:** 09/10/2026  
**Trạng thái:** Đặc tả sản phẩm độc lập công nghệ (technology-agnostic)  
**Đối tượng:** Một người Việt Nam trưởng thành, tự quản lý tài chính cá nhân

## 1. Tầm nhìn

Xây dựng **một thư ký tài chính cá nhân chủ động, lấy hội thoại làm trung tâm**: người dùng chỉ cần mở một kênh chat và nói bằng tiếng Việt tự nhiên để ghi nhận giao dịch, hỏi về tình hình tài chính, nhận nhắc việc và báo cáo có giải thích. Hệ thống tự tổ chức dữ liệu, thực hiện nghiệp vụ và trình bày kết quả; không yêu cầu người dùng biết kế toán, thao tác bảng tính hay tự phân tích biểu đồ.

**Lời hứa sản phẩm:** *“Chỉ cần nói chuyện; trợ lý ghi sổ chính xác, theo dõi nghĩa vụ và giải thích tiền của bạn đang đi đâu.”*

## 2. Người dùng & vấn đề

- **Người dùng chính:** Cá nhân sống độc lập tại Việt Nam; có tiền mặt, nhiều tài khoản ngân hàng/ví điện tử, chi phí sinh hoạt, thuê nhà, đăng ký dịch vụ, công nợ cá nhân và có thể có thẻ tín dụng.
- **Nỗi đau:** Ghi chép thủ công rườm rà; số dư nằm rải rác; khó phân biệt thu chi với chuyển khoản, vay nợ và thanh toán thẻ; quên khoản đến hạn; báo cáo chỉ có biểu đồ nhưng thiếu giải thích và hành động cụ thể.
- **Kết quả mong muốn:** Một cuộc hội thoại duy nhất để nhập liệu, truy vấn, chỉnh sửa và nhận thông tin chủ động trên các thiết bị.

## 3. Nguyên tắc sản phẩm (không phụ thuộc nền tảng)

1. **Chat-first, minimal friction:** Mọi tác vụ thường ngày hoàn thành qua ngôn ngữ tự nhiên; giao diện phụ chỉ là tùy chọn.
2. **Accuracy before fluency:** Số tiền, số dư và công nợ do bộ máy nghiệp vụ xác định; AI không tự bịa dữ liệu hoặc tự quyết định bút toán.
3. **Explainable & auditable:** Mọi kết luận truy xuất được về giao dịch, kỳ báo cáo và quy tắc tính.
4. **Proactive, not presumptive:** Chủ động nhắc và báo cáo; không tự coi một nghĩa vụ đã được thanh toán.
5. **Privacy & portability:** Giới hạn truy cập, bảo vệ bí mật, xuất/khôi phục dữ liệu, dễ đổi nền tảng và nhà cung cấp AI.
6. **Free-first, graceful degradation:** Ưu tiên chi phí vận hành bằng 0 ở quy mô cá nhân; các chức năng cốt lõi vẫn hoạt động khi AI không sẵn có.

## 4. Phạm vi chức năng cốt lõi (MVP)

| Năng lực | Yêu cầu sản phẩm |
|---|---|
| **Hội thoại tiếng Việt** | Hiểu cách nói tự nhiên, viết tắt số tiền (`50k`, `1tr5`), ngày tương đối, nhiều giao dịch trong một tin; hỏi lại khi mơ hồ; sửa và hoàn tác qua chat. |
| **Thu chi** | Danh mục hai cấp có thể tùy biến: nhà ở, ăn uống, di chuyển, sinh hoạt, sức khỏe, mua sắm, giải trí, subscription, học tập/công việc, gia đình, phí tài chính và các nguồn thu nhập. |
| **Nhiều nguồn tiền** | Quản lý tiền mặt, ngân hàng, ví điện tử, tiết kiệm; chuyển tiền nội bộ và phí giao dịch; bí danh tài khoản. |
| **Công nợ cá nhân đơn giản** | Cho vay, đi vay, trả/thu hồi từng phần; nhiều khoản với cùng đối tượng; hạn trả có hoặc không; nhắc hạn, đối chiếu dư nợ đầu/cuối kỳ. Không yêu cầu tính lãi/trả góp phức tạp ở MVP. |
| **Chi phí định kỳ** | Thuê trọ, điện nước, internet, subscription và nghĩa vụ định kỳ; số tiền cố định hoặc ước tính; nhắc hạn, tạm dừng/hủy; chỉ ghi thanh toán khi được xác nhận hoặc có giao dịch khớp. |
| **Thẻ tín dụng** | Ghi chi bằng thẻ, dư nợ, kỳ sao kê, hạn thanh toán, trả thẻ, phí và hoàn tiền; không cộng chi phí lần hai khi trả thẻ. |
| **Hỏi đáp & phân tích** | Trả lời “Tôi tiêu bao nhiêu?”, “Ai còn nợ tôi?”, “Tháng này thay đổi gì?”, “Tôi có đủ tiền trả các khoản sắp đến hạn không?”; nói rõ khi thiếu dữ liệu. |
| **Báo cáo chủ động** | Tự gửi cuối tuần, cuối tháng, cuối năm; báo cáo ngày và mọi kỳ theo yêu cầu; có bảng số liệu, biểu đồ, so sánh kỳ, giải thích và gợi ý hành động. |
| **Kiểm soát dữ liệu** | Lịch sử chỉnh sửa, chống ghi trùng, sao lưu/khôi phục, xuất dữ liệu, quyền riêng tư và khả năng di chuyển hệ thống. |

## 5. Quy tắc tài chính bất biến

- **Thu nhập/chi phí khác dòng tiền:** Chuyển giữa tài khoản của chính mình không phải thu hoặc chi.
- **Cho vay / thu hồi gốc:** Là biến động tiền và khoản phải thu, không phải chi phí / thu nhập.
- **Đi vay / trả gốc:** Là biến động tiền và khoản phải trả, không phải thu nhập / chi phí.
- **Mua bằng thẻ tín dụng:** Ghi chi phí tại thời điểm mua và tăng nợ thẻ; thanh toán thẻ chỉ giảm tiền và giảm nợ, không ghi chi phí lần nữa.
- **Nghĩa vụ định kỳ:** Đến hạn không đồng nghĩa đã thanh toán; tránh tạo giao dịch trùng.
- **Công nợ không có hạn:** Vẫn phải xuất hiện trong báo cáo, không tự đánh dấu quá hạn.
- **Giao dịch mơ hồ:** Hỏi xác nhận trước khi làm thay đổi số dư hoặc phân bổ vào khoản nợ không chắc chắn.
- **Ghi sổ nhất quán:** Một nghiệp vụ có nhiều tác động phải được lưu trọn vẹn hoặc không được ghi nhận; sửa đổi có dấu vết.

## 6. Chính sách số dư khởi tạo

**Mặc định khởi tạo 0, không bắt buộc nhập số dư thực tế.** Đây là mốc theo dõi biến động, **không phải khẳng định tài khoản ngoài đời có 0 đồng**. Trước khi người dùng xác nhận số dư tại một thời điểm, chỉ được báo cáo **biến động ghi nhận**; không trình bày số dư suy tính như số dư thực tế. Hỗ trợ đối soát bằng chat bất kỳ lúc nào và lưu mốc xác minh.

## 7. Trải nghiệm tiêu biểu

- **Ghi nhanh:** “Sáng ăn phở 45k tiền mặt, đổ xăng 70k bằng MB.” → Xác nhận hai khoản, danh mục và nguồn tiền.
- **Công nợ:** “Cho Nam vay 2 triệu, chưa hẹn ngày trả.” → Tạo khoản phải thu không có hạn. “Nam trả 500k vào ví.” → Còn 1,5 triệu.
- **Chuyển nội bộ:** “Chuyển 1 triệu từ MB sang MoMo, phí 3k.” → Không ghi 1 triệu là chi tiêu; ghi phí 3k.
- **Thẻ tín dụng:** “Mua laptop 12 triệu bằng thẻ.” → Ghi chi và tăng nợ thẻ; trả sao kê không tạo thêm chi phí.
- **Hỏi đáp:** “Tháng này tiền giảm vì sao?” → Phân rã thu chi, vay/trả nợ, chuyển khoản và thanh toán thẻ bằng số liệu thực.
- **Báo cáo:** Cuối tháng gửi tóm tắt thu chi, nhóm chi lớn, thay đổi so với tháng trước, công nợ có/không hạn, nợ thẻ, khoản sắp đến hạn, biểu đồ và nhận xét dễ hiểu.

## 8. Cấu trúc báo cáo chuẩn

Mỗi báo cáo có **(1) kỳ và độ đầy đủ dữ liệu, (2) thu nhập – chi phí – chênh lệch, (3) dòng tiền và số dư đã xác minh nếu có, (4) phân bổ chi phí và so sánh kỳ trước, (5) công nợ phải thu/phải trả gồm khoản không có hạn, (6) thẻ tín dụng, (7) nghĩa vụ định kỳ sắp đến, (8) biểu đồ/bảng, (9) diễn giải và đề xuất có căn cứ**.

**Lịch mặc định:** Cuối tuần, cuối tháng, cuối năm; thời điểm gửi có thể tùy chỉnh theo múi giờ người dùng. Báo cáo gửi trước khi kỳ kết thúc hoàn toàn phải ghi rõ là **tạm tính**; sửa giao dịch hồi tố phải được phản ánh khi tạo lại báo cáo.

## 9. Mô hình miền dữ liệu tối thiểu (logic, không ràng buộc DB)

- **Identity & Preferences:** Chủ sở hữu, quyền truy cập, múi giờ, lịch báo cáo, cấu hình bảo mật.
- **Account & Balance Checkpoint:** Nguồn tiền, loại tài khoản, trạng thái đối soát, mốc số dư đã xác minh.
- **Category:** Nhóm thu/chi và danh mục con, tùy chỉnh và lịch sử phân loại.
- **Financial Event & Ledger Entry:** Giao dịch nghiệp vụ và các tác động tiền/tài sản/nợ tương ứng, định danh chống trùng.
- **Debt & Debt Settlement:** Khoản cho vay/đi vay, hạn tùy chọn, thanh toán từng phần, số dư còn lại.
- **Recurring Rule & Occurrence:** Quy tắc lặp, kỳ đến hạn, trạng thái xác nhận thanh toán.
- **Credit Card & Statement:** Thẻ, dư nợ, sao kê, hạn thanh toán, giao dịch liên quan.
- **Conversation Context & Pending Confirmation:** Ngữ cảnh ngắn, câu hỏi xác nhận chưa hoàn tất.
- **Report & Audit Trail:** Lần tạo/gửi báo cáo, nguồn số liệu, nhật ký thay đổi.

Mô hình phải có định danh ổn định, quan hệ rõ ràng, ràng buộc tính toàn vẹn và khả năng xuất/nhập độc lập nhà cung cấp. **Không chọn database, backend, frontend, framework hoặc AI provider trong tài liệu này.**

## 10. AI & tự động hóa

AI đảm nhiệm **hiểu ý định, trích xuất cấu trúc, diễn giải và hội thoại**; bộ máy nghiệp vụ đảm nhiệm **xác thực, ghi sổ, truy vấn, tính toán**. Thiết kế lớp kết nối model thay thế được, có fallback, giới hạn dữ liệu gửi ra ngoài và kiểm tra đầu ra có cấu trúc. Nếu AI lỗi/hết hạn mức, các lệnh thu chi và báo cáo cơ bản vẫn sử dụng được. Kênh chat và kênh thông báo là **adapter thay thế được**; có thể bắt đầu bằng một ứng dụng nhắn tin và mở rộng sang web/mobile/desktop khi thực sự cần.

## 11. Ngoài phạm vi phiên bản đầu

Tự đồng bộ ngân hàng, đọc SMS, OCR hóa đơn, giao dịch tự động thay người dùng, quản lý đầu tư chuyên sâu, ngoại tệ phức tạp, thuế doanh nghiệp, lãi vay/trả góp phức tạp, đa người dùng chia sẻ sổ chung và ứng dụng native riêng. Các năng lực này có thể bổ sung mà không phá vỡ miền nghiệp vụ lõi.

## 12. Tiêu chí thành công & nghiệm thu

**Kết quả người dùng:** Hầu hết tác vụ hằng ngày hoàn tất bằng 1–2 tin nhắn; không phải mở trang quản trị để ghi chép; báo cáo có thể hiểu và hành động ngay.

**Tiêu chí bắt buộc trước khi sử dụng thật:**

1. Không cộng trùng thu/chi khi chuyển tiền, vay/trả nợ và thanh toán thẻ.
2. Trả nợ từng phần và khoản nợ không hạn được tính/hiển thị chính xác.
3. Không khẳng định số dư thực tế khi tài khoản chưa đối soát.
4. Không ghi trùng tin nhắn hoặc ghi nửa chừng một nghiệp vụ.
5. Giao dịch mơ hồ được hỏi lại; mọi sửa/hoàn tác truy vết được.
6. Báo cáo tuần/tháng/năm có số liệu, công nợ, biểu đồ và giải thích dựa trên dữ liệu thật.
7. Người không được cấp quyền không thể truy cập dữ liệu; bí mật không xuất hiện trong mã nguồn hoặc log.
8. Có thể xuất dữ liệu, sao lưu, khôi phục và triển khai lại trên hạ tầng khác.

## 13. Ràng buộc & định hướng phát triển

- **Không khóa công nghệ:** Mọi lựa chọn giao diện, backend, database, nhà cung cấp AI và hosting thuộc tài liệu kiến trúc triển khai riêng.
- **Ưu tiên miễn phí:** Tối ưu cho một người dùng, minh bạch hạn mức và phương án suy giảm dịch vụ.
- **Mở rộng được:** Phân tách miền tài chính, lớp AI, lưu trữ, kênh chat, bộ lập lịch và báo cáo để tái sử dụng.
- **Có thể chia sẻ mã nguồn:** Repository có tài liệu thiết lập, cấu hình mẫu không chứa bí mật, bộ kiểm thử và hướng dẫn xuất/khôi phục; dữ liệu cá nhân không được đưa vào repository.

---

**Định nghĩa hoàn thành:** Người dùng có một **thư ký tài chính chủ động, đáng tin cậy**, không phải một ứng dụng nhập liệu và tự xem biểu đồ. Công nghệ có thể thay đổi; các nguyên tắc tài chính, trải nghiệm chat-first và khả năng giải thích phải được giữ nguyên.
