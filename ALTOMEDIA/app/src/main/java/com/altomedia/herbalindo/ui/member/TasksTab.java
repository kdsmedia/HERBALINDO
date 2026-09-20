package com.altomedia.herbalindo.ui.member;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.altomedia.herbalindo.R;
import com.altomedia.herbalindo.ads.AdsManager;
import com.altomedia.herbalindo.core.Config;
import com.altomedia.herbalindo.core.Ui;
import com.altomedia.herbalindo.core.Util;
import com.altomedia.herbalindo.data.Models;
import com.altomedia.herbalindo.data.Repository;

/** Tab Tugas: check-in harian, rewarded ads, dan undangan referral. */
class TasksTab {

    private final MemberActivity a;
    private View root;

    TasksTab(MemberActivity a) { this.a = a; }

    View view() {
        if (root == null) {
            root = LayoutInflater.from(a).inflate(R.layout.tab_tasks, null, false);
            root.findViewById(R.id.task_checkin_btn).setOnClickListener(v -> checkin());
            root.findViewById(R.id.task_ads_btn).setOnClickListener(v -> watchAd());
            root.findViewById(R.id.task_ref_copy).setOnClickListener(v -> copyRef());
            root.findViewById(R.id.task_ref_share).setOnClickListener(v -> shareRef());
        }
        return root;
    }

    void refresh() {
        if (root == null) return;
        Models.User u = a.repo().user(a.user.userId);
        a.user = u;
        Models.Settings s = a.repo().settings();
        Models.DailyTask t = a.repo().todayTask(u.userId);

        ((TextView) root.findViewById(R.id.task_date)).setText("Tanggal " + t.date + " · hanya berlaku hari ini");

        ((TextView) root.findViewById(R.id.task_checkin_sub)).setText(
                t.checkin ? "Sudah diambil hari ini" : "Bonus +" + s.checkinPoints + " poin");
        Button ci = root.findViewById(R.id.task_checkin_btn);
        ci.setEnabled(!t.checkin);
        ci.setText(t.checkin ? "Selesai" : "Ambil");

        ((TextView) root.findViewById(R.id.task_ads_sub)).setText(
                "Rewarded Ads +" + s.adPoints + " poin · maksimal " + s.adMaxPerDay + " per hari");
        ((TextView) root.findViewById(R.id.task_ads_note)).setText(
                "Poin diberikan hanya setelah iklan ditonton sampai selesai. "
                        + "Maksimal 20 (dua puluh) iklan berhadiah per hari.");
        ProgressBar pb = root.findViewById(R.id.task_ads_progress);
        pb.setMax((int) s.adMaxPerDay);
        pb.setProgress(t.adsWatched);
        Button ad = root.findViewById(R.id.task_ads_btn);
        boolean ready = t.adsWatched < s.adMaxPerDay;
        ad.setEnabled(ready);
        ad.setText(ready ? "Tonton" : "Limit");

        ((TextView) root.findViewById(R.id.task_ref_sub)).setText(
                "Referral ID " + u.referralId + " · bonus " + Util.num(s.referralBonus)
                        + " poin untuk pesanan pertama teman (minimal " + Util.rupiah(s.referralMinOrder) + ")");

        ((TextView) root.findViewById(R.id.task_rules)).setText(
                "1. Referral hanya 1 tingkat: bonus dibayarkan kepada pengundang langsung saat pesanan pertama "
                        + "teman terverifikasi. Tidak ada bonus berantai.\n"
                        + "2. Withdrawal membutuhkan saldo minimal " + Util.rupiah(s.minWithdrawRupiah)
                        + ", menonton " + s.adMaxPerDay + " iklan berhadiah pada hari yang sama, akun aktif, "
                        + "dan maksimal " + s.maxWithdrawPerDay + " pengajuan per hari.\n"
                        + "3. Konversi poin: " + Util.num(s.pointsPerUnit) + " poin = " + Util.rupiah(s.rupiahPerUnit) + ".\n"
                        + "4. Aktivitas mencurigakan dapat dikenakan pembatasan dan tercatat pada audit log.");
    }

    private void checkin() {
        try {
            a.repo().checkin(a.user.userId);
            refresh();
            a.onSessionReady(a.repo().user(a.user.userId));
            Ui.ok(a, "Check-in berhasil");
        } catch (Repository.RuleException e) {
            Ui.error(a, e.getMessage());
        }
    }

    private void watchAd() {
        AdsManager.get(a).preloadRewarded();
        AdsManager.get(a).showRewarded(a, new AdsManager.RewardCallback() {
            @Override public void onRewarded() {
                try {
                    long total = a.repo().watchAd(a.user.userId);
                    refresh();
                    a.onSessionReady(a.repo().user(a.user.userId));
                    Ui.ok(a, "Poin diberikan. Total " + Util.num(total) + " poin");
                } catch (Repository.RuleException e) {
                    Ui.error(a, e.getMessage());
                }
            }
            @Override public void onFailed(String reason) {
                Ui.error(a, reason);
            }
        });
    }

    private void copyRef() {
        ClipboardManager cm = (ClipboardManager) a.getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) {
            cm.setPrimaryClip(ClipData.newPlainText("Referral ID", a.user.referralId));
            Ui.ok(a, "Referral ID " + a.user.referralId + " disalin");
        }
    }

    private void shareRef() {
        Models.Settings s = a.repo().settings();
        String text = "Ayo bergabung di " + Config.APP_NAME + " (" + a.user.name + ").\n\n"
                + "Gunakan Referral ID: " + a.user.referralId + "\n"
                + "Bonus " + Util.num(s.referralBonus) + " poin untuk pesanan pertama (minimal "
                + Util.rupiah(s.referralMinOrder) + ").\n"
                + "Komisi dan saldo dapat ditarik sesuai syarat aplikasi.\n\n"
                + "Herbal diet alami, poin harian, dan saldo rupiah.";
        Intent i = new Intent(Intent.ACTION_SEND);
        i.setType("text/plain");
        i.putExtra(Intent.EXTRA_SUBJECT, "Undangan " + Config.APP_NAME);
        i.putExtra(Intent.EXTRA_TEXT, text);
        a.startActivity(Intent.createChooser(i, "Bagikan undangan"));
    }
}