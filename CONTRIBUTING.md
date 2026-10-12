# Đóng góp cho Thư ký tài chính

Cảm ơn bạn đã dành thời gian cải thiện dự án. Có thể đóng góp bằng cách sửa tài liệu, báo lỗi, đề xuất tính năng hoặc gửi mã nguồn. Trao đổi bằng tiếng Việt hoặc tiếng Anh đều được.

## Báo lỗi

Tìm issue tương tự trước khi mở [issue mới](https://github.com/vxtor012/finance-secretary-android/issues). Cung cấp:

- Phiên bản app, phiên bản Android và loại thiết bị/emulator.
- Các bước tái hiện với số tiền/tên người/nguồn tiền giả.
- Kết quả mong đợi và kết quả thực tế.
- App dùng offline hay AI; tên model và thông báo lỗi nếu liên quan.
- Ảnh hoặc log đã che thông tin cá nhân khi cần.

Không đính kèm API key, dữ liệu tài chính thật, sao lưu, keystore hoặc mật khẩu. Với lỗi có thể làm lộ bí mật/dữ liệu, chỉ mô tả khái quát trên issue công khai và đề nghị người duy trì thống nhất kênh trao đổi riêng; không công khai cách khai thác hoặc bí mật người dùng.

## Đề xuất tính năng

Mô tả vấn đề của người dùng, ví dụ thực tế và hành vi đề xuất. Với thay đổi lớn về dữ liệu, nghiệp vụ hoặc phạm vi sản phẩm, nên trao đổi trong issue trước khi triển khai. Xem [phạm vi sản phẩm](docs/PRODUCT_ONE_PAGE.md) và [kiến trúc](docs/ARCHITECTURE.md).

## Gửi pull request

1. Fork repo và clone fork của bạn; hoặc tạo branch nếu có quyền ghi.
2. Tạo branch có tên dễ hiểu, ví dụ `docs/setup-guide` hoặc `fix/debt-settlement`.
3. Thiết lập môi trường theo [DEVELOPMENT](docs/DEVELOPMENT.md).
4. Giữ thay đổi tập trung vào một vấn đề. Cập nhật docs khi thay đổi hành vi hoặc cách cài đặt.
5. Chạy kiểm tra phù hợp bên dưới, commit rồi gửi pull request vào `main`.

PR nên mô tả vấn đề, hành vi sau thay đổi và kiểm tra đã thực hiện. Nêu rõ kiểm tra chưa chạy và lý do. Thay đổi UI nên có ảnh bằng dữ liệu giả; thay đổi schema/sao lưu cần giải thích tương thích và khôi phục dữ liệu cũ.

## Kiểm tra trước khi gửi

Với thay đổi mã nguồn, chạy:

```powershell
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug
```

Trên macOS/Linux dùng `./gradlew`. Với lưu trữ, lifecycle, điều hướng hoặc thông báo, chạy thêm kiểm thử Android theo [DEVELOPMENT](docs/DEVELOPMENT.md). Với sửa nghiệp vụ, thêm kiểm thử tình huống tái hiện lỗi và giữ các [bất biến tài chính](docs/ARCHITECTURE.md).

Với thay đổi chỉ gồm tài liệu, kiểm tra liên kết, đường dẫn, ví dụ lệnh và đối chiếu nội dung với mã nguồn; không bắt buộc build APK. CI trong [android.yml](.github/workflows/android.yml) vẫn chạy trên pull request.

## Quy ước

Giữ phong cách Java và cấu trúc hiện có. Không để AI tự ghi giao dịch hoặc tính số dư thay `Ledger`. Không đưa log chứa key/dữ liệu tài chính vào mã nguồn. Không commit output build, `local.properties`, `.fsb`, JSON cá nhân hoặc khóa ký. Dependency mới cần có lý do cụ thể.

Đóng góp được phân phối theo [MIT License](LICENSE) của dự án. Bạn cần có quyền cung cấp mã nguồn/tài liệu mà mình gửi.
