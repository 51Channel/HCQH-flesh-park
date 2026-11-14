package world.hechao.fleshpark.client;

import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundSource;
import org.lwjgl.BufferUtils;
import org.lwjgl.openal.AL10;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;
import java.util.HashMap;
import java.util.Map;

/**
 * 配音和音乐。原版声音要 ogg，这台机器做不出来，所以 wav 直接交给游戏已经开好的 OpenAL。
 * 音高能实时改，音乐才能慢下来走调。
 * 这些声音不经过原版的声音引擎，1.21.11 的原版也不再靠 OpenAL 听者音量管主音量，所以音量滑块要自己乘：
 * 每个频道对应设置里的一个滑块（再乘主音量），每 tick 读一次，拖滑块马上生效。
 */
final class Audio {
    private static final class Channel {
        int source = -1;
        float gain, gainTarget, gainStep, pitch = 1, pitchTarget = 1, pitchStep;
        /** 对应设置里哪个滑块。 */
        SoundSource slider = SoundSource.MASTER;
        /** 上次真正交给 OpenAL 的音量（频道音量 × 滑块）。 */
        float applied = -1;
    }

    /** 频道 → 设置里的滑块。背景音乐跟“音乐”，环境声跟“环境”，广播和人声跟“语音/讲话”，其余只跟主音量。 */
    private static SoundSource slider(String channel) {
        return switch (channel) {
            case "music" -> SoundSource.MUSIC;
            case "amb", "tapebed", "bus", "moan" -> SoundSource.AMBIENT;
            case "voice", "tramradio", "tramguide", "busradio", "tv" -> SoundSource.VOICE;
            default -> SoundSource.MASTER;
        };
    }

    private static float volume(SoundSource slider) {
        try {
            return Math.max(0, Math.min(1, Minecraft.getInstance().options.getFinalSoundSourceVolume(slider)));
        } catch (RuntimeException notReady) {
            return 1;
        }
    }

    private static void apply(Channel c) {
        float v = c.gain * volume(c.slider);
        if (v == c.applied) return;
        c.applied = v;
        AL10.alSourcef(c.source, AL10.AL_GAIN, v);
    }

    private static final Map<String, Integer> BUFFERS = new HashMap<>();
    private static final Map<String, Channel> CHANNELS = new HashMap<>();

    private Audio() {
    }

    static void play(String channel, String clip, float gain, float pitch, boolean loop, int fadeTicks) {
        try {
            Channel c = CHANNELS.computeIfAbsent(channel, k -> new Channel());
            release(c);
            int buffer = buffer(clip);
            if (buffer < 0) return;
            c.source = AL10.alGenSources();
            AL10.alSourcei(c.source, AL10.AL_BUFFER, buffer);
            AL10.alSourcei(c.source, AL10.AL_SOURCE_RELATIVE, AL10.AL_TRUE);
            AL10.alSource3f(c.source, AL10.AL_POSITION, 0, 0, 0);
            AL10.alSourcei(c.source, AL10.AL_LOOPING, loop ? AL10.AL_TRUE : AL10.AL_FALSE);
            c.gain = fadeTicks > 0 ? 0 : gain;
            c.gainTarget = gain;
            c.gainStep = fadeTicks > 0 ? gain / fadeTicks : 0;
            c.pitch = c.pitchTarget = pitch;
            c.slider = slider(channel);
            c.applied = -1;
            apply(c);
            AL10.alSourcef(c.source, AL10.AL_PITCH, pitch);
            AL10.alSourcePlay(c.source);
        } catch (RuntimeException | IOException | UnsatisfiedLinkError error) {
            System.err.println("[血肉公园] 放不了 " + clip + "：" + error);
        }
    }

    static void gain(String channel, float gain, int ticks) {
        Channel c = CHANNELS.get(channel);
        if (c == null) return;
        c.gainTarget = gain;
        c.gainStep = Math.abs(gain - c.gain) / Math.max(1, ticks);
    }

    static void pitch(String channel, float pitch, int ticks) {
        Channel c = CHANNELS.get(channel);
        if (c == null) return;
        c.pitchTarget = pitch;
        c.pitchStep = Math.abs(pitch - c.pitch) / Math.max(1, ticks);
    }

    /** 测试用：改设置里的一个音量滑块（master、music、ambient、voice……），不存盘。测试客户端每次启动都把主音量写成 0。 */
    static void slider(String name, double value) {
        for (SoundSource s : SoundSource.values()) {
            if (s.getName().equals(name)) {
                Minecraft.getInstance().options.getSoundSourceOptionInstance(s).set(Math.max(0, Math.min(1, value)));
                return;
            }
        }
        System.err.println("[血肉公园] 没有这个音量滑块：" + name);
    }

