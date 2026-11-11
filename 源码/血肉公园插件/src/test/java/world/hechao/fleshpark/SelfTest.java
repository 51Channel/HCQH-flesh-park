package world.hechao.fleshpark;

import world.hechao.fleshpark.core.Well;
import world.hechao.fleshpark.core.B;
import world.hechao.fleshpark.core.Lower;
import world.hechao.fleshpark.core.ParkPlan;
import world.hechao.fleshpark.core.Parts;
import world.hechao.fleshpark.core.RideProfile;
import world.hechao.fleshpark.core.Route;

/** 离线自检：不开服也能查出来的问题。穿模、堵路、走不到、时长不对。 */
public final class SelfTest {
    private static int passed, failed;

    public static void main(String[] args) {
        long start = System.currentTimeMillis();
        ParkPlan plan = new ParkPlan();
        System.out.printf("方块 %d，肉索 %d，方块展示 %d，杆 %d，字 %d，生成 %d 毫秒%n", plan.blockCount(), plan.ropes.size(), plan.boxes.size(), plan.beams.size(), plan.texts.size(), System.currentTimeMillis() - start);
        route(plan.route);
        ride(plan);
        clearance(plan);
        paths(plan);
        notes(plan);
        limits(plan);
        System.out.printf("通过 %d，失败 %d%n", passed, failed);
        if (failed > 0) System.exit(1);
    }

    private static void route(Route route) {
        double[] p = new double[3];
        double last = 999, rise = 0;
        for (double d = 0; d <= route.length; d += 0.5) {
            route.at(d, p);
            rise = Math.max(rise, p[1] - last);
            last = p[1];
        }
        check("路线一路往下", rise < 0.05, "最大上升 " + rise);
        route.at(route.length, p);
        check("终点在 3 号码头", Math.abs(p[0] - 102.5) < 0.01 && Math.abs(p[1] - 30.33) < 0.01, p[0] + "," + p[1]);
        check("到站时地板和码头齐平", Math.abs(p[1] - Route.FLOOR_DROP - ParkPlan.FLOOR_HALL) < 0.01, "");
        route.at(0, p);
        check("停靠时地板和站台齐平", Math.abs(p[1] - Route.FLOOR_DROP - ParkPlan.FLOOR_STATION) < 0.01, "");
    }

    private static void ride(ParkPlan plan) {
        RideProfile profile = new RideProfile(plan.route);
        double t = 0, stoppedAt = -1, peak = 0;
        while (!profile.done() && t < 400) {
            profile.step(0.05);
            t += 0.05;
            peak = Math.max(peak, profile.v);
            if (profile.holding() && stoppedAt < 0) stoppedAt = profile.d;
        }
        System.out.printf("路线 %.0f 米，全程 %.1f 秒，最高 %.1f 米/秒%n", plan.route.length, t, peak);
        check("能开到终点", profile.done(), "t=" + t);
        check("全程三分半到五分半", t >= 210 && t <= 330, "t=" + t);
        check("半路被夹停", Math.abs(stoppedAt - profile.dSqueeze) < 0.05, "停在 " + stoppedAt);
    }

    private static void clearance(ParkPlan plan) {
        int inside = 0;
        for (Parts.Rope rope : plan.ropes) {
            for (double[] q : rope.pts()) if (plan.route.carHit(q[0], q[1], q[2], q[3])) inside++;
        }
        check("肉索不穿进车厢", inside == 0, inside + " 处");
        int[] blocked = {0};
        plan.route.forEachSample(0.5, (x, y, z) -> {
            for (double dx = Route.CABIN_X0; dx <= Route.CABIN_X1; dx += 1) {
                for (double dz = -Route.CABIN_HALF_Z; dz <= Route.CABIN_HALF_Z; dz += 1) {
                    for (double dy = -Route.FLOOR_DROP + 0.1; dy <= Route.CABLE_Y; dy += 1) {
                        B b = plan.block((int) Math.floor(x + dx), (int) Math.floor(y + dy), (int) Math.floor(z + dz));
                        if (b != null && b != B.BARRIER) blocked[0]++;
                    }
                }
            }
        });
        check("车厢路线上没有方块", blocked[0] == 0, blocked[0] + " 处");
    }

