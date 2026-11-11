package world.hechao.fleshpark.client;

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
 * 走的是同一个输出设备，主音量滑块也管得到（它改的是 OpenAL 的听者音量）。音高能实时改，音乐才能慢下来走调。
 */
final class Audio {
    private static final class Channel {
        int source = -1;
        float gain, gainTarget, gainStep, pitch = 1, pitchTarget = 1, pitchStep;
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
            AL10.alSourcef(c.source, AL10.AL_GAIN, c.gain);
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

    /** 测试用：把现在在放的写进日志。 */
    static void status() {
        StringBuilder b = new StringBuilder("[血肉公园] 声音");
        for (Map.Entry<String, Channel> e : CHANNELS.entrySet()) {
            Channel c = e.getValue();
            int state = c.source < 0 ? 0 : AL10.alGetSourcei(c.source, AL10.AL_SOURCE_STATE);
            b.append(' ').append(e.getKey()).append('=').append(state == AL10.AL_PLAYING ? "在放" : "没放").append('/').append(c.gain).append('/').append(c.pitch);
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
                    AL10.alSourcef(c.source, AL10.AL_GAIN, c.gain);
                    if (c.gain == 0 && c.gainTarget == 0) {
                        release(c);
                        continue;
                    }
                }
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
