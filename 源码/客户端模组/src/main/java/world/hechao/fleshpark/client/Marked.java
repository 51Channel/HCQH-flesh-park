package world.hechao.fleshpark.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

/**
 * 坏掉的字。{码:xx} 是乱码，一闪一闪偶尔露出原字；{黑:xx} 是方块，盖住看不见；{涂:xx} 是纸上被人用笔涂掉，还能隐约认出来；
 * {变:甲>乙} 一开始是甲，到了 {@link #changeAfter} 那一刻乱一下，变成乙（乙按甲的字数截齐，不够补空格）。
 */
final class Marked {
    private static final byte PLAIN = 0, GARBLED = 1, BLOCKED = 2, SCRIBBLED = 3, CHANGE = 4;
    /** {变:} 的字从这一刻（毫秒）起换成后面那个。纸读完三秒，由画纸的地方设。 */
    static long changeAfter = Long.MAX_VALUE;
    private static final String POOL = "锟斤拷烫屯囧¤§□";
    private static final String NOT_AT_START = "，。、！？：；”）…";

    private final char[] chars, alt;
    private final byte[] kinds;

    private Marked(char[] chars, char[] alt, byte[] kinds) {
        this.chars = chars;
        this.alt = alt;
        this.kinds = kinds;
    }

    static Marked of(String raw) {
        StringBuilder text = new StringBuilder(), later = new StringBuilder();
        List<Byte> kinds = new ArrayList<>();
        for (int i = 0; i < raw.length(); ) {
            byte kind = raw.startsWith("{码:", i) ? GARBLED : raw.startsWith("{黑:", i) ? BLOCKED : raw.startsWith("{涂:", i) ? SCRIBBLED : raw.startsWith("{变:", i) ? CHANGE : PLAIN;
            int end = kind == PLAIN ? -1 : raw.indexOf('}', i);
            if (end < 0) {
                later.append(raw.charAt(i));
                text.append(raw.charAt(i++));
                kinds.add(PLAIN);
                continue;
            }
            String inner = raw.substring(i + 3, end), before = inner, after = inner;
            if (kind == CHANGE) {
                int arrow = inner.indexOf('>');
                before = arrow < 0 ? inner : inner.substring(0, arrow);
                after = arrow < 0 ? inner : inner.substring(arrow + 1);
            }
            for (int k = 0; k < before.length(); k++) {
                text.append(before.charAt(k));
                later.append(k < after.length() ? after.charAt(k) : ' ');
                kinds.add(kind);
            }
            i = end + 1;
        }
        byte[] flat = new byte[kinds.size()];
        for (int i = 0; i < flat.length; i++) flat[i] = kinds.get(i);
        return new Marked(text.toString().toCharArray(), later.toString().toCharArray(), flat);
    }

    int length() {
        return chars.length;
    }

    /** 现在显示出来的样子，一整串。手机预览用。 */
    String plain() {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < chars.length; i++) out.append(shown(i));
        return out.toString();
    }

    /** 这一刻第 i 个字显示成什么。 */
    private String shown(int i) {
        return switch (kinds[i]) {
            case GARBLED -> {
                long beat = System.currentTimeMillis() / 140;
                int roll = new Random(beat * 31 + i * 17L + chars[i]).nextInt(POOL.length() + 2);
                yield roll >= POOL.length() ? String.valueOf(chars[i]) : String.valueOf(POOL.charAt(roll));
            }
            case BLOCKED -> "■";
            case CHANGE -> {
                long now = System.currentTimeMillis();
                if (now < changeAfter) yield String.valueOf(chars[i]);
                // 换的那一下乱几帧，再定下来
                if (now < changeAfter + 260) yield String.valueOf(POOL.charAt(new Random(now / 60 * 31 + i).nextInt(POOL.length())));
                yield String.valueOf(alt[i]);
            }
            default -> String.valueOf(chars[i]);
        };
    }

    /** 按宽度折行。标点不放到行首，宁可这一行挤一点。 */
    List<Marked> wrap(Font font, int width) {
        List<Marked> rows = new ArrayList<>();
        int start = 0, used = 0;
        for (int i = 0; i < chars.length; i++) {
            int w = font.width(String.valueOf(chars[i]));
            if (used + w > width && i > start && NOT_AT_START.indexOf(chars[i]) < 0) {
                rows.add(slice(start, i));
                start = i;
                used = 0;
            }
            used += w;
        }
        rows.add(slice(start, chars.length));
        return rows;
    }

    private Marked slice(int from, int to) {
        return new Marked(Arrays.copyOfRange(chars, from, to), Arrays.copyOfRange(alt, from, to), Arrays.copyOfRange(kinds, from, to));
    }

    /** 画前 count 个字，涂掉的地方压几笔来回的线。 */
    void draw(GuiGraphics g, Font font, int count, int x, int y, int color, int scribbleColor) {
        int cx = x, runStart = -1, runFirst = 0;
        int limit = Math.min(count, chars.length);
        for (int i = 0; i < limit; i++) {
            String c = shown(i);
            g.drawString(font, c, cx, y, color, false);
            if (kinds[i] == SCRIBBLED && runStart < 0) {
                runStart = cx;
                runFirst = i;
            }
            cx += font.width(c);
            boolean runEnds = runStart >= 0 && (i == limit - 1 || kinds[i + 1] != SCRIBBLED);
            if (runEnds) {
                // 笔画按这段开头的字定：字还在一个个写出来的时候，已经画上的笔画不跟着变
                scribble(g, runStart, cx, y, chars[runFirst] * 31 + runFirst, scribbleColor);
                runStart = -1;
            }
        }
    }

    private static void scribble(GuiGraphics g, int x0, int x1, int y, int seed, int color) {
        Random r = new Random(seed);
        for (int pass = 0; pass < 2; pass++) {
            float x = x0 - 1, py = y + 1 + r.nextFloat() * 5;
            boolean down = r.nextBoolean();
            while (x < x1) {
                float nx = Math.min(x1 + 1, x + 1.2f + r.nextFloat() * 1.8f);
                float ny = down ? y + 6 + r.nextFloat() * 2.5f : y - 0.5f + r.nextFloat() * 2.5f;
                stroke(g, x, py, nx, ny, color);
                x = nx;
                py = ny;
                down = !down;
            }
        }
    }

    private static void stroke(GuiGraphics g, float ax, float ay, float bx, float by, int color) {
        float dx = bx - ax, dy = by - ay;
        g.pose().pushMatrix();
        g.pose().translate(ax, ay);
        g.pose().rotate((float) Math.atan2(dy, dx));
        g.fill(0, 0, Math.max(1, Math.round((float) Math.sqrt(dx * dx + dy * dy))), 1, color);
        g.pose().popMatrix();
    }
}
