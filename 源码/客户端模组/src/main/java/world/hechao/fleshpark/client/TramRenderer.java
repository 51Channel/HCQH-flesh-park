package world.hechao.fleshpark.client;

import com.mojang.blaze3d.vertex.PoseStack;
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
import org.joml.Vector3f;
import org.joml.Vector3fc;

import java.util.function.Consumer;

/**
 * 813 号缆车：圆角的车身、车窗立柱、弧形车顶、吊臂、座椅、扶手。坐标相对根实体（x 从 -6.2 到 0.8，z 从 -2 到 2，地板在 -0.33）。
 * 车门开在 +z 一侧，卡死在开着的位置。
 */
final class TramRenderer implements NoDataSpecialModelRenderer {
    private static final RenderType SOLID = RenderTypes.entitySolid(TextureAtlas.LOCATION_ITEMS);
    private static final Material PAINT = new Material(TextureAtlas.LOCATION_ITEMS, Identifier.fromNamespaceAndPath("fleshpark", "item/tram_paint"));

    private static final double X0 = -6.2, X1 = 0.8, HALF_Z = 2, CX = (X0 + X1) / 2, HALF_X = (X1 - X0) / 2, CORNER = 0.85, FLOOR = -0.33, SILL = FLOOR + 0.62, TOP = FLOOR + 2.2, CEIL = FLOOR + 2.42, CABLE = 3.2, CABLE_SIDE = 2.6;
    private static final double DOOR0 = -4.2, DOOR1 = -2.2;
    private static final double PERIMETER = 4 * (HALF_X - CORNER) + 4 * (HALF_Z - CORNER) + 2 * Math.PI * CORNER;
    private static final int BODY = 0xFF8E9086, BELT = 0xFF4E6A64, TRIM = 0xFF2E3234, FLOOR_COLOR = 0xFF555656, SEAT = 0xFF6A5444, METAL = 0xFF6E706C, BOX = 0xFF49655A;

    private static final Shape BODY_SHAPE = new Shape(), BELT_SHAPE = new Shape(), TRIM_SHAPE = new Shape(), FLOOR_SHAPE = new Shape(), SEAT_SHAPE = new Shape(), METAL_SHAPE = new Shape(), BOX_SHAPE = new Shape();

    static {
        build();
    }

    private final MaterialSet materials;
    private TextureAtlasSprite paint;

    private TramRenderer(MaterialSet materials) {
        this.materials = materials;
    }

    /** 车身的平面轮廓：圆角矩形。t 从车头正中开始绕一圈，输出 x、z。 */
    private static void outline(double t, double scale, double[] out) {
        double s = ((t % 1) + 1) % 1 * PERIMETER;
        double hx = HALF_X - CORNER, hz = HALF_Z - CORNER;
        double[] lens = {hz, Math.PI / 2 * CORNER, 2 * hx, Math.PI / 2 * CORNER, 2 * hz, Math.PI / 2 * CORNER, 2 * hx, Math.PI / 2 * CORNER, hz};
        double x, z;
        int i = 0;
        while (i < lens.length - 1 && s > lens[i]) {
            s -= lens[i];
            i++;
        }
        switch (i) {
            case 0 -> {
                x = HALF_X;
                z = s;
            }
            case 1 -> {
                double a = s / CORNER;
                x = hx + Math.cos(a) * CORNER;
                z = hz + Math.sin(a) * CORNER;
            }
            case 2 -> {
                x = hx - s;
                z = HALF_Z;
            }
            case 3 -> {
                double a = Math.PI / 2 + s / CORNER;
                x = -hx + Math.cos(a) * CORNER;
                z = hz + Math.sin(a) * CORNER;
            }
            case 4 -> {
                x = -HALF_X;
                z = hz - s;
            }
            case 5 -> {
                double a = Math.PI + s / CORNER;
                x = -hx + Math.cos(a) * CORNER;
                z = -hz + Math.sin(a) * CORNER;
            }
            case 6 -> {
                x = -hx + s;
                z = -HALF_Z;
            }
            case 7 -> {
                double a = Math.PI * 1.5 + s / CORNER;
                x = hx + Math.cos(a) * CORNER;
                z = -hz + Math.sin(a) * CORNER;
            }
            default -> {
                x = HALF_X;
                z = -hz + s;
            }
        }
        out[0] = CX + x * scale;
        out[1] = z * scale;
    }

    /** +z 侧走到某个 x 时的 t。 */
    private static double tAtSideX(double x) {
        double hx = HALF_X - CORNER, hz = HALF_Z - CORNER;
        return (hz + Math.PI / 2 * CORNER + (hx - (x - CX))) / PERIMETER;
    }

