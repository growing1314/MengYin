package com.mengyin.prank;

/** 变声效果：通过播放时改变音调（pitch）和语速（speed）实现。 */
public final class VoicePreset {
    public final String id;
    public final String name;
    public final float pitch;
    public final float speed;

    VoicePreset(String id, String name, float pitch, float speed) {
        this.id = id;
        this.name = name;
        this.pitch = pitch;
        this.speed = speed;
    }

    public static final VoicePreset[] ALL = {
            new VoicePreset("normal", "原声", 1.0f, 1.0f),
            new VoicePreset("loli", "萝莉", 1.6f, 1.08f),
            new VoicePreset("girl", "少女", 1.3f, 1.0f),
            new VoicePreset("boy", "正太", 1.45f, 1.05f),
            new VoicePreset("uncle", "大叔", 0.75f, 0.95f),
            new VoicePreset("grandpa", "老爷爷", 0.68f, 0.85f),
            new VoicePreset("monster", "怪兽", 0.5f, 0.85f),
            new VoicePreset("chipmunk", "花栗鼠", 2.0f, 1.35f),
            new VoicePreset("slow", "慢动作", 0.85f, 0.6f),
    };

    public static VoicePreset byId(String id) {
        for (VoicePreset p : ALL) {
            if (p.id.equals(id)) return p;
        }
        return ALL[0];
    }

    public VoicePreset next() {
        for (int i = 0; i < ALL.length; i++) {
            if (ALL[i] == this) return ALL[(i + 1) % ALL.length];
        }
        return ALL[0];
    }
}
