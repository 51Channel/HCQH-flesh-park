package world.hechao.fleshpark.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * 导演栏：按「·」（波浪号键）打开。赫朝视觉语言的场主台：暖纸面板、墨字、直角大方砖、墨色硬阴影，
 * 红色只给主操作和按下。跳段、解锁本站、测惊吓、开关录像带角标；“回到开局前”要按住。
 * 指令都由服务端的 /fp 执行，要有管理员权限。
 */
final class DirectorScreen extends Screen {
    private static final int RED = 0xFFD74735, PAPER = 0xFFFFFBF5, INK = 0xFF24211F, INK_SOFT = 0xFF6B645E;
    private static final int SURFACE = 0xFFF3ECE1, HOVER = 0xFFE9DFD0, LINE = 0xFFDCD2C3, PRESSED = 0xFFB83A2A;
    private static final long HOLD_MS = 1200;
    private static KeyMapping key;

    private record Tile(String label, String note, String command, boolean main, boolean hold, Runnable local) {
    }

    private record Row(String title, List<Tile> tiles) {
    }

    private final List<Row> rows = new ArrayList<>();
    private final List<int[]> hits = new ArrayList<>();
    private final List<Tile> hitTiles = new ArrayList<>();
    private Tile holding;
    private long holdSince;
    private String status = "";
    private long statusAt;

    DirectorScreen() {
        super(Component.literal("导演栏"));
        rows.add(new Row("现场", List.of(
                new Tile("跳过开场", "直接到站台", "fp skip", false, false, null),
                new Tile("解锁本站", "纸条全部算读过", "fp unlock", true, false, null),
                new Tile("测一次惊吓", "人形贴到面前", "fp scare", false, false, null),
                new Tile("录像带角标", "开 / 关", null, false, false, Overlay::toggleTape),
                // 这一轮按意见改了哪些地方，一条一条能跳过去看
                new Tile("本轮修改", ChangesScreen.count() + " 处 · 去看", null, true, false,
                        () -> Minecraft.getInstance().setScreen(new ChangesScreen(this))))));
        // 一排一关，从左往右、从上往下就是玩的顺序。只有一个结尾，在最后一格
        rows.add(new Row("序幕 · 第一关  游客中心", List.of(
                new Tile("入园大厅", "闸机和桌子", "fp tp entry", false, false, null),
                new Tile("地面车站", "站台", "fp tp station", false, false, null),
                new Tile("坐缆车", "从站台发车", "fp go", false, false, null),
                new Tile("3 号码头", "下车的地方", "fp tp dock", false, false, null),
                new Tile("大厅", "游客中心", "fp tp hall", false, false, null),
                new Tile("观景窗", "人形的窗", "fp tp window", false, false, null))));
        rows.add(new Row("第二关  温泉 · 第三关  森林", List.of(
                new Tile("花园层", "楼梯底下", "fp tp garden", false, false, null),
                new Tile("更衣室", "B19 号柜", "fp tp lockers", false, false, null),
                new Tile("温泉", "一号池", "fp tp pool1", false, false, null),
                new Tile("森林", "护林站", "fp tp forest", false, false, null),
                new Tile("护林员车", "值班日志", "fp tp vehicle", false, false, null),
                new Tile("森林电梯", "掉进井里", "fp tp cab", false, false, null))));
        rows.add(new Row("第四关  深井", List.of(
                new Tile("电梯卡住", "门撬开一半", "fp tp well", false, false, null),
                new Tile("员工平台", "抬头看", "fp tp rim", false, false, null),
                new Tile("4 号支撑", "断楼梯", "fp tp truss", false, false, null),
                new Tile("肉块", "对面的头灯", "fp tp rope", false, false, null),
                new Tile("807 号车", "冷光棒 · 滑下去", "fp tp car", false, false, null))));
        rows.add(new Row("第五关  井底的游客中心 · 结尾", List.of(
                new Tile("3 号码头", "下车的地方", "fp tp dock2", false, false, null),
                new Tile("大厅", "原件", "fp tp hall2", false, false, null),
                new Tile("观景窗", "手印", "fp tp window2", false, false, null),
                new Tile("花园层", "三张原件", "fp tp garden2", false, false, null),
                new Tile("收尾", "发疯 · 片尾", "fp tp ending", false, false, null))));
        rows.add(new Row("", List.of(new Tile("回到开局前", "按住 1 秒，整局重来", "fp reset", true, true, null))));
    }

    static void register() {
        key = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.fleshpark.director", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_GRAVE_ACCENT, Phone.CATEGORY));
    }

