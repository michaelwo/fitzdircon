# Getting Started: Ride Your NordicTrack with Zwift

This guide walks you through installing fitzdircon on your NordicTrack console so you can ride with Zwift. No technical experience required.

---

## What You'll Need

Before you start, make sure you have:

- **A NordicTrack treadmill or exercise bike** with the large built-in touchscreen (models that run the iFit app directly on the console — e.g. the S22i, S15i, T8.5, T9.5, RW900, etc.)
- **A Zwift account** and the Zwift app installed on a phone, tablet, Apple TV, or computer
- Both the **console and your Zwift device connected to the same Wi-Fi network**

> **Not sure if your console is compatible?** If your machine has a large built-in touchscreen that runs the iFit app, it's almost certainly supported. Machines with a small display or no built-in screen are not.

---

## How It Works

fitzdircon is a small app that runs quietly in the background on your console's built-in screen. Once installed, it makes your NordicTrack visible to Zwift the same way a smart trainer would be — so Zwift can control your incline and resistance, and your real speed, cadence, and power show up live in the game. You don't have to do anything extra to start it; it runs automatically every time the console powers on.

---

## Step 1 — Allow Apps from Outside the Play Store (one-time setup)

Your console's Android software blocks apps that didn't come from the Google Play Store by default. You need to turn this off once.

1. On the console touchscreen, swipe down from the top to open the notification shade, then tap the **gear icon** to open **Settings**.
2. Scroll down and tap **Apps** (may also be called **Application Manager**).
3. Tap the three-dot menu (⋮) in the top-right corner and choose **Special access** or **Install unknown apps**.
4. Find **Chrome** (or whichever browser is on the console) in the list and tap it.
5. Toggle **Allow from this source** to **on**.

You only need to do this once. You can turn it back off after installing fitzdircon if you prefer.

---

## Step 2 — Download fitzdircon on the Console

1. On the console, open the **Chrome** browser (or the built-in browser if Chrome isn't available). You may need to swipe up on the home screen to find it in the app drawer.
2. In the address bar, type or paste:
   ```
   github.com/michaelwo/fitzdircon/releases/latest
   ```
3. The page will show the latest release. Look for the file ending in **`-release.apk`** and tap it.
4. A download warning may appear — tap **Download anyway** or **Keep** to proceed.
5. Wait for the download to finish (the file is small — usually under 5 MB).

---

## Step 3 — Install fitzdircon

1. Once the download is complete, a prompt at the bottom of the browser may appear saying **"fitzdircon...apk downloaded"**. Tap **Open**.
   - If that prompt disappears, open the **Files** app (or **My Files**) on the console, go to **Downloads**, and tap the `.apk` file there.
2. Android will show an installation screen. Tap **Install**.
3. If you see a **"Play Protect"** warning, tap **Install anyway** — this is normal for apps not distributed through the Play Store.
4. Tap **Done** when installation finishes. fitzdircon is now installed.

---

## Step 4 — Open fitzdircon

1. Swipe up on the console home screen to open the app drawer and find **fitzdircon**, then tap it.
2. The app opens to a status screen. Within a few seconds it should show:
   - A **green** connection indicator
   - The console's **IP address** (something like `192.168.1.xx`)
   - The machine type detected: **BIKE** or **TREADMILL**
3. That's it — fitzdircon is running and your machine is ready to be found by Zwift.

> **From now on, fitzdircon starts automatically** every time the console powers on. You don't need to open it again before rides unless you want to check the status screen.

---

## Step 5 — Connect Zwift to Your Machine

1. Open Zwift on your phone, tablet, Apple TV, or PC and log in.
2. Before starting a ride, go to the **Pairing** screen (the screen where you select your sensors and power source).
3. Under **Controllable**, tap **Search**. Your NordicTrack should appear within a few seconds — it will be listed as **"iFit via fitzdircon"**.
4. Tap it to select it. You can also pair it as your **Power Source** and **Cadence** source for the most accurate stats.
5. Tap the checkmark or **OK** to save your selections.
6. Start a ride. Zwift will now adjust your incline and resistance automatically, and your real speed, cadence, and power will display in the game.

---

## Troubleshooting

**My machine doesn't appear in Zwift's pairing screen.**
- Make sure both the console and your Zwift device are on the **same Wi-Fi network** (e.g. not one on 2.4 GHz and the other on 5 GHz if your router treats them separately).
- Make sure fitzdircon is open and showing a green status on the console.
- Try force-quitting and reopening Zwift, then searching again.
- Restart the console (hold the power button → Restart) and reopen fitzdircon.

**fitzdircon shows a red or orange status / "Platform not available".**
- The iFit app may not have finished loading yet. Open the **iFit** app, wait for its home screen to fully load, then switch back to fitzdircon. It should turn green within a few seconds.
- If the console just powered on, give it 30–60 seconds for all background services to start before checking fitzdircon.

**Zwift found my machine but incline/resistance commands aren't working.**
- In Zwift's pairing screen, confirm your machine is paired under **Controllable** (not just Power or Cadence).
- Make sure you're riding a route that has gradient changes, or try a workout that sends explicit resistance commands (e.g. an ERG workout on a bike).

**"Install blocked" or the app won't install.**
- Double-check Step 1 — the browser you used to download the file must be the one that has "Allow from this source" enabled in Settings.
- If you see "App not installed", the download may be corrupt. Delete the `.apk` file and download it again.

**The app installed but I can't find it.**
- Swipe up (or tap the circle/square home button) to open the full app drawer. fitzdircon should be listed alphabetically under "F".

---

## Uninstalling

If you ever want to remove fitzdircon: go to **Settings → Apps**, find **fitzdircon** in the list, tap it, and tap **Uninstall**.
