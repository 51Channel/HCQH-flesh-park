package world.hechao.fleshpark.core;

import world.hechao.fleshpark.core.Parts.Beam;
import world.hechao.fleshpark.core.Parts.Box;
import world.hechao.fleshpark.core.Parts.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 第四关深井、第五关井底的真游客中心。
 * 森林那部电梯掉下来，卡在井壁半中间。井口上压着第一关那座游客中心的底，井底躺着 2007 年塌下去的那座。
 * 往下的路：电梯口平台 → 4 号支撑斜着往东下去 → 支撑脚下的平台 → 搜救队的绳梯 → 防水袋平台 → 807 号车顺着断缆滑到底 → 井底北边的落脚处。
 * 真游客中心是把第一关那座整栋平移下来再弄碎：位置一一对得上，走进去才认得出来。
 * 井壁是肉网格，没有碰撞：人能走到的地方四周都要围上看不见的墙。
 */
public final class Well {
    /** 井的中轴。 */
    public static final int SX = 510, SZ = 0;
    /** 井口（上面那座楼的底）和井底地面。 */
    public static final int TOP = 184, BOTTOM = 14;
    /** 真游客中心 = 第一关那座平移这么多。 */
    public static final int DX = 376, DY = -16;
    public static final int REAL_FLOOR = ParkPlan.FLOOR_HALL + DY, REAL_GARDEN = Lower.GARDEN_FLOOR + DY;

    /** 卡住的电梯：门朝 +x，和森林那部一模一样。 */
    public static final int CAB_X = 476, CAB_Y = 166, CAB_Z = -21;
    /** 电梯口平台、支撑、平台都在这条 z 上：PATH_Z0 到 PATH_Z1，中间三格是路。 */
    public static final int PATH_Z0 = -22, PATH_Z1 = -18;
    public static final int LEDGE_X0 = 480, LEDGE_X1 = 486;
    /** 4 号支撑：每往东一格往下一格。第 i 级台阶在 x = TRUSS_X0 + i，方块在 CAB_Y - 1 - i。 */
    public static final int TRUSS_X0 = 487, STEPS = 53, TRUSS_X1 = TRUSS_X0 + STEPS - 1;
    /** 支撑脚下的平台：人站着的高度。 */
    public static final int FOOT_X0 = TRUSS_X1 + 1, FOOT_X1 = FOOT_X0 + 3, FOOT_Y = CAB_Y - STEPS;
    /** 绳梯：x 这一列，从 FOOT_Y + 1 一直到 BAG_Y。 */
    public static final int LADDER_X = FOOT_X1 + 1, LADDER_Z = -20, BAG_Y = 72;
    /** 绳梯底下的平台：防水袋、冷光棒，807 号车停在它北边。 */
    public static final int BAG_X0 = FOOT_X0 - 1, BAG_X1 = LADDER_X;
    /** 807 号车：开始挂在防水袋平台北边，顺着断缆滑到井底北边。根实体的位置（车厢地板）。 */
    public static final double[] CAR_FROM = {LADDER_X + 1.5, BAG_Y, PATH_Z0 - 2.6}, CAR_TO = {500, REAL_FLOOR, -34};
    /** 井底北边的落脚处。 */
    public static final int LAND_X0 = 486, LAND_X1 = 516, LAND_Z0 = -42, LAND_Z1 = -29;
    /** 冷光棒那一捆。 */
    public static final double[] STICKS = {BAG_X0 + 1.4, BAG_Y + 0.02, PATH_Z1 - 0.4};
    /** 花园层东头通隧道的口子：真楼里被肉堵死了。肉腔不往这一片长。 */
    public static final int ROOM_X0 = 153 + DX, ROOM_X1 = ROOM_X0 + 8, ROOM_Z0 = -3, ROOM_Z1 = 3;
    /** 第一关那座：要整栋复制的范围。 */
    private static final int SRC_X0 = 86, SRC_X1 = 163, SRC_Y0 = 16, SRC_Y1 = 52, SRC_Z0 = -30, SRC_Z1 = 30;

    /** 人走的路（x、脚下高度、z）。 */
    public final List<double[]> walk = new ArrayList<>();

    private final ParkPlan p;
    private final Random rnd;

    Well(ParkPlan plan) {
        p = plan;
        rnd = new Random(4407);
    }