    private static void paths(ParkPlan plan) {
        walk(plan, "接驳站站台上走得动", new double[][]{{-552.5, 6.5}, {-537.5, 6.5}, {-537.5, 9.5}, {-549.5, 9.5}}, ParkPlan.FLOOR_STATION);
        boolean fenced = true;
        for (int x = ParkPlan.PICKUP_X0 - 1; x <= ParkPlan.PICKUP_X1 + 1; x++) fenced &= plan.block(x, ParkPlan.FLOOR_STATION, 4) == B.BARRIER && plan.block(x, ParkPlan.FLOOR_STATION + 1, 4) == B.BARRIER;
        for (int z = 5; z <= 10; z++) fenced &= plan.block(ParkPlan.PICKUP_X0 - 1, ParkPlan.FLOOR_STATION, z) == B.BARRIER && plan.block(ParkPlan.PICKUP_X1 + 1, ParkPlan.FLOOR_STATION, z) == B.BARRIER;
        check("接驳站站台四面围着，走不到路上去", fenced, "");
        double ndz = ParkPlan.NEW_STATION_DZ;
        walk(plan, "出电梯沿红绳走到结算窗口", new double[][]{{-14, ndz + 5.85}, {ParkPlan.SETTLE_AT[0], ParkPlan.SETTLE_AT[1]}}, ParkPlan.FLOOR_STATION);
        walk(plan, "结算窗口走到员工通道门口", new double[][]{{ParkPlan.SETTLE_AT[0], ParkPlan.SETTLE_AT[1]}, {14.2, ndz + 4}, {15.6, ndz + 4}}, ParkPlan.FLOOR_STATION);
        walk(plan, "员工通道走到头", new double[][]{{17, ndz + 4}, {24, ndz + 4}}, ParkPlan.FLOOR_STATION);
        walk(plan, "调度站小屋走到车门", new double[][]{{ParkPlan.DEPOT_SPAWN[0], ParkPlan.DEPOT_SPAWN[1]}, {-639, 11}, {ParkPlan.BUS_FROM_X + 4.2, 6}}, ParkPlan.FLOOR_STATION);
        double door = ParkPlan.BUS_STOP_X + 4.2;
        walk(plan, "下车走进入园大厅", new double[][]{{door, 4.3}, {door, 6.5}, {-36, 5.5}, {-30.5, 5.5}}, ParkPlan.FLOOR_STATION);
        walk(plan, "开局绕排队栏杆走上站台", new double[][]{{-30.5, 5.5}, {-29.5, 9.5}, {-24, 10}, {-23.5, 1.5}, {-20.5, 1.5}, {-20.5, 5.5}, {-15, 5.5}}, ParkPlan.FLOOR_STATION);
        walk(plan, "站台走到车门", new double[][]{{-12.5, 5.5}, {-4.5, 5.5}, {-4.5, 2.5}, {-4.5, 0}}, ParkPlan.FLOOR_STATION);
        walk(plan, "码头走进大厅", new double[][]{{99.3, 3.5}, {108, 4.5}, {112, 4.5}, {124, 7}, {130, 8}}, ParkPlan.FLOOR_HALL);
        walk(plan, "大厅走到楼梯口", new double[][]{{130.5, 7.5}, {132, 9.5}, {143.5, 9.5}}, ParkPlan.FLOOR_HALL);
        walk(plan, "走道拐进楼梯口", new double[][]{{143.5, 9.5}, {143.5, 6.2}}, ParkPlan.FLOOR_HALL);
        int hy = ParkPlan.FLOOR_HALL;
        boolean shut = true;
        for (int z = 4; z <= 8; z++) shut &= plan.block(132, hy, z) != null && plan.block(132, hy + 1, z) == B.BARRIER;
        for (int x = 132; x <= 142; x++) for (int z : new int[]{4, 8}) shut &= plan.block(x, hy, z) != null && plan.block(x, hy + 1, z) != null && (plan.block(x, hy + 1, z) == B.BARRIER || plan.block(x, hy + 1, z) == B.STEEL);
        boolean column = true;
        for (int x = 132; x <= 142; x++) for (int z = 4; z <= 8; z++) if (plan.block(x, hy + 2, z) == B.STEEL) column &= plan.block(x, hy, z) == B.STEEL && plan.block(x, hy + 1, z) == B.STEEL;
        check("楼梯井边上的钢柱一直落到地面，没被栏杆截断", column, "");
        check("楼梯井南北和西头都有栏杆，上面一格看不见的墙，翻不过去", shut, "");
        boolean flight = true;
        for (int x = 133; x <= 142; x++) {
            B head = plan.block(x, hy - (142 - x), 6), top = plan.block(x, hy + 1 - (142 - x), 6);
            // 最上面一级头顶是警戒线后面的墙，松线的时候拆掉
            boolean tape = x == 142 && head == B.BARRIER && top == B.BARRIER;
            flight &= plan.block(x, hy - 1 - (142 - x), 6) == B.STAIR_E && (tape || ((head == null || head.isWalkable()) && (top == null || top.isWalkable())));
        }
        check("台阶一级一级连着，从大厅一直到花园地面", flight && plan.block(132, Lower.GARDEN_FLOOR - 1, 6) != null && plan.block(132, Lower.GARDEN_FLOOR, 6) == null, "");
        boolean tape = plan.block(143, hy - 1, 6) != null && plan.block(143, hy, 6) == null && plan.block(142, hy - 1, 6) == B.STAIR_E;
        for (int z = 5; z <= 7; z++) tape &= plan.block(142, hy, z) == B.BARRIER && plan.block(142, hy + 1, z) == B.BARRIER;
        check("楼梯口拉着警戒线，线外有落脚的地方", tape, "");
        walk(plan, "花园走进隧道", new double[][]{{131, 6}, {131, 2}, {152, 1.5}, {160, 1.5}, {178, 1.5}}, world.hechao.fleshpark.core.Lower.GARDEN_FLOOR);
        walk(plan, "站台走进站务室", new double[][]{{4.5, 6}, {4.5, 12.5}}, ParkPlan.FLOOR_STATION);
        well(plan);
        walk(plan, "花园走进医务站", new double[][]{{121.5, -5}, {121.5, -11.5}}, Lower.GARDEN_FLOOR);
        walk(plan, "花园走进园艺工具间", new double[][]{{142.5, -5}, {142.5, -11.5}}, Lower.GARDEN_FLOOR);
        walk(plan, "隧道走进广播室", new double[][]{{158.5, 1}, {158.5, -4.5}}, Lower.GARDEN_FLOOR);
        walk(plan, "更衣室走进淋浴间", new double[][]{{177.5, 0}, {177.5, -5}, {173.5, -5}, {173.5, -10.5}}, Lower.GARDEN_FLOOR);
        walk(plan, "一号池走进救生员室", new double[][]{{186.5, -4}, {186.5, -11.5}}, Lower.POOL1_FLOOR);
        walk(plan, "第一池", new double[][]{{183, 1.5}, {190, 1.5}}, world.hechao.fleshpark.core.Lower.POOL1_FLOOR);
        walk(plan, "第二池", new double[][]{{196.5, 1.5}, {202, 1.5}}, world.hechao.fleshpark.core.Lower.POOL2_FLOOR);
        boolean held = true;
        for (int[] w : new int[][]{{world.hechao.fleshpark.core.Lower.POOL2_X - 1, world.hechao.fleshpark.core.Lower.POOL1_FLOOR}, {world.hechao.fleshpark.core.Lower.POOL3_X - 1, world.hechao.fleshpark.core.Lower.POOL2_FLOOR}}) {
            for (int z = 0; z <= 2; z++) held &= plan.block(w[0], w[1], z) != null && plan.block(w[0], w[1] + 1, z) == null && w[1] + 1 > w[1] + world.hechao.fleshpark.core.Lower.SURFACE;
        }
        check("池子之间的门槛比上一池水面高，水兜得住", held, "");
        walk(plan, "第三池顺着瓷砖步道走到腺泡", new double[][]{{208.5, 1.5}, {214.5, 1.5}, {214.5, -15}}, world.hechao.fleshpark.core.Lower.POOL3_FLOOR + 1);
        trek(plan, "储物柜走到第三池（过门洞，会往下跳）", new double[][]{{172, 1.5}, {180, 1.5}, {191, 1.5}, {203, 1.5}, {209.5, 1.5}, {209.5, -13}}, world.hechao.fleshpark.core.Lower.GARDEN_FLOOR);
        trek(plan, "爬出洞口走到气压计", new double[][]{{293.4, -65.5}, {294.6, -65.5}, {296, -64}, {299.5, -60}, {299.5, -54.2}}, world.hechao.fleshpark.core.Lower.BURROW_Y);
        ropesClear(plan);
        steps(plan, "栈道走到车", plan.forest.walkLine);
        steps(plan, "栈道跑到电梯", plan.forest.exitLine);
        check("站台有地面", solid(plan.block(-12, ParkPlan.FLOOR_STATION - 1, 5)), "");
        check("车厢里有地面", solid(plan.block(-4, ParkPlan.FLOOR_STATION - 1, 0)), "");
        check("车门口有地面", solid(plan.block(-5, ParkPlan.FLOOR_STATION - 1, 2)), "");
        check("码头有地面", solid(plan.block(99, ParkPlan.FLOOR_HALL - 1, 3)), "");
        check("大厅门能进", plan.block(ParkPlan.HX0, ParkPlan.FLOOR_HALL, 4) == null, "");
        check("人形站的地方是空的", plan.block((int) ParkPlan.FIGURE_X, ParkPlan.FLOOR_HALL + 1, (int) ParkPlan.FIGURE_Z) == null, String.valueOf(plan.block((int) ParkPlan.FIGURE_X, ParkPlan.FLOOR_HALL + 1, (int) ParkPlan.FIGURE_Z)));
        check("窗外堵满了肉", plan.block(120, ParkPlan.FLOOR_HALL + 3, 18) != null && plan.block(120, ParkPlan.FLOOR_HALL + 3, 18).isFlesh(), "");
        check("全景窗是玻璃", plan.block(112, ParkPlan.FLOOR_HALL + 3, ParkPlan.HZ1) == B.GLASS_X, "");
        // 从窗前到人形的视线不能被方块挡住：这是整段的高潮
        double[] eye = {131.5, ParkPlan.FLOOR_HALL + 1.62, 8};
        double[] head = {ParkPlan.FIGURE_X, ParkPlan.FLOOR_HALL + 1.9, ParkPlan.FIGURE_Z};
        boolean clear = true;
        for (double k = 0; k <= 1; k += 0.02) {
            B b = plan.block((int) Math.floor(eye[0] + (head[0] - eye[0]) * k), (int) Math.floor(eye[1] + (head[1] - eye[1]) * k), (int) Math.floor(eye[2] + (head[2] - eye[2]) * k));
            if (b != null && b.isOpaque()) clear = false;
        }
        check("窗前能看见洞里的人形", clear, "");
    }

