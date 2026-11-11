package world.hechao.fleshpark.client;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

/**
 * 一路上截下来的画面：每进一关、照镜子、进中控室、最后按评分机的那一下，各截一张。
 * 就是屏幕上的样子，连录像带的角标和字幕一起。结尾车上的电视拿它们当宣传片放。
 * 只存在内存里，不写到截图文件夹；裁成电视的 4:3，缩到四分之一。
 */
public final class Snaps {
    static final int COUNT = 8;
    private static final Identifier[] IDS = new Identifier[COUNT];
    private static final boolean[] TAKEN = new boolean[COUNT];

    static {
        for (int i = 0; i < COUNT; i++) IDS[i] = Identifier.fromNamespaceAndPath("fleshpark", "snap/" + i);
    }

    private Snaps() {
    }

    static void take(int i) {
        if (i < 0 || i >= COUNT) return;
        Minecraft mc = Minecraft.getInstance();
        Screenshot.takeScreenshot(mc.getMainRenderTarget(), 4, image -> mc.execute(() -> store(i, image)));
    }

    private static int photoPending;
    private static net.minecraft.world.entity.Entity photoBack;

    /**
     * 缆车拍照点的纪念照：从车窗外那个机位（插件放的一个看不见的盔甲架）看进来，拍你坐在车里，身后的肉壁上那一排灰脸。
     * 换到那个机位画一帧，世界画完就截，再换回来；这一帧屏幕上是闪光灯的白，看不出镜头跳过。找不到机位就截眼前这一帧。
     */
    static void photo(int entityId) {
        Minecraft mc = Minecraft.getInstance();
        net.minecraft.world.entity.Entity cam = mc.level == null ? null : mc.level.getEntity(entityId);
        if (cam == null) {
            take(0);
            return;
        }
        photoBack = mc.getCameraEntity();
        mc.setCameraEntity(cam);
        photoPending = 1;
    }

    /** 每一帧世界画完调一次（PhotoMixin）。 */
    public static void afterWorld() {
        if (photoPending <= 0 || --photoPending > 0) return;
        Minecraft mc = Minecraft.getInstance();
        take(0);
        mc.setCameraEntity(photoBack != null ? photoBack : mc.player);
        photoBack = null;
    }

    private static void store(int i, NativeImage image) {
        int w = image.getWidth(), h = image.getHeight();
        int cw = Math.min(w, h * 4 / 3), ch = Math.min(h, w * 3 / 4);
        NativeImage tv = new NativeImage(cw, ch, false);
        image.copyRect(tv, (w - cw) / 2, (h - ch) / 2, 0, 0, cw, ch, false, false);
        image.close();
        Minecraft mc = Minecraft.getInstance();
        if (TAKEN[i]) mc.getTextureManager().release(IDS[i]);
        mc.getTextureManager().register(IDS[i], new DynamicTexture(() -> "fleshpark snap " + i, tv));
        TAKEN[i] = true;
    }

    /** 截过就给贴图，没截到给 null。 */
    static Identifier get(int i) {
        return i >= 0 && i < COUNT && TAKEN[i] ? IDS[i] : null;
    }

    static void reset() {
        Minecraft mc = Minecraft.getInstance();
        for (int i = 0; i < COUNT; i++) {
            if (TAKEN[i]) mc.getTextureManager().release(IDS[i]);
            TAKEN[i] = false;
        }
    }
}
