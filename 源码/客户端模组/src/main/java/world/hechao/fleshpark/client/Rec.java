package world.hechao.fleshpark.client;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Util;
import org.lwjgl.stb.STBImageWrite;
import org.lwjgl.system.MemoryUtil;

import java.io.PrintWriter;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 录素材：每帧把画面抓下来存成 JPEG（游戏目录下 fleshpark-rec/片段名/），旁边记每帧的时刻，之后拼成视频。
 * 只给剪辑挑镜头用，正式录还是在 Windows 上开录屏。录的时候 clean：不画准星、录像带框、头顶标签、深度计、手机、字幕和聊天栏。
 * 默认原画、每秒最多 60 帧（游戏画多快就抓多快）；rec start 名字 half 缩一半存，省盘。
 */
final class Rec {
    private static final int MAX_QUEUE = 150;
    private static int fps = 60;
    private static boolean half;
    private static final ExecutorService POOL = Executors.newFixedThreadPool(6, r -> {
        Thread t = new Thread(r, "fleshpark-rec");
        t.setDaemon(true);
        return t;
    });
    private static final AtomicInteger QUEUED = new AtomicInteger();
    static boolean clean;
    private static boolean recording, pending;
    private static Path clip;
    private static PrintWriter index;
    private static long started, stamp;
    private static int taken, dropped;

    private Rec() {
    }

    /** rec start 名字 [half] | rec stop | rec clean on|off | rec fps 帧数 | rec fullscreen on|off */
    static void command(String[] a) {
        var log = org.slf4j.LoggerFactory.getLogger("fleshpark");
        switch (a.length > 1 ? a[1] : "") {
            case "start" -> {
                try {
                    clip = Minecraft.getInstance().gameDirectory.toPath().resolve("fleshpark-rec").resolve(a[2]);
                    Files.createDirectories(clip);
                    index = new PrintWriter(Files.newBufferedWriter(clip.resolve("frames.tsv")), true);
                    // 第一行记开录时的墙钟毫秒：和另外录的游戏声音对齐用
                    index.println("#epoch\t" + System.currentTimeMillis());
                } catch (Exception e) {
                    log.warn("录不了", e);
                    return;
                }
                half = a.length > 3 && a[3].equals("half");
                taken = dropped = 0;
                started = Util.getMillis();
                recording = true;
                pending = false;
                clean = true;
                Minecraft.getInstance().getToastManager().clear();
                log.info("开录 " + a[2]);
            }
            case "stop" -> {
                if (!recording) return;
                recording = pending = false;
                index.close();
                log.info("停录 帧 " + taken + " 时长 " + (Util.getMillis() - started) + "ms 丢 " + dropped);
            }
            case "fps" -> fps = Integer.parseInt(a[2]);
            // 录成片时切全屏：窗口在 Mac 上拉不到 1080 高
            case "fullscreen" -> {
                var window = Minecraft.getInstance().getWindow();
                if (window.isFullscreen() != a[2].equals("on")) window.toggleFullScreen();
            }
            case "clean" -> clean = a.length < 3 || !a[2].equals("off");
            default -> {
            }
        }
    }

    /** 每帧画 HUD 的时候排一下，真正抓在下一帧开头：那时候上一帧已经整张画完了。 */
    static void frame(Minecraft mc) {
        if (!recording) return;
        mc.schedule(() -> {
            long now = Util.getMillis();
            if (pending) {
                pending = false;
                grab(mc, taken++, stamp);
            }
            if (recording && now - started >= taken * 1000L / fps) {
                pending = true;
                stamp = now;
            }
        });
    }

    private static void grab(Minecraft mc, int n, long at) {
        if (QUEUED.get() > MAX_QUEUE) {
            dropped++;
            return;
        }
        RenderTarget target = mc.getMainRenderTarget();
        GpuTexture texture = target.getColorTexture();
        int w = target.width, h = target.height, size = w * h * 4;
        GpuDevice device = RenderSystem.getDevice();
        GpuBuffer buffer = device.createBuffer(() -> "fleshpark rec", 9, size);
        CommandEncoder encoder = device.createCommandEncoder();
        String file = clip.resolve(String.format(Locale.ROOT, "f%06d.jpg", n)).toString();
        index.println(n + "\t" + (at - started));
        QUEUED.incrementAndGet();
        encoder.copyTextureToBuffer(texture, buffer, 0L, () -> {
            ByteBuffer copy = MemoryUtil.memAlloc(size);
            try (GpuBuffer.MappedView view = encoder.mapBuffer(buffer, true, false)) {
                MemoryUtil.memCopy(view.data(), copy);
            }
            buffer.close();
            POOL.execute(() -> {
                try {
                    int ow = half ? w / 2 : w, oh = half ? h / 2 : h;
                    ByteBuffer out = half ? MemoryUtil.memAlloc(ow * oh * 4) : copy;
                    try {
                        // 存的时候上下翻过来（显卡的行是从下往上的）；要缩就 2×2 取平均
                        if (half) {
                            for (int y = 0; y < oh; y++) {
                                int sy = h - 2 - y * 2;
                                for (int x = 0; x < ow; x++) {
                                    int i0 = (sy * w + x * 2) * 4, i1 = ((sy + 1) * w + x * 2) * 4, o = (y * ow + x) * 4;
                                    for (int c = 0; c < 4; c++) {
                                        int s = (copy.get(i0 + c) & 255) + (copy.get(i0 + 4 + c) & 255) + (copy.get(i1 + c) & 255) + (copy.get(i1 + 4 + c) & 255);
                                        out.put(o + c, (byte) (s >> 2));
                                    }
                                }
                            }
                        } else {
                            long start = MemoryUtil.memAddress(copy), stride = w * 4L, row = MemoryUtil.nmemAlloc(stride);
                            for (int y = 0; y < h / 2; y++) {
                                long a = start + y * stride, b = start + (h - 1 - y) * stride;
                                MemoryUtil.memCopy(a, row, stride);
                                MemoryUtil.memCopy(b, a, stride);
                                MemoryUtil.memCopy(row, b, stride);
                            }
                            MemoryUtil.nmemFree(row);
                        }
                        STBImageWrite.stbi_write_jpg(file, ow, oh, 4, out, 93);
                    } finally {
                        if (half) MemoryUtil.memFree(out);
                    }
                } finally {
                    MemoryUtil.memFree(copy);
                    QUEUED.decrementAndGet();
                }
            });
        }, 0);
    }
}