    private static boolean air(B b) {
        return b == null || b.name().startsWith("LIGHT_");
    }

    /** 深井：电梯口平台 → 4 号支撑一级一级往下 → 支撑脚 → 绳梯 → 防水袋平台；井底绕到真楼的码头，码头进大厅，大厅下楼梯到花园层，花园层走进机房。 */
    private static void well(ParkPlan plan) {
        walk(plan, "卡住的电梯走上平台", new double[][]{{Well.CAB_X + 1.5, -19.5}, {Well.LEDGE_X1 + 0.5, -19.5}}, Well.CAB_Y);
        boolean steps = true;
        String bad = "";
        for (int i = 0; i < Well.STEPS; i++) {
            int x = Well.TRUSS_X0 + i, y = Well.stepY(i);
            for (int z = Well.PATH_Z0 + 1; z <= Well.PATH_Z1 - 1; z++) {
                if (plan.block(x, y, z) != B.STAIR_W || !air(plan.block(x, y + 1, z)) || !air(plan.block(x, y + 2, z))) {
                    steps = false;
                    bad = x + "," + y + "," + z;
                }
            }
            boolean fenced = plan.block(x, y + 1, Well.PATH_Z0 - 1) == B.BARRIER && plan.block(x, y + 2, Well.PATH_Z0 - 1) == B.BARRIER && plan.block(x, y + 1, Well.PATH_Z1 + 1) == B.BARRIER && plan.block(x, y + 2, Well.PATH_Z1 + 1) == B.BARRIER;
            if (!fenced) {
                steps = false;
                bad = "栏杆 " + x;
            }
        }
        check("4 号支撑的台阶一级一级连着，两边拦着，头顶空着", steps, bad);
        for (int i = 0; i + 1 < Well.STEPS; i += 10) {
            int to = Math.min(Well.STEPS, i + 10);
            trek(plan, "顺着支撑往下走（第 " + i + " 到 " + to + " 级）", new double[][]{{Well.TRUSS_X0 + i + 0.5, -19.5}, {Well.TRUSS_X0 + to + 0.5, -19.5}}, Well.stepY(i) + 1);
        }
        walk(plan, "支撑脚下的平台走到梯子口", new double[][]{{Well.FOOT_X0 + 0.5, -19.5}, {Well.FOOT_X1 + 0.5, -19.5}}, Well.FOOT_Y);
        boolean ladder = true;
        for (int y = Well.BAG_Y; y <= Well.FOOT_Y + 1; y++) ladder &= plan.block(Well.LADDER_X, y, Well.LADDER_Z) == B.LADDER_W && plan.block(Well.LADDER_X + 1, y, Well.LADDER_Z) == B.STEEL;
        check("绳梯从支撑脚一直挂到防水袋平台，背后是钢柱", ladder, "");
        walk(plan, "防水袋平台走得动", new double[][]{{Well.BAG_X0 + 0.5, -19.5}, {Well.BAG_X1 - 0.5, -19.5}, {Well.BAG_X1 - 0.5, -21.5}}, Well.BAG_Y);
        walk(plan, "井底落脚处绕到真楼的码头", new double[][]{{Well.CAR_TO[0] - 2, Well.CAR_TO[2] + 3}, {488, -32}, {478, -20}, {474, -8}, {476, 0}, {480, 3}}, Well.REAL_FLOOR);
        int d = Well.DX;
        walk(plan, "真码头走进大厅", new double[][]{{ParkPlan.DOCK_OFF[0] + d, 3.5}, {108 + d, 4.5}, {112 + d, 4.5}, {124 + d, 7}, {130 + d, 8}}, Well.REAL_FLOOR);
        walk(plan, "真大厅走到楼梯口", new double[][]{{130.5 + d, 7.5}, {132 + d, 9.5}, {143.5 + d, 9.5}, {143.5 + d, 6.2}}, Well.REAL_FLOOR);
        trek(plan, "真楼梯下到花园层", new double[][]{{143.5 + d, 6.5}, {131.5 + d, 6.5}}, Well.REAL_FLOOR);
        walk(plan, "花园层走到东头", new double[][]{{131 + d, 2}, {131 + d, 0.5}, {150 + d, 0.5}}, Well.REAL_GARDEN);
        check("井底一圈看不见的墙", plan.block(Well.SX + 45, Well.REAL_FLOOR, Well.SZ) == B.BARRIER && plan.block(Well.SX, Well.REAL_FLOOR, Well.SZ - 45) == B.BARRIER, "");
    }


