package world.hechao.fleshpark.core;

import world.hechao.fleshpark.core.Parts.Beam;
import world.hechao.fleshpark.core.Parts.Box;
import world.hechao.fleshpark.core.Parts.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 游客中心下面：放松花园层 → 加固隧道 → 更衣室（两排柜子，A01–A19、B01–B19）→ 三级浴池 → 腺泡洞口。
 * 再远一点是腺泡内壁，人爬着穿过去，黑暗里接到支气管森林。
 */
public final class Lower {
    public static final int GARDEN_FLOOR = 20, GARDEN_CEIL = 26;
    public static final int TUNNEL_X0 = 153, TUNNEL_X1 = 164, LOCKER_X0 = 165, LOCKER_X1 = 180;
    public static final int POOL1_X = 182, POOL2_X = 194, POOL3_X = 206, POOL_END_X = 228;
    public static final int POOL1_FLOOR = 19, POOL2_FLOOR = 17, POOL3_FLOOR = 15;
    public static final double SURFACE = 0.85;
    /** 真正能爬进去的那个腺泡。 */
    public static final double[] HOLE = {214, POOL3_FLOOR + 1.0, -16.3};
    /** 人形那一排的 x。 */
    public static final double ROW_X = 224.5;
    public static final int FIGURES = 37;
    /** 腺泡内壁：x 从 BURROW_X0 到 BURROW_X1，一格高的通道。 */
    public static final int BURROW_X0 = 270, BURROW_X1 = 292, BURROW_Y = 178, BURROW_Z = -66;

    /** 38 个柜子的位置：x、z、朝向。北排 A01–A19，南排 B01–B19；最后一个 B19 是开着的。 */
    public final List<double[]> lockers = new ArrayList<>();

    private final ParkPlan p;
    private final Random rnd;

    Lower(ParkPlan plan) {
        p = plan;
        rnd = plan.rnd;
    }

    void build() {
        garden();
        tunnel();
        lockerRoom();
        pools();
        burrow();
        // 每一层边上的员工房间，最后建：肉壁往外包的时候已经包完了
        firstAid();
        gardenShed();
        broadcastRoom();
        showers();
        lifeguardRoom();
    }

    /** 员工小屋：登记成室内，再交给 ParkPlan 铺。 */
    private void staffRoom(int x0, int z0, int x1, int z1, int floor, int ceil) {
        interiors.add(new int[]{x0, z0, x1, z1, floor, ceil - 1});
        p.office(x0, z0, x1, z1, floor, ceil);
    }

    /** 门洞：从 x0 到 x1、z0 到 z1，三格高，门框两根深色立柱。 */
    private void doorway(int x0, int z0, int x1, int z1, int floor) {
        p.clearBox(x0, floor, z0, x1, floor + 2, z1);
    }

    /** 花园层医务站：一张病床、输液架、登记桌。 */
    private void firstAid() {
        int y = GARDEN_FLOOR;
        staffRoom(118, -13, 125, -9, y, y + 4);
        doorway(121, -8, 122, -7, y);
        p.texts.add(new Text("＋  医务站", 121.95, y + 3.2, -6.05, 180f, 0xffffff, 0.8f, 0xffa02818, 12));
        p.boxes.add(new Box("minecraft:white_wool", 118.2, y, -12.9, 1.0, 0.62, 2.0, -1));
        p.boxes.add(new Box("minecraft:light_gray_wool", 118.25, y + 0.62, -12.85, 0.9, 0.12, 0.6, -1));
        p.beams.add(new Parts.Beam("minecraft:iron_block", 119.5, y, -10.6, 119.5, y + 1.9, -10.6, 0.04, -1));
        p.boxes.add(new Box("minecraft:light_blue_stained_glass", 119.38, y + 1.6, -10.65, 0.24, 0.3, 0.1, -1));
        p.boxes.add(new Box("minecraft:white_concrete", 120.0, y, -13.0, 3.0, 0.93, 1.0, -1));
        p.boxes.add(new Box("minecraft:red_concrete", 124.6, y + 0.93, -12.8, 0.3, 0.2, 0.2, -1));
        p.boxes.add(new Box("minecraft:white_concrete", 124.1, y, -9.95, 0.9, 1.3, 0.9, -1));
        p.texts.add(new Text("候诊请排队\n今日已接诊  41", 125.94, y + 2.0, -11.0, 90f, 0x24211f, 0.45f, 0xffe6dcc8, 6));
        p.light(121, y + 3, -11, 6);
    }