    /** 井在这个高度的半径：越往下越宽，井底罩得住整栋楼。 */
    public static double radius(double y) {
        double k = Math.max(0, Math.min(1, (TOP - y) / (TOP - BOTTOM - 28.0)));
        return 36 + 10 * k;
    }

    /** 第 i 级台阶的方块高度。 */
    public static int stepY(int i) {
        return CAB_Y - 1 - i;
    }

    void build() {
        realBuilding();
        cab();
        truss();
        foot();
        ladder();
        bag();
        cable();
        bottom();
        sealTunnel();
        lid();
        stumps();
        paths();
    }

    // ---------- 往下的路 ----------

    private void cab() {
        Forest.cabAt(p, CAB_X, CAB_Y, CAB_Z);
        Forest.cabPanel(p, CAB_X, CAB_Y, CAB_Z, null, 0);
        // 电梯口平台：安诺丹的旧员工平台，一半已经被肉吃进去
        fill(LEDGE_X0, CAB_Y - 1, PATH_Z0, LEDGE_X1, CAB_Y - 1, PATH_Z1, B.GRATE);
        p.texts.add(new Text("安诺丹 · 员工专用\n支撑检修平台", LEDGE_X0 + 0.04, CAB_Y + 1.9, PATH_Z0 + 0.6, -90f, 0xf2e8cc, 0.45f, 0xff1b3a3a, 6));
        // 平台三面是井，只有东头接着支撑
        for (int x = LEDGE_X0; x <= LEDGE_X1; x++) {
            wall(x, CAB_Y, PATH_Z0 - 1);
            wall(x, CAB_Y, PATH_Z1 + 1);
            rail(x, CAB_Y, PATH_Z1, PATH_Z0);
        }
        wall(LEDGE_X1 + 1, CAB_Y, PATH_Z0);
        wall(LEDGE_X1 + 1, CAB_Y, PATH_Z1);
        p.light(LEDGE_X0 + 2, CAB_Y + 2, PATH_Z0 + 2, 5);
    }

    /** 4 号支撑：钢桁架斜插进井里，检修步道一级一级往下。 */
    private void truss() {
        for (int i = 0; i < STEPS; i++) {
            int x = TRUSS_X0 + i, y = stepY(i);
            for (int z = PATH_Z0 + 1; z <= PATH_Z1 - 1; z++) p.set(x, y, z, B.STAIR_W);
            p.set(x, y + 1, PATH_Z0, B.BARS_X);
            p.set(x, y + 1, PATH_Z1, B.BARS_X);
            // 栏杆外面看不见的墙，两格高，人翻不出去
            wall(x, y + 1, PATH_Z0 - 1);
            wall(x, y + 1, PATH_Z1 + 1);
        }
        // 两根主梁和下面的斜撑
        double ax = TRUSS_X0, ay = stepY(0) - 0.2, bx = TRUSS_X1 + 1, by = stepY(STEPS - 1) - 0.7;
        for (double z : new double[]{PATH_Z0 + 0.35, PATH_Z1 + 0.65}) {
            p.beams.add(new Beam("minecraft:polished_deepslate", ax - 6, ay + 6, z, bx, by, z, 0.5, -1));
            p.beams.add(new Beam("minecraft:polished_deepslate", ax - 6, ay + 4.6, z, bx, by - 1.4, z, 0.3, -1));
        }
        for (int i = 0; i < STEPS; i += 4) {
            double x = TRUSS_X0 + i, y = stepY(i) - 0.2;
            p.beams.add(new Beam("minecraft:polished_deepslate", x, y, PATH_Z0 + 0.35, x + 2, y - 3.4, PATH_Z1 + 0.65, 0.18, -1));
            p.beams.add(new Beam("minecraft:polished_deepslate", x, y, PATH_Z1 + 0.65, x + 2, y - 3.4, PATH_Z0 + 0.35, 0.18, -1));
        }
        // 焊缝裂开的地方肉挤出来
        for (int i = 6; i < STEPS; i += 9 + rnd.nextInt(5)) {
            double x = TRUSS_X0 + i + rnd.nextDouble(), y = stepY(i) - 0.6;
            p.boxes.add(new Box("minecraft:red_mushroom_block", x, y - 0.5, PATH_Z0 + 0.1, 0.9, 0.8, 0.6, -1));
        }
        // 桁架上挂着刮下来的东西：有一个很大的东西从这里掉下去过
        int hang = 14;
        p.boxes.add(new Box("minecraft:iron_bars", TRUSS_X0 + hang + 0.2, stepY(hang) - 1.6, PATH_Z1 + 0.7, 1.6, 1.1, 0.06, -1));
        p.boxes.add(new Box("minecraft:polished_deepslate", TRUSS_X0 + 27.3, stepY(27) - 1.9, PATH_Z0 - 0.12, 1.4, 0.7, 0.06, -1));
        p.texts.add(new Text("3 号码头", TRUSS_X0 + 28.0, stepY(27) - 1.8, PATH_Z0 - 0.19, 180f, 0xf2e8cc, 0.4f, 0xff1b3a3a, 6));
        p.boxes.add(new Box("minecraft:pink_wool", TRUSS_X0 + 39.4, stepY(39) - 0.9, PATH_Z1 + 0.62, 0.22, 0.08, 0.1, -1));
        // 一路几盏还亮着的检修灯，灯和灯之间是黑的
        for (int i = 4; i < STEPS; i += 12) p.light(TRUSS_X0 + i, stepY(i) + 2, PATH_Z0 + 2, 6);
        p.boxes.add(new Box("minecraft:ochre_froglight", TRUSS_X0 + 0.3, stepY(0) + 2.4, PATH_Z0 + 0.1, 0.25, 0.25, 0.2, 13));
    }

