package com.swift.radiohome;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.location.GpsSatellite;
import android.location.GpsStatus;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.SystemClock;
import android.provider.Settings;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.util.SparseArray;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Home screen for the ADAYO AC822X head unit (Android 4.2.2, 800x480, resistive touch). One fixed screen plus three
 * lists built only while they are open (all apps, open apps, picking an app for a big tile): no widgets, no
 * wallpaper, no database, so it stays a few MB.
 */
public class MainActivity extends Activity {

    private static final Locale ES = new Locale("es");

    /** title, subtitle, icon, icon colour, tile colour, pressed colour, package, activity (null = its launcher). */
    private static final Object[][] BIG = {
            {"Waze", "Navegar", IconView.NAV, 0xff38bdf8, 0xff0f2a3a, 0xff1a4157, "com.waze", null},
            {"YouTube", "RadioTube", IconView.PLAY, 0xfff87171, 0xff3a1212, 0xff571c1c,
                    "bo.reproductor.adayo", "bo.reproductor.adayo.InicioActivity"},
            {"Radio", "AM / FM", IconView.RADIO, 0xfffbbf24, 0xff33230c, 0xff4d3614, "com.adayo.radio", "com.adayo.radio.Radio"},
            {"Teléfono", "Bluetooth", IconView.PHONE, 0xffc084fc, 0xff2a1533, 0xff3f204d,
                    "com.adayo.bluetooth", "com.adayo.bluetooth.hfp.HfpMainActivity"},
    };
    /** Icons a chosen app's big tile can wear; 0 = the app's own icon. */
    private static final int[] STYLE_ICONS = {-1, IconView.TV, IconView.MOVIE, IconView.TV_MOVIE, IconView.PLAY,
            IconView.MUSIC, IconView.RADIO, IconView.NAV};
    private static final String[] STYLE_LABELS = {"De la app", "TV", "Películas", "TV y cine", "Video", "Música",
            "Radio", "Navegar"};
    /** Tile colours: icon, fill, pressed. The first six are the Home's own. */
    private static final int[][] PALETTE = {
            {0xfff87171, 0xff3a1212, 0xff571c1c}, {0xfffbbf24, 0xff33230c, 0xff4d3614},
            {0xff4ade80, 0xff0f2e1c, 0xff17452a}, {0xff2dd4bf, 0xff0c2e2b, 0xff12453f},
            {0xff38bdf8, 0xff0f2a3a, 0xff1a4157}, {0xffc084fc, 0xff2a1533, 0xff3f204d},
            {0xfffb923c, 0xff3a1d0c, 0xff572c13}, {0xffd4d4d4, 0xff1c1c1c, 0xff2a2a2a}};
    private static final String[] PALETTE_NAMES = {"Rojo", "Ámbar", "Verde", "Turquesa", "Celeste", "Morado",
            "Naranja", "Gris"};

    private static final Object[][] SMALL = {
            {"Música BT", IconView.BLUETOOTH, "com.adayo.bluetooth", "com.adayo.bluetooth.a2dp.A2dpMainActivity"},
            {"USB", IconView.USB, "com.adayo.media", "com.adayo.media.MediaActivity$USB1Activity"},
            {"Ecualizador", IconView.EQ, "com.adayo.eq", "com.adayo.eq.MainActivity"},
    };

    /** A speed older than this is stale: the fix was lost (tunnel, parking garage). */
    private static final long SPEED_MAX_AGE_MS = 5000;

    private Typeface extraLight, light, medium;
    private float density;
    private SharedPreferences tiles;
    private SharedPreferences ui;

    private FrameLayout root;
    private View home, apps, open, style;
    private LinearLayout bigRow;
    private TextView clock, date, gpsValue, gpsUnit, wifiText, openCount;
    private IconView wifiIcon;
    private TextView gridCaption, gridTitle, gridBackLabel, gridAction;
    private GridView grid;
    private LinearLayout openList;
    private TextView freeRam, openTitleCount;
    private View closeAll;
    private LocationManager locationManager;

    private int satellitesUsed;
    private int speedKmh = -1;
    private long speedAt;
    /** Big tile being reassigned in the app grid, or -1 when the grid just lists every app. */
    private int pickSlot = -1;

