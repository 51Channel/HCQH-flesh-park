package world.hechao.fleshpark.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * 手机。园方的提示都发成短信：来了在左下角轻轻冒一条预览，几秒就收回去，不打断人说话。按 M 掏出来看全文，再按一下收起，↑↓ 翻。
 */
final class Phone {
    private static final class Sms {
        final String from, time;
        final Marked body;
        boolean read;

        Sms(String from, String time, Marked body) {
            this.from = from;
            this.time = time;
            this.body = body;
        }
    }

    private static final int WIDTH = 112, HEIGHT = 216, SHOWN = 190, LCD_H = 148;
    private static final int LCD = 0xFF87977A, LCD_DARK = 0xFF7D8D70, INK = 0xFF10140C, SHELL = 0xFF26282B, EDGE = 0xFF3C3F43, BEZEL = 0xFF0D0E0F, KEY = 0xFF44474C;
    private static final Identifier FLASHLIGHT = Identifier.fromNamespaceAndPath("fleshpark", "flashlight");
    static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(Identifier.fromNamespaceAndPath("fleshpark", "main"));
    private static KeyMapping toggle, up, down;

    private static final List<Sms> INBOX = new ArrayList<>();
    private static int at;
    private static boolean open, hinted, owned, toastHint;
    private static Sms toast;
    private static long toastAt;
    private static float raised;
    private static long lastDraw;

    private Phone() {
    }

    static void register() {
        toggle = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.fleshpark.phone", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_M, CATEGORY));
        up = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.fleshpark.phone_up", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UP, CATEGORY));
        down = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.fleshpark.phone_down", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_DOWN, CATEGORY));
    }

    /** 发件人、时间（空着就是现在）、正文。 */
    static void receive(String[] fields) {
        if (fields.length < 3) return;
        String time = fields[1];
        if (time.isEmpty()) {
            java.time.LocalTime now = java.time.LocalTime.now();
            time = String.format("今天 %02d:%02d", now.getHour(), now.getMinute());
        }
        StringBuilder body = new StringBuilder();
        for (int i = 2; i < fields.length; i++) body.append(fields[i]);
        Sms sms = new Sms(fields[0], time, Marked.of(body.toString()));
        INBOX.add(sms);
        Audio.play("phone", "buzz", 0.3f, 1.2f, false, 0);
        toast = sms;
        toastAt = System.currentTimeMillis();
        // 第一条预览后面带一个“M 查看”，之后就只有预览
        toastHint = !hinted;
        hinted = true;
    }

    static void clear() {
        INBOX.clear();
        at = 0;
        open = false;
        hinted = false;
        owned = false;
        toast = null;
        raised = 0;
    }

    /** 测试和导演栏用：不按键也能开关和翻页。 */
    static void command(String what) {
        switch (what) {
            case "next" -> flip(1);
            case "prev" -> flip(-1);
            case "on" -> owned = true;
            // 收尾：手机自己举起来，翻到最新那条
            case "show" -> {
                if (!owned || INBOX.isEmpty()) return;
                open = true;
                at = INBOX.size() - 1;
            }
            case "hide" -> open = false;
            default -> flipOpen();
        }
    }

    static void tick() {
        if (toggle == null) return;
        while (toggle.consumeClick()) flipOpen();
        while (up.consumeClick()) if (open) flip(-1);
        while (down.consumeClick()) if (open) flip(1);
        if (open && at < INBOX.size()) INBOX.get(at).read = true;
    }

    private static void flipOpen() {
        // 手机在入园大厅的桌子上，拿到之前没有手机可掏
        if (!owned) return;
        open = !open;
        if (!open) return;
        // 先看最早没读的那条，都读过就看最新的
        at = Math.max(0, INBOX.size() - 1);
        for (int i = 0; i < INBOX.size(); i++) {
            if (!INBOX.get(i).read) {
                at = i;
                break;
            }
        }
    }

    private static void flip(int by) {
        if (!INBOX.isEmpty()) at = Math.max(0, Math.min(INBOX.size() - 1, at + by));
    }

    private static boolean flashlightInOffhand(Minecraft mc) {
        if (mc.player == null) return false;
        ItemStack off = mc.player.getOffhandItem();
        return FLASHLIGHT.equals(off.get(DataComponents.ITEM_MODEL));
    }

    /** 左下角的预览：发件人一行，正文前一截一行。滑上来，停四秒，滑下去。手机掏出来的时候不画。 */
    private static void drawToast(GuiGraphics g, Font font, int h, long now) {
        if (toast == null || open) return;
        float t = (now - toastAt) / 1000f;
        if (t > 4.6f) {
            toast = null;
            return;
        }
        float k = t < 0.3f ? t / 0.3f : t > 4.2f ? (4.6f - t) / 0.4f : 1;
        int tw = 150, th = 26, x = 10, y = h - 44 - th + Math.round((1 - k) * 12);
        int a = Math.round(k * 200);
        g.fill(x, y, x + tw, y + th, a << 24 | 0x14161A);
        g.fill(x, y, x + 2, y + th, Math.round(k * 255) << 24 | 0x87977A);
        String from = toast.from + (toastHint ? "    M 查看" : "");
        g.drawString(font, from, x + 7, y + 4, Math.round(k * 255) << 24 | 0xC8D2BC, false);
        String first = toast.body.plain();
        if (font.width(first) > tw - 14) first = font.plainSubstrByWidth(first, tw - 14 - font.width("…")) + "…";
        g.drawString(font, first, x + 7, y + 15, Math.round(k * 230) << 24 | 0xF2EEE4, false);
    }