    /** 和 leak 一样，范围自己给。 */
    private static int[] leakIn(ParkPlan plan, int x0, int y0, int z0, int minX, int maxX, int minY, int maxY, int minZ, int maxZ) {
        java.util.Set<Long> seen = new java.util.HashSet<>();
        java.util.ArrayDeque<int[]> queue = new java.util.ArrayDeque<>();
        queue.add(new int[]{x0, y0, z0});
        seen.add(cell(x0, y0, z0));
        int[][] dirs = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
        while (!queue.isEmpty()) {
            int[] q = queue.poll();
            if (q[0] < minX || q[0] > maxX || q[1] < minY || q[1] > maxY || q[2] < minZ || q[2] > maxZ) return q;
            if (seen.size() > 600_000) return q;
            for (int[] d : dirs) {
                int nx = q[0] + d[0], ny = q[1] + d[1], nz = q[2] + d[2];
                B b = plan.block(nx, ny, nz);
                if (b != null && !b.name().startsWith("LIGHT_")) continue;
                if (seen.add(cell(nx, ny, nz))) queue.add(new int[]{nx, ny, nz});
            }
        }
        return null;
    }


    private static boolean sameCab(ParkPlan plan, int ax, int ay, int az, int bx, int by, int bz) {
        for (int dx = -1; dx <= 3; dx++) {
            for (int dz = -1; dz <= 3; dz++) {
                for (int dy = -1; dy <= 3; dy++) {
                    if (plan.block(ax + dx, ay + dy, az + dz) != plan.block(bx + dx, by + dy, bz + dz)) {
                        B a = plan.block(ax + dx, ay + dy, az + dz), b = plan.block(bx + dx, by + dy, bz + dz);
                        // 灯方块不算
                        if ((a == null || a.name().startsWith("LIGHT")) && (b == null || b.name().startsWith("LIGHT"))) continue;
                        return false;
                    }
                }
            }
        }
        return true;
    }