    /** 园艺工具间兼失物招领：工作台、工具架、一排招领箱。 */
    private void gardenShed() {
        int y = GARDEN_FLOOR;
        staffRoom(139, -13, 146, -9, y, y + 4);
        doorway(142, -8, 143, -7, y);
        p.texts.add(new Text("园艺工具间  ·  失物招领", 142.95, y + 3.2, -6.05, 180f, 0xf2e8cc, 0.55f, 0xff2c4a3a, 9));
        p.boxes.add(new Box("minecraft:spruce_planks", 140.3, y, -13.0, 5.8, 0.93, 1.0, -1));
        for (int i = 0; i < 3; i++) p.boxes.add(new Box("minecraft:brown_wool", 143.0 + i * 0.5, y + 0.93, -12.9, 0.42, 0.3, 0.42, -1));
        p.beams.add(new Parts.Beam("minecraft:spruce_planks", 139.2, y, -9.4, 139.6, y + 1.6, -9.6, 0.05, -1));
        p.beams.add(new Parts.Beam("minecraft:iron_block", 139.6, y + 1.6, -9.6, 139.7, y + 1.7, -9.9, 0.08, -1));
        p.set(139, y, -11, B.SHELF);
        p.set(139, y + 1, -11, B.SHELF);
        p.set(146, y, -9, B.SHELF);
        p.boxes.add(new Box("minecraft:white_concrete", 140.4, y + 0.93, -12.9, 0.5, 0.25, 0.35, -1));
        p.texts.add(new Text("失物招领\n左手手套  37\n右手手套   0", 146.94, y + 1.9, -11.0, 90f, 0x24211f, 0.45f, 0xffe6dcc8, 6));
        p.light(142, y + 3, -11, 5);
    }

    /** 园区广播室：隧道边上一扇门。调音台、开盘录音机，播音中的红灯还亮着。 */
    private void broadcastRoom() {
        int y = GARDEN_FLOOR;
        staffRoom(156, -6, 161, -3, y, y + 4);
        doorway(158, -2, 159, -1, y);
        p.boxes.add(new Box("minecraft:red_concrete", 158.2, y + 2.6, -0.95, 1.6, 0.3, 0.05, 15));
        p.texts.add(new Text("播音中", 159.0, y + 2.63, -0.88, 0f, 0xffffff, 0.5f, 0, 15));
        p.boxes.add(new Box("minecraft:polished_deepslate", 156.2, y, -6.0, 5.6, 0.93, 1.0, -1));
        p.boxes.add(new Box("minecraft:gray_concrete", 157.0, y + 0.93, -5.9, 1.2, 0.1, 0.6, -1));
        for (int i = 0; i < 6; i++) p.boxes.add(new Box(i % 2 == 0 ? "minecraft:white_concrete" : "minecraft:red_concrete", 157.1 + i * 0.18, y + 1.03, -5.55, 0.06, 0.05, 0.12, 9));
        // 开盘录音机：两个盘还在转（转不动，是看起来）
        p.boxes.add(new Box("minecraft:polished_blackstone", 159.4, y + 0.93, -5.95, 0.9, 0.7, 0.3, -1));
        p.boxes.add(new Box("minecraft:black_concrete", 159.5, y + 1.25, -5.66, 0.3, 0.3, 0.02, -1));
        p.boxes.add(new Box("minecraft:black_concrete", 159.92, y + 1.25, -5.66, 0.3, 0.3, 0.02, -1));
        p.beams.add(new Parts.Beam("minecraft:iron_block", 157.6, y + 0.93, -5.6, 157.8, y + 1.45, -5.2, 0.02, -1));
        p.boxes.add(new Box("minecraft:polished_deepslate", 157.72, y + 1.42, -5.28, 0.14, 0.14, 0.14, -1));
        p.light(158, y + 3, -4, 4);
    }

    /** 淋浴间：更衣室北墙开门，六个隔间，四号的门是撬开的。 */
    private void showers() {
        int y = GARDEN_FLOOR;
        staffRoom(169, -12, 176, -9, y, y + 4);
        doorway(173, -8, 173, -7, y);
        for (int i = 0; i <= 6; i++) {
            double x = 169.05 + i * 1.3;
            p.boxes.add(new Box("minecraft:white_terracotta", x, y, -12.95, 0.06, 2.1, 1.5, -1));
        }
        for (int i = 0; i < 6; i++) {
            double x = 169.6 + i * 1.3;
            p.boxes.add(new Box("minecraft:iron_block", x, y + 2.2, -12.9, 0.18, 0.06, 0.3, -1));
            // 四号隔间的门板撬下来扔在地上
            if (i == 3) p.boxes.add(new Box("minecraft:light_gray_concrete", x - 0.6, y, -11.0, 1.2, 0.05, 1.8, -1));
            else p.boxes.add(new Box("minecraft:light_gray_concrete", x - 0.55, y + 0.15, -11.45, 1.18, 1.85, 0.05, -1));
        }
        p.boxes.add(new Box("minecraft:black_concrete", 173.5, y, -12.3, 0.3, 0.02, 0.3, -1));
        p.light(172, y + 3, -10, 5);
    }