    static void tick(Minecraft mc) {
        if (key == null) return;
        while (key.consumeClick()) {
            if (mc.screen == null && mc.player != null) mc.setScreen(new DirectorScreen());
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // 不糊背景：要看得见现场
        g.fill(0, 0, width, height, 0x5524211F);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        // 方砖按屏高定，但整块面板一定要放得下。2560×1440 自动界面缩放只有 240 高：
        // 两行字的方砖放不下七排，就收紧成只写大字的一行方砖，最后一排（结尾、回到开局前）不能掉到屏幕外面
        int n0 = rows.size();
        boolean compact = 30 + 10 + n0 * (24 + 12 + 6) + 18 > height - 12;
        int titleH = compact ? 20 : 30, gap = compact ? 3 : 6, sectionH = compact ? 10 : 12, top = compact ? 4 : 8, foot = compact ? 13 : 18;
        int fit = (height - 12 - titleH - top - 2 - foot) / n0 - sectionH - gap;
        int tileH = compact ? Math.max(11, Math.min(16, fit)) : Math.max(24, Math.min(Math.min(52, Math.max(34, (height - 140) / 4)), fit));
        int pw = Math.min(width - 24, 460), x0 = (width - pw) / 2;
        int ph = titleH + top + 2 + n0 * (tileH + sectionH + gap) + foot;
        int y0 = Math.max(4, (height - ph) / 2);
        // 硬阴影、红框、暖纸
        g.fill(x0 + 5, y0 + 5, x0 + pw + 5, y0 + ph + 5, INK);
        g.fill(x0, y0, x0 + pw, y0 + ph, RED);
        g.fill(x0 + 3, y0 + 3, x0 + pw - 3, y0 + ph - 3, PAPER);
        int ty = y0 + (titleH - 8) / 2;
        if (!compact) icon(g, x0 + 10, y0 + 6, 1);
        g.drawString(font, "血肉公园 · 导演栏", x0 + (compact ? 12 : 34), ty, INK, false);
        String hint = "· 键关闭";
        g.drawString(font, hint, x0 + pw - 12 - font.width(hint), ty, INK_SOFT, false);
        g.fill(x0 + 10, y0 + titleH, x0 + pw - 10, y0 + titleH + 1, LINE);
        hits.clear();
        hitTiles.clear();
        int y = y0 + titleH + top;
        long now = System.currentTimeMillis();
        for (Row row : rows) {
            if (!row.title().isEmpty()) g.drawString(font, row.title(), x0 + 12, y, INK_SOFT, false);
            y += sectionH;
            int n = row.tiles().size(), inner = pw - 24, tw = (inner - (n - 1) * gap) / n;
            for (int i = 0; i < n; i++) {
                Tile t = row.tiles().get(i);
                int tx = x0 + 12 + i * (tw + gap);
                boolean over = mouseX >= tx && mouseX < tx + tw && mouseY >= y && mouseY < y + tileH;
                boolean held = t == holding;
                int face = held ? PRESSED : t.main() ? RED : over ? HOVER : SURFACE;
                g.fill(tx + 2, y + 2, tx + tw + 2, y + tileH + 2, INK);
                g.fill(tx, y, tx + tw, y + tileH, face);
                if (held) {
                    // 按住的时候整块从下往上涨满，满了才执行
                    float k = Math.min(1, (now - holdSince) / (float) HOLD_MS);
                    g.fill(tx, y + tileH - Math.round(tileH * k), tx + tw, y + tileH, INK);
                    if (k >= 1) {
                        run(t);
                        holding = null;
                    }
                }
                int text = t.main() || held ? PAPER : INK, soft = t.main() || held ? 0xFFF7E4DF : INK_SOFT;
                int cy = y + (tileH - (compact ? 8 : 20)) / 2;
                g.drawString(font, fit(t.label(), tw - 8), tx + (tw - font.width(fit(t.label(), tw - 8))) / 2, cy, text, false);
                if (!compact) g.drawString(font, fit(t.note(), tw - 8), tx + (tw - font.width(fit(t.note(), tw - 8))) / 2, cy + 11, soft, false);
                hits.add(new int[]{tx, y, tw, tileH});
                hitTiles.add(t);
            }
            y += tileH + gap;
        }
        String line = now - statusAt < 4000 ? status : "指令由服务端 /fp 执行，需要管理员权限。";
        g.drawString(font, fit(line, pw - 24), x0 + 12, y0 + ph - foot + (compact ? 1 : 4), now - statusAt < 4000 ? RED : INK_SOFT, false);
    }

    private String fit(String s, int width) {
        if (font.width(s) <= width) return s;
        return font.plainSubstrByWidth(s, width);
    }

    /** 赫朝图标的 18 像素版，按整数倍放大。 */
    private static void icon(GuiGraphics g, int x, int y, int scale) {
        int s = scale;
        g.fill(x, y, x + 18 * s, y + 18 * s, RED);
        g.fill(x + 1 * s, y + 1 * s, x + 17 * s, y + 17 * s, PAPER);
        g.fill(x + 4 * s, y + 4 * s, x + 14 * s, y + 14 * s, INK);
        for (int[] c : new int[][]{{3, 3}, {11, 3}, {3, 11}, {11, 11}}) g.fill(x + c[0] * s, y + c[1] * s, x + (c[0] + 4) * s, y + (c[1] + 4) * s, RED);
        g.fill(x + 8 * s, y + 8 * s, x + 10 * s, y + 10 * s, PAPER);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        Tile t = at(event.x(), event.y());
        if (t == null || event.button() != 0) return super.mouseClicked(event, doubleClick);
        if (t.hold()) {
            holding = t;
            holdSince = System.currentTimeMillis();
        } else {
            run(t);
        }
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        holding = null;
        return super.mouseReleased(event);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (key != null && key.matches(event)) {
            onClose();
            return true;
        }
        return super.keyPressed(event);
    }

    private Tile at(double mx, double my) {
        for (int i = 0; i < hits.size(); i++) {
            int[] r = hits.get(i);
            if (mx >= r[0] && mx < r[0] + r[2] && my >= r[1] && my < r[1] + r[3]) return hitTiles.get(i);
        }
        return null;
    }

    private void run(Tile t) {
        Minecraft mc = Minecraft.getInstance();
        if (t.local() != null) {
            note("已切换：" + t.label());
            t.local().run();
            return;
        }
        if (mc.player == null) return;
        mc.player.connection.sendCommand(t.command());
        note("已发送：/" + t.command());
        // 跳段以后直接回到画面
        if (!t.command().equals("fp unlock") && !t.command().equals("fp scare")) onClose();
    }

    private void note(String text) {
        status = text;
        statusAt = System.currentTimeMillis();
    }
}
