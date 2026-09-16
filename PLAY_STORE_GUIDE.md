# 🚀 Google Play Store Publishing Guide for SpendTracker

This guide walks you through every step needed to build and publish **SpendTracker** to the **Google Play Store**.

---

## 📦 Step 1: Build the Release APK & AAB (App Bundle)

> [!NOTE]
> Since August 2021, Google Play requires an **Android App Bundle (`.aab`)**, not an `.apk`, for all new app releases.  
> Your project is configured to produce **both**:
> 1. `SpendTracker-Release-APK` (for instant sideload testing on your phone)
> 2. `SpendTracker-GooglePlay-AAB` (for uploading to Google Play Console)

### 🌟 Easiest Method: Automatic Build via GitHub Actions (Zero Local Installs)
1. Initialize a git repository in `spend-tracker` and push to GitHub:
   ```bash
   cd spend-tracker
   git init
   git add .
   git commit -m "feat: initial SpendTracker release build"
   git branch -M main
   git remote add origin https://github.com/<your-username>/spend-tracker.git
   git push -u origin main
   ```
2. Open your repository on GitHub and click the **Actions** tab.
3. The workflow **"Build SpendTracker Android APK"** will run on Ubuntu with full Android SDK and Java 17.
4. When finished (2-3 minutes), click the completed run to download:
   - **`SpendTracker-GooglePlay-AAB`** (Upload this to Google Play!)
   - **`SpendTracker-Release-APK`** (Directly installs on any phone)

---

## 🔑 Keystore Signing Details (Already Configured)
Your app bundle is signed with the production keystore:
- **Keystore File:** `release-keystore.jks`
- **Alias:** `spendtracker`
- **Store Password:** `SpendTracker123!`
- **Key Password:** `SpendTracker123!`

---

## 🌐 Step 2: Google Play Developer Console Setup

1. **Log in to Google Play Console**:
   Go to [https://play.google.com/console](https://play.google.com/console).  
   *(If you don't have an account, Google charges a one-time \$25 fee).*

2. **Create New App**:
   - Click **Create app** (top right).
   - **App name:** `SpendTracker: Expense Manager`
   - **Default language:** English (United Kingdom / United States)
   - **App or game:** App
   - **Free or paid:** Free
   - Accept declarations and click **Create app**.

---

## 📝 Step 3: Store Listing Copy (Ready to Paste)

### Short Description (max 80 chars):
> Real-time spending tracker for HSBC card purchases and PayPal payment alerts.

### Full Description (max 4000 chars):
```
SpendTracker is a simple, private personal finance tracker designed specifically to track where and how much you spend in real time using your HSBC and PayPal notifications.

✨ KEY FEATURES:
• Real-Time Spend Tracking: Automatically detects when you make purchases using HSBC cards or PayPal via system notification alerts.
• Instant Extraction: Pulls the exact amount, payment source, and clean merchant name automatically.
• Smart Auto-Categorization: Classifies spending into Groceries (Tesco, Sainsbury's, Asda), Dining & Drinks (Costa, Deliveroo, Uber Eats), Transport & Fuel (Shell, BP, Uber, TfL), Bills & Subscriptions (Netflix, Spotify, broadband), Shopping, and Entertainment.
• Intuitive Monthly Analytics: Displays your total spending this month, today's spend, and side-by-side HSBC vs PayPal breakdown.
• Top Places: View where your money goes with a ranking of your top merchants.
• 100% Private & Local: Zero cloud sync. All transaction data resides entirely on your device in local encrypted storage. No external servers or tracking.
```

---

## 📋 Step 4: Policy & Sensitive Permission Declarations

In the Google Play Console under **Policy and programs > App content**:

1. **Privacy Policy**:
   - Paste the link to your `PRIVACY_POLICY.md` (e.g. host it on GitHub Pages or paste into a public Gist).
2. **Financial Features**:
   - Select **Personal finance management / expense tracking**.
3. **Data Safety**:
   - Data collected? **No** (Data is processed locally on device and never transmitted off-device).
4. **Target Audience**:
   - Select **18 and over**.
5. **Notification Listener (`BIND_NOTIFICATION_LISTENER_SERVICE`) Declaration**:
   - When asked why the app requires notification access:
     > *"The app uses NotificationListenerService solely to read local payment notifications from the user's HSBC and PayPal banking apps in real time to categorize personal spending. No notifications are shared, stored remotely, or transmitted off the device."*

---

## 🚀 Step 5: Upload the AAB & Publish

1. In Play Console, navigate to **Release > Production** (or **Closed testing**).
2. Click **Create new release**.
3. Under **App bundles**, upload `app-release.aab`.
4. Enter Release notes:
   > `Initial release: Automated real-time spend tracking and categorization for HSBC and PayPal.`
5. Click **Next** and **Save**.
6. Submit for review! (Google typically approves within 24 to 72 hours).
