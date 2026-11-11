package world.hechao.fleshpark.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.serialization.MapCodec;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.special.NoDataSpecialModelRenderer;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.MaterialSet;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.function.Consumer;

/**
 * 开园前夜的公路：外面的世界来了。从接驳站一直排到落客区的车队，车边站着举手机的人，园区上空放烟花。
 * 一整条路只有一个物品展示实体，跟着人走（实体离人太远服务端就不发了），顶点按世界坐标直接算，所以远处也看得见。
 * 车和人是固定的随机序列，每个客户端看到的都一样。
 */
final class Road implements NoDataSpecialModelRenderer {
    private static final RenderType SOLID = RenderTypes.entitySolid(TextureAtlas.LOCATION_ITEMS);
    private static final RenderType GLOW = RenderTypes.entityTranslucent(TextureAtlas.LOCATION_ITEMS);
    private static final Material METAL = new Material(TextureAtlas.LOCATION_ITEMS, Identifier.fromNamespaceAndPath("fleshpark", "item/flashlight"));
    private static final Material LENS = new Material(TextureAtlas.LOCATION_ITEMS, Identifier.fromNamespaceAndPath("fleshpark", "item/flashlight_lens"));
    private static final int DIM = 3 << 4, BRIGHT = 15 << 4 | 15 << 20;
    private static final float GROUND = 231f;
    private static final int[] PAINT = {0xFF8A2E2A, 0xFF2F4F6E, 0xFFB8B8B4, 0xFF3D3D40, 0xFF6E6A5E, 0xFF20344A, 0xFFA8A29A, 0xFF5A2A36};
    private static final int[] CLOTH = {0xFFB84A3A, 0xFF3C6A8A, 0xFFD8C8A0, 0xFF4A6A4A, 0xFF8A5AA0, 0xFFD0A030, 0xFF505058, 0xFFC0C4C8};

    private record Car(float x, float z, float len, float wid, float hi, int paint) {
    }

    private record Person(float x, float z, float tall, int shirt, float phone) {
    }

    /** 一朵烟花：炸开的地方、颜色、什么时候炸的（毫秒）。 */
    private record Burst(float x, float y, float z, int rgb, long at, int seed, float size) {
    }

    private static final List<Car> CARS = new ArrayList<>();
    private static final List<Person> PEOPLE = new ArrayList<>();
    private static final List<Burst> BURSTS = new ArrayList<>();

    static {
        Random r = new Random(38);
        // 主队列贴着车道走，第二排停在路肩，到处是车。接驳站对面留一段空着
        for (double[] lane : new double[][]{{0.55, 5.4, 7.2}, {-3.9, 6.4, 9.5}}) {
            double x = -535;
            while (x < -66) {
                float len = 3.9f + r.nextFloat() * 1.1f, wid = 1.75f + r.nextFloat() * 0.2f, hi = 1.2f + r.nextFloat() * 0.45f;
                // 第二排别撞路灯的灯杆
                boolean pole = lane[0] < 0 && Math.abs(Math.floorMod((int) x + 578, 76) - 0) < 5;
                if (!pole) CARS.add(new Car((float) x, (float) lane[0], len, wid, hi, PAINT[r.nextInt(PAINT.length)]));
                x += lane[1] + r.nextFloat() * (lane[2] - lane[1]);
            }
        }
        // 车边上的人：两排车中间和路肩外面，好些人举着手机对着路
        for (int i = 0; i < 210; i++) {
            float x = -528 + r.nextFloat() * 462;
            float z = i % 3 == 0 ? 9.6f + r.nextFloat() * 2.2f : -1.5f + r.nextFloat() * 0.75f;
            PEOPLE.add(new Person(x, z, 1.5f + r.nextFloat() * 0.35f, CLOTH[r.nextInt(CLOTH.length)], r.nextFloat() < 0.6f ? r.nextFloat() * 6.28f : -1));
        }
    }

    private static boolean on;

    private final MaterialSet materials;
    private TextureAtlasSprite metal, lens;

    private Road(MaterialSet materials) {
        this.materials = materials;
    }

    /** 开着才画。服务端摆实体的时候会一起打开。 */
    static void show(boolean visible) {
        on = visible;
        if (!visible) BURSTS.clear();
    }

    /** 放一朵烟花。 */
    static void burst(float x, float y, float z, int rgb, float size) {
        BURSTS.add(new Burst(x, y, z, rgb, System.currentTimeMillis(), BURSTS.size() * 31 + (int) x, size));
    }

