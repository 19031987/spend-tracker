# 👑 Monarch Spend (SpendTracker Android)
### Modern Cash Flow & Vendor Spending Tracker Inspired by Monarch Money

**Monarch Spend** is an intelligent, privacy-friendly, lightweight Android app and simulator that automatically detects and organizes where your money goes in real-time using bank notifications (HSBC, PayPal, Monzo, Revolut, Chase, Barclays, and more).

---

## 🌟 Key Upgrades & Features

### 1. 📊 Monarch Cash Flow Engine (Money In & Money Out)
- **Net Cash Flow Card**: Displays your net monthly balance in real-time (`Money In - Money Out`).
- **↑ Money In (Income)**: Automatically captures deposits, salary, transfers, and refunds in vibrant **Emerald Green** (`+£2,500.00`).
- **↓ Money Out (Expenses)**: Captures card purchases, direct debits, and payments in clean **Crimson/Red** (`-£1,240.50`).
- **Savings Ratio Bar**: Real-time visual progress bar showing percentage of income spent vs saved.
- **Today vs Month**: Separate tracking for today's spending/income and current month.

### 2. 🏢 Accurate Vendor & Merchant Capture
- **Smart Vendor Extraction**: Extracts clean merchant and sender names (e.g. `Tesco Stores`, `Costa Coffee`, `British Gas`, `Steam Games`, `Amazon`, `John Smith`).
- **Noise Filter**: Removes card suffixes, dates, timestamps, reference codes, and legal tags (`LTD`, `UK`).
- **Exact Date & Time**: Captures and records full transaction timestamps down to the second.

### 3. 🗄️ Lightweight File-Based SQLite Database
- Built on Android's native, lightweight SQLite engine (`spend_tracker.db`), requiring zero external cloud servers or API tokens.
- Captures `type` (`IN` vs `OUT`), `amount`, `currency`, `merchant`, `category`, `source`, `timestamp`, and `raw_text`.

### 4. 🎨 Monarch Modern Dark Aesthetic
- Refined high-contrast dark palette (`#0B0F19` background, `#121927` cards, `#1E293B` borders).
- Interactive filter pills (`All`, `↓ Money Out`, `↑ Money In`).
- Category breakdown with visual progress bars and category emojis.
- Top Places You Spend leaderboard showing top vendors.
- Tap any transaction to inspect details or delete.
- Quick **+ Add** button to manually record transactions.

---

## 🚀 How to Build & Install the APK

### Method 1: Automatic Cloud Build via GitHub Actions (Recommended)
This repository includes a `.github/workflows/build-apk.yml` workflow configured with Ubuntu, Temurin JDK 17, and Android SDK (API 36).
1. Commit and push changes to `main` branch:
   ```bash
   git add .
   git commit -m "feat: implement Monarch budgeting app UI with Money In/Out calculation"
   git push origin main
   ```
2. GitHub Actions will run automatically and create a new GitHub Release with:
   - **`app-release.apk`**: Direct installable APK for your Android phone.
   - **`app-release.aab`**: Google Play Store App Bundle.

### Method 2: Local Build in Android Studio
1. Open Android Studio.
2. Select **Open** and choose the `spend-tracker` folder.
3. Let Gradle sync and run `Build > Build Bundle(s) / APK(s) > Build APK(s)`.

---

## 💻 Web Simulator Preview
You can also run the full Monarch Spend experience directly in your browser:
- Open [`simulator/index.html`](file:///C:/Users/RAGHU/.gemini/antigravity/scratch/exam_generator/spend-tracker/simulator/index.html) in any browser to test bank alerts and verify the UI.