    static void draw(GuiGraphics g, Font font, int w, int h, Minecraft mc) {
        long now = System.currentTimeMillis();
        drawToast(g, font, h, now);
        float dt = lastDraw == 0 ? 0 : Math.min(0.1f, (now - lastDraw) / 1000f);
        lastDraw = now;
        raised = Math.max(0, Math.min(1, raised + (open ? dt : -dt) * 4.5f));
        if (raised <= 0) return;
        float ease = 1 - (1 - raised) * (1 - raised) * (1 - raised);
        // 拿在空着的那只手上，手有一点抖
        int x = flashlightInOffhand(mc) ? w - WIDTH - 16 : 16;
        int y = h - Math.round(Math.min(SHOWN, h - 12) * ease) + (int) Math.round(Math.sin(now / 900.0));
        shell(g, x, y);
        int lx = x + 8, ly = y + 21, lw = WIDTH - 16, lh = LCD_H;
        g.fill(lx, ly, lx + lw, ly + lh, LCD);
        for (int sy = ly; sy < ly + lh; sy += 2) g.fill(lx, sy, lx + lw, sy + 1, LCD_DARK);
        status(g, font, lx, ly, lw, mc);
        if (INBOX.isEmpty()) {
            g.drawString(font, "没有短信", lx + (lw - font.width("没有短信")) / 2, ly + 50, INK, false);
            return;
        }
        Sms sms = INBOX.get(Math.min(at, INBOX.size() - 1));
        String count = (at + 1) + "/" + INBOX.size();
        g.drawString(font, clip(font, sms.from, lw - 10 - font.width(count)), lx + 3, ly + 14, INK, false);
        g.drawString(font, count, lx + lw - 3 - font.width(count), ly + 14, INK, false);
        g.drawString(font, clip(font, sms.time, lw - 6), lx + 3, ly + 24, INK, false);
        g.fill(lx + 2, ly + 34, lx + lw - 2, ly + 35, INK);
        int row = ly + 38;
        for (Marked line : sms.body.wrap(font, lw - 6)) {
            if (row > ly + lh - 19) break;
            line.draw(g, font, line.length(), lx + 3, row, INK, INK);
            row += 10;
        }
        g.fill(lx + 2, ly + lh - 12, lx + lw - 2, ly + lh - 11, INK);
        g.drawString(font, "↑↓ 翻", lx + 3, ly + lh - 10, INK, false);
        g.drawString(font, "M 收起", lx + lw - 3 - font.width("M 收起"), ly + lh - 10, INK, false);
    }

    /** 越往下信号越差，短信还是会来。 */
    private static void status(GuiGraphics g, Font font, int lx, int ly, int lw, Minecraft mc) {
        double y = mc.player == null ? 0 : mc.player.getY();
        int bars = y >= 222 ? 3 : y >= 170 ? 1 : 0;
        for (int i = 0; i < 3; i++) {
            int bh = 2 + i * 2;
            if (i < bars) g.fill(lx + 3 + i * 3, ly + 10 - bh, lx + 5 + i * 3, ly + 10, INK);
        }
        if (bars == 0) g.drawString(font, "无服务", lx + 3, ly + 2, INK, false);
        g.fill(lx + lw - 13, ly + 3, lx + lw - 3, ly + 9, INK);
        g.fill(lx + lw - 12, ly + 4, lx + lw - 9, ly + 8, LCD);
        g.fill(lx + 2, ly + 11, lx + lw - 2, ly + 12, INK);
    }

    private static void shell(GuiGraphics g, int x, int y) {
        int bottom = y + HEIGHT;
        g.fill(x + 3, y, x + WIDTH - 3, bottom, SHELL);
        g.fill(x, y + 3, x + WIDTH, bottom - 3, SHELL);
        g.fill(x + 1, y + 1, x + WIDTH - 1, y + 3, SHELL);
        g.fill(x + 3, y, x + WIDTH - 3, y + 1, EDGE);
        g.fill(x, y + 3, x + 1, bottom - 3, EDGE);
        g.fill(x + WIDTH / 2 - 14, y + 9, x + WIDTH / 2 + 14, y + 11, BEZEL);
        g.fill(x + 6, y + 19, x + WIDTH - 6, y + 21 + LCD_H + 2, BEZEL);
        // 键盘：屏幕下面露出来一点
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 3; c++) {
                int kx = x + 10 + c * 32, ky = y + 21 + LCD_H + 8 + r * 15;
                g.fill(kx, ky, kx + 28, ky + 11, KEY);
                g.fill(kx, ky, kx + 28, ky + 1, EDGE);
            }
        }
    }

    private static String clip(Font font, String text, int width) {
        if (font.width(text) <= width) return text;
        return font.plainSubstrByWidth(text, width - font.width("…")) + "…";
    }
}