    /** 救生员室：一号池北墙开门。值班桌、一把高椅、墙上的人数登记板。 */
    private void lifeguardRoom() {
        int y = POOL1_FLOOR;
        staffRoom(184, -13, 190, -10, y, y + 4);
        doorway(186, -9, 187, -7, y);
        p.texts.add(new Text("救生员室", 186.95, y + 3.0, -6.06, 180f, 0xffffff, 0.6f, 0xffa02818, 12));
        p.boxes.add(new Box("minecraft:white_concrete", 185.6, y, -13.0, 3.0, 0.93, 1.0, -1));
        p.beams.add(new Parts.Beam("minecraft:red_concrete", 184.5, y, -11.0, 184.5, y + 1.6, -11.0, 0.06, -1));
        p.boxes.add(new Box("minecraft:red_concrete", 184.2, y + 1.6, -11.3, 0.6, 0.08, 0.6, -1));
        p.texts.add(new Text("三号池在池人数\n14:00  12\n16:00  35\n17:00  36\n——  ——", 190.94, y + 2.0, -11.5, 90f, 0x24211f, 0.45f, 0xffe6dcc8, 6));
        p.light(187, y + 3, -11, 6);
    }

    /** 一间屋子：地、顶、四面墙，墙由 wall 决定。 */
    /** 已经建好的房间的室内。肉壁往外包的时候不能把隔壁的房间填上。 */
    private final List<int[]> interiors = new ArrayList<>();

    private boolean insideRoom(int x, int y, int z) {
        for (int[] r : interiors) {
            if (x >= r[0] && x <= r[2] && z >= r[1] && z <= r[3] && y >= r[4] && y <= r[5]) return true;
        }
        return false;
    }

    private void room(int x0, int z0, int x1, int z1, int floor, int ceil, WallStyle wall) {
        interiors.add(new int[]{x0, z0, x1, z1, floor, ceil - 1});
        // 肉壁要有厚度，曲面才算得出来：外面再包两层
        if (wall != OFFICE) {
            for (int x = x0 - 3; x <= x1 + 3; x++) {
                for (int z = z0 - 3; z <= z1 + 3; z++) {
                    for (int y = floor - 3; y <= ceil + 2; y++) {
                        boolean shell = x < x0 - 1 || x > x1 + 1 || z < z0 - 1 || z > z1 + 1 || y < floor - 1 || y > ceil;
                        if (shell && p.block(x, y, z) == null && !insideRoom(x, y, z)) p.set(x, y, z, ParkPlan.flesh(x, y, z));
                    }
                }
            }
        }
        for (int x = x0 - 1; x <= x1 + 1; x++) {
            for (int z = z0 - 1; z <= z1 + 1; z++) {
                boolean edge = x == x0 - 1 || x == x1 + 1 || z == z0 - 1 || z == z1 + 1;
                p.set(x, floor - 1, z, wall.floor(x, z));
                p.set(x, ceil, z, wall.ceiling(x, z));
                for (int y = floor; y < ceil; y++) {
                    if (edge) p.set(x, y, z, wall.wall(x, y, z, y - floor));
                    else p.clear(x, y, z);
                }
            }
        }
    }

    private interface WallStyle {
        B floor(int x, int z);

        B ceiling(int x, int z);

        B wall(int x, int y, int z, int h);
    }

    private static final WallStyle OFFICE = new WallStyle() {
        @Override
        public B floor(int x, int z) {
            return ((x + z) & 1) == 0 ? B.TERRAZZO : B.TERRAZZO_DARK;
        }

        @Override
        public B ceiling(int x, int z) {
            return B.HALL_CEIL;
        }

        @Override
        public B wall(int x, int y, int z, int h) {
            return h <= 1 ? B.WALL_STRIP : h == 2 ? B.TILE_B : B.TILE_A;
        }
    };

    private static final WallStyle FLESHY = new WallStyle() {
        @Override
        public B floor(int x, int z) {
            return Noise.fbm(x * 0.2, 1, z * 0.2, 2) > 0.55 ? ParkPlan.flesh(x, 0, z) : B.TILE_B;
        }

        @Override
        public B ceiling(int x, int z) {
            return ParkPlan.flesh(x, 30, z);
        }

        @Override
        public B wall(int x, int y, int z, int h) {
            // 下面一整圈瓷砖墙裙，上面是长满的肉。瓷砖不能东一块西一块，不然肉面一抹圆，它们就成了飘着的方块
            return h <= 1 ? B.TILE_A : ParkPlan.flesh(x, y, z);
        }
    };

    private static final WallStyle MEAT = new WallStyle() {
        @Override
        public B floor(int x, int z) {
            return ParkPlan.flesh(x, 3, z);
        }

        @Override
        public B ceiling(int x, int z) {
            return ParkPlan.flesh(x, 40, z);
        }

        @Override
        public B wall(int x, int y, int z, int h) {
            return ParkPlan.flesh(x, y, z);
        }
    };

