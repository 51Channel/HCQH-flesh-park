package world.hechao.fleshpark.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.special.NoDataSpecialModelRenderer;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.MaterialSet;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import java.util.function.Consumer;

/**
 * 一节肉索。实体还是原版的物品展示，只是模型换成真网格：
 * - 近处顶点多，远处少，所以几千节也跑得动；
 * - 光按这一节的两头和中间分别取，不是整节一个亮度，手电的光才会顺着肉索渐变；
 * - 再叠一层高光，手电就在眼睛这里，所以高光跟着视线在湿肉上滑。
 */
final class LumpRenderer implements NoDataSpecialModelRenderer {
    private static final TubeMesh NEAR = new TubeMesh(28, 19, 2), MID = new TubeMesh(13, 9, 1), FAR = new TubeMesh(7, 5, 0);
    private static final RenderType SOLID = RenderTypes.entitySolid(TextureAtlas.LOCATION_ITEMS);
    private static final RenderType GLOSS = RenderTypes.eyes(TextureAtlas.LOCATION_ITEMS);
    private static final Material GLOSS_MATERIAL = new Material(TextureAtlas.LOCATION_ITEMS, Identifier.fromNamespaceAndPath("fleshpark", "item/flesh_gloss"));
    private static final float[] X = new float[NEAR.position.length], N = new float[NEAR.position.length], SPEC = new float[NEAR.position.length / 3];

    private final MaterialSet materials;
    private final Material spriteMaterial;
    private final boolean glows;
    private final RenderType tiled;
    /** 烘模型的时候图集还没好，第一次画的时候才取。 */
    private TextureAtlasSprite sprite, gloss;

    private LumpRenderer(MaterialSet materials, Material spriteMaterial, boolean glows) {
        this.materials = materials;
        this.spriteMaterial = spriteMaterial;
        this.glows = glows;
        Identifier id = spriteMaterial.texture();
        tiled = RenderTypes.entitySolid(id.withPath(path -> "textures/" + path + ".png"));
    }

    @Override
    public void submit(ItemDisplayContext context, PoseStack stack, SubmitNodeCollector collector, int light, int overlay, boolean glint, int outline) {
        if (sprite == null) {
            sprite = materials.get(spriteMaterial);
            gloss = materials.get(GLOSS_MATERIAL);
        }
        Matrix4f m = stack.last().pose();
        float cx = (m.m00() + m.m10() + m.m20()) * 0.5f + m.m30();
        float cy = (m.m01() + m.m11() + m.m21()) * 0.5f + m.m31();
        float cz = (m.m02() + m.m12() + m.m22()) * 0.5f + m.m32();
        float distance = Math.max(0.1f, (float) Math.sqrt(cx * cx + cy * cy + cz * cz));
        float wide = (float) Math.sqrt(m.m10() * m.m10() + m.m11() * m.m11() + m.m12() * m.m12());
        float apparent = wide / distance;
        TubeMesh mesh = apparent > 0.16f ? NEAR : apparent > 0.03f ? MID : FAR;
        int[] lights = {light, light, light};
        if (mesh != FAR && context == ItemDisplayContext.NONE) {
            lights[0] = sample(m, 0.12f, light);
            lights[1] = sample(m, 0.5f, light);
            lights[2] = sample(m, 0.88f, light);
        }
        float along = (float) Math.sqrt(m.m00() * m.m00() + m.m01() * m.m01() + m.m02() * m.m02());
        collector.submitCustomGeometry(stack, mesh == FAR ? SOLID : tiled, (pose, out) -> solid(pose, out, mesh, lights, overlay, along, wide));
        if (!ClientFx.shaderOn && mesh != FAR && (lights[0] | lights[1] | lights[2]) != 0) {
            collector.order(1).submitCustomGeometry(stack, GLOSS, (pose, out) -> gloss(pose, out, mesh, lights));
        }
    }

