package com.mengyin.prank;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** 语音包存储：音频文件放在 App 私有目录，列表信息存成 JSON。 */
public final class ClipStore {

    public static final class Clip {
        public String id;
        public String name;
        public String category;
        public String file;
    }

    private static ClipStore instance;

    private final File dir;
    private final File meta;
    private final List<Clip> clips = new ArrayList<>();
    private final List<Runnable> listeners = new ArrayList<>();
    private final Handler main = new Handler(Looper.getMainLooper());

    public static synchronized ClipStore get(Context c) {
        if (instance == null) instance = new ClipStore(c.getApplicationContext());
        return instance;
    }

    private ClipStore(Context c) {
        dir = new File(c.getFilesDir(), "clips");
        if (!dir.exists()) dir.mkdirs();
        meta = new File(dir, "clips.json");
        load();
    }

    private void load() {
        clips.clear();
        if (!meta.exists()) return;
        try (InputStream in = new FileInputStream(meta)) {
            byte[] buf = new byte[(int) meta.length()];
            int off = 0;
            while (off < buf.length) {
                int n = in.read(buf, off, buf.length - off);
                if (n < 0) break;
                off += n;
            }
            JSONArray arr = new JSONArray(new String(buf, 0, off, StandardCharsets.UTF_8));
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                Clip c = new Clip();
                c.id = o.getString("id");
                c.name = o.getString("name");
                c.category = o.optString("category", "");
                c.file = o.getString("file");
                if (new File(dir, c.file).exists()) clips.add(c);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void save() {
        try {
            JSONArray arr = new JSONArray();
            for (Clip c : clips) {
                JSONObject o = new JSONObject();
                o.put("id", c.id);
                o.put("name", c.name);
                o.put("category", c.category);
                o.put("file", c.file);
                arr.put(o);
            }
            try (OutputStream out = new FileOutputStream(meta)) {
                out.write(arr.toString().getBytes(StandardCharsets.UTF_8));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        notifyChanged();
    }

    public synchronized List<Clip> all() {
        return new ArrayList<>(clips);
    }

    public synchronized boolean isEmpty() {
        return clips.isEmpty();
    }

    public File fileOf(Clip c) {
        return new File(dir, c.file);
    }

    /** 新建一个空文件路径，用于保存录音、合成语音或导入的音频。 */
    public File newFile(String ext) {
        if (ext == null || ext.isEmpty()) ext = ".audio";
        if (!ext.startsWith(".")) ext = "." + ext;
        return new File(dir, UUID.randomUUID().toString().replace("-", "") + ext);
    }

    /** 把已经写好的文件登记进语音包。文件必须在 newFile() 给出的目录里。 */
    public synchronized Clip add(String name, String category, File f, boolean atEnd) {
        Clip c = new Clip();
        c.id = UUID.randomUUID().toString();
        c.name = name;
        c.category = category;
        c.file = f.getName();
        if (atEnd) clips.add(c);
        else clips.add(0, c);
        save();
        return c;
    }

    public synchronized void rename(Clip c, String name) {
        for (Clip x : clips) {
            if (x.id.equals(c.id)) x.name = name;
        }
        save();
    }

    public synchronized void delete(Clip c) {
        for (int i = 0; i < clips.size(); i++) {
            if (clips.get(i).id.equals(c.id)) {
                new File(dir, clips.get(i).file).delete();
                clips.remove(i);
                break;
            }
        }
        save();
    }

    public static void copy(InputStream in, File out) throws Exception {
        try (OutputStream os = new FileOutputStream(out)) {
            byte[] buf = new byte[16 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) os.write(buf, 0, n);
        }
    }

    public synchronized void addListener(Runnable r) {
        listeners.add(r);
    }

    public synchronized void removeListener(Runnable r) {
        listeners.remove(r);
    }

    private void notifyChanged() {
        final List<Runnable> copy = new ArrayList<>(listeners);
        main.post(() -> {
            for (Runnable r : copy) r.run();
        });
    }
}
