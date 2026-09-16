# 🛡️ SpendTracker Privacy Policy

**Effective Date:** 7 September 2026  
**Last Updated:** 7 September 2026  

SpendTracker ("we", "our", or "the App") is committed to protecting your personal and financial privacy. This Privacy Policy outlines how your data is collected, used, and protected.

---

### 1. Data Collection & Processing
- **Notification Access (`BIND_NOTIFICATION_LISTENER_SERVICE`)**:
  - The App requests notification listener access exclusively to read transaction push alerts issued by supported banking and payment apps installed on your device (including **Chase, Monzo, Revolut, American Express, Barclays, Apple Pay, Google Pay, Starling, HSBC, and PayPal**).
  - The App strictly parses spending amounts, merchant names, and payment sources (e.g. "You spent £18.50 at Asda").
  - Unrelated notifications (messages, social media, emails, etc.) are discarded immediately and never stored or read.
- **Zero Cloud Transmission**:
  - **No financial data leaves your device.** The App operates 100% offline.
  - All parsed transactions, categories, and totals are stored in a local SQLite database residing solely in your phone's internal storage.
- **No Third-Party Sharing**:
  - We do not sell, transfer, or share your financial data, spending patterns, or personal information with any third party, advertiser, or remote server.

---

### 2. Permissions Required
- `android.permission.BIND_NOTIFICATION_LISTENER_SERVICE`: Required strictly to detect real-time transaction notifications when you make a card or online purchase.

---

### 3. Data Retention & Deletion
- All data remains on your device until you clear the app's cache/storage or uninstall the App from your device.
- Uninstalling the App permanently deletes all stored spending records.

---

### 4. Contact Us
For inquiries regarding this privacy policy, contact:  
`developer@spendtracker.local`