    private final BroadcastReceiver timeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            updateClock();
        }
    };

    private final BroadcastReceiver networkReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            updateWifi();
        }
    };

    private final GpsStatus.Listener gpsListener = new GpsStatus.Listener() {
        @Override
        public void onGpsStatusChanged(int event) {
            if (event == GpsStatus.GPS_EVENT_SATELLITE_STATUS) countSatellites();
            else if (event == GpsStatus.GPS_EVENT_STOPPED) satellitesUsed = 0;
            updateGps();
        }
    };

    private final LocationListener speedListener = new LocationListener() {
        @Override
        public void onLocationChanged(Location location) {
            speedKmh = location.hasSpeed() ? Math.round(location.getSpeed() * 3.6f) : -1;
            speedAt = SystemClock.elapsedRealtime();
            updateGps();
        }

        @Override
        public void onStatusChanged(String provider, int status, Bundle extras) {
        }

        @Override
        public void onProviderEnabled(String provider) {
        }

        @Override
        public void onProviderDisabled(String provider) {
            speedKmh = -1;
            updateGps();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        density = getResources().getDisplayMetrics().density;
        extraLight = Typeface.createFromAsset(getAssets(), "fonts/outfit_extralight.ttf");
        light = Typeface.createFromAsset(getAssets(), "fonts/outfit_light.ttf");
        medium = Typeface.createFromAsset(getAssets(), "fonts/outfit_medium.ttf");
        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        tiles = getSharedPreferences("tiles", MODE_PRIVATE);
        ui = getSharedPreferences("ui", MODE_PRIVATE);

        root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        home = buildHome();
        apps = buildApps();
        open = buildOpen();
        style = buildStyle();
        apps.setVisibility(View.GONE);
        open.setVisibility(View.GONE);
        style.setVisibility(View.GONE);
        root.addView(home);
        root.addView(apps);
        root.addView(open);
        root.addView(style);
        setContentView(root);
        setDimmed(ui.getBoolean("dim", false));
    }

    @Override
    protected void onResume() {
        super.onResume();
        IntentFilter f = new IntentFilter(Intent.ACTION_TIME_TICK);
        f.addAction(Intent.ACTION_TIME_CHANGED);
        f.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        registerReceiver(timeReceiver, f);
        IntentFilter n = new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION);
        n.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
        registerReceiver(networkReceiver, n);
        updateClock();
        updateWifi();
        satellitesUsed = 0;
        speedKmh = -1;
        updateGps();
        try {
            // The head unit's own service keeps the GPS running, so these only listen alongside it.
            locationManager.addGpsStatusListener(gpsListener);
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 0, speedListener);
        } catch (RuntimeException ignored) {
            // No permission or no GPS provider: the chip keeps its dash.
        }
        refreshOpenCount();
        refreshStartSource();
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(timeReceiver);
        unregisterReceiver(networkReceiver);
        try {
            locationManager.removeGpsStatusListener(gpsListener);
            locationManager.removeUpdates(speedListener);
        } catch (RuntimeException ignored) {
            // Never registered (see onResume).
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // Home pressed while the launcher is already up: back to the main screen.
        showHome();
    }

    @Override
    public void onBackPressed() {
        // Home has nowhere to go back to.
        showHome();
    }

    // ---- Home screen ----

    private View buildHome() {
        LinearLayout col = vertical();
        col.setPadding(dp(24), dp(20), dp(24), dp(22));

        // Header (design "A · Velocímetro"): the speed is what a driver glances at, so it is the biggest thing on
        // screen; the clock is smaller beside it (tap: the head unit's date and time settings); status buttons are
        // 52dp with 24dp icons, comfortable for a finger while driving.
        LinearLayout header = horizontal();
        header.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout speed = horizontal();
        speed.setGravity(Gravity.BOTTOM);
        speed.setMinimumWidth(dp(150));
        gpsValue = text(92, light, 0xffffffff);
        gpsValue.setLineSpacing(0, 0.82f);
        speed.addView(gpsValue, wrap());
        LinearLayout unit = vertical();
        unit.setPadding(dp(8), 0, 0, dp(6));
        unit.addView(text(22, medium, 0xffe6e6e6, "km/h"), wrap());
        LinearLayout sats = horizontal();
        sats.setGravity(Gravity.CENTER_VERTICAL);
        sats.setPadding(0, dp(6), 0, 0);
        sats.addView(new IconView(this, IconView.GPS, 0xff4ade80, 2.4f), new LinearLayout.LayoutParams(dp(14), dp(14)));
        gpsUnit = text(14, light, 0xffa3a3a3);
        gpsUnit.setPadding(dp(4), 0, 0, 0);
        sats.addView(gpsUnit, wrap());
        unit.addView(sats, wrap());
        speed.addView(unit, wrap());
        header.addView(speed, wrap());

        View divider = new View(this);
        divider.setBackgroundColor(0xff262626);
        LinearLayout.LayoutParams dvp = new LinearLayout.LayoutParams(dp(1), dp(76));
        dvp.leftMargin = dp(18);
        dvp.rightMargin = dp(18);
        header.addView(divider, dvp);

        LinearLayout time = vertical();
        time.setBackgroundDrawable(background(Color.TRANSPARENT, 0xff1a1a1a, 0, 12));
        clock = text(48, extraLight, 0xfff2f2f2);
        time.addView(clock, wrap());
        date = text(16, light, 0xffc4c4c4);
        date.setPadding(dp(2), dp(4), 0, 0);
        time.addView(date, wrap());
        time.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startSettings(Settings.ACTION_DATE_SETTINGS);
            }
        });
        header.addView(time, wrap());
        header.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1));

        // Wi-Fi network (the phone's hotspot in the car); tap: Wi-Fi settings.
        LinearLayout wifi = chip(IconView.WIFI, 0xff737373);
        wifiIcon = (IconView) wifi.getChildAt(0);
        wifiText = (TextView) wifi.getChildAt(1);
        wifiText.setSingleLine(true);
        wifiText.setEllipsize(TextUtils.TruncateAt.END);
        wifiText.setMaxWidth(dp(84));
        wifi.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startSettings(Settings.ACTION_WIFI_SETTINGS);
            }
        });
        header.addView(wifi, chipParams(0));

        LinearLayout bt = iconChip(IconView.BLUETOOTH, 0xff60a5fa);
        bt.setOnClickListener(launcher("com.adayo.bluetooth", "com.adayo.bluetooth.hfp.HfpMainActivity"));
        header.addView(bt, iconChipParams());

        // Only when the next car start would open a system source (FM static...): tap to start quiet.
        startChip = chip(IconView.RADIO, 0xfff87171);
        startText = (TextView) startChip.getChildAt(1);
        startChip.setVisibility(View.GONE);
        startChip.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                clearStartSource();
            }
        });
        header.addView(startChip, chipParams(10));

        // Dim Home on demand (glare while driving): tap to darken, tap again for normal. Remembered across starts.
        LinearLayout dim = iconChip(IconView.MOON, 0xffa3a3a3);
        dimIcon = (IconView) dim.getChildAt(0);
        dim.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setDimmed(!ui.getBoolean("dim", false));
            }
        });
        header.addView(dim, iconChipParams());

        // Open apps: how many, and tap to see and close them.
        LinearLayout running = chip(IconView.STACK, 0xfffbbf24);
        openCount = (TextView) running.getChildAt(1);
        openCount.setTypeface(medium);
        openCount.setText("–");
        running.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showOpen();
            }
        });
        header.addView(running, chipParams(10));
        col.addView(header, new LinearLayout.LayoutParams(-1, dp(98)));

        // Four big tiles; hold one to choose which app it opens.
        bigRow = horizontal();
        for (int i = 0; i < BIG.length; i++) bigRow.addView(bigTile(i), cell(i));
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(-1, dp(196));
        bp.topMargin = dp(16);
        col.addView(bigRow, bp);

        // Small tiles, the last one opening every app.
        LinearLayout small = horizontal();
        for (int i = 0; i < SMALL.length; i++) {
            Object[] t = SMALL[i];
            LinearLayout tile = smallTile((String) t[0], (Integer) t[1], 0xffa3a3a3, 0xff111111, 0xff222222);
            tile.setOnClickListener(launcher((String) t[2], (String) t[3]));
            small.addView(tile, cell(i));
        }
        LinearLayout all = smallTile("Todas", IconView.GRID, Color.WHITE, 0xff1a1a1a, 0xff333333);
        all.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showApps(-1);
            }
        });
        small.addView(all, cell(3));
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-1, 0, 1);
        sp.topMargin = dp(16);
        col.addView(small, sp);
        return col;
    }

    /** Big tile {@code slot}: its stock app, or the one chosen for it, shown with that app's own icon and name. */
    private LinearLayout bigTile(final int slot) {
        Object[] t = BIG[slot];
        LinearLayout tile = vertical();
        tile.setPadding(dp(18), dp(18), dp(18), dp(18));
        ActivityInfo chosen = chosenApp(slot);
        int styleIcon = chosen == null ? 0 : tiles.getInt("icon" + slot, 0);
        int styleColor = chosen == null ? -1 : tiles.getInt("color" + slot, -1);
        int fg = styleColor >= 0 ? PALETTE[styleColor][0] : (Integer) t[3];
        tile.setBackgroundDrawable(background(styleColor >= 0 ? PALETTE[styleColor][1] : (Integer) t[4],
                styleColor >= 0 ? PALETTE[styleColor][2] : (Integer) t[5], 0, 22));
        if (chosen == null) {
            tile.addView(new IconView(this, (Integer) t[2], (Integer) t[3], 2f), new LinearLayout.LayoutParams(dp(52), dp(52)));
        } else if (styleIcon > 0) {
            tile.addView(new IconView(this, STYLE_ICONS[styleIcon], fg, 2f), new LinearLayout.LayoutParams(dp(52), dp(52)));
        } else {
            ImageView icon = new ImageView(this);
            icon.setImageDrawable(chosen.loadIcon(getPackageManager()));
            tile.addView(icon, new LinearLayout.LayoutParams(dp(52), dp(52)));
        }
        tile.addView(new View(this), new LinearLayout.LayoutParams(1, 0, 1));
        TextView title = text(26, medium, 0xfff2f2f2, chosen == null ? (String) t[0]
                : chosen.loadLabel(getPackageManager()).toString());
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        tile.addView(title, wrap());
        tile.addView(text(13, light, 0xffb8b8b8, chosen == null ? (String) t[1]
                : styleIcon > 0 ? STYLE_LABELS[styleIcon] : " "), wrap());
        tile.setOnClickListener(chosen == null ? launcher((String) t[6], (String) t[7])
                : launcher(chosen.packageName, chosen.name));
        tile.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                showApps(slot);
                return true;
            }
        });
        return tile;
    }

    /** The app chosen for a big tile, or null for its stock one (also when the chosen app was uninstalled). */
    private ActivityInfo chosenApp(int slot) {
        String saved = tiles.getString("big" + slot, null);
        if (saved == null) return null;
        ComponentName cn = ComponentName.unflattenFromString(saved);
        if (cn == null) return null;
        // Picking the tile's own stock app means "back to stock": its drawn icon and captions,
        // not the app's PNG icon and bare name (YouTube -> Chrome -> RadioTube lost the ▶ icon).
        if (cn.getPackageName().equals(BIG[slot][6])) return null;
        try {
            return getPackageManager().getActivityInfo(cn, 0);
        } catch (PackageManager.NameNotFoundException e) {
            return null;
        }
    }

    private void replaceBigTile(int slot) {
        bigRow.removeViewAt(slot);
        bigRow.addView(bigTile(slot), slot, cell(slot));
    }

    private LinearLayout smallTile(String label, int icon, int iconColor, int fill, int stroke) {
        LinearLayout tile = horizontal();
        tile.setGravity(Gravity.CENTER_VERTICAL);
        tile.setPadding(dp(16), 0, dp(12), 0);
        tile.setBackgroundDrawable(background(fill, 0xff2a2a2a, stroke, 18));
        tile.addView(new IconView(this, icon, iconColor, 1.8f), new LinearLayout.LayoutParams(dp(28), dp(28)));
        TextView name = text(18, light, 0xffe6e6e6, label);
        name.setPadding(dp(12), 0, 0, 0);
        name.setSingleLine(true);
        name.setEllipsize(TextUtils.TruncateAt.END);
        tile.addView(name, wrap());
        return tile;
    }

    /** A 52dp status button: 24dp icon and a label. */
    private LinearLayout chip(int icon, int iconColor) {
        LinearLayout chip = horizontal();
        chip.setGravity(Gravity.CENTER_VERTICAL);
        chip.setPadding(dp(14), 0, dp(16), 0);
        chip.setBackgroundDrawable(background(0xff161616, 0xff2a2a2a, 0xff2a2a2a, 16));
        chip.addView(new IconView(this, icon, iconColor, 2f), new LinearLayout.LayoutParams(dp(24), dp(24)));
        TextView label = text(17, light, 0xffe6e6e6);
        label.setPadding(dp(8), 0, 0, 0);
        chip.addView(label, wrap());
        return chip;
    }

    /** A square 52dp status button with only its icon. */
    private LinearLayout iconChip(int icon, int iconColor) {
        LinearLayout chip = horizontal();
        chip.setGravity(Gravity.CENTER);
        chip.setBackgroundDrawable(background(0xff161616, 0xff2a2a2a, 0xff2a2a2a, 16));
        chip.addView(new IconView(this, icon, iconColor, 2f), new LinearLayout.LayoutParams(dp(24), dp(24)));
        return chip;
    }

    // ---- App grid: every app, or picking one for a big tile ----

    private View buildApps() {
        LinearLayout col = vertical();
        col.setPadding(dp(24), dp(20), dp(24), dp(22));

        FrameLayout header = new FrameLayout(this);
        LinearLayout back = pill(IconView.BACK);
        gridBackLabel = (TextView) back.getChildAt(1);
        back.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showHome();
            }
        });
        header.addView(back, new FrameLayout.LayoutParams(-2, dp(52), Gravity.LEFT | Gravity.CENTER_VERTICAL));
        LinearLayout titles = vertical();
        titles.setGravity(Gravity.CENTER_HORIZONTAL);
        gridCaption = text(12, medium, 0xfff87171);
        titles.addView(gridCaption, wrap());
        gridTitle = text(30, light, 0xfff2f2f2);
        titles.addView(gridTitle, wrap());
        header.addView(titles, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));
        LinearLayout action = pill(IconView.GEAR);
        gridAction = (TextView) action.getChildAt(1);
        action.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (pickSlot < 0) {
                    startSettings(Settings.ACTION_SETTINGS);
                } else {
                    // Back to the tile's stock app.
                    resetBigTile(pickSlot);
                    replaceBigTile(pickSlot);
                    showHome();
                }
            }
        });
        header.addView(action, new FrameLayout.LayoutParams(-2, dp(52), Gravity.RIGHT | Gravity.CENTER_VERTICAL));
        col.addView(header, new LinearLayout.LayoutParams(-1, dp(56)));

        grid = new GridView(this);
        grid.setNumColumns(5);
        grid.setHorizontalSpacing(dp(12));
        grid.setVerticalSpacing(dp(12));
        grid.setSelector(new GradientDrawable());
        grid.setVerticalScrollBarEnabled(false);
        grid.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                ResolveInfo ri = (ResolveInfo) parent.getItemAtPosition(position);
                ComponentName cn = new ComponentName(ri.activityInfo.packageName, ri.activityInfo.name);
                if (pickSlot < 0) {
                    start(cn, ri.activityInfo.packageName);
                } else {
                    if (cn.getPackageName().equals(BIG[pickSlot][6])) {
                        resetBigTile(pickSlot); // its stock app: back to stock
                        replaceBigTile(pickSlot);
                        showHome();
                    } else {
                        showStyle(pickSlot, cn, ri.loadLabel(getPackageManager()).toString()); // then its icon and colour
                    }
                }
            }
        });
        LinearLayout.LayoutParams gp = new LinearLayout.LayoutParams(-1, 0, 1);
        gp.topMargin = dp(18);
        col.addView(grid, gp);
        return col;
    }

    /** @param slot big tile to reassign, or -1 to list every app. */
    private void showApps(int slot) {
        pickSlot = slot;
        String current = null;
        if (slot < 0) {
            gridCaption.setVisibility(View.GONE);
            gridTitle.setText("Todas las apps");
            gridBackLabel.setText("Inicio");
            gridAction.setText("Ajustes");
            ((View) gridAction.getParent()).findViewWithTag("icon").setVisibility(View.VISIBLE);
        } else {
            ActivityInfo chosen = chosenApp(slot);
            current = chosen != null ? chosen.packageName : (String) BIG[slot][6];
            String now = chosen != null ? chosen.loadLabel(getPackageManager()).toString() : (String) BIG[slot][0];
            gridCaption.setText(("Botón " + (slot + 1) + " · ahora: " + now).toUpperCase(ES));
            gridCaption.setVisibility(View.VISIBLE);
            gridTitle.setText("Elige qué app abre");
            gridBackLabel.setText("Cancelar");
            gridAction.setText("Restablecer");
            ((View) gridAction.getParent()).findViewWithTag("icon").setVisibility(View.GONE);
        }
        PackageManager pm = getPackageManager();
        Intent main = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> found = pm.queryIntentActivities(main, 0);
        final List<ResolveInfo> list = new ArrayList<ResolveInfo>();
        for (ResolveInfo ri : found) {
            if (!getPackageName().equals(ri.activityInfo.packageName)) list.add(ri);
        }
        Collections.sort(list, new ResolveInfo.DisplayNameComparator(pm));
        grid.setAdapter(new AppsAdapter(list, current));
        show(apps);
    }

    private final class AppsAdapter extends BaseAdapter {
        private final List<ResolveInfo> items;
        private final String current;
        // Icons decoded once per opening of the list, not on every scroll rebind; dropped with the adapter.
        private final SparseArray<Drawable> icons = new SparseArray<Drawable>();

        AppsAdapter(List<ResolveInfo> items, String current) {
            this.items = items;
            this.current = current;
        }

        @Override
        public int getCount() {
            return items.size();
        }

        @Override
        public Object getItem(int position) {
            return items.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            LinearLayout tile = (LinearLayout) convertView;
            if (tile == null) {
                tile = vertical();
                tile.setGravity(Gravity.CENTER);
                tile.setPadding(dp(6), 0, dp(6), 0);
                tile.setLayoutParams(new GridView.LayoutParams(-1, dp(112)));
                tile.addView(new ImageView(MainActivity.this), new LinearLayout.LayoutParams(dp(44), dp(44)));
                TextView name = text(15, light, 0xffe6e6e6);
                name.setGravity(Gravity.CENTER);
                name.setSingleLine(true);
                name.setEllipsize(TextUtils.TruncateAt.END);
                name.setPadding(0, dp(8), 0, 0);
                tile.addView(name, new LinearLayout.LayoutParams(-1, -2));
            }
            ResolveInfo ri = items.get(position);
            // The app a big tile opens now is outlined while picking.
            boolean selected = ri.activityInfo.packageName.equals(current);
            tile.setBackgroundDrawable(selected ? background(0xff3a1212, 0xff571c1c, 0xfff87171, 16)
                    : background(0xff111111, 0xff2a2a2a, 0xff222222, 16));
            PackageManager pm = getPackageManager();
            Drawable icon = icons.get(position);
            if (icon == null) {
                icon = ri.loadIcon(pm);
                icons.put(position, icon);
            }
            ((ImageView) tile.getChildAt(0)).setImageDrawable(icon);
            ((TextView) tile.getChildAt(1)).setText(ri.loadLabel(pm));
            return tile;
        }
    }

    // ---- Open apps ----

    private View buildOpen() {
        LinearLayout col = vertical();
        col.setPadding(dp(24), dp(20), dp(24), dp(22));

        FrameLayout header = new FrameLayout(this);
        LinearLayout back = pill(IconView.BACK);
        ((TextView) back.getChildAt(1)).setText("Inicio");
        back.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showHome();
            }
        });
        header.addView(back, new FrameLayout.LayoutParams(-2, dp(52), Gravity.LEFT | Gravity.CENTER_VERTICAL));
        LinearLayout title = horizontal();
        title.setGravity(Gravity.CENTER_VERTICAL);
        title.addView(text(30, light, 0xfff2f2f2, "Apps abiertas"), wrap());
        openTitleCount = text(30, medium, 0xfffbbf24); // same amber as the Home chip
        openTitleCount.setPadding(dp(10), 0, 0, 0);
        title.addView(openTitleCount, wrap());
        // Left, after "Inicio" (not centred): centred it ran into RAM + "Cerrar todas" on the right.
        FrameLayout.LayoutParams tp = new FrameLayout.LayoutParams(-2, -2, Gravity.LEFT | Gravity.CENTER_VERTICAL);
        tp.leftMargin = dp(140);
        header.addView(title, tp);

        // Right side: free RAM, then "Cerrar todas" as a compact pill (it was a full-width red bar below the list).
        LinearLayout right = horizontal();
        right.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout ram = vertical();
        ram.setGravity(Gravity.RIGHT);
        ram.addView(text(12, medium, 0xffa3a3a3, "RAM LIBRE"), wrap());
        freeRam = text(22, light, 0xfff2f2f2);
        ram.addView(freeRam, wrap());
        right.addView(ram, wrap());
        LinearLayout all = horizontal();
        all.setGravity(Gravity.CENTER_VERTICAL);
        all.setPadding(dp(14), 0, dp(18), 0);
        all.setBackgroundDrawable(background(0xff1a0d0d, 0xff3a1414, 0xff7f1d1d, 26));
        all.addView(new IconView(this, IconView.CLOSE, 0xfffca5a5, 2.4f), new LinearLayout.LayoutParams(dp(18), dp(18)));
        TextView allLabel = text(17, light, 0xfffca5a5, "Cerrar todas");
        allLabel.setPadding(dp(8), 0, 0, 0);
        all.addView(allLabel, wrap());
        all.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                List<String> pkgs = new ArrayList<String>();
                for (int i = 0; i < openList.getChildCount(); i++) {
                    Object tag = openList.getChildAt(i).getTag();
                    if (tag instanceof String) pkgs.add((String) tag);
                }
                close(pkgs);
            }
        });
        LinearLayout.LayoutParams ap = new LinearLayout.LayoutParams(-2, dp(52));
        ap.leftMargin = dp(16);
        right.addView(all, ap);
        closeAll = all;
        header.addView(right, new FrameLayout.LayoutParams(-2, -2, Gravity.RIGHT | Gravity.CENTER_VERTICAL));
        col.addView(header, new LinearLayout.LayoutParams(-1, dp(56)));

        ScrollView scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        openList = vertical();
        scroll.addView(openList, new FrameLayout.LayoutParams(-1, -2));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, 0, 1);
        lp.topMargin = dp(14);
        col.addView(scroll, lp);

        return col;
    }

    private void showOpen() {
        show(open);
        loadOpen("Buscando…");
    }

    /** Lists the open apps off the UI thread (reading their memory takes a while), with {@code status} meanwhile. */
    private void loadOpen(String status) {
        openList.removeAllViews();
        openList.addView(emptyText(status));
        closeAll.setVisibility(View.GONE);
        final Context app = getApplicationContext();
        new Thread(new Runnable() {
            @Override
            public void run() {
                final List<RunningApps.App> list = RunningApps.list(app);
                final int free = RunningApps.freeRamMb(app);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (open.getVisibility() != View.VISIBLE) return;
                        fillOpen(list, free);
                    }
                });
            }
        }, "open-apps").start();
    }

    private void fillOpen(List<RunningApps.App> list, int freeMb) {
        freeRam.setText(freeMb + " MB");
        openCount.setText(String.valueOf(list.size()));
        openTitleCount.setText(list.isEmpty() ? "" : String.valueOf(list.size()));
        openList.removeAllViews();
        if (list.isEmpty()) {
            openList.addView(emptyText("No hay apps abiertas"));
            return;
        }
        for (final RunningApps.App a : list) {
            LinearLayout row = horizontal();
            row.setTag(a.pkg);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(16), 0, dp(8), 0);
            row.setBackgroundDrawable(shape(0xff111111, 0xff222222, 16));
            ImageView icon = new ImageView(this);
            icon.setImageDrawable(a.icon);
            row.addView(icon, new LinearLayout.LayoutParams(dp(40), dp(40)));
            TextView name = text(20, light, 0xfff2f2f2, a.label.toString());
            name.setSingleLine(true);
            name.setEllipsize(TextUtils.TruncateAt.END);
            name.setPadding(dp(16), 0, dp(8), 0);
            row.addView(name, new LinearLayout.LayoutParams(0, -2, 1));
            TextView ram = text(16, light, 0xffa3a3a3, a.ramMb + " MB");
            ram.setGravity(Gravity.RIGHT);
            row.addView(ram, new LinearLayout.LayoutParams(dp(72), -2));
            // A round ✕ (48 dp, a finger's width) instead of the long "Cerrar" button.
            LinearLayout close = horizontal();
            close.setGravity(Gravity.CENTER);
            close.setBackgroundDrawable(background(0xff2a1414, 0xff4a2222, 0xff4a2222, 24));
            close.addView(new IconView(this, IconView.CLOSE, 0xfffca5a5, 2.4f), new LinearLayout.LayoutParams(dp(20), dp(20)));
            close.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    close(Collections.singletonList(a.pkg));
                }
            });
            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(dp(48), dp(48));
            clp.leftMargin = dp(16);
            row.addView(close, clp);
            LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(-1, dp(62));
            if (openList.getChildCount() > 0) rp.topMargin = dp(10);
            openList.addView(row, rp);
        }
        closeAll.setVisibility(View.VISIBLE);
    }

    private void close(final List<String> pkgs) {
        if (pkgs.isEmpty()) return;
        openList.removeAllViews();
        openList.addView(emptyText("Cerrando…"));
        closeAll.setVisibility(View.GONE);
        final Context app = getApplicationContext();
        new Thread(new Runnable() {
            @Override
            public void run() {
                for (String pkg : pkgs) RunningApps.close(app, pkg);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (open.getVisibility() == View.VISIBLE) loadOpen("Actualizando…");
                    }
                });
            }
        }, "close-apps").start();
    }

    private TextView emptyText(String value) {
        TextView t = text(20, light, 0xffa3a3a3, value);
        t.setGravity(Gravity.CENTER);
        t.setPadding(0, dp(80), 0, 0);
        t.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));
        return t;
    }

    private IconView dimIcon;

    /**
     * Only Home is darkened (a translucent black layer), never the backlight: the head unit's own
     * day/night brightness keeps control of the screen, and apps and Settings stay as they are.
     */
    private void setDimmed(boolean on) {
        ui.edit().putBoolean("dim", on).apply();
        root.setForeground(on ? new android.graphics.drawable.ColorDrawable(0x80000000) : null);
        dimIcon.setColor(on ? 0xfffde68a : 0xffa3a3a3);
    }

    private LinearLayout startChip;
    private TextView startText;

    private void refreshStartSource() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                final String source = StartSource.read();
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        startChip.setVisibility(source == null ? View.GONE : View.VISIBLE);
                        wifiText.setVisibility(source == null ? View.VISIBLE : View.GONE); // room for the chip
                        if (source != null) startText.setText(StartSource.label(source)); // red radio icon: "starts in"; the toast says the rest
                    }
                });
            }
        }, "start-source").start();
    }

    private void clearStartSource() {
        final Context app = getApplicationContext();
        new Thread(new Runnable() {
            @Override
            public void run() {
                final boolean ok = StartSource.clear();
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (ok) {
                            startChip.setVisibility(View.GONE);
                            wifiText.setVisibility(View.VISIBLE);
                        }
                        Toast.makeText(app, ok ? "Al encender: Bluetooth (sin ruido)" : "No se pudo cambiar",
                                Toast.LENGTH_SHORT).show();
                    }
                });
            }
        }, "start-source").start();
    }

    private void refreshOpenCount() {
        final Context app = getApplicationContext();
        new Thread(new Runnable() {
            @Override
            public void run() {
                final int n = RunningApps.count(app);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        openCount.setText(String.valueOf(n));
                    }
                });
            }
        }, "open-count").start();
    }

    // ---- Switching screens ----

    private void show(View screen) {
        home.setVisibility(screen == home ? View.VISIBLE : View.GONE);
        apps.setVisibility(screen == apps ? View.VISIBLE : View.GONE);
        open.setVisibility(screen == open ? View.VISIBLE : View.GONE);
        style.setVisibility(screen == style ? View.VISIBLE : View.GONE);
    }

    private void showHome() {
        if (home.getVisibility() == View.VISIBLE) return;
        show(home);
        // Drop the icons: the lists are rebuilt the next time they open, so they never sit in RAM behind Home.
        grid.setAdapter(null);
        openList.removeAllViews();
        pickSlot = -1;
        refreshOpenCount();
    }

    // ---- Icon and colour of a chosen app's big tile ----

    private int styleSlot = -1, styleIcon, styleColor;
    private ComponentName styleApp;
    private String styleName;
    private TextView styleCaption;
    private LinearLayout stylePreview;
    private final View[] styleIconCells = new View[STYLE_ICONS.length];
    private final View[] styleColorCells = new View[PALETTE.length];

    private void resetBigTile(int slot) {
        tiles.edit().remove("big" + slot).remove("icon" + slot).remove("color" + slot).apply();
    }

    /** After picking an app for big tile {@code slot}: choose its icon (its own, or a drawn one) and colour. */
    private void showStyle(int slot, ComponentName app, String name) {
        styleSlot = slot;
        styleApp = app;
        styleName = name;
        boolean same = app.flattenToString().equals(tiles.getString("big" + slot, null));
        styleIcon = same ? tiles.getInt("icon" + slot, 0) : 0;
        styleColor = same ? tiles.getInt("color" + slot, -1) : -1;
        if (styleColor < 0) styleColor = defaultColor(slot);
        styleCaption.setText(("Botón " + (slot + 1) + " · app: " + name).toUpperCase(ES));
        refreshStyle();
        show(style);
    }

    /** The palette entry of the tile's stock colour (the first six are the Home's own), else gray. */
    private int defaultColor(int slot) {
        for (int i = 0; i < PALETTE.length; i++) {
            if (PALETTE[i][1] == (Integer) BIG[slot][4]) return i;
        }
        return PALETTE.length - 1;
    }

    private View buildStyle() {
        LinearLayout col = vertical();
        col.setPadding(dp(24), dp(20), dp(24), dp(22));

        FrameLayout header = new FrameLayout(this);
        LinearLayout back = pill(IconView.BACK);
        ((TextView) back.getChildAt(1)).setText("Atrás");
        back.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                show(apps); // back to the app list, same tile
            }
        });
        header.addView(back, new FrameLayout.LayoutParams(-2, dp(52), Gravity.LEFT | Gravity.CENTER_VERTICAL));
        LinearLayout titles = vertical();
        titles.setGravity(Gravity.CENTER_HORIZONTAL);
        styleCaption = text(12, medium, 0xff4ade80);
        titles.addView(styleCaption, wrap());
        titles.addView(text(26, light, 0xfff2f2f2, "Ícono y color"), wrap());
        header.addView(titles, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));
        TextView done = text(18, medium, 0xff04140a, "Listo");
        done.setGravity(Gravity.CENTER);
        done.setPadding(dp(24), 0, dp(24), 0);
        done.setBackgroundDrawable(background(0xff4ade80, 0xff22c55e, 0, 26));
        done.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                tiles.edit().putString("big" + styleSlot, styleApp.flattenToString())
                        .putInt("icon" + styleSlot, styleIcon).putInt("color" + styleSlot, styleColor).apply();
                replaceBigTile(styleSlot);
                showHome();
            }
        });
        header.addView(done, new FrameLayout.LayoutParams(-2, dp(52), Gravity.RIGHT | Gravity.CENTER_VERTICAL));
        col.addView(header, new LinearLayout.LayoutParams(-1, dp(56)));

        LinearLayout body = horizontal();
        LinearLayout left = vertical();
        left.addView(text(12, medium, 0xffa3a3a3, "ASÍ QUEDA"), wrap());
        stylePreview = vertical();
        stylePreview.setPadding(dp(18), dp(18), dp(18), dp(18));
        LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(dp(176), dp(196));
        pp.topMargin = dp(8);
        left.addView(stylePreview, pp);
        body.addView(left, new LinearLayout.LayoutParams(dp(176), -1));

        LinearLayout right = vertical();
        right.addView(text(12, medium, 0xffa3a3a3, "ÍCONO"), wrap());
        for (int row = 0; row < 2; row++) {
            LinearLayout line = horizontal();
            for (int k = 0; k < 4; k++) {
                final int i = row * 4 + k;
                LinearLayout cell = vertical();
                cell.setGravity(Gravity.CENTER);
                if (STYLE_ICONS[i] < 0) {
                    ImageView own = new ImageView(this);
                    own.setTag("own");
                    cell.addView(own, new LinearLayout.LayoutParams(dp(34), dp(34)));
                } else {
                    IconView iv = new IconView(this, STYLE_ICONS[i], 0xffd4d4d4, 2f);
                    iv.setTag("icon");
                    cell.addView(iv, new LinearLayout.LayoutParams(dp(34), dp(34)));
                }
                TextView label = text(13, light, 0xffe6e6e6, STYLE_LABELS[i]);
                label.setPadding(0, dp(6), 0, 0);
                cell.addView(label, wrap());
                cell.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        styleIcon = i;
                        refreshStyle();
                    }
                });
                styleIconCells[i] = cell;
                LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(0, dp(84), 1);
                if (k > 0) cp.leftMargin = dp(10);
                line.addView(cell, cp);
            }
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.topMargin = dp(row == 0 ? 10 : 10);
            right.addView(line, lp);
        }
        TextView colorTitle = text(12, medium, 0xffa3a3a3, "COLOR");
        colorTitle.setPadding(0, dp(14), 0, 0);
        right.addView(colorTitle, wrap());
        LinearLayout swatches = horizontal();
        for (int i = 0; i < PALETTE.length; i++) {
            final int c = i;
            LinearLayout sw = horizontal();
            sw.setGravity(Gravity.CENTER);
            sw.setContentDescription(PALETTE_NAMES[i]);
            View dot = new View(this);
            dot.setBackgroundDrawable(shape(PALETTE[i][0], 0, 9));
            sw.addView(dot, new LinearLayout.LayoutParams(dp(18), dp(18)));
            sw.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    styleColor = c;
                    refreshStyle();
                }
            });
            styleColorCells[i] = sw;
            LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(0, dp(52), 1);
            if (i > 0) sp.leftMargin = dp(8);
            swatches.addView(sw, sp);
        }
        LinearLayout.LayoutParams swp = new LinearLayout.LayoutParams(-1, -2);
        swp.topMargin = dp(10);
        right.addView(swatches, swp);
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(0, -1, 1);
        rp.leftMargin = dp(20);
        body.addView(right, rp);

        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(-1, 0, 1);
        bp.topMargin = dp(16);
        col.addView(body, bp);
        return col;
    }

    /** A selected cell: a thicker border than {@link #shape}'s hairline, so it reads at a glance. */
    private GradientDrawable marked(int fill, int stroke, int strokeDp) {
        GradientDrawable g = shape(fill, 0, 14);
        g.setStroke(dp(strokeDp), stroke);
        return g;
    }

    /** Marks the chosen icon and colour and redraws the preview tile. */
    private void refreshStyle() {
        int[] pal = PALETTE[styleColor];
        Drawable appIcon = null;
        try {
            appIcon = getPackageManager().getActivityIcon(styleApp);
        } catch (PackageManager.NameNotFoundException ignored) {
        }
        for (int i = 0; i < styleIconCells.length; i++) {
            boolean on = i == styleIcon;
            View cell = styleIconCells[i];
            cell.setBackgroundDrawable(on ? marked(pal[1], pal[0], 2) : shape(0xff111111, 0xff262626, 14));
            View own = cell.findViewWithTag("own");
            if (own != null) ((ImageView) own).setImageDrawable(appIcon);
            View iv = cell.findViewWithTag("icon");
            if (iv != null) ((IconView) iv).setColor(on ? pal[0] : 0xffd4d4d4);
        }
        for (int i = 0; i < styleColorCells.length; i++) {
            styleColorCells[i].setBackgroundDrawable(i == styleColor ? marked(PALETTE[i][1], Color.WHITE, 3)
                    : shape(PALETTE[i][1], 0xff2a2a2a, 14));
        }
        stylePreview.removeAllViews();
        stylePreview.setBackgroundDrawable(shape(pal[1], 0, 22));
        if (styleIcon > 0) {
            stylePreview.addView(new IconView(this, STYLE_ICONS[styleIcon], pal[0], 2f), new LinearLayout.LayoutParams(dp(52), dp(52)));
        } else {
            ImageView icon = new ImageView(this);
            icon.setImageDrawable(appIcon);
            stylePreview.addView(icon, new LinearLayout.LayoutParams(dp(52), dp(52)));
        }
        stylePreview.addView(new View(this), new LinearLayout.LayoutParams(1, 0, 1));
        TextView title = text(26, medium, 0xfff2f2f2, styleName);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        stylePreview.addView(title, wrap());
        stylePreview.addView(text(13, light, 0xffb8b8b8, styleIcon > 0 ? STYLE_LABELS[styleIcon] : " "), wrap());
    }

    // ---- Status ----

    private void updateClock() {
        Date now = new Date();
        String pattern = DateFormat.is24HourFormat(this) ? "H:mm" : "h:mm";
        clock.setText(new SimpleDateFormat(pattern, ES).format(now));
        String day = new SimpleDateFormat("EEEE d 'de' MMMM", ES).format(now);
        date.setText(day.substring(0, 1).toUpperCase(ES) + day.substring(1));
    }

    private void countSatellites() {
        int used = 0;
        try {
            GpsStatus status = locationManager.getGpsStatus(null);
            for (GpsSatellite sat : status.getSatellites()) {
                if (sat.usedInFix()) used++;
            }
        } catch (RuntimeException ignored) {
            return;
        }
        satellitesUsed = used;
    }

    /** "62 km/h · 7" while there is a fresh fix with speed, else "GPS" and the satellites in use. */
    private void updateGps() {
        boolean fresh = speedKmh >= 0 && SystemClock.elapsedRealtime() - speedAt < SPEED_MAX_AGE_MS;
        if (fresh) {
            // Below walking pace the GPS speed is noise from a parked car.
            gpsValue.setText(String.valueOf(speedKmh < 3 ? 0 : speedKmh));
        } else {
            gpsValue.setText("–"); // no fix: a dash, so the big number never shows a stale speed
        }
        gpsUnit.setText(satellitesUsed > 0 ? String.valueOf(satellitesUsed) : "sin GPS");
    }

    private void updateWifi() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        NetworkInfo wifi = cm.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
        if (wifi != null && wifi.isConnected()) {
            String ssid = ((WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE)).getConnectionInfo().getSSID();
            if (ssid != null && ssid.length() > 1 && ssid.startsWith("\"") && ssid.endsWith("\"")) {
                ssid = ssid.substring(1, ssid.length() - 1);
            }
            wifiText.setText(TextUtils.isEmpty(ssid) || ssid.startsWith("<") ? "Wi‑Fi" : ssid);
            wifiText.setTextColor(0xffd4d4d4);
            wifiIcon.setColor(0xff4ade80);
        } else {
            wifiText.setText("Sin red");
            wifiText.setTextColor(0xffa3a3a3);
            wifiIcon.setColor(0xff737373);
        }
    }

    // ---- Launching ----

    private View.OnClickListener launcher(final String pkg, final String cls) {
        return new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                start(cls == null ? null : new ComponentName(pkg, cls), pkg);
            }
        };
    }

    private void start(ComponentName component, String pkg) {
        Intent intent = null;
        if (component != null) {
            intent = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setComponent(component);
        }
        if (intent == null || getPackageManager().resolveActivity(intent, 0) == null) {
            intent = getPackageManager().getLaunchIntentForPackage(pkg);
        }
        if (intent == null) {
            Toast.makeText(this, "No está instalada", Toast.LENGTH_SHORT).show();
            return;
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException | SecurityException e) {
            Toast.makeText(this, "No se pudo abrir", Toast.LENGTH_SHORT).show();
        }
    }

    private void startSettings(String action) {
        try {
            startActivity(new Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "No se pudo abrir", Toast.LENGTH_SHORT).show();
        }
    }

    // ---- Small view helpers ----

    private int dp(float v) {
        return Math.round(v * density);
    }

    private LinearLayout vertical() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    private LinearLayout horizontal() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.HORIZONTAL);
        return l;
    }

    /** A 52dp rounded header button with an icon (tagged "icon") and a label. */
    private LinearLayout pill(int icon) {
        LinearLayout pill = horizontal();
        pill.setGravity(Gravity.CENTER_VERTICAL);
        pill.setPadding(dp(14), 0, dp(20), 0);
        pill.setBackgroundDrawable(background(0xff1a1a1a, 0xff2a2a2a, 0xff333333, 26));
        IconView iv = new IconView(this, icon, Color.WHITE, 2f);
        iv.setTag("icon");
        pill.addView(iv, new LinearLayout.LayoutParams(dp(24), dp(24)));
        TextView label = text(18, light, Color.WHITE);
        label.setPadding(dp(10), 0, 0, 0);
        pill.addView(label, wrap());
        return pill;
    }

    private TextView text(float size, Typeface face, int color) {
        return text(size, face, color, null);
    }

    private TextView text(float size, Typeface face, int color, String value) {
        TextView t = new TextView(this);
        // Pixel size from dp, so the system font scale cannot push the layout off an 800x480 screen.
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX, dp(size));
        t.setTypeface(face);
        t.setTextColor(color);
        t.setIncludeFontPadding(false);
        if (value != null) t.setText(value);
        return t;
    }

    private Drawable background(int fill, int pressed, int stroke, int radius) {
        StateListDrawable states = new StateListDrawable();
        states.addState(new int[]{android.R.attr.state_pressed}, shape(pressed, stroke, radius));
        states.addState(new int[]{}, shape(fill, stroke, radius));
        return states;
    }

    private GradientDrawable shape(int fill, int stroke, int radius) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(dp(radius));
        if (stroke != 0) g.setStroke(dp(1), stroke);
        return g;
    }

    private LinearLayout.LayoutParams wrap() {
        return new LinearLayout.LayoutParams(-2, -2);
    }

    private LinearLayout.LayoutParams chipParams(int leftMargin) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-2, dp(52));
        p.leftMargin = dp(leftMargin);
        return p;
    }

    private LinearLayout.LayoutParams iconChipParams() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(dp(52), dp(52));
        p.leftMargin = dp(10);
        return p;
    }

    /** One of four equal columns with a 14dp gap. */
    private LinearLayout.LayoutParams cell(int index) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, -1, 1);
        p.leftMargin = index == 0 ? 0 : dp(7);
        p.rightMargin = index == 3 ? 0 : dp(7);
        return p;
    }
}
