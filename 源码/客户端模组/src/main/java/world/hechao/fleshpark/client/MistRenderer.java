package world.hechao.fleshpark.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.serialization.MapCodec;
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
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import java.util.function.Consumer;

/**
 * 全景窗上的水汽：整扇窗一张细网格，透明度按顶点算，所以没有格子。
 * 雨刮的转轴在 (PIVOT_X, PIVOT_Y)，扫过的扇形慢慢露出外面。实体放在 (128, 大厅地面, 11.86)。
 */
final class MistRenderer implements NoDataSpecialModelRenderer {
    private static final RenderType TYPE = RenderTypes.entityTranslucent(TextureAtlas.LOCATION_ITEMS);
    private static final Material PUFF = new Material(TextureAtlas.LOCATION_ITEMS, Identifier.fromNamespaceAndPath("fleshpark", "item/mist_puff"));
    private static final float X0 = 110, X1 = 146, HEIGHT = 11.2f, STEP = 0.25f;
    private static final int NX = (int) ((X1 - X0) / STEP), NY = (int) (HEIGHT / STEP);
    private static final float[][] THICK = new float[NX + 1][NY + 1];
    /** 转轴：和 Story 里雨刮的位置一致（ParkPlan.FIGURE_X，地面上 1.1 格）。 */
    private static final float PIVOT_X = 132f, PIVOT_Y = 1.1f, REACH = 9.4f;

    private static float swept = 999;

    static {
        for (int i = 0; i <= NX; i++) {
            for (int j = 0; j <= NY; j++) {
                float wx = X0 + i * STEP, wy = j * STEP;
                float n = fbm(wx * 0.19f, wy * 0.27f) * 0.7f + fbm(wx * 0.71f + 9, wy * 0.71f) * 0.3f;
                float a = 0.13f + 0.25f * smooth(0.3f, 0.7f, n);
                // 四边收掉，不露出一条硬边
                a *= smooth(0f, 0.8f, wy) * smooth(HEIGHT, HEIGHT - 0.8f, wy);
                THICK[i][j] = a;
            }
        }
    }

    private final MaterialSet materials;
    private TextureAtlasSprite sprite;

    private MistRenderer(MaterialSet materials) {
        this.materials = materials;
    }

    static void wipe(String arg) {
        swept = arg.equals("reset") ? 999 : Math.min(swept, Float.parseFloat(arg));
    }

    private static float smooth(float a, float b, float x) {
        float t = Math.max(0, Math.min(1, (x - a) / (b - a)));
        return t * t * (3 - 2 * t);
    }

    private static float value(int x, int y) {
        int h = x * 374761393 + y * 668265263;
        h = (h ^ (h >>> 13)) * 1274126177;
        return ((h ^ (h >>> 16)) & 0xFFFF) / 65535f;
    }

    private static float noise(float x, float y) {
        int ix = (int) Math.floor(x), iy = (int) Math.floor(y);
        float fx = x - ix, fy = y - iy;
        fx = fx * fx * (3 - 2 * fx);
        fy = fy * fy * (3 - 2 * fy);
        float a = value(ix, iy) + (value(ix + 1, iy) - value(ix, iy)) * fx;
        float b = value(ix, iy + 1) + (value(ix + 1, iy + 1) - value(ix, iy + 1)) * fx;
        return a + (b - a) * fy;
    }

    private static float fbm(float x, float y) {
        return noise(x, y) * 0.6f + noise(x * 2.1f + 3, y * 2.1f) * 0.28f + noise(x * 4.3f, y * 4.3f + 7) * 0.12f;
    }

    private static float tri(float v) {
        float t = v - (float) Math.floor(v);
        return t < 0.5f ? t * 2 : 2 - t * 2;
    }

    private static int alpha(int i, int j) {
        float a = THICK[i][j];
        if (swept < 900) {
            float dx = X0 + i * STEP - PIVOT_X, dy = j * STEP - PIVOT_Y;
            float d = (float) Math.sqrt(dx * dx + dy * dy);
            if (d < REACH + 0.5f && dy > -0.2f) {
                float deg = (float) Math.toDegrees(Math.atan2(dy, dx));
                float clear = smooth(swept - 7, swept + 3, deg) * smooth(REACH + 0.5f, REACH - 0.5f, d);
                a *= 1 - clear * 0.97f;
            }
        }
        return (int) (a * 255);
    }

    private static void corner(VertexConsumer out, Matrix4f m, PoseStack.Pose pose, TextureAtlasSprite s, int i, int j, int overlay, int light) {
        float wx = X0 + i * STEP, wy = j * STEP;
        // 物品展示实体转了 180 度：世界的 +x 是模型的 -x
        float x = 0.5f - (wx - 128f), y = 0.5f + wy, z = 0.5f;
        out.addVertex(m.m00() * x + m.m10() * y + m.m20() * z + m.m30(), m.m01() * x + m.m11() * y + m.m21() * z + m.m31(), m.m02() * x + m.m12() * y + m.m22() * z + m.m32(),
                alpha(i, j) << 24 | 0xFFFFFF, s.getU(tri(wx / 7f)), s.getV(tri(wy / 7f)), overlay, light, 0, 0, 1);
    }

    @Override
    public void submit(ItemDisplayContext context, PoseStack stack, SubmitNodeCollector collector, int light, int overlay, boolean glint, int outline) {
        if (sprite == null) sprite = materials.get(PUFF);
        collector.submitCustomGeometry(stack, TYPE, (pose, out) -> {
            Matrix4f m = pose.pose();
            for (int i = 0; i < NX; i++) {
                for (int j = 0; j < NY; j++) {
                    corner(out, m, pose, sprite, i, j, overlay, light);
                    corner(out, m, pose, sprite, i + 1, j, overlay, light);
                    corner(out, m, pose, sprite, i + 1, j + 1, overlay, light);
                    corner(out, m, pose, sprite, i, j + 1, overlay, light);
                }
            }
        });
    }

    @Override
    public void getExtents(Consumer<Vector3fc> output) {
        output.accept(new Vector3f(-18f, 0, 0.5f));
        output.accept(new Vector3f(19f, 12f, 0.5f));
    }

    record Unbaked() implements SpecialModelRenderer.Unbaked {
        static final MapCodec<Unbaked> MAP_CODEC = MapCodec.unit(new Unbaked());

        @Override
        public MapCodec<Unbaked> type() {
            return MAP_CODEC;
        }

        @Override
        public SpecialModelRenderer<?> bake(SpecialModelRenderer.BakingContext context) {
            return new MistRenderer(context.materials());
        }
    }
}
