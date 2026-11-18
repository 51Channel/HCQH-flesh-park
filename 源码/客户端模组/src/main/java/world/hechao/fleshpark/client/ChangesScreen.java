package world.hechao.fleshpark.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 导演栏里的「本轮修改」：这一轮按用户提的意见改了哪些地方，每条写原来怎样、现在怎样，
 * 右边「去看」一按就传到那个地方（或者从头演一遍那一段）。内容在 assets/fleshpark/changes.txt，跟着每次修改一起写。
 */
final class ChangesScreen extends Screen {
    private static final int RED = 0xFFD74735, PAPER = 0xFFFFFBF5, INK = 0xFF24211F, INK_SOFT = 0xFF6B645E;
    private static final int SURFACE = 0xFFF3ECE1, HOVER = 0xFFE9DFD0, LINE = 0xFFDCD2C3;

    /** 一条修改：编号、标题、去看要发的指令（多条用 ; 隔开）、补拍说明、正文几行。 */
    record Change(int no, String title, String go, String reshoot, List<String> body) {
    }

    private final Screen back;
    private final List<Change> changes = load();
    private final List<int[]> hits = new ArrayList<>();
    private final List<Runnable> actions = new ArrayList<>();
    private double scroll;
    private int contentH;
    private String status = "";
    private long statusAt;

    ChangesScreen(Screen back) {
        super(Component.literal("本轮修改"));
        this.back = back;
    }

    static List<Change> load() {
        List<Change> out = new ArrayList<>();
        try (InputStream in = ChangesScreen.class.getResourceAsStream("/assets/fleshpark/changes.txt")) {
            if (in == null) return out;
            String title = null, go = "", reshoot = "";
            List<String> body = new ArrayList<>();
            for (String raw : (new String(in.readAllBytes(), StandardCharsets.UTF_8) + "\n\n").split("\n")) {
                String line = raw.strip();
                if (line.startsWith("#")) continue;
                if (line.isEmpty()) {
                    if (title != null) out.add(new Change(out.size() + 1, title, go, reshoot, body));
                    title = null;
                    body = new ArrayList<>();
                    continue;
                }
                if (title == null) {
                    String[] f = line.split("\\|", -1);
                    title = f[0].strip();
                    go = f.length > 1 ? f[1].strip() : "";
                    reshoot = f.length > 2 ? f[2].strip() : "";
                } else {
                    body.add(line);
                }
            }
        } catch (Exception e) {
            out.add(new Change(1, "清单读不到：" + e, "", "", List.of()));
        }
        return out;
    }

    static int count() {
        return load().size();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, width, height, 0x7724211F);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        int pw = Math.min(width - 24, 520), x0 = (width - pw) / 2;
        int y0 = 8, ph = height - 16, head = 26, foot = 16;
        g.fill(x0 + 5, y0 + 5, x0 + pw + 5, y0 + ph + 5, INK);
        g.fill(x0, y0, x0 + pw, y0 + ph, RED);
        g.fill(x0 + 3, y0 + 3, x0 + pw - 3, y0 + ph - 3, PAPER);
        hits.clear();
        actions.clear();

        // 标题栏：左边返回，中间标题
        String backLabel = "← 导演栏";
        int bw = font.width(backLabel) + 12;
        boolean overBack = mouseX >= x0 + 8 && mouseX < x0 + 8 + bw && mouseY >= y0 + 6 && mouseY < y0 + 20;
        g.fill(x0 + 8, y0 + 6, x0 + 8 + bw, y0 + 20, overBack ? HOVER : SURFACE);
        g.drawString(font, backLabel, x0 + 14, y0 + 9, INK, false);
        hits.add(new int[]{x0 + 8, y0 + 6, bw, 14});
        actions.add(this::onClose);
        String title = "本轮修改 · " + changes.size() + " 处";
        g.drawString(font, title, x0 + (pw - font.width(title)) / 2, y0 + 9, INK, false);
        g.fill(x0 + 10, y0 + head - 2, x0 + pw - 10, y0 + head - 1, LINE);

        // 正文：一条一张卡片，能滚
        int top = y0 + head, bottom = y0 + ph - foot, inner = pw - 24, btnW = 46;
        g.enableScissor(x0 + 4, top, x0 + pw - 4, bottom);
        int y = top + 6 - (int) scroll;
        for (Change c : changes) {
            int textW = inner - btnW - 16;
            List<FormattedCharSequence> lines = new ArrayList<>();
            for (String b : c.body()) lines.addAll(font.split(FormattedText.of(b), textW));
            if (!c.reshoot().isEmpty()) lines.addAll(font.split(FormattedText.of("※ " + c.reshoot()), textW));
            int cardH = 8 + 11 + 3 + lines.size() * 10 + 6;
            int cx = x0 + 12;
            g.fill(cx + 2, y + 2, cx + inner + 2, y + cardH + 2, INK);
            g.fill(cx, y, cx + inner, y + cardH, SURFACE);
            g.drawString(font, c.no() + ".  " + c.title(), cx + 8, y + 7, INK, false);
            int ly = y + 8 + 11 + 3;
            int i = 0;
            for (FormattedCharSequence l : lines) {
                boolean note = !c.reshoot().isEmpty() && i >= lines.size() - font.split(FormattedText.of("※ " + c.reshoot()), textW).size();
                g.drawString(font, l, cx + 8, ly, note ? RED : INK_SOFT, false);
                ly += 10;
                i++;
            }
            if (!c.go().isEmpty()) {
                int bx = cx + inner - btnW - 8, by = y + 6;
                boolean over = mouseX >= bx && mouseX < bx + btnW && mouseY >= by && mouseY < by + 16 && mouseY >= top && mouseY < bottom;
                g.fill(bx + 2, by + 2, bx + btnW + 2, by + 18, INK);
                g.fill(bx, by, bx + btnW, by + 16, over ? 0xFFB83A2A : RED);
                g.drawString(font, "去看", bx + (btnW - font.width("去看")) / 2, by + 4, PAPER, false);
                if (by + 16 > top && by < bottom) {
                    hits.add(new int[]{bx, Math.max(by, top), btnW, Math.min(by + 16, bottom) - Math.max(by, top)});
                    actions.add(() -> go(c));
                }
            }
            y += cardH + 8;
        }
        if (changes.isEmpty()) g.drawString(font, "这一轮还没有修改。", x0 + 14, top + 8, INK_SOFT, false);
        contentH = y + (int) scroll - top;
        g.disableScissor();

        long now = System.currentTimeMillis();
        String line = now - statusAt < 4000 ? status : "滚轮翻页。「去看」直接传过去演一遍，要管理员权限。";
        g.drawString(font, font.plainSubstrByWidth(line, pw - 24), x0 + 12, y0 + ph - foot + 3, now - statusAt < 4000 ? RED : INK_SOFT, false);
    }

    private void go(Change c) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        for (String cmd : c.go().split(";")) {
            if (!cmd.isBlank()) mc.player.connection.sendCommand(cmd.strip());
        }
        status = "已发送：/" + c.go().replace(";", "　/");
        statusAt = System.currentTimeMillis();
        mc.setScreen(null);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int view = height - 16 - 26 - 16;
        scroll = Math.max(0, Math.min(Math.max(0, contentH - view + 8), scroll - scrollY * 24));
        return true;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 0) {
            for (int i = 0; i < hits.size(); i++) {
                int[] r = hits.get(i);
                if (event.x() >= r[0] && event.x() < r[0] + r[2] && event.y() >= r[1] && event.y() < r[1] + r[3]) {
                    actions.get(i).run();
                    return true;
                }
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
            onClose();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(back);
    }
}