    private void garden() {
        int y = GARDEN_FLOOR;
        // 楼梯从顶上穿下来：别把它铺掉
        List<long[]> keep = new ArrayList<>();
        room(116, -6, 152, 11, y, GARDEN_CEIL, OFFICE);
        for (int z = 5; z <= 7; z++) {
            for (int x = ParkPlan.STAIR_TOP_X; x >= ParkPlan.STAIR_TOP_X - 9; x--) {
                int sy = ParkPlan.FLOOR_HALL - 1 - (ParkPlan.STAIR_TOP_X - x);
                for (int yy = sy; yy <= Math.max(GARDEN_CEIL, sy); yy++) p.clear(x, yy, z);
                p.set(x, sy, z, B.STAIR_E);
            }
        }
        for (int x = ParkPlan.STAIR_TOP_X; x >= ParkPlan.STAIR_TOP_X - 9; x--) {
            int sy = ParkPlan.FLOOR_HALL - 1 - (ParkPlan.STAIR_TOP_X - x);
            for (int z : new int[]{4, 8}) {
                for (int yy = Math.max(sy + 1, y); yy <= Math.min(sy + 2, GARDEN_CEIL - 1); yy++) p.set(x, yy, z, B.BARRIER);
                p.clear(x, GARDEN_CEIL, z - (z == 4 ? 0 : 0));
                if (sy >= GARDEN_CEIL - 2) p.set(x, GARDEN_CEIL, z, B.WALL_A);
            }
        }
        // 花槽：土里长出来的不是草
        for (int i = 0; i < 5; i++) {
            int x = 120 + i * 6, z = i % 2 == 0 ? -3 : 9;
            p.fill(x, y, z, x + 3, y, z, B.WOOD_DARK);
            // 一槽长满了：细的肉茎往外歪，顶上像蕨菜一样卷起来；有几根耷拉出槽沿
            for (int k = 0; k < 20; k++) {
                double sx = x + 0.15 + rnd.nextDouble() * 3.7, sz = z + 0.15 + rnd.nextDouble() * 0.7;
                double h = 0.5 + rnd.nextDouble() * 0.9, angle = rnd.nextDouble() * Math.PI * 2;
                boolean droop = k % 6 == 0;
                if (droop) angle = (z < 0 ? Math.PI / 2 : -Math.PI / 2) + rnd.nextDouble() * 0.6 - 0.3;
                p.lateRope(fiddlehead(sx, y + 1.0, sz, h, angle, droop ? 0.5 : 0.08 + rnd.nextDouble() * 0.3, 0.12 + rnd.nextDouble() * 0.08, droop), 0.06 + rnd.nextDouble() * 0.05, k % 3 == 0 ? 1 : 0, 0.05);
            }
        }
        // 红色野餐桌
        for (double[] t : new double[][]{{124, 3}, {129, 0}, {122, -1}, {144, -2}, {147, 7}, {141, 1}}) {
            p.boxes.add(new Box("minecraft:red_concrete", t[0], y + 0.72, t[1], 1.5, 0.08, 1.5, -1));
            p.boxes.add(new Box("minecraft:polished_deepslate", t[0] + 0.68, y, t[1] + 0.68, 0.14, 0.72, 0.14, -1));
            p.boxes.add(new Box("minecraft:red_concrete", t[0] - 0.4, y + 0.42, t[1] + 0.5, 0.3, 0.06, 0.5, -1));
            p.boxes.add(new Box("minecraft:red_concrete", t[0] + 1.6, y + 0.42, t[1] + 0.5, 0.3, 0.06, 0.5, -1));
            p.boxes.add(new Box("minecraft:polished_deepslate", t[0] - 0.3, y, t[1] + 0.7, 0.08, 0.42, 0.08, -1));
            p.boxes.add(new Box("minecraft:polished_deepslate", t[0] + 1.72, y, t[1] + 0.7, 0.08, 0.42, 0.08, -1));
        }
        p.texts.add(new Text("休闲花园层", 134, y + 3.6, -5.95, 0f, 0xf2e8cc, 1.6f, 0xff1b3a3a, 8));
        gardenLife(y);
        p.texts.add(new Text("→ 羊膜温泉", 152.97, y + 3.2, 1.5, 90f, 0xffd8a0, 1.2f, 0xff24211f, 13));
        p.light(150, y + 3, 1, 7);
        p.light(132, y + 4, 2, 5);
        p.light(121, y + 4, 4, 3);
        // 肉从顶上渗下来
        p.rope(List.of(new double[]{127, GARDEN_CEIL + 0.5, 6}, new double[]{127.6, GARDEN_CEIL - 1.5, 6.4}, new double[]{127.2, y + 2.2, 7.2}, new double[]{127.8, y + 0.4, 8}), 0.3, 0, 0.4, false);
        p.rope(List.of(new double[]{146, GARDEN_CEIL + 0.5, -3}, new double[]{146.5, y + 3, -3.4}, new double[]{147.5, y + 0.6, -3.8}), 0.26, 1, 0.35, false);
    }

    /**
     * 一根卷起来的肉茎：从 (x,y,z) 长出来，朝 angle 歪 lean 米，长到 h 高，顶上向前卷一圈半、越卷越紧。
     * droop 的那种长得矮、歪得多，卷头垂到槽沿外面。
     */
    private static List<double[]> fiddlehead(double x, double y, double z, double h, double angle, double lean, double curl, boolean droop) {
        double dx = Math.cos(angle), dz = Math.sin(angle);
        if (droop) h *= 0.55;
        List<double[]> path = new ArrayList<>();
        for (int i = 0; i <= 4; i++) {
            double t = i / 4.0, bend = lean * t * t;
            path.add(new double[]{x + dx * bend, y + h * t, z + dz * bend});
        }
        double[] top = path.get(path.size() - 1);
        double cx = top[0] + dx * curl, cz = top[2] + dz * curl;
        for (int i = 1; i <= 9; i++) {
            double th = i / 9.0 * Math.PI * (droop ? 1.4 : 1.6), r = curl * (1 - 0.4 * th / (2 * Math.PI));
            double out = -Math.cos(th) * r, up = Math.sin(th) * r;
            path.add(new double[]{cx + dx * out, top[1] + up - (droop ? th * 0.06 : 0), cz + dz * out});
        }
        return path;
    }

