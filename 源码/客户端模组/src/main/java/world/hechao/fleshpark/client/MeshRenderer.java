package world.hechao.fleshpark.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.serialization.MapCodec;
import net.minecraft.client.Minecraft;
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

import java.util.function.Consumer;

/**
 * 一段肉的网格。实体只是个锚点，编号写在物品的 custom_model_data 里。
 * 肉索离得远就隔圈隔边地画。开着光影时顶点色另有用处：相位、幅度、种类，光影按它们让肉蠕动。
 */
final class MeshRenderer implements SpecialModelRenderer<Integer> {
    private static final String[] NAMES = {"wall", "rope", "dark", "pale", "vein"};
    private static final RenderType[] TYPES = new RenderType[NAMES.length];

    static {
        for (int i = 0; i < NAMES.length; i++) {
            TYPES[i] = RenderTypes.entitySolid(Identifier.fromNamespaceAndPath("fleshpark", "textures/flesh/" + NAMES[i] + ".png"));
            LeanVertices.add(TYPES[i]);
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
        if (id == null) return;
        MeshStore.Piece p = MeshStore.get(id);
        if (p == null) return;
        boolean shadowPass = ClientFx.shadowPass();
        // 两趟用同一套精度：阴影图里的肉比画面里粗，就会自己给自己投出一格一格的影子
        var camera = net.minecraft.client.Minecraft.getInstance().gameRenderer.getMainCamera();
        var eye = camera.position();
        double dx = p.ox - eye.x, dy = p.oy - eye.y, dz = p.oz - eye.z;
        float distance = Math.max(0.1f, (float) Math.sqrt(dx * dx + dy * dy + dz * dz) - p.reach());
        if (hidden(p, distance)) return;
        if (shadowPass) {
            // 阴影只有手电用。光从手上出来，锥外面的肉挡不到锥里的东西，投了也看不见，这一趟是最费的
            var forward = camera.forwardVector();
            float reach = p.reach();
            double along = dx * forward.x() + dy * forward.y() + dz * forward.z();
            if (distance > (p.sides == 0 ? 12 : 14) || along < -reach - 2) return;
            double side = Math.sqrt(Math.max(0, dx * dx + dy * dy + dz * dz - along * along));
            // 投影的半宽是 0.8，算上对角线 1.15；手和镜头差一点，再宽一米
            if (side - reach > Math.max(along, 0) * 1.15 + 1) return;
        } else {
            MeshStore.drawn(p, ClientFx.ticks, light);
        }
        int stride = 1;
        if (p.sides > 0) {
            // 按一条边在画面上占多大来定：粗肉索 48 条边，离远了每条边不到一个像素，全是白算。最少留 8 条边，轮廓不至于变方
            float edge = 6.283f * p.radius / p.sides / distance;
            stride = edge > 0.012f ? 1 : edge > 0.006f ? 2 : edge > 0.003f ? 4 : 8;
            stride = Math.min(stride, Math.max(1, p.sides / 8));
        }
        int step = stride;
        // 一大块肉壁常常只有一角在画面里：逐个面再按视锥剔一遍。阴影那一趟按手电的光锥
        float cone = !quadCull ? -1 : shadowPass ? BEAM : viewCone();
        if (cone > 0) {
            var f = camera.forwardVector();
            fx = f.x();
            fy = f.y();
            fz = f.z();
        }
        // 物品展示实体自带 180 度旋转。网格是按世界方向烘的，要转回来，不然每一块都绕自己的中心转反了
        stack.pushPose();
        stack.translate(0.5f, 0, 0.5f);
        stack.mulPose(com.mojang.math.Axis.YP.rotation((float) Math.PI));
        stack.translate(-0.5f, 0, -0.5f);
        collector.submitCustomGeometry(stack, TYPES[p.texture], (pose, out) -> {
            int quads = p.sides == 0 ? walls(pose, out, p, overlay, !shadowPass, (float) -dx, (float) -dy, (float) -dz, cone) : tube(pose, out, p, step, overlay);
            if (shadowPass) shadowQuads += quads;
            else mainQuads += quads;
        });
        stack.popPose();
    }

    /** 画面里根本看不见的肉不画：每一帧每个顶点都要现算，这是最贵的地方。 */
    private static boolean hidden(MeshStore.Piece p, float distance) {
        // 光影在雾距 1.25 倍以外整个换成雾色，再远的画了也白画
        if (distance > ParkFog.end() * 1.3f) return true;
        if ((ClientFx.chambers >> p.group & 1) == 0) return true;
        if (p.group != 0) return false;
        float reach = p.reach();
        return switch (ClientFx.storey) {
            // 大厅脚底下是花园和温泉。只认楼下那一片，窗外往下看的肉还要画
            case 1 -> p.oy + reach < 27 && Math.abs(p.oz) < 20 && p.ox > 110 && p.ox < 240;
            case 2 -> p.oy - reach > 29.5;
            case 3 -> p.ox + reach < 240;
            // 深井和井底：只画井里这一片的肉（真楼观景窗外面那些）
            case 4 -> p.ox + reach < 455 || p.ox - reach > 565;
            default -> false;
        };
    }

    /** 测帧数时可以关掉（fx quadcull off），比一比。 */
    static boolean quadCull = true;
    /** 阴影图从手电看出去，半宽是距离的 0.8 倍，算上对角线 1.15 倍；手和镜头差一点，留在 MARGIN 里。 */
    private static final float BEAM = 1.15f;
    /** 肉壁的格子是 0.85 米，一个面的四个角最远差将近两米；按第一个角算，四边都让出这么多。 */
    private static final float MARGIN = 2f;
    private static float fx, fy, fz;

    /** 视锥的外接圆锥：半张角的正切。视野设置是竖着的张角，按窗口宽高比折到对角线；跑步、药水还会把视野拉宽，多留两成。 */
    private static float viewCone() {
        var mc = net.minecraft.client.Minecraft.getInstance();
        double half = Math.min(Math.toRadians(mc.options.fov().get()) * 0.5 * 1.2, 1.45);
        double aspect = (double) mc.getWindow().getWidth() / Math.max(1, mc.getWindow().getHeight());
        return (float) (Math.tan(half) * Math.sqrt(1 + aspect * aspect));
    }

    /** 测帧数用：一共发了多少个面（fx meshstat 打到日志里）。 */
    static long mainQuads, shadowQuads, figureQuads;

    /** ex、ey、ez 是镜头相对这一段原点的位置。返回发了几个面。 */
    /** cone 大于 0 时，镜头朝向那个圆锥（半张角正切是 cone）外面的面不发。 */
    private static int walls(PoseStack.Pose pose, VertexConsumer out, MeshStore.Piece p, int overlay, boolean cull, float ex, float ey, float ez, float cone) {
        Matrix4f m = pose.pose();
        Matrix3f n = pose.normal();
        boolean deform = ClientFx.squeeze > 0.002f;
        boolean shader = ClientFx.shaderOn;
        int quads = p.pos.length / 12, sent = 0;
        float[] pos = p.pos;
        byte[] nr = p.normal;
        // 肉被挤压的时候顶点会挪好几格，原来在视野外的也可能被推进来
        if (deform) cone = -1;
        float ax = fx, ay = fy, az = fz;
        for (int q = 0; q < quads; q++) {
            if (cone > 0) {
                int v = q * 12;
                float vx = pos[v] - ex, vy = pos[v + 1] - ey, vz = pos[v + 2] - ez;
                float along = vx * ax + vy * ay + vz * az;
                if (along < -MARGIN) continue;
                float reach = Math.max(along, 0) * cone + MARGIN;
                if (vx * vx + vy * vy + vz * vz - along * along > reach * reach) continue;
            }
            if (cull) {
                // 明显背对镜头的面不发。法线是平滑过的，不到一定角度不算，免得轮廓边上缺一块
                int v = q * 12;
                float vx = pos[v] - ex, vy = pos[v + 1] - ey, vz = pos[v + 2] - ez;
                int nx = nr[v] + nr[v + 3] + nr[v + 6] + nr[v + 9], ny = nr[v + 1] + nr[v + 4] + nr[v + 7] + nr[v + 10], nz = nr[v + 2] + nr[v + 5] + nr[v + 8] + nr[v + 11];
                float d = vx * nx + vy * ny + vz * nz;
                if (d > 0 && d * d > 0.16f * (nx * nx + ny * ny + nz * nz) * (vx * vx + vy * vy + vz * vz)) continue;
            }
            int light = ember(p.light[q]);
            for (int k = 0; k < 4; k++) vertex(out, m, n, p, q * 4 + k, light, overlay, deform, shader);
            sent++;
        }
        return sent;
    }

    private static int tube(PoseStack.Pose pose, VertexConsumer out, MeshStore.Piece p, int stride, int overlay) {
        Matrix4f m = pose.pose();
        Matrix3f n = pose.normal();
        boolean deform = ClientFx.squeeze > 0.002f || (p.window && ClientFx.heave > 0.002f);
        boolean shader = ClientFx.shaderOn;
        int columns = p.sides + 1, sent = 0;
        for (int r = 0; r < p.rings - 1; r += stride) {
            int next = Math.min(p.rings - 1, r + stride);
            int a = glow(p, p.light[r]), b = glow(p, p.light[next]);
            for (int s = 0; s < p.sides; s += stride) {
                int s1 = Math.min(p.sides, s + stride);
                // 沿线 × 绕圈 指向管子里面，所以要反过来走，面才朝外
                vertex(out, m, n, p, r * columns + s, a, overlay, deform, shader);
                vertex(out, m, n, p, r * columns + s1, a, overlay, deform, shader);
                vertex(out, m, n, p, next * columns + s1, b, overlay, deform, shader);
                vertex(out, m, n, p, next * columns + s, b, overlay, deform, shader);
                sent++;
            }
        }
        return sent;
    }

    /** 大空腔太远，手电和灯都够不着。肉壁跟着心跳透出一点光，才看得出它有多大。 */
    private static int ember(int light) {
        if (ClientFx.ember <= 0) return light;
        return Math.max(light & 0xFFFF, (int) ((ClientFx.pulse * 72 + 44) * ClientFx.ember)) | light & 0xFFFF0000;
    }

    /** 血管跟着心跳发亮。 */
    private static int glow(MeshStore.Piece p, int light) {
        if (p.texture != 4) return ember(light);
        int pulse = (int) (ClientFx.pulse * 200) + 20;
        return Math.max(light & 0xFFFF, pulse) | light & 0xFFFF0000;
    }

    private static void vertex(VertexConsumer out, Matrix4f m, Matrix3f n, MeshStore.Piece p, int v, int light, int overlay, boolean deform, boolean shader) {
        float x = p.pos[v * 3], y = p.pos[v * 3 + 1], z = p.pos[v * 3 + 2];
        if (deform) {
            double wx = p.ox + x, wy = p.oy + y, wz = p.oz + z;
            if (ClientFx.squeeze > 0.002f) {
                double cx = ClientFx.squeezeX - wx, cy = ClientFx.squeezeY - wy, cz = ClientFx.squeezeZ - wz;
                cx -= Math.max(-2.6, Math.min(2.6, cx));
                double dc = Math.sqrt(cx * cx + cy * cy + cz * cz);
                if (dc < 15 && dc > 0.01) {
                    double k = Math.max(0, Math.min(1, (15 - dc) / 8));
                    double push = Math.max(0, dc - 2.3) * ClientFx.squeeze * k * k * (3 - 2 * k) / dc;
                    x += (float) (cx * push);
                    y += (float) (cy * push);
                    z += (float) (cz * push);
                }
            }
            if (p.window && ClientFx.heave > 0.002f) {
                double hx = wx - 128, hy = wy - 33;
                z -= (float) (0.55 * ClientFx.heave * Math.exp(-(hx * hx + hy * hy) / 80));
            }
        }
        x += 0.5f;
        y += 0.5f;
        z += 0.5f;
        float a = p.normal[v * 3] / 127f, b = p.normal[v * 3 + 1] / 127f, c = p.normal[v * 3 + 2] / 127f;
        float nx = n.m00() * a + n.m10() * b + n.m20() * c, ny = n.m01() * a + n.m11() * b + n.m21() * c, nz = n.m02() * a + n.m12() * b + n.m22() * c;
        int color = shader ? 0xFE000000 | (p.wave[v * 2] & 255) << 16 | (p.wave[v * 2 + 1] & 255) << 8 | p.texture : 0xFFFFFFFF;
        out.addVertex(m.m00() * x + m.m10() * y + m.m20() * z + m.m30(), m.m01() * x + m.m11() * y + m.m21() * z + m.m31(), m.m02() * x + m.m12() * y + m.m22() * z + m.m32(),
                color, shader ? Shape.octU(nx, ny, nz) : p.uv[v * 2], shader ? Shape.octV(nx, ny, nz) : p.uv[v * 2 + 1], overlay, light, nx, ny, nz);
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
            return new MeshRenderer();
        }
    }
}
