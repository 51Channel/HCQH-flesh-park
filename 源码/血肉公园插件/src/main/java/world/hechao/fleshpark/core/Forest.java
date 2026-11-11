package world.hechao.fleshpark.core;

import world.hechao.fleshpark.core.Parts.Box;
import world.hechao.fleshpark.core.Parts.Text;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * 支气管森林：肉色的气管从头顶一路分叉下来，像倒着长的树林。木栈道从护林站往下绕，走到一辆半陷在肉里的护林员车。
 * 呼气以后从另一条栈道跑到电梯。
 */
public final class Forest {
    public static final double[] STATION = {300, 176, -60};
    /** 走进去的主路。 */
    public static final double[][] WALK = {{300, 176, -58}, {307, 176, -52}, {315, 172, -47}, {330, 166, -40}, {348, 160, -28}, {356, 152, -8}, {350, 146, 12}, {334, 140, 24}, {323, 134, 30}};
    public static final double[][] FORK1 = {{348, 160, -28}, {368, 158, -40}, {382, 156, -50}};
    public static final double[][] FORK2 = {{350, 146, 12}, {372, 142, 14}, {388, 140, 10}};
    /** 呼气以后跑的路。 */
    public static final double[][] EXIT = {{307, 134, 24}, {300, 130, 20}, {288, 124, 0}, {292, 118, -24}, {312, 112, -40}, {340, 106, -44}, {366, 100, -30}, {380, 94, -6}, {388, 91, 4}, {387, 90, 11.5}, {384.5, 90, 11.5}};
    public static final double[] VEHICLE = {317, 134, 30};
    public static final int CAB_X = 380, CAB_Y = 90, CAB_Z = 10;

    /** 系绳的灯和绳：按顺序，序号大的靠近护林员车。 */
    public final List<Parts.Beam> tether = new ArrayList<>();
    public final List<Box> tetherLamps = new ArrayList<>();
    /** 走进去这条路的采样点，给系绳和栈道用。 */
    public final List<double[]> walkLine = new ArrayList<>();
    public final List<double[]> exitLine = new ArrayList<>();
    /** 枝干上站着的人形：x、y、z。 */
    public final List<double[]> watchers = new ArrayList<>();
    /** 呼气前挡住出路的格子。 */
    public final List<int[]> exitGate = new ArrayList<>();
    /** 封闭步道尽头那个背包的位置。 */
    public double[] deadEnd;

    private final ParkPlan p;
    private final Random rnd;
    private final List<double[]> allLines = new ArrayList<>();
    /** 栈道每一格的面高。两条路会上下交叉，所以分开记。 */
    private final Map<Long, Double> surface = new HashMap<>(), lowerSurface = new HashMap<>();

    Forest(ParkPlan plan) {
        p = plan;
        rnd = plan.rnd;
    }

    void build() {
        walkLine.addAll(smooth(WALK));
        exitLine.addAll(smooth(EXIT));
        List<double[]> f1 = smooth(FORK1), f2 = smooth(FORK2);
        raster(walkLine, surface);
        raster(f1, surface);
        raster(f2, surface);
        raster(exitLine, lowerSurface);
        allLines.addAll(walkLine);
        allLines.addAll(f1);
        allLines.addAll(f2);
        allLines.addAll(exitLine);
        mound();
        station();
        surfaceBlocks();
        rails(walkLine);
        rails(f1);
        rails(f2);
        rails(exitLine);
        forkEnds(f1, f2);
        tether();
        vehicle();
        cab(CAB_X, CAB_Y, CAB_Z);
        // 森林这部的楼层显示是剧情里换字的，不在这里写死
        cabPanel(p, CAB_X, CAB_Y, CAB_Z, null, 0);
        exitGate();
        lamps(walkLine, 12);
        lamps(exitLine, 15);
        List<double[]> keep = new ArrayList<>();
        keep.add(new double[]{288, 172, -72, 313, 186, -50});
        keep.add(new double[]{306, 131, 20, 326, 142, 40});
        keep.add(new double[]{CAB_X - 4, CAB_Y - 2, CAB_Z - 6, CAB_X + 10, CAB_Y + 8, CAB_Z + 8});
        p.forestChamber = Chamber.forest(List.of(walkLine, f1, f2, exitLine), keep);
        watchers();
    }