    /** 测试用：把现在在放的写进日志。 */
    static void status() {
        StringBuilder b = new StringBuilder("[血肉公园] 声音");
        for (Map.Entry<String, Channel> e : CHANNELS.entrySet()) {
            Channel c = e.getValue();
            int state = c.source < 0 ? 0 : AL10.alGetSourcei(c.source, AL10.AL_SOURCE_STATE);
            b.append(' ').append(e.getKey()).append('=').append(state == AL10.AL_PLAYING ? "在放" : "没放").append('/').append(c.gain).append('/').append(c.pitch)
                    .append('/').append(c.slider.getName()).append('×').append(volume(c.slider));
        }
        b.append(" 已载入 ").append(BUFFERS.keySet());
        System.out.println(b);
    }

    static void stopAll() {
        for (Channel c : CHANNELS.values()) release(c);
    }

    /** 每 tick 走一步淡入淡出。淡到 0 的、放完的都放掉。 */
    static void tick() {
        try {
            for (Channel c : CHANNELS.values()) {
                if (c.source < 0) continue;
                if (!AL10.alIsSource(c.source)) {
                    // 换了输出设备或者重载了声音，OpenAL 换了一个上下文，旧的都没了
                    c.source = -1;
                    BUFFERS.clear();
                    continue;
                }
                if (c.gain != c.gainTarget) {
                    c.gain = approach(c.gain, c.gainTarget, c.gainStep);
                    if (c.gain == 0 && c.gainTarget == 0) {
                        release(c);
                        continue;
                    }
                }
                apply(c);
                if (c.pitch != c.pitchTarget) {
                    c.pitch = approach(c.pitch, c.pitchTarget, c.pitchStep);
                    AL10.alSourcef(c.source, AL10.AL_PITCH, Math.max(0.05f, c.pitch));
                }
                if (AL10.alGetSourcei(c.source, AL10.AL_SOURCE_STATE) == AL10.AL_STOPPED) release(c);
            }
        } catch (RuntimeException | UnsatisfiedLinkError ignored) {
            // 声音引擎正在重启
        }
    }

    private static float approach(float value, float target, float step) {
        if (step <= 0) return target;
        return value < target ? Math.min(target, value + step) : Math.max(target, value - step);
    }

    private static void release(Channel c) {
        if (c.source < 0) return;
        try {
            if (AL10.alIsSource(c.source)) {
                AL10.alSourceStop(c.source);
                AL10.alDeleteSources(c.source);
            }
        } catch (RuntimeException | UnsatisfiedLinkError ignored) {
            // 同上
        }
        c.source = -1;
    }

    private static int buffer(String clip) throws IOException {
        Integer known = BUFFERS.get(clip);
        if (known != null && AL10.alIsBuffer(known)) return known;
        try (InputStream in = Audio.class.getResourceAsStream("/assets/fleshpark/audio/" + clip + ".wav")) {
            if (in == null) {
                System.err.println("[血肉公园] 没有这段声音：" + clip);
                return -1;
            }
            ByteArrayOutputStream all = new ByteArrayOutputStream();
            in.transferTo(all);
            ByteBuffer wav = ByteBuffer.wrap(all.toByteArray()).order(ByteOrder.LITTLE_ENDIAN);
            int channels = 1, rate = 22050, at = 12;
            ShortBuffer pcm = null;
            while (at + 8 <= wav.limit()) {
                String id = new String(wav.array(), at, 4, java.nio.charset.StandardCharsets.US_ASCII);
                int size = wav.getInt(at + 4);
                if (id.equals("fmt ")) {
                    channels = wav.getShort(at + 10);
                    rate = wav.getInt(at + 12);
                } else if (id.equals("data")) {
                    pcm = BufferUtils.createShortBuffer(size / 2);
                    wav.position(at + 8);
                    for (int i = 0; i < size / 2; i++) pcm.put(wav.getShort());
                    pcm.flip();
                }
                at += 8 + size + (size & 1);
            }
            if (pcm == null) return -1;
            int buffer = AL10.alGenBuffers();
            AL10.alBufferData(buffer, channels == 2 ? AL10.AL_FORMAT_STEREO16 : AL10.AL_FORMAT_MONO16, pcm, rate);
            BUFFERS.put(clip, buffer);
            return buffer;
        }
    }
}