    /** 肉索中轴上这一点的亮度。这一点要是埋在肉墙里就是全黑，改用实体自己的亮度。血管按心跳发亮，但手电照上去也要亮，取两者大的。 */
    private int sample(Matrix4f m, float t, int fallback) {
        Minecraft client = Minecraft.getInstance();
        ClientLevel level = client.level;
        if (level == null) return fallback;
        Vec3 camera = client.gameRenderer.getMainCamera().position();
        double x = m.m00() * t + (m.m10() + m.m20()) * 0.5 + m.m30() + camera.x;
        double y = m.m01() * t + (m.m11() + m.m21()) * 0.5 + m.m31() + camera.y;
        double z = m.m02() * t + (m.m12() + m.m22()) * 0.5 + m.m32() + camera.z;
        BlockPos pos = BlockPos.containing(x, y, z);
        if (level.getBlockState(pos).isSolidRender()) return fallback;
        int world = LevelRenderer.getLightColor(level, pos);
        return glows ? Math.max(world & 0xFFFF, fallback & 0xFFFF) | Math.max(world >>> 16, fallback >>> 16) << 16 : world;
    }

    private static int ringLight(int[] lights, float t) {
        float k = t < 0.5f ? t * 2 : (t - 0.5f) * 2;
        int a = t < 0.5f ? lights[0] : lights[1], b = t < 0.5f ? lights[1] : lights[2];
        int block = Math.round((a & 0xFFFF) + ((b & 0xFFFF) - (a & 0xFFFF)) * k);
        int sky = Math.round((a >>> 16) + ((b >>> 16) - (a >>> 16)) * k);
        return block | sky << 16;
    }

    private static void transform(PoseStack.Pose pose, TubeMesh mesh) {
        Matrix4f m = pose.pose();
        Matrix3f n = pose.normal();
        float[] p = mesh.position, q = mesh.normal;
        for (int i = 0; i < p.length; i += 3) {
            float x = p[i], y = p[i + 1], z = p[i + 2];
            X[i] = m.m00() * x + m.m10() * y + m.m20() * z + m.m30();
            X[i + 1] = m.m01() * x + m.m11() * y + m.m21() * z + m.m31();
            X[i + 2] = m.m02() * x + m.m12() * y + m.m22() * z + m.m32();
            float a = q[i], b = q[i + 1], c = q[i + 2];
            float nx = n.m00() * a + n.m10() * b + n.m20() * c, ny = n.m01() * a + n.m11() * b + n.m21() * c, nz = n.m02() * a + n.m12() * b + n.m22() * c;
            float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
            N[i] = nx / len;
            N[i + 1] = ny / len;
            N[i + 2] = nz / len;
        }
    }

    private void solid(PoseStack.Pose pose, VertexConsumer out, TubeMesh mesh, int[] lights, int overlay, float along, float wide) {
        transform(pose, mesh);
        // 近处铺成一米多一块的平滑贴图，不管这一节多粗，肉纹大小都差不多，贴到眼前也不是马赛克。绕一圈要是整数块，接缝才对得上
        float tile = Math.max(0.6f, Math.min(2.2f, wide * 1.25f));
        float su = along / tile, sv = Math.max(1, Math.round((float) Math.PI * wide / tile)), cap = wide / tile;
        boolean atlas = mesh == FAR;
        for (int r = 0; r < mesh.rings - 1; r++) {
            int near = ringLight(lights, mesh.along[r]), far = ringLight(lights, mesh.along[r + 1]);
            for (int s = 0; s < mesh.sides; s++) {
                solidVertex(out, mesh, r, s, near, overlay, atlas, su, sv, cap);
                solidVertex(out, mesh, r, s + 1, near, overlay, atlas, su, sv, cap);
                solidVertex(out, mesh, r + 1, s + 1, far, overlay, atlas, su, sv, cap);
                solidVertex(out, mesh, r + 1, s, far, overlay, atlas, su, sv, cap);
            }
        }
    }