    /** 三次切角，把折线磨圆，再按 0.25 米一个点取样。 */
    private static List<double[]> smooth(double[][] points) {
        List<double[]> line = new ArrayList<>(List.of(points));
        for (int k = 0; k < 3; k++) {
            List<double[]> next = new ArrayList<>();
            next.add(line.get(0));
            for (int i = 0; i < line.size() - 1; i++) {
                double[] a = line.get(i), b = line.get(i + 1);
                next.add(mix(a, b, 0.25));
                next.add(mix(a, b, 0.75));
            }
            next.add(line.get(line.size() - 1));
            line = next;
        }
        List<double[]> out = new ArrayList<>();
        for (int i = 0; i < line.size() - 1; i++) {
            double[] a = line.get(i), b = line.get(i + 1);
            double len = dist(a, b);
            int n = Math.max(1, (int) Math.ceil(len / 0.25));
            for (int j = 0; j < n; j++) out.add(mix(a, b, (double) j / n));
        }
        out.add(line.get(line.size() - 1));
        return out;
    }

    private static double[] mix(double[] a, double[] b, double k) {
        return new double[]{a[0] + (b[0] - a[0]) * k, a[1] + (b[1] - a[1]) * k, a[2] + (b[2] - a[2]) * k};
    }

    public static double dist2(double[] s, double x, double y, double z) {
        return (s[0] - x) * (s[0] - x) + (s[1] - y) * (s[1] - y) + (s[2] - z) * (s[2] - z);
    }

    static double dist(double[] a, double[] b) {
        return Math.sqrt((a[0] - b[0]) * (a[0] - b[0]) + (a[1] - b[1]) * (a[1] - b[1]) + (a[2] - b[2]) * (a[2] - b[2]));
    }

    private static long key(int x, int z) {
        return ((long) (x + 4096)) << 20 | (long) (z + 4096);
    }

    /** 栈道宽 3 米：每格记住走到这里时的高度，取最先到的。高度只有整数和半格，半格一步能走上去。 */
    private void raster(List<double[]> line, Map<Long, Double> map) {
        for (double[] s : line) {
            int cx = (int) Math.floor(s[0]), cz = (int) Math.floor(s[2]);
            double h = Math.round(s[1] * 2) / 2.0;
            for (int x = cx - 2; x <= cx + 2; x++) {
                for (int z = cz - 2; z <= cz + 2; z++) {
                    if (Math.hypot(x + 0.5 - s[0], z + 0.5 - s[2]) > 1.7) continue;
                    map.putIfAbsent(key(x, z), h);
                }
            }
        }
    }

