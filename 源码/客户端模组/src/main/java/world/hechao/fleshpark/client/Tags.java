package world.hechao.fleshpark.client;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 纸条的名牌。画在界面层，因为世界里的字会被光影的雾和辉光吃掉。
 * 算出纸在屏幕上的位置，把名字写在那儿：走到 5 米内才淡出来，走远就没了，不占屏幕。隔着一堵墙也看得见，不同楼层的不显示。
 */
final class Tags {
    private record Tag(double x, double y, double z, boolean cabin, String text) {
    }

    private static final double RANGE = 5, FADE = 1.5, FLOOR_GAP = 4;
    private static final int ROW = 12;
    private static final Map<String, Tag> TAGS = new LinkedHashMap<>();
    /** 缆车根实体的位置。坐车时跟着车走，下了车留在到站的地方。 */
    private static double ax, ay, az;

    private Tags() {
    }

    /** id、x、y、z、是不是相对缆车、名字。 */
    static void add(String[] f) {
        if (f.length < 6) return;
        TAGS.put(f[0], new Tag(Double.parseDouble(f[1]), Double.parseDouble(f[2]), Double.parseDouble(f[3]), f[4].equals("1"), f[5]));
    }

    static void remove(String id) {
        TAGS.remove(id);
    }

    static void anchor(double x, double y, double z) {
        ax = x;
        ay = y;
        az = z;
    }

    static void clear() {
        TAGS.clear();
    }

    static void draw(GuiGraphics g, Font font, int w, int h, Minecraft mc) {
        if (TAGS.isEmpty() || mc.player == null) return;
        Entity vehicle = mc.player.getVehicle();
        if (vehicle != null) anchor(vehicle.getX(), vehicle.getY(), vehicle.getZ());
        Camera camera = mc.gameRenderer.getMainCamera();
        Vec3 eye = camera.position();
        double yaw = Math.toRadians(camera.yRot()), pitch = Math.toRadians(camera.xRot());
        // 相机的三个方向
        double fx = -Math.sin(yaw) * Math.cos(pitch), fy = -Math.sin(pitch), fz = Math.cos(yaw) * Math.cos(pitch);
        double rx = -Math.cos(yaw), rz = -Math.sin(yaw);
        double ux = -Math.sin(yaw) * Math.sin(pitch), uy = Math.cos(pitch), uz = Math.cos(yaw) * Math.sin(pitch);
        double focal = (h / 2.0) / Math.tan(Math.toRadians(mc.options.fov().get()) / 2);
        // 近的先摆。同一方向叠在一起的话，远的那个往上让
        java.util.List<double[]> order = new java.util.ArrayList<>();
        java.util.List<Tag> list = new java.util.ArrayList<>(TAGS.values());
        for (int i = 0; i < list.size(); i++) {
            Tag tag = list.get(i);
            double dx = tag.x + (tag.cabin ? ax : 0) - eye.x, dy = tag.y + (tag.cabin ? ay : 0) - eye.y, dz = tag.z + (tag.cabin ? az : 0) - eye.z;
            double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (dist > RANGE || Math.abs(dy) > FLOOR_GAP) continue;
            double depth = dx * fx + dy * fy + dz * fz;
            if (depth < 0.2) continue;
            int sx = (int) (w / 2.0 + (dx * rx + dz * rz) / depth * focal);
            int sy = (int) (h / 2.0 - (dx * ux + dy * uy + dz * uz) / depth * focal);
            order.add(new double[]{dist, i, sx, sy});
        }
        order.sort(java.util.Comparator.comparingDouble(o -> o[0]));
        java.util.List<int[]> placed = new java.util.ArrayList<>();
        for (double[] o : order) {
            Tag tag = list.get((int) o[1]);
            int tw = font.width(tag.text), x0 = (int) o[2] - tw / 2 - 3, x1 = x0 + tw + 6, y0 = (int) o[3] - 2;
            if (x1 < 0 || x0 > w || y0 < -12 || y0 > h) continue;
            boolean moved = true;
            while (moved) {
                moved = false;
                for (int[] r : placed) {
                    if (x0 < r[2] && x1 > r[0] && y0 < r[3] && y0 + ROW > r[1]) {
                        y0 = r[1] - ROW - 1;
                        moved = true;
                    }
                }
            }
            placed.add(new int[]{x0, y0, x1, y0 + ROW});
            float alpha = (float) Math.min(1, (RANGE - o[0]) / FADE);
            g.fill(x0, y0, x1, y0 + ROW, (int) (alpha * 0.6f * 255) << 24 | 0x24211F);
            g.drawString(font, tag.text, x0 + 3, y0 + 2, (int) (alpha * 255) << 24 | 0xFFFBF5, false);
        }
    }
}
