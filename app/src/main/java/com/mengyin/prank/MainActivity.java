package com.mengyin.prank;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Typeface;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.InputStream;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {

    private static final int REQ_IMPORT = 1;
    private static final int REQ_REC = 2;
    private static final int REQ_NOTIF = 3;

    /** 首次打开时用文字转语音自动生成的整蛊台词（原创）。 */
    private static final String[] STARTER_LINES = {
            "猜猜我是谁？",
            "喂，你好，你的快递到了，麻烦下楼签收一下",
            "我是你失散多年的亲妹妹",
            "主人，该起床啦，再不起来就要迟到啦",
            "哈哈哈哈哈，你上当啦",
    };

    private ClipStore store;
    private TtsHelper tts;
    private final Handler h = new Handler(Looper.getMainLooper());
    private final Runnable storeListener = this::refreshClips;

    private LinearLayout chipRow;
    private LinearLayout clipList;
    private TextView floatStatus;
    private TextView floatBtn;
    private final LinearLayout[] pages = new LinearLayout[3];
    private final TextView[] tabs = new TextView[3];

    private EditText ttsInput;
    private TextView ttsHint;

    private MediaRecorder recorder;
    private File recFile;
    private long recStart;
    private TextView recBtn, recPlay, recSave, recTime;
    private final Runnable recTick = new Runnable() {
        @Override
        public void run() {
            if (recorder == null) return;
            long s = (System.currentTimeMillis() - recStart) / 1000;
            recTime.setText(String.format(Locale.US, "%02d:%02d", s / 60, s % 60));
            h.postDelayed(this, 500);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        store = ClipStore.get(this);
        getWindow().setStatusBarColor(Ui.BG);
        if (Build.VERSION.SDK_INT >= 23) {
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        }
        setContentView(buildUi());
        store.addListener(storeListener);
        refreshClips();
        selectTab(0);

        tts = new TtsHelper(this, ok -> {
            if (ok) {
                ttsHint.setText("使用手机自带的语音引擎合成，播放时会套用上面选的声音效果。");
                if (!Prefs.seeded(this)) seedStarter(0);
            } else {
                ttsHint.setText("没找到可用的中文语音引擎。请点下面的按钮，在系统设置里安装或选择中文引擎"
                        + "（例如讯飞语记、谷歌文字转语音），然后重新打开 App。");
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshChips();
        h.postDelayed(this::refreshFloatStatus, 300);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (recorder != null) stopRecording();
    }

    @Override
    protected void onDestroy() {
        store.removeListener(storeListener);
        if (tts != null) tts.shutdown();
        if (!FloatingService.running) Player.stop(this);
        super.onDestroy();
    }

    // ---------------- 界面搭建 ----------------

    private int dp(float v) {
        return Ui.dp(this, v);
    }

    private View buildUi() {
        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(Ui.BG);
        sv.setFitsSystemWindows(true);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(18), dp(16), dp(32));
        sv.addView(root);

        root.addView(Ui.text(this, "萌音整蛊", 28, Ui.PINK_DARK, true));
        TextView sub = Ui.text(this, "语音包 · 文字转语音 · 录音变声 · 悬浮窗发语音条", 13, Ui.SUB, false);
        sub.setPadding(0, dp(2), 0, 0);
        root.addView(sub);

        // 悬浮窗卡片
        LinearLayout fc = Ui.card(this);
        fc.addView(Ui.text(this, "悬浮窗发微信语音条", 17, Ui.TEXT, true));
        floatStatus = Ui.text(this, "", 13, Ui.SUB, false);
        floatStatus.setPadding(0, dp(4), 0, dp(10));
        fc.addView(floatStatus);
        floatBtn = Ui.button(this, "开启悬浮窗", true);
        floatBtn.setOnClickListener(v -> toggleFloating());
        fc.addView(floatBtn);
        CheckBox boost = new CheckBox(this);
        boost.setText("播放时自动把媒体音量调到最大（播完恢复）");
        boost.setTextColor(Ui.SUB);
        boost.setTextSize(13);
        boost.setChecked(Prefs.boost(this));
        boost.setOnCheckedChangeListener((b, on) -> Prefs.setBoost(this, on));
        fc.addView(boost, Ui.matchWrap(this, 6));
        root.addView(fc, Ui.matchWrap(this, 16));

        // 变声效果
        TextView pt = Ui.text(this, "选择声音（播放时生效）", 15, Ui.TEXT, true);
        root.addView(pt, Ui.matchWrap(this, 20));
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        chipRow = new LinearLayout(this);
        hs.addView(chipRow);
        root.addView(hs, Ui.matchWrap(this, 8));

        // 标签页
        LinearLayout tabRow = new LinearLayout(this);
        tabRow.setBackground(Ui.round(this, Ui.PINK_LIGHT, 22));
        tabRow.setPadding(dp(4), dp(4), dp(4), dp(4));
        String[] names = {"语音包", "文字转语音", "录音变声"};
        for (int i = 0; i < 3; i++) {
            final int idx = i;
            TextView t = Ui.text(this, names[i], 14, Ui.PINK_DARK, true);
            t.setGravity(Gravity.CENTER);
            t.setPadding(0, dp(9), 0, dp(9));
            t.setOnClickListener(v -> selectTab(idx));
            tabs[i] = t;
            tabRow.addView(t, Ui.weight(this, 0));
        }
        root.addView(tabRow, Ui.matchWrap(this, 20));

        pages[0] = buildPackPage();
        pages[1] = buildTtsPage();
        pages[2] = buildRecordPage();
        for (LinearLayout p : pages) root.addView(p, Ui.matchWrap(this, 12));

        // 使用说明
        LinearLayout help = Ui.card(this);
        help.addView(Ui.text(this, "怎么发成微信语音条", 15, Ui.TEXT, true));
        TextView ht = Ui.text(this,
                "1. 点上面「开启悬浮窗」，允许显示在其他应用上层\n"
                        + "2. 打开微信聊天，切换到「按住说话」\n"
                        + "3. 点悬浮球，选一条语音，悬浮球开始倒计时\n"
                        + "4. 倒计时期间按住微信的说话按钮，手机外放的声音会被录进去\n"
                        + "5. 悬浮球震动并变绿显示「松手」后，松开手指，语音条就发出去了\n\n"
                        + "小贴士：环境安静效果最好；不要戴耳机；倒计时时间可以在悬浮窗里调。"
                        + "再点一下悬浮球可以取消播放。",
                13, Ui.SUB, false);
        ht.setLineSpacing(dp(3), 1f);
        ht.setPadding(0, dp(6), 0, 0);
        help.addView(ht);
        root.addView(help, Ui.matchWrap(this, 20));
        return sv;
    }

    private LinearLayout buildPackPage() {
        LinearLayout p = new LinearLayout(this);
        p.setOrientation(LinearLayout.VERTICAL);
        TextView imp = Ui.button(this, "＋ 导入手机里的音频", false);
        imp.setOnClickListener(v -> {
            Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.setType("audio/*");
            try {
                startActivityForResult(i, REQ_IMPORT);
            } catch (Exception e) {
                toast("打开文件选择失败");
            }
        });
        p.addView(imp);
        TextView tip = Ui.text(this, "点一下试听，长按可以重命名或删除", 12, Ui.SUB, false);
        tip.setPadding(dp(4), dp(10), 0, 0);
        p.addView(tip);
        clipList = new LinearLayout(this);
        clipList.setOrientation(LinearLayout.VERTICAL);
        p.addView(clipList);
        return p;
    }

    private LinearLayout buildTtsPage() {
        LinearLayout p = Ui.card(this);
        ttsInput = new EditText(this);
        ttsInput.setHint("输入想说的话，例如：你的外卖到了，请下楼取一下");
        ttsInput.setMinLines(3);
        ttsInput.setGravity(Gravity.TOP | Gravity.START);
        ttsInput.setTextSize(15);
        ttsInput.setTextColor(Ui.TEXT);
        ttsInput.setPadding(dp(12), dp(10), dp(12), dp(10));
        ttsInput.setBackground(Ui.round(this, 0xFFFFF0F5, 12));
        p.addView(ttsInput);

        LinearLayout row = new LinearLayout(this);
        TextView preview = Ui.button(this, "▶ 试听", false);
        preview.setOnClickListener(v -> ttsPreview());
        TextView save = Ui.button(this, "保存到语音包", true);
        save.setOnClickListener(v -> ttsSave());
        row.addView(preview, Ui.weight(this, 0));
        row.addView(save, Ui.weight(this, 10));
        p.addView(row, Ui.matchWrap(this, 12));

        ttsHint = Ui.text(this, "正在加载语音引擎…", 12, Ui.SUB, false);
        ttsHint.setPadding(0, dp(10), 0, 0);
        p.addView(ttsHint);

        TextView settings = Ui.text(this, "语音引擎设置 ›", 13, Ui.PINK_DARK, true);
        settings.setPadding(0, dp(10), 0, 0);
        settings.setOnClickListener(v -> {
            try {
                startActivity(new Intent("com.android.settings.TTS_SETTINGS"));
            } catch (Exception e) {
                toast("请在系统设置里搜索「文字转语音」");
            }
        });
        p.addView(settings);
        return p;
    }

    private LinearLayout buildRecordPage() {
        LinearLayout p = Ui.card(this);
        p.setGravity(Gravity.CENTER_HORIZONTAL);
        recTime = Ui.text(this, "00:00", 34, Ui.TEXT, true);
        recTime.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        recTime.setGravity(Gravity.CENTER);
        p.addView(recTime);
        TextView tip = Ui.text(this, "录一段自己的声音，再用上面的声音效果试听", 12, Ui.SUB, false);
        tip.setGravity(Gravity.CENTER);
        tip.setPadding(0, dp(4), 0, dp(12));
        p.addView(tip);
        recBtn = Ui.button(this, "● 开始录音", true);
        recBtn.setOnClickListener(v -> {
            if (recorder == null) startRecording();
            else stopRecording();
        });
        p.addView(recBtn, Ui.matchWrap(this, 0));
        LinearLayout row = new LinearLayout(this);
        recPlay = Ui.button(this, "▶ 变声试听", false);
        recPlay.setOnClickListener(v -> {
            if (recFile != null) Player.play(this, recFile, null);
        });
        recSave = Ui.button(this, "保存到语音包", false);
        recSave.setOnClickListener(v -> saveRecording());
        row.addView(recPlay, Ui.weight(this, 0));
        row.addView(recSave, Ui.weight(this, 10));
        p.addView(row, Ui.matchWrap(this, 10));
        Ui.setEnabled(recPlay, false);
        Ui.setEnabled(recSave, false);
        return p;
    }

    private void selectTab(int idx) {
        for (int i = 0; i < 3; i++) {
            boolean on = i == idx;
            pages[i].setVisibility(on ? View.VISIBLE : View.GONE);
            tabs[i].setBackground(on ? Ui.round(this, Ui.CARD, 18) : null);
            tabs[i].setTextColor(on ? Ui.PINK_DARK : Ui.SUB);
        }
    }

    private void refreshChips() {
        chipRow.removeAllViews();
        VoicePreset cur = Prefs.preset(this);
        for (final VoicePreset p : VoicePreset.ALL) {
            boolean on = p == cur;
            TextView c = Ui.text(this, p.name, 14, on ? 0xFFFFFFFF : Ui.PINK_DARK, true);
            c.setPadding(dp(16), dp(8), dp(16), dp(8));
            c.setBackground(on ? Ui.round(this, Ui.PINK, 18) : Ui.roundStroke(this, Ui.CARD, Ui.PINK_LIGHT, 18));
            c.setOnClickListener(v -> {
                Prefs.setPreset(this, p);
                refreshChips();
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.rightMargin = dp(8);
            chipRow.addView(c, lp);
        }
    }

    private void refreshClips() {
        clipList.removeAllViews();
        List<ClipStore.Clip> clips = store.all();
        if (clips.isEmpty()) {
            TextView e = Ui.text(this, "还没有语音。可以导入音频、用文字合成，或者录一段。", 13, Ui.SUB, false);
            e.setPadding(dp(4), dp(16), dp(4), dp(16));
            clipList.addView(e);
            return;
        }
        for (final ClipStore.Clip c : clips) {
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(14), dp(12), dp(12), dp(12));
            row.setBackground(Ui.round(this, Ui.CARD, 14));

            LinearLayout texts = new LinearLayout(this);
            texts.setOrientation(LinearLayout.VERTICAL);
            TextView name = Ui.text(this, c.name, 15, Ui.TEXT, false);
            name.setSingleLine(true);
            name.setEllipsize(TextUtils.TruncateAt.END);
            texts.addView(name);
            if (!TextUtils.isEmpty(c.category)) {
                texts.addView(Ui.text(this, c.category, 11, Ui.SUB, false));
            }
            row.addView(texts, Ui.weight(this, 0));

            TextView play = Ui.text(this, "▶", 16, 0xFFFFFFFF, true);
            play.setGravity(Gravity.CENTER);
            play.setBackground(Ui.round(this, Ui.PINK, 18));
            row.addView(play, new LinearLayout.LayoutParams(dp(36), dp(36)));

            row.setOnClickListener(v -> Player.play(this, store.fileOf(c), null));
            play.setOnClickListener(v -> Player.play(this, store.fileOf(c), null));
            row.setOnLongClickListener(v -> {
                clipMenu(c);
                return true;
            });
            clipList.addView(row, Ui.matchWrap(this, 8));
        }
    }

    private void clipMenu(final ClipStore.Clip c) {
        new AlertDialog.Builder(this)
                .setTitle(c.name)
                .setItems(new String[]{"重命名", "删除"}, (d, which) -> {
                    if (which == 0) renameDialog(c);
                    else store.delete(c);
                })
                .show();
    }

    private void renameDialog(final ClipStore.Clip c) {
        final EditText et = new EditText(this);
        et.setText(c.name);
        et.setSelectAllOnFocus(true);
        LinearLayout wrap = new LinearLayout(this);
        wrap.setPadding(dp(20), dp(8), dp(20), 0);
        wrap.addView(et, Ui.matchWrap(this, 0));
        new AlertDialog.Builder(this)
                .setTitle("重命名")
                .setView(wrap)
                .setPositiveButton("确定", (d, w) -> {
                    String n = et.getText().toString().trim();
                    if (!n.isEmpty()) store.rename(c, n);
                })
                .setNegativeButton("取消", null)
                .show();
    }

    // ---------------- 悬浮窗 ----------------

    private void refreshFloatStatus() {
        boolean on = FloatingService.running;
        floatStatus.setText(on ? "悬浮窗已开启，去微信里试试吧"
                : "开启后会在微信上层显示一个悬浮球，用来播放语音");
        floatBtn.setText(on ? "关闭悬浮窗" : "开启悬浮窗");
    }

    private void toggleFloating() {
        if (FloatingService.running) {
            stopService(new Intent(this, FloatingService.class));
            h.postDelayed(this::refreshFloatStatus, 300);
            return;
        }
        if (Build.VERSION.SDK_INT >= 23 && !Settings.canDrawOverlays(this)) {
            toast("请允许「显示在其他应用的上层」，然后返回这里再点一次");
            try {
                startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName())));
            } catch (Exception e) {
                startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION));
            }
            return;
        }
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission("android.permission.POST_NOTIFICATIONS")
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, REQ_NOTIF);
        }
        Intent i = new Intent(this, FloatingService.class);
        try {
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(i);
            else startService(i);
        } catch (Exception e) {
            toast("悬浮窗启动失败：" + e.getMessage());
        }
        h.postDelayed(this::refreshFloatStatus, 400);
    }

    // ---------------- 导入音频 ----------------

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_IMPORT || resultCode != RESULT_OK || data == null || data.getData() == null) {
            return;
        }
        Uri uri = data.getData();
        String display = queryName(uri);
        String ext = ".audio";
        String name = display;
        int dot = display.lastIndexOf('.');
        if (dot > 0) {
            ext = display.substring(dot);
            name = display.substring(0, dot);
        }
        File out = store.newFile(ext);
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            if (in == null) throw new Exception("无法读取");
            ClipStore.copy(in, out);
            store.add(name, "导入", out, false);
            toast("已导入：" + name);
        } catch (Exception e) {
            out.delete();
            toast("导入失败");
        }
    }

    private String queryName(Uri uri) {
        try (Cursor c = getContentResolver().query(uri,
                new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                String s = c.getString(0);
                if (!TextUtils.isEmpty(s)) return s;
            }
        } catch (Exception ignored) {
        }
        return "导入的音频";
    }

    // ---------------- 文字转语音 ----------------

    private String ttsText() {
        String s = ttsInput.getText().toString().trim();
        if (s.isEmpty()) toast("先输入要说的话");
        return s;
    }

    private void ttsPreview() {
        String s = ttsText();
        if (s.isEmpty()) return;
        if (!tts.isReady()) {
            toast("语音引擎还不可用");
            return;
        }
        Player.stop(this);
        final File f = new File(getCacheDir(), "tts_preview.wav");
        tts.synth(s, f, ok -> {
            if (ok) Player.play(this, f, null);
            else toast("合成失败");
        });
    }

    private void ttsSave() {
        final String s = ttsText();
        if (s.isEmpty()) return;
        if (!tts.isReady()) {
            toast("语音引擎还不可用");
            return;
        }
        final File f = store.newFile(".wav");
        tts.synth(s, f, ok -> {
            if (ok && f.length() > 0) {
                String name = s.length() > 16 ? s.substring(0, 16) + "…" : s;
                store.add(name, "文字转语音", f, false);
                toast("已保存到语音包");
                selectTab(0);
            } else {
                f.delete();
                toast("合成失败");
            }
        });
    }

    private void seedStarter(final int i) {
        if (i >= STARTER_LINES.length) {
            Prefs.setSeeded(this);
            return;
        }
        final File f = store.newFile(".wav");
        tts.synth(STARTER_LINES[i], f, ok -> {
            if (ok && f.length() > 0) store.add(STARTER_LINES[i], "整蛊台词", f, true);
            else f.delete();
            seedStarter(i + 1);
        });
    }

    // ---------------- 录音 ----------------

    private void startRecording() {
        if (Build.VERSION.SDK_INT >= 23
                && checkSelfPermission("android.permission.RECORD_AUDIO") != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{"android.permission.RECORD_AUDIO"}, REQ_REC);
            return;
        }
        Player.stop(this);
        File f = new File(getCacheDir(), "record.m4a");
        f.delete();
        MediaRecorder r = new MediaRecorder();
        try {
            r.setAudioSource(MediaRecorder.AudioSource.MIC);
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            r.setAudioSamplingRate(44100);
            r.setAudioEncodingBitRate(128000);
            r.setOutputFile(f.getAbsolutePath());
            r.prepare();
            r.start();
        } catch (Exception e) {
            r.release();
            toast("录音启动失败");
            return;
        }
        recorder = r;
        recFile = null;
        recStart = System.currentTimeMillis();
        recBtn.setText("■ 停止录音");
        Ui.setEnabled(recPlay, false);
        Ui.setEnabled(recSave, false);
        h.post(recTick);
    }

    private void stopRecording() {
        MediaRecorder r = recorder;
        recorder = null;
        h.removeCallbacks(recTick);
        recBtn.setText("● 重新录音");
        boolean ok = true;
        try {
            r.stop();
        } catch (Exception e) {
            ok = false; // 录得太短会失败
        }
        r.release();
        File f = new File(getCacheDir(), "record.m4a");
        if (ok && f.length() > 0) {
            recFile = f;
            Ui.setEnabled(recPlay, true);
            Ui.setEnabled(recSave, true);
        } else {
            toast("录得太短了，再试一次");
        }
    }

    private void saveRecording() {
        if (recFile == null) return;
        File out = store.newFile(".m4a");
        try (InputStream in = new java.io.FileInputStream(recFile)) {
            ClipStore.copy(in, out);
        } catch (Exception e) {
            toast("保存失败");
            return;
        }
        final EditText et = new EditText(this);
        et.setHint("给这段录音起个名字");
        LinearLayout wrap = new LinearLayout(this);
        wrap.setPadding(dp(20), dp(8), dp(20), 0);
        wrap.addView(et, Ui.matchWrap(this, 0));
        new AlertDialog.Builder(this)
                .setTitle("保存录音")
                .setView(wrap)
                .setCancelable(false)
                .setPositiveButton("保存", (d, w) -> {
                    String n = et.getText().toString().trim();
                    if (n.isEmpty()) n = "我的录音";
                    store.add(n, "我的录音 · 播放时可变声", out, false);
                    toast("已保存到语音包");
                    selectTab(0);
                })
                .setNegativeButton("取消", (d, w) -> out.delete())
                .show();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == REQ_REC) {
            if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) startRecording();
            else toast("需要麦克风权限才能录音");
        }
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }
}