    private void surfaceBlocks() {
        for (Map<Long, Double> map : List.of(surface, lowerSurface)) {
            for (Map.Entry<Long, Double> e : map.entrySet()) {
                int x = (int) (e.getKey() >> 20) - 4096, z = (int) (e.getKey() & 0xfffff) - 4096;
                double h = e.getValue();
                int fy = (int) Math.floor(h);
                if (h > fy) p.set(x, fy, z, B.WALK_B);
                else p.set(x, fy - 1, z, B.WALK_T);
            }
        }
        // 栈道两边的空气墙，掉不下去
        for (Map<Long, Double> map : List.of(surface, lowerSurface)) {
            for (Map.Entry<Long, Double> e : map.entrySet()) {
                int x = (int) (e.getKey() >> 20) - 4096, z = (int) (e.getKey() & 0xfffff) - 4096;
                int fy = (int) Math.floor(e.getValue());
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (onWalk(x + dx, z + dz, fy)) continue;
                        for (int y = fy - 1; y <= fy + 3; y++) if (p.block(x + dx, y, z + dz) == null) p.set(x + dx, y, z + dz, B.BARRIER);
                    }
                }
            }
        }
    }

    private boolean onWalk(int x, int z, int fy) {
        Double a = surface.get(key(x, z)), b = lowerSurface.get(key(x, z));
        return (a != null && Math.abs(a - fy) < 6) || (b != null && Math.abs(b - fy) < 6);
    }

    /** 车陷在这堆肉里。 */
    private void mound() {
        int cx = (int) VEHICLE[0], cy = (int) VEHICLE[1], cz = (int) VEHICLE[2];
        for (int x = cx - 12; x <= cx + 12; x++) {
            for (int z = cz - 12; z <= cz + 12; z++) {
                double r = Math.hypot(x + 0.5 - cx, z + 0.5 - cz);
                if (r > 11.5) continue;
                surface.put(key(x, z), (double) cy);
                lowerSurface.put(key(x, z), (double) cy);
                for (int y = cy - 7 + (int) (r / 2.2); y < cy; y++) p.set(x, y, z, ParkPlan.flesh(x, y, z));
            }
        }
    }

    /** 护林站：一块平台、一间小木屋。 */
    private void station() {
        int y = 176;
        for (int x = 294; x <= 310; x++) for (int z = -68; z <= -54; z++) surface.put(key(x, z), (double) y);
        for (int x = 303; x <= 309; x++) {
            for (int z = -68; z <= -63; z++) {
                boolean edge = x == 303 || x == 309 || z == -68 || z == -63;
                for (int yy = y; yy <= y + 3; yy++) if (edge && !(z == -63 && x >= 305 && x <= 306 && yy <= y + 2)) p.set(x, yy, z, B.HUT);
                p.set(x, y + 4, z, B.WALL_STRIP);
            }
        }
        p.texts.add(new Text("第七护林站\n支气管森林  ·  冬季开放时间", 306, y + 3.15, -62.95, 0f, 0xf2e8cc, 0.9f, 0xff2c4a3a, 9));
        p.boxes.add(new Box("minecraft:polished_deepslate", 300.6, y, -56.2, 1.8, 1.0, 0.8, -1));
        p.light(301, y + 2, -58, 7);
        p.light(306, y + 2, -60, 5);
        p.texts.add(new Text("冬季开放时间", 299, y + 2.4, -66, 0f, 0xc8b890, 1.4f, 0xff33261c, 3));
        for (double zz : new double[]{-66.1}) p.boxes.add(new Box("minecraft:polished_deepslate", 298.9, y, zz, 0.2, 2.4, 0.1, -1));
        // 腺泡的出口：一团肉堆在平台西边
        for (int i = 0; i < 6; i++) {
            double a = i * 1.05;
            p.rope(List.of(new double[]{293.5 + Math.cos(a) * 2, 178 + Math.sin(a) * 2, -65.5 + Math.sin(a * 1.7)}, new double[]{291 + Math.cos(a) * 1.6, 178.4 + Math.sin(a) * 1.4, -65.5 + Math.cos(a) * 1.8}, new double[]{287 + Math.cos(a), 178.4 + Math.sin(a), -65.5 + Math.cos(a)}), 0.7, i % 2, 0.5, false);
        }
    }

    private void rails(List<double[]> line) {
        int every = 12;
        double[] prevL = null, prevR = null;
        for (int i = 0; i < line.size() - every; i += every) {
            double[] s = line.get(i), n = line.get(i + every);
            double dx = n[0] - s[0], dz = n[2] - s[2], len = Math.max(0.01, Math.hypot(dx, dz));
            double ox = -dz / len * 1.75, oz = dx / len * 1.75;
            double h = Math.round(s[1] * 2) / 2.0;
            double[] l = {s[0] + ox, h, s[2] + oz}, r = {s[0] - ox, h, s[2] - oz};
            p.beams.add(new Parts.Beam("minecraft:gray_concrete", l[0], h, l[2], l[0], h + 1.05, l[2], 0.07, -1));
            p.beams.add(new Parts.Beam("minecraft:gray_concrete", r[0], h, r[2], r[0], h + 1.05, r[2], 0.07, -1));
            if (prevL != null && dist(prevL, l) < 5) {
                p.beams.add(new Parts.Beam("minecraft:gray_concrete", prevL[0], prevL[1] + 1.05, prevL[2], l[0], h + 1.05, l[2], 0.05, -1));
                p.beams.add(new Parts.Beam("minecraft:gray_concrete", prevR[0], prevR[1] + 1.05, prevR[2], r[0], h + 1.05, r[2], 0.05, -1));
            }
            prevL = l;
            prevR = r;
            // 吊索
            if (i % (every * 6) == 0) {
                p.beams.add(new Parts.Beam("minecraft:gray_concrete", l[0], h + 1, l[2], l[0] + 1, h + 34, l[2], 0.03, -1));
                p.beams.add(new Parts.Beam("minecraft:gray_concrete", r[0], h + 1, r[2], r[0] - 1, h + 34, r[2], 0.03, -1));
            }
        }
    }

    private void forkEnds(List<double[]> f1, List<double[]> f2) {
        double[] a = f1.get(f1.size() - 1), b = f2.get(f2.size() - 1);
        deadEnd = a.clone();
        // 死路：一个背包、一根登山杖，再往前全是肉
        p.boxes.add(new Box("minecraft:brown_wool", a[0] - 0.3, a[1], a[2] + 0.3, 0.5, 0.7, 0.34, -1));
        p.beams.add(new Parts.Beam("minecraft:iron_block", a[0] + 0.6, a[1], a[2], a[0] + 1.1, a[1] + 1.2, a[2] + 0.4, 0.04, -1));
        p.texts.add(new Text("步道已封闭", a[0] + 0.5, a[1] + 1.6, a[2] - 1.2, 0f, 0xf4ecd8, 0.9f, 0xffa02818, 4));
        p.posters.add(new Parts.Poster("poster_map", b[0], b[1] + 1.6, b[2] + 1.6, 0f, 1.6, 1.2, 3));
        p.boxes.add(new Box("minecraft:polished_deepslate", b[0] - 0.05, b[1], b[2] + 1.65, 0.1, 1.1, 0.1, -1));
        for (double[] e : new double[][]{a, b}) {
            for (int i = 0; i < 5; i++) {
                double t = i * 1.3;
                p.rope(List.of(new double[]{e[0] + 4 + Math.cos(t) * 2, e[1] + 4 + Math.sin(t) * 2, e[2] + Math.sin(t * 2) * 2}, new double[]{e[0] + 2.2 + Math.cos(t), e[1] + 1 + Math.sin(t), e[2] + Math.sin(t * 2)}, new double[]{e[0] + 2.4 + Math.cos(t) * 1.6, e[1] - 2, e[2] + Math.cos(t) * 1.6}), 0.75, i % 2, 0.6, false);
            }
        }
    }

    /** 当年勘探留下的系绳：栈道一侧一根绳，隔一段一盏小灯。 */
    private void tether() {
        int every = 28;
        double[] prev = null;
        for (int i = 0; i < walkLine.size() - every; i += every) {
            double[] s = walkLine.get(i), n = walkLine.get(i + every);
            double dx = n[0] - s[0], dz = n[2] - s[2], len = Math.max(0.01, Math.hypot(dx, dz));
            double[] at = {s[0] + dz / len * 1.2, Math.round(s[1] * 2) / 2.0 + 0.72, s[2] - dx / len * 1.2};
            if (prev != null) tether.add(new Parts.Beam("minecraft:white_concrete", prev[0], prev[1], prev[2], at[0], at[1], at[2], 0.018, -1));
            if (i % (every * 3) == 0) tetherLamps.add(new Box("minecraft:orange_concrete", at[0] - 0.035, at[1] - 0.07, at[2] - 0.035, 0.07, 0.07, 0.07, 1));
            prev = at;
        }
    }

    /** 被困的 14 号护林员车。人从车尾进去，电台在仪表盘上，纸在副驾座位上。 */
    private void vehicle() {
        int y = (int) VEHICLE[1];
        // 车厢的空气墙
        for (int x = 313; x <= 319; x++) {
            for (int yy = y; yy <= y + 2; yy++) {
                p.set(x, yy, 28, B.BARRIER);
                p.set(x, yy, 32, B.BARRIER);
            }
            for (int z = 29; z <= 31; z++) p.set(x, y + 3, z, B.BARRIER);
        }
        for (int yy = y; yy <= y + 2; yy++) for (int z = 29; z <= 31; z++) p.set(313, yy, z, B.BARRIER);
        double fx = 313.9, rx = 320.1, z0 = 28.9, z1 = 32.1, top = y + 2.5;
        String body = "minecraft:light_gray_concrete", trim = "minecraft:green_concrete", dark = "minecraft:polished_deepslate", glass = "minecraft:light_gray_stained_glass";
        // 地板、车顶、两侧下半截、两侧车窗、前脸
        p.boxes.add(new Box(dark, fx, y - 0.08, z0, rx - fx, 0.1, z1 - z0, -1));
        p.boxes.add(new Box(body, fx, top, z0, rx - fx, 0.14, z1 - z0, -1));
        for (double z : new double[]{z0 - 0.05, z1 - 0.05}) {
            p.boxes.add(new Box(body, fx, y, z, rx - fx, 1.05, 0.1, -1));
            p.boxes.add(new Box(trim, fx, y + 1.05, z, rx - fx, 0.14, 0.1, -1));
            p.boxes.add(new Box(glass, fx, y + 1.19, z + 0.03, rx - fx, 1.31, 0.04, -1));
            for (double x : new double[]{fx, fx + 2, fx + 4, rx - 0.1}) p.boxes.add(new Box(dark, x, y + 1.19, z, 0.1, 1.31, 0.1, -1));
        }
        p.boxes.add(new Box(body, fx - 0.1, y, z0, 0.1, 1.05, z1 - z0, -1));
        p.boxes.add(new Box(glass, fx - 0.07, y + 1.05, z0, 0.04, 1.45, z1 - z0, -1));
        // 车尾的门开着
        p.boxes.add(new Box(body, rx, y, z1 - 0.1, 1.5, 2.5, 0.08, -1));
        // 仪表盘和座椅
        p.boxes.add(new Box(dark, fx + 0.1, y + 0.75, z0 + 0.1, 0.6, 0.3, z1 - z0 - 0.2, -1));
        p.boxes.add(new Box("minecraft:gray_wool", fx + 1.4, y + 0.42, z0 + 0.25, 0.7, 0.14, 1.0, -1));
        p.boxes.add(new Box("minecraft:gray_wool", fx + 2.1, y + 0.42, z0 + 0.25, 0.14, 0.9, 1.0, -1));
        p.boxes.add(new Box("minecraft:gray_wool", fx + 1.4, y + 0.42, z1 - 1.25, 0.7, 0.14, 1.0, -1));
        p.boxes.add(new Box("minecraft:gray_wool", fx + 2.1, y + 0.42, z1 - 1.25, 0.14, 0.9, 1.0, -1));
        // 大轮子：半埋着
        for (double[] w : new double[][]{{315, z0 - 0.5}, {319, z0 - 0.5}, {315, z1 + 0.5}, {319, z1 + 0.5}}) {
            p.pipes.add(new Parts.Pipe(2, w[0], y - 0.05, w[1] - 0.3, w[0], y - 0.05, w[1] + 0.3, 0.72));
        }
        p.texts.add(new Text("护林员 14 号车", 317, y + 0.42, z1 + 0.02, 0f, 0x2c4a3a, 1.2f, 0, -1));
        p.light(316, y + 2, 30, 3);
        // 肉把车爬满了
        for (int i = 0; i < 9; i++) {
            double t = i * 0.8, x = fx + rnd.nextDouble() * 6;
            p.rope(List.of(new double[]{x - 2 + Math.sin(t), y - 0.6, z0 - 3.2}, new double[]{x + Math.cos(t) * 0.5, y + 1.2, z0 - 1.1}, new double[]{x + Math.sin(t) * 0.6, top + 0.3, z0 - 0.9}, new double[]{x + Math.sin(t) * 0.8, top + 0.72, z0 + 0.7}, new double[]{x + 0.4, top + 0.72, z1 - 0.7}, new double[]{x + Math.sin(t) * 0.5, top + 0.3, z1 + 0.9}, new double[]{x + Math.sin(t) * 0.5, y + 1.2, z1 + 1.1}), 0.28 + rnd.nextDouble() * 0.26, i % 2, 0.36, false);
        }
        for (int i = 0; i < 4; i++) {
            double z = z0 + 0.4 + i * 0.8;
            p.rope(List.of(new double[]{fx - 4, y + 0.1, z + (i - 1.5) * 2}, new double[]{fx - 1.8, y + 1, z}, new double[]{fx - 0.9, y + 2.2, z}, new double[]{fx - 0.6, top + 0.5, z}, new double[]{fx + 1.0, top + 0.7, z}), 0.42, i % 2, 0.4, false);
        }
        p.texts.add(new Text("牡蛎之耻", 310, y + 2.3, 22.3, 0f, 0xf2e8cc, 1.1f, 0xff33261c, 3));
        p.boxes.add(new Box("minecraft:polished_deepslate", 309.95, y, 22.2, 0.1, 2.3, 0.1, -1));
    }

    /** 电梯：门朝 +x。结局时人在三部一模一样的电梯之间换，门关着看不出来。 */
    void cab(int x0, int y, int z0) {
        cabAt(p, x0, y, z0);
    }

    /** 楼层显示，钉在评分机上方的正墙上。 */
    public static final double FLOOR_SIGN_Y = 2.3;

    /**
     * 每部电梯里评分机左手边一块楼层牌：上面三层是游客能去的，下面隔一条“员工专用”，灰着一层停用的：
     * 2007 年真游客中心的 B1。楼塌进井里以后，这部电梯往下通的就是那口井。floor 不为空时，评分机上方再挂一块写死的楼层显示。
     */
    public static void cabPanel(ParkPlan p, int x0, int y, int z0, String floor, int floorColor) {
        double x = x0 + 0.03, z = z0 + 2.55;
        p.texts.add(new Text("地面车站\n下游客中心\n支气管森林", x, y + 1.36, z, -90f, 0xf2e8cc, 0.36f, 0xff1b1a18, 12));
        p.texts.add(new Text("额定载重  1000 kg\n限乘  13 人", x, y + 0.82, z, -90f, 0xb0a498, 0.36f, 0xff1b1a18, 9));
        if (floor != null) p.texts.add(new Text(floor, x0 + 0.04, y + FLOOR_SIGN_Y, z0 + 1.5, -90f, floorColor, 0.5f, 0xff0a0806, 15));
    }

    public static void cabAt(ParkPlan p, int x0, int y, int z0) {
        for (int x = x0 - 1; x <= x0 + 3; x++) {
            for (int z = z0 - 1; z <= z0 + 3; z++) {
                p.set(x, y - 1, z, B.GRATE);
                p.set(x, y + 3, z, B.CAB_WALL);
                for (int yy = y; yy <= y + 2; yy++) {
                    boolean edge = x == x0 - 1 || z == z0 - 1 || z == z0 + 3;
                    if (edge) p.set(x, yy, z, B.CAB_WALL);
                    else p.clear(x, yy, z);
                }
            }
        }
        p.light(x0 + 1, y + 2, z0 + 1, 9);
        p.boxes.add(new Box("minecraft:ochre_froglight", x0 + 0.5, y + 2.92, z0 + 0.5, 2, 0.06, 2, 14));
    }

    private void exitGate() {
        // 呼气前这条路走不通
        for (int z = 20; z <= 28; z++) {
            for (int y = 133; y <= 137; y++) {
                if (p.block(306, y, z) == null || p.block(306, y, z) == B.BARRIER) {
                    p.set(306, y, z, B.BARRIER);
                    exitGate.add(new int[]{306, y, z});
                }
            }
        }
        p.texts.add(new Text("出站电梯", 305, 137.2, 23, -90f, 0x2a1408, 0.9f, 0xff403020, 0));
        double[] e = exitLine.get(exitLine.size() - 1);
        p.texts.add(new Text("出口\n出站电梯", e[0] - 2.5, 94.4, e[2] + 0.5, -90f, 0xb8ffc8, 1.3f, 0xff0d5a2a, 15));
        p.light(CAB_X + 5, CAB_Y + 2, CAB_Z + 1, 9);
    }

    /** 倒着长的树：从高处往下分叉，越分越细。走道附近的肉索挪开。 */
    /** 沿路的工作灯：一盏一盏把身边的肉照出来，灯和灯之间是黑的。 */
    private void lamps(List<double[]> line, double spacing) {
        double walked = 0;
        double[] last = null;
        boolean left = true;
        for (int i = 0; i < line.size() - 1; i++) {
            double[] s = line.get(i);
            if (last != null) walked += dist(s, last);
            last = s;
            if (walked < spacing) continue;
            walked = 0;
            double[] n = line.get(i + 1);
            double dx = n[0] - s[0], dz = n[2] - s[2], len = Math.max(0.01, Math.hypot(dx, dz));
            double side = left ? 1.75 : -1.75;
            left = !left;
            double x = s[0] - dz / len * side, z = s[2] + dx / len * side, h = Math.round(s[1] * 2) / 2.0;
            p.beams.add(new Parts.Beam("minecraft:gray_concrete", x, h, z, x, h + 2.4, z, 0.05, -1));
            p.boxes.add(new Box("minecraft:shroomlight", x - 0.12, h + 2.4, z - 0.12, 0.24, 0.1, 0.24, 15));
            p.light((int) Math.floor(s[0]), (int) Math.floor(h) + 3, (int) Math.floor(s[2]), 15);
        }
    }

    public boolean nearWalk(double x, double y, double z, double r) {
        double[] q = {x, y, z};
        for (int i = 0; i < allLines.size(); i += 6) {
            double[] s = allLines.get(i);
            double dx = q[0] - s[0], dz = q[2] - s[2], dy = q[1] - s[1] - 1;
            if (dx * dx + dy * dy + dz * dz < r * r) return true;
        }
        return false;
    }

    /** 呼气时枝干上站着的人形：沿出路两边，离栈道十来米。 */
    private void watchers() {
        for (int i = 80; i < exitLine.size() - 40; i += 72) {
            double[] s = exitLine.get(i);
            double a = rnd.nextDouble() * 6.28, d = 9 + rnd.nextDouble() * 9;
            double x = s[0] + Math.cos(a) * d, z = s[2] + Math.sin(a) * d, y = s[1] + rnd.nextDouble() * 5 - 1;
            watchers.add(new double[]{x, y, z});
            // 脚下垫一根肉索
            p.rope(List.of(new double[]{x - 3, y - 2.2, z - 2}, new double[]{x, y - 0.5, z}, new double[]{x + 3, y - 1.8, z + 2.4}), 0.8, 0, 0.7, false);
        }
    }

    /** 这条线上离 (x,z) 最近的点的序号。 */
    public static int nearest(List<double[]> line, double x, double y, double z) {
        int best = 0;
        double bestD = Double.MAX_VALUE;
        for (int i = 0; i < line.size(); i += 2) {
            double[] s = line.get(i);
            double d = (s[0] - x) * (s[0] - x) + (s[2] - z) * (s[2] - z) + (s[1] - y) * (s[1] - y) * 0.5;
            if (d < bestD) {
                bestD = d;
                best = i;
            }
        }
        return best;
    }
}