    private static void walk(ParkPlan plan, String name, double[][] points, int floor) {
        int bad = 0;
        String first = "";
        for (int i = 0; i < points.length - 1; i++) {
            for (double k = 0; k <= 1; k += 0.05) {
                double x = points[i][0] + (points[i + 1][0] - points[i][0]) * k, z = points[i][1] + (points[i + 1][1] - points[i][1]) * k;
                for (int y = floor; y <= floor + 1; y++) {
                    B b = plan.block((int) Math.floor(x), y, (int) Math.floor(z));
                    if (b != null && !b.isWalkable()) bad++;
                }
                B ground = plan.block((int) Math.floor(x), floor - 1, (int) Math.floor(z));
                if (ground == null) bad++;
                if (bad > 0 && first.isEmpty()) first = (int) Math.floor(x) + "," + (int) Math.floor(z);
            }
        }
        check(name, bad == 0, bad + " 处挡路或没地面，第一处 " + first);
    }

    /** 按人的身体走：0.6 宽 1.8 高，前面有墙就算挡住，脚下空了就往下掉。只允许往下，不允许跳。 */
    private static void trek(ParkPlan plan, String name, double[][] points, int startFloor) {
        int feet = startFloor, bad = 0;
        String first = "";
        boolean onSlab = false;
        for (int i = 0; i < points.length - 1; i++) {
            for (double k = 0; k <= 1; k += 0.02) {
                double x = points[i][0] + (points[i + 1][0] - points[i][0]) * k, z = points[i][1] + (points[i + 1][1] - points[i][1]) * k;
                boolean blocked = false;
                for (double ox : new double[]{-0.3, 0.3}) {
                    for (double oz : new double[]{-0.3, 0.3}) {
                        for (int y = feet; y <= feet + 1; y++) {
                            B b = plan.block((int) Math.floor(x + ox), y, (int) Math.floor(z + oz));
                            if (b != null && !b.isWalkable()) blocked = true;
                        }
                    }
                }
                // 站在半砖上，前面一格高的台阶走得上去（人一次能迈半格）
                if (blocked && onSlab && clear(plan, x, z, feet + 1)) {
                    feet++;
                    blocked = false;
                }
                if (blocked) {
                    bad++;
                    if (first.isEmpty()) first = String.format("%.1f,%d,%.1f", x, feet, z);
                    continue;
                }
                onSlab = false;
                for (double ox : new double[]{-0.3, 0.3}) for (double oz : new double[]{-0.3, 0.3}) onSlab |= plan.block((int) Math.floor(x + ox), feet, (int) Math.floor(z + oz)) == B.WALK_B;
                boolean ground = false;
                while (!ground && feet > startFloor - 12) {
                    for (double ox : new double[]{-0.3, 0.3}) {
                        for (double oz : new double[]{-0.3, 0.3}) {
                            if (plan.block((int) Math.floor(x + ox), feet - 1, (int) Math.floor(z + oz)) != null) ground = true;
                        }
                    }
                    if (!ground) feet--;
                }
            }
        }
        check(name, bad == 0, bad + " 处挡路，第一处 " + first);
    }

