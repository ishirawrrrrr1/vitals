# 🛑 IMPORTANT LOGIN INSTRUCTIONS 🛑

If you are seeing **"Invalid credentials"** when trying to log in via the Android App, even though you just created an account, it is because of one critical step:

### You MUST Restart Your Backend Server!
When I modify your `index.js` file to fix backend bugs (such as the payload mismatch that caused the login screen to reject your app), **the changes DO NOT automatically apply if the server is already running.** Node.js keeps the old code cached in memory until you stop it!

Since your server terminal has been running in the background, your Android app is still talking to the *old, buggy version* of the code.

#### How to Fix It Right Now:
1. Go to the Command Prompt or PowerShell window where you typed `node index.js`.
2. Press **`Ctrl + C`** on your keyboard to kill the active server.
3. Type **`node index.js`** again and hit Enter to start it fresh.

#### Default Accounts in Your Database
I have checked your MySQL database. If you want to log in immediately after restarting the server, these are the exact two accounts that are currently registered and working in your database:

**Admin Account:**
- **Email:** `admin@vitalsmonitor.local`
- **Password:** `admin123`

**Your New Test Account:**
- **Email:** `a@gmail.com`
- **Password:** `12345678`

**Restart the backend right now**, type those exact credentials into the Android app, and you will instantly break through to the main dashboard!
