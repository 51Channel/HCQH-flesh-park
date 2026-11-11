package world.hechao.fleshpark.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.special.NoDataSpecialModelRenderer;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.MaterialSet;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemDisplayContext;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * 人形：息壤照着人长出来的仿品，造型见 {@link Figure}。每一个都不一样：按物品定义里的 pose（身份.编号.姿势）雕。
 * 朝 +z，脚底在模型 y=-0.5，实体放在脚底上方 1 格。
 * 雕一个人形要几十毫秒，放在后台线程里雕，雕好之前先画粗一档的（还没有就先不画）。
 */
final class FigureRenderer implements NoDataSpecialModelRenderer {
    private static final RenderType SOLID = RenderTypes.entitySolid(TextureAtlas.LOCATION_ITEMS);

    static {
        // 物品图集这一种渲染类型人形、手、缆车、管子都在用，一起走原版格式
        LeanVertices.add(SOLID);
    }

    private static final Material SKIN = new Material(TextureAtlas.LOCATION_ITEMS, Identifier.fromNamespaceAndPath("fleshpark", "item/grey_skin"));
    /** 衣服：浅色的布纹，乘上颜色才是原来的颜色。皮肤那张太暗，衣服乘上去全是黑的。 */
    private static final Material CLOTH = new Material(TextureAtlas.LOCATION_ITEMS, Identifier.fromNamespaceAndPath("fleshpark", "item/cloth"));
    private static final Material BADGE = new Material(TextureAtlas.LOCATION_ITEMS, Identifier.fromNamespaceAndPath("fleshpark", "item/poster_badge"));
    /** 你：工牌上是你自己的号。 */
    private static final Material BADGE_SELF = new Material(TextureAtlas.LOCATION_ITEMS, Identifier.fromNamespaceAndPath("fleshpark", "item/poster_badge_self"));
    /** 前十个体验官的工牌：号码糊掉了。 */
    private static final Material BADGE_STAFF = new Material(TextureAtlas.LOCATION_ITEMS, Identifier.fromNamespaceAndPath("fleshpark", "item/poster_badge_staff"));
    private static final int CORD = 0xFFD74735, CORD_PARK = 0xFF2C7A74, VOID = 0xFF000000, BRIGHT = 0xF000F0;