    /** 一根方条，物品格子的坐标：扔给方向盘当辐条用。 */
    static void bar(PoseStack.Pose pose, VertexConsumer out, TextureAtlasSprite s, float x0, float y0, float z0, float x1, float y1, float z1, int color, int light, int overlay) {
        Matrix4f m = pose.pose();
        Matrix3f n = pose.normal();
        cubeFaces(x0, y0, z0, x1, y1, z1, (px, py, pz, nx, ny, nz) -> {
            Vector3f v = new Vector3f(px, py, pz).mulPosition(m);
            Vector3f w = new Vector3f(nx, ny, nz).mul(n).normalize();
            out.addVertex(v.x, v.y, v.z, color, s.getU0(), s.getV0(), overlay, light, w.x, w.y, w.z);
        });
    }

    private interface Vertex {
        void at(float x, float y, float z, float nx, float ny, float nz);
    }

    /** 六个面，四个角一组，从外面看逆时针。 */
    private static void cubeFaces(float x0, float y0, float z0, float x1, float y1, float z1, Vertex v) {
        v.at(x1, y0, z0, 1, 0, 0); v.at(x1, y1, z0, 1, 0, 0); v.at(x1, y1, z1, 1, 0, 0); v.at(x1, y0, z1, 1, 0, 0);
        v.at(x0, y0, z1, -1, 0, 0); v.at(x0, y1, z1, -1, 0, 0); v.at(x0, y1, z0, -1, 0, 0); v.at(x0, y0, z0, -1, 0, 0);
        v.at(x0, y1, z0, 0, 1, 0); v.at(x0, y1, z1, 0, 1, 0); v.at(x1, y1, z1, 0, 1, 0); v.at(x1, y1, z0, 0, 1, 0);
        v.at(x0, y0, z1, 0, -1, 0); v.at(x0, y0, z0, 0, -1, 0); v.at(x1, y0, z0, 0, -1, 0); v.at(x1, y0, z1, 0, -1, 0);
        v.at(x1, y0, z1, 0, 0, 1); v.at(x1, y1, z1, 0, 0, 1); v.at(x0, y1, z1, 0, 0, 1); v.at(x0, y0, z1, 0, 0, 1);
        v.at(x0, y0, z0, 0, 0, -1); v.at(x0, y1, z0, 0, 0, -1); v.at(x1, y1, z0, 0, 0, -1); v.at(x1, y0, z0, 0, 0, -1);
    }

    @Override
    public void submit(ItemDisplayContext context, PoseStack stack, SubmitNodeCollector collector, int light, int overlay, boolean glint, int outline) {
        if (!on) return;
        if (metal == null) {
            metal = materials.get(METAL);
            lens = materials.get(LENS);
        }
        collector.submitCustomGeometry(stack, SOLID, (pose, out) -> {
            Vec3 eye = Minecraft.getInstance().gameRenderer.getMainCamera().position();
            float ex = (float) eye.x, ey = (float) eye.y, ez = (float) eye.z;
            for (Car car : CARS) drawCar(out, car, ex, ey, ez);
            for (Person person : PEOPLE) drawPerson(out, person, ex, ey, ez);
        });
        collector.submitCustomGeometry(stack, GLOW, (pose, out) -> drawBursts(out));
    }

    private void box(VertexConsumer out, float x0, float y0, float z0, float x1, float y1, float z1, int color, int lit, TextureAtlasSprite s, float ex, float ey, float ez) {
        float u = s.getU0(), w = s.getV0();
        cubeFaces(x0 - ex, y0 - ey, z0 - ez, x1 - ex, y1 - ey, z1 - ez, (px, py, pz, nx, ny, nz) -> out.addVertex(px, py, pz, color, u, w, OverlayTexture.NO_OVERLAY, lit, nx, ny, nz));
    }

    /** 车头朝 +x：朝着公园排。后面两盏红灯，前面两盏白灯。 */
    private void drawCar(VertexConsumer out, Car c, float ex, float ey, float ez) {
        float x0 = c.x, x1 = c.x + c.len, z0 = c.z, z1 = c.z + c.wid, base = GROUND + 0.22f, top = base + c.hi * 0.5f;
        box(out, x0, base, z0, x1, top, z1, c.paint, DIM, metal, ex, ey, ez);
        box(out, x0 + c.len * 0.2f, top, z0 + 0.08f, x1 - c.len * 0.22f, base + c.hi, z1 - 0.08f, 0xFF16181C, DIM, metal, ex, ey, ez);
        for (float wz : new float[]{z0 + 0.12f, z1 - 0.42f}) {
            box(out, x0 - 0.02f, top - 0.26f, wz, x0, top - 0.1f, wz + 0.3f, 0xFFFF2A1E, BRIGHT, lens, ex, ey, ez);
            box(out, x1, top - 0.3f, wz, x1 + 0.02f, top - 0.14f, wz + 0.3f, 0xFFFFF2C8, BRIGHT, lens, ex, ey, ez);
        }
    }

