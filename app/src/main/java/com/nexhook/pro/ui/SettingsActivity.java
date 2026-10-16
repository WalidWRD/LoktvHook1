package com.nexhook.pro.ui;

import android.app.Activity;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.nexhook.pro.core.Config;

import java.io.File;
import java.io.FileWriter;

public class SettingsActivity extends Activity {

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(48, 48, 48, 48);

        TextView title = new TextView(this);
        title.setText("NexHookPro v" + Config.VERSION);
        title.setTextSize(22f);
        root.addView(title);

        TextView info = new TextView(this);
        info.setText(
            "Universal VIP unlock — any app, any version.\n\n" +
            "Features:\n" +
            "• Reflection + fingerprint discovery\n" +
            "• Multi-source inventory (splits included)\n" +
            "• Word-aware forbidden filter\n" +
            "• VIP gate (tier A/B)\n" +
            "• SharedPreferences + JSON + BaseBundle\n" +
            "• TextView replacement\n" +
            "• Infrastructure-safe: no fancybypass/pairip\n" +
            "• No ClassLoader hooks\n" +
            "• NO System.loadLibrary hooks\n" +
            "• Compatible: LSPosed / EdXposed / NPatch / LSPatch / HKP\n\n" +
            "Limits:\n" +
            "MAX_HOOKS=" + Config.MAX_HOOKS + "\n" +
            "PER_CLASS=" + Config.MAX_HOOKS_PER_CLASS + "\n" +
            "TIMEOUT_MS=" + Config.ACTIVATION_TIMEOUT_MS + "\n\n" +
            "Log: <cacheDir>/" + Config.LOG_FILE + "\n" +
            "Emergency: <cacheDir>/" + Config.EMERGENCY_FLAG
        );
        info.setTextSize(14f);
        info.setPadding(0, 32, 0, 32);
        root.addView(info);

        final TextView btn = new TextView(this);
        btn.setText("[ Create emergency flag ]");
        btn.setGravity(Gravity.CENTER);
        btn.setPadding(24, 24, 24, 24);
        btn.setOnClickListener(new android.view.View.OnClickListener() {
            @Override public void onClick(android.view.View v) {
                FileWriter fw = null;
                try {
                    File f = new File(getCacheDir(), Config.EMERGENCY_FLAG);
                    fw = new FileWriter(f, false);
                    fw.write("safe\n");
                    btn.setText("[ Flag created ]");
                } catch (Throwable t) {
                    String m = t.getMessage();
                    btn.setText("[ Failed: " + (m == null ? t.toString() : m) + " ]");
                } finally {
                    if (fw != null) {
                        try { fw.close(); } catch (Throwable ignored) {}
                    }
                }
            }
        });
        root.addView(btn);

        ScrollView sv = new ScrollView(this);
        sv.addView(root);
        setContentView(sv);
    }
}