    /** 离得远就换粗一档的雕刻。温泉里泡着一排三十七个，每个都按最细的画，帧数掉到个位数。 */
    private static final double[] STEPS = {0.016, 0.032, 0.064};
    // 十米外一个人形在 1440p 上不到两百像素高，0.064 一档每层还不到七个像素，看不出棱角
    private static final float[] NEAR = {4f, 10f}, NEAR_OLD = {5f, 14f};
    /** 测帧数用（fx figlod off）：换回原来的远近分档，眼窝和嘴不跟着变粗。 */
    static boolean coarse = true;
    /** 后台雕刻：两条线程，雕好了下一帧就画。 */
    private static final ExecutorService CARVER = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "fleshpark-figure");
        t.setDaemon(true);
        t.setPriority(Thread.MIN_PRIORITY);
        return t;
    });

    /** 雕好的一档：每个部位一块、眼窝和嘴里的黑、挂绳和工牌。 */
    private record Built(Shape[] slots, Shape dark, Shape cord, Shape card, int quads) {
    }

    private final MaterialSet materials;
    private final String pose;
    private final java.util.concurrent.atomic.AtomicReferenceArray<Built> built = new java.util.concurrent.atomic.AtomicReferenceArray<>(STEPS.length);
    private final boolean[] asked = new boolean[STEPS.length];
    private Figure figure;
    private TextureAtlasSprite skin, cloth, badge;

    private FigureRenderer(MaterialSet materials, String pose) {
        this.materials = materials;
        this.pose = pose;
    }

    private Built build(int lod) {
        Figure f = Figure.of(pose);
        double step = STEPS[lod];
        Shape[] slots = new Shape[Figure.SLOTS];
        for (int i = 0; i < slots.length; i++) slots[i] = new Shape();
        double[] b = f.s.bounds(step * 2);
        f.s.intoSlots(slots, b[0], b[1], b[2], b[3], b[4], b[5], step, 0.5, -0.5, 0.5);
        // 眼窝和嘴里面是黑的：塞进去几块不反光的黑。细分跟着身子的精度走
        int fine = lod + 1;
        Shape dark = new Shape();
        for (double[] h : f.holes) {
            double[] a = f.s.place(h[0], h[1], h[2]), c = f.s.place(h[3], h[4], h[5]);
            dark.capsule(0.5 + a[0], a[1] - 0.5, 0.5 + a[2], h[6], 0.5 + c[0], c[1] - 0.5 + 0.0005, 0.5 + c[2], h[6], 24 / fine, 18 / fine);
        }
        Shape cord = new Shape(), card = new Shape();
        if (f.badge != null) badge(f, cord, card);
        int quads = dark.quads() + cord.quads() + card.quads();
        for (Shape s : slots) quads += s.quads();
        return new Built(slots, dark, cord, card, quads);
    }

    /** 挂绳从脖子两边垂到胸前的牌子上；穿背心的，绳子绕过背心的领口，牌子挂在背心外面。 */
    private static void badge(Figure f, Shape cord, Shape card) {
        for (double side : new double[]{-1, 1}) {
            double[] top = f.s.place(side * 0.045, f.neckY + 0.04, f.neckZ), bottom = f.s.place(side * 0.03, f.cardY + 0.075, f.cardZ);
            if (f.vested) {
                double[] mid = f.s.place(side * 0.04, f.cardY + 0.17, f.cardZ - 0.005);
                cord.capsule(0.5 + top[0], top[1] - 0.5, 0.5 + top[2], 0.005, 0.5 + mid[0], mid[1] - 0.5, 0.5 + mid[2], 0.005, 6, 4);
                top = mid;
            }
            cord.capsule(0.5 + top[0], top[1] - 0.5, 0.5 + top[2], 0.005, 0.5 + bottom[0], bottom[1] - 0.5, 0.5 + bottom[2], 0.005, 6, 4);
        }
        double[] c = f.s.place(0, f.cardY, f.cardZ + 0.004);
        double w = 0.045, h = 0.065;
        card.surface((u, v, out) -> {
            out[0] = 0.5 + c[0] + (u - 0.5) * 2 * w;
            out[1] = c[1] - 0.5 + (0.5 - v) * 2 * h;
            out[2] = 0.5 + c[2];
        }, 1, 1, 0.5 + c[0], c[1] - 0.5, 0.5 + c[2] - 1);
    }

    /** 要这一档：没雕就交给后台。 */
    private Built want(int lod) {
        Built b = built.get(lod);
        if (b == null && !asked[lod]) {
            asked[lod] = true;
            CARVER.execute(() -> {
                try {
                    built.set(lod, build(lod));
                } catch (RuntimeException e) {
                    org.slf4j.LoggerFactory.getLogger("fleshpark").warn("人形雕不出来：" + pose, e);
                }
            });
        }
        return b;
    }

    @Override
    public void submit(ItemDisplayContext context, PoseStack stack, SubmitNodeCollector collector, int light, int overlay, boolean glint, int outline) {
        // 位姿里的平移就是相对镜头的位置，转没转都不影响长度
        org.joml.Matrix4f at = stack.last().pose();
        float distance = (float) Math.sqrt(at.m30() * at.m30() + at.m31() * at.m31() + at.m32() * at.m32());
        // 手电的影子只有贴近了才看得出来
        if (ClientFx.shadowPass() && distance > 8) return;
        float[] near = coarse ? NEAR : NEAR_OLD;
        int lod = distance < near[0] ? 0 : distance < near[1] ? 1 : 2;
        // 先保证最粗的一档在雕：哪一档都还没有的时候，至少很快就有个样子
        want(2);
        Built body = want(lod);
        for (int l = lod + 1; body == null && l < STEPS.length; l++) body = built.get(l);
        for (int l = lod - 1; body == null && l >= 0; l--) body = built.get(l);
        if (body == null) return;
        if (figure == null) figure = Figure.of(pose);
        if (skin == null) {
            skin = materials.get(SKIN);
            cloth = materials.get(CLOTH);
            String b = figure.badge;
            badge = materials.get("self".equals(b) ? BADGE_SELF : "staff".equals(b) ? BADGE_STAFF : BADGE);
        }
        Figure f = figure;
        Built draw = body;
        // 物品展示实体自带 180 度旋转：转回来，脸才朝着实体的朝向
        stack.pushPose();
        stack.translate(0.5f, 0, 0.5f);
        stack.mulPose(com.mojang.math.Axis.YP.rotation((float) Math.PI));
        // 高矮胖瘦：脚底（模型 y=-0.5）不动
        stack.translate(0, -0.5f, 0);
        stack.scale(f.scale * f.width, f.scale, f.scale * f.width);
        stack.translate(-0.5f, 0.5f, -0.5f);
        collector.submitCustomGeometry(stack, SOLID, (p, out) -> {
            MeshRenderer.figureQuads += draw.quads();
            draw.slots()[Figure.SKIN].emit(p, out, skin, Figure.SKIN_TINT, light, overlay);
            // 荧光橙、头灯在暗处也认得出来
            int lit = Math.max(light & 0xFFFF, 7 << 4) | (light & 0xFFFF0000);
            for (int i = 1; i < Figure.SLOTS; i++) {
                Shape s = draw.slots()[i];
                if (s.quads() == 0) continue;
                int l = i == Figure.LAMP || i == Figure.STRIP ? BRIGHT : i == Figure.VEST ? lit : light;
                s.emit(p, out, cloth, f.color[i], l, overlay);
            }
            if (f.badge != null) {
                draw.cord().emit(p, out, skin, "park".equals(f.badge) ? CORD_PARK : CORD, light, overlay);
                draw.card().emit(p, out, badge, 0xFFFFFFFF, light, overlay);
            }
            draw.dark().emit(p, out, skin, VOID, 0, overlay);
        });
        stack.popPose();
    }

    @Override
    public void getExtents(Consumer<Vector3fc> output) {
        for (int i = 0; i < 8; i++) output.accept(new Vector3f((i & 1) * 1.4f - 0.2f, (i >> 1 & 1) * 3f - .5f, (i >> 2 & 1) * 1.8f - 0.4f));
    }

    record Unbaked(String pose) implements SpecialModelRenderer.Unbaked {
        static final MapCodec<Unbaked> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                Codec.STRING.optionalFieldOf("pose", "stand").forGetter(Unbaked::pose)
        ).apply(instance, Unbaked::new));

        @Override
        public MapCodec<Unbaked> type() {
            return MAP_CODEC;
        }

        @Override
        public SpecialModelRenderer<?> bake(SpecialModelRenderer.BakingContext context) {
            return new FigureRenderer(context.materials(), pose);
        }
    }
}
