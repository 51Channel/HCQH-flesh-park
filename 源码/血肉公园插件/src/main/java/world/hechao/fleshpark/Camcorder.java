package world.hechao.fleshpark;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * 搜救二队老周那台 DV：几块方块拼起来，整台一起挪、一起转（插值，客户端看着是顺的），左边的侧屏能翻开、屏幕能亮。
 * 所有方块实体都放在同一个点（base），靠各自的变换摆到位置上：挪整台就是一起改变换，不用传送实体。
 * 机身自己的坐标：原点在机身底面中心，镜头朝 +z，+y 朝上，+x 是拿着它的人的左手边（侧屏在这边）。
 */
final class Camcorder {
    /** 整台放大多少：真 DV 的侧屏太小，举到眼前看不清。下面的尺寸都是放大前的。 */
    static final float S = 1.3f;
    /** 机身宽（x）、高（y）、长（z）。 */
    static final float W = 0.10f, H = 0.115f, L = 0.24f;
    /** 侧屏的合页：机身左侧、中间偏后的竖边。侧屏合上时贴着机身后半截，翻开时朝左伸出去、屏幕朝后对着人；机身大半在屏幕前头，举得近也不会戳到脸。 */
    private static final Vector3f HINGE = new Vector3f(W / 2 + 0.014f, 0.022f, -0.02f);
    /** 侧屏长、高、厚。 */
    static final float PANEL_L = 0.1f, PANEL_H = 0.078f, PANEL_T = 0.014f;

    private record Part(BlockDisplay d, Vector3f corner, Vector3f size, boolean panel) {
    }

    private final List<Part> parts = new ArrayList<>();
    private final Location base;
    private BlockDisplay screen;
    private Vector3f off = new Vector3f();
    private Quaternionf rot = new Quaternionf();
    private float open;

    Camcorder(World world, Location base, Quaternionf rot, List<Entity> scene) {
        this.base = base.clone();
        this.rot = new Quaternionf(rot);
        String body = "minecraft:gray_concrete", dark = "minecraft:black_concrete";
        // 机身、上面的提手、前头的镜头筒和镜片、后头的取景器、右边的手带、背后一颗红灯
        add(world, scene, body, -W / 2, 0, -L / 2, W, H, L, false, -1);
        add(world, scene, dark, -0.028f, H, -0.075f, 0.056f, 0.026f, 0.15f, false, -1);
        add(world, scene, dark, -0.038f, 0.025f, L / 2, 0.076f, 0.076f, 0.055f, false, -1);
        add(world, scene, "minecraft:cyan_terracotta", -0.026f, 0.037f, L / 2 + 0.055f, 0.052f, 0.052f, 0.004f, false, -1);
        add(world, scene, dark, -0.022f, H - 0.035f, -L / 2 - 0.02f, 0.044f, 0.032f, 0.02f, false, -1);
        add(world, scene, dark, -W / 2 - 0.012f, 0.03f, -0.085f, 0.012f, 0.055f, 0.17f, false, -1);
        add(world, scene, "minecraft:white_concrete", -0.03f, H - 0.004f, 0.08f, 0.04f, 0.006f, 0.035f, false, -1);
        // 侧屏：外壳一块，里侧（合上时贴着机身那面）一块屏幕
        add(world, scene, body, 0, 0, 0, PANEL_T, PANEL_H, PANEL_L, true, -1);
        screen = add(world, scene, "minecraft:black_concrete", PANEL_T, 0.008f, 0.008f, 0.002f, PANEL_H - 0.016f, PANEL_L - 0.016f, true, -1);
        pose(new Vector3f(), rot, 0, 0);
    }

    private BlockDisplay add(World world, List<Entity> scene, String block, float x, float y, float z, float sx, float sy, float sz, boolean panel, int glow) {
        BlockDisplay d = world.spawn(base, BlockDisplay.class, e -> {
            e.setPersistent(false);
            e.setBlock(Bukkit.createBlockData(block));
            e.setShadowRadius(0);
            e.setViewRange(2f);
            if (glow >= 0) e.setBrightness(new Display.Brightness(glow, 0));
        });
        scene.add(d);
        parts.add(new Part(d, new Vector3f(x, y, z), new Vector3f(sx, sy, sz), panel));
        return d;
    }

    /** 侧屏翻开多少（0 合上，1 翻开 90 度）时，侧屏相对合页的转角。 */
    private static Quaternionf panelRot(float open) {
        return new Quaternionf().rotationY((float) (Math.PI - open * Math.PI / 2));
    }

    /**
     * 整台摆到 base + off 的位置、朝向 rot，侧屏翻开 open，用 dur 个 tick 过去。
     * 方块的变换是 平移 + 旋转·缩放，所以一块在机身坐标里的角点 c，世界里的平移就是 off + rot·c。
     */
    void pose(Vector3f off, Quaternionf rot, float open, int dur) {
        this.off = new Vector3f(off);
        this.rot = new Quaternionf(rot);
        this.open = open;
        Quaternionf pr = panelRot(open);
        for (Part p : parts) {
            if (!p.d().isValid()) continue;
            Vector3f t;
            Quaternionf r;
            if (p.panel()) {
                t = rot.transform(new Vector3f(HINGE).add(pr.transform(new Vector3f(p.corner()))).mul(S));
                r = new Quaternionf(rot).mul(pr);
            } else {
                t = rot.transform(new Vector3f(p.corner()).mul(S));
                r = new Quaternionf(rot);
            }
            t.add(off);
            p.d().setInterpolationDelay(0);
            p.d().setInterpolationDuration(dur);
            p.d().setTransformation(new Transformation(t, r, new Vector3f(p.size()).mul(S), new Quaternionf()));
        }
    }

    /** 只翻侧屏，位置不动。 */
    void open(float open, int dur) {
        pose(off, rot, open, dur);
    }

    /** 屏幕换颜色：glow 是亮度（-1 跟着环境光）。 */
    void screen(String block, int glow) {
        if (screen == null || !screen.isValid()) return;
        screen.setBlock(Bukkit.createBlockData(block));
        if (glow >= 0) screen.setBrightness(new Display.Brightness(glow, glow));
        else screen.setBrightness(null);
    }

    Location base() {
        return base.clone();
    }

    /** 机身坐标里的一点（比如屏幕中间）现在在世界里的哪。 */
    Vector3f world(Vector3f local) {
        return rot.transform(new Vector3f(local)).add(off).add((float) base.getX(), (float) base.getY(), (float) base.getZ());
    }

    /** 翻开以后屏幕中间在机身坐标里的哪（放大以后）：合页往左伸出去半个屏，往后退半个屏厚。 */
    static Vector3f screenCenter() {
        return new Vector3f(HINGE).add(panelRot(1).transform(new Vector3f(PANEL_T, PANEL_H / 2, PANEL_L / 2))).mul(S);
    }

    void remove() {
        for (Part p : parts) p.d().remove();
        parts.clear();
    }
}
