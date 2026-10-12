# Finance Secretary for Android

[Tiếng Việt](README.md) · [Download APK](https://github.com/vxtor012/finance-secretary-android/releases/latest) · [Documentation](docs/README.md)

An open-source personal finance app with a Vietnamese chat interface. Track expenses, income, accounts, personal debts, credit cards and recurring obligations. Data is stored on your device; no self-hosted backend is required.

**Android 8.0+ · Java 17 · MIT license**

## Try it

1. Download `finance-secretary.apk` from Releases and install it on Android.
2. Open Chat and send `chi 45k cash ăn sáng` (spend VND 45,000 from cash for breakfast). This works offline without an API key.
3. Review the proposed amount, account, date and category, then confirm. Transactions are only recorded after confirmation.
4. View transactions in **Sổ** (Ledger) and reports in **Báo cáo** (Reports).

For natural-language chat, open **Cài đặt → Kết nối AI** (Settings → AI connection), enter your own [Google AI Studio API key](https://aistudio.google.com/apikey), choose a model and test the connection. Follow [Google's API key instructions](https://ai.google.dev/gemini-api/docs/api-key). The source defaults to `gemma-4-26b-a4b-it`; availability, quotas and costs depend on your Google account and the service.

The app UI and detailed documentation currently use Vietnamese. This English page provides an entry point for international contributors.

## Capabilities and limits

- Income/expenses, internal transfers and fees; loans and partial repayments; card purchases, payments and refunds.
- Offline income/expense commands and a quick-entry form for other transaction types.
- Reports, reminders, account reconciliation, group undo/replacement and encrypted backups.
- Integer VND amounts, 40 fixed categories and a personal ledger on one device.

There is no bank sync, multi-device sync, SMS import, receipt OCR or shared ledger. Reminders never automatically record payments. AI proposes transactions; the app validates them and computes the ledger.

## Data and backups

Local state uses AES-GCM with Android Keystore. Android cloud backup is disabled. AI requests send the current message, short recent user context, account metadata, categories and debt/obligation context to Google.

Create a password-protected `.fsb` backup before uninstalling, clearing data or moving devices. Backups omit API keys and pending proposals; restore replaces the current ledger. Exported JSON is readable sensitive data. See [privacy and backups](docs/PRIVACY.md).

Install updates signed with the same key over the existing app to preserve data. A locally built debug APK may use a different signing key from the released APK.

## Build

Install JDK 17, Android SDK Platform 35 and Build Tools 35.0.0. Configure `ANDROID_HOME` or `local.properties`. Gradle Wrapper 8.11.1 is included; a Google API key is not required for debug builds or unit tests.

```sh
git clone https://github.com/vxtor012/finance-secretary-android.git
cd finance-secretary-android
chmod +x gradlew
./gradlew testDebugUnitTest lintDebug assembleDebug
```

Windows PowerShell: use `.\gradlew.bat testDebugUnitTest lintDebug assembleDebug`. Output: `app/build/outputs/apk/debug/app-debug.apk`.

Read [development](docs/DEVELOPMENT.md), [architecture](docs/ARCHITECTURE.md), [release signing](docs/RELEASING.md) and [contribution guidelines](CONTRIBUTING.md). Report bugs through [GitHub Issues](https://github.com/vxtor012/finance-secretary-android/issues) using synthetic data and without API keys.

## License

[MIT](LICENSE), copyright 2026 vxtor012.
