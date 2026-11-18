package world.hechao.fleshpark.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.serialization.MapCodec;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomModelData;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.function.Consumer;

/**
 * 深井的钢结构和残骸：检修铁楼梯、工字钢梁、楼板、格栅走道、风管、灯箱。网格是插件构建时按落脚的地方生成的（插件的 Rig），放在模组里，
 * 实体只是锚点，编号写在 custom_model_data 里。按锚点处的亮度画；手电是光影里加的。灯箱的面板还有一点电，自己发光、一闪一闪。
 * 双面画（格栅从下面也看得见，弯的扶手两面都对）。
 */
final class SteelRenderer implements SpecialModelRenderer<Integer> {
    /** 和插件 Rig.MATERIALS 一一对应。 */
    private static final String[] NAMES = {"rust", "paint", "tread", "grate", "terrazzo", "concrete", "duct", "sign", "tread_old", "paint_old"};
    private static final int SIGN = 7;
    private static final RenderType[] TYPES = new RenderType[NAMES.length];

    static {
        for (int i = 0; i < NAMES.length; i++) TYPES[i] = RenderTypes.entityCutoutNoCull(Identifier.fromNamespaceAndPath("fleshpark", "textures/steel/" + NAMES[i] + ".png"));
    }

    static final class Piece {
        int id;
        double ox, oy, oz;
        float radius;
        int[] materials;
        /** 每种材质：四边形的顶点 (x, y, z, u, v)，相对原点。 */
        float[][] verts;
        /** 每种材质：每个四边形一条法线（-127..127）。 */
        byte[][] normals;
    }

    private static volatile Piece[] pieces;

    static void loadInBackground() {
        Thread thread = new Thread(SteelRenderer::load, "血肉公园钢结构");
        thread.setDaemon(true);
        thread.start();
    }

    private static void load() {
        try (InputStream raw = SteelRenderer.class.getResourceAsStream("/assets/fleshpark/mesh/steel.bin")) {
            if (raw == null) {
                System.err.println("[血肉公园] 模组里没有深井的钢结构");
                return;
            }
            DataInputStream in = new DataInputStream(new BufferedInputStream(raw, 1 << 18));
            int count = in.readInt();
            Piece[] all = new Piece[count];
            for (int i = 0; i < count; i++) {
                Piece p = new Piece();
                p.id = in.readInt();
                p.ox = in.readDouble();
                p.oy = in.readDouble();
                p.oz = in.readDouble();
                p.radius = in.readFloat();
                int groups = in.readByte();
                p.materials = new int[groups];
                p.verts = new float[groups][];
                p.normals = new byte[groups][];
                for (int g = 0; g < groups; g++) {
                    p.materials[g] = in.readByte();
                    int quads = in.readInt();
                    byte[] floats = new byte[quads * 20 * 4];
                    in.readFully(floats);
                    p.verts[g] = new float[quads * 20];
                    ByteBuffer.wrap(floats).asFloatBuffer().get(p.verts[g]);
                    p.normals[g] = new byte[quads * 3];
                    in.readFully(p.normals[g]);
                }
                all[p.id] = p;
            }
            pieces = all;
            System.out.println("[血肉公园] 深井的钢结构读好了：" + count + " 段");
        } catch (IOException | RuntimeException error) {
            System.err.println("[血肉公园] 读不了深井的钢结构：" + error);
        }
    }

    @Override
    public Integer extractArgument(ItemStack stack) {
        CustomModelData data = stack.get(DataComponents.CUSTOM_MODEL_DATA);
        if (data == null) return null;
        Float id = data.getFloat(0);
        return id == null ? null : id.intValue();
    }

    @Override
    public void submit(Integer id, ItemDisplayContext context, PoseStack stack, SubmitNodeCollector collector, int light, int overlay, boolean glint, int outline) {
        Piece[] all = pieces;
        if (id == null || all == null || id < 0 || id >= all.length || all[id] == null) return;
        Piece p = all[id];
        var camera = net.minecraft.client.Minecraft.getInstance().gameRenderer.getMainCamera();
        var eye = camera.position();
        double dx = p.ox - eye.x, dy = p.oy - eye.y, dz = p.oz - eye.z;
        float distance = (float) Math.sqrt(dx * dx + dy * dy + dz * dz) - p.radius;
        // 雾外面的不画；阴影只有手电用，离远了不画
        if (distance > ParkFog.end() * 1.3f) return;
        if (ClientFx.shadowPass() && distance > 14) return;
        // 灯箱：大多时候亮着，隔一阵灭一下、闪两下
        long t = ClientFx.ticks + p.id * 37L;
        boolean signOn = (t % 97) > 6 && (t % 211) != 3 && (t % 211) != 5;
        int glow = signOn ? 0xF000F0 : light;
        // 物品展示实体自带 180 度旋转。网格是按世界方向烘的，要转回来（和肉的网格一样）
        stack.pushPose();
        stack.translate(0.5f, 0, 0.5f);
        stack.mulPose(com.mojang.math.Axis.YP.rotation((float) Math.PI));
        stack.translate(-0.5f, 0, -0.5f);
        for (int g = 0; g < p.materials.length; g++) {
            int mat = p.materials[g];
            float[] v = p.verts[g];
            byte[] nr = p.normals[g];
            int l = mat == SIGN ? glow : light;
            collector.submitCustomGeometry(stack, TYPES[mat], (pose, out) -> quads(pose, out, v, nr, l, overlay));
        }
        stack.popPose();
    }

    private static void quads(PoseStack.Pose pose, VertexConsumer out, float[] v, byte[] nr, int light, int overlay) {
        Matrix4f m = pose.pose();
        Matrix3f n = pose.normal();
        int quads = nr.length / 3;
        for (int q = 0; q < quads; q++) {
            float a = nr[q * 3] / 127f, b = nr[q * 3 + 1] / 127f, c = nr[q * 3 + 2] / 127f;
            float nx = n.m00() * a + n.m10() * b + n.m20() * c, ny = n.m01() * a + n.m11() * b + n.m21() * c, nz = n.m02() * a + n.m12() * b + n.m22() * c;
            for (int k = 0; k < 4; k++) {
                int i = (q * 4 + k) * 5;
                float x = v[i] + 0.5f, y = v[i + 1] + 0.5f, z = v[i + 2] + 0.5f;
                out.addVertex(m.m00() * x + m.m10() * y + m.m20() * z + m.m30(), m.m01() * x + m.m11() * y + m.m21() * z + m.m31(), m.m02() * x + m.m12() * y + m.m22() * z + m.m32(),
                        0xFFFFFFFF, v[i + 3], v[i + 4], overlay, light, nx, ny, nz);
            }
        }
    }

    @Override
    public void getExtents(Consumer<Vector3fc> output) {
        for (int i = 0; i < 8; i++) output.accept(new Vector3f(i & 1, i >> 1 & 1, i >> 2 & 1));
    }

    record Unbaked() implements SpecialModelRenderer.Unbaked {
        static final MapCodec<Unbaked> MAP_CODEC = MapCodec.unit(new Unbaked());

        @Override
        public MapCodec<Unbaked> type() {
            return MAP_CODEC;
        }

        @Override
        public SpecialModelRenderer<?> bake(SpecialModelRenderer.BakingContext context) {
            return new SteelRenderer();
        }
    }
}
