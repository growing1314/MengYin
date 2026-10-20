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
import java.text.Collator;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/** 语音包存储：音频文件放在 App 私有目录，列表信息存成 JSON。 */
public final class ClipStore {

    public static final String UNCATEGORIZED = "未分类";

    /** 筛选：全部 / 只看收藏 / 某个分类。 */
    public static final String FILTER_ALL = "";
    public static final String FILTER_FAV = "__fav__";

    public static final String SORT_NEW = "new";
    public static final String SORT_USED = "used";
    public static final String SORT_NAME = "name";

    public static final class Clip {
        public String id;
        public String name;
        public String category;
        public String file;
        public boolean favorite;
        public int plays;
        public long lastUsed;
        public long added;

        public String categoryOrDefault() {
            return category == null || category.isEmpty() ? UNCATEGORIZED : category;
        }
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
            long now = System.currentTimeMillis();
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                Clip c = new Clip();
                c.id = o.getString("id");
                c.name = o.getString("name");
                c.category = o.optString("category", "");
                c.file = o.getString("file");
                c.favorite = o.optBoolean("favorite", false);
                c.plays = o.optInt("plays", 0);
                c.lastUsed = o.optLong("lastUsed", 0);
                // 旧版本没有添加时间：按原来的顺序（新的在前）补一个
                c.added = o.optLong("added", now - i);
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
                o.put("favorite", c.favorite);
                o.put("plays", c.plays);
                o.put("lastUsed", c.lastUsed);
                o.put("added", c.added);
                arr.put(o);
            }
            File tmp = new File(dir, "clips.json.tmp");
            try (OutputStream out = new FileOutputStream(tmp)) {
                out.write(arr.toString().getBytes(StandardCharsets.UTF_8));
            }
            if (!tmp.renameTo(meta)) {
                meta.delete();
                tmp.renameTo(meta);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        notifyChanged();
    }

    // ---------------- 查询 ----------------

    public synchronized List<Clip> all() {
        return new ArrayList<>(clips);
    }

    public synchronized boolean isEmpty() {
        return clips.isEmpty();
    }

    public synchronized int count() {
        return clips.size();
    }

    public synchronized int favoriteCount() {
        int n = 0;
        for (Clip c : clips) if (c.favorite) n++;
        return n;
    }

    /** 所有分类（按第一次出现的顺序，未分类放最后）。 */
    public synchronized List<String> categories() {
        Set<String> set = new LinkedHashSet<>();
        boolean hasUncat = false;
        List<Clip> sorted = new ArrayList<>(clips);
        sorted.sort((a, b) -> Long.compare(a.added, b.added));
        for (Clip c : sorted) {
            String cat = c.categoryOrDefault();
            if (UNCATEGORIZED.equals(cat)) hasUncat = true;
            else set.add(cat);
        }
        List<String> out = new ArrayList<>(set);
        if (hasUncat) out.add(UNCATEGORIZED);
        return out;
    }

    public synchronized int countIn(String category) {
        int n = 0;
        for (Clip c : clips) if (c.categoryOrDefault().equals(category)) n++;
        return n;
    }

    /** 按筛选、关键字和排序方式取出列表。 */
    public synchronized List<Clip> query(String filter, String keyword, String sort) {
        List<Clip> out = new ArrayList<>();
        String kw = keyword == null ? "" : keyword.trim().toLowerCase(Locale.ROOT);
        for (Clip c : clips) {
            if (FILTER_FAV.equals(filter)) {
                if (!c.favorite) continue;
            } else if (filter != null && !filter.isEmpty()) {
                if (!c.categoryOrDefault().equals(filter)) continue;
            }
            if (!kw.isEmpty()) {
                String hay = (c.name + " " + c.categoryOrDefault()).toLowerCase(Locale.ROOT);
                if (!hay.contains(kw)) continue;
            }
            out.add(c);
        }
        out.sort(comparator(sort));
        return out;
    }

    private static Comparator<Clip> comparator(String sort) {
        if (SORT_USED.equals(sort)) {
            return (a, b) -> {
                if (a.plays != b.plays) return Integer.compare(b.plays, a.plays);
                return Long.compare(b.lastUsed, a.lastUsed);
            };
        }
        if (SORT_NAME.equals(sort)) {
            final Collator col = Collator.getInstance(Locale.CHINA);
            return (a, b) -> col.compare(a.name, b.name);
        }
        return (a, b) -> Long.compare(b.added, a.added);
    }

    public File fileOf(Clip c) {
        return new File(dir, c.file);
    }

    // ---------------- 修改 ----------------

    /** 新建一个空文件路径，用于保存录音、合成语音或导入的音频。 */
    public File newFile(String ext) {
        if (ext == null || ext.isEmpty()) ext = ".audio";
        if (!ext.startsWith(".")) ext = "." + ext;
        return new File(dir, UUID.randomUUID().toString().replace("-", "") + ext);
    }

    /** 把已经写好的文件登记进语音包。文件必须在 newFile() 给出的目录里。 */
    public synchronized Clip add(String name, String category, File f) {
        Clip c = make(name, category, f, System.currentTimeMillis());
        clips.add(c);
        save();
        return c;
    }

    /** 批量登记，只写一次列表文件。names/files 一一对应。 */
    public synchronized void addAll(List<String> names, String category, List<File> files) {
        long now = System.currentTimeMillis();
        for (int i = 0; i < files.size(); i++) {
            // 让排在前面的文件“更新”，按选择顺序显示
            clips.add(make(names.get(i), category, files.get(i), now + files.size() - i));
        }
        save();
    }

    private static Clip make(String name, String category, File f, long added) {
        Clip c = new Clip();
        c.id = UUID.randomUUID().toString();
        c.name = name;
        c.category = category == null ? "" : category;
        c.file = f.getName();
        c.added = added;
        return c;
    }

    private Clip find(String id) {
        for (Clip x : clips) if (x.id.equals(id)) return x;
        return null;
    }

    public synchronized void rename(Clip c, String name) {
        Clip x = find(c.id);
        if (x != null) x.name = name;
        save();
    }

    public synchronized void setFavorite(Collection<String> ids, boolean fav) {
        for (String id : ids) {
            Clip x = find(id);
            if (x != null) x.favorite = fav;
        }
        save();
    }

    public synchronized void toggleFavorite(Clip c) {
        Clip x = find(c.id);
        if (x != null) x.favorite = !x.favorite;
        save();
    }

    public synchronized void move(Collection<String> ids, String category) {
        for (String id : ids) {
            Clip x = find(id);
            if (x != null) x.category = category;
        }
        save();
    }

    /** 把整个分类改名（或合并到另一个分类）。 */
    public synchronized void renameCategory(String from, String to) {
        for (Clip x : clips) {
            if (x.categoryOrDefault().equals(from)) x.category = to;
        }
        save();
    }

    public synchronized void markUsed(Clip c) {
        Clip x = find(c.id);
        if (x == null) return;
        x.plays++;
        x.lastUsed = System.currentTimeMillis();
        save();
    }

    public synchronized void delete(Collection<String> ids) {
        for (int i = clips.size() - 1; i >= 0; i--) {
            Clip x = clips.get(i);
            if (ids.contains(x.id)) {
                new File(dir, x.file).delete();
                clips.remove(i);
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
