package world.hechao.fleshpark.client;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;

/**
 * 叠在游戏画面上的东西：黑场、暖光、招募函、片名、体验报告、片尾。
 * 纸面按赫朝视觉语言：红外框、暖纸底、墨字、硬阴影。片名用方格收束加逐字盖章。
 */
final class Overlay {
    private static final int RED = 0xFFD74735, PAPER = 0xFFFFFBF5, INK = 0xFF24211F, INK_SOFT = 0xFF6B645E, PAPER_LINE = 0xFFDCD2C3;
    private static final String TITLE = "血肉公园";

    private static final String[] ROWS = {"地面车站", "缆车乘坐", "游客中心", "全景窗", "羊膜温泉", "支气管森林"};

    private static long introAt = -1, titleAt = -1, reportAt = -1, endAt = -1, lastTitle;
    private static long subAt = -1, stampAt = -1, glitchUntil = -1, nextGlitch;
    private static String[] sub = new String[0], stamp = new String[0];
    private static final java.util.Random RND = new java.util.Random();
    private static String[] report = new String[0];
    private static int stamped;

    private Overlay() {
    }

    /** 情景提要。不带参数是开场那份；带参数是：日期|大字|一行|一行…|最后一句提示|秒数。结尾也用同一个样子收。 */
    static void intro(String[] custom) {
        clear();
        introAt = now();
        briefDate = "2026 年 11 月  ·  西得克萨斯  ·  夜";
        briefHead = "体验官 #0011";
        briefLines = BRIEF;
        briefHint = "▶  按下站牌上的呼叫按钮";
        briefSeconds = BRIEF_SECONDS;
        if (custom != null && custom.length >= 5) {
            briefDate = custom[0];
            briefHead = custom[1];
            briefLines = java.util.Arrays.copyOfRange(custom, 2, custom.length - 2);
            briefHint = "▶  " + custom[custom.length - 2];
            briefSeconds = Float.parseFloat(custom[custom.length - 1]);
        }
    }

    static void title() {
        // 同一次开场里服务端偶尔会连发两次（前面还跟着一条 clear）：接着放原来那一遍，不从头再来
        if (now() - lastTitle < 6000) {
            titleAt = lastTitle;
            return;
        }
        lastTitle = titleAt = now();
        stamped = 0;
    }

    /** 日期、六项分数、总分。 */
    static void report(String[] fields) {
        report = fields;
        reportAt = now();
    }

    private static long termAt = -1;
    private static String[] term = new String[0];

    /** 机房那块屏：一行一行打出来。term off 收掉。以 ! 开头的一行是红的。 */
    static void term(String body) {
        if (body.equals("off")) {
            termAt = -1;
            return;
        }
        term = body.split("\\|");
        termAt = now();
    }

    private static void drawTerm(GuiGraphics g, Font font, int w, int h) {
        float t = (now() - termAt) / 1000f;
        if (t > 90) {
            termAt = -1;
            return;
        }
        int lh = 11, widest = 0;
        for (String line : term) widest = Math.max(widest, font.width(line.startsWith("!") ? line.substring(1) : line));
        int pw = Math.min(w - 24, widest + 28), ph = 22 + term.length * lh;
        int x = (w - pw) / 2, y = Math.max(16, (h - ph) / 2);
        float in = Math.min(1, t / 0.25f);
        // 机柜上那块老屏：深色的玻璃，一圈灰壳子，扫描线
        g.fill(x - 4, y - 4, x + pw + 4, y + ph + 4, (int) (in * 255) << 24 | 0x2A2E2C);
        g.fill(x, y, x + pw, y + ph, (int) (in * 245) << 24 | 0x04100A);
        for (int sy = y; sy < y + ph; sy += 2) g.fill(x, sy, x + pw, sy + 1, 0x1A000000);
        int shown = (int) ((t - 0.3f) * 34), ty = y + 11;
        for (String line : term) {
            if (shown <= 0) break;
            boolean red = line.startsWith("!");
            String body = red ? line.substring(1) : line;
            String part = body.length() <= shown ? body : body.substring(0, shown);
            g.drawString(font, part, x + 14, ty, red ? 0xFFFF5A44 : 0xFF8CF0A6, false);
            shown -= body.length();
            ty += lh;
        }
        if ((now() / 450) % 2 == 0 && shown > 0) g.fill(x + 14, ty, x + 20, ty + 8, 0xFF8CF0A6);
    }

    static void endCard() {
        reportAt = -1;
        endAt = now();
    }

    /** 谁说的、说什么、显示多少 tick。 */
    static void subtitle(String[] fields) {
        sub = fields;
        subAt = now();
    }

    /** 标题，然后几行字。 */
    static void notice(String[] fields) {
        // 园方通告不再弹在画面上，发到手机里
        if (fields.length < 2) return;
        StringBuilder body = new StringBuilder(fields[0]).append("：");
        for (int i = 1; i < fields.length; i++) body.append(fields[i]);
        Phone.receive(new String[]{"息壤国家公园", "", body.toString()});
    }

    static void setTape(boolean on) {
        tapeHidden = !on;
    }

    private static final String TAPE_LABEL = "存档  体验官 #0011";
    private static String tapeLabel = TAPE_LABEL;

    /** 角上那行字换掉，红点不闪了：这一段不再是存档。 */
    static void setTapeLabel(String label) {
        tapeLabel = label.isEmpty() ? TAPE_LABEL : label;
    }