    private void tunnel() {
        int y = GARDEN_FLOOR, z0 = 0, z1 = 2;
        p.clearBox(153, y, z0, 153, y + 2, z1);
        for (int x = TUNNEL_X0; x <= TUNNEL_X1; x++) {
            for (int z = z0 - 1; z <= z1 + 1; z++) {
                p.set(x, y - 1, z, B.GRATE);
                p.set(x, y + 3, z, x % 3 == 0 ? B.STEEL : ParkPlan.flesh(x, y + 3, z));
                for (int yy = y; yy <= y + 2; yy++) {
                    if (z == z0 - 1 || z == z1 + 1) p.set(x, yy, z, x % 3 == 0 ? B.STEEL : ParkPlan.flesh(x, yy, z));
                    else p.clear(x, yy, z);
                }
            }
        }
        p.light(158, y + 2, 1, 3);
        p.rope(List.of(new double[]{161, y + 3.2, 0.4}, new double[]{160.2, y + 2.2, 0.3}, new double[]{159.4, y + 0.8, 0.3}), 0.2, 0, 0.3, false);
    }

    private void lockerRoom() {
        int y = GARDEN_FLOOR;
        room(LOCKER_X0, -6, LOCKER_X1, 8, y, y + 5, OFFICE);
        p.clearBox(LOCKER_X0 - 1, y, 0, LOCKER_X0 - 1, y + 2, 2);
        p.clearBox(LOCKER_X1 + 1, y, 0, LOCKER_X1 + 1, y + 2, 2);
        // 两排柜子，一排 19 个
        for (int i = 0; i < 38; i++) {
            boolean north = i < 19;
            // 北排给淋浴间让出一扇门：21 个格子，第 10、11 格空着
            int slot = north && i >= 10 ? i + 2 : i % 19;
            double x = north ? LOCKER_X0 + 0.02 + slot * 0.76 : LOCKER_X0 + 0.3 + (i % 19) * 0.8, z = north ? -6 : 8.4;
            lockers.add(new double[]{x, z, north ? 0 : 180});
            if (i == 37) continue;
            p.boxes.add(new Box("minecraft:waxed_oxidized_copper", x, y, z, 0.74, 2.3, 0.6, -1));
            p.texts.add(new Text((north ? "A" : "B") + String.format("%02d", i % 19 + 1), x + 0.37, y + 1.8, north ? z + 0.62 : z - 0.02, north ? 0f : 180f, 0x24211f, 0.5f, 0, -1));
        }
        // B19 开着，门牌写着体验官。2007 年那张存柜单上，B19 没有登记过任何人
        double[] open = lockers.get(37);
        double ox = open[0], oz = open[1];
        p.boxes.add(new Box("minecraft:polished_deepslate", ox, y, oz + 0.1, 0.74, 2.3, 0.5, -1));
        p.boxes.add(new Box("minecraft:waxed_oxidized_copper", ox - 0.03, y, oz - 0.7, 0.05, 2.3, 0.74, -1));
        p.boxes.add(new Box("minecraft:white_wool", ox + 0.1, y + 1.15, oz + 0.08, 0.54, 0.16, 0.3, 9));
        p.texts.add(new Text("体验官", ox + 0.37, y + 1.86, oz + 0.08, 180f, 0xffe8c0, 0.62f, 0xff24211f, 12));
        p.fill(170, y, -2, 175, y, -2, B.BENCH);
        p.fill(170, y, 5, 175, y, 5, B.BENCH);
        p.light(173, y + 3, 1, 5);
        p.light((int) ox, y + 2, 6, 7);
        p.texts.add(new Text("更衣室  ·  入池前请先淋浴", 173, y + 3.1, -5.95, 0f, 0x4a4438, 0.9f, 0, -1));
    }

    /** 两池之间的门槛：x 是门洞那一列，upper、lower 是两边池底。门槛顶比上一池池底高一格，水面比它低。 */
    private void weir(int x, int upper, int lower) {
        for (int z = 0; z <= 2; z++) {
            p.set(x - 1, upper, z, B.WALK_B);
            for (int y = lower; y <= upper; y++) p.set(x, y, z, B.TILE_B);
            for (int y = upper + 1; y <= upper + 3; y++) p.clear(x, y, z);
            // 下一池那边的台阶：每一级矮一格，最后落到池底
            for (int step = 1; upper - step >= lower; step++) {
                for (int y = lower; y <= upper - step; y++) p.set(x + step, y, z, B.TILE_B);
            }
        }
    }