    /** 沿轮廓从 t0 到 t1 的一圈墙，内外各一层。 */
    private static void wall(Shape shape, double t0, double t1, double y0, double y1, double inset) {
        int sides = Math.max(6, (int) ((t1 - t0) * 96));
        double[] q = new double[2];
        Shape.Surface outer = (u, v, out) -> {
            outline(t0 + (t1 - t0) * u, 1, q);
            out[0] = 0.5 + q[0];
            out[1] = 0.5 + y0 + (y1 - y0) * v;
            out[2] = 0.5 + q[1];
        };
        Shape.Surface inner = (u, v, out) -> {
            outline(t0 + (t1 - t0) * u, 1 - inset, q);
            out[0] = 0.5 + q[0];
            out[1] = 0.5 + y0 + (y1 - y0) * v;
            out[2] = 0.5 + q[1];
        };
        shape.surface(outer, sides, 2, 0.5 + CX, 0.5 + (y0 + y1) / 2, 0.5);
        shape.surface(inner, sides, 2, 0.5 + CX, 0.5 + (y0 + y1) / 2, 0.5, true);
    }

    private static void pillar(Shape shape, double t, double y0, double y1, double r) {
        double[] q = new double[2];
        outline(t, 0.985, q);
        shape.capsule(0.5 + q[0], 0.5 + y0, 0.5 + q[1], r, 0.5 + q[0], 0.5 + y1, 0.5 + q[1], r, 12, 9);
    }

    private static void rod(Shape shape, double x0, double y0, double z0, double x1, double y1, double z1, double r) {
        shape.capsule(0.5 + x0, 0.5 + y0, 0.5 + z0, r, 0.5 + x1, 0.5 + y1, 0.5 + z1, r, 12, 9);
    }

    private static void build() {
        double d0 = tAtSideX(DOOR1), d1 = tAtSideX(DOOR0);
        BODY_SHAPE.side(16);
        BELT_SHAPE.side(16);
        // 下半截车身：门洞那一段空着
        wall(BODY_SHAPE, d1, d0 + 1, FLOOR - 0.2, SILL - 0.08, 0.02);
        wall(BELT_SHAPE, d1, d0 + 1, SILL - 0.08, SILL + 0.02, 0.03);
        // 门头
        wall(BODY_SHAPE, d0, d1, TOP - 0.18, TOP, 0.02);
        // 窗上那一圈
        wall(BODY_SHAPE, 0, 1, TOP, CEIL - 0.02, 0.02);
        // 窗上不放玻璃：半透明的面会写进深度，光影就算不到窗外的雾和天空，窗外会变成一片平色
        double[] q = new double[2];
        // 立柱：车头两根让出视野，两侧各几根
        for (double t : new double[]{0.055, 0.945, 0.2, 0.31, 0.4, 0.5, 0.6, 0.69, 0.8}) pillar(TRIM_SHAPE, t, SILL, TOP, 0.045);
        pillar(TRIM_SHAPE, d0, FLOOR, TOP, 0.06);
        pillar(TRIM_SHAPE, d1, FLOOR, TOP, 0.06);
        // 弧形车顶：外面一层，里面一层
        Shape.Surface roof = (u, v, out) -> {
            double k = Math.cos(v * Math.PI / 2);
            outline(u, Math.max(0.001, k), q);
            out[0] = 0.5 + q[0];
            out[1] = 0.5 + CEIL + Math.sin(v * Math.PI / 2) * 0.3;
            out[2] = 0.5 + q[1];
        };
        BODY_SHAPE.side(0);
        BODY_SHAPE.planar(8);
        BODY_SHAPE.surface(roof, 96, 10, 0.5 + CX, 0.5 + CEIL - 6, 0.5);
        Shape.Surface ceiling = (u, v, out) -> {
            double k = Math.cos(v * Math.PI / 2);
            outline(u, Math.max(0.001, k) * 0.98, q);
            out[0] = 0.5 + q[0];
            out[1] = 0.5 + CEIL - 0.03 + Math.sin(v * Math.PI / 2) * 0.22;
            out[2] = 0.5 + q[1];
        };
        BODY_SHAPE.surface(ceiling, 96, 10, 0.5 + CX, 0.5 + CEIL - 6, 0.5, true);
        BODY_SHAPE.planar(0);
        // 地板
        Shape.Surface floor = (u, v, out) -> {
            outline(u, Math.max(0.001, 1 - v), q);
            out[0] = 0.5 + q[0];
            out[1] = 0.5 + FLOOR - 0.01;
            out[2] = 0.5 + q[1];
        };
        FLOOR_SHAPE.planar(8);
        FLOOR_SHAPE.surface(floor, 96, 4, 0.5 + CX, 0.5 + FLOOR - 6, 0.5);
        Shape.Surface under = (u, v, out) -> {
            outline(u, Math.max(0.001, 1 - v), q);
            out[0] = 0.5 + q[0];
            out[1] = 0.5 + FLOOR - 0.2;
            out[2] = 0.5 + q[1];
        };
        BELT_SHAPE.side(0);
        BELT_SHAPE.planar(8);
        BELT_SHAPE.surface(under, 96, 4, 0.5 + CX, 0.5 + FLOOR + 6, 0.5);
        BELT_SHAPE.planar(0);
        // 长椅：靠 -z 一整条，靠 +z 车门前面一段
        SEAT_SHAPE.roundBox(0.5 + CX - 0.5, 0.5 + FLOOR + 0.42, 0.5 - HALF_Z + 0.42, 2.45, 0.09, 0.27, 16);
        SEAT_SHAPE.roundBox(0.5 + CX - 0.5, 0.5 + FLOOR + 0.78, 0.5 - HALF_Z + 0.15, 2.45, 0.32, 0.06, 16);
        SEAT_SHAPE.roundBox(0.5 + X0 + 0.95, 0.5 + FLOOR + 0.42, 0.5 + HALF_Z - 0.42, 0.55, 0.09, 0.27, 12);
        BOX_SHAPE.roundBox(0.5 + CX - 0.5, 0.5 + FLOOR + 0.17, 0.5 - HALF_Z + 0.42, 2.4, 0.17, 0.22, 12);
        // 前窗下的操作箱
        BOX_SHAPE.roundBox(0.5 + X1 - 0.3, 0.5 + FLOOR + 0.46, 0.5 - HALF_Z + 0.65, 0.17, 0.46, 0.45, 14);
        // 扶手和立杆
        rod(METAL_SHAPE, X0 + 0.6, FLOOR + 1.95, -HALF_Z + 0.32, X1 - 0.75, FLOOR + 1.95, -HALF_Z + 0.32, 0.018);
        rod(METAL_SHAPE, X0 + 0.6, FLOOR + 1.95, HALF_Z - 0.32, DOOR0 - 0.1, FLOOR + 1.95, HALF_Z - 0.32, 0.018);
        // 吊臂：车顶伸出四根臂，搭在两边的缆绳上。缆绳在车厢外面，再陡也不会穿过车厢
        for (double sx : new double[]{-1.7, 1.7}) {
            for (double sz : new double[]{-1, 1}) {
                rod(TRIM_SHAPE, CX + sx, CEIL + 0.22, sz * 1.1, CX + sx, CABLE, sz * CABLE_SIDE, 0.06);
            }
            rod(TRIM_SHAPE, CX + sx, CEIL + 0.26, -1.1, CX + sx, CEIL + 0.26, 1.1, 0.07);
        }
        for (double sz : new double[]{-1, 1}) {
            rod(TRIM_SHAPE, CX - 2.1, CABLE, sz * CABLE_SIDE, CX + 2.1, CABLE, sz * CABLE_SIDE, 0.09);
            BOX_SHAPE.roundBox(0.5 + CX - 1.7, 0.5 + CABLE + 0.08, 0.5 + sz * CABLE_SIDE, 0.22, 0.12, 0.1, 10);
            BOX_SHAPE.roundBox(0.5 + CX + 1.7, 0.5 + CABLE + 0.08, 0.5 + sz * CABLE_SIDE, 0.22, 0.12, 0.1, 10);
        }
        // 车头灯
        BOX_SHAPE.roundBox(0.5 + X1 + 0.02, 0.5 + FLOOR + 0.1, 0.5 - 1.1, 0.05, 0.08, 0.18, 10);
        BOX_SHAPE.roundBox(0.5 + X1 + 0.02, 0.5 + FLOOR + 0.1, 0.5 + 1.1, 0.05, 0.08, 0.18, 10);
    }