    private static boolean tapeHidden;

    /** 搜救二队老周那台 DV 拍的录像：2007 年 7 月 7 日凌晨那一段。-1 是没在放，不然是开头那一秒（时:分:秒）。 */
    private static int helmetSec = -1;
    private static long helmetAt;

    /** helmet 03:20:00 开始放（钟从这一秒往后走），helmet off 回到存档。 */
    static void helmet(String arg) {
        if (arg.equals("off")) {
            helmetSec = -1;
            return;
        }
        String[] f = arg.split(":");
        helmetSec = Integer.parseInt(f[0]) * 3600 + Integer.parseInt(f[1]) * 60 + Integer.parseInt(f[2]);
        helmetAt = now();
        glitch(8);
    }

    static boolean helmet() {
        return helmetSec >= 0;
    }

    /**
     * 老周那台 DV 放出来的录像：4:3 的画面（两边黑边），画质差、四角压暗、有噪点；左上 ▶ PLAY，右上电量格一闪一闪，
     * 左下磁带计数，右下 DV 自己打的日期时间。刚开始放那半秒是蓝屏。
     */
    private static void drawHelmet(GuiGraphics g, Font font, int w, int h) {
        long ms = now(), since = ms - helmetAt;
        int pw = Math.min(w, h * 4 / 3), px = (w - pw) / 2;
        if (since < 600) {
            g.fill(0, 0, w, h, 0xFF000000);
            g.fill(px, 0, px + pw, h, 0xFF1E3FA8);
            g.drawString(font, "▶ PLAY", px + 16, 14, 0xFFFFFBF5, true);
            return;
        }
        tint(g, w, h, 0x10140A, 0.16f);
        for (int y = 0; y < h; y += 2) g.fill(0, y, w, y + 1, 0x16000000);
        int edge = 0x90000000;
        g.fillGradient(0, 0, w, h / 5, edge, 0);
        g.fillGradient(0, h - h / 5, w, h, 0, edge);
        for (int i = 0; i < 220; i++) {
            int x = px + RND.nextInt(pw), y = RND.nextInt(h);
            g.fill(x, y, x + 1, y + 1, RND.nextBoolean() ? 0x2CFFFFFF : 0x3C000000);
        }
        // 偶尔一条横着的雪花带往下走：磁带有伤
        if (ms / 3000 % 3 == 0) {
            int band = (int) (ms / 8 % (h + 40)) - 20;
            for (int i = 0; i < 90; i++) g.fill(px + RND.nextInt(pw), band + RND.nextInt(6), px + RND.nextInt(pw), band + RND.nextInt(6) + 1, 0x40FFFFFF);
        }
        g.fill(0, 0, px, h, 0xFF000000);
        g.fill(px + pw, 0, w, h, 0xFF000000);
        int white = 0xEEFFFBF5;
        g.drawString(font, "▶ PLAY", px + 16, 14, white, true);
        g.drawString(font, "SP", px + 16, 26, 0xAAFFFBF5, true);
        // 电量：只剩一格，一闪一闪
        int bx = px + pw - 40, by = 14;
        g.fill(bx, by, bx + 22, by + 1, white);
        g.fill(bx, by + 9, bx + 22, by + 10, white);
        g.fill(bx, by, bx + 1, by + 10, white);
        g.fill(bx + 21, by, bx + 22, by + 10, white);
        g.fill(bx + 22, by + 3, bx + 24, by + 7, white);
        if (ms / 500 % 2 == 0) g.fill(bx + 2, by + 2, bx + 7, by + 8, 0xFFD74735);
        int sec = helmetSec + (int) (since / 1000);
        int run = 47 * 60 + 12 + (int) (since / 1000);
        g.drawString(font, String.format("%d:%02d:%02d", run / 3600, run / 60 % 60, run % 60), px + 16, h - 22, white, true);
        int hour = sec / 3600 % 24;
        String day = "2007. 7. 7", time = String.format("%s %d:%02d:%02d", hour < 12 ? "AM" : "PM", hour % 12 == 0 ? 12 : hour % 12, sec / 60 % 60, sec % 60);
        g.drawString(font, day, px + pw - 16 - font.width(day), h - 34, white, true);
        g.drawString(font, time, px + pw - 16 - font.width(time), h - 22, white, true);
        if (ms > nextGlitch) {
            nextGlitch = ms + 2500 + RND.nextInt(4000);
            glitch(2 + RND.nextInt(4));
        }
    }

    /** 导演栏用：录像带角标开关。 */
    static void toggleTape() {
        tapeHidden = !tapeHidden;
    }

    private static long diaryAt = -1, diaryClosingAt = -1;
    private static String[] diary = new String[0];
    /** 纸一直拿在手上，右键收进背包。很久不收也会自己收起来。 */
    private static final float DIARY_MAX = 120;

    /** 谁写的、日期，然后几行字。同一张正拿着的时候再点一下不重来，不然按住右键会一直重放、抖个不停。 */
    static void diary(String[] fields) {
        if (diaryOpen() && java.util.Arrays.equals(fields, diary)) return;
        diary = fields;
        diaryAt = now();
        diaryClosingAt = -1;
    }

    static boolean diaryOpen() {
        return diaryAt >= 0 && diaryClosingAt < 0;
    }