    private void foot() {
        fill(FOOT_X0, FOOT_Y - 1, PATH_Z0, FOOT_X1, FOOT_Y - 1, PATH_Z1, B.GRATE);
        for (int x = FOOT_X0; x <= FOOT_X1 + 1; x++) {
            wall(x, FOOT_Y, PATH_Z0 - 1);
            wall(x, FOOT_Y, PATH_Z1 + 1);
            if (x <= FOOT_X1) {
                p.set(x, FOOT_Y, PATH_Z0, B.BARS_X);
                p.set(x, FOOT_Y, PATH_Z1, B.BARS_X);
            }
        }
        // 支撑脚顶在东边井壁上：一大块钢座
        p.boxes.add(new Box("minecraft:polished_deepslate", FOOT_X0 - 0.5, FOOT_Y - 3.2, PATH_Z0 - 0.5, FOOT_X1 - FOOT_X0 + 4, 2.2, PATH_Z1 - PATH_Z0 + 2, -1));
        p.light(FOOT_X0 + 1, FOOT_Y + 2, -20, 6);
    }

    /** 搜救队的绳梯：从支撑脚往下，挂在一根打进井壁的钢柱上。 */
    private void ladder() {
        for (int y = BAG_Y - 1; y <= FOOT_Y + 2; y++) p.set(LADDER_X + 1, y, LADDER_Z, B.STEEL);
        for (int y = BAG_Y; y <= FOOT_Y + 1; y++) {
            p.set(LADDER_X, y, LADDER_Z, B.LADDER_W);
            // 梯子两边和背后围起来，人只能顺着梯子上下
            wall(LADDER_X, y, LADDER_Z - 1);
            wall(LADDER_X, y, LADDER_Z + 1);
            // 梯子口朝西：上面平台那两格和最底下两格留着出入
            if (y < FOOT_Y - 1 && y > BAG_Y + 1) wall(LADDER_X - 1, y, LADDER_Z);
        }
        // 岩钉一颗一颗往下
        for (int y = BAG_Y + 2; y < FOOT_Y; y += 3) {
            p.boxes.add(new Box("minecraft:iron_block", LADDER_X + 0.82, y + 0.4, LADDER_Z + 0.35, 0.16, 0.16, 0.3, -1));
        }
        p.texts.add(new Text("搜救 07.7.6 ↓", LADDER_X + 0.9, FOOT_Y - 1.6, LADDER_Z - 0.9, 90f, 0xff7a20, 0.8f, 0, 4));
    }

    private void bag() {
        fill(BAG_X0, BAG_Y - 1, PATH_Z0, BAG_X1, BAG_Y - 1, PATH_Z1, B.GRATE);
        for (int x = BAG_X0 - 1; x <= BAG_X1 + 1; x++) {
            wall(x, BAG_Y, PATH_Z1 + 1);
            // 北边是车：车门那几格先拦着，车来了插件拆
            wall(x, BAG_Y, PATH_Z0 - 1);
        }
        for (int z = PATH_Z0; z <= PATH_Z1; z++) wall(BAG_X0 - 1, BAG_Y, z);
        p.light(BAG_X0 + 2, BAG_Y + 2, PATH_Z0 + 2, 5);
    }