    private static boolean clear(ParkPlan plan, double x, double z, int feet) {
        for (double ox : new double[]{-0.3, 0.3}) {
            for (double oz : new double[]{-0.3, 0.3}) {
                for (int y = feet; y <= feet + 1; y++) {
                    B b = plan.block((int) Math.floor(x + ox), y, (int) Math.floor(z + oz));
                    if (b != null && !b.isWalkable()) return false;
                }
            }
        }
        return true;
    }

    /** 肉索不能穿进建筑和摆设：允许贴着，不允许穿进去超过半径的 35%。 */
    private static void ropesClear(ParkPlan plan) {
        int bad = 0;
        String first = "";
        for (world.hechao.fleshpark.core.Parts.Rope rope : plan.ropes) {
            for (double[] q : rope.pts()) {
                boolean hit = false;
                for (world.hechao.fleshpark.core.Parts.Box b : plan.boxes) {
                    if (b.sx() * b.sy() * b.sz() <= 0.02) continue;
                    double nx = Math.max(b.x(), Math.min(b.x() + b.sx(), q[0])), ny = Math.max(b.y(), Math.min(b.y() + b.sy(), q[1])), nz = Math.max(b.z(), Math.min(b.z() + b.sz(), q[2]));
                    double d = Math.sqrt((q[0] - nx) * (q[0] - nx) + (q[1] - ny) * (q[1] - ny) + (q[2] - nz) * (q[2] - nz));
                    if (d < q[3] * 0.6) {
                        hit = true;
                        break;
                    }
                }
                if (hit) {
                    bad++;
                    if (first.isEmpty()) first = String.format("%.1f,%.1f,%.1f r=%.2f", q[0], q[1], q[2], q[3]);
                }
            }
        }
        check("肉索不穿实体", bad == 0, bad + " 处，第一处 " + first);
    }

    /** 栈道：每一步脚下有板，头顶和身子不被挡，相邻两步的高差不超过原版能自动迈上去的 0.6 格。 */
    private static void steps(ParkPlan plan, String name, java.util.List<double[]> line) {
        int bad = 0;
        double last = Double.NaN;
        String first = "";
        for (double[] s : line) {
            int x = (int) Math.floor(s[0]), z = (int) Math.floor(s[2]), around = (int) Math.floor(s[1]);
            double top = Double.NaN;
            for (int y = around + 3; y >= around - 3 && Double.isNaN(top); y--) {
                B b = plan.block(x, y, z);
                if (b == B.WALK_B) top = y + 0.5;
                else if (b == B.WALK_T) top = y + 1;
                else if (b != null && b.isFlesh()) top = y + 1;
            }
            boolean wrong = Double.isNaN(top);
            if (!wrong) {
                for (int y = (int) Math.ceil(top); y <= (int) Math.ceil(top) + 1; y++) {
                    B b = plan.block(x, y, z);
                    // 呼气前挡路的空气墙是故意的
                    if (b != null && !b.isWalkable() && !(b == B.BARRIER && x == 306)) wrong = true;
                }
                if (!Double.isNaN(last) && top - last > 0.61) wrong = true;
                last = top;
            }
            if (wrong) {
                bad++;
                if (first.isEmpty()) first = x + "," + around + "," + z + " 面高 " + top + " 上一步 " + last;
            }
        }
        check(name, bad == 0, bad + " 处，第一处 " + first);
    }