    @Override
    public void submit(ItemDisplayContext context, PoseStack stack, SubmitNodeCollector collector, int light, int overlay, boolean glint, int outline) {
        if (paint == null) paint = materials.get(PAINT);
        stack.pushPose();
        stack.translate(0.5f, 0, 0.5f);
        stack.mulPose(com.mojang.math.Axis.YP.rotation((float) Math.PI));
        stack.translate(-0.5f, 0, -0.5f);
        collector.submitCustomGeometry(stack, SOLID, (pose, out) -> {
            BODY_SHAPE.emit(pose, out, paint, BODY, light, overlay);
            BELT_SHAPE.emit(pose, out, paint, BELT, light, overlay);
            TRIM_SHAPE.emit(pose, out, paint, TRIM, light, overlay);
            FLOOR_SHAPE.emit(pose, out, paint, FLOOR_COLOR, light, overlay);
            SEAT_SHAPE.emit(pose, out, paint, SEAT, light, overlay);
            METAL_SHAPE.emit(pose, out, paint, METAL, light, overlay);
            BOX_SHAPE.emit(pose, out, paint, BOX, light, overlay);
        });
        stack.popPose();
    }

    @Override
    public void getExtents(Consumer<Vector3fc> output) {
        output.accept(new Vector3f((float) X0, (float) FLOOR, (float) -CABLE_SIDE));
        output.accept(new Vector3f((float) X1, (float) CABLE, (float) CABLE_SIDE));
    }

    record Unbaked() implements SpecialModelRenderer.Unbaked {
        static final MapCodec<Unbaked> MAP_CODEC = MapCodec.unit(new Unbaked());

        @Override
        public MapCodec<Unbaked> type() {
            return MAP_CODEC;
        }

        @Override
        public SpecialModelRenderer<?> bake(SpecialModelRenderer.BakingContext context) {
            return new TramRenderer(context.materials());
        }
    }
}
