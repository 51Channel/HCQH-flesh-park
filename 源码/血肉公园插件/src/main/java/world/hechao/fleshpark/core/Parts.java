package world.hechao.fleshpark.core;

/** 插件要摆的展示实体，这里只记数据。 */
public final class Parts {
    private Parts() {
    }

    /** 一根肉索：沿 pts（x、y、z、半径）扫出一条连续的管子。kind：0 肉 1 暗肉 2 白筋 3 血管。window 表示压在大厅玻璃外面。 */
    public record Rope(int id, double[][] pts, int kind, boolean window) {
    }

    /** 轴对齐的方块展示：从 (x,y,z) 起，长宽高 (sx,sy,sz)。glow>=0 时亮度固定，不受周围光照影响。 */
    public record Box(String block, double x, double y, double z, double sx, double sy, double sz, int glow) {
    }

    /** 从 a 到 b 的一根杆。 */
    public record Beam(String block, double ax, double ay, double az, double bx, double by, double bz, double thick, int glow) {
    }

    /** 贴在墙上的字。yaw：0 朝 +z，180 朝 -z，90 朝 -x，-90 朝 +x。 */
    public record Text(String text, double x, double y, double z, float yaw, int color, float scale, int background, int glow) {
    }

    /** 一段圆管线。kind：0 黄 1 蓝 2 灰。 */
    public record Pipe(int kind, double ax, double ay, double az, double bx, double by, double bz, double rad) {
    }

    /** 海报：一张贴在墙上的薄片，(x,y,z) 是中心。 */
    public record Poster(String model, double x, double y, double z, float yaw, double w, double h, int glow) {
    }

    /** 从 a 到 b 的一块板，截面高 h 深 d，a、b 是截面中心。环廊的边、弧形墙面都用它，比方块的锯齿平滑。 */
    public record Panel(String block, double ax, double ay, double az, double bx, double by, double bz, double h, double d, int glow) {
    }

    /** 能开关的灯带。ring 是一组灯，大厅停电时一组组灭。 */
    public record Lamp(int ring, String block, double ax, double ay, double az, double bx, double by, double bz, double thick, int on, int off) {
    }

    /** 液面。 */
    public record Pool(String item, double x0, double z0, double x1, double z1, double y) {
    }

    /** 平放在地上的物品（宣传册）。 */
    public record Flat(String item, double x, double y, double z, double angle, float scale) {
    }
}
