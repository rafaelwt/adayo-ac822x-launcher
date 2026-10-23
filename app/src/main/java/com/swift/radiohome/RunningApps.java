package com.swift.radiohome;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.os.Debug;
import android.util.Log;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The apps the driver opened and that still hold a process, and closing them, like the recents screen of a phone.
 * Call from a worker thread: reading process memory is slow on this CPU.
 */
final class RunningApps {
    private static final String TAG = "RadioHome";

    static final class App {
        final String pkg;
        final CharSequence label;
        final Drawable icon;
        final int ramMb;

        App(String pkg, CharSequence label, Drawable icon, int ramMb) {
            this.pkg = pkg;
            this.label = label;
            this.icon = icon;
            this.ramMb = ramMb;
        }
    }

    private RunningApps() {
    }

    /** Apps with a launcher icon and a live process, biggest first; never ADAYO's own, Home or system core. */
    static List<App> list(Context context) {
        ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        PackageManager pm = context.getPackageManager();
        Set<String> homes = homePackages(pm);
        Map<String, List<Integer>> pids = new LinkedHashMap<String, List<Integer>>();
        List<ActivityManager.RunningAppProcessInfo> procs = am.getRunningAppProcesses();
        if (procs == null) return new ArrayList<App>();
        for (ActivityManager.RunningAppProcessInfo p : procs) {
            if (p.pkgList == null) continue;
            for (String pkg : p.pkgList) {
                if (!pids.containsKey(pkg) && !closable(context, pm, homes, pkg)) continue;
                List<Integer> list = pids.get(pkg);
                if (list == null) pids.put(pkg, list = new ArrayList<Integer>());
                list.add(p.pid);
            }
        }
        List<App> apps = new ArrayList<App>();
        for (Map.Entry<String, List<Integer>> e : pids.entrySet()) {
            int[] ids = new int[e.getValue().size()];
            for (int i = 0; i < ids.length; i++) ids[i] = e.getValue().get(i);
            int pssKb = 0;
            for (Debug.MemoryInfo m : am.getProcessMemoryInfo(ids)) pssKb += m.getTotalPss();
            try {
                ApplicationInfo info = pm.getApplicationInfo(e.getKey(), 0);
                apps.add(new App(e.getKey(), info.loadLabel(pm), info.loadIcon(pm), Math.round(pssKb / 1024f)));
            } catch (PackageManager.NameNotFoundException ignored) {
                // Uninstalled while we looked.
            }
        }
        Collections.sort(apps, new Comparator<App>() {
            @Override
            public int compare(App a, App b) {
                return b.ramMb - a.ramMb;
            }
        });
        return apps;
    }

    /** Number of apps {@link #list} would show, without reading their memory. */
    static int count(Context context) {
        ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        PackageManager pm = context.getPackageManager();
        Set<String> homes = homePackages(pm);
        Set<String> seen = new HashSet<String>();
        List<ActivityManager.RunningAppProcessInfo> procs = am.getRunningAppProcesses();
        if (procs == null) return 0;
        for (ActivityManager.RunningAppProcessInfo p : procs) {
            if (p.pkgList == null) continue;
            for (String pkg : p.pkgList) {
                if (!seen.contains(pkg) && closable(context, pm, homes, pkg)) seen.add(pkg);
            }
        }
        return seen.size();
    }

    static int freeRamMb(Context context) {
        ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
        ((ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE)).getMemoryInfo(mi);
        return (int) (mi.availMem / (1024 * 1024));
    }

    /**
     * Kills the app's processes. killBackgroundProcesses needs no root but spares an app that runs a foreground
     * service (Waze while navigating), so for those the head unit's adbd force-stops it, as Settings would.
     */
    static void close(Context context, String pkg) {
        ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        am.killBackgroundProcesses(pkg);
        if (!isRunning(am, pkg)) return;
        // The package name goes into a shell command: accept only what a package name can contain.
        if (!pkg.matches("[A-Za-z0-9_.]+")) return;
        try {
            LocalAdb.shell("am force-stop " + pkg);
        } catch (IOException e) {
            Log.w(TAG, "close " + pkg + ": adb unavailable (" + e.getMessage() + ")");
        }
    }

    private static boolean isRunning(ActivityManager am, String pkg) {
        List<ActivityManager.RunningAppProcessInfo> procs = am.getRunningAppProcesses();
        if (procs == null) return false;
        for (ActivityManager.RunningAppProcessInfo p : procs) {
            if (p.pkgList == null) continue;
            for (String name : p.pkgList) if (pkg.equals(name)) return true;
        }
        return false;
    }

    private static boolean closable(Context context, PackageManager pm, Set<String> homes, String pkg) {
        // ADAYO's packages run the radio, Bluetooth calls, steering wheel keys and the MCU link: never touch them.
        if (pkg.startsWith("com.adayo.") || homes.contains(pkg) || pkg.equals(context.getPackageName())) return false;
        if (pkg.equals("android") || pkg.equals("com.android.systemui") || pkg.equals("com.android.phone")) return false;
        try {
            if ((pm.getApplicationInfo(pkg, 0).flags & ApplicationInfo.FLAG_PERSISTENT) != 0) return false;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
        return pm.getLaunchIntentForPackage(pkg) != null;
    }

    private static Set<String> homePackages(PackageManager pm) {
        Set<String> homes = new HashSet<String>();
        Intent home = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
        for (ResolveInfo ri : pm.queryIntentActivities(home, 0)) homes.add(ri.activityInfo.packageName);
        return homes;
    }
}
