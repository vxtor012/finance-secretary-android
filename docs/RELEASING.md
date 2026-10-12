# Ký APK và phát hành

[Mục lục](README.md) · [Build debug](DEVELOPMENT.md)

Tài liệu dành cho người duy trì repo hoặc người phát hành fork. Người dùng thông thường tải APK từ Releases; người đóng góp có thể build debug mà không cần khóa ký.

## Điều kiện phát hành

1. Chạy unit tests, lint, build debug và kiểm thử Android theo [DEVELOPMENT](DEVELOPMENT.md).
2. Tăng `versionCode` (số nguyên, phải lớn hơn bản trước) và đặt `versionName` trong `app/build.gradle`.
3. Cập nhật [release notes](RELEASE_NOTES.md) và tài liệu liên quan.
4. Chuẩn bị khóa ký ổn định; giữ bản sao khóa và mật khẩu ngoài repo.

App hiện dùng application ID `vn.finance.secretary`. Cài cập nhật cần cùng ID và chữ ký. Đổi khóa ký khiến APK mới không thể cài đè thông thường. Fork muốn cài song song cần thay application ID và kiểm tra lại cấu hình/runner liên quan.

## Tạo khóa ký lần đầu

Dùng `keytool` của JDK; ví dụ tạo keystore ở thư mục riêng bên ngoài repo:

```sh
keytool -genkeypair -v -keystore /absolute/private/path/finance-release.jks -alias finance -keyalg RSA -keysize 2048 -validity 10000
```

Trên Windows thay đường dẫn bằng đường dẫn thực, ví dụ `C:\private\finance-release.jks`; thư mục cha phải tồn tại. `keytool` hỏi mật khẩu và thông tin chứng thư. Không ghi mật khẩu thật vào lệnh hoặc commit khóa. Nếu dùng workflow hiện tại, alias phải là `finance` và mật khẩu key phải trùng mật khẩu keystore.

## Build release tại máy

`app/build.gradle` đọc các biến dưới đây. Dùng đường dẫn tuyệt đối để tránh nhầm vị trí keystore:

| Biến | Giá trị |
|---|---|
| `RELEASE_STORE_FILE` | Đường dẫn keystore |
| `RELEASE_STORE_PASSWORD` | Mật khẩu keystore |
| `RELEASE_KEY_ALIAS` | Alias khóa; workflow hiện dùng `finance` |
| `RELEASE_KEY_PASSWORD` | Mật khẩu khóa |

PowerShell (thay các giá trị mẫu bằng thông tin riêng của bạn):

```powershell
$env:RELEASE_STORE_FILE = 'C:\private\finance-release.jks'
$env:RELEASE_STORE_PASSWORD = '<store-password>'
$env:RELEASE_KEY_ALIAS = 'finance'
$env:RELEASE_KEY_PASSWORD = '<key-password>'
.\gradlew.bat assembleRelease
```

macOS/Linux:

```sh
export RELEASE_STORE_FILE='/absolute/private/path/finance-release.jks'
export RELEASE_STORE_PASSWORD='<store-password>'
export RELEASE_KEY_ALIAS='finance'
export RELEASE_KEY_PASSWORD='<key-password>'
./gradlew assembleRelease
```

Giá trị mẫu không phải bí mật dùng chung; tránh lưu mật khẩu thật vào lịch sử shell. Nếu không đặt `RELEASE_STORE_FILE`, cấu hình tìm `.tools/release.jks` ở gốc repo, nhưng repo không cung cấp khóa này.

APK đầu ra: `app/build/outputs/apk/release/app-release.apk`. Có thể kiểm tra chữ ký bằng `apksigner` trong Build Tools:

```sh
apksigner verify --verbose --print-certs app/build/outputs/apk/release/app-release.apk
```

Nếu lệnh không có trong PATH, gọi bằng đường dẫn đầy đủ tới `build-tools/35.0.0/apksigner` (`apksigner.bat` trên Windows). Thử cài đè bản trước trên thiết bị thử và kiểm tra dữ liệu trước khi phát hành.

## GitHub Actions

[Workflow hiện tại](../.github/workflows/android.yml) có ba job: `checks`, `device-test` và `release`. Release chỉ chạy sau hai job kiểm tra thành công, khi push tag bắt đầu bằng `v` hoặc chạy thủ công với `release_tag` bắt đầu bằng `v`.

Thiết lập hai repository Actions secrets:

| Secret | Nội dung |
|---|---|
| `ANDROID_KEYSTORE_BASE64` | Toàn bộ file keystore mã hóa base64 |
| `ANDROID_KEYSTORE_PASSWORD` | Mật khẩu keystore và key |

Workflow cố định alias `finance` và dùng một mật khẩu cho cả store/key. Nếu khóa có cấu hình khác, điều chỉnh workflow trước. Base64 không phải mã hóa bảo mật; chỉ lưu giá trị này trong GitHub Secrets. Không in nội dung ra issue/log.

Sau khi commit phiên bản và notes, tạo tag khớp `versionName`. Ví dụ **chỉ khi đã đặt versionName thành 1.3.3**:

```sh
git tag v1.3.3
git push origin v1.3.3
```

Workflow tạo GitHub Release, đính kèm `finance-secretary.apk` và `SHA256SUMS.txt`. Nội dung mô tả lấy từ **toàn bộ** `docs/RELEASE_NOTES.md`. Nếu muốn chỉ đưa ghi chú phiên bản mới, cần điều chỉnh bước chuẩn bị notes của workflow.

`workflow_dispatch` với `release_tag` yêu cầu tag đã tồn tại; không tự tạo tag. Bước `gh release create` không cập nhật Release đã tồn tại, nên chạy lại với tag đã phát hành có thể thất bại ở bước publish. Kiểm tra trạng thái Release trước khi thử lại; không xóa bản phát hành đang dùng chỉ để vượt lỗi.

## Kiểm tra file tải về

Tải APK và `SHA256SUMS.txt` của cùng bản phát hành. So sánh hash:

```powershell
Get-FileHash .\finance-secretary.apk -Algorithm SHA256
Get-Content .\SHA256SUMS.txt
```

Trên Linux dùng `sha256sum -c SHA256SUMS.txt`; macOS dùng `shasum -a 256 finance-secretary.apk` rồi so sánh. SHA256 kiểm tra file khớp artifact phát hành; chữ ký APK xác định tính tương thích khi cài cập nhật.