    /** 当年通 3 号码头的缆车线：楼塌的时候连着缆一起拽了下来，斜着穿进雾里。 */
    private void cable() {
        double[] a = CAR_FROM, b = CAR_TO;
        double dx = b[0] - a[0], dy = b[1] - a[1], dz = b[2] - a[2];
        double up = Route.CABLE_Y + 0.15;
        p.beams.add(new Beam("minecraft:black_concrete", a[0] - dx * 0.18, a[1] + up - dy * 0.18, a[2] - dz * 0.18, b[0] + dx * 0.04, b[1] + up + dy * 0.04, b[2] + dz * 0.04, 0.12, -1));
    }

    /** 井底：看不见的地面和一圈墙。真楼的地板也在这个高度。 */
    private void bottom() {
        for (int x = SX - 50; x <= SX + 50; x++) {
            for (int z = SZ - 50; z <= SZ + 50; z++) {
                double r = Math.hypot(x + 0.5 - SX, z + 0.5 - SZ);
                if (r > 47) continue;
                // 真楼里面不铺：楼梯井底下是花园层
                if (Math.hypot(x + 0.5 - ParkPlan.DRUM_X - DX, z + 0.5) < ParkPlan.DRUM_R + 1) continue;
                if (p.block(x, REAL_FLOOR - 1, z) == null) p.set(x, REAL_FLOOR - 1, z, B.BARRIER);
                if (r > 43.5) for (int y = REAL_FLOOR; y <= REAL_FLOOR + 3; y++) if (p.block(x, y, z) == null) p.set(x, y, z, B.BARRIER);
            }
        }
        // 落脚处地上的碎东西
        for (int i = 0; i < 26; i++) {
            int x = LAND_X0 + rnd.nextInt(LAND_X1 - LAND_X0), z = LAND_Z0 + 1 + rnd.nextInt(LAND_Z1 - LAND_Z0 - 1);
            if (p.block(x, REAL_FLOOR, z) == null) p.set(x, REAL_FLOOR, z, rnd.nextBoolean() ? B.DEBRIS : B.DEBRIS_DARK);
        }
        p.light(CAR_TO_X(), REAL_FLOOR + 3, (int) CAR_TO[2], 3);
    }

    private static int CAR_TO_X() {
        return (int) CAR_TO[0];
    }

    // ---------- 真游客中心 ----------

