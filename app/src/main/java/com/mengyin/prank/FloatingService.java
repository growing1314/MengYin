package com.mengyin.prank;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;

/**
 * 悬浮窗服务：在微信等 App 上层显示一个悬浮球。
 * 用法：点悬浮球 → 选一条语音 → 倒计时期间按住微信的「按住说话」→ 播完震动提示 → 松手发送。
 */
public class FloatingService extends Service {

    public static final String ACTION_STOP = "com.mengyin.prank.STOP";
    private static final String CHANNEL = "floating";

    public static boolean running;

    private WindowManager wm;
    private WindowManager.LayoutParams lp;
    private LinearLayout root;
    private TextView bubble;
    private LinearLayout panel;
    private LinearLayout list;
    private ScrollView listScroll;
    private TextView presetBtn;
    private TextView delayBtn;
    private TextView boostBtn;

    private final Handler h = new Handler(Looper.getMainLooper());
    private final Runnable storeListener = this::refreshList;
    private Runnable countdown;
    private boolean busy;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        running = true;
        startAsForeground();
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        buildViews();
        ClipStore.get(this).addListener(storeListener);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopSelf();
        }
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        running = false;
        cancelCountdown();
        Player.stop(this);
        ClipStore.get(this).removeListener(storeListener);
        try {
            if (root != null) wm.removeView(root);
        } catch (Exception ignored) {
        }
        super.onDestroy();
    }

    // ---------------- 前台通知 ----------------

    private void startAsForeground() {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        Notification.Builder b;
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(CHANNEL, "悬浮窗",
                    NotificationManager.IMPORTANCE_LOW);
            nm.createNotificationChannel(ch);
            b = new Notification.Builder(this, CHANNEL);
        } else {
            b = new Notification.Builder(this);
        }
        Intent stop = new Intent(this, FloatingService.class).setAction(ACTION_STOP);
        PendingIntent stopPi = PendingIntent.getService(this, 1, stop,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Intent open = new Intent(this, MainActivity.class);
        PendingIntent openPi = PendingIntent.getActivity(this, 2, open,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification n = b.setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .setContentTitle("萌音悬浮窗运行中")
                .setContentText("点悬浮球选语音，按住微信说话按钮录进去")
                .setContentIntent(openPi)
                .addAction(new Notification.Action.Builder(null, "关闭悬浮窗", stopPi).build())
                .setOngoing(true)
                .build();
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
        } else {
            startForeground(1, n);
        }
    }

    // ---------------- 界面 ----------------

    private int dp(float v) {
        return Ui.dp(this, v);
    }

    private void buildViews() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);

        bubble = Ui.text(this, "萌", 20, 0xFFFFFFFF, true);
        bubble.setGravity(Gravity.CENTER);
        resetBubble();
        root.addView(bubble, new LinearLayout.LayoutParams(dp(58), dp(58)));
        setupDrag();

        panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(10), dp(10), dp(10), dp(10));
        panel.setBackground(Ui.roundStroke(this, 0xF7FFFFFF, Ui.PINK_LIGHT, 16));
        panel.setElevation(dp(6));
        panel.setVisibility(View.GONE);

        LinearLayout row1 = new LinearLayout(this);
        presetBtn = smallBtn("");
        presetBtn.setOnClickListener(v -> {
            Prefs.setPreset(this, Prefs.preset(this).next());
            updateButtons();
        });
        delayBtn = smallBtn("");
        delayBtn.setOnClickListener(v -> {
            Prefs.setDelaySeconds(this, (Prefs.delaySeconds(this) + 1) % 4);
            updateButtons();
        });
        row1.addView(presetBtn, Ui.weight(this, 0));
        row1.addView(delayBtn, Ui.weight(this, 6));
        panel.addView(row1);

        LinearLayout row2 = new LinearLayout(this);
        boostBtn = smallBtn("");
        boostBtn.setOnClickListener(v -> {
            Prefs.setBoost(this, !Prefs.boost(this));
            updateButtons();
        });
        TextView close = smallBtn("关闭悬浮窗");
        close.setOnClickListener(v -> stopSelf());
        row2.addView(boostBtn, Ui.weight(this, 0));
        row2.addView(close, Ui.weight(this, 6));
        LinearLayout.LayoutParams r2 = Ui.matchWrap(this, 6);
        panel.addView(row2, r2);

        TextView tip = Ui.text(this, "点一条 → 去按住微信说话 → 震动后松手", 11, Ui.SUB, false);
        tip.setPadding(dp(2), dp(8), dp(2), dp(4));
        panel.addView(tip);

        listScroll = new ScrollView(this);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        listScroll.addView(list);
        panel.addView(listScroll);

        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(dp(250),
                LinearLayout.LayoutParams.WRAP_CONTENT);
        plp.topMargin = dp(6);
        root.addView(panel, plp);

        updateButtons();
        refreshList();

        int type = Build.VERSION.SDK_INT >= 26
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;
        lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.START;
        lp.x = dp(12);
        lp.y = getResources().getDisplayMetrics().heightPixels / 4;
        wm.addView(root, lp);
    }

    private TextView smallBtn(String s) {
        TextView b = Ui.text(this, s, 12, Ui.PINK_DARK, true);
        b.setGravity(Gravity.CENTER);
        b.setSingleLine(true);
        b.setPadding(dp(6), dp(7), dp(6), dp(7));
        b.setBackground(Ui.round(this, Ui.PINK_LIGHT, 12));
        return b;
    }

    private void updateButtons() {
        presetBtn.setText("声音：" + Prefs.preset(this).name);
        int d = Prefs.delaySeconds(this);
        delayBtn.setText(d == 0 ? "立即播放" : "延迟 " + d + " 秒");
        boostBtn.setText(Prefs.boost(this) ? "音量加强：开" : "音量加强：关");
    }

    private void refreshList() {
        if (list == null) return;
        list.removeAllViews();
        List<ClipStore.Clip> clips = ClipStore.get(this).all();
        if (clips.isEmpty()) {
            TextView e = Ui.text(this, "还没有语音，打开 App 添加吧", 13, Ui.SUB, false);
            e.setPadding(dp(4), dp(10), dp(4), dp(10));
            list.addView(e);
        }
        for (final ClipStore.Clip c : clips) {
            TextView t = Ui.text(this, c.name, 14, Ui.TEXT, false);
            t.setSingleLine(true);
            t.setEllipsize(TextUtils.TruncateAt.END);
            t.setPadding(dp(10), dp(10), dp(10), dp(10));
            t.setBackground(Ui.round(this, 0xFFFFF0F5, 10));
            t.setOnClickListener(v -> pick(c));
            LinearLayout.LayoutParams l = Ui.matchWrap(this, 6);
            list.addView(t, l);
        }
        int maxH = dp(280);
        ViewGroupHeight.set(listScroll, clips.size() > 6 ? maxH
                : LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    /** 小工具：设置 ScrollView 高度。 */
    private static final class ViewGroupHeight {
        static void set(View v, int h) {
            LinearLayout.LayoutParams l = (LinearLayout.LayoutParams) v.getLayoutParams();
            if (l == null) {
                l = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, h);
            } else {
                l.height = h;
            }
            v.setLayoutParams(l);
        }
    }

    private void togglePanel() {
        if (busy) {
            // 倒计时或播放中再点一下 = 取消
            cancelCountdown();
            Player.stop(this);
            busy = false;
            resetBubble();
            return;
        }
        boolean show = panel.getVisibility() != View.VISIBLE;
        if (show) {
            updateButtons();
            refreshList();
        }
        panel.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    // ---------------- 播放流程 ----------------

    private void pick(ClipStore.Clip c) {
        panel.setVisibility(View.GONE);
        busy = true;
        final int delay = Prefs.delaySeconds(this);
        if (delay <= 0) {
            playNow(c);
            return;
        }
        final int[] left = {delay};
        setBubble(String.valueOf(left[0]), 0xFFFFA940);
        countdown = new Runnable() {
            @Override
            public void run() {
                left[0]--;
                if (left[0] <= 0) {
                    countdown = null;
                    playNow(c);
                } else {
                    setBubble(String.valueOf(left[0]), 0xFFFFA940);
                    h.postDelayed(this, 1000);
                }
            }
        };
        h.postDelayed(countdown, 1000);
    }

    private void playNow(ClipStore.Clip c) {
        Player.play(this, ClipStore.get(this).fileOf(c), new Player.Listener() {
            @Override
            public void onStart() {
                setBubble("♪", Ui.PINK_DARK);
            }

            @Override
            public void onDone(boolean ok) {
                if (!busy) return;
                busy = false;
                if (ok) {
                    vibrate();
                    setBubble("松手", Ui.GREEN);
                    h.postDelayed(() -> {
                        if (!busy) resetBubble();
                    }, 1800);
                } else {
                    resetBubble();
                }
            }
        });
    }

    private void cancelCountdown() {
        if (countdown != null) {
            h.removeCallbacks(countdown);
            countdown = null;
        }
    }

    private void setBubble(String s, int color) {
        bubble.setText(s);
        bubble.setTextSize(s.length() > 1 ? 15 : 20);
        android.graphics.drawable.GradientDrawable g = Ui.round(this, color, 29);
        g.setStroke(dp(3), 0xFFFFFFFF);
        bubble.setBackground(g);
    }

    private void resetBubble() {
        setBubble("萌", Ui.PINK);
    }

    private void vibrate() {
        try {
            Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            if (v == null) return;
            if (Build.VERSION.SDK_INT >= 26) {
                v.vibrate(VibrationEffect.createOneShot(250, VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                v.vibrate(250);
            }
        } catch (Exception ignored) {
        }
    }

    // ---------------- 拖动悬浮球 ----------------

    private void setupDrag() {
        final int slop = ViewConfiguration.get(this).getScaledTouchSlop();
        bubble.setOnTouchListener(new View.OnTouchListener() {
            float downX, downY;
            int startX, startY;
            boolean moved;

            @Override
            public boolean onTouch(View v, MotionEvent e) {
                switch (e.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        downX = e.getRawX();
                        downY = e.getRawY();
                        startX = lp.x;
                        startY = lp.y;
                        moved = false;
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        float dx = e.getRawX() - downX;
                        float dy = e.getRawY() - downY;
                        if (!moved && Math.abs(dx) < slop && Math.abs(dy) < slop) return true;
                        moved = true;
                        lp.x = Math.max(0, startX + (int) dx);
                        lp.y = Math.max(0, startY + (int) dy);
                        wm.updateViewLayout(root, lp);
                        return true;
                    case MotionEvent.ACTION_UP:
                        if (!moved) togglePanel();
                        return true;
                    default:
                        return false;
                }
            }
        });
    }
}
