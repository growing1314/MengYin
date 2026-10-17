package com.mengyin.prank;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.PlaybackParams;
import android.widget.Toast;

import java.io.File;

/** 播放器：按当前变声效果播放音频，App 和悬浮窗共用。 */
public final class Player {

    public interface Listener {
        void onStart();

        void onDone(boolean ok);
    }

    private static MediaPlayer mp;
    private static Listener current;
    private static int savedVolume = -1;

    private Player() {}

    public static boolean isPlaying() {
        return mp != null;
    }

    public static void play(Context ctx, File f, Listener l) {
        final Context app = ctx.getApplicationContext();
        stop(app);
        VoicePreset p = Prefs.preset(app);
        MediaPlayer m = new MediaPlayer();
        try {
            m.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build());
            m.setDataSource(f.getAbsolutePath());
            m.prepare();
        } catch (Exception e) {
            m.release();
            Toast.makeText(app, "这个音频播放不了", Toast.LENGTH_SHORT).show();
            if (l != null) l.onDone(false);
            return;
        }
        mp = m;
        current = l;
        if (Prefs.boost(app)) boostVolume(app);
        m.setOnCompletionListener(x -> finish(app, m, true));
        m.setOnErrorListener((x, what, extra) -> {
            finish(app, m, false);
            return true;
        });
        try {
            if (p.pitch != 1f || p.speed != 1f) {
                // 在 prepared 状态设置速度会直接开始播放
                m.setPlaybackParams(new PlaybackParams().setPitch(p.pitch).setSpeed(p.speed));
            }
            if (!m.isPlaying()) m.start();
        } catch (Exception e) {
            // 少数机型不支持变调，退回原声播放
            try {
                if (!m.isPlaying()) m.start();
            } catch (Exception ignored) {
                finish(app, m, false);
                return;
            }
        }
        if (l != null) l.onStart();
    }

    public static void stop(Context ctx) {
        if (mp != null) finish(ctx.getApplicationContext(), mp, false);
    }

    private static void finish(Context app, MediaPlayer m, boolean ok) {
        if (m != mp) return;
        Listener l = current;
        mp = null;
        current = null;
        try {
            m.stop();
        } catch (Exception ignored) {
        }
        m.release();
        restoreVolume(app);
        if (l != null) l.onDone(ok);
    }

    private static void boostVolume(Context app) {
        try {
            AudioManager am = (AudioManager) app.getSystemService(Context.AUDIO_SERVICE);
            if (savedVolume < 0) savedVolume = am.getStreamVolume(AudioManager.STREAM_MUSIC);
            am.setStreamVolume(AudioManager.STREAM_MUSIC,
                    am.getStreamMaxVolume(AudioManager.STREAM_MUSIC), 0);
        } catch (Exception ignored) {
        }
    }

    private static void restoreVolume(Context app) {
        if (savedVolume < 0) return;
        try {
            AudioManager am = (AudioManager) app.getSystemService(Context.AUDIO_SERVICE);
            am.setStreamVolume(AudioManager.STREAM_MUSIC, savedVolume, 0);
        } catch (Exception ignored) {
        }
        savedVolume = -1;
    }
}