    /**
     * 第一关那座整栋平移下来：方块、摆设、字都照搬，再弄碎、泡水、熄灯。
     * 码头那头的看不见的墙拆掉，人要从井底走上码头。
     */
    private void realBuilding() {
        List<int[]> put = new ArrayList<>();
        List<B> what = new ArrayList<>();
        for (int x = SRC_X0; x <= SRC_X1; x++) {
            for (int y = SRC_Y0; y <= SRC_Y1; y++) {
                for (int z = SRC_Z0; z <= SRC_Z1; z++) {
                    if (y < ParkPlan.FLOOR_HALL - 1 && x >= Lower.TUNNEL_X0) continue;
                    B b = p.block(x, y, z);
                    if (b == null) continue;
                    B to = damage(b, x, y, z);
                    if (to == null) continue;
                    put.add(new int[]{x + DX, y + DY, z});
                    what.add(to);
                }
            }
        }
        for (int i = 0; i < put.size(); i++) {
            int[] q = put.get(i);
            p.set(q[0], q[1], q[2], what.get(i));
        }
        for (Box b : new ArrayList<>(p.boxes)) {
            if (!inSource(b.x(), b.y(), b.z())) continue;
            if (rnd.nextDouble() < 0.18) continue;
            String block = b.block().contains("froglight") || b.block().contains("shroomlight") || b.block().contains("sea_lantern") ? "minecraft:gray_concrete" : b.block();
            p.boxes.add(new Box(block, b.x() + DX, b.y() + DY, b.z(), b.sx(), b.sy(), b.sz(), -1));
        }
        for (Text t : new ArrayList<>(p.texts)) {
            if (!inSource(t.x(), t.y(), t.z())) continue;
            // 门头那几个大字倒了一地，不照搬
            if (t.scale() >= 3) continue;
            p.texts.add(new Text(t.text(), t.x() + DX, t.y() + DY, t.z(), t.yaw(), dim(t.color()), t.scale(), t.background(), Math.min(t.glow(), 3)));
        }
        for (Beam b : new ArrayList<>(p.beams)) {
            if (!inSource(b.ax(), b.ay(), b.az()) || !inSource(b.bx(), b.by(), b.bz())) continue;
            if (b.block().contains("light") || b.block().contains("lantern")) continue;
            p.beams.add(new Beam(b.block(), b.ax() + DX, b.ay() + DY, b.az(), b.bx() + DX, b.by() + DY, b.bz(), b.thick(), -1));
        }
        for (Parts.Panel b : new ArrayList<>(p.panels)) {
            if (!inSource(b.ax(), b.ay(), b.az()) || !inSource(b.bx(), b.by(), b.bz())) continue;
            if (b.block().contains("light") || b.block().contains("lantern")) continue;
            if (rnd.nextDouble() < 0.12) continue;
            p.panels.add(new Parts.Panel(b.block(), b.ax() + DX, b.ay() + DY, b.az(), b.bx() + DX, b.by() + DY, b.bz(), b.h(), b.d(), -1));
        }
        for (Parts.Poster b : new ArrayList<>(p.posters)) {
            if (!inSource(b.x(), b.y(), b.z())) continue;
            p.posters.add(new Parts.Poster(b.model(), b.x() + DX, b.y() + DY, b.z(), b.yaw(), b.w(), b.h(), -1));
        }
        for (Parts.Flat f : new ArrayList<>(p.flats)) {
            if (!inSource(f.x(), f.y(), f.z())) continue;
            p.flats.add(new Parts.Flat(f.item(), f.x() + DX, f.y() + DY, f.z(), f.angle(), f.scale()));
        }
        // 楼梯口那条警戒线后面的墙：第一关停电时插件才拆，这座里一开始就没有
        for (int z = 5; z <= 7; z++) for (int y = REAL_FLOOR; y <= REAL_FLOOR + 1; y++) {
            if (p.block(ParkPlan.STAIR_TOP_X + DX, y, z) == B.BARRIER) p.clear(ParkPlan.STAIR_TOP_X + DX, y, z);
        }
        // 门头的大字倒了一地
        double face = ParkPlan.DRUM_X - ParkPlan.DRUM_R - 1.25 + DX;
        String[] letters = {"M", "Y", "S", "T", "E", "R", "Y", "F", "L", "E", "S", "H", "P", "I", "T"};
        for (int i = 0; i < letters.length; i++) {
            double z = -9 + i * 1.4 + rnd.nextDouble() * 0.5, x = face - 1.5 - rnd.nextDouble() * 3;
            p.texts.add(new Text(letters[i], x, REAL_FLOOR + 0.05, z, (float) (rnd.nextDouble() * 360), 0x8a8270, 2.2f, 0, 2));
        }
        // 礼品店门口堆着那晚没放的烟花：晚会九点开始，箱子还没拆
        double cx = 113.4 + DX, cz = 1.4;
        for (int i = 0; i < 5; i++) {
            p.boxes.add(new Box("minecraft:red_terracotta", cx + (i % 3) * 0.72, REAL_FLOOR + (i / 3) * 0.52, cz + (i % 2) * 0.08, 0.68, 0.5, 0.52, -1));
        }
        p.texts.add(new Text("独立日烟花  ·  易燃\n勿近明火", cx + 1.08, REAL_FLOOR + 1.3, cz + 0.62, 0f, 0xe8dcc0, 0.3f, 0, 2));
        // 低的一头泡在水里：窗边那半边大厅、整个花园层
        p.pools.add(new Parts.Pool("pool_water", ParkPlan.HX0 + DX + 4, 5, ParkPlan.HX1 + DX, ParkPlan.HZ1, REAL_FLOOR + 0.12));
        p.pools.add(new Parts.Pool("pool_water", 110 + DX, -14, 152 + DX, 12, REAL_GARDEN + 0.35));
        // 码头和大厅之间：码头那边的看不见的墙拆了（damage 里没搬）。码头北边的轨道槽是井底地面，可以走上去
        for (int x = 95 + DX; x < ParkPlan.HX0 + DX; x++) walk.add(new double[]{x + 0.5, REAL_FLOOR, 4.5});
    }