    private void drawPerson(VertexConsumer out, Person p, float ex, float ey, float ez) {
        float hip = GROUND + p.tall * 0.47f, neck = GROUND + p.tall * 0.82f;
        box(out, p.x - 0.14f, GROUND, p.z - 0.09f, p.x + 0.14f, hip, p.z + 0.09f, 0xFF24262C, DIM, metal, ex, ey, ez);
        box(out, p.x - 0.19f, hip, p.z - 0.11f, p.x + 0.19f, neck, p.z + 0.11f, p.shirt, DIM, metal, ex, ey, ez);
        box(out, p.x - 0.13f, neck, p.z - 0.13f, p.x + 0.13f, GROUND + p.tall, p.z + 0.13f, 0xFFC09878, DIM, metal, ex, ey, ez);
        if (p.phone >= 0) {
            // 手机举在脸前面，屏幕亮着；隔一会儿闪一下，像在拍
            float side = (float) Math.cos(p.phone) * 0.1f, hold = GROUND + p.tall * 0.95f;
            box(out, p.x + side - 0.05f, hold, p.z - 0.26f, p.x + side + 0.05f, hold + 0.18f, p.z - 0.2f, 0xFFE8F2FF, BRIGHT, lens, ex, ey, ez);
            box(out, p.x + side - 0.05f, hold, p.z + 0.2f, p.x + side + 0.05f, hold + 0.18f, p.z + 0.26f, 0xFFE8F2FF, BRIGHT, lens, ex, ey, ez);
        }
    }

    /** 烟花：每朵一圈亮点往外炸，慢慢往下坠、变暗，末尾闪两下。亮点永远朝着镜头，远的画大一点，远处也看得见。 */
    private void drawBursts(VertexConsumer out) {
        Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        Vec3 eye = camera.position();
        Vector3fc left = camera.leftVector(), up = camera.upVector();
        long now = System.currentTimeMillis();
        float u = lens.getU0(), v = lens.getV0();
        Iterator<Burst> it = BURSTS.iterator();
        while (it.hasNext()) {
            Burst b = it.next();
            float t = (now - b.at) / 1000f;
            if (t > 3.4f) {
                it.remove();
                continue;
            }
            Random r = new Random(b.seed);
            float fall = 3.4f * t * t;
            for (int i = 0; i < 90; i++) {
                float a = r.nextFloat() * 6.283f, h = r.nextFloat() * 2 - 1, q = (float) Math.sqrt(1 - h * h), speed = 0.8f + r.nextFloat() * 0.2f, phase = r.nextFloat() * 6.28f;
                float fade = t < 1.8f ? 1 : Math.max(0, 1 - (t - 1.8f) / 1.6f);
                if (t > 1.8f && Math.sin(t * 24 + phase) < -0.2) fade *= 0.15f;
                if (fade <= 0.01f) continue;
                // 一颗亮点带一小截尾巴
                for (int tail = 0; tail < 3; tail++) {
                    float back = tail * 0.09f, g = 1 - (float) Math.exp(-3.2f * Math.max(0, t - back));
                    float x = b.x + q * (float) Math.cos(a) * b.size * speed * g, y = b.y + h * b.size * speed * g - fall, z = b.z + q * (float) Math.sin(a) * b.size * speed * g;
                    float dx = x - (float) eye.x, dy = y - (float) eye.y, dz = z - (float) eye.z, dist = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
                    float s = Math.max(0.12f, dist * 0.0026f) * (1 - tail * 0.25f);
                    int alpha = (int) (255 * fade * (1 - tail * 0.32f));
                    int color = alpha << 24 | b.rgb;
                    vertex(out, dx - left.x() * s - up.x() * s, dy - left.y() * s - up.y() * s, dz - left.z() * s - up.z() * s, color, u, v);
                    vertex(out, dx + left.x() * s - up.x() * s, dy + left.y() * s - up.y() * s, dz + left.z() * s - up.z() * s, color, u, v);
                    vertex(out, dx + left.x() * s + up.x() * s, dy + left.y() * s + up.y() * s, dz + left.z() * s + up.z() * s, color, u, v);
                    vertex(out, dx - left.x() * s + up.x() * s, dy - left.y() * s + up.y() * s, dz - left.z() * s + up.z() * s, color, u, v);
                }
            }
        }
    }

    private static void vertex(VertexConsumer out, float x, float y, float z, int color, float u, float v) {
        out.addVertex(x, y, z, color, u, v, OverlayTexture.NO_OVERLAY, BRIGHT, 0, 1, 0);
    }

    @Override
    public void getExtents(Consumer<Vector3fc> output) {
        output.accept(new Vector3f(-1, -1, -1));
        output.accept(new Vector3f(1, 1, 1));
    }

    record Unbaked() implements SpecialModelRenderer.Unbaked {
        static final MapCodec<Unbaked> MAP_CODEC = MapCodec.unit(new Unbaked());

        @Override
        public MapCodec<Unbaked> type() {
            return MAP_CODEC;
        }

        @Override
        public SpecialModelRenderer<?> bake(SpecialModelRenderer.BakingContext context) {
            return new Road(context.materials());
        }
    }
}
