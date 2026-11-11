package world.hechao.fleshpark.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * 入场片和收尾里出现的文件：一张报纸、一则招聘、一份名单、一张工牌、一份 2007 年的内部通知、一块工牌板。
 * 原点在屏幕正中，单位是界面像素。都是纸，暖白底、墨字，红色只给标题条和印章。
 */
final class Docs {
    private static final int PAPER = 0xFFEDE6D6, PAPER_DARK = 0xFFD9CFBC, INK = 0xFF24211F, SOFT = 0xFF6B645E, RED = 0xFFA82A1C, RULE = 0xFF8A7F72;

    private Docs() {
    }

    static void draw(GuiGraphics g, Font font, String id) {
        switch (id) {
            case "news" -> news(g, font);
            case "hire" -> hire(g, font);
            case "roster" -> roster(g, font);
            case "badge" -> badge(g, font, "#0011", true);
            case "memo" -> memo(g, font);
            case "board" -> board(g, font);
            default -> {
            }
        }
    }

    private static void card(GuiGraphics g, int w, int h, int color) {
        g.fill(-w / 2 + 4, -h / 2 + 4, w / 2 + 4, h / 2 + 4, 0x66000000);
        g.fill(-w / 2, -h / 2, w / 2, h / 2, color);
    }

    private static void center(GuiGraphics g, Font font, String s, int y, int color, float scale) {
        g.pose().pushMatrix();
        g.pose().translate(0, y);
        g.pose().scale(scale, scale);
        g.drawString(font, s, -font.width(s) / 2, 0, color, false);
        g.pose().popMatrix();
    }

    private static void left(GuiGraphics g, Font font, String s, int x, int y, int color, float scale) {
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(scale, scale);
        g.drawString(font, s, 0, 0, color, false);
        g.pose().popMatrix();
    }

    /** 2007 年 7 月 5 日的报纸头版。 */
    private static void news(GuiGraphics g, Font font) {
        int w = 320, h = 200, x0 = -w / 2 + 12, y0 = -h / 2;
        card(g, w, h, PAPER);
        center(g, font, "二 叠 纪 日 报", y0 + 8, INK, 2f);
        g.fill(x0, y0 + 29, -x0, y0 + 30, INK);
        left(g, font, "2007 年 7 月 5 日  星期四", x0, y0 + 33, SOFT, 0.75f);
        left(g, font, "第 12,841 期", -x0 - 45, y0 + 33, SOFT, 0.75f);
        g.fill(x0, y0 + 42, -x0, y0 + 43, INK);
        center(g, font, "息壤下游客中心垮塌", y0 + 50, INK, 2f);
        center(g, font, "独立日烟花晚会前夕  ·  遇难者超过七百五十人", y0 + 72, RED, 1f);
        // 左边一张网点照片：黑乎乎的中庭
        int px = x0, py = y0 + 88, pw = 118, ph = 78;
        g.fill(px, py, px + pw, py + ph, 0xFF2A2724);
        for (int yy = 0; yy < ph; yy += 3) {
            for (int xx = (yy / 3) % 2; xx < pw; xx += 3) {
                double light = Math.max(0, 1 - Math.hypot(xx - pw * 0.55, (yy - ph * 0.4) * 1.6) / 60);
                if (light * 1.2 > ((xx * 7 + yy * 13) % 10) / 10.0) g.fill(px + xx, py + yy, px + xx + 2, py + yy + 2, 0xFF8C8478);
            }
        }
        left(g, font, "资料图：下游客中心中庭", px, py + ph + 3, SOFT, 0.7f);
        String[] body = {
                "本报讯  7 月 4 日 21 时 41 分，",
                "息壤国家公园下游客中心开始进水。",
                "23 时 05 分，整座建筑连同六根",
                "支撑一起坠入腔内。",
                "当晚中庭原定举行独立日烟花晚会，",
                "末班缆车 20 时 30 分发车后，",
                "地面与地下的联系全部中断。",
                "安诺丹公司至截稿时未予回应。"};
        int by = py;
        for (String line : body) {
            left(g, font, line, px + pw + 10, by, INK, 0.75f);
            by += 10;
        }
    }

    /** 回收公司的招聘启事。 */
    private static void hire(GuiGraphics g, Font font) {
        int w = 230, h = 200, x0 = -w / 2 + 14, y0 = -h / 2;
        card(g, w, h, PAPER);
        g.fill(-w / 2, y0, w / 2, y0 + 30, RED);
        center(g, font, "诚 聘  ·  体 验 官", y0 + 8, 0xFFF4ECD8, 1.6f);
        center(g, font, "二叠纪盆地回收与超级生物体收容公司", y0 + 38, INK, 0.85f);
        g.fill(x0, y0 + 50, -x0, y0 + 51, RULE);
        String[] lines = {
                "·  走完息壤国家公园规定游览路线",
                "·  为每一站的游客体验打分",
                "·  全程约四小时，接驳车接送",
                "·  日薪 800 美元，交回评分表后结算",
                "",
                "公园已关闭十九年，现评估重新开放。",
                "无需经验。身体健康，不怕黑。"};
        int y = y0 + 58;
        for (String line : lines) {
            left(g, font, line, x0, y, INK, 0.9f);
            y += 12;
        }
        left(g, font, "本公司不对园区内的任何体验负责。", x0, y0 + h - 16, SOFT, 0.7f);
        // 红章：急招
        g.pose().pushMatrix();
        g.pose().translate(w / 2f - 40, y0 + h - 52);
        g.pose().rotate((float) Math.toRadians(-14));
        g.fill(-22, -11, 22, 11, 0x00000000);
        g.fill(-22, -11, 22, -10, RED);
        g.fill(-22, 10, 22, 11, RED);
        g.fill(-22, -11, -21, 11, RED);
        g.fill(21, -11, 22, 11, RED);
        center(g, font, "急 招", -5, RED, 1.1f);
        g.pose().popMatrix();
    }