    private void pools() {
        // 第一池还是瓷砖的，越往下肉越多
        room(POOL1_X, -6, POOL2_X - 2, 8, POOL1_FLOOR, 25, FLESHY);
        p.clearBox(POOL1_X - 1, POOL1_FLOOR + 1, 0, POOL1_X - 1, POOL1_FLOOR + 3, 2);
        room(POOL2_X, -8, POOL3_X - 2, 10, POOL2_FLOOR, 25, FLESHY);
        room(POOL3_X, -16, POOL_END_X, 16, POOL3_FLOOR, 26, MEAT);
        // 池子之间的门洞：一道比上一池水面高的门槛，水才兜得住。上一池这边一级台阶上门槛，下一池那边一级一级下到池底
        weir(POOL2_X - 1, POOL1_FLOOR, POOL2_FLOOR);
        weir(POOL3_X - 1, POOL2_FLOOR, POOL3_FLOOR);
        // 水边的壁灯：暖光贴着水面，水才有东西可以倒映
        for (int[] pool : new int[][]{{POOL1_X, POOL2_X - 2, -6, 8, POOL1_FLOOR}, {POOL2_X, POOL3_X - 2, -8, 10, POOL2_FLOOR}, {POOL3_X, POOL_END_X, -16, 16, POOL3_FLOOR}}) {
            for (int x = pool[0] + 2; x < pool[1]; x += 5) {
                for (int z : new int[]{pool[2], pool[3]}) {
                    double face = z == pool[2] ? z + 0.02 : z + 0.93;
                    p.boxes.add(new Box("minecraft:shroomlight", x, pool[4] + 3.1, face, 0.9, 0.5, 0.05, 15));
                    p.light(x, pool[4] + 3, z == pool[2] ? z + 1 : z - 1, 9);
                }
            }
        }
        // 液面四边都伸进墙里一点，从哪个角度都看不到液面的边和底下
        double in = 0.45;
        p.pools.add(new Parts.Pool("pool_thin", POOL1_X - in, -6 - in, POOL2_X - 2 + 1 + in, 8 + 1 + in, POOL1_FLOOR + SURFACE));
        p.pools.add(new Parts.Pool("pool_mid", POOL2_X - in, -8 - in, POOL3_X - 2 + 1 + in, 10 + 1 + in, POOL2_FLOOR + SURFACE));
        p.pools.add(new Parts.Pool("pool_thick", POOL3_X - in, -16 - in, POOL_END_X + 1 + in, 16 + 1 + in, POOL3_FLOOR + SURFACE));
        // 告示
        p.texts.add(new Text("一号池  家庭池\n请先咨询您的医生", POOL1_X + 0.08 - 1, POOL1_FLOOR + 3.0, 4.5, -90f, 0x24211f, 0.9f, 0xffd8c9a0, 7));
        p.texts.add(new Text("二号池\n请确认您的信仰能够承受", POOL2_X + 0.08 - 1, POOL2_FLOOR + 3.6, 5.0, -90f, 0x24211f, 0.9f, 0xffd8c9a0, 7));
        p.texts.add(new Text("三号池\n从此处起，园方不再负责", POOL3_X + 0.08 - 1, POOL3_FLOOR + 4.0, 5.0, -90f, 0xf4ecd8, 0.9f, 0xffa02818, 8));
        p.light(186, POOL1_FLOOR + 4, 1, 7);
        p.light(199, POOL2_FLOOR + 5, 1, 5);
        p.light(212, POOL3_FLOOR + 6, 0, 5);
        p.light(222, POOL3_FLOOR + 6, 0, 3);
        // 肉索从顶上垂进池子里
        for (int i = 0; i < 26; i++) {
            double x = POOL1_X + rnd.nextDouble() * (POOL_END_X - POOL1_X), span = x > POOL3_X ? 15 : 6;
            if (x < POOL1_X + 4) continue;
            double z = (rnd.nextDouble() * 2 - 1) * span;
            if (Math.abs(z - 1) < 2.5 && x < POOL3_X + 8) continue;
            if (x > ROW_X - 3 && x < ROW_X + 2) continue;
            double floor = x > POOL3_X ? POOL3_FLOOR : x > POOL2_X ? POOL2_FLOOR : POOL1_FLOOR;
            double r = (x > POOL3_X ? 0.5 : 0.28) + rnd.nextDouble() * 0.5;
            List<double[]> path = new ArrayList<>();
            for (int q = 0; q <= 5; q++) {
                double f = q / 5.0;
                path.add(new double[]{x + Math.sin(f * 4 + i) * 0.7, 25.5 + (floor - 0.3 - 25.5) * f, z + Math.cos(f * 3 + i) * 0.7});
            }
            p.rope(path, r, i % 3 == 0 ? 1 : i % 7 == 0 ? 2 : 0, 0.5, false);
        }
        // 腺泡一串：墙上鼓着，只有 HOLE 那个能爬
        grapes.add(new double[]{HOLE[0], HOLE[1], HOLE[2], 2.0});
        for (double[] g : new double[][]{{-2.0, 1.4, 1.5}, {1.9, 1.0, 1.6}, {-3.1, -0.2, 1.3}, {3.3, 0.2, 1.4}, {0.1, 2.6, 1.7}, {-1.6, 3.1, 1.3}, {2.0, 3.0, 1.2}}) {
            grapes.add(new double[]{HOLE[0] + g[0], HOLE[1] + g[1], HOLE[2] - 0.1, g[2]});
        }
        // 入口要一眼看出来：洞口正上方一块牌子带箭头，两边两盏暖灯，前面一级瓷砖台阶，人站上去正对着洞
        p.texts.add(new Text("特别体验  ·  未开发腺泡\n↓  请自行爬入  ↓", HOLE[0], HOLE[1] + 2.35, HOLE[2] + 1.0, 0f, 0x24211f, 0.9f, 0xffd8c9a0, 12));
        p.boxes.add(new Box("minecraft:polished_deepslate", HOLE[0] - 1.4, HOLE[1] + 2.2, HOLE[2] + 0.9, 2.8, 0.06, 0.06, -1));
        for (double side : new double[]{-1.7, 1.7}) {
            p.boxes.add(new Box("minecraft:shroomlight", HOLE[0] + side - 0.15, HOLE[1] + 0.9, HOLE[2] + 0.95, 0.3, 0.4, 0.08, 13));
            p.light((int) Math.floor(HOLE[0] + side), (int) HOLE[1] + 1, (int) Math.floor(HOLE[2] + 2), 9);
        }
        p.fill((int) HOLE[0] - 1, POOL3_FLOOR, (int) Math.floor(HOLE[2]) + 1, (int) HOLE[0] + 1, POOL3_FLOOR, (int) Math.floor(HOLE[2]) + 1, B.TILE_B);
        holeWalk();
    }

