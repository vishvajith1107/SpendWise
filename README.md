# SpendWise 💰

A lightweight, 100% offline native Android app written in **Kotlin** and **Jetpack Compose** that tracks your daily spending from bank debit SMS messages and tells you how much money you have left to spend for the day.

---

## 🔒 100% Offline & Private
* **Zero Network Permissions**: The app does **not** request `android.permission.INTERNET`.
* All transaction details, budgets, and SMS data stay 100% on your device inside an offline Room database.

---

## 🌟 Key Features
1. **Automatic Debit SMS Ingestion**:
   - `BroadcastReceiver` listening for incoming bank debit SMS messages.
   - Tuned specifically for **ICICI Bank** debit alerts (`AX-ICICIT-S`), plus generic Indian bank regex fallbacks (SBI, HDFC, Axis, etc.).
   - Automatically parses **Amount**, **Merchant / Beneficiary**, **Date**, and **UPI Reference ID**.
   - Filters out OTPs, balance queries, deposits, and non-spend alerts.
   - Deduplicates identical transactions using UPI reference IDs.
2. **Rollover Budget Engine**:
   - Configurable daily limit (e.g. ₹500, ₹1000).
   - Unspent budget from previous days automatically rolls over to increase today's allowance. Overspending carries over as a deficit.
   - One-tap rollover reset in Settings if you want to start clean from today.
3. **Jetpack Compose Material 3 UI**:
   - Dashboard card showing remaining budget, spent today, and rollover.
   - Color-coded: Forest green when within budget, red with warning when over budget.
   - Transaction list with delete confirmation.
   - Floating Action Button to manually log cash expenses.
4. **Home Screen Glance Widget**:
   - Live at-a-glance remaining balance and today's spend right on your home screen.
   - Automatically refreshes whenever a new debit SMS arrives or a transaction is logged.

---

## 🚀 How to Get Your APK

### Option A: Build via GitHub Actions (Zero Local Setup Required)
Since no local Android Studio or Java installation is required on your machine:
1. Create a new repository on your GitHub account (public or private).
2. Push this project folder to your GitHub repo:
   ```bash
   cd ~/.gemini/antigravity/scratch/SpendWise
   git remote add origin https://github.com/<your-username>/<your-repo-name>.git
   git branch -M main
   git push -u origin main
   ```
3. Go to the **Actions** tab on your GitHub repository.
4. The workflow `Build SpendWise APK` will run automatically.
5. Once complete (~2 minutes), click on the run and download the **`SpendWise-Debug-APK`** artifact directly to your phone!

### Option B: Build Locally in Android Studio
1. Open **Android Studio** (Koala / Ladybug or newer).
2. Select **Open** and choose `/Users/vishvajithlogasenthilkumar/.gemini/antigravity/scratch/SpendWise`.
3. Let Gradle sync.
4. Connect your phone or emulator and click **Run** (or run `./gradlew assembleDebug` in terminal).