    /** 体验官名单：前十个都没交回评分表。 */
    private static void roster(GuiGraphics g, Font font) {
        int w = 320, h = 196, x0 = -w / 2 + 12, y0 = -h / 2;
        card(g, w, h, PAPER);
        center(g, font, "体验官名单  ·  息壤国家公园重开评估", y0 + 8, INK, 1.1f);
        g.fill(x0, y0 + 22, -x0, y0 + 23, INK);
        for (int i = 0; i < 11; i++) {
            int col = i / 13, row = i % 13;
            int x = x0 + col * 100, y = y0 + 30 + row * 12;
            boolean me = i == 10;
            String id = String.format("#%04d", i + 1);
            left(g, font, id, x, y, me ? RED : INK, 0.8f);
            left(g, font, me ? "今晚出发" : "评分表  未交回", x + 32, y, me ? RED : SOFT, 0.8f);
            if (!me) g.fill(x + 31, y + 3, x + 88, y + 4, 0x66A82A1C);
        }
    }

    /** 一张体验官工牌。 */
    private static void badge(GuiGraphics g, Font font, String number, boolean big) {
        int w = big ? 120 : 34, h = big ? 168 : 48;
        float k = big ? 1 : 0.28f;
        g.pose().pushMatrix();
        g.pose().scale(k, k);
        int bw = 120, bh = 168;
        // 挂绳
        g.fill(-2, -bh / 2 - 40, 2, -bh / 2 + 4, 0xFF5A5450);
        card(g, bw, bh, 0xFFFFFBF5);
        g.fill(-bw / 2, -bh / 2, bw / 2, -bh / 2 + 32, 0xFFD74735);
        center(g, font, "体验官", -bh / 2 + 9, 0xFFFFFBF5, 1.6f);
        g.fill(-30, -bh / 2 + 44, 30, -bh / 2 + 112, 0xFF8A8480);
        g.fill(-14, -bh / 2 + 56, 14, -bh / 2 + 84, 0xFF5E5854);
        g.fill(-22, -bh / 2 + 88, 22, -bh / 2 + 112, 0xFF5E5854);
        center(g, font, number, -bh / 2 + 122, 0xFFD74735, 2f);
        center(g, font, "二叠纪盆地回收", -bh / 2 + 148, SOFT, 0.75f);
        g.pose().popMatrix();
    }

    /** 2007 年 6 月的内部通知：满意度终端接进了“改进系统”。 */
    private static void memo(GuiGraphics g, Font font) {
        int w = 260, h = 170, x0 = -w / 2 + 14, y0 = -h / 2;
        card(g, w, h, PAPER);
        left(g, font, "安诺丹公园管理处  ·  内部通知", x0, y0 + 10, INK, 1f);
        left(g, font, "2007.06.12", -x0 - 48, y0 + 10, SOFT, 0.8f);
        g.fill(x0, y0 + 24, -x0, y0 + 25, INK);
        String[] lines = {
                "关于试运行游客满意度终端的通知：",
                "即日起，各站点安装满意度终端。",
                "游客评分直接接入园区改进系统，",
                "用于实时优化游览体验。",
                "终端数据线一律沿墙体走线。",
                "如发现墙体包裹线缆，无需清理。"};
        int y = y0 + 34;
        for (String line : lines) {
            left(g, font, line, x0, y, INK, 0.9f);
            y += 13;
        }
        // 红色的“内部”章
        g.pose().pushMatrix();
        g.pose().translate(w / 2f - 42, y0 + h - 34);
        g.pose().rotate((float) Math.toRadians(-10));
        center(g, font, "内 部", -6, RED, 1.4f);
        g.fill(-26, -10, 26, -9, RED);
        g.fill(-26, 10, 26, 11, RED);
        g.pose().popMatrix();
    }

    /** 工牌板：十一张挂满，第十二格空着。 */
    private static void board(GuiGraphics g, Font font) {
        int w = 330, h = 210, y0 = -h / 2;
        card(g, w, h, 0xFF2E2A26);
        center(g, font, "体验官  ·  已到岗", y0 + 8, 0xFFF2EEE4, 1.1f);
        for (int i = 0; i < 12; i++) {
            int col = i % 13, row = i / 13;
            int x = -w / 2 + 18 + col * 24, y = y0 + 34 + row * 50;
            if (i == 11) {
                // 空着的那一格
                g.fill(x - 9, y, x + 9, y + 1, 0xFF8A7F72);
                g.fill(x - 9, y + 38, x + 9, y + 39, 0xFF8A7F72);
                g.fill(x - 9, y, x - 8, y + 39, 0xFF8A7F72);
                g.fill(x + 8, y, x + 9, y + 39, 0xFF8A7F72);
                left(g, font, "#0012", x - 9, y + 42, 0xFFD74735, 0.55f);
                continue;
            }
            g.fill(x - 9, y, x + 9, y + 38, i == 10 ? 0xFFFFFBF5 : 0xFFB8B0A4);
            g.fill(x - 9, y, x + 9, y + 7, i == 10 ? 0xFFD74735 : 0xFF8A4A40);
            g.fill(x - 5, y + 11, x + 5, y + 25, 0xFF6E6862);
            left(g, font, String.format("#%04d", i + 1), x - 9, y + 42, i == 10 ? 0xFFF2EEE4 : 0xFF8A8478, 0.55f);
        }
        center(g, font, "下一位体验官  二十分钟后到达", y0 + h - 14, 0xFFD74735, 0.9f);
    }
}