    private static boolean inSource(double x, double y, double z) {
        if (y < ParkPlan.FLOOR_HALL - 1 && x >= Lower.TUNNEL_X0) return false;
        return x >= SRC_X0 && x <= SRC_X1 + 1 && y >= SRC_Y0 && y <= SRC_Y1 + 1 && z >= SRC_Z0 && z <= SRC_Z1 + 1;
    }

    private static int dim(int rgb) {
        int r = (rgb >> 16 & 255) * 3 / 4, g = (rgb >> 8 & 255) * 3 / 4, b = (rgb & 255) * 3 / 4;
        return r << 16 | g << 8 | b;
    }

    /** 照搬过去的一格：灯全灭，玻璃碎掉一大半，墙上破洞、发黄，地上一层碎东西。null 是这格不要。 */
    private B damage(B b, int x, int y, int z) {
        switch (b) {
            case LIGHT_3, LIGHT_5, LIGHT_7, LIGHT_9, LIGHT_15 -> {
                return null;
            }
            case BARRIER -> {
                // 码头那头的墙拆掉，人要从井底走上去
                return x < ParkPlan.HX0 + 1 ? null : b;
            }
            case GLASS_X, SHOP_GLASS, HULL_GLASS, GLASS_Z -> {
                return rnd.nextDouble() < 0.55 ? null : b;
            }
            case HALL_WALL, TILE_A, WALL_A, WALL_B -> {
                if (y > ParkPlan.FLOOR_HALL + 1 && rnd.nextDouble() < 0.05) return null;
                return rnd.nextDouble() < 0.35 ? (b == B.HALL_WALL ? B.HALL_WALL_DIRTY : B.TILE_STAIN) : b;
            }
            case TERRAZZO, TERRAZZO_DARK, TILE_B, PLATFORM -> {
                return rnd.nextDouble() < 0.2 ? B.TILE_STAIN : b;
            }
            case HALL_CEIL -> {
                return rnd.nextDouble() < 0.1 ? null : rnd.nextDouble() < 0.4 ? B.HALL_WALL_DIRTY : b;
            }
            default -> {
                return b;
            }
        }
    }

    /** 花园层东头原来通隧道的口子：隧道没跟着楼掉下来，口子被肉堵死了。 */
    private void sealTunnel() {
        int f = REAL_GARDEN;
        for (int x = ROOM_X0 - 1; x <= ROOM_X0 + 1; x++) {
            for (int z = -2; z <= 2; z++) for (int y = f; y <= f + 3; y++) p.set(x, y, z, x == ROOM_X0 - 1 && Math.abs(z) < 2 ? B.FLESH_DARK : B.FLESH_MID);
        }
    }

    // ---------- 井口和井壁 ----------

    /**
     * 井口：上面那座楼的底。没有地基，是一大把往下垂的肉根；楼板、管道、楼梯的形状都是肉长成的，人看不见的地方没长完。
     * 肉根是肉腔模型的一部分（Chamber.well），这里只摆几块还没被包住的楼板和管子，缝里漏下来上面的灯光。
     */
    private void lid() {
        int y = TOP;
        double r = 27;
        // 钢梁的格子：楼板底下的结构，一格一格，被肉根吃掉一些
        for (int x = (int) (SX - r); x <= SX + r; x++) {
            for (int z = (int) (SZ - r); z <= SZ + r; z++) {
                double d = Math.hypot(x + 0.5 - SX, z + 0.5 - SZ);
                if (d > r) continue;
                boolean beam = Math.floorMod(x - SX, 6) == 0 || Math.floorMod(z - SZ, 6) == 0;
                if (beam && rnd.nextDouble() > 0.12) p.set(x, y - 2, z, B.STEEL);
                // 楼板：白色的，碎了不少洞，洞里漏下上面那座的灯
                double hole = Noise.fbm(x * 0.17, z * 0.17, 41, 2);
                if (hole > 0.62) {
                    if (rnd.nextDouble() < 0.25) p.light(x, y - 1, z, 15);
                    if (rnd.nextDouble() < 0.08) p.boxes.add(new Box("minecraft:ochre_froglight", x + 0.25, y - 0.6, z + 0.25, 0.5, 0.1, 0.5, 15));
                } else {
                    p.set(x, y - 1, z, rnd.nextDouble() < 0.3 ? B.HALL_WALL_DIRTY : B.HALL_WALL);
                }
            }
        }
        // 管子沿着钢梁走
        for (int i = 0; i < 10; i++) {
            double a = rnd.nextDouble() * Math.PI * 2, rr = 4 + rnd.nextDouble() * 20;
            double x = SX + Math.cos(a) * rr, z = SZ + Math.sin(a) * rr;
            p.pipes.add(new Parts.Pipe(rnd.nextInt(3), x, y - 3.2, z, x + (rnd.nextDouble() - 0.5) * 18, y - 2.6 - rnd.nextDouble() * 2, z + (rnd.nextDouble() - 0.5) * 18, 0.25));
        }
        // 一道楼梯从楼板底下伸出来，只长了一半，没长到地方
        for (int k = 0; k < 8; k++) {
            int x = SX + 6 + k, yy = y - 3 - k;
            for (int z = SZ - 9; z <= SZ - 7; z++) p.set(x, yy, z, B.STAIR_E);
        }
        // 一扇窗的形状：楼上那座的灯从里面透出来
        for (int x = SX - 14; x <= SX - 9; x++) for (int yy = y - 4; yy <= y - 3; yy++) p.set(x, yy, SZ + 12, B.SHOP_GLASS);
        p.light(SX - 11, y - 3, SZ + 13, 12);
    }

