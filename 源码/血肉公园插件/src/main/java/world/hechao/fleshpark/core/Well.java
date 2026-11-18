package world.hechao.fleshpark.core;

import world.hechao.fleshpark.core.Parts.Beam;
import world.hechao.fleshpark.core.Parts.Box;
import world.hechao.fleshpark.core.Parts.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 第四关深井、第五关井底的真游客中心。
 * 井分三段：井口一圈宽，压着上面那座楼的底（肉根、六根支撑的断头）；往下收成一段窄的肉喉咙；喉咙底下突然撑开，大腔里躺着 2007 年塌下去的那座楼。
 * 森林那部电梯掉下来，卡在喉咙口的井壁里。门外是一块旧员工平台，平台起，肉壁上长出一道台阶，贴着井壁一圈一圈螺旋往下，三圈多。
 * 台阶是它照着楼里的楼梯长的：一级一级嵌在肉里，形状对，高矮不对。台阶外侧就是井心，头顶是肉壁压下来的槽，有两处肉箍压得要蹲着过。
 * 4 号支撑从井口斜着插下来，脚扎在第一圈快走完的井壁上；搜救队的绳顺着南边的井壁垂下来，早被肉吃进墙里，只在台阶这一层露出来，到最下面那块平台就没了。
 * 平台北边挂着 807 号车，顺着断缆滑出喉咙，落到真楼的 3 号码头外面。
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
    /** 井的几段：井口那一圈在 NECK_Y 以上，喉咙从 THROAT_TOP 到 THROAT_BOTTOM，大腔的顶在 BULB_Y。 */
    public static final int NECK_Y = 172, THROAT_TOP = 164, THROAT_BOTTOM = 70, BULB_Y = 52;
    /** 喉咙上口、下口的半径；井口一圈、大腔的半径。 */
    static final double THROAT_R0 = 16, THROAT_R1 = 12.5, NECK_R = 26, BULB_R = 46;

    /** 卡住的电梯：门朝 +x（井心），和森林那部一模一样。门外 x = CAB_X + 4 起是平台。 */
    public static final int CAB_X = 489, CAB_Y = 164, CAB_Z = -1;

    /** 最底下那块平台（防水袋、冷光棒、807 号车）：方块范围，人站的高度。台阶从西边接进来。 */
    public static final int BAG_Y = 72, BAG_X0 = 504, BAG_X1 = 511, BAG_Z0 = 9, BAG_Z1 = 13;
    /** 807 号车：开始挂在平台北边，车门对着平台，顺着断缆滑到井底北边。根实体的位置（车厢地板）。 */
    public static final double[] CAR_FROM = {BAG_X0 + 6.7, BAG_Y, BAG_Z0 - 2.6}, CAR_TO = {500, REAL_FLOOR, -34};
    /** 井底北边的落脚处。 */
    public static final int LAND_X0 = 486, LAND_X1 = 516, LAND_Z0 = -42, LAND_Z1 = -29;
    /** 冷光棒那一捆：插在平台靠井壁那一边，从台阶走上平台就在手边。 */
    public static final double[] STICKS = {BAG_X0 + 2.2, BAG_Y + 0.02, BAG_Z1 + 0.35};
    /** 花园层东头通隧道的口子：真楼里被肉堵死了。肉腔不往这一片长。 */
    public static final int ROOM_X0 = 153 + DX, ROOM_X1 = ROOM_X0 + 8, ROOM_Z0 = -3, ROOM_Z1 = 3;
    /** 第一关那座：要整栋复制的范围。 */
    private static final int SRC_X0 = 86, SRC_X1 = 163, SRC_Y0 = 16, SRC_Y1 = 52, SRC_Z0 = -30, SRC_Z1 = 30;

    // ---------- 往下的路：一处一处落脚的地方 ----------

    /**
     * 电梯门口那块旧员工平台：贴着井壁，φ 从 PHI0 到 LANDING_TO。φ 是绕井心转过的弧度（从西往南转，一路往下）。
     * 平台以下没有连着的路了：员工楼梯被吃断成一截一截，再往下只剩横穿井心的钢梁，最后只剩井壁上伸出来的肉块。
     */
    public static final double PHI0 = -0.14, LANDING_TO = 0.26;
    /** 平台中线离井心多远、往井心那边伸多远、往井壁那边伸多远。 */
    static final double LAND_R = THROAT_R0 + 0.4, LAND_IN = 2.1, LAND_OUT = 1.1;

    /** 一处落脚的地方：一截断楼梯、一根钢梁（或断开的一截）、一块肉、两头的平台。 */
    public static final class Hold {
        /** 'L' 电梯门口的平台、'S' 一截断楼梯、'B' 钢梁、'D' 一块楼塌下来卡在井壁上的残骸、'P' 最底下那块平台。 */
        public final char kind;
        public final int index;
        /** 人站的格子：{x, 脚下高度, z, 头顶压低（1 是肉箍底下）}。 */
        public final List<double[]> cells = new ArrayList<>();
        /** 落脚的点、走到头起跳（或迈下去）的点：{x, 脚下高度, z}。 */
        public double[] in, out;
        /** 贴井壁的：φ 范围、离井心的里外。 */
        public double phiA, phiB, rIn, rOut;
        /** 钢梁：顺着 x（0）还是顺着 z（1）；两头是不是断口（不是插进墙里）。 */
        public int axis = -1;
        public boolean brokenLo, brokenHi;
        /** 断楼梯：一级一级的 {φ 起, φ 止, 脚下高度}。 */
        public double[][] zones = new double[0][];
        /** 残骸是什么：SLAB 楼板、GRATING 格栅走道、DUCT 风管、SIGN 灯箱。 */
        public int style;
        /**
         * 断楼梯旧了的样子：整截往井心那边歪几度（起头、断口那头不一样，整截是拧着的；负的是往井壁那边歪），断口那头往下耷拉多少；
         * 人走的那条线离井心多远（歪是绕这条线歪的，人走的地方和脚下看不见的方块对得上）。
         */
        public double leanA, leanB, droop, rWalk;

        Hold(char kind, int index) {
            this.kind = kind;
            this.index = index;
        }

        public boolean onWall() {
            return kind == 'S' || kind == 'D' || kind == 'L';
        }

        public double top() {
            double t = -1e9;
            for (double[] c : cells) t = Math.max(t, c[1]);
            return t;
        }

        public double bottom() {
            double t = 1e9;
            for (double[] c : cells) t = Math.min(t, c[1]);
            return t;
        }

        /** 顺着往下走的朝向（度）。 */
        public float yaw() {
            double dx = out[0] - in[0], dz = out[2] - in[2];
            if (Math.abs(dx) + Math.abs(dz) < 0.3) return 0f;
            return (float) Math.toDegrees(Math.atan2(-dx, dz));
        }

        public double midPhi() {
            return (phiA + phiB) / 2;
        }

        /** 这一处的正中：{x, 脚下高度, z}。 */
        public double[] center() {
            double x = 0, z = 0;
            for (double[] c : cells) {
                x += c[0] + 0.5;
                z += c[2] + 0.5;
            }
            x /= cells.size();
            z /= cells.size();
            return new double[]{x, topNear(this, x, z), z};
        }

        /** 顺着这一截走到哪了：起头 0，断口 1。 */
        double along(double phi) {
            return Math.max(0, Math.min(1, (phi - phiA) / (phiB - phiA)));
        }

        /** 断楼梯 φ 处往井心那边歪了多少（弧度）。 */
        public double lean(double phi) {
            return Math.toRadians(leanA + (leanB - leanA) * along(phi));
        }

        /** 断楼梯歪了、断口那头耷拉下去，φ 处离井心 r 那一点比脚下的方块高多少（低是负的）。 */
        public double warp(double phi, double r) {
            double t = Math.max(0, (along(phi) - 0.8) / 0.2);
            return Math.tan(lean(phi)) * (r - rWalk) - droop * t * t;
        }

        /** 断楼梯 φ 处是哪一级：脚下的高度。 */
        public double zoneTop(double phi) {
            for (double[] z : zones) if (phi < z[1]) return z[2];
            return zones[zones.length - 1][2];
        }

        /** 断楼梯的踏板面在 φ、离井心 r 那一点有多高（看得见的那一面）。 */
        public double surface(double phi, double r) {
            return zoneTop(phi) + warp(phi, r);
        }

        /** 踏步前沿连成的线（扶手、斜梁顺着它走）：在第 k 级的前沿是这一级的高度，到下一级前沿降到下一级；最后一级接着往下斜。 */
        public double nose(double phi) {
            int n = zones.length;
            if (n == 1) return zones[0][2];
            for (int k = 0; k < n; k++) {
                double a = zones[k][0], b = zones[k][1];
                if (phi <= b || k == n - 1) {
                    double next = k + 1 < n ? zones[k + 1][2] : zones[k][2] - 0.5;
                    double t = Math.max(0, Math.min(1, (phi - a) / (b - a)));
                    return zones[k][2] + (next - zones[k][2]) * t;
                }
            }
            return zones[n - 1][2];
        }

        /** 扶手离井心多远。 */
        public double railR() {
            return rIn + 0.04;
        }

        /** 扶手立杆 φ 处、往上 up 那一点：立杆跟着楼梯一起往井心歪。 */
        public double[] rail(double phi, double up) {
            double r = railR(), l = lean(phi);
            double[] base = at(phi, r, nose(phi) + warp(phi, r));
            double t = Math.PI - phi;
            // 往井心那边：-径向
            return new double[]{base[0] - Math.cos(t) * Math.sin(l) * up, base[1] + Math.cos(l) * up, base[2] - Math.sin(t) * Math.sin(l) * up};
        }
    }

    /** (x, z) 绕井心转到哪个 φ（挪到离 near 最近的那一圈）。 */
    public static double phiNear(double x, double z, double near) {
        return unwrap(Math.PI - Math.atan2(z - SZ, x - SX), near);
    }

    /** 从电梯门口到最底下那块平台，按往下走的顺序。 */
    public static final List<Hold> HOLDS = new ArrayList<>();
    /** 哪一格上有落脚的地方：格子 → {第几处, 脚下高度}。 */
    private static final java.util.Map<Long, List<double[]>> CELLS = new java.util.HashMap<>();
    /** 肉腔模型里落脚处那一带要改的形状（见 shapes），按 4 格一块分好。 */
    private static final List<double[]> SHAPES = new ArrayList<>();
    private static final java.util.Map<Long, List<double[]>> SHAPE_GRID = new java.util.HashMap<>();
    /** 肉箍压着的那一段：φ 范围。 */
    private static double sqA, sqB;

    /**
     * 断楼梯：每截多长（中线上几格）、截里往下几级（一级半格）；跳过来之前空几格、落多少。
     * 第 SQUEEZE 截是平的，中间一段头顶压着一团肉（肉箍），只留 1.5 格，蹲着过。
     */
    private static final double[] ST_LEN = {5, 4, 6, 4, 5, 8, 4, 5, 6};
    private static final int[] ST_STEPS = {2, 1, 3, 2, 1, 0, 1, 1, 2};
    private static final double[] ST_GAP = {1.2, 1.3, 1.1, 1.5, 1.2, 1.1, 1.4, 1.2, 1.3};
    private static final double[] ST_DROP = {1.5, 2, 2.5, 1.5, 1.5, 2, 2.5, 2.5, 2.5};
    /** 每截靠井心那一沿、靠井壁那一沿离中线多远：有宽有窄，有的贴着井壁，有的探出去。 */
    private static final double[] ST_IN = {1.2, 0.85, 1.5, 0.9, 1.3, 1.2, 0.95, 1.35, 1.05};
    private static final double[] ST_OUT = {1.2, 1.0, 1.3, 1.05, 1.1, 1.2, 0.9, 1.3, 1.2};
    /** 每截里一级一级各占多长（按比例）：有的一级长、有的一级短。 */
    private static final double[][] ST_TREAD = {{1.5, 0.7, 1.1}, {0.75, 1.25}, {1.1, 0.7, 1.4, 0.8}, {1.25, 0.8, 0.95}, {1.45, 0.55}, {1}, {1.3, 0.7}, {0.8, 1.2}, {1.3, 0.75, 0.95}};
    /** 每截往井心那边歪几度（起头、断口），断口那头往下耷拉多少格。 */
    private static final double[][] ST_LEAN = {{1, 4}, {-2, -1}, {2, 7}, {5, 6}, {0, 2.5}, {1, 1}, {3, 8}, {-1, 2}, {4, 3}};
    private static final double[] ST_DROOP = {0.1, 0.06, 0.14, 0.08, 0.12, 0.04, 0.14, 0.07, 0.1};
    /** 肉箍压在第几截的哪一段（从这截起头量，几格到几格）。 */
    private static final int SQUEEZE_SEG = 5;
    private static final double SQUEEZE_FROM = 2, SQUEEZE_TO = 6;
    /** 断楼梯最后一截停在北边井壁、x = 512 这一列的东边：往西一迈，落到第一根钢梁上。 */
    private static final int FIRST_BEAM_X = 512;

    /**
     * 钢梁：{顺着 x 为 0、顺着 z 为 1, 固定的那一格, 脚下高度, 这一截从哪格, 到哪格（±99 一直到井壁）, 落脚那格, 走到哪格迈下去}。
     * 一根比一根低三格，十字交叉着往下：走到下一根正上方，往旁边一迈就落到它上面。中间两根断了：一根断口空一格，一根断口空一格、那头塌下去一格。
     */
    private static final int[][] BEAMS = {
            {1, FIRST_BEAM_X, 136, -99, 99, -13, -3},
            {0, -3, 133, -99, 99, 511, 504},
            {1, 504, 130, -99, 99, -4, -9},
            {0, -9, 127, -99, 509, 505, 509},
            {0, -9, 127, 511, 99, 511, 516},
            {1, 516, 124, -99, -1, -8, -1},
            {1, 516, 123, 1, 99, 1, 6},
            {0, 6, 120, -99, 99, 515, 506},
            {1, 506, 117, -99, 99, 5, 0},
            {0, 0, 114, -99, 99, 507, 511},
            {1, 511, 111, -99, 99, 1, 99},
    };

    /** 残骸：每块顺着井壁多宽（格）、跳过来要落多少、空多少（按比例摊开）。第一块在最后那根钢梁南头的东边，最后一块在平台西边。 */
    private static final double[] CH_SIZE = {3, 2.8, 3.4, 2.8, 3, 3.4, 2.8, 3, 3.4, 2.8, 3, 3.4, 2.8, 3, 3.4, 2.8};
    private static final double[] CH_DROP = {3, 2, 3, 2, 2, 3, 2, 2, 3, 2, 2, 3, 2, 2, 3, 2};
    private static final double[] CH_GAP = {0, 1.5, 1.8, 1.4, 2.0, 1.6, 1.5, 1.9, 1.4, 1.0, 1.6, 2.0, 1.5, 1.8, 1.4, 1.6};
    /** 残骸的样子：楼板、格栅走道、风管、灯箱。缠腿、吸脚、湿滑的都放在楼板、风管上。 */
    public static final int SLAB = 0, GRATING = 1, DUCT = 2, SIGN = 3;
    private static final int[] CH_STYLE = {SLAB, GRATING, DUCT, SLAB, SIGN, SLAB, GRATING, SLAB, DUCT, SLAB, GRATING, SLAB, SIGN, SLAB, GRATING, SLAB};
    /** 第一块、最后一块残骸的中心在哪个 φ。 */
    private static final double CH_FIRST = 8.11, CH_LAST = 13.47;

    /** 肉箍那一截、4 号支撑脚边那一截、搜救队的绳从旁边垂下去那一截：第几处。 */
    public static final int SQUEEZE, TRUSS, ROPE_MARK;
    /** 墙里伸出肉索缠腿的、楼板上长了一层肉吸脚的、楼板湿滑站不住的：第几处。 */
    public static final int[] GRAB, SUCK, SLIP;
    /** 从第几处起，对面井壁上那串头灯跟着你。 */
    public static final int LAMPS_FROM;
    /** 第一根钢梁、第一块残骸、最底下那块平台：第几处。 */
    public static final int FIRST_BEAM, FIRST_DEBRIS, BAG;
    /** 4 号支撑的脚扎在哪个 φ 的井壁里。 */
    public static final double TRUSS_PHI;
    /** 搜救队的绳：顺着正南的井壁垂下来。最底下那块平台就在绳子底下。 */
    public static final double ROPE_ANGLE = Math.PI / 2;
    /** 走到这个高度以下，对面井壁上那串头灯开始跟着你。 */
    public static final double LAMPS_BELOW = 104;

    static {
        // 电梯门口的平台
        Hold land = new Hold('L', 0);
        land.phiA = PHI0;
        land.phiB = LANDING_TO;
        land.rIn = LAND_R - LAND_IN;
        land.rOut = LAND_R + LAND_OUT;
        arcCells(land, phi -> (double) CAB_Y, -1, -1);
        land.in = at(0.02, LAND_R - 0.3, CAB_Y);
        land.out = at(LANDING_TO - 0.04, LAND_R - 0.4, CAB_Y);
        add(land);

        // 断楼梯：先按原样排一遍，看最后一截停在哪，再把每截和空档一起放大缩小，让最后一截正好停到第一根钢梁的东边
        double[] rc = new double[ST_LEN.length];
        double[] top = new double[ST_LEN.length];
        double y = CAB_Y;
        for (int i = 0; i < ST_LEN.length; i++) {
            y -= ST_DROP[i];
            top[i] = y;
            rc[i] = throatR(y) - 1.3;
            y -= ST_STEPS[i] * 0.5;
        }
        double endPhi = Math.PI + Math.acos((FIRST_BEAM_X - SX) / rc[rc.length - 1]);
        double raw = LANDING_TO;
        for (int i = 0; i < ST_LEN.length; i++) raw += (ST_GAP[i] + ST_LEN[i]) / rc[i];
        double k = (endPhi - LANDING_TO) / (raw - LANDING_TO);
        double phi = LANDING_TO;
        int squeeze = -1, truss = -1, ropeMark = -1, grab0 = -1;
        for (int i = 0; i < ST_LEN.length; i++) {
            Hold s = new Hold('S', HOLDS.size());
            double len = ST_LEN[i] * k, r = rc[i], t0 = top[i];
            int steps = ST_STEPS[i];
            s.phiA = phi + ST_GAP[i] * k / r;
            s.phiB = s.phiA + len / r;
            s.rIn = r - ST_IN[i];
            s.rOut = r + ST_OUT[i];
            s.rWalk = r - 0.2;
            s.leanA = ST_LEAN[i][0];
            s.leanB = ST_LEAN[i][1];
            s.droop = ST_DROOP[i];
            double a = s.phiA;
            boolean sq = i == SQUEEZE_SEG;
            double[] w = ST_TREAD[i];
            double sum = 0, acc = 0;
            for (double v : w) sum += v;
            s.zones = new double[steps + 1][];
            for (int z = 0; z <= steps; z++) {
                double z0 = a + len / r * acc / sum;
                acc += w[z];
                s.zones[z] = new double[]{z0, a + len / r * acc / sum, t0 - 0.5 * z};
            }
            arcCells(s, s::zoneTop, sq ? a + SQUEEZE_FROM * k / r : -1, sq ? a + SQUEEZE_TO * k / r : -1);
            if (i == ST_LEN.length - 1) s.cells.removeIf(c -> c[0] <= FIRST_BEAM_X);
            s.in = at(s.phiA + 0.5 / r, r - 0.2, t0);
            s.out = at(s.phiB - 0.6 / r, r - 0.2, t0 - 0.5 * steps);
            phi = s.phiB;
            if (sq) squeeze = s.index;
            if (i == 7) truss = s.index;
            if (i == 3) grab0 = s.index;
            if (ropeMark < 0 && s.phiB > ROPE_ANGLE) ropeMark = s.index;
            add(s);
        }
        // 最后一截：起跳点挪到剩下那几格的西头
        Hold lastStair = HOLDS.get(HOLDS.size() - 1);
        double oz = 0;
        int n = 0;
        for (double[] c : lastStair.cells) if (c[0] == FIRST_BEAM_X + 1) {
            oz += c[2] + 0.5;
            n++;
        }
        lastStair.out = new double[]{FIRST_BEAM_X + 1.5, lastStair.cells.get(lastStair.cells.size() - 1)[1], n > 0 ? oz / n : lastStair.out[2]};
        for (double[] c : lastStair.cells) if (c[0] == FIRST_BEAM_X + 1) lastStair.out[1] = c[1];

        // 钢梁
        int firstBeam = HOLDS.size();
        for (int[] b : BEAMS) {
            Hold h = new Hold('B', HOLDS.size());
            h.axis = b[0];
            h.brokenLo = b[3] != -99;
            h.brokenHi = b[4] != 99;
            int fixed = b[1], feet = b[2];
            double wall = throatR(feet) - 0.9;
            for (int v = -40; v <= 40; v++) {
                int x = b[0] == 0 ? SX + v : fixed, z = b[0] == 0 ? fixed : SZ + v;
                int along = b[0] == 0 ? x : z;
                if (along < (b[3] == -99 ? -999 : b[3]) || along > (b[4] == 99 ? 999 : b[4])) continue;
                if (Math.hypot(x + 0.5 - SX, z + 0.5 - SZ) > wall) continue;
                h.cells.add(new double[]{x, feet, z, 0});
            }
            int inA = b[5], outA = b[6];
            if (outA == 99) {
                outA = -999;
                for (double[] c : h.cells) outA = Math.max(outA, (int) (b[0] == 0 ? c[0] : c[2]));
            }
            h.in = b[0] == 0 ? new double[]{inA + 0.5, feet, fixed + 0.5} : new double[]{fixed + 0.5, feet, inA + 0.5};
            h.out = b[0] == 0 ? new double[]{outA + 0.5, feet, fixed + 0.5} : new double[]{fixed + 0.5, feet, outA + 0.5};
            add(h);
        }

        // 残骸：第一块和最后一块的位置定死，中间的空档按比例摊开
        int firstMeat = HOLDS.size();
        double[] mt = new double[CH_SIZE.length], mr = new double[CH_SIZE.length];
        double yy = BEAMS[BEAMS.length - 1][2];
        for (int i = 0; i < CH_SIZE.length; i++) {
            yy -= CH_DROP[i];
            mt[i] = yy;
            mr[i] = throatR(yy) - 1.3;
        }
        double fixedArc = 0, gapArc = 0;
        for (int i = 1; i < CH_SIZE.length; i++) {
            fixedArc += (CH_SIZE[i - 1] / 2 + CH_SIZE[i] / 2) / mr[i];
            gapArc += CH_GAP[i] / mr[i];
        }
        double gk = (CH_LAST - CH_FIRST - fixedArc) / gapArc;
        double c0 = CH_FIRST;
        int[] suck = new int[2], slip = new int[2];
        int grab1 = -1, lampsFrom = -1;
        for (int i = 0; i < CH_SIZE.length; i++) {
            if (i > 0) c0 += (CH_SIZE[i - 1] / 2 + CH_GAP[i] * gk + CH_SIZE[i] / 2) / mr[i];
            Hold m = new Hold('D', HOLDS.size());
            m.style = CH_STYLE[i];
            double r = mr[i], half = CH_SIZE[i] / 2 / r, t0 = mt[i];
            m.phiA = c0 - half;
            m.phiB = c0 + half;
            boolean last = i == CH_SIZE.length - 1;
            // 最后一块挨着平台：往井壁里多陷一点，和平台对齐
            m.rIn = last ? 12.1 : r - 1.1;
            m.rOut = last ? 14.3 : r + 1.1;
            arcCells(m, q -> t0, -1, -1);
            double rm = (m.rIn + m.rOut) / 2;
            m.in = at(m.phiA + 0.45 / rm, rm, t0);
            m.out = at(m.phiB - 0.45 / rm, rm, t0);
            if (i == 5) suck[0] = m.index;
            if (i == 11) suck[1] = m.index;
            if (i == 3) slip[0] = m.index;
            if (i == 13) slip[1] = m.index;
            if (i == 8) grab1 = m.index;
            if (lampsFrom < 0 && t0 < LAMPS_BELOW) lampsFrom = m.index;
            add(m);
        }
        // 第一块肉：落脚点就是最后那根钢梁南头往东一迈的那一格
        Hold lastBeam = HOLDS.get(firstMeat - 1), meat0 = HOLDS.get(firstMeat);
        meat0.in = new double[]{lastBeam.out[0] + 1, meat0.top(), lastBeam.out[2]};

        // 最底下那块平台
        Hold bag = new Hold('P', HOLDS.size());
        for (int x = BAG_X0; x <= BAG_X1; x++) for (int z = BAG_Z0; z <= BAG_Z1; z++) bag.cells.add(new double[]{x, BAG_Y, z, 0});
        bag.in = new double[]{BAG_X0 + 0.5, BAG_Y, (BAG_Z0 + BAG_Z1) / 2.0 + 0.5};
        bag.out = new double[]{BAG_X0 + 3.5, BAG_Y, BAG_Z0 + 2.5};
        add(bag);
        Hold lastMeat = HOLDS.get(bag.index - 1);
        bag.in = new double[]{BAG_X0 + 0.5, BAG_Y, Math.max(BAG_Z0 + 0.5, Math.min(BAG_Z1 + 0.5, lastMeat.out[2]))};

        // 起跳点、落脚点挪到最近那一格的正中，免得算出来的点落在两格之间的缝上
        for (Hold h : HOLDS) {
            if (h.kind == 'B' || h.kind == 'P') continue;
            h.in = snap(h, h.in);
            h.out = snap(h, h.out);
        }
        meat0.in = new double[]{lastBeam.out[0] + 1, meat0.top(), lastBeam.out[2]};
        // 断楼梯的起跳点：最后一级里离下一截落脚点最近的那一格（人走到头往最近的地方跳；按弧线算的那一格可能离下一截还隔着缺的一角）。
        // 肉箍那一截、最后一截（往旁边一迈到钢梁上）不动
        for (int i = 1; i + 1 < firstBeam - 1; i++) {
            Hold h = HOLDS.get(i), next = HOLDS.get(i + 1);
            if (h.kind != 'S' || i == squeeze) continue;
            double best = Math.hypot(h.out[0] - next.in[0], h.out[2] - next.in[2]);
            for (double[] c : h.cells) {
                if (c[1] != h.out[1] || c[3] > 0) continue;
                double d = Math.hypot(c[0] + 0.5 - next.in[0], c[2] + 0.5 - next.in[2]);
                if (d < best - 1e-6) {
                    best = d;
                    h.out = new double[]{c[0] + 0.5, c[1], c[2] + 0.5};
                }
            }
        }

        SQUEEZE = squeeze;
        TRUSS = truss;
        ROPE_MARK = ropeMark;
        GRAB = new int[]{grab0, grab1};
        SUCK = suck;
        SLIP = slip;
        LAMPS_FROM = lampsFrom;
        FIRST_BEAM = firstBeam;
        FIRST_DEBRIS = firstMeat;
        BAG = bag.index;
        TRUSS_PHI = HOLDS.get(truss).midPhi();
        shapes();
    }

    /** 离 q 最近那一格的正中、那一格的脚下高度。 */
    private static double[] snap(Hold h, double[] q) {
        double best = 1e9;
        double[] at = q;
        for (double[] c : h.cells) {
            double d = Math.hypot(c[0] + 0.5 - q[0], c[2] + 0.5 - q[2]);
            if (d < best) {
                best = d;
                at = new double[]{c[0] + 0.5, c[1], c[2] + 0.5};
            }
        }
        return at;
    }

    private static void add(Hold h) {
        HOLDS.add(h);
        for (double[] c : h.cells) CELLS.computeIfAbsent(cellKey((int) c[0], (int) c[2]), q -> new ArrayList<>()).add(new double[]{h.index, c[1]});
    }

    /** 贴井壁的一处：中心落在 φ 范围、离井心 rIn 到 rOut 之间的格子都算；脚下高度按格子中心的 φ 算。sqA..sqB 之间头顶压低。 */
    private static void arcCells(Hold h, java.util.function.DoubleUnaryOperator top, double sqA, double sqB) {
        int reach = (int) Math.ceil(h.rOut) + 1;
        double mid = h.midPhi();
        for (int x = SX - reach; x <= SX + reach; x++) {
            for (int z = SZ - reach; z <= SZ + reach; z++) {
                double cx = x + 0.5 - SX, cz = z + 0.5 - SZ, rho = Math.hypot(cx, cz);
                if (rho < h.rIn || rho > h.rOut) continue;
                double phi = unwrap(Math.PI - Math.atan2(cz, cx), mid);
                if (phi < h.phiA || phi > h.phiB) continue;
                double t = top.applyAsDouble(phi);
                h.cells.add(new double[]{x, t, z, phi >= sqA && phi <= sqB ? 1 : 0});
            }
        }
    }

    /** 把角度挪到离 near 最近的那一圈。 */
    static double unwrap(double phi, double near) {
        return phi + 2 * Math.PI * Math.round((near - phi) / (2 * Math.PI));
    }

    /** 贴井壁 φ、离井心 r 的那一点：{x, y, z}。 */
    public static double[] at(double phi, double r, double y) {
        double t = Math.PI - phi;
        return new double[]{SX + Math.cos(t) * r, y, SZ + Math.sin(t) * r};
    }

    /** 喉咙里的井壁半径（不算井口那一圈和大腔）。 */
    static double throatR(double y) {
        return Noise.mix(THROAT_R1, THROAT_R0, Math.max(0, Math.min(1, (y - THROAT_BOTTOM) / (double) (THROAT_TOP - THROAT_BOTTOM))));
    }

    /** 井在这个高度的半径：井口一圈宽，喉咙窄，喉咙底下撑开成大腔，罩得住整栋楼。 */
    public static double radius(double y) {
        if (y >= THROAT_TOP) return Noise.mix(THROAT_R0, NECK_R, Noise.smooth(THROAT_TOP, NECK_Y, y));
        if (y >= THROAT_BOTTOM) return throatR(y);
        return Noise.mix(BULB_R, THROAT_R1, Noise.smooth(BULB_Y, THROAT_BOTTOM, y));
    }

    /** 人脚踩在哪一处上（站着的时候）：第几处，没踩在哪一处上是 -1。脚底下压着几格都算（人站在边上，中心可能悬空）。 */
    public static int holdAt(double x, double y, double z) {
        int best = -1;
        for (double ox = -0.3; ox <= 0.31; ox += 0.6) {
            for (double oz = -0.3; oz <= 0.31; oz += 0.6) {
                List<double[]> here = CELLS.get(cellKey((int) Math.floor(x + ox), (int) Math.floor(z + oz)));
                if (here == null) continue;
                for (double[] q : here) if (Math.abs(y - q[1]) < 0.3) best = Math.max(best, (int) q[0]);
            }
        }
        return best;
    }

    /** 这一处的头顶压不压低（肉箍底下）。 */
    public static boolean squeezed(double x, double z) {
        Hold h = HOLDS.get(SQUEEZE);
        for (double[] c : h.cells) if (c[3] > 0 && Math.abs(c[0] + 0.5 - x) < 0.8 && Math.abs(c[2] + 0.5 - z) < 0.8) return true;
        return false;
    }

    private static long cellKey(int x, int z) {
        return ((long) x << 32) | (z & 0xffffffffL);
    }

    /** 人走的路（x、脚下高度、z）。 */
    public final List<double[]> walk = new ArrayList<>();

    private final ParkPlan p;
    private final Random rnd;

    Well(ParkPlan plan) {
        p = plan;
        rnd = new Random(4407);
    }

    void build() {
        realBuilding();
        cab();
        holds();
        bag();
        truss();
        rope();
        lamps();
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
        // 门外那块是安诺丹的旧员工平台，员工楼梯从它往南接下去（现在断成一截一截了）
        p.texts.add(new Text("安诺丹 · 员工专用\n支撑检修平台", CAB_X + 4.04, CAB_Y + 1.9, CAB_Z - 0.4, -90f, 0xf2e8cc, 0.45f, 0xff1b3a3a, 6));
        p.light(CAB_X + 5, CAB_Y + 2, CAB_Z + 1, 5);
    }

    private static boolean inCab(int x, int y, int z) {
        return x >= CAB_X - 1 && x <= CAB_X + 3 && z >= CAB_Z - 1 && z <= CAB_Z + 3 && y >= CAB_Y - 2 && y <= CAB_Y + 4;
    }

    private static boolean inBag(int x, int y, int z) {
        return x >= BAG_X0 && x <= BAG_X1 + 1 && z >= BAG_Z0 - 1 && z <= BAG_Z1 + 1 && Math.abs(y - BAG_Y) < 4;
    }

    /**
     * 每一处落脚的地方铺上方块：平台是铁格栅；断楼梯是楼里那种磨石子，半格一级；钢梁锈透了；肉块面上一层白花花的油、底下是红的，两格厚。
     * 看不见的墙只围在靠井壁那一面（不然一走就走进肉里）、钢梁插进墙的两头、电梯门口那块平台的井心那边；其余的边都敞着，踩空了就掉下去。
     * 肉箍底下头顶压一格看不见的墙，只留 1.5 格，蹲着过。
     */
    private void holds() {
        for (Hold h : HOLDS) {
            for (double[] c : h.cells) {
                int x = (int) c[0], z = (int) c[2], fl = (int) Math.floor(c[1]);
                boolean half = c[1] - fl > 0.25;
                long hash = (long) x * 73856093L ^ (long) z * 19349663L ^ (long) fl * 83492791L;
                int pick = (int) Math.floorMod(hash, 100L);
                // 样子是模型（Rig），脚下只放看不见的方块：整格的、半格的
                if (h.kind != 'P') {
                    if (half) p.set(x, fl, z, B.FOOT_HALF);
                    else p.set(x, fl - 1, z, B.FOOT);
                }
                if (c[3] > 0) p.set(x, fl + 2, z, B.BARRIER);
            }
        }
        // 电梯门口那块平台：一圈看不见的墙，往下跳的那头敞着
        Hold land = HOLDS.get(0);
        for (double[] c : land.cells) {
            int x = (int) c[0], z = (int) c[2];
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    int nx = x + dx, nz = z + dz;
                    if (isCell(land, nx, nz) || inCab(nx, CAB_Y, nz)) continue;
                    double phi = unwrap(Math.PI - Math.atan2(nz + 0.5 - SZ, nx + 0.5 - SX), land.midPhi());
                    double rho = Math.hypot(nx + 0.5 - SX, nz + 0.5 - SZ);
                    if (phi > LANDING_TO - 0.04 && rho < land.rOut - 0.2) continue;
                    wall3(nx, CAB_Y, nz);
                }
            }
        }
        // 断楼梯靠井心那一沿是扶手：扶手外面一格竖看不见的墙（断口那几格扶手没了，敞着）
        for (Hold h : HOLDS) {
            if (h.kind != 'S') continue;
            double rm = (h.rIn + h.rOut) / 2, end = 0.9 / rm;
            int reach = (int) Math.ceil(h.rOut) + 1;
            for (int x = SX - reach; x <= SX + reach; x++) {
                for (int z = SZ - reach; z <= SZ + reach; z++) {
                    double rho = Math.hypot(x + 0.5 - SX, z + 0.5 - SZ);
                    if (rho >= h.rIn || rho < h.rIn - 1.2 || isCell(h, x, z)) continue;
                    double phi = unwrap(Math.PI - Math.atan2(z + 0.5 - SZ, x + 0.5 - SX), h.midPhi());
                    if (phi < h.phiA + end || phi > h.phiB - end) continue;
                    double t = topNear(h, x + 0.5, z + 0.5);
                    for (int y = (int) Math.floor(t); y <= (int) Math.floor(t) + 1; y++) if (p.block(x, y, z) == null) p.set(x, y, z, B.BARRIER);
                }
            }
        }
        // 断楼梯、残骸靠井壁那一面
        for (Hold h : HOLDS) {
            if (h.kind != 'S' && h.kind != 'D') continue;
            int lo = (int) Math.floor(h.bottom()), hi = (int) Math.floor(h.top()) + 2;
            int reach = (int) Math.ceil(h.rOut + 1.6) + 1;
            for (int x = SX - reach; x <= SX + reach; x++) {
                for (int z = SZ - reach; z <= SZ + reach; z++) {
                    double rho = Math.hypot(x + 0.5 - SX, z + 0.5 - SZ);
                    if (rho <= h.rOut || rho > h.rOut + 1.5) continue;
                    double phi = unwrap(Math.PI - Math.atan2(z + 0.5 - SZ, x + 0.5 - SX), h.midPhi());
                    double slack = 1.3 / h.rOut;
                    if (phi < h.phiA - slack || phi > h.phiB + slack) continue;
                    for (int y = lo; y <= hi; y++) if (p.block(x, y, z) == null && !inBag(x, y, z)) p.set(x, y, z, B.BARRIER);
                }
            }
        }
        // 钢梁两头插进墙里
        for (Hold h : HOLDS) {
            if (h.kind != 'B') continue;
            for (double[] c : h.cells) {
                int x = (int) c[0], z = (int) c[2], fl = (int) Math.floor(c[1]);
                for (int s = -1; s <= 1; s += 2) {
                    int nx = h.axis == 0 ? x + s : x, nz = h.axis == 0 ? z : z + s;
                    if (isCell(h, nx, nz) || Math.hypot(nx + 0.5 - SX, nz + 0.5 - SZ) < throatR(fl) - 3) continue;
                    wall3(nx, fl, nz);
                }
            }
        }
        clearHops();
    }

    /** 每一跳身子扫过去的地方（脚下 0.3 到头顶，跳起来中间再高 1 格，左右各 0.3）：看不见的墙全拆掉，不然跳不过去。 */
    private void clearHops() {
        for (int i = 0; i + 1 < HOLDS.size(); i++) {
            double[] a = HOLDS.get(i).out, b = HOLDS.get(i + 1).in;
            boolean jump = Math.hypot(b[0] - a[0], b[2] - a[2]) > 1.2;
            for (double k = 0.05; k <= 0.95; k += 0.05) {
                double x = a[0] + (b[0] - a[0]) * k, z = a[2] + (b[2] - a[2]) * k;
                double y = a[1] + (b[1] - a[1]) * k * k + (jump ? 4 * k * (1 - k) : 0);
                for (double h = 0.3; h <= 1.75; h += 0.35) {
                    for (double ox = -0.3; ox <= 0.31; ox += 0.6) {
                        for (double oz = -0.3; oz <= 0.31; oz += 0.6) {
                            int bx = (int) Math.floor(x + ox), by = (int) Math.floor(y + h), bz = (int) Math.floor(z + oz);
                            if (p.block(bx, by, bz) != B.BARRIER) continue;
                            // 挡着某一处靠井壁那一面的墙，只有身子正中穿过去才拆（身子边上蹭到的留着，不然那一处靠墙那面就漏了）
                            boolean centre = bx == (int) Math.floor(x) && bz == (int) Math.floor(z);
                            if (centre || !guardsWall(bx, by, bz)) p.clear(bx, by, bz);
                        }
                    }
                }
            }
        }
    }

    /** 这一格是不是哪一处靠井壁那一面的墙：挨着一格落脚的格子，比它离井心远。 */
    private static boolean guardsWall(int x, int y, int z) {
        double r = Math.hypot(x + 0.5 - SX, z + 0.5 - SZ);
        int[][] around = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int[] d : around) {
            List<double[]> here = CELLS.get(cellKey(x + d[0], z + d[1]));
            if (here == null || Math.hypot(x + d[0] + 0.5 - SX, z + d[1] + 0.5 - SZ) >= r) continue;
            for (double[] q : here) if (y >= Math.floor(q[1]) - 1 && y <= Math.floor(q[1]) + 2) return true;
        }
        return false;
    }

    private static boolean isCell(Hold h, int x, int z) {
        for (double[] c : h.cells) if ((int) c[0] == x && (int) c[2] == z) return true;
        return false;
    }

    /** 最底下那块平台：台阶从西边接进来，北边挂着 807 号车（车门那几格先拦着，车来了插件拆），南边是井壁。 */
    private void bag() {
        fill(BAG_X0, BAG_Y - 1, BAG_Z0, BAG_X1, BAG_Y - 1, BAG_Z1, B.GRATE);
        for (int x = BAG_X0; x <= BAG_X1 + 1; x++) {
            wall3(x, BAG_Y, BAG_Z0 - 1);
            wall3(x, BAG_Y, BAG_Z1 + 1);
        }
        for (int z = BAG_Z0 - 1; z <= BAG_Z1 + 1; z++) wall3(BAG_X1 + 1, BAG_Y, z);
        p.light(BAG_X0 + 3, BAG_Y + 2, BAG_Z1 - 1, 5);
    }

    /**
     * 4 号支撑：从井口那一圈斜着插下来，穿过喉咙，脚扎进断楼梯倒数第二截旁边的井壁里，就在头顶上。
     * 楼塌的时候它没断，被拽歪了，一直这么斜着。桁架上挂着刮下来的东西：有一个很大的东西从这里掉下去过。
     */
    private void truss() {
        Hold h = HOLDS.get(TRUSS);
        double ft = Math.PI - TRUSS_PHI, fr = h.rOut + 1.2;
        double bx = SX + Math.cos(ft) * fr, bz = SZ + Math.sin(ft) * fr, by = h.top() + 4.2;
        double ta = ft + Math.PI, ar = NECK_R - 1.6;
        double ax = SX + Math.cos(ta) * ar, az = SZ + Math.sin(ta) * ar, ay = NECK_Y + 1.5;
        double lx = bx - ax, lz = bz - az, len = Math.hypot(lx, lz), px = -lz / len * 0.9, pz = lx / len * 0.9;
        for (int s = -1; s <= 1; s += 2) {
            p.beams.add(new Beam("minecraft:polished_deepslate", ax + px * s, ay, az + pz * s, bx + px * s, by, bz + pz * s, 0.45, -1));
            p.beams.add(new Beam("minecraft:polished_deepslate", ax + px * s, ay - 1.5, az + pz * s, bx + px * s, by - 1.5, bz + pz * s, 0.28, -1));
        }
        double full = Math.sqrt(lx * lx + lz * lz + (by - ay) * (by - ay));
        int segs = (int) (full / 2.6);
        for (int k = 0; k < segs; k++) {
            double t0 = k / (double) segs, t1 = (k + 1) / (double) segs;
            int s = k % 2 == 0 ? 1 : -1;
            p.beams.add(new Beam("minecraft:polished_deepslate", ax + lx * t0 + px * s, ay + (by - ay) * t0, az + lz * t0 + pz * s, ax + lx * t1 - px * s, ay + (by - ay) * t1 - 1.5, az + lz * t1 - pz * s, 0.16, -1));
            p.beams.add(new Beam("minecraft:polished_deepslate", ax + lx * t0 - px * s, ay + (by - ay) * t0 - 1.5, az + lz * t0 - pz * s, ax + lx * t1 + px * s, ay + (by - ay) * t1, az + lz * t1 + pz * s, 0.16, -1));
        }
        // 脚：一大块钢座吃进肉里
        p.boxes.add(new Box("minecraft:polished_deepslate", bx - 1.3, by - 2.2, bz - 1.3, 2.6, 2.4, 2.6, -1));
        // 挂着的东西
        double t = 0.42, hx = ax + lx * t, hy = ay + (by - ay) * t - 2.0, hz = az + lz * t;
        p.boxes.add(new Box("minecraft:polished_deepslate", hx - 0.7, hy, hz - 0.03, 1.4, 0.7, 0.06, -1));
        p.texts.add(new Text("3 号码头", hx, hy + 0.12, hz + 0.05, (float) Math.toDegrees(Math.atan2(-lx, lz)) + 90f, 0xf2e8cc, 0.4f, 0xff1b3a3a, 6));
        t = 0.66;
        p.boxes.add(new Box("minecraft:iron_bars", ax + lx * t - 0.8, ay + (by - ay) * t - 2.6, az + lz * t, 1.6, 1.1, 0.06, -1));
        t = 0.8;
        p.boxes.add(new Box("minecraft:pink_wool", ax + lx * t + px, ay + (by - ay) * t - 1.9, az + lz * t + pz, 0.22, 0.08, 0.1, -1));
        // 上头那盏检修灯还亮着
        p.boxes.add(new Box("minecraft:ochre_froglight", ax - 0.2, ay + 0.6, az - 0.2, 0.4, 0.3, 0.4, 13));
        p.light((int) Math.floor(ax), (int) Math.floor(ay) + 1, (int) Math.floor(az), 7);
    }

    /**
     * 搜救队 2007.7.6 下井用的绳：顺着正南的井壁垂下来。十九年过去，井壁把绳和岩钉吃进去一大半，只一段一段露出来。
     * 断楼梯第一次贴着它过去的地方，井壁上喷着搜救队的记号。绳到最下面那块平台就没了：还差 30 米。
     */
    private void rope() {
        double a = ROPE_ANGLE;
        double top = NECK_Y + 4, bottom = BAG_Y + 1.1, lastY = top;
        double[] last = ropeAt(a, top);
        for (double y = top - 3; y >= bottom - 0.01; y -= 3) {
            double yy = Math.max(bottom, y);
            double[] q = ropeAt(a, yy);
            p.beams.add(new Beam("minecraft:orange_concrete", last[0], lastY, last[1], q[0], yy, q[1], 0.06, -1));
            last = q;
            lastY = yy;
        }
        // 岩钉一颗一颗往下，每颗旁边都补打过一颗
        for (double y = top - 2; y > bottom + 1; y -= 2.5) {
            double[] q = ropeAt(a, y);
            p.boxes.add(new Box("minecraft:iron_block", q[0] - 0.08, y, q[1] - 0.25, 0.16, 0.16, 0.3, -1));
            p.boxes.add(new Box("minecraft:iron_block", q[0] + 0.25, y - 0.6, q[1] - 0.2, 0.14, 0.14, 0.26, -1));
        }
        Hold h = HOLDS.get(ROPE_MARK);
        double f = h.top();
        double[] q = ropeAt(a, f + 1.8);
        p.texts.add(new Text("搜救 07.7.6 ↓", q[0] + 0.9, f + 1.9, q[1] - 0.35, faceIn(a), 0xff7a20, 0.7f, 0, 4));
        // 残骸里那截风管头顶的井壁上，搜救队用喷漆写的
        Hold duct = HOLDS.get(FIRST_DEBRIS + 8);
        double phi = duct.midPhi(), t = Math.PI - phi;
        double[] w = at(phi, duct.rOut + 0.12, duct.top() + 1.3);
        p.texts.add(new Text("二队 小罗\n腰绳挂过这根风管  吃得住", w[0], w[1], w[2], faceIn(t), 0xff7a20, 0.45f, 0, 3));
    }

    /** 绳在这个高度挂在哪：喉咙里贴着井壁的面（一段露在外面、一段埋进肉里），井口那一圈贴着井壁。 */
    private static double[] ropeAt(double a, double y) {
        double r = y >= THROAT_TOP ? radius(y) - 0.3 : throatR(y) - 0.15;
        return new double[]{SX + Math.cos(a) * r, SZ + Math.sin(a) * r};
    }

    /** 文字贴在井壁上 a 这个方向、朝着井心。 */
    static float faceIn(double a) {
        return (float) Math.toDegrees(a) + 90f;
    }

    /**
     * 灯：断楼梯每截靠井心那一沿嵌着一盏踏步灯（楼里的楼梯边上一级一盏，它也照着长了，有的不亮）；钢梁上，每根要往下迈的那一格放着一盏检修灯，
     * 隔着井心往下看，一盏一盏错开着往下没进雾里；肉块上没有灯，只有几处很暗的光从肉里透出来。
     */
    private void lamps() {
        Random r = new Random(77);
        for (Hold h : HOLDS) {
            if (h.kind == 'S') {
                // 扶手底下挂着一盏小检修灯（挂在中间那一级前沿的扶手上），有的不亮了
                double[] zone = h.zones[h.zones.length / 2];
                double phi = zone[0] + 0.02;
                double[] q = h.rail(phi, 0.84);
                double y = q[1];
                p.boxes.add(new Box("minecraft:ochre_froglight", q[0] - 0.06, y, q[2] - 0.06, 0.12, 0.1, 0.12, r.nextDouble() < 0.2 ? 6 : 15));
                p.boxes.add(new Box("minecraft:iron_bars", q[0] - 0.08, y - 0.02, q[2] - 0.08, 0.16, 0.14, 0.16, -1));
                if (h.index % 2 == 0) {
                    double[] c = at(phi, (h.rIn + h.rOut) / 2, 0);
                    p.light((int) Math.floor(c[0]), (int) Math.floor(zone[2]) + 1, (int) Math.floor(c[2]), 5);
                }
            } else if (h.kind == 'B') {
                double[] o = h.out;
                p.boxes.add(new Box("minecraft:ochre_froglight", o[0] - 0.35, o[1] + 0.01, o[2] - 0.35, 0.2, 0.16, 0.2, 12));
                p.light((int) Math.floor(o[0]), (int) Math.floor(o[1]) + 1, (int) Math.floor(o[2]), 6);
            } else if (h.kind == 'D' && (h.style == SIGN || h.index % 3 == 0)) {
                // 灯箱还有一点电，一闪一闪的；别的残骸上隔几块透一点光
                double[] c = at(h.midPhi(), (h.rIn + h.rOut) / 2, 0);
                p.light((int) Math.floor(c[0]), (int) Math.floor(h.top()) + 1, (int) Math.floor(c[2]), h.style == SIGN ? 7 : 4);
            }
        }
    }

    /** 这一处离 (x, z) 最近那一格的脚下高度。 */
    static double topNear(Hold h, double x, double z) {
        double best = 1e9, t = h.top();
        for (double[] c : h.cells) {
            double d = Math.hypot(c[0] + 0.5 - x, c[2] + 0.5 - z);
            if (d < best) {
                best = d;
                t = c[1];
            }
        }
        return t;
    }

    /** 贴井壁那一处里 φ 这一点、离中线往井壁那边偏 u：{x, 脚下高度, z, 顺着往下走的朝向}。 */
    public static double[] spot(int index, double phi, double u) {
        Hold h = HOLDS.get(index);
        double rr = (h.rIn + h.rOut) / 2 + u;
        double[] q = at(phi, rr, 0);
        double t = Math.PI - phi;
        // 断楼梯：放在看得见的踏板面上（楼梯歪了，和脚下的方块差一点）
        q[1] = h.kind == 'S' ? h.surface(phi, Math.min(rr, h.rOut)) : topNear(h, q[0], q[2]);
        return new double[]{q[0], q[1], q[2], yawAlong(t)};
    }

    /** 顺着 φ 变大的方向（从西往南）走，脸朝哪（度）。 */
    private static double yawAlong(double t) {
        double tx = Math.sin(t), tz = -Math.cos(t);
        return Math.toDegrees(Math.atan2(-tx, tz));
    }

    /** 当年通 3 号码头的缆车线：楼塌的时候连着缆一起拽了下来，从平台头顶的井壁里穿出来，斜着穿进雾里。 */
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
        p.light((int) CAR_TO[0], REAL_FLOOR + 3, (int) CAR_TO[2], 3);
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
        // 下花园层的楼梯洞（x 133–142、z 5–7）那一块不铺：不然洞口上横着一层水面，走楼梯下去会从水面里穿过去
        int sx0 = ParkPlan.STAIR_TOP_X - 9 + DX, sx1 = ParkPlan.STAIR_TOP_X + 1 + DX;
        p.pools.add(new Parts.Pool("pool_water", ParkPlan.HX0 + DX + 4, 5, sx0, ParkPlan.HZ1, REAL_FLOOR + 0.12));
        p.pools.add(new Parts.Pool("pool_water", sx0, 8, ParkPlan.HX1 + DX, ParkPlan.HZ1, REAL_FLOOR + 0.12));
        p.pools.add(new Parts.Pool("pool_water", sx1, 5, ParkPlan.HX1 + DX, 8, REAL_FLOOR + 0.12));
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
        double r = NECK_R - 1;
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
            double a = rnd.nextDouble() * Math.PI * 2, rr = 4 + rnd.nextDouble() * 18;
            double x = SX + Math.cos(a) * rr, z = SZ + Math.sin(a) * rr;
            p.pipes.add(new Parts.Pipe(rnd.nextInt(3), x, y - 3.2, z, x + (rnd.nextDouble() - 0.5) * 16, y - 2.6 - rnd.nextDouble() * 2, z + (rnd.nextDouble() - 0.5) * 16, 0.25));
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

    /** 井口那一圈井壁上，六根支撑的断头。4 号没断，整根斜着插进喉咙里。 */
    private void stumps() {
        int y = NECK_Y + 1;
        double a4 = Math.PI - TRUSS_PHI + Math.PI;
        double r = NECK_R - 1.6;
        for (int n = 1; n <= 6; n++) {
            double a = a4 + (n - 4) * Math.PI / 3;
            double x = SX + Math.cos(a) * r, z = SZ + Math.sin(a) * r;
            if (n != 4) p.boxes.add(new Box("minecraft:polished_deepslate", x - 1.2, y - 1.2, z - 1.2, 2.4, 2.4, 2.4, -1));
            double tr = r - (n == 4 ? 1.4 : 1.25);
            p.texts.add(new Text(String.valueOf(n), SX + Math.cos(a) * tr, y + (n == 4 ? 1.4 : 1.6), SZ + Math.sin(a) * tr, faceIn(a), 0xffe050, 1.2f, 0, 4));
        }
    }

    private void paths() {
        for (int i = 0; i < HOLDS.size(); i++) {
            Hold h = HOLDS.get(i);
            for (double[] c : h.cells) walk.add(new double[]{c[0] + 0.5, c[1], c[2] + 0.5});
            if (i + 1 < HOLDS.size()) {
                double[] a = h.out, b = HOLDS.get(i + 1).in;
                for (double k = 0; k <= 1; k += 0.2) walk.add(new double[]{a[0] + (b[0] - a[0]) * k, a[1] + (b[1] - a[1]) * k, a[2] + (b[2] - a[2]) * k});
            }
        }
        for (int x = LAND_X0; x <= LAND_X1; x += 2) walk.add(new double[]{x + 0.5, REAL_FLOOR, -35.5});
        // 落脚处绕过真楼西边走到码头
        double[][] around = {{488, -32}, {478, -20}, {474, -8}, {476, 0}, {480, 3}};
        for (double[] q : around) walk.add(new double[]{q[0], REAL_FLOOR, q[1]});
    }

    // ---------- 肉腔模型里落脚处那一带的肉 ----------

    /**
     * 肉腔模型（Chamber.f，大于 0 是肉）在落脚处那一带要改的形状：
     * 贴井壁的那几处，头顶挖空，背后和底下长满（看着是从墙里长出来的，靠墙那面正好贴着看不见的墙）；钢梁插进墙的两头裹一团肉；
     * 每一处跳到下一处的那一路挖空，免得跳的时候脸穿进肉里。肉箍那一截头顶只挖 1.5 格，肉箍压下来。
     * 每个形状：{种类（0 扇形、1 胶囊、2 球、3 盒子）, 肉（1）或挖空（0）, 参数……}。
     */

    private static void shapes() {
        for (Hold h : HOLDS) {
            if (h.onWall()) {
                double lo = h.bottom(), hi = h.top(), rm = (h.rIn + h.rOut) / 2, ext = 0.6 / rm;
                if (h.index == SQUEEZE) {
                    double a = Double.MAX_VALUE, b = -Double.MAX_VALUE;
                    for (double[] c : h.cells) {
                        if (c[3] == 0) continue;
                        double phi = unwrap(Math.PI - Math.atan2(c[2] + 0.5 - SZ, c[0] + 0.5 - SX), h.midPhi());
                        a = Math.min(a, phi);
                        b = Math.max(b, phi);
                    }
                    sqA = a - 0.4 / rm;
                    sqB = b + 0.4 / rm;
                    sector(0, h.rIn - 0.8, h.rOut + 0.25, lo, hi + 3.2, h.phiA - ext, sqA);
                    sector(0, h.rIn - 0.8, h.rOut + 0.25, lo, hi + 1.55, sqA, sqB);
                    sector(0, h.rIn - 0.8, h.rOut + 0.25, lo, hi + 3.2, sqB, h.phiB + ext);
                } else {
                    // 残骸整块露出来（楼板厚度那一截也挖开），断楼梯只挖头顶
                    double from = h.kind == 'D' ? lo - 1.2 : lo;
                    sector(0, h.rIn - 0.8, h.rOut + 0.25, from, hi + 3.2, h.phiA - ext, h.phiB + ext);
                }
                sector(1, h.rOut + 0.25, h.rOut + 4, lo - 2.5, hi + 3.6, h.phiA - 2 * ext, h.phiB + 2 * ext);
                boolean debris = h.kind == 'D';
                // 底下托着：断楼梯底下一道肉棱（斜梁吃进去一半）；残骸靠墙那一头被肉裹住一点
                if (debris) sector(1, h.rOut - 0.5, h.rOut + 0.6, lo - 3.0, lo - 1.1, h.phiA + ext, h.phiB - ext);
                else sector(1, h.rOut - 0.6, h.rOut + 0.3, lo - 3.0, lo - 1.0, h.phiA + ext * 0.3, h.phiB - ext * 0.3);
            } else if (h.kind == 'B') {
                double wall = throatR(h.top()) - 3;
                double[] lo = null, hi = null;
                for (double[] c : h.cells) {
                    double along = h.axis == 0 ? c[0] : c[2];
                    if (lo == null || along < (h.axis == 0 ? lo[0] : lo[2])) lo = c;
                    if (hi == null || along > (h.axis == 0 ? hi[0] : hi[2])) hi = c;
                    if (Math.hypot(c[0] + 0.5 - SX, c[2] + 0.5 - SZ) > wall) add(new double[]{3, 0, c[0] - 0.3, c[1], c[2] - 0.3, c[0] + 1.3, c[1] + 3.1, c[2] + 1.3});
                }
                for (double[] e : new double[][]{lo, hi}) {
                    if (e == null || Math.hypot(e[0] + 0.5 - SX, e[2] + 0.5 - SZ) < wall) continue;
                    add(new double[]{2, 1, e[0] + 0.5 + (h.axis == 0 ? (e == lo ? -0.8 : 0.8) : 0), e[1] - 0.6, e[2] + 0.5 + (h.axis == 1 ? (e == lo ? -0.8 : 0.8) : 0), 1.6});
                }
            }
        }
        for (int i = 0; i + 1 < HOLDS.size(); i++) {
            double[] a = HOLDS.get(i).out, b = HOLDS.get(i + 1).in;
            add(new double[]{1, 0, a[0], a[1] + 1.0, a[2], b[0], b[1] + 1.0, b[2], 1.4});
        }
    }

    private static void sector(int flesh, double r0, double r1, double y0, double y1, double p0, double p1) {
        add(new double[]{0, flesh, r0, r1, y0, y1, p0, p1});
    }

    private static void add(double[] s) {
        double[] box = bounds(s);
        SHAPES.add(s);
        for (int x = (int) Math.floor(box[0] - 1.5) >> 2; x <= (int) Math.floor(box[3] + 1.5) >> 2; x++) {
            for (int y = (int) Math.floor(box[1] - 1.5) >> 2; y <= (int) Math.floor(box[4] + 1.5) >> 2; y++) {
                for (int z = (int) Math.floor(box[2] - 1.5) >> 2; z <= (int) Math.floor(box[5] + 1.5) >> 2; z++) {
                    SHAPE_GRID.computeIfAbsent(gridKey(x, y, z), k -> new ArrayList<>()).add(s);
                }
            }
        }
    }

    private static long gridKey(int x, int y, int z) {
        return ((long) (x + 4096) << 40) | ((long) (y + 4096) << 20) | (z + 4096);
    }

    /** 形状的包围盒：{x0, y0, z0, x1, y1, z1}。 */
    private static double[] bounds(double[] s) {
        switch ((int) s[0]) {
            case 0 -> {
                double x0 = 1e9, z0 = 1e9, x1 = -1e9, z1 = -1e9;
                for (double phi = s[6]; phi <= s[7] + 1e-9; phi += Math.max(1e-3, (s[7] - s[6]) / 12)) {
                    for (double r : new double[]{s[2], s[3]}) {
                        double[] q = at(phi, r, 0);
                        x0 = Math.min(x0, q[0]);
                        x1 = Math.max(x1, q[0]);
                        z0 = Math.min(z0, q[2]);
                        z1 = Math.max(z1, q[2]);
                    }
                }
                return new double[]{x0, s[4], z0, x1, s[5], z1};
            }
            case 1 -> {
                double r = s[8];
                return new double[]{Math.min(s[2], s[5]) - r, Math.min(s[3], s[6]) - r, Math.min(s[4], s[7]) - r, Math.max(s[2], s[5]) + r, Math.max(s[3], s[6]) + r, Math.max(s[4], s[7]) + r};
            }
            case 2 -> {
                return new double[]{s[2] - s[5], s[3] - s[5], s[4] - s[5], s[2] + s[5], s[3] + s[5], s[4] + s[5]};
            }
            default -> {
                return new double[]{s[2], s[3], s[4], s[5], s[6], s[7]};
            }
        }
    }

    /** 点在形状里面多深（外面是负的）。 */
    private static double inside(double[] s, double x, double y, double z) {
        switch ((int) s[0]) {
            case 0 -> {
                double dx = x - SX, dz = z - SZ, r = Math.sqrt(dx * dx + dz * dz);
                double phi = unwrap(Math.PI - Math.atan2(dz, dx), (s[6] + s[7]) / 2);
                return Math.min(Math.min(Math.min(r - s[2], s[3] - r), Math.min(y - s[4], s[5] - y)), Math.min((phi - s[6]) * r, (s[7] - phi) * r));
            }
            case 1 -> {
                double bx = s[5] - s[2], by = s[6] - s[3], bz = s[7] - s[4];
                double px = x - s[2], py = y - s[3], pz = z - s[4];
                double t = Math.max(0, Math.min(1, (px * bx + py * by + pz * bz) / Math.max(1e-9, bx * bx + by * by + bz * bz)));
                double ex = px - bx * t, ey = py - by * t, ez = pz - bz * t;
                return s[8] - Math.sqrt(ex * ex + ey * ey + ez * ez);
            }
            case 2 -> {
                double ex = x - s[2], ey = y - s[3], ez = z - s[4];
                return s[5] - Math.sqrt(ex * ex + ey * ey + ez * ez);
            }
            default -> {
                return Math.min(Math.min(Math.min(x - s[2], s[5] - x), Math.min(y - s[3], s[6] - y)), Math.min(z - s[4], s[7] - z));
            }
        }
    }

    /** 落脚处那一带该长满的肉（在挖洞之前算）。 */
    static double fleshNear(double x, double y, double z, double v) {
        List<double[]> here = SHAPE_GRID.get(gridKey((int) Math.floor(x) >> 2, (int) Math.floor(y) >> 2, (int) Math.floor(z) >> 2));
        if (here == null) return v;
        for (double[] s : here) if (s[1] == 1) v = smax(v, inside(s, x, y, z), 1.0);
        return v;
    }

    /** 落脚处头顶、跳过去那一路该挖空的（最后算）。 */
    static double airNear(double x, double y, double z, double v) {
        List<double[]> here = SHAPE_GRID.get(gridKey((int) Math.floor(x) >> 2, (int) Math.floor(y) >> 2, (int) Math.floor(z) >> 2));
        if (here == null) return v;
        for (double[] s : here) if (s[1] == 0) v = smin(v, -inside(s, x, y, z), 0.6);
        return v;
    }

    /** 肉箍：一大团肉从井壁上鼓出来，压在那一截头顶上。{起点 x, y, z, 终点 x, y, z, 粗, 粗}（肉腔模型的肉枝）。 */
    static double[] hoop() {
        Hold h = HOLDS.get(SQUEEZE);
        double y = h.top() + 2.7, r = h.rOut - 0.1;
        double[] a = at(sqA, r, y), b = at(sqB, r, y);
        return new double[]{a[0], a[1], a[2], b[0], b[1], b[2], 2.5, 2.5};
    }

    private static double smin(double a, double b, double k) {
        double h = Math.max(k - Math.abs(a - b), 0) / k;
        return Math.min(a, b) - h * h * k * 0.25;
    }

    private static double smax(double a, double b, double k) {
        return -smin(-a, -b, k);
    }

    // ---------- 小工具 ----------

    private void fill(int x0, int y0, int z0, int x1, int y1, int z1, B b) {
        p.fill(x0, y0, z0, x1, y1, z1, b);
    }

    /** 看不见的墙：三格高。已经有方块的格子不动。 */
    private void wall3(int x, int y, int z) {
        for (int yy = y; yy <= y + 2; yy++) if (p.block(x, yy, z) == null) p.set(x, yy, z, B.BARRIER);
    }

    /** 人在不在井里（第四关）或井底的楼里（第五关）。 */
    public static boolean inWell(double x, double y, double z) {
        return x >= SX - 52 && x <= SX + 52 && z >= SZ - 52 && z <= SZ + 52 && y > REAL_GARDEN - 6;
    }
}