    /** 右键：纸折起来往左下角一收，进了背包。 */
    static void closeDiary() {
        if (!diaryOpen()) return;
        diaryClosingAt = now();
        Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.BOOK_PAGE_TURN, 1.3f, 0.6f));
    }

    /** 车站站台那一层算零米。 */
    private static final int DEPTH_ZERO = 231;
    private static boolean depthOn;
    private static float depthShown = -1, depthAlpha;

    static void depth(boolean on) {
        depthOn = on;
    }

    /** 几颗星、当前满意度。 */
    static void stamp(String[] fields) {
        stamp = fields;
        stampAt = now();
    }

    private static long flashAt = -1, joltAt = -1;

    /** 惊吓那一下：画面红黑闪几帧，撕得很厉害。 */
    static void flash() {
        flashAt = now();
    }

    static void jolt() {
        joltAt = now();
        glitch(14);
    }

    static void glitch(int ticks) {
        glitchUntil = Math.max(glitchUntil, now() + ticks * 50L);
    }

    static void clear() {
        introAt = titleAt = reportAt = endAt = subAt = stampAt = diaryAt = termAt = -1;
        depthOn = false;
        depthAlpha = 0;
        report = new String[0];
        tapeLabel = TAPE_LABEL;
        helmetSec = -1;
        Phone.clear();
        Tags.clear();
    }

    private static long now() {
        return System.currentTimeMillis();
    }

    static void render(GuiGraphics g, DeltaTracker delta) {
        ClientFx.meshFrame();
        Minecraft mc = Minecraft.getInstance();
        Walk.frame(mc);
        Rec.frame(mc);
        if (!ParkFog.applies(mc.level)) return;
        int w = g.guiWidth(), h = g.guiHeight();
        if (ClientFx.warm > 0) tint(g, w, h, 0xE89A50, 0.2f * ClientFx.warm);
        if (ClientFx.sick > 0) {
            tint(g, w, h, 0x220606, 0.42f * ClientFx.sick);
            int edge = (int) (150 * ClientFx.sick) << 24;
            g.fillGradient(0, 0, w, h / 3, edge, 0);
            g.fillGradient(0, h - h / 3, w, h, 0, edge);
        }
        float red = ClientFx.red();
        if (red > 0) {
            // 发疯最后：一层层变红，边上更深
            tint(g, w, h, 0x8E0B06, 0.66f * red);
            int edge = (int) (Math.min(1, red) * 235) << 24 | 0x2A0000;
            g.fillGradient(0, 0, w, h / 2, edge, 0x002A0000);
            g.fillGradient(0, h / 2, w, h, 0x002A0000, edge);
        }
        if (ClientFx.black > 0) tint(g, w, h, 0, ClientFx.black);
        Font font = mc.font;
        boolean paper = introAt >= 0 || titleAt >= 0 || reportAt >= 0 || endAt >= 0;
        if (cine) {
            drawCine(g, font, w, h);
        } else if (!paper) {
            if (helmetSec >= 0) drawHelmet(g, font, w, h);
            else if (!tapeHidden && !Rec.clean) drawTape(g, font, w, h);
            if (!Rec.clean) drawDepth(g, font, w, h, mc);
            if (ClientFx.black < 0.5f && !Rec.clean) Tags.draw(g, font, w, h, mc);
            if (diaryAt >= 0) drawDiary(g, font, w, h);
            if (!Rec.clean) Phone.draw(g, font, w, h, mc);
            if (stampAt >= 0) drawStamp(g, font, w, h);
            if (subAt >= 0 && !Rec.clean) drawSubtitle(g, font, w, h);
            if (termAt >= 0) drawTerm(g, font, w, h);
        }
        drawGlitch(g, w, h);
        if (flashAt >= 0) {
            // 拍照的闪光灯：一下全白，半秒退掉
            long ms = now() - flashAt;
            if (ms > 700) flashAt = -1;
            else tint(g, w, h, 0xFFF8EC, ms < 60 ? 0.92f : 0.92f * (1 - (ms - 60) / 640f));
        }
        if (joltAt >= 0) {
            long ms = now() - joltAt;
            if (ms > 320) joltAt = -1;
            else if (ms / 40 % 2 == 0) tint(g, w, h, ms < 120 ? 0xD74735 : 0, ms < 120 ? 0.22f : 0.5f);
        }
        if (introAt >= 0) drawIntro(g, font, w, h);
        if (titleAt >= 0) drawTitle(g, font, w, h, mc);
        if (reportAt >= 0) drawReport(g, font, w, h);
        if (endAt >= 0) drawEnd(g, font, w, h);
    }

    private static void tint(GuiGraphics g, int w, int h, int rgb, float alpha) {
        g.fill(0, 0, w, h, (int) (Math.min(1, alpha) * 255) << 24 | rgb);
    }

    // ---------- 录像带：角上的字、扫描线 ----------

    private static void drawTape(GuiGraphics g, Font font, int w, int h) {
        for (int y = 0; y < h; y += 3) g.fill(0, y, w, y + 1, 0x0E000000);
        long ms = now();
        boolean blink = ms / 700 % 2 == 0;
        boolean archive = tapeLabel.equals(TAPE_LABEL);
        if (blink && archive) g.fill(10, 10, 16, 16, RED);
        g.drawString(font, tapeLabel, 20, 9, 0x99FFFBF5, false);
        String right = "安诺丹公园家族 内部资料";
        g.drawString(font, right, w - 10 - font.width(right), 9, 0x66FFFBF5, false);
        java.time.LocalTime now = java.time.LocalTime.now();
        String clock = String.format("2026.11.07  %02d:%02d:%02d", now.getHour(), now.getMinute(), now.getSecond());
        g.drawString(font, clock, 10, h - 17, 0x88FFFBF5, false);
        if (ms > nextGlitch) {
            nextGlitch = ms + 18000 + RND.nextInt(30000);
            glitch(4 + RND.nextInt(6));
        }
    }

    private static void drawGlitch(GuiGraphics g, int w, int h) {
        if (now() > glitchUntil) return;
        for (int i = 0; i < 7; i++) {
            int y = RND.nextInt(h), band = 1 + RND.nextInt(5), x = RND.nextInt(w / 2);
            int color = switch (RND.nextInt(4)) {
                case 0 -> 0x55FFFBF5;
                case 1 -> 0x66D74735;
                case 2 -> 0x8824211F;
                default -> 0x3300FFFF;
            };
            g.fill(x, y, x + w / 3 + RND.nextInt(w / 2), y + band, color);
        }
        for (int i = 0; i < 120; i++) {
            int x = RND.nextInt(w), y = RND.nextInt(h);
            g.fill(x, y, x + 1 + RND.nextInt(3), y + 1, RND.nextBoolean() ? 0x44FFFFFF : 0x44000000);
        }
    }

    // ---------- 字幕：逐字盖章，最新的字是红的 ----------

    private static void drawSubtitle(GuiGraphics g, Font font, int w, int h) {
        if (sub.length < 3) {
            subAt = -1;
            return;
        }
        float t = (now() - subAt) / 1000f, life = Integer.parseInt(sub[2]) / 20f;
        if (t > life) {
            subAt = -1;
            return;
        }
        float alpha = t > life - 0.4f ? (life - t) / 0.4f : 1;
        String text = sub[1];
        int count = Math.min(text.length(), (int) (t * 11) + 1);
        int total = font.width(text), tag = font.width(sub[0]) + 8;
        int x = (w - total - tag - 6) / 2 + (now() < glitchUntil ? RND.nextInt(5) - 2 : 0), y = h - 30;
        g.fill(x - 4, y - 3, x + tag + total + 8, y + 11, alphaOf(0x7024211F, alpha));
        g.fill(x - 4, y - 3, x + tag - 2, y + 11, alphaOf(0xCCD74735, alpha));
        g.drawString(font, sub[0], x - 2, y, alphaOf(0xFFFFFBF5, alpha), false);
        int cx = x + tag + 3;
        for (int i = 0; i < count; i++) {
            String c = text.substring(i, i + 1);
            boolean fresh = i == count - 1 && count < text.length();
            g.drawString(font, c, cx, y, alphaOf(fresh ? 0xFFD74735 : 0xFFFFFBF5, alpha), false);
            cx += font.width(c);
        }
    }

    // ---------- 入场片和收尾：上下黑边，中间是世界里的镜头，或者一张文件，旁白的字幕在下面的黑边里 ----------

    private static boolean cine;
    private static String doc = "";
    private static long docAt = -1;

    static void cine(boolean on) {
        cine = on;
        doc = "";
        if (!on) subAt = -1;
    }

    static void doc(String id) {
        doc = id.equals("off") ? "" : id;
        docAt = now();
    }

    static boolean cinematic() {
        return cine || helmetSec >= 0;
    }

    private static void drawCine(GuiGraphics g, Font font, int w, int h) {
        if (!doc.isEmpty()) {
            float t = (now() - docAt) / 1000f;
            g.fill(0, 0, w, h, 0xF00C0B0A);
            g.pose().pushMatrix();
            // 文件慢慢推近一点
            float k = 1 + Math.min(t, 9) * 0.006f;
            g.pose().translate(w / 2f, h / 2f);
            g.pose().scale(k, k);
            g.pose().rotate((float) Math.toRadians(-1.0));
            Docs.draw(g, font, doc);
            g.pose().popMatrix();
        }
        int bar = Math.max(22, h / 9);
        g.fill(0, 0, w, bar, 0xFF000000);
        g.fill(0, h - bar, w, h, 0xFF000000);
        // 旁白：白字，在下面的黑边里，太长就折成两行
        if (subAt >= 0 && sub.length >= 3) {
            float t = (now() - subAt) / 1000f, life = Integer.parseInt(sub[2]) / 20f;
            if (t > life) {
                subAt = -1;
                return;
            }
            float alpha = Math.min(1, t / 0.25f) * (t > life - 0.4f ? (life - t) / 0.4f : 1);
            if (sub[0].equals("档案")) {
                drawArchive(g, font, w, h, bar, t, alpha);
                return;
            }
            java.util.List<Marked> rows = Marked.of(sub[1]).wrap(font, w - 80);
            int y = h - bar / 2 - rows.size() * 6;
            for (Marked row : rows) {
                String line = row.plain();
                g.drawString(font, line, (w - font.width(line)) / 2, y, alphaOf(0xFFF2EEE4, alpha), false);
                y += 12;
            }
        }
    }

    /** 开场的档案字幕：冷灰色的字，一行一行打出来，¶ 是换行。没有人声，只有打字的节奏。 */
    private static void drawArchive(GuiGraphics g, Font font, int w, int h, int bar, float t, float alpha) {
        String[] lines = sub[1].split("¶");
        int budget = (int) (Math.max(0, t - 0.2f) * 16), y = h - bar / 2 - lines.length * 6;
        for (String line : lines) {
            int count = Math.min(line.length(), budget);
            int x = (w - font.width(line)) / 2;
            if (count > 0) g.drawString(font, line.substring(0, count), x, y, alphaOf(0xFFB8BEC2, alpha), false);
            // 光标跟在正在打的那一行后面：打字时常亮，打完一闪一闪
            boolean current = budget <= line.length();
            if (count > 0 && current && (count < line.length() || (now() / 400) % 2 == 0)) {
                int cx = x + font.width(line.substring(0, count)) + 1;
                g.fill(cx, y, cx + 5, y + 8, alphaOf(0xFFB8BEC2, alpha));
            }
            budget -= line.length();
            if (budget <= 0) break;
            y += 12;
        }
    }

    private static float ease(float t) {
        t = Math.max(0, Math.min(1, t));
        return 1 - (1 - t) * (1 - t) * (1 - t);
    }

    // ---------- 下行深度表：贴在右边，一把刻度尺跟着往上走 ----------

    private static void drawDepth(GuiGraphics g, Font font, int w, int h, Minecraft mc) {
        depthAlpha = Math.max(0, Math.min(1, depthAlpha + (depthOn ? 0.03f : -0.03f)));
        if (depthAlpha <= 0 || mc.player == null) {
            depthShown = -1;
            return;
        }
        float target = Math.max(0, (float) (DEPTH_ZERO - mc.player.getY()));
        depthShown = depthShown < 0 ? target : depthShown + (target - depthShown) * 0.2f;
        float d = depthShown, a = depthAlpha;
        int x = w - 16, mid = h / 2, half = Math.min(80, h / 2 - 40);
        float perMeter = 2.4f;
        g.fill(x, mid - half, x + 1, mid + half, alphaOf(0x55FFFBF5, a));
        int from = (int) Math.floor((d - half / perMeter) / 5) * 5, to = (int) Math.ceil(d + half / perMeter);
        for (int m = Math.max(0, from); m <= to; m += 5) {
            int y = Math.round(mid + (m - d) * perMeter);
            if (y < mid - half || y > mid + half) continue;
            // 离中线越远越淡
            float fade = a * (1 - Math.abs(y - mid) / (float) half);
            boolean major = m % 25 == 0;
            g.fill(x - (major ? 8 : 4), y, x, y + 1, alphaOf(major ? 0xAAFFFBF5 : 0x66FFFBF5, fade));
            if (major) {
                String label = String.valueOf(m);
                g.drawString(font, label, x - 11 - font.width(label), y - 4, alphaOf(0x88FFFBF5, fade), false);
            }
        }
        g.fill(x - 14, mid, x + 3, mid + 1, alphaOf(RED, a));
        String value = String.format("%03d", Math.round(d));
        int vx = x - 18 - font.width(value) * 2;
        g.pose().pushMatrix();
        g.pose().translate(vx, mid - 8);
        g.pose().scale(2, 2);
        g.drawString(font, value, 0, 0, alphaOf(0xFFFFFBF5, a), true);
        g.pose().popMatrix();
        String unit = "米";
        g.drawString(font, unit, x - 16 - font.width(unit), mid + 10, alphaOf(0xAAFFFBF5, a), true);
        String title = "下行深度";
        g.drawString(font, title, x + 1 - font.width(title), mid - half - 13, alphaOf(0x99FFFBF5, a), true);
    }

    // ---------- 前任体验官的日记：左边一张横格便签，字一行行写出来，不挡中间 ----------

    private static void drawDiary(GuiGraphics g, Font font, int w, int h) {
        if (diary.length < 3) {
            diaryAt = -1;
            return;
        }
        float t = (now() - diaryAt) / 1000f;
        int chars = 0;
        java.util.List<Marked> lines = new java.util.ArrayList<>();
        for (int i = 2; i < diary.length; i++) {
            Marked m = Marked.of(diary[i]);
            lines.add(m);
            chars += m.length();
        }
        float typed = 0.5f + chars / 16f;
        // 纸上要变的字：全写出来以后再过三秒才变
        Marked.changeAfter = diaryAt + (long) ((typed + 3) * 1000);
        if (diaryClosingAt < 0 && t > DIARY_MAX) closeDiary();
        float closing = diaryClosingAt < 0 ? 0 : (now() - diaryClosingAt) / 350f;
        if (closing >= 1) {
            diaryAt = diaryClosingAt = -1;
            return;
        }
        int pw = Math.min(200, w / 3), x = 10, text = pw - 30;
        java.util.List<Marked> rows = new java.util.ArrayList<>();
        for (Marked m : lines) rows.addAll(m.wrap(font, text));
        int ph = 44 + rows.size() * 12 + 20, y = Math.max(34, (h - ph) / 2);
        float in = ease(t / 0.4f), out = 1 - closing * closing;
        g.pose().pushMatrix();
        g.pose().translate(-(pw + 14) * (1 - in), 0);
        if (closing > 0) {
            // 往左下角缩下去
            float k = ease(closing);
            g.pose().translate(x + pw * 0.2f * k, y + (h - y) * 0.85f * k);
            g.pose().scale(1 - 0.75f * k, 1 - 0.75f * k);
            g.pose().translate(-x, -y);
        }
        g.pose().rotate((float) Math.toRadians(-1.2));
        int alpha = (int) (out * 255);
        g.fill(x + 3, y + 3, x + pw + 3, y + ph + 3, alpha * 0x44 / 255 << 24);
        g.fill(x, y, x + pw, y + ph, alpha * 0xE8 / 255 << 24 | 0xE6DCC8);
        // 横格线和左边一条红边线，纸上一块水渍
        for (int ly = y + 41; ly < y + ph - 4; ly += 12) g.fill(x + 4, ly + 10, x + pw - 4, ly + 11, alpha * 0x30 / 255 << 24 | 0x5A6C8A);
        g.fill(x + 18, y, x + 19, y + ph, alpha * 0x66 / 255 << 24 | 0xD74735);
        // 谁写的一行，日期另起一行
        g.drawString(font, diary[0], x + 24, y + 7, alphaOf(INK, out), false);
        g.drawString(font, diary[1], x + 24, y + 18, alphaOf(INK_SOFT, out), false);
        g.fill(x + 24, y + 29, x + pw - 8, y + 30, alphaOf(0xFF8A7F72, out));
        int shown = (int) ((t - 0.5f) * 16), ty = y + 41;
        for (Marked row : rows) {
            if (shown <= 0) break;
            row.draw(g, font, shown, x + 24, ty, alphaOf(0xFF2E2A6E, out), alphaOf(0xEE1A1830, out));
            shown -= row.length();
            ty += 12;
        }
        if (t > typed + 0.3f) {
            String hint = "右键收起";
            g.drawString(font, hint, x + pw - 8 - font.width(hint), y + ph - 12, alphaOf(0xFF8A7F72, out * Math.min(1, (t - typed - 0.3f) * 2)), false);
        }
        g.pose().popMatrix();
    }

    // ---------- 打分后的印章 ----------

    private static void drawStamp(GuiGraphics g, Font font, int w, int h) {
        float t = (now() - stampAt) / 1000f;
        if (t > 2.6f || stamp.length < 2) {
            stampAt = -1;
            return;
        }
        float pop = t < 0.12f ? 1.6f - t / 0.12f * 0.6f : 1, alpha = t > 2.2f ? (2.6f - t) / 0.4f : 1;
        int red = alphaOf(0xFFD74735, alpha);
        g.pose().pushMatrix();
        g.pose().translate(w - 52f, h - 46f);
        g.pose().rotate((float) Math.toRadians(-9));
        g.pose().scale(pop * 0.72f, pop * 0.72f);
        int bw = 62, bh = 42;
        g.fill(-bw, -bh / 2, bw, -bh / 2 + 2, red);
        g.fill(-bw, bh / 2 - 2, bw, bh / 2, red);
        g.fill(-bw, -bh / 2, -bw + 2, bh / 2, red);
        g.fill(bw - 2, -bh / 2, bw, bh / 2, red);
        String a = "已记录", b = stars(stamp[0]), c = "当前满意度 " + stamp[1] + "%";
        g.drawString(font, a, -font.width(a) / 2, -17, red, false);
        g.drawString(font, b, -font.width(b) / 2, -5, red, false);
        g.drawString(font, c, -font.width(c) / 2, 7, red, false);
        g.pose().popMatrix();
    }

    // ---------- 开场：片名之后是情境提要 ----------

    /** 前面的入场片已经把来龙去脉讲了，这里只交代眼下：在哪、等什么、第一步做什么。 */
    private static final String[] BRIEF = {
            "公路边的旧接驳站。",
            "接驳车今晚来接你进园。",
    };
    static final float BRIEF_SECONDS = 7;

    /** 情境提要：黑底上一段打字机的字，交代你是谁、来干什么，最后一行告诉你第一步做什么。 */
    private static String briefDate = "", briefHead = "", briefHint = "";
    private static String[] briefLines = BRIEF;
    private static float briefSeconds = BRIEF_SECONDS;

    private static void drawIntro(GuiGraphics g, Font font, int w, int h) {
        float t = (now() - introAt) / 1000f;
        if (t > briefSeconds) {
            introAt = -1;
            return;
        }
        float alpha = t > briefSeconds - 1 ? briefSeconds - t : Math.min(1, t / 0.4f);
        int width = font.width(briefHead) * 2 + 10;
        for (String line : briefLines) width = Math.max(width, font.width(line));
        int rows = briefLines.length + 5, x0 = (w - width) / 2, y = (h - rows * 12) / 2;
        g.drawString(font, briefDate, x0, y, alphaOf(0xFF9A928A, alpha), false);
        y += 14;
        g.fill(x0, y + 3, x0 + 6, y + 9, alphaOf(RED, alpha));
        big(g, font, briefHead, x0 + 10, y, 2, alphaOf(PAPER, alpha), false);
        y += 26;
        // 一行一行打出来，每秒 14 个字
        int budget = (int) (Math.max(0, t - 0.8f) * 14);
        for (String line : briefLines) {
            int count = Math.min(line.length(), budget);
            if (count > 0) g.drawString(font, line.substring(0, count), x0, y, alphaOf(0xFFE8E0D2, alpha), false);
            budget -= line.length();
            y += 12;
            if (budget <= 0) break;
        }
        if (budget > 2) {
            float blink = 0.6f + 0.4f * (float) Math.sin(t * 4);
            g.drawString(font, briefHint, x0, y + 10, alphaOf(RED, alpha * blink), false);
        }
    }

    private static void drawTitle(GuiGraphics g, Font font, int w, int h, Minecraft mc) {
        float ticks = (now() - titleAt) / 50f;
        if (ticks > 80) {
            titleAt = -1;
            return;
        }
        float leave = ticks > 70 ? (ticks - 70) / 10 : 0, alpha = 1 - leave;
        tint(g, w, h, 0, 0.55f * alpha);
        int rise = (int) (leave * leave * 24);
        float k = Math.min(1, ticks / 20);
        g.pose().pushMatrix();
        g.pose().translate(w / 2f - 180 * 0.22f, h / 2f - 180 * 0.22f - 26 - rise);
        g.pose().scale(0.44f, 0.44f);
        icon(g, k, alpha);
        g.pose().popMatrix();
        // 每 3 tick 盖一个字，一声比一声高
        int want = Math.min(TITLE.length(), Math.max(0, (int) ((ticks - 24) / 3) + 1));
        for (; stamped < want; stamped++) {
            mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_BASEDRUM.value(), 0.6f + stamped * 0.14f, 0.9f));
        }
        int scale = 4, gap = font.width("血") * scale + 4, total = gap * TITLE.length(), tx = (w - total) / 2 + 2, ty = h / 2 + 22 - rise;
        for (int i = 0; i < want; i++) {
            float age = ticks - 24 - i * 3;
            float s = age < 3 ? scale + (3 - age) * 0.5f : scale;
            String c = TITLE.substring(i, i + 1);
            g.pose().pushMatrix();
            g.pose().translate(tx + i * gap + font.width(c) * scale / 2f, ty + font.lineHeight * scale / 2f);
            g.pose().scale(s, s);
            g.drawString(font, c, -font.width(c) / 2, -font.lineHeight / 2, alphaOf(0xFFFFFBF5, alpha), false);
            g.pose().popMatrix();
        }
        if (ticks > 46) {
            int line = (int) (Math.min(1, (ticks - 46) / 8) * total / 2);
            g.fill(w / 2 - line, ty + font.lineHeight * scale + 6, w / 2 + line, ty + font.lineHeight * scale + 8, alphaOf(RED, alpha));
        }
    }

    /**
     * 赫朝官方图标（creator-context/brand/hechao-icon.svg，180×180）：红外框、暖纸内底、墨块、四角红块、中心镂空。
     * 动画是方格收束：外框沿周长画出来，内底和墨块从中心长开，四角红块从外面收进来，最后镂空。
     */
    private static void icon(GuiGraphics g, float t, float alpha) {
        int red = alphaOf(RED, alpha), paper = alphaOf(PAPER, alpha), ink = alphaOf(INK, alpha);
        if (t >= 0.0f) frame(g, (int) (Math.min(1, t / 0.3f) * 656), red);
        if (t >= 0.2f) {
            int half = (int) (Math.min(1, (t - 0.2f) / 0.18f) * 72);
            g.fill(90 - half, 90 - half, 90 + half, 90 + half, paper);
        }
        if (t >= 0.34f) {
            int half = (int) (Math.min(1, (t - 0.34f) / 0.2f) * 54);
            g.fill(90 - half, 90 - half, 90 + half, 90 + half, ink);
        }
        if (t >= 0.5f) {
            float k = Math.min(1, (t - 0.5f) / 0.28f);
            int off = (int) (60 * (1 - k) * (1 - k) * (1 - k));
            g.fill(32 - off, 32 - off, 68 - off, 68 - off, red);
            g.fill(112 + off, 32 - off, 148 + off, 68 - off, red);
            g.fill(32 - off, 112 + off, 68 - off, 148 + off, red);
            g.fill(112 + off, 112 + off, 148 + off, 148 + off, red);
        }
        if (t >= 0.82f) {
            int half = (int) (Math.min(1, (t - 0.82f) / 0.18f) * 14);
            g.fill(90 - half, 90 - half, 90 + half, 90 + half, paper);
        }
    }

    /** 红外框沿周长从左上顺时针画。 */
    private static void frame(GuiGraphics g, int len, int color) {
        int a = Math.min(len, 164);
        g.fill(8, 8, 8 + a, 18, color);
        if (len > 164) g.fill(162, 8, 172, 8 + Math.min(len - 164, 164), color);
        if (len > 328) g.fill(172 - Math.min(len - 328, 164), 162, 172, 172, color);
        if (len > 492) g.fill(8, 172 - Math.min(len - 492, 164), 18, 172, color);
    }

    // ---------- 体验报告：分数一行行打出来 ----------

    private static void drawReport(GuiGraphics g, Font font, int w, int h) {
        float t = (now() - reportAt) / 1000f;
        boolean dim = report.length > 0 && report[report.length - 1].equals("dim");
        boolean fix = report.length > 8 && report[8].equals("fix");
        if (dim && t > 15) {
            reportAt = -1;
            return;
        }
        tint(g, w, h, 0, dim ? 0.55f : 1);
        int pw = Math.min(w - 40, 280), ph = Math.min(h - 24, 214), x = (w - pw) / 2, y = (h - ph) / 2;
        paper(g, x, y, pw, ph);
        big(g, font, "体验报告", x + 18, y + 14, 2, INK, false);
        if (report.length > 0) g.drawString(font, report[0], x + pw - 18 - font.width(report[0]), y + 22, INK_SOFT, false);
        int ty = y + 44;
        for (int i = 0; i < ROWS.length; i++) {
            if (t < 0.8f + i * 0.9f) break;
            g.drawString(font, ROWS[i], x + 18, ty, INK, false);
            String score = i + 1 < report.length ? report[i + 1] : "—";
            // 先显示您打的分，停一下，闪一闪，变成满分
            float changeAt = 3.2f + i * 0.55f;
            if (fix && i + 9 < report.length && t < changeAt) {
                score = report[i + 9];
            }
            int row = fix && t >= changeAt - 0.18f && t < changeAt + 0.1f ? (RND.nextBoolean() ? INK : RED) : RED;
            g.drawString(font, stars(score), x + pw - 18 - font.width(stars(score)), ty, row, false);
            g.fill(x + 18, ty + 11, x + pw - 18, ty + 12, PAPER_LINE);
            ty += 16;
        }
        if (t > (fix ? 6.9f : 6.6f) && report.length > 7) {
            g.drawString(font, "总分", x + 18, ty + 2, INK, false);
            big(g, font, report[7], x + pw - 18 - font.width(report[7]) * 2, ty - 2, 2, RED, false);
        }
        if (fix && t > 7.2f) g.drawString(font, "您的评价已按您的真实感受修正。", x + 18, y + ph - 48, RED, false);
        if (t > 8.2f) g.drawString(font, "结论：建议重新开放。", x + 18, y + ph - 34, INK, false);
        if (t > 9.4f) g.drawString(font, "评估人：体验官", x + 18, y + ph - 20, INK, false);
    }

    private static String stars(String score) {
        try {
            int n = Integer.parseInt(score);
            return "★".repeat(n) + "☆".repeat(5 - n);
        } catch (NumberFormatException notRated) {
            return "未评价";
        }
    }

    // ---------- 片尾 ----------

    private static void drawEnd(GuiGraphics g, Font font, int w, int h) {
        float t = (now() - endAt) / 1000f;
        if (t > 19f) {
            endAt = -1;
            return;
        }
        tint(g, w, h, 0, 1);
        // 打分那一屏放到 10.5 秒，一秒暗下去，黑一会儿，出"未完待续"
        if (t > 11.5f) {
            float in = Math.min(1, (t - 12.2f) / 0.6f), out = t > 17.5f ? (19f - t) / 1.5f : 1;
            if (in * out < 0.03f) return;
            String more = "未完待续";
            big(g, font, more, (w - font.width(more) * 3) / 2, h / 2 - 14, 3, alphaOf(PAPER, in * out), false);
            return;
        }
        String ask = "您对本视频满意吗？";
        big(g, font, ask, (w - font.width(ask) * 2) / 2, h / 2 - 62, 2, PAPER, false);
        int size = 30, gap = 8, x0 = (w - size * 5 - gap * 4) / 2, y0 = h / 2 - 28;
        for (int i = 0; i < 5; i++) {
            if (t < 0.5f + i * 0.25f) break;
            int x = x0 + i * (size + gap);
            g.fill(x + 3, y0 + 3, x + size + 3, y0 + size + 3, 0x5524211F);
            g.fill(x, y0, x + size, y0 + size, RED);
            g.fill(x + 3, y0 + 3, x + size - 3, y0 + size - 3, PAPER);
            String n = String.valueOf(i + 1);
            big(g, font, n, x + (size - font.width(n) * 2) / 2 + 1, y0 + 8, 2, INK, false);
        }
        if (t > 2.2f) {
            String a = "请在评论区打分。";
            g.drawString(font, a, (w - font.width(a)) / 2, y0 + size + 14, PAPER, false);
        }
        if (t > 3.4f) {
            String b = "我们会改进。";
            g.drawString(font, b, (w - font.width(b)) / 2, y0 + size + 28, RED, false);
        }
        if (t > 5.5f) {
            String c = "改编自 Trevor Roberts 的虚构作品 Mystery Flesh Pit National Park，纯属虚构。";
            g.drawString(font, c, (w - font.width(c)) / 2, h - 18, 0xFF7A736C, false);
        }
        if (t > 10.5f) tint(g, w, h, 0, Math.min(1, t - 10.5f));
    }

    // ---------- 零件 ----------

    private static void paper(GuiGraphics g, int x, int y, int pw, int ph) {
        g.fill(x + 4, y + 4, x + pw + 4, y + ph + 4, 0x5524211F);
        g.fill(x, y, x + pw, y + ph, RED);
        g.fill(x + 3, y + 3, x + pw - 3, y + ph - 3, PAPER);
    }

    private static void big(GuiGraphics g, Font font, String text, int x, int y, int scale, int color, boolean shadow) {
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(scale, scale);
        g.drawString(font, text, 0, 0, color, shadow);
        g.pose().popMatrix();
    }

    private static int alphaOf(int argb, float alpha) {
        return (int) (Math.max(0, Math.min(1, alpha)) * (argb >>> 24)) << 24 | argb & 0xFFFFFF;
    }
}
