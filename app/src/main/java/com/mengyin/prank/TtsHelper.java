package com.mengyin.prank;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;

import java.io.File;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * 文字转语音：使用手机系统的语音引擎，把文字合成为音频文件。
 * 合成的是原声，播放时再套用当前的变声效果。
 */
public final class TtsHelper implements TextToSpeech.OnInitListener {

    public interface Ready {
        void onReady(boolean ok);
    }

    public interface Done {
        void onDone(boolean ok);
    }

    private final TextToSpeech tts;
    private final Ready readyCb;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Map<String, Done> pending = new HashMap<>();
    private boolean ready;

    public TtsHelper(Context c, Ready cb) {
        readyCb = cb;
        tts = new TextToSpeech(c.getApplicationContext(), this);
    }

    public boolean isReady() {
        return ready;
    }

    @Override
    public void onInit(int status) {
        boolean ok = false;
        if (status == TextToSpeech.SUCCESS) {
            int r = tts.setLanguage(Locale.SIMPLIFIED_CHINESE);
            if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) {
                r = tts.setLanguage(Locale.CHINESE);
            }
            ok = r != TextToSpeech.LANG_MISSING_DATA && r != TextToSpeech.LANG_NOT_SUPPORTED;
            tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                @Override
                public void onStart(String id) {
                }

                @Override
                public void onDone(String id) {
                    finish(id, true);
                }

                @Override
                public void onError(String id) {
                    finish(id, false);
                }
            });
        }
        ready = ok;
        final boolean result = ok;
        main.post(() -> readyCb.onReady(result));
    }

    private void finish(String id, boolean ok) {
        main.post(() -> {
            Done d = pending.remove(id);
            if (d != null) d.onDone(ok);
        });
    }

    /** 合成到文件（wav）。必须在主线程调用。 */
    public void synth(String text, File out, Done d) {
        if (!ready) {
            d.onDone(false);
            return;
        }
        String id = UUID.randomUUID().toString();
        pending.put(id, d);
        tts.setPitch(1.0f);
        tts.setSpeechRate(1.0f);
        int r = tts.synthesizeToFile(text, new Bundle(), out, id);
        if (r != TextToSpeech.SUCCESS) {
            pending.remove(id);
            d.onDone(false);
        }
    }

    public void shutdown() {
        tts.shutdown();
    }
}
