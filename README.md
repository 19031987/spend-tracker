# 💸 SpendTracker (Android)
### Simple Real-Time Spending Tracker for HSBC & PayPal

SpendTracker is a focused, lightweight Android app that automatically detects and tracks where you spend money and how much you spend in real-time, using your phone's notifications from **HSBC** and **PayPal**.

---

## 🌟 Features

- ⚡ **Automated Real-Time Tracking**: Listens for transaction alerts from HSBC and PayPal (`NotificationListenerService`).
- 🔍 **Instant Spend Extraction**: Automatically pulls the exact amount (e.g. `£14.80`), clean merchant name (e.g. `Tesco Stores`), and payment source.
- 🏷️ **Smart Auto-Categorization**:
  - **Groceries**: Tesco, Sainsbury's, Asda, Morrisons, Aldi, Lidl, Waitrose, M&S, Co-op.
  - **Dining & Drinks**: Costa, Starbucks, McDonald's, Greggs, Nando's, Deliveroo, Uber Eats, Just Eat.
  - **Transport & Fuel**: Shell, BP, Esso, Uber, TfL, Trainline.
  - **Bills & Subscriptions**: Netflix, Spotify, Disney, Prime, EE, O2, Vodafone, British Gas.
  - **Shopping**: Amazon, eBay, Argos, Boots, Currys, Zara, H&M.
  - **Entertainment**: Steam, PlayStation, Xbox, Cinema, Ticketmaster.
- 📊 **Simple Dashboard**:
  - **Total Spent This Month** & **Today's Spend** in large, clear figures.
  - **Source Split**: `🔴 HSBC` vs `🔵 PayPal`.
  - **Category Breakdown**: Clean visual progress bars with percentages.
  - **Top Places You Spend**: Quick summary of your top 5 merchants.
  - **Recent Spending Feed**: Chronological list of expenses with timestamp and source tags.
- 🧪 **Built-in Simulation Tester**: Tap the "⚡ Test" button anytime to simulate real HSBC or PayPal notification spending alerts and test the app on any device.

---

## 🚀 How to Build the APK

### Method 1: Automatic Build via GitHub Actions (Zero Local Setup)
This repository includes a `.github/workflows/build-apk.yml` workflow.
1. Push this project to your GitHub repository (e.g. `https://github.com/19031987/spend-tracker.git`).
2. GitHub Actions will automatically run Ubuntu with Java 17 and Android SDK.
3. Once finished, download the ready-to-install **`SpendTracker-Debug-APK`** directly onto your Android phone!

### Method 2: Local Build in Android Studio
1. Open Android Studio.
2. Select **Open** and choose the `spend-tracker` folder.
3. Let Gradle sync and click **Run** (or `Build > Build Bundle(s) / APK(s) > Build APK(s)`).

---

## 📱 How to Use on Your Android Phone

1. Install the APK on your phone.
2. Open **SpendTracker**.
3. On first launch, tap the **"⚠️ Notification Access Required"** banner.
4. Android Settings will open: toggle **SpendTracker** to **ON** (Allow).
5. That's it! As you make purchases with your HSBC card or PayPal, SpendTracker will automatically log the spend, categorize it, and update your spending charts in real-time.
