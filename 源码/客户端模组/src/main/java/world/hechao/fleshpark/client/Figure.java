package world.hechao.fleshpark.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 人形的造型：息壤照着见过的人长出来的仿品。人的皮肤它学不会颜色，一直是湿的灰白；
 * 衣服、帽子、包是照着学的，有颜色，可是旧、穿得不太对。比人高一点、瘦一点、胳膊长一点，手指太长。
 * 每一个按身份（kind）和编号（n）定：身高、胖瘦、头型、眼窝、嘴、手指、站姿、衣服、帽子、头发都不一样。
 * pose 写成 kind.n 或 kind.n.posture，比如 tourist.12、bather.3.bust、tester.4.sit。
 * <p>
 * 身份：tourist 2007 年的游客（夏装、挂独立日门票）、bather 泡池的游客、kid 小孩、ranger 护林员、guide 导览员、cook 餐厅、
 * miner 回收公司的采收工（安全帽、头灯）、rescue 2007 年的搜救队（橙衣服、白安全帽、头灯）、tester 前十个体验官（反光背心、红挂绳）、you 你（背心、你的工牌）、announcer 播音员。
 * 姿势：stand 站、raised 举一只手、reach 两只手往前伸、bust 泡在池子里的半身、sit 坐、clap 坐着鼓掌、slump 垂着头。
 */
final class Figure {
    static final int SKIN = 0, TOP = 1, BOTTOM = 2, SHOES = 3, HAT = 4, HAIR = 5, BAG = 6, VEST = 7, STRIP = 8, LAMP = 9, APRON = 10, TRIM = 11, SLOTS = 12;
    static final int SKIN_TINT = 0xFFB4B0AA;
    /** 2007 年的夏装：都褪过色。 */
    static final int RED = 0xFF9A3A32, NAVY = 0xFF2E3A52, SKY = 0xFF5C7C96, MUSTARD = 0xFFB08A3A, KHAKI = 0xFF8A7A5A, WHITE = 0xFFC8C4B8, OLIVE = 0xFF5A6040,
            PINK = 0xFFA8707A, DENIM = 0xFF3E5068, ROBE = 0xFFD8D4CA, YELLOW = 0xFFC8A030, RANGER = 0xFFA08E62, RANGER_GREEN = 0xFF3E4A36, COVERALL = 0xFF2C3440,
            HARDHAT = 0xFFD8A020, PLUM = 0xFF7A4A6A, GREEN = 0xFF4A6A3A, HAIR_DARK = 0xFF3A2A22, HAIR_BROWN = 0xFF6A4A30, HAIR_GREY = 0xFF8A8478, ORANGE = 0xFFB85A2A,
            LEATHER = 0xFF5A3A2A, BLACK = 0xFF24211F, PARK_GREEN = 0xFF49655A, VEST_ORANGE = 0xFFE8661E, STRIP_GREY = 0xFFD8DAD2, LAMP_WHITE = 0xFFFFFBF5, TIE = 0xFFD74735;
    /** 光影包要认得的颜色：和 Shape.TINTS 一起改。 */
    static final int[] PALETTE = {RED, NAVY, SKY, MUSTARD, KHAKI, WHITE, OLIVE, PINK, DENIM, ROBE, YELLOW, RANGER, RANGER_GREEN, COVERALL, HARDHAT, PLUM, GREEN,
            HAIR_DARK, HAIR_BROWN, HAIR_GREY, ORANGE, LEATHER};

    final Sculpt s = new Sculpt();
    final int[] color = new int[SLOTS];
    /** 工牌：null 不挂；park 2007 年独立日门票（青绿挂绳）；staff 前十个体验官（红挂绳，号码糊了）；self 你的工牌。 */
    String badge;
    /** 整体缩放：小孩矮，有的高，有的宽。 */
    float scale = 1, width = 1;
    /** 眼窝、嘴里面塞的黑块：雕刻坐标的两个端点和半径。 */
    final List<double[]> holes = new ArrayList<>();
    /** 工牌的位置（雕刻坐标）：脖子两边、牌子中心。 */
    double neckY, neckZ, cardY, cardZ;
    boolean vested;

    private Random r;
    private String kind, posture;
    // 身材
    private double pelvisY, bellyY, chestY, shoulderY, neckTop, headY, legK, torsoK, shoulder, chestX, chestZ, belly, hips, limb, armK, headK, fingerLen;
    private int fingers, headType;
    private double headRx, headRy, headRz, faceZ;
    // 穿什么
    private String top, bottom, hat, hair, bag, shoes;

    private Figure() {
        java.util.Arrays.fill(color, SKIN_TINT);
    }