    /**
     * 从三号池门口一眼就要找得到洞：2007 年铺的一段瓷砖步道露出水面，从下池的台阶往东走，拐个弯往北一直通到洞口，
     * 两边扶手栏杆；拐角立一块牌子对着走进来的人；洞里往外透暖光，是三号池里最亮的地方。
     */
    private void holeWalk() {
        int y = POOL3_FLOOR, hx = (int) HOLE[0], end = (int) Math.floor(HOLE[2]) + 1;
        // 往东一段：接着下池最后一级台阶
        for (int x = POOL3_X + 1; x <= hx + 1; x++) for (int z = 0; z <= 2; z++) p.set(x, y, z, B.TILE_B);
        // 往北一段：一直到洞口那级台阶
        for (int z = end; z < 0; z++) for (int x = hx - 1; x <= hx + 1; x++) p.set(x, y, z, B.TILE_B);
        // 扶手：立柱加一根横杆，只是看着像栏杆，人还能从边上下水
        for (double x : new double[]{hx - 1.55, hx + 2.45}) {
            for (double z = end + 1; z <= -1; z += 2.5) p.boxes.add(new Box("minecraft:polished_deepslate", x, y + 1, z, 0.08, 0.95, 0.08, -1));
            p.beams.add(new Beam("minecraft:polished_deepslate", x + 0.04, y + 1.95, end + 1, x + 0.04, y + 1.95, -1, 0.06, -1));
        }
        // 拐角的牌子：对着从西边门口走进来的人，箭头指向步道往北拐的方向
        p.boxes.add(new Box("minecraft:polished_deepslate", hx + 1.6, y + 1, 1.45, 0.1, 1.9, 0.1, -1));
        p.texts.add(new Text("特别体验  ·  未开发腺泡\n←  沿步道前往", hx + 1.55, y + 2.65, 1.5, 90f, 0x24211f, 0.8f, 0xffd8c9a0, 12));
        // 步道上隔一段一盏小灯，在黑水上连成一条线
        for (int z = end + 2; z <= 0; z += 4) p.light(hx, y + 1, z, 6);
        // 洞里往外透暖光
        p.boxes.add(new Box("minecraft:shroomlight", HOLE[0] - 0.55, HOLE[1] - 0.45, HOLE[2] - 0.9, 1.1, 1.0, 0.1, 15));
        p.light(hx, (int) HOLE[1], end, 13);
    }