    private void solidVertex(VertexConsumer out, TubeMesh mesh, int ring, int side, int light, int overlay, boolean atlas, float su, float sv, float cap) {
        int i = mesh.index(ring, side) * 3, j = i / 3 * 2;
        float u, v;
        if (atlas) {
            u = sprite.getU(mesh.tex[j]);
            v = sprite.getV(mesh.tex[j + 1]);
        } else if (mesh.along[ring] < TubeMesh.TIP || mesh.along[ring] > 1 - TubeMesh.TIP) {
            u = mesh.position[i + 1] * cap;
            v = mesh.position[i + 2] * cap;
        } else {
            u = mesh.along[ring] * su;
            v = (float) side / mesh.sides * sv;
        }
        out.addVertex(X[i], X[i + 1], X[i + 2], Shape.smoothColor(N[i], N[i + 1], N[i + 2], 0xFFFFFFFF), u, v, overlay, light, N[i], N[i + 1], N[i + 2]);
    }

    private void gloss(PoseStack.Pose pose, VertexConsumer out, TubeMesh mesh, int[] lights) {
        transform(pose, mesh);
        for (int r = 0; r < mesh.rings; r++) {
            float block = (ringLight(lights, mesh.along[r]) & 0xFFFF) / 240f;
            float wet = block * block;
            for (int s = 0; s <= mesh.sides; s++) {
                int i = mesh.index(r, s);
                int k = i * 3;
                float x = X[k], y = X[k + 1], z = X[k + 2];
                float len = Math.max(0.05f, (float) Math.sqrt(x * x + y * y + z * z));
                float facing = -(x * N[k] + y * N[k + 1] + z * N[k + 2]) / len;
                if (facing <= 0) {
                    SPEC[i] = 0;
                    continue;
                }
                float f2 = facing * facing, f4 = f2 * f2, f8 = f4 * f4, f16 = f8 * f8;
                // 一层窄的亮点加一层宽的湿润感
                SPEC[i] = wet * (0.55f * f16 * f8 + 0.1f * f4);
            }
        }
        for (int r = 0; r < mesh.rings - 1; r++) {
            for (int s = 0; s < mesh.sides; s++) {
                int a = mesh.index(r, s), b = mesh.index(r, s + 1), c = mesh.index(r + 1, s + 1), d = mesh.index(r + 1, s);
                if (SPEC[a] + SPEC[b] + SPEC[c] + SPEC[d] < 0.01f) continue;
                glossVertex(out, mesh, a);
                glossVertex(out, mesh, b);
                glossVertex(out, mesh, c);
                glossVertex(out, mesh, d);
            }
        }
    }

    /** 这一层是半透明混合，不是加色：强弱放在透明度里，颜色固定是高光色，贴图的透明度再把高光打碎。放在颜色里的话弱的地方会盖一层黑。 */
    private void glossVertex(VertexConsumer out, TubeMesh mesh, int index) {
        int k = index * 3, j = index * 2;
        int alpha = (int) (Math.min(1, SPEC[index]) * 255);
        out.addVertex(X[k], X[k + 1], X[k + 2], alpha << 24 | 0xFFE2CD, gloss.getU(mesh.tex[j]), gloss.getV(mesh.tex[j + 1]), 0, 0xF000F0, N[k], N[k + 1], N[k + 2]);
    }

    @Override
    public void getExtents(Consumer<Vector3fc> output) {
        for (int i = 0; i < 8; i++) output.accept(new Vector3f(i & 1, i >> 1 & 1, i >> 2 & 1));
    }

    record Unbaked(Identifier sprite, boolean glows) implements SpecialModelRenderer.Unbaked {
        static final MapCodec<Unbaked> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                Identifier.CODEC.fieldOf("sprite").forGetter(Unbaked::sprite),
                Codec.BOOL.optionalFieldOf("glows", false).forGetter(Unbaked::glows)
        ).apply(instance, Unbaked::new));

        @Override
        public MapCodec<Unbaked> type() {
            return MAP_CODEC;
        }

        @Override
        public SpecialModelRenderer<?> bake(SpecialModelRenderer.BakingContext context) {
            return new LumpRenderer(context.materials(), new Material(TextureAtlas.LOCATION_ITEMS, sprite), glows);
        }
    }
}
