package world.hechao.fleshpark.core;

import world.hechao.fleshpark.core.Parts.Box;

import java.util.ArrayList;
import java.util.List;

/** 缆车车厢，全用方块展示拼，坐标相对根实体。车门卡死在开着的位置，开在 +z 一侧。 */
public final class TramModel {
    /** 门洞（相对根实体的 x）。对准车站屏障留的口子。 */
    public static final double DOOR_X0 = -4.2, DOOR_X1 = -2.2;

    private static final String RAIL = "minecraft:iron_block";

    private TramModel() {
    }

    /** 车身、窗框、座椅、扶手都在客户端模组的圆角模型里，这里只剩遗留物。 */
    public static List<Box> parts() {
        List<Box> p = new ArrayList<>();
        double x0 = Route.CABIN_X0, hz = Route.CABIN_HALF_Z, floor = -Route.FLOOR_DROP;
        add(p, "minecraft:brown_wool", x0 + 1.6, floor + 0.52, -hz + 0.2, 0.36, 0.42, 0.26);
        add(p, RAIL, x0 + 3.2, floor + 0.52, -hz + 0.3, 0.07, 0.13, 0.07);
        add(p, "minecraft:red_concrete", x0 + 3.2, floor + 0.55, -hz + 0.295, 0.072, 0.07, 0.072);
        return p;
    }

    /** 红按钮：单独一块，亮度固定，黑暗里能找到。 */
    public static Box button() {
        return new Box("minecraft:red_concrete", Route.CABIN_X1 - 0.5, -Route.FLOOR_DROP + 0.66, -Route.CABIN_HALF_Z + 0.55, 0.05, 0.2, 0.2, 15);
    }

    /** 亮着的那根灯管。亮度由 Tram 控制。 */
    public static Box lampTube() {
        return new Box("minecraft:ochre_froglight", Route.CABIN_X0 + 1.2, -Route.FLOOR_DROP + 2.4 - 0.08, -0.76, Route.CABIN_X1 - Route.CABIN_X0 - 2.4, 0.03, 0.08, 0);
    }

    private static void add(List<Box> parts, String block, double x, double y, double z, double sx, double sy, double sz) {
        parts.add(new Box(block, x, y, z, sx, sy, sz, -1));
    }
}