    /**
     * 花园层那天下午的样子：一家家人在这儿等来电，等了两个小时。
     * 餐盘没收、气球还拴在椅子上、婴儿车停在桌边，墙上贴着当晚的烟花晚会和评分终端的海报。
     */
    private void gardenLife(int y) {
        double top = y + 0.8;
        // 西墙：自动售货机，还亮着
        p.boxes.add(new Box("minecraft:light_gray_concrete", 116.05, y, 4.6, 0.95, 2.05, 1.1, -1));
        p.boxes.add(new Box("minecraft:light_blue_concrete", 116.98, y + 0.95, 4.75, 0.04, 0.9, 0.8, 11));
        p.boxes.add(new Box("minecraft:black_concrete", 116.98, y + 0.3, 4.8, 0.04, 0.35, 0.7, -1));
        p.texts.add(new Text("冷饮  $0.75", 117.02, y + 2.15, 5.15, -90f, 0x24211f, 0.42f, 0xffe6dcc8, 10));
        // 西墙：告示板，烟花晚会和评分终端的海报
        p.boxes.add(new Box("minecraft:brown_terracotta", 116.0, y + 0.9, -1.3, 0.05, 1.7, 2.8, -1));
        p.posters.add(new Parts.Poster("poster_july4", 116.12, y + 1.75, 0.65, 90f, 0.95, 1.3, -1));
        p.posters.add(new Parts.Poster("poster_rating", 116.12, y + 1.75, -0.55, 90f, 0.95, 1.3, -1));
        // 桌上：没收的餐盘、喝了一半的杯子、一次性相机、拴在椅子上的气球
        for (double[] t : new double[][]{{124, 3}, {129, 0}, {141, 1}, {147, 7}}) {
            p.boxes.add(new Box("minecraft:orange_terracotta", t[0] + 0.85, top, t[1] + 0.9, 0.48, 0.03, 0.34, -1));
            p.boxes.add(new Box("minecraft:white_concrete", t[0] + 0.95, top + 0.03, t[1] + 0.98, 0.08, 0.14, 0.08, -1));
            p.boxes.add(new Box("minecraft:white_concrete", t[0] + 1.18, top + 0.03, t[1] + 1.02, 0.08, 0.12, 0.08, -1));
        }
        p.boxes.add(new Box("minecraft:yellow_concrete", 129.3, top, 0.3, 0.14, 0.08, 0.07, -1));
        p.beams.add(new Parts.Beam("minecraft:white_concrete", 122.0, y + 0.48, -0.4, 122.25, y + 1.75, -0.35, 0.012, -1));
        p.boxes.add(new Box("minecraft:red_concrete", 122.1, y + 1.7, -0.5, 0.3, 0.34, 0.3, -1));
        // 婴儿车停在桌边
        p.boxes.add(new Box("minecraft:blue_wool", 145.7, y + 0.35, 0.3, 0.8, 0.42, 0.5, -1));
        p.boxes.add(new Box("minecraft:blue_wool", 145.7, y + 0.77, 0.3, 0.3, 0.32, 0.5, -1));
        p.beams.add(new Parts.Beam("minecraft:iron_block", 146.5, y + 0.6, 0.35, 146.85, y + 1.1, 0.35, 0.02, -1));
        p.beams.add(new Parts.Beam("minecraft:iron_block", 146.5, y + 0.6, 0.75, 146.85, y + 1.1, 0.75, 0.02, -1));
        for (double[] w : new double[][]{{145.75, 0.25}, {146.35, 0.25}, {145.75, 0.75}, {146.35, 0.75}}) {
            p.boxes.add(new Box("minecraft:black_concrete", w[0], y, w[1], 0.14, 0.32, 0.06, -1));
        }
        // 投币摇摇车：一匹褪色的小马
        p.boxes.add(new Box("minecraft:yellow_concrete", 130.4, y, -6.0, 1.2, 0.25, 0.8, -1));
        p.boxes.add(new Box("minecraft:red_concrete", 130.6, y + 0.45, -5.9, 0.75, 0.38, 0.5, -1));
        p.boxes.add(new Box("minecraft:red_concrete", 131.25, y + 0.75, -5.83, 0.3, 0.42, 0.36, -1));
        p.beams.add(new Parts.Beam("minecraft:iron_block", 130.95, y + 0.25, -5.65, 130.95, y + 0.45, -5.65, 0.08, -1));
        p.texts.add(new Text("投币 25¢", 131.0, y + 0.08, -5.18, 0f, 0x24211f, 0.3f, 0xffe6dcc8, -1));
        // 顶上的广播喇叭
        p.boxes.add(new Box("minecraft:gray_concrete", 133.8, GARDEN_CEIL - 0.32, 2.3, 0.4, 0.3, 0.4, -1));
        p.boxes.add(new Box("minecraft:gray_concrete", 129.8, GARDEN_CEIL - 0.32, -3.7, 0.4, 0.3, 0.4, -1));
        // 楼梯下来的导览牌
        p.beams.add(new Parts.Beam("minecraft:polished_deepslate", 130.8, y, 3.6, 130.8, y + 2.0, 3.6, 0.05, -1));
        p.texts.add(new Text("休闲花园层导览\n南侧  医务站 · 园艺工具间 · 失物招领\n东侧  隧道 → 更衣室 → 羊膜温泉", 130.86, y + 2.1, 3.6, -90f, 0x24211f, 0.42f, 0xffe6dcc8, 8));
    }

    /** 腺泡的位置：x、y、z、直径。 */
    public final List<double[]> grapes = new ArrayList<>();

    private void burrow() {
        int y = BURROW_Y, z = BURROW_Z;
        for (int x = BURROW_X0 - 1; x <= BURROW_X1; x++) {
            p.set(x, y - 1, z, B.BARRIER);
            p.set(x, y + 1, z, B.BARRIER);
            p.set(x, y, z - 1, B.BARRIER);
            p.set(x, y, z + 1, B.BARRIER);
        }
        p.set(BURROW_X0 - 1, y, z, B.BARRIER);
    }
}