    /** 井壁上一圈六根支撑的断头。4 号就是那根斜下去的桁架。 */
    private void stumps() {
        int y = CAB_Y + 3;
        for (int n = 1; n <= 6; n++) {
            if (n == 4) {
                p.texts.add(new Text("4", LEDGE_X0 + 0.04, CAB_Y + 2.6, PATH_Z1 - 0.2, -90f, 0xffe050, 1.2f, 0, 5));
                continue;
            }
            double a = Math.PI + (n - 4) * Math.PI / 3;
            double r = radius(y) - 1.5, x = SX + Math.cos(a) * r, z = SZ + Math.sin(a) * r;
            p.boxes.add(new Box("minecraft:polished_deepslate", x - 1.2, y - 1.2, z - 1.2, 2.4, 2.4, 2.4, -1));
            p.texts.add(new Text(String.valueOf(n), x, y + 1.6, z, (float) Math.toDegrees(-a) - 90, 0xffe050, 1.2f, 0, 4));
        }
    }

    private void paths() {
        for (int x = LEDGE_X0; x <= LEDGE_X1; x++) walk.add(new double[]{x + 0.5, CAB_Y, -19.5});
        for (int i = 0; i < STEPS; i++) walk.add(new double[]{TRUSS_X0 + i + 0.5, stepY(i) + 0.5, -19.5});
        for (int x = FOOT_X0; x <= FOOT_X1; x++) walk.add(new double[]{x + 0.5, FOOT_Y, -19.5});
        for (int y = BAG_Y; y <= FOOT_Y; y += 2) walk.add(new double[]{LADDER_X + 0.5, y, LADDER_Z + 0.5});
        for (int x = BAG_X0; x <= BAG_X1; x++) walk.add(new double[]{x + 0.5, BAG_Y, -19.5});
        for (int x = LAND_X0; x <= LAND_X1; x += 2) walk.add(new double[]{x + 0.5, REAL_FLOOR, -35.5});
        // 落脚处绕过真楼西边走到码头
        double[][] around = {{488, -32}, {478, -20}, {474, -8}, {476, 0}, {480, 3}};
        for (double[] q : around) walk.add(new double[]{q[0], REAL_FLOOR, q[1]});
    }

    // ---------- 小工具 ----------

    private void fill(int x0, int y0, int z0, int x1, int y1, int z1, B b) {
        p.fill(x0, y0, z0, x1, y1, z1, b);
    }

    /** 看不见的墙：两格高。已经有方块的格子不动。 */
    private void wall(int x, int y, int z) {
        for (int yy = y; yy <= y + 1; yy++) if (p.block(x, yy, z) == null) p.set(x, yy, z, B.BARRIER);
    }

    /** 平台边上的栏杆。 */
    private void rail(int x, int y, int... zs) {
        for (int z : zs) if (p.block(x, y, z) == null) p.set(x, y, z, B.BARS_X);
    }

    /** 人在不在井里（第四关）或井底的楼里（第五关）。 */
    public static boolean inWell(double x, double y, double z) {
        return x >= SX - 52 && x <= SX + 52 && z >= SZ - 52 && z <= SZ + 52 && y > REAL_GARDEN - 6;
    }
}