    private static boolean solid(B b) {
        return b != null && b != B.DEBRIS && b != B.DEBRIS_DARK;
    }

    private static void limits(ParkPlan plan) {
        check("肉索不超过 3000 根", plan.ropes.size() <= 3000, plan.ropes.size() + "");
        check("方块不超过 90 万", plan.blockCount() <= 900_000, plan.blockCount() + "");
        int[] outOfRange = {0};
        plan.forEachBlock((x, y, z, b) -> {
            if (y < 0 || y > 255) outOfRange[0]++;
        });
        check("所有方块在下界高度范围内", outOfRange[0] == 0, outOfRange[0] + "");
    }

    /** 下一站的门要读完才开，所以每张纸都要从本站入口走得到、站着够得着（3 格）、视线不被方块挡。读不到就卡关。 */
    private static void notes(ParkPlan plan) {
        java.util.Map<String, int[]> starts = new java.util.HashMap<>();
        starts.put("station", new int[]{-12, ParkPlan.FLOOR_STATION, 5});
        starts.put("hall", new int[]{100, ParkPlan.FLOOR_HALL, 3});
        starts.put("garden", new int[]{131, Lower.GARDEN_FLOOR, 6});
        starts.put("lockers", new int[]{Lower.LOCKER_X0 + 1, Lower.GARDEN_FLOOR, 1});
        starts.put("pools", new int[]{183, Lower.POOL1_FLOOR, 1});
        starts.put("forest", new int[]{300, 177, -58});
        starts.put("well", new int[]{Well.LEDGE_X0 + 1, Well.CAB_Y, -20});
        starts.put("rope", new int[]{Well.BAG_X0 + 1, Well.BAG_Y, -20});
        starts.put("real", new int[]{(int) ParkPlan.DOCK_OFF[0] + Well.DX, Well.REAL_FLOOR, 3});
        java.util.Map<String, java.util.Set<Long>> reach = new java.util.HashMap<>();
        StringBuilder script = new StringBuilder();
        int bad = 0, count = 0;
        StringBuilder first = new StringBuilder();
        for (Notes.Note n : Notes.all(plan)) {
            if (n.cabin()) continue;
            count++;
            java.util.Set<Long> cells = reach.computeIfAbsent(n.area(), a -> flood(plan, starts.get(a)));
            double ax = n.x(), ay = n.y() + 0.06, az = n.z();
            // 摆设没有碰撞，点得到也可能根本看不见：纸不能埋在摆设里，视线也不能穿过摆设
            java.util.List<Parts.Box> near = new java.util.ArrayList<>();
            boolean buried = false;
            for (Parts.Box b : plan.boxes) {
                if (b.x() > ax + 4 || b.x() + b.sx() < ax - 4 || b.z() > az + 4 || b.z() + b.sz() < az - 4 || b.y() > ay + 4 || b.y() + b.sy() < ay - 4) continue;
                near.add(b);
                if (inBox(b, ax, ay, az)) buried = true;
            }
            double best = 1e9;
            int[] at = null;
            for (long c : cells) {
                int x = (int) (c >> 40) - 1000, y = (int) ((c >> 20) & 0xFFFFF) - 1000, z = (int) (c & 0xFFFFF) - 1000;
                double ex = x + 0.5, ey = y + 1.62, ez = z + 0.5;
                double dist = Math.sqrt((ex - ax) * (ex - ax) + (ey - ay) * (ey - ay) + (ez - az) * (ez - az));
                if (dist > 2.8 || Math.abs(dist - 2.0) >= best || !clearSight(plan, near, ex, ey, ez, ax, ay, az)) continue;
                best = Math.abs(dist - 2.0);
                at = new int[]{x, y, z};
            }
            if (at == null || buried) {
                bad++;
                if (first.length() < 200) first.append(n.id()).append(' ');
                continue;
            }
            script.append("c cmd tp ").append(at[0] + 0.5).append(' ').append(at[1]).append(' ').append(at[2] + 0.5).append('\n')
                    .append("w 0.7\n").append("c lookat ").append(ax).append(' ').append(ay).append(' ').append(az).append('\n')
                    .append("w 0.5\nc hit\nc use\nw 1.2\nc shot note_").append(n.id()).append('\n');
        }
        check("每张纸都走得到、够得着（" + count + " 张）", bad == 0, bad + " 张读不到：" + first);
        try {
            java.nio.file.Path out = java.nio.file.Path.of("build", "notes-test.txt");
            java.nio.file.Files.createDirectories(out.getParent());
            java.nio.file.Files.writeString(out, script.toString());
        } catch (java.io.IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static boolean inBox(Parts.Box b, double x, double y, double z) {
        return x > b.x() && x < b.x() + b.sx() && y > b.y() && y < b.y() + b.sy() && z > b.z() && z < b.z() + b.sz();
    }

    private static boolean clearSight(ParkPlan plan, java.util.List<Parts.Box> near, double ex, double ey, double ez, double ax, double ay, double az) {
        double dx = ax - ex, dy = ay - ey, dz = az - ez, len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        for (double t = 0.1; t < len; t += 0.05) {
            double k = t / len;
            double py = ey + dy * k, fy = py - Math.floor(py);
            for (Parts.Box box : near) if (inBox(box, ex + dx * k, py, ez + dz * k)) return false;
            B b = plan.block((int) Math.floor(ex + dx * k), (int) Math.floor(py), (int) Math.floor(ez + dz * k));
            if (b == null || b.name().startsWith("LIGHT_")) continue;
            // 半砖和地毯只占一部分，纸可以放在上面
            boolean inside = switch (b) {
                case BENCH, WALK_B -> fy < 0.5;
                case TABLE, WALK_T -> fy >= 0.5;
                case DEBRIS, DEBRIS_DARK -> fy < 0.0625;
                default -> true;
            };
            if (inside) return false;
        }
        return true;
    }

    /** 从入口按格子走一遍：能迈上 1 格、能往下跳。两道关着的门算墙。 */
    private static java.util.Set<Long> flood(ParkPlan plan, int[] start) {
        java.util.Set<Long> seen = new java.util.HashSet<>();
        java.util.ArrayDeque<int[]> queue = new java.util.ArrayDeque<>();
        int[] s = start;
        for (int y = s[1] + 2; y >= s[1] - 3; y--) {
            if (stand(plan, s[0], y, s[2])) {
                queue.add(new int[]{s[0], y, s[2]});
                seen.add(cell(s[0], y, s[2]));
                break;
            }
        }
        int[][] around = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        while (!queue.isEmpty() && seen.size() < 400_000) {
            int[] c = queue.poll();
            for (int[] d : around) {
                int nx = c[0] + d[0], nz = c[2] + d[1];
                if (gate(nx, c[1], nz)) continue;
                for (int ny = c[1] + 1; ny >= c[1] - 4; ny--) {
                    if (!free(plan, nx, ny, nz)) {
                        if (ny <= c[1]) break;
                        continue;
                    }
                    if (!stand(plan, nx, ny, nz)) continue;
                    if (seen.add(cell(nx, ny, nz))) queue.add(new int[]{nx, ny, nz});
                    break;
                }
            }
        }
        return seen;
    }

    private static boolean gate(int x, int y, int z) {
        return (x == Lower.LOCKER_X0 - 1 || x == Lower.LOCKER_X1 + 1) && z >= 0 && z <= 2 && y >= Lower.GARDEN_FLOOR - 2 && y <= Lower.GARDEN_FLOOR + 2;
    }

    private static boolean free(ParkPlan plan, int x, int y, int z) {
        B a = plan.block(x, y, z), b = plan.block(x, y + 1, z);
        return (a == null || a.isWalkable()) && (b == null || b.isWalkable());
    }

    private static boolean stand(ParkPlan plan, int x, int y, int z) {
        if (!free(plan, x, y, z)) return false;
        B here = plan.block(x, y, z), below = plan.block(x, y - 1, z);
        if (here != null && !here.name().startsWith("LIGHT_")) return true;
        return below != null && !below.name().startsWith("LIGHT_");
    }

    private static long cell(int x, int y, int z) {
        return ((long) (x + 1000) << 40) | ((long) (y + 1000) << 20) | (z + 1000);
    }

    private static void check(String name, boolean ok, String detail) {
        if (ok) {
            passed++;
            System.out.println("通过：" + name);
        } else {
            failed++;
            System.out.println("失败：" + name + "（" + detail + "）");
        }
    }
}