    static Figure of(String pose) {
        // 旧的名字：前三关用过的
        pose = switch (pose) {
            case "stand" -> "tourist.0";
            case "raised" -> "tourist.1.raised";
            case "bust" -> "bather.0.bust";
            case "staff" -> "tester.1";
            case "self", "copy", "you", "mirror" -> "you.0";
            default -> pose;
        };
        String[] a = pose.split("\\.");
        Figure f = new Figure();
        f.kind = a[0];
        int n = a.length > 1 ? parse(a[1]) : 0;
        f.posture = a.length > 2 ? a[2] : "stand";
        f.r = new Random(f.kind.hashCode() * 7919L + n * 104729L + 17);
        f.build();
        return f;
    }

    private static int parse(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private double rand(double a, double b) {
        return a + r.nextDouble() * (b - a);
    }

    private boolean chance(double p) {
        return r.nextDouble() < p;
    }

    @SafeVarargs
    private <T> T pick(T... options) {
        return options[r.nextInt(options.length)];
    }

    private int pickColor(int... options) {
        return options[r.nextInt(options.length)];
    }

    private boolean sitting() {
        return posture.equals("sit") || posture.equals("clap");
    }

    private void build() {
        body();
        dress();
        boolean kid = kind.equals("kid");
        legK = kid ? rand(0.7, 0.8) : rand(0.9, 1.1);
        torsoK = kid ? rand(0.72, 0.8) : rand(0.88, 1.12);
        pelvisY = sitting() ? 0.52 : 1.0 * legK;
        bellyY = pelvisY + 0.24 * torsoK;
        chestY = pelvisY + 0.5 * torsoK;
        shoulderY = pelvisY + 0.67 * torsoK;
        neckTop = shoulderY + 0.05 + 0.2 * rand(0.65, 1.6) * (kid ? 0.7 : 1);
        headY = neckTop + 0.11 * headK;
        s.leanY = pelvisY;
        s.neckY = neckTop - 0.06;
        if (kid) {
            scale = 0.66f;
            headK *= 1.3;
            headY = neckTop + 0.11 * headK;
        }
        torso();
        head();
        legs();
        arms();
        clothes();
        if (posture.equals("bust")) s.cutBelow = pelvisY + 0.3;
    }

    // ---------- 身材 ----------

    private void body() {
        shoulder = rand(0.15, 0.22);
        chestX = rand(0.13, 0.19);
        chestZ = rand(0.08, 0.12);
        belly = chance(0.3) ? rand(0.3, 1) : 0;
        hips = rand(0.1, 0.16);
        limb = rand(0.8, 1.25);
        armK = rand(0.9, 1.35);
        fingers = 3 + r.nextInt(3);
        fingerLen = rand(0.15, 0.3);
        headType = r.nextInt(5);
        headK = rand(0.85, 1.25);
        s.lean = rand(0.02, 0.16);
        s.tilt = rand(-0.3, 0.3);
        s.yaw = rand(-0.35, 0.35);
        s.pitch = rand(-0.05, 0.2);
        width = (float) rand(0.92, 1.12);
        scale = (float) rand(0.94, 1.08);
        if (posture.equals("slump")) {
            s.lean = 0.2;
            s.pitch = 0.75;
        }
    }

    /** 每种身份穿什么。颜色从这一种身份会穿的里面挑。 */
    private void dress() {
        top = bottom = hat = hair = bag = "none";
        shoes = "shoes";
        hair = pick("none", "short", "short", "long", "pony", "bun", "none");
        switch (kind) {
            case "tourist", "kid" -> {
                top = pick("tee", "tee", "tank", "long", "polo", "dress");
                bottom = top.equals("dress") ? "none" : pick("shorts", "shorts", "pants", "skirt", "capri");
                hat = pick("none", "none", "cap", "sun", "bucket", "visor");
                bag = pick("none", "none", "backpack", "fanny", "camera", "shoulder");
                shoes = pick("shoes", "shoes", "sandals");
                color[TOP] = pickColor(RED, NAVY, SKY, MUSTARD, WHITE, OLIVE, PINK, ORANGE, PLUM, GREEN, PARK_GREEN);
                color[BOTTOM] = pickColor(KHAKI, DENIM, NAVY, WHITE, OLIVE, BLACK);
                color[HAT] = pickColor(RED, NAVY, WHITE, KHAKI, YELLOW, PARK_GREEN);
                color[BAG] = pickColor(LEATHER, BLACK, NAVY, RED, OLIVE);
                color[SHOES] = pickColor(WHITE, BLACK, LEATHER, NAVY);
                badge = chance(0.75) ? "park" : null;
            }
            case "bather" -> {
                top = chance(0.5) ? "robe" : "none";
                bottom = "brief";
                hat = chance(0.55) ? "swimcap" : "none";
                hair = hat.equals("none") ? pick("short", "long", "none", "bun") : "none";
                shoes = "none";
                color[TOP] = pickColor(ROBE, ROBE, WHITE, SKY);
                color[BOTTOM] = pickColor(NAVY, RED, BLACK, SKY, PLUM);
                color[HAT] = pickColor(YELLOW, RED, WHITE, SKY, PINK);
                badge = chance(0.6) ? "park" : null;
            }
            case "ranger" -> {
                top = "long";
                bottom = "pants";
                hat = "ranger";
                bag = "radio";
                shoes = "boots";
                color[TOP] = RANGER;
                color[BOTTOM] = RANGER_GREEN;
                color[HAT] = pickColor(LEATHER, KHAKI);
                color[BAG] = BLACK;
                color[SHOES] = LEATHER;
                color[TRIM] = LEATHER;
            }
            case "guide" -> {
                top = "polo";
                bottom = pick("shorts", "pants");
                hat = pick("cap", "none");
                color[TOP] = PARK_GREEN;
                color[BOTTOM] = KHAKI;
                color[HAT] = PARK_GREEN;
                color[SHOES] = BLACK;
                badge = "park";
            }
            case "cook" -> {
                top = "tee";
                bottom = "pants";
                hat = "paper";
                bag = "apron";
                color[TOP] = WHITE;
                color[BOTTOM] = pickColor(BLACK, NAVY);
                color[HAT] = ROBE;
                color[APRON] = pickColor(ROBE, RED, PARK_GREEN);
                color[SHOES] = BLACK;
            }
            case "miner" -> {
                top = "long";
                bottom = "pants";
                hat = "hard";
                shoes = "boots";
                bag = chance(0.4) ? "belt" : "none";
                hair = "none";
                color[TOP] = color[BOTTOM] = pickColor(COVERALL, COVERALL, NAVY, OLIVE);
                color[HAT] = pickColor(HARDHAT, HARDHAT, WHITE, ORANGE);
                color[SHOES] = BLACK;
                color[TRIM] = LEATHER;
                color[BAG] = LEATHER;
                color[LAMP] = LAMP_WHITE;
            }
            case "rescue" -> {
                // 2007 年的搜救队：橙色连体服，白安全帽带头灯，腰上挂绳
                top = "long";
                bottom = "pants";
                hat = "hard";
                shoes = "boots";
                bag = "belt";
                hair = "none";
                color[TOP] = color[BOTTOM] = ORANGE;
                color[HAT] = WHITE;
                color[SHOES] = BLACK;
                color[TRIM] = LEATHER;
                color[BAG] = LEATHER;
                color[LAMP] = LAMP_WHITE;
            }
            case "tester", "you" -> {
                top = "long";
                bottom = "pants";
                hat = "none";
                vested = true;
                color[TOP] = pickColor(NAVY, BLACK, OLIVE, DENIM);
                color[BOTTOM] = pickColor(BLACK, NAVY, KHAKI);
                color[SHOES] = BLACK;
                color[VEST] = VEST_ORANGE;
                color[STRIP] = STRIP_GREY;
                badge = kind.equals("you") ? "self" : "staff";
                if (kind.equals("you")) {
                    hair = "short";
                    color[TOP] = NAVY;
                    color[BOTTOM] = BLACK;
                }
            }
            case "announcer" -> {
                top = "long";
                bottom = "pants";
                hat = "headset";
                color[TOP] = NAVY;
                color[BOTTOM] = NAVY;
                color[TRIM] = BLACK;
                color[APRON] = TIE;
                color[SHOES] = BLACK;
                bag = "tie";
                badge = "park";
            }
            default -> {
            }
        }
        color[HAIR] = pickColor(HAIR_DARK, HAIR_DARK, HAIR_BROWN, HAIR_GREY, BLACK);
    }

    // ---------- 雕 ----------

    private void slot(int slot) {
        s.slot = slot;
    }

    private boolean sleeves() {
        return top.equals("tee") || top.equals("polo") || top.equals("long") || top.equals("robe") || top.equals("dress");
    }

    private boolean longSleeves() {
        return top.equals("long") || top.equals("robe");
    }

    private void torso() {
        boolean clothed = !top.equals("none");
        double pad = clothed ? 0.012 : 0;
        // 脖子太长
        slot(SKIN);
        s.capsule(0, shoulderY + 0.04, -0.01, 0.042 * limb, 0, neckTop + 0.02, 0.015, 0.036 * limb, 0.05);
        neckY = shoulderY + 0.12;
        neckZ = 0.03;
        // 胸廓：没穿衣服的，肋骨一条条顶出来
        slot(clothed ? TOP : SKIN);
        s.ellipsoid(0, chestY, 0, chestX + pad, 0.2 * torsoK, chestZ + pad, 0.06);
        if (!clothed) {
            for (int i = 0; i < 6; i++) {
                double y = chestY + 0.1 - i * 0.055, w = chestX - 0.01 - i * 0.004;
                s.capsule(-w, y + 0.02, -0.01, 0.012, 0, y - 0.03, chestZ, 0.01, 0.02);
                s.capsule(w, y + 0.02, -0.01, 0.012, 0, y - 0.03, chestZ, 0.01, 0.02);
            }
        }
        // 肚子：有的凹进去，有的鼓出来
        s.ellipsoid(0, bellyY, 0.01 + belly * 0.05, 0.105 + belly * 0.07 + pad, 0.15 * torsoK, 0.07 + belly * 0.09 + pad, 0.07);
        // 肩膀
        slot(sleeves() ? TOP : SKIN);
        s.capsule(-shoulder, shoulderY, 0, 0.042 + (sleeves() ? pad : 0), shoulder, shoulderY, 0, 0.042 + (sleeves() ? pad : 0), 0.05);
        // 骨盆
        slot(bottom.equals("none") ? (top.equals("dress") || top.equals("robe") ? TOP : SKIN) : BOTTOM);
        s.ellipsoid(0, pelvisY, 0, hips + 0.025 + (bottom.equals("none") ? 0 : 0.012), 0.085, 0.085 + (bottom.equals("none") ? 0 : 0.012), 0.07);
        cardY = chestY - 0.04;
        cardZ = chestZ + 0.026;
    }

    private void head() {
        double k = headK, y = headY;
        slot(SKIN);
        switch (headType) {
            case 0 -> {
                // 后脑勺很长，下巴很小
                headRx = 0.1 * k;
                headRy = 0.13 * k;
                headRz = 0.125 * k;
                s.ellipsoid(0, y, -0.01, headRx, headRy, headRz, 0.04);
                s.ellipsoid(0, y - 0.1 * k, 0.035 * k, 0.068 * k, 0.07 * k, 0.08 * k, 0.05);
            }
            case 1 -> {
                // 圆，太大
                headRx = headRy = headRz = 0.125 * k;
                s.ellipsoid(0, y, 0, headRx, headRy, headRz, 0.04);
                s.ellipsoid(0, y - 0.09 * k, 0.03 * k, 0.06 * k, 0.05 * k, 0.07 * k, 0.04);
            }
            case 2 -> {
                // 又窄又长，下巴拖下来
                headRx = 0.078 * k;
                headRy = 0.17 * k;
                headRz = 0.1 * k;
                s.ellipsoid(0, y + 0.03 * k, 0, headRx, headRy, headRz, 0.04);
                s.ellipsoid(0, y - 0.12 * k, 0.025 * k, 0.05 * k, 0.08 * k, 0.065 * k, 0.04);
            }
            case 3 -> {
                // 扁、宽
                headRx = 0.15 * k;
                headRy = 0.1 * k;
                headRz = 0.11 * k;
                s.ellipsoid(0, y, 0, headRx, headRy, headRz, 0.04);
                s.ellipsoid(0, y - 0.07 * k, 0.03 * k, 0.1 * k, 0.05 * k, 0.08 * k, 0.04);
            }
            default -> {
                // 差不多像人：这种最难看出来哪里不对
                headRx = 0.095 * k;
                headRy = 0.12 * k;
                headRz = 0.11 * k;
                s.ellipsoid(0, y, 0, headRx, headRy, headRz, 0.04);
                s.ellipsoid(0, y - 0.08 * k, 0.03 * k, 0.07 * k, 0.06 * k, 0.075 * k, 0.04);
            }
        }
        faceZ = headRz * 0.92;
        // 耳朵、鼻子：有的有，有的没长出来
        if (chance(0.45)) {
            double ey = y + rand(-0.03, 0.03) * k, size = rand(0.018, 0.034) * k;
            s.ellipsoid(-headRx * 0.98, ey, -0.005, size * 0.6, size * 1.4, size, 0.02);
            s.ellipsoid(headRx * 0.98, ey + rand(-0.02, 0.02), -0.005, size * 0.6, size * 1.4, size, 0.02);
        }
        if (chance(0.4)) {
            double len = rand(0.03, 0.07) * k;
            s.capsule(0, y + 0.01 * k, faceZ, 0.012 * k, 0, y - len, faceZ + 0.025 * k, 0.014 * k, 0.02);
        }
        // 眼窝：位置、大小、两只不一样大；有的没有，有的多一只
        int eyes = chance(0.1) ? 0 : chance(0.06) ? 3 : 2;
        double spread = rand(0.03, 0.056) * k, ey = y + rand(-0.025, 0.035) * k, er = rand(0.022, 0.038) * k, skew = rand(0.7, 1.3);
        for (int i = 0; i < eyes; i++) {
            double ex = i == 2 ? 0 : (i == 0 ? -1 : 1) * spread, yy = i == 2 ? ey + 0.06 * k : ey + (i == 1 ? rand(-0.01, 0.01) : 0);
            double rr = er * (i == 1 ? skew : 1) * (i == 2 ? 0.7 : 1);
            double ez = faceZ * Math.sqrt(Math.max(0.05, 1 - (ex / headRx) * (ex / headRx))) + 0.01;
            s.dent(ex, yy, ez, rr);
            holes.add(new double[]{ex, yy, ez - rr * 0.35, ex, yy - 0.001, ez - rr * 0.35, rr * 0.8});
        }
        // 嘴
        double my = y - rand(0.07, 0.11) * k, mz = faceZ + 0.005;
        switch (r.nextInt(5)) {
            case 0 -> {
                // 一道竖着拉开的口子，一直裂到下巴
                s.groove(0, my, mz, 0, my - 0.09 * k, mz - 0.01, 0.018);
                holes.add(new double[]{0, my, mz - 0.015, 0, my - 0.09 * k, mz - 0.025, 0.016});
            }
            case 1 -> {
                double w = rand(0.03, 0.06) * k;
                s.groove(-w, my, mz - 0.01, w, my, mz - 0.01, 0.013);
                holes.add(new double[]{-w * 0.9, my, mz - 0.02, w * 0.9, my, mz - 0.02, 0.011});
            }
            case 2 -> {
                s.dent(0, my, mz, 0.024 * k);
                holes.add(new double[]{0, my, mz - 0.012, 0, my - 0.001, mz - 0.012, 0.02 * k});
            }
            case 3 -> {
                // 没长出嘴
            }
            default -> {
                // 下巴掉下来，张得太长
                s.groove(0, my + 0.01, mz, 0, my - 0.17 * k, mz - 0.02, 0.024);
                holes.add(new double[]{0, my, mz - 0.02, 0, my - 0.16 * k, mz - 0.035, 0.021});
            }
        }
        // 头发
        slot(HAIR);
        double top = y + headRy * 0.55;
        switch (hair) {
            case "short" -> s.ellipsoid(0, y + headRy * 0.25, -headRz * 0.12, headRx * 1.06, headRy * 0.82, headRz * 1.0, 0.01);
            case "long" -> {
                s.ellipsoid(0, y + headRy * 0.25, -headRz * 0.15, headRx * 1.08, headRy * 0.85, headRz * 1.0, 0.01);
                s.capsule(0, y, -headRz * 0.7, headRx * 0.9, 0, shoulderY + 0.02, -headRz * 0.9 - 0.02, headRx * 0.7, 0.03);
            }
            case "pony" -> {
                s.ellipsoid(0, y + headRy * 0.25, -headRz * 0.12, headRx * 1.06, headRy * 0.82, headRz * 1.0, 0.01);
                s.capsule(0, top - 0.02, -headRz * 1.0, 0.03, 0, y - 0.12, -headRz * 1.3, 0.022, 0.02);
            }
            case "bun" -> {
                s.ellipsoid(0, y + headRy * 0.25, -headRz * 0.12, headRx * 1.06, headRy * 0.82, headRz * 1.0, 0.01);
                s.ellipsoid(0, top + 0.03, -headRz * 0.6, 0.05, 0.045, 0.05, 0.02);
            }
            default -> {
            }
        }
        // 帽子
        slot(HAT);
        double crown = y + headRy * 0.55;
        switch (hat) {
            case "cap" -> {
                s.ellipsoid(0, crown, -0.005, headRx * 1.1, headRy * 0.55, headRz * 1.08, 0.005);
                s.ellipsoid(0, crown - headRy * 0.2, headRz * 1.05, headRx * 0.85, 0.012, 0.08, 0.004);
            }
            case "visor" -> s.ellipsoid(0, crown - headRy * 0.15, headRz * 1.0, headRx * 0.95, 0.012, 0.085, 0.004);
            case "sun" -> {
                s.ellipsoid(0, crown, 0, headRx * 1.12, headRy * 0.6, headRz * 1.1, 0.005);
                s.ellipsoid(0, crown - headRy * 0.2, 0, 0.21, 0.012, 0.21, 0.006);
            }
            case "bucket" -> {
                s.ellipsoid(0, crown, 0, headRx * 1.12, headRy * 0.62, headRz * 1.1, 0.005);
                s.capsule(0, crown - headRy * 0.25, 0, headRx * 1.25, 0, crown - headRy * 0.4, 0, headRx * 1.45, 0.01);
            }
            case "ranger" -> {
                s.ellipsoid(0, crown + 0.03, 0, headRx * 1.05, headRy * 0.75, headRz * 1.02, 0.005);
                s.ellipsoid(0, crown - headRy * 0.18, 0, 0.23, 0.01, 0.23, 0.004);
            }
            case "hard" -> {
                s.ellipsoid(0, crown + 0.005, 0, headRx * 1.18, headRy * 0.66, headRz * 1.16, 0.005);
                s.ellipsoid(0, crown - headRy * 0.2, 0.02, headRx * 1.3, 0.014, headRz * 1.32, 0.004);
                // 头灯：灯座跟帽子一个颜色，灯面是亮的
                s.capsule(0, crown + 0.0, headRz * 1.0, 0.026, 0, crown + 0.0, headRz * 1.0 + 0.035, 0.026, 0.004);
                slot(LAMP);
                s.capsule(0, crown, headRz * 1.0 + 0.04, 0.02, 0, crown, headRz * 1.0 + 0.047, 0.02, 0.002);
            }
            case "paper" -> s.capsule(0, crown - 0.02, -0.01, headRx * 0.95, 0, crown + 0.16, -0.01, headRx * 1.05, 0.01);
            case "swimcap" -> s.ellipsoid(0, y + headRy * 0.22, -headRz * 0.05, headRx * 1.07, headRy * 0.88, headRz * 1.06, 0.006);
            case "headset" -> {
                slot(TRIM);
                s.capsule(-headRx * 1.02, y + 0.01, 0, 0.01, 0, crown + headRy * 0.45, 0, 0.01, 0.004);
                s.capsule(0, crown + headRy * 0.45, 0, 0.01, headRx * 1.02, y + 0.01, 0, 0.01, 0.004);
                s.ellipsoid(-headRx * 1.02, y, 0, 0.02, 0.04, 0.035, 0.005);
                s.ellipsoid(headRx * 1.02, y, 0, 0.02, 0.04, 0.035, 0.005);
                s.capsule(-headRx * 1.02, y - 0.02, 0.02, 0.006, -0.03, y - 0.1 * k, faceZ + 0.04, 0.006, 0.003);
                s.ellipsoid(-0.03, y - 0.1 * k, faceZ + 0.045, 0.014, 0.012, 0.014, 0.003);
            }
            default -> {
            }
        }
    }

    private void legs() {
        double pants = bottom.equals("pants") ? 0.012 : 0;
        for (double side : new double[]{-1, 1}) {
            double x = side * (hips * 0.55 + 0.015);
            double kneeY = sitting() ? pelvisY : pelvisY * 0.56;
            double[] knee = sitting() ? new double[]{x * 1.1, kneeY - 0.01, 0.44} : new double[]{x * 1.15, kneeY, 0.015};
            double[] ankle = sitting() ? new double[]{x * 1.15, 0.08, 0.48} : new double[]{x * 1.2, 0.08, -0.01};
            double[] mid = {(x + knee[0]) / 2, (pelvisY - 0.02 + knee[1]) / 2, knee[2] / 2};
            // 大腿：上半截短裤盖着，长袍、裙子的下摆另外加
            boolean robe = top.equals("dress") || top.equals("robe");
            int upper = switch (bottom) {
                case "none" -> robe ? TOP : SKIN;
                case "brief" -> SKIN;
                default -> BOTTOM;
            };
            int lower = bottom.equals("pants") || bottom.equals("capri") ? BOTTOM : bottom.equals("none") && top.equals("robe") ? TOP : SKIN;
            slot(upper);
            s.capsule(x, pelvisY - 0.02, 0, 0.06 * limb + (upper == SKIN ? 0 : 0.012), mid[0], mid[1], mid[2], 0.05 * limb + (upper == SKIN ? 0 : 0.012), 0.04);
            slot(lower);
            s.capsule(mid[0], mid[1], mid[2], 0.05 * limb + pants, knee[0], knee[1], knee[2], 0.038 * limb + pants, 0.04);
            s.ellipsoid(knee[0], knee[1] - 0.01, knee[2] + 0.03, 0.045 * limb + pants, 0.05, 0.045 + pants, 0.03);
            // 小腿
            double[] calf = {(knee[0] + ankle[0]) / 2, (knee[1] + ankle[1]) / 2, (knee[2] + ankle[2]) / 2};
            slot(bottom.equals("pants") || bottom.equals("capri") ? BOTTOM : SKIN);
            s.capsule(knee[0], knee[1] - 0.02, knee[2], 0.037 * limb + pants, calf[0], calf[1], calf[2], 0.032 * limb + pants, 0.03);
            slot(bottom.equals("pants") ? BOTTOM : shoes.equals("boots") ? SHOES : SKIN);
            s.capsule(calf[0], calf[1], calf[2], 0.032 * limb + pants, ankle[0], ankle[1], ankle[2], 0.025 * limb + (shoes.equals("boots") ? 0.014 : pants), 0.03);
            // 脚
            slot(shoes.equals("none") || shoes.equals("sandals") ? SKIN : SHOES);
            double sh = shoes.equals("none") || shoes.equals("sandals") ? 0 : 0.012;
            s.capsule(ankle[0], 0.04, ankle[2] - 0.02, 0.026 + sh, ankle[0] * 1.04, 0.025, ankle[2] + 0.14, 0.02 + sh, 0.03);
            if (shoes.equals("sandals")) {
                slot(SHOES);
                s.capsule(ankle[0], 0.006, ankle[2] - 0.03, 0.03, ankle[0] * 1.04, 0.006, ankle[2] + 0.15, 0.028, 0.004);
            }
        }
        // 裙子、长袍的下摆：上窄下宽的圆台，下摆是平的；坐着的时候铺在腿上
        if (bottom.equals("skirt") || top.equals("dress")) {
            slot(bottom.equals("skirt") ? BOTTOM : TOP);
            double flare = hips + rand(0.06, 0.11), hem = pelvisY - rand(0.28, 0.44);
            if (sitting()) s.cone(0, pelvisY + 0.06, 0.02, hips + 0.035, pelvisY - 0.06, 0.2, flare + 0.06, 0.02);
            else s.cone(0, pelvisY + 0.1, 0, hips + 0.035, hem, 0.02, flare, 0.02);
        }
        if (top.equals("robe")) {
            slot(TOP);
            if (sitting()) s.cone(0, chestY, 0, chestX + 0.03, pelvisY - 0.04, 0.22, hips + 0.12, 0.03);
            else s.cone(0, chestY, 0, chestX + 0.03, pelvisY - 0.5, 0.03, hips + 0.1, 0.03);
        }
    }

    private void arms() {
        for (double side : new double[]{-1, 1}) {
            double sx = side * shoulder;
            double[] elbow, wrist, dir;
            boolean up = posture.equals("raised") && side > 0;
            if (up) {
                // 手举起来，手掌贴在洞口上
                elbow = new double[]{side * (shoulder + 0.1), shoulderY + 0.31 * armK, 0.08};
                wrist = new double[]{side * (shoulder + 0.13), shoulderY + 0.64 * armK, 0.15};
                dir = new double[]{0, 1, 0.1};
            } else if (posture.equals("reach")) {
                elbow = new double[]{side * (shoulder - 0.01), shoulderY - 0.06, 0.33 * armK};
                wrist = new double[]{side * (shoulder - 0.04), shoulderY - 0.1, 0.68 * armK};
                dir = new double[]{0, -0.15, 1};
            } else if (posture.equals("sit")) {
                elbow = new double[]{side * (shoulder + 0.03), shoulderY - 0.34 * armK, 0.12};
                wrist = new double[]{side * (hips * 0.75), pelvisY + 0.1, 0.36};
                dir = new double[]{0, -0.5, 1};
            } else if (posture.equals("clap")) {
                elbow = new double[]{side * (shoulder + 0.05), shoulderY - 0.3, 0.14};
                wrist = new double[]{side * 0.035, shoulderY - 0.22, 0.34};
                dir = new double[]{0, 1, 0.15};
            } else {
                double bend = rand(0, 0.08);
                elbow = new double[]{side * (shoulder + 0.035), shoulderY - 0.38 * armK, 0.02 + bend};
                wrist = new double[]{side * (shoulder + 0.05), shoulderY - 0.38 * armK - 0.4 * armK, 0.06 + bend * 2.5};
                dir = new double[]{0, -1, 0.05};
            }
            slot(sleeves() ? TOP : SKIN);
            double pad = sleeves() ? 0.012 : 0;
            double[] mid = {(sx + elbow[0]) / 2, (shoulderY + elbow[1]) / 2, elbow[2] / 2};
            s.capsule(sx, shoulderY - 0.01, 0, 0.034 * limb + pad, mid[0], mid[1], mid[2], 0.031 * limb + pad, 0.03);
            slot(longSleeves() ? TOP : SKIN);
            s.capsule(mid[0], mid[1], mid[2], 0.031 * limb + (longSleeves() ? 0.012 : 0), elbow[0], elbow[1], elbow[2], 0.028 * limb + (longSleeves() ? 0.012 : 0), 0.03);
            s.ellipsoid(elbow[0], elbow[1], elbow[2], 0.032 * limb, 0.032, 0.032, 0.02);
            s.capsule(elbow[0], elbow[1], elbow[2], 0.026 * limb + (longSleeves() ? 0.012 : 0), wrist[0], wrist[1], wrist[2], 0.02 * limb + (longSleeves() ? 0.01 : 0), 0.02);
            slot(SKIN);
            hand(wrist, dir, side);
        }
    }

    /** 手掌和几根很长的手指。dir 是手指朝的方向。 */
    private void hand(double[] w, double[] dir, double side) {
        double l = Math.sqrt(dir[0] * dir[0] + dir[1] * dir[1] + dir[2] * dir[2]);
        double dx = dir[0] / l, dy = dir[1] / l, dz = dir[2] / l;
        double px = w[0] + dx * 0.06, py = w[1] + dy * 0.06, pz = w[2] + dz * 0.06;
        s.ellipsoid(px, py, pz, 0.035, Math.abs(dy) > 0.5 ? 0.06 : 0.035, Math.abs(dz) > 0.5 ? 0.06 : 0.016, 0.02);
        for (int i = 0; i < fingers; i++) {
            double off = (i - (fingers - 1) / 2.0) * 0.017 * side, spread = (i - (fingers - 1) / 2.0) * 0.012 * side;
            double len = fingerLen - Math.abs(i - (fingers - 1) / 2.0) * 0.025;
            double bx = px + dx * 0.05 + off, by = py + dy * 0.05, bz = pz + dz * 0.05;
            s.capsule(bx, by, bz, 0.012, bx + dx * len + spread, by + dy * len, bz + dz * len + (Math.abs(dz) > 0.5 ? 0 : 0.015), 0.009, 0.008);
        }
        s.capsule(px - side * 0.035, py, pz + 0.01, 0.012, px - side * 0.065 + dx * 0.08, py + dy * 0.08, pz + 0.03 + dz * 0.08, 0.01, 0.008);
    }

    private void clothes() {
        // 包、腰包、相机、对讲机、围裙、领带
        switch (bag) {
            case "backpack" -> {
                slot(BAG);
                s.box(0, chestY - 0.03, -chestZ - 0.08, 0.13, 0.17, 0.07, 0.035, 0.01);
                slot(TRIM);
                color[TRIM] = color[BAG];
                for (double side : new double[]{-1, 1}) s.capsule(side * 0.08, shoulderY + 0.02, 0.0, 0.014, side * 0.09, chestY - 0.12, chestZ + 0.01, 0.012, 0.004);
            }
            case "fanny" -> {
                slot(BAG);
                s.ellipsoid(0, pelvisY + 0.08, 0.1 + belly * 0.05, 0.09, 0.045, 0.04, 0.008);
            }
            case "camera" -> {
                slot(BAG);
                color[BAG] = BLACK;
                s.box(0, chestY - 0.06, chestZ + 0.05, 0.06, 0.04, 0.03, 0.008, 0.004);
                s.capsule(0, chestY - 0.06, chestZ + 0.08, 0.02, 0, chestY - 0.06, chestZ + 0.11, 0.02, 0.004);
                slot(TRIM);
                color[TRIM] = BLACK;
                s.capsule(-0.11, shoulderY + 0.03, 0, 0.007, 0, chestY - 0.03, chestZ + 0.04, 0.007, 0.003);
                s.capsule(0.11, shoulderY + 0.03, 0, 0.007, 0, chestY - 0.03, chestZ + 0.04, 0.007, 0.003);
            }
            case "shoulder" -> {
                slot(BAG);
                s.box(0.18, pelvisY + 0.02, 0.02, 0.045, 0.09, 0.12, 0.025, 0.008);
                s.capsule(-0.12, shoulderY + 0.02, 0.0, 0.01, 0.15, pelvisY + 0.1, 0.06, 0.01, 0.004);
            }
            case "radio" -> {
                slot(BAG);
                s.box(-0.08, chestY + 0.04, chestZ + 0.03, 0.03, 0.06, 0.02, 0.008, 0.005);
                s.capsule(-0.09, chestY + 0.1, chestZ + 0.03, 0.006, -0.09, chestY + 0.22, chestZ + 0.03, 0.005, 0.003);
                slot(TRIM);
                s.ellipsoid(0, pelvisY + 0.08, 0, hips + 0.045, 0.022, 0.1, 0.004);
            }
            case "belt" -> {
                slot(TRIM);
                s.ellipsoid(0, pelvisY + 0.08, 0, hips + 0.045, 0.024, 0.1, 0.004);
                slot(BAG);
                s.ellipsoid(0.12, pelvisY + 0.04, 0.06, 0.035, 0.05, 0.03, 0.004);
            }
            case "apron" -> {
                slot(APRON);
                double hem = sitting() ? pelvisY - 0.02 : pelvisY - 0.42;
                s.box(0, (chestY + hem) / 2, chestZ + 0.025 + belly * 0.06, chestX + 0.02, (chestY - hem) / 2 + 0.04, 0.012, 0.01, 0.01);
            }
            case "tie" -> {
                slot(APRON);
                s.capsule(0, shoulderY + 0.02, chestZ + 0.015, 0.012, 0, chestY - 0.12, chestZ + 0.02, 0.022, 0.004);
            }
            default -> {
            }
        }
        if (vested) {
            // 反光背心：套在上衣外面，跟着驼背；两根肩带搭过肩膀；两道反光条
            slot(VEST);
            s.ellipsoid(0, chestY - 0.13, 0.004, chestX + 0.035, 0.27 * torsoK, chestZ + 0.04 + belly * 0.04, 0.01);
            for (double side : new double[]{-1, 1}) {
                s.capsule(side * 0.115, chestY + 0.08, chestZ + 0.02, 0.03, side * 0.13, shoulderY + 0.04, 0, 0.028, 0.01);
                s.capsule(side * 0.13, shoulderY + 0.04, 0, 0.028, side * 0.115, chestY + 0.08, -chestZ - 0.02, 0.03, 0.01);
            }
            double low = chestY - 0.3 * torsoK, high = chestY - 0.1 * torsoK;
            s.paint = (slot, x, y, z) -> slot == VEST && (Math.abs(y - low) < 0.022 || Math.abs(y - high) < 0.022) ? STRIP : slot;
            cardZ = chestZ + 0.06;
        }
    }
}
