package org.fitzdircon.ui;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.util.TypedValue;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.graphics.Typeface;

import java.net.Inet4Address;
import java.net.NetworkInterface;

import org.fitzdircon.BuildConfig;
import org.fitzdircon.R;
import org.fitzdircon.device.Device;
import org.fitzdircon.device.DeviceController;
import org.fitzdircon.dircon.DirectConnectCommandBridge;
import org.fitzdircon.dircon.DirectConnectServiceInfo;
import org.fitzdircon.dircon.ZwiftDirectConnectService;
import org.fitzdircon.platform.IFitPlatform;
import org.fitzdircon.platform.crash.CrashHandler;
import org.fitzdircon.telemetry.TelemetryHub;

public class MainActivity extends AppCompatActivity {

    private static final String LOG_TAG = "FZ:Main";

    private static SharedPreferences sharedPreferences;
    private IFitPlatform platform;
    private DeviceController activeController = null;
    private String localIpAddress = null;

    private final android.os.Handler heartbeatHandler =
            new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable heartbeatTick = new Runnable() {
        @Override public void run() {
            updateStatusList();
            heartbeatHandler.postDelayed(this, 5_000);
        }
    };

    public static final String PREF_DIRECT_CONNECT = ZwiftDirectConnectService.PREF_ENABLED;
    private static final String PREFS_FILE = "fitzdircon";
    public static SharedPreferences prefs() { return sharedPreferences; }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Thread.setDefaultUncaughtExceptionHandler(new CrashHandler(this));
        sharedPreferences = getSharedPreferences(PREFS_FILE, MODE_PRIVATE);

        if (getSupportActionBar() != null) {
            String buildId = BuildConfig.IS_CI_BUILD
                    ? "build " + BuildConfig.VERSION_CODE
                    : "dev-" + BuildConfig.GIT_HASH;
            getSupportActionBar().setSubtitle("v" + BuildConfig.VERSION_NAME + "  ·  " + buildId);
        }

        platform = IFitPlatform.detect(this);

        if (platform.available) {
            Device device = platform.createDevice(this);
            device.logger = (level, tag, msg) -> Log.println(level, tag, msg);
            TelemetryHub.configure(platform.createTelemetryReader(this));
            activeController = new DeviceController(device);
            DirectConnectCommandBridge.setSink(activeController::enqueueCommand);
            Log.i(LOG_TAG, "device: " + device.displayName());
        } else {
            Log.i(LOG_TAG, "iFit2 not available — Direct Connect inactive");
        }

        localIpAddress = getLocalIpAddress();
        startDirectConnect();
        updateStatusList();
    }

    private void startDirectConnect() {
        boolean enabled = sharedPreferences.getBoolean(PREF_DIRECT_CONNECT, true);
        Intent intent = new Intent(getApplicationContext(), ZwiftDirectConnectService.class);
        if (enabled) getApplicationContext().startService(intent);
        else getApplicationContext().stopService(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateStatusList();
        invalidateOptionsMenu();
        heartbeatHandler.postDelayed(heartbeatTick, 5_000);
    }

    @Override
    protected void onPause() {
        super.onPause();
        heartbeatHandler.removeCallbacks(heartbeatTick);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        menu.findItem(R.id.menu_direct_connect).setChecked(
                sharedPreferences.getBoolean(PREF_DIRECT_CONNECT, true));
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.menu_direct_connect) {
            boolean next = !item.isChecked();
            item.setChecked(next);
            sharedPreferences.edit().putBoolean(PREF_DIRECT_CONNECT, next).apply();
            startDirectConnect();
            updateStatusList();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void updateStatusList() {
        LinearLayout list = findViewById(R.id.statusList);
        if (list == null) return;
        list.removeAllViews();

        addRow(list, platform.available,
                "iFit2 / GlassOS",
                platform.available
                        ? "Available — " + machineClassLabel()
                        : "Not available — gRPC credentials not found; Zwift Direct Connect will not function on this console");

        if (platform.available) {
            String deviceName = activeController != null
                    ? activeController.device().displayName() : "unknown";
            addRow(list, activeController != null,
                    "Device",
                    deviceName);
        }

        addRow(list, true, "Local IP", localIpAddress != null ? localIpAddress : "no IP");

        boolean dcEnabled = sharedPreferences.getBoolean(PREF_DIRECT_CONNECT, true);
        boolean dcConnected = ZwiftDirectConnectService.connectedClient() != null;
        boolean dcAdvertising = ZwiftDirectConnectService.isAdvertising();
        String dcDetail;
        if (!dcEnabled) {
            dcDetail = "Disabled — enable from the overflow menu";
        } else if (dcConnected) {
            dcDetail = "Connected to Zwift client " + ZwiftDirectConnectService.connectedClient();
        } else if (dcAdvertising) {
            dcDetail = "Advertising " + DirectConnectServiceInfo.SERVICE_TYPE
                    + " on port " + DirectConnectServiceInfo.DEFAULT_PORT;
        } else if (ZwiftDirectConnectService.lastError() != null) {
            dcDetail = ZwiftDirectConnectService.lastError();
        } else {
            dcDetail = "Starting…";
        }
        addRow(list, dcEnabled && (dcAdvertising || dcConnected), "Zwift Direct Connect", dcDetail);
    }

    private String machineClassLabel() {
        switch (platform.machineClass) {
            case BIKE:      return "bike";
            case TREADMILL: return "treadmill";
            default:        return "machine class unknown";
        }
    }

    private String getLocalIpAddress() {
        try {
            for (NetworkInterface ni :
                    java.util.Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!ni.isUp() || ni.isLoopback()) continue;
                for (java.net.InetAddress addr :
                        java.util.Collections.list(ni.getInetAddresses())) {
                    if (!addr.isLoopbackAddress() && addr instanceof Inet4Address)
                        return addr.getHostAddress();
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    private void addRow(LinearLayout container, boolean ok, String name, String detail) {
        Context ctx = container.getContext();
        int px16 = dpToPx(ctx, 16);
        int px10 = dpToPx(ctx, 10);

        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(px16, px10, px16, px10);

        TextView dot = new TextView(ctx);
        dot.setText("● ");
        dot.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        dot.setTextColor(ok ? 0xFF4CAF50 : 0xFFFF5722);
        dot.setTypeface(null, Typeface.BOLD);

        TextView text = new TextView(ctx);
        text.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        android.text.SpannableStringBuilder sb = new android.text.SpannableStringBuilder();
        sb.append(name, new android.text.style.StyleSpan(Typeface.BOLD),
                android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        sb.append("  ");
        sb.append(detail);
        text.setText(sb);

        row.addView(dot);
        row.addView(text);
        container.addView(row);
    }

    private static int dpToPx(Context ctx, int dp) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, dp,
                ctx.getResources().getDisplayMetrics()));
    }
}
