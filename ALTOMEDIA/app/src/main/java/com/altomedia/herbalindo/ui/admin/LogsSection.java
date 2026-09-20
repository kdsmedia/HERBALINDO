package com.altomedia.herbalindo.ui.admin;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.altomedia.herbalindo.R;
import com.altomedia.herbalindo.core.Ui;
import com.altomedia.herbalindo.core.Util;

import org.json.JSONObject;

import java.util.List;

/** Audit log: rekam jejak seluruh aksi penting admin dan sistem. */
class LogsSection {

    private final AdminActivity a;
    private View root;

    LogsSection(AdminActivity a) { this.a = a; }

    View view() {
        if (root == null) {
            root = LayoutInflater.from(a).inflate(R.layout.section_list, null, false);
            ((TextView) root.findViewById(R.id.sec_title)).setText("Audit Log");
            android.widget.Button add = root.findViewById(R.id.sec_add);
            add.setVisibility(View.VISIBLE);
            add.setText("Ekspor");
            add.setOnClickListener(v -> export());
        }
        return root;
    }

    void refresh() { if (root != null) render(); }

    private void render() {
        LinearLayout list = root.findViewById(R.id.sec_list);
        list.removeAllViews();
        List<JSONObject> logs = a.repo().adminLogs(200);
        if (logs.isEmpty()) {
            list.addView(AdminActivity.row(a, "Belum ada catatan audit", "-", R.color.text_secondary));
            return;
        }
        for (int i = logs.size() - 1; i >= 0; i--) {
            JSONObject l = logs.get(i);
            View card = LayoutInflater.from(a).inflate(R.layout.item_admin_card, null, false);
            ((TextView) card.findViewById(R.id.ac_title)).setText(l.optString("action"));
            String actor = l.optString("adminId");
            String actorName = "USR-ADMIN".equals(actor) || "ADMIN".equals(actor) ? "Administrator"
                    : (a.repo().user(actor) == null ? actor : a.repo().user(actor).name);
            ((TextView) card.findViewById(R.id.ac_sub)).setText(
                    Util.dateTime(l.optString("createdAt")) + " · oleh " + actorName);
            ((TextView) card.findViewById(R.id.ac_badge)).setText("");
            ((TextView) card.findViewById(R.id.ac_body)).setText(
                    "Target: " + l.optString("target") + "\n" + l.optString("data"));
            card.findViewById(R.id.ac_actions).setVisibility(View.GONE);
            list.addView(card);
        }
    }

    private void export() {
        StringBuilder sb = new StringBuilder("HERBALINDO Audit Log\nDibuat: " + Util.nowIso() + "\n\n");
        List<JSONObject> logs = a.repo().adminLogs(0);
        for (int i = logs.size() - 1; i >= 0; i--) {
            JSONObject l = logs.get(i);
            sb.append(Util.dateTime(l.optString("createdAt"))).append(" | ")
                    .append(l.optString("action")).append(" | target=")
                    .append(l.optString("target")).append(" | actor=")
                    .append(l.optString("adminId")).append(" | ")
                    .append(l.optString("data")).append("\n");
        }
        String content = sb.toString();
        ClipboardManager cm = (ClipboardManager) a.getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("Audit Log", content));

        Intent i = new Intent(Intent.ACTION_SEND);
        i.setType("text/plain");
        i.putExtra(Intent.EXTRA_SUBJECT, "Audit Log HERBALINDO");
        i.putExtra(Intent.EXTRA_TEXT, content.length() > 20000 ? content.substring(0, 20000) : content);
        a.startActivity(Intent.createChooser(i, "Bagikan audit log"));
        Ui.ok(a, logs.size() + " baris log disalin ke clipboard");
    }
}