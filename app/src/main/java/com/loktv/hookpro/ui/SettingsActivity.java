package com.loktv.hookpro.ui;

import android.app.Activity;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.loktv.hookpro.core.Config;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;

/** شاشة تشخيص: الاسم/الإصدار/النطاق + عرض اللوج. */
public final class SettingsActivity extends Activity {

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        root.setPadding(pad, pad, pad, pad);

        TextView title = new TextView(this);
        title.setText("LokTvHookPro v" + Config.VERSION_NAME);
        title.setTextSize(20);
        root.addView(title);

        TextView info = new TextView(this);
        info.setText(
                "API " + Config.MIN_SDK + " → " + Config.MAX_SDK + "\n"
                + "Package: " + getPackageName() + "\n"
                + "Log: <cacheDir>/" + Config.LOG_FILE + "\n"
                + "Kill-switch: create <cacheDir>/" + Config.KILLSWITCH_FILE);
        info.setPadding(0, pad / 2, 0, pad);
        root.addView(info);

        TextView log = new TextView(this);
        log.setTextSize(10);
        log.setText(readLog());
        ScrollView sv = new ScrollView(this);
        sv.addView(log, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        root.addView(sv);

        setContentView(root);
    }

    private String readLog() {
        try {
            File f = new File(getCacheDir(), Config.LOG_FILE);
            if (!f.exists()) return "(no log yet)";
            StringBuilder sb = new StringBuilder();
            BufferedReader r = null;
            try {
                r = new BufferedReader(new FileReader(f));
                String line;
                int n = 0;
                while ((line = r.readLine()) != null && n++ < 400) {
                    sb.append(line).append('\n');
                }
            } finally {
                if (r != null) {
                    try { r.close(); } catch (Throwable ignored) {}
                }
            }
            return sb.toString();
        } catch (Throwable t) {
            return "(error: " + t + ")";
        }
    }
}
