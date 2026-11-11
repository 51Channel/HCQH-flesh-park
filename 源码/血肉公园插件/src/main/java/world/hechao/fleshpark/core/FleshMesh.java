package world.hechao.fleshpark.core;

import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 把规划里的肉变成连续的网格：肉壁是一张圆润的曲面（Surface Nets），肉索是一整条沿样条扫出来的管子。
 * 构建时算好存进客户端模组，插件只摆一个个锚点实体，客户端按编号取网格来画。
 */
public final class FleshMesh {
    /** 贴图：0 肉壁 1 肉索 2 暗肉 3 白筋 4 血管。 */
    public static final class Piece {
        public int id, texture, sides, rings;
        public boolean window;
        /** 0 零碎的肉（肉壁、肉索），1 缆车腔，2 森林，3 穹顶，4 腺泡。客户端按所在区域整块不画。 */
        public int group;
        public double ox, oy, oz;
        public float sx, sy, sz, radius;
        public float[] pos, uv;
        public byte[] normal;
        /** 每个顶点两个字节：蠕动的相位、幅度。 */
        public byte[] wave;
    }

    private static final int CHUNK = 24;
    private static final float ISO = 0.5f;

    public final List<Piece> pieces = new ArrayList<>();
    private final ParkPlan plan;
    private LongSet flesh;
    /** 缆车要穿过的空间：一串球，按 8 米一格分桶。肉在球附近平滑地退开，不会被切出台阶。 */
    private final Map<Long, List<double[]>> clearing = new HashMap<>();
    private final Map<Long, Float> memo = new HashMap<>(4_000_000);

    public FleshMesh(ParkPlan plan) {
        this.plan = plan;
        walls();
        memo.clear();
        chamber(plan.chamber, -32, 4, -92, 170, 186, 92, 0);
        if (plan.forestChamber != null) chamber(plan.forestChamber, 240, 40, -130, 440, 240, 90, 1);
        chamber(plan.dome, -46, 222, -72, 104, 270, 72, 2);
        chamber(Chamber.burrow(), Lower.BURROW_X0 - 9, Lower.BURROW_Y - 8, Lower.BURROW_Z - 9, Lower.BURROW_X1 + 3, Lower.BURROW_Y + 9, Lower.BURROW_Z + 10, 3);
        chamber(plan.wellChamber, Well.SX - 56, Well.BOTTOM - 14, Well.SZ - 56, Well.SX + 56, Well.TOP + 12, Well.SZ + 56, 4);
        for (Parts.Rope rope : plan.ropes) tube(rope);
        for (int i = 0; i < pieces.size(); i++) pieces.get(i).id = i;
    }

    // ---------- 肉壁 ----------

    private static long key(int x, int y, int z) {
        return ((long) (x + 4096)) << 42 | ((long) (y + 4096)) << 21 | (long) (z + 4096);
    }

    /** 开放寻址的 long 集合，这里要查上亿次，HashSet 太慢。 */
    private static final class LongSet {
        private long[] table = new long[1 << 22];
        private int size;

        LongSet() {
            Arrays.fill(table, -1);
        }

        boolean add(long k) {
            if (size * 2 >= table.length) grow();
            int mask = table.length - 1, i = (int) ((k * 0x9E3779B97F4A7C15L) >>> 40) & mask;
            while (table[i] != -1) {
                if (table[i] == k) return false;
                i = (i + 1) & mask;
            }
            table[i] = k;
            size++;
            return true;
        }

        boolean has(long k) {
            int mask = table.length - 1, i = (int) ((k * 0x9E3779B97F4A7C15L) >>> 40) & mask;
            while (table[i] != -1) {
                if (table[i] == k) return true;
                i = (i + 1) & mask;
            }
            return false;
        }

        private void grow() {
            long[] old = table;
            table = new long[old.length * 2];
            Arrays.fill(table, -1);
            size = 0;
            for (long k : old) if (k != -1) add(k);
        }
    }

    /** 格点 (i,j,k) 是方块的角：周围一圈方块按距离加权。0.5 正好在方块面上，边角被磨圆。再加起伏，肉壁才是一团一团的。 */
    private float field(int i, int j, int k) {
        long id = key(i, j, k);
        Float known = memo.get(id);
        if (known != null) return known;
        float sum = 0, total = 0;
        for (int x = i - 2; x <= i + 1; x++) {
            for (int y = j - 2; y <= j + 1; y++) {
                for (int z = k - 2; z <= k + 1; z++) {
                    float dx = x + 0.5f - i, dy = y + 0.5f - j, dz = z + 0.5f - k;
                    float w = (float) Math.exp(-(dx * dx + dy * dy + dz * dz) / 1.6);
                    total += w;
                    if (flesh.has(key(x, y, z))) sum += w;
                }
            }
        }
        float value = sum / total;
        if (value > 0.04f && value < 0.96f) {
            // 人碰得到的房间里起伏小一点，不然肉壁会鼓进人走的地方
            float amount = i >= 116 && j < 30 ? 0.3f : 1f;
            value += ((float) (Noise.fbm(i * 0.17, j * 0.17, k * 0.17, 2) - 0.5) * 0.62f + (float) (Noise.fbm(i * 0.47 + 9, j * 0.47, k * 0.47, 1) - 0.5) * 0.14f) * amount;
        }
        if (value > 0.04f) value *= clearance(i, j, k);
        memo.put(id, value);
        return value;
    }

    private float clearance(int i, int j, int k) {
        int bx = Math.floorDiv(i, 8), by = Math.floorDiv(j, 8), bz = Math.floorDiv(k, 8);
        double best = 1e9;
        for (int x = bx - 1; x <= bx + 1; x++) {
            for (int y = by - 1; y <= by + 1; y++) {
                for (int z = bz - 1; z <= bz + 1; z++) {
                    List<double[]> list = clearing.get(key(x, y, z));
                    if (list == null) continue;
                    for (double[] s : list) {
                        double dx = i - s[0], dy = j - s[1], dz = k - s[2];
                        best = Math.min(best, dx * dx + dy * dy + dz * dz);
                    }
                }
            }
        }
        if (best > 49) return 1;
        double d = Math.sqrt(best);
        double t = Math.max(0, Math.min(1, (d - 3.9) / 2.8));
        return (float) (t * t * (3 - 2 * t));
    }

    private void walls() {
        flesh = new LongSet();
        List<int[]> boundary = new ArrayList<>();
        plan.forEachBlock((x, y, z, b) -> {
            if (b.isFlesh()) flesh.add(key(x, y, z));
        });
        plan.forEachBlock((x, y, z, b) -> {
            if (!b.isFlesh()) return;
            if (!flesh.has(key(x + 1, y, z)) || !flesh.has(key(x - 1, y, z)) || !flesh.has(key(x, y + 1, z)) || !flesh.has(key(x, y - 1, z)) || !flesh.has(key(x, y, z + 1)) || !flesh.has(key(x, y, z - 1))) {
                boundary.add(new int[]{x, y, z});
            }
        });
        // 缆车扫过的空间不能有肉
        plan.route.forEachSample(1.0, (x, y, z) -> {
            for (double dx : new double[]{-5.4, -3.0, -0.6}) {
                clearing.computeIfAbsent(key(Math.floorDiv((int) Math.floor(x + dx), 8), Math.floorDiv((int) Math.floor(y + 1.3), 8), Math.floorDiv((int) Math.floor(z), 8)), a -> new ArrayList<>()).add(new double[]{x + dx, y + 1.3, z});
            }
        });
        Map<Long, Integer> vertexOf = new HashMap<>(2_000_000);
        List<float[]> vertices = new ArrayList<>(1_000_000);
        List<int[]> cubes = new ArrayList<>(1_000_000);
        LongSet visited = new LongSet();
        for (int[] c : boundary) {
            for (int i = c[0] - 2; i <= c[0] + 2; i++) {
                for (int j = c[1] - 2; j <= c[1] + 2; j++) {
                    for (int k = c[2] - 2; k <= c[2] + 2; k++) {
                        if (!visited.add(key(i, j, k))) continue;
                        float[] v = crossing(i, j, k);
                        if (v == null) continue;
                        vertexOf.put(key(i, j, k), vertices.size());
                        vertices.add(v);
                        cubes.add(new int[]{i, j, k});
                    }
                }
            }
        }
        // 相邻四个小方格的顶点连成一个面
        int[][] quads = new int[cubes.size() * 3][];
        int count = 0;
        for (int[] cube : cubes) {
            int i = cube[0], j = cube[1], k = cube[2];
            float f0 = field(i, j, k);
            int[][] edges = {{1, 0, 0}, {0, 1, 0}, {0, 0, 1}};
            for (int e = 0; e < 3; e++) {
                float f1 = field(i + edges[e][0], j + edges[e][1], k + edges[e][2]);
                if ((f0 > ISO) == (f1 > ISO)) continue;
                int[] a = e == 0 ? new int[]{0, -1, 0} : e == 1 ? new int[]{0, 0, -1} : new int[]{-1, 0, 0};
                int[] b = e == 0 ? new int[]{0, 0, -1} : e == 1 ? new int[]{-1, 0, 0} : new int[]{0, -1, 0};
                Integer v0 = vertexOf.get(key(i, j, k));
                Integer v1 = vertexOf.get(key(i + a[0], j + a[1], k + a[2]));
                Integer v2 = vertexOf.get(key(i + a[0] + b[0], j + a[1] + b[1], k + a[2] + b[2]));
                Integer v3 = vertexOf.get(key(i + b[0], j + b[1], k + b[2]));
                if (v0 == null || v1 == null || v2 == null || v3 == null) continue;
                // 朝向：法线从肉里指向空气
                quads[count++] = f0 > ISO ? new int[]{v0, v1, v2, v3} : new int[]{v3, v2, v1, v0};
            }
        }
        // 面积加权的光滑法线
        float[][] normals = new float[vertices.size()][3];
        for (int q = 0; q < count; q++) {
            int[] f = quads[q];
            float[] n = faceNormal(vertices.get(f[0]), vertices.get(f[1]), vertices.get(f[2]), vertices.get(f[3]));
            for (int v : f) {
                normals[v][0] += n[0];
                normals[v][1] += n[1];
                normals[v][2] += n[2];
            }
        }
        for (float[] n : normals) {
            float len = (float) Math.sqrt(n[0] * n[0] + n[1] * n[1] + n[2] * n[2]);
            if (len > 1e-6f) {
                n[0] /= len;
                n[1] /= len;
                n[2] /= len;
            }
        }
        // 按区块分开，看不见的区块不用画
        Map<Long, List<int[]>> byChunk = new HashMap<>();
        for (int q = 0; q < count; q++) {
            float[] v = vertices.get(quads[q][0]);
            byChunk.computeIfAbsent(key(Math.floorDiv((int) v[0], CHUNK), Math.floorDiv((int) v[1], CHUNK), Math.floorDiv((int) v[2], CHUNK)), x -> new ArrayList<>()).add(quads[q]);
        }
        for (List<int[]> list : byChunk.values()) pieces.add(chunk(list, vertices, normals));
    }

    /** 这个小方格里有没有穿过等值面，有的话顶点取各条穿过的边的平均。 */
    private float[] crossing(int i, int j, int k) {
        float[] f = new float[8];
        boolean any = false, all = true;
        for (int c = 0; c < 8; c++) {
            f[c] = field(i + (c & 1), j + (c >> 1 & 1), k + (c >> 2 & 1));
            if (f[c] > ISO) any = true;
            else all = false;
        }
        if (!any || all) return null;
        float x = 0, y = 0, z = 0;
        int n = 0;
        int[][] edges = {{0, 1}, {2, 3}, {4, 5}, {6, 7}, {0, 2}, {1, 3}, {4, 6}, {5, 7}, {0, 4}, {1, 5}, {2, 6}, {3, 7}};
        for (int[] e : edges) {
            float a = f[e[0]], b = f[e[1]];
            if ((a > ISO) == (b > ISO)) continue;
            float t = (ISO - a) / (b - a);
            x += (e[0] & 1) + ((e[1] & 1) - (e[0] & 1)) * t;
            y += (e[0] >> 1 & 1) + ((e[1] >> 1 & 1) - (e[0] >> 1 & 1)) * t;
            z += (e[0] >> 2 & 1) + ((e[1] >> 2 & 1) - (e[0] >> 2 & 1)) * t;
            n++;
        }
        return new float[]{i + x / n, j + y / n, k + z / n};
    }

    private static float[] faceNormal(float[] a, float[] b, float[] c, float[] d) {
        float ux = c[0] - a[0], uy = c[1] - a[1], uz = c[2] - a[2], vx = d[0] - b[0], vy = d[1] - b[1], vz = d[2] - b[2];
        return new float[]{uy * vz - uz * vy, uz * vx - ux * vz, ux * vy - uy * vx};
    }

    private Piece chunk(List<int[]> quads, List<float[]> vertices, float[][] normals) {
        Piece p = new Piece();
        p.texture = 0;
        float minX = 1e9f, minY = 1e9f, minZ = 1e9f, maxX = -1e9f, maxY = -1e9f, maxZ = -1e9f;
        for (int[] q : quads) {
            for (int v : q) {
                float[] a = vertices.get(v);
                minX = Math.min(minX, a[0]);
                minY = Math.min(minY, a[1]);
                minZ = Math.min(minZ, a[2]);
                maxX = Math.max(maxX, a[0]);
                maxY = Math.max(maxY, a[1]);
                maxZ = Math.max(maxZ, a[2]);
            }
        }
        p.ox = (minX + maxX) / 2;
        p.oy = minY;
        p.oz = (minZ + maxZ) / 2;
        p.sx = maxX - minX;
        p.sy = maxY - minY;
        p.sz = maxZ - minZ;
        int n = quads.size() * 4;
        p.pos = new float[n * 3];
        p.uv = new float[n * 2];
        p.normal = new byte[n * 3];
        p.wave = new byte[n * 2];
        int at = 0;
        for (int[] q : quads) {
            float[] fn = faceNormal(vertices.get(q[0]), vertices.get(q[1]), vertices.get(q[2]), vertices.get(q[3]));
            float ax = Math.abs(fn[0]), ay = Math.abs(fn[1]), az = Math.abs(fn[2]);
            for (int v : q) {
                float[] a = vertices.get(v);
                float[] m = normals[v];
                p.pos[at * 3] = (float) (a[0] - p.ox);
                p.pos[at * 3 + 1] = (float) (a[1] - p.oy);
                p.pos[at * 3 + 2] = (float) (a[2] - p.oz);
                // 按面朝向从三个方向贴
                float u = ax >= ay && ax >= az ? a[2] : a[0], w = ay >= ax && ay >= az ? a[2] : a[1];
                p.uv[at * 2] = u / 2.3f;
                p.uv[at * 2 + 1] = w / 2.3f;
                p.normal[at * 3] = (byte) (m[0] * 127);
                p.normal[at * 3 + 1] = (byte) (m[1] * 127);
                p.normal[at * 3 + 2] = (byte) (m[2] * 127);
                at++;
            }
        }
        return p;
    }

    // ---------- 超大肉腔 ----------

    private static final double FINE = 0.85;
    private static final int CELL = 5;
    /** 肉腔的面，导出 OBJ 用。 */
    public final List<Piece> chamberPieces = new ArrayList<>();

    /** 大模型：先粗筛哪些格子挨着表面，只在这些格子里细算，再用 Surface Nets 连成一张面。 */
    private void chamber(Chamber c, int x0, int y0, int z0, int x1, int y1, int z1, int kind) {
        double cs = CELL * FINE;
        int nx = (int) Math.ceil((x1 - x0) / cs), ny = (int) Math.ceil((y1 - y0) / cs), nz = (int) Math.ceil((z1 - z0) / cs);
        int total = (nx + 1) * (ny + 1) * (nz + 1);
        float[] coarse = new float[total];
        java.util.stream.IntStream.range(0, total).parallel().forEach(i -> {
            int ix = i % (nx + 1), iy = i / (nx + 1) % (ny + 1), iz = i / ((nx + 1) * (ny + 1));
            coarse[i] = (float) c.f(x0 + ix * cs, y0 + iy * cs, z0 + iz * cs);
        });
        float[][] fine = new float[nx * ny * nz][];
        List<Integer> marked = new ArrayList<>();
        for (int iz = 0; iz < nz; iz++) {
            for (int iy = 0; iy < ny; iy++) {
                for (int ix = 0; ix < nx; ix++) {
                    float low = 1e9f, high = -1e9f;
                    for (int k = 0; k < 8; k++) {
                        float v = coarse[(ix + (k & 1)) + (iy + (k >> 1 & 1)) * (nx + 1) + (iz + (k >> 2 & 1)) * (nx + 1) * (ny + 1)];
                        low = Math.min(low, v);
                        high = Math.max(high, v);
                    }
                    if (low < 8 && high > -8) marked.add(ix + iy * nx + iz * nx * ny);
                }
            }
        }
        int side = CELL + 1;
        marked.parallelStream().forEach(cell -> {
            int ix = cell % nx, iy = cell / nx % ny, iz = cell / (nx * ny);
            float[] v = new float[side * side * side];
            for (int a = 0; a < side; a++) {
                for (int b = 0; b < side; b++) {
                    for (int d = 0; d < side; d++) {
                        v[a + b * side + d * side * side] = (float) c.f(x0 + (ix * CELL + a) * FINE, y0 + (iy * CELL + b) * FINE, z0 + (iz * CELL + d) * FINE);
                    }
                }
            }
            fine[cell] = v;
        });
        // 细格子的值：边界上的点属于哪一格都行
        NodeField node = (I, J, K) -> {
            int cx = Math.min(I / CELL, nx - 1), cy = Math.min(J / CELL, ny - 1), cz = Math.min(K / CELL, nz - 1);
            if (I < 0 || J < 0 || K < 0 || I > nx * CELL || J > ny * CELL || K > nz * CELL) return Float.NaN;
            for (int ax = 0; ax < 2; ax++) {
                for (int ay = 0; ay < 2; ay++) {
                    for (int az = 0; az < 2; az++) {
                        int tx = cx - ax, ty = cy - ay, tz = cz - az;
                        if (tx < 0 || ty < 0 || tz < 0) continue;
                        int lx = I - tx * CELL, ly = J - ty * CELL, lz = K - tz * CELL;
                        if (lx > CELL || ly > CELL || lz > CELL) continue;
                        float[] v = fine[tx + ty * nx + tz * nx * ny];
                        if (v != null) return v[lx + ly * side + lz * side * side];
                    }
                }
            }
            return Float.NaN;
        };
        Map<Long, Integer> vertexOf = new HashMap<>(1_000_000);
        List<float[]> vertices = new ArrayList<>(500_000);
        List<int[]> cubes = new ArrayList<>(500_000);
        float[] f = new float[8];
        int[][] edges = {{0, 1}, {2, 3}, {4, 5}, {6, 7}, {0, 2}, {1, 3}, {4, 6}, {5, 7}, {0, 4}, {1, 5}, {2, 6}, {3, 7}};
        for (int cell : marked) {
            int ix = cell % nx, iy = cell / nx % ny, iz = cell / (nx * ny);
            for (int a = 0; a < CELL; a++) {
                for (int b = 0; b < CELL; b++) {
                    for (int d = 0; d < CELL; d++) {
                        int I = ix * CELL + a, J = iy * CELL + b, K = iz * CELL + d;
                        boolean any = false, all = true, bad = false;
                        for (int k = 0; k < 8; k++) {
                            f[k] = node.at(I + (k & 1), J + (k >> 1 & 1), K + (k >> 2 & 1));
                            if (Float.isNaN(f[k])) bad = true;
                            if (f[k] > 0) any = true;
                            else all = false;
                        }
                        if (bad || !any || all) continue;
                        float sx = 0, sy = 0, sz = 0;
                        int n = 0;
                        for (int[] e : edges) {
                            float p0 = f[e[0]], p1 = f[e[1]];
                            if ((p0 > 0) == (p1 > 0)) continue;
                            float t = -p0 / (p1 - p0);
                            sx += (e[0] & 1) + ((e[1] & 1) - (e[0] & 1)) * t;
                            sy += (e[0] >> 1 & 1) + ((e[1] >> 1 & 1) - (e[0] >> 1 & 1)) * t;
                            sz += (e[0] >> 2 & 1) + ((e[1] >> 2 & 1) - (e[0] >> 2 & 1)) * t;
                            n++;
                        }
                        vertexOf.put(key(I, J, K), vertices.size());
                        vertices.add(new float[]{(float) (x0 + (I + sx / n) * FINE), (float) (y0 + (J + sy / n) * FINE), (float) (z0 + (K + sz / n) * FINE)});
                        cubes.add(new int[]{I, J, K});
                    }
                }
            }
        }
        List<int[]> quads = new ArrayList<>(cubes.size());
        for (int[] cube : cubes) {
            int I = cube[0], J = cube[1], K = cube[2];
            float f0 = node.at(I, J, K);
            int[][] dirs = {{1, 0, 0}, {0, 1, 0}, {0, 0, 1}};
            for (int e = 0; e < 3; e++) {
                float f1 = node.at(I + dirs[e][0], J + dirs[e][1], K + dirs[e][2]);
                if (Float.isNaN(f0) || Float.isNaN(f1) || (f0 > 0) == (f1 > 0)) continue;
                int[] a = e == 0 ? new int[]{0, -1, 0} : e == 1 ? new int[]{0, 0, -1} : new int[]{-1, 0, 0};
                int[] b = e == 0 ? new int[]{0, 0, -1} : e == 1 ? new int[]{-1, 0, 0} : new int[]{0, -1, 0};
                Integer v0 = vertexOf.get(key(I, J, K)), v1 = vertexOf.get(key(I + a[0], J + a[1], K + a[2]));
                Integer v2 = vertexOf.get(key(I + a[0] + b[0], J + a[1] + b[1], K + a[2] + b[2])), v3 = vertexOf.get(key(I + b[0], J + b[1], K + b[2]));
                if (v0 == null || v1 == null || v2 == null || v3 == null) continue;
                float[] mid = vertices.get(v0);
                // 游客中心里面不要有肉腔的面
                if (kind == 0 && (mid[0] > ParkPlan.DRUM_X + 6 || Math.hypot(mid[0] - ParkPlan.DRUM_X, mid[2]) < ParkPlan.DRUM_R + 1.5 && mid[1] > 22 && mid[1] < 50)) continue;
                quads.add(f0 > 0 ? new int[]{v0, v1, v2, v3} : new int[]{v3, v2, v1, v0});
            }
        }
        float[][] normals = new float[vertices.size()][3];
        for (int[] q : quads) {
            float[] n = faceNormal(vertices.get(q[0]), vertices.get(q[1]), vertices.get(q[2]), vertices.get(q[3]));
            for (int v : q) {
                normals[v][0] += n[0];
                normals[v][1] += n[1];
                normals[v][2] += n[2];
            }
        }
        for (float[] n : normals) {
            float len = (float) Math.sqrt(n[0] * n[0] + n[1] * n[1] + n[2] * n[2]);
            if (len > 1e-6f) {
                n[0] /= len;
                n[1] /= len;
                n[2] /= len;
            }
        }
        Map<Long, List<int[]>> byChunk = new HashMap<>();
        for (int[] q : quads) {
            float[] v = vertices.get(q[0]);
            byChunk.computeIfAbsent(key(Math.floorDiv((int) v[0], CHUNK), Math.floorDiv((int) v[1], CHUNK), Math.floorDiv((int) v[2], CHUNK)), k -> new ArrayList<>()).add(q);
        }
        for (List<int[]> list : byChunk.values()) {
            Piece p = chunk(list, vertices, normals);
            p.group = kind + 1;
            pieces.add(p);
            chamberPieces.add(p);
        }
        System.out.printf("肉腔 %d：粗格 %d，细算 %d 格，%d 个面%n", kind, total, marked.size(), quads.size());
    }

    private interface NodeField {
        float at(int i, int j, int k);
    }

    /** 把肉腔导出成 OBJ，可以拿去雕刻软件里看或改。 */
    public void writeObj(java.nio.file.Path path) throws IOException {
        try (java.io.PrintWriter out = new java.io.PrintWriter(new java.io.BufferedWriter(new java.io.OutputStreamWriter(java.nio.file.Files.newOutputStream(path), java.nio.charset.StandardCharsets.UTF_8)))) {
            out.println("# 血肉公园 超大肉腔。大于 0 的一侧是肉，法线朝向空气");
            int base = 1;
            for (Piece p : chamberPieces) {
                int n = p.pos.length / 3;
                for (int i = 0; i < n; i++) {
                    out.printf(java.util.Locale.ROOT, "v %.2f %.2f %.2f%n", p.ox + p.pos[i * 3], p.oy + p.pos[i * 3 + 1], p.oz + p.pos[i * 3 + 2]);
                }
                for (int i = 0; i < n; i += 4) out.printf("f %d %d %d %d%n", base + i, base + i + 1, base + i + 2, base + i + 3);
                base += n;
            }
        }
    }

    // ---------- 肉索 ----------

    /** 一根肉索：样条磨圆，沿着它扫出一条连续的管子。表面有拧着的肌束，粗细慢慢变，不分节。超过一定长度切成几段，好做视野剔除。 */
    private void tube(Parts.Rope rope) {
        double[][] line = spline(rope.pts());
        if (line.length < 3) return;
        settle(line);
        relax(line);
        int texture = rope.kind() == 1 ? 2 : rope.kind() == 2 ? 3 : rope.kind() == 3 ? 4 : 1;
        double max = 0;
        for (double[] s : line) max = Math.max(max, s[3]);
        int sides = (int) Math.min(48, Math.max(12, Math.round((10 + max * 20) / 4) * 4));
        double seed = rope.id() * 1.37;
        // 累计长度
        double[] along = new double[line.length];
        for (int i = 1; i < line.length; i++) along[i] = along[i - 1] + dist(line[i - 1], line[i]);
        // 沿线平行移动的坐标系
        double[][] e1 = new double[line.length][], e2 = new double[line.length][];
        double[] t0 = tangent(line, 0);
        double[] seedAxis = Math.abs(t0[1]) < 0.9 ? new double[]{0, 1, 0} : new double[]{1, 0, 0};
        double[] u = normalize(cross(t0, seedAxis));
        for (int i = 0; i < line.length; i++) {
            double[] t = tangent(line, i);
            double dot = u[0] * t[0] + u[1] * t[1] + u[2] * t[2];
            u = normalize(new double[]{u[0] - t[0] * dot, u[1] - t[1] * dot, u[2] - t[2] * dot});
            e1[i] = u;
            e2[i] = normalize(cross(t, u));
        }
        int perPiece = (int) Math.max(24, Math.min(90, 14 / Math.max(0.05, along[along.length - 1] / line.length)));
        for (int from = 0; from < line.length - 1; from += perPiece) {
            int to = Math.min(line.length - 1, from + perPiece);
            if (line.length - 1 - to < 8) to = line.length - 1;
            pieces.add(section(rope, line, along, e1, e2, from, to, sides, texture, seed));
            if (to == line.length - 1) break;
        }
    }

    /** 中心线上有小抖动，截面就跟着一圈圈歪，轮廓成了锯齿。先把线抹顺。 */
    private static void settle(double[][] line) {
        for (int pass = 0; pass < 8; pass++) {
            double[][] old = new double[line.length][];
            for (int i = 0; i < line.length; i++) old[i] = line[i].clone();
            for (int i = 1; i < line.length - 1; i++) {
                for (int k = 0; k < 3; k++) line[i][k] = old[i][k] * 0.5 + (old[i - 1][k] + old[i + 1][k]) * 0.25;
            }
        }
    }

    /** 拐弯比自身半径还急的地方，一圈圈截面互相穿过去，边上就折出锯齿。把这种地方抹开。 */
    private static void relax(double[][] line) {
        for (int pass = 0; pass < 80; pass++) {
            boolean any = false;
            for (int i = 1; i < line.length - 1; i++) {
                double[] a = line[i - 1], b = line[i], c = line[i + 1];
                double la = dist(a, b), lb = dist(b, c);
                if (la < 1e-5 || lb < 1e-5) continue;
                double dot = ((b[0] - a[0]) * (c[0] - b[0]) + (b[1] - a[1]) * (c[1] - b[1]) + (b[2] - a[2]) * (c[2] - b[2])) / (la * lb);
                double angle = Math.acos(Math.max(-1, Math.min(1, dot)));
                if (angle > 1e-3 && (la + lb) * 0.5 / angle < b[3] * 1.8) {
                    for (int k = 0; k < 3; k++) b[k] += ((a[k] + c[k]) * 0.5 - b[k]) * 0.6;
                    any = true;
                }
            }
            if (!any) break;
        }
    }

    private Piece section(Parts.Rope rope, double[][] line, double[] along, double[][] e1, double[][] e2, int from, int to, int sides, int texture, double seed) {
        Piece p = new Piece();
        p.texture = texture;
        p.window = rope.window();
        p.sides = sides;
        p.rings = to - from + 1;
        int n = p.rings * (sides + 1);
        double minX = 1e18, minY = 1e18, minZ = 1e18, maxX = -1e18, maxY = -1e18, maxZ = -1e18;
        double[][] points = new double[n][3];
        double radiusSum = 0;
        for (int r = 0; r < p.rings; r++) {
            int i = from + r;
            double[] c = line[i];
            radiusSum += c[3];
            double s = along[i], end = along[along.length - 1];
            // 两头收成圆头
            double cap = Math.min(s, end - s) / Math.max(0.05, c[3] * 1.4);
            double round = cap >= 1 ? 1 : Math.sqrt(Math.max(0, 1 - (1 - cap) * (1 - cap)));
            double swell = 1 + 0.14 * Math.sin(s / Math.max(0.5, c[3] * 3.1) + seed) + 0.07 * Math.sin(s / Math.max(0.25, c[3] * 1.3) + seed * 2.3);
            for (int a = 0; a <= sides; a++) {
                double angle = Math.PI * 2 * (a % sides) / sides;
                double twist = s / Math.max(0.6, c[3] * 3.4);
                double bundles = 1 + 0.05 * Math.cos(2 * angle + twist * 0.6 + seed);
                double cos = Math.cos(angle), sin = Math.sin(angle);
                double px = e1[i][0] * cos + e2[i][0] * sin, py = e1[i][1] * cos + e2[i][1] * sin, pz = e1[i][2] * cos + e2[i][2] * sin;
                // 一团一团不规则的肉：按世界位置取噪声，粗肉索的鼓包也跟着大
                double f = 1.0 / Math.max(0.3, c[3] * 1.2);
                double lump = 1 + 0.42 * (Noise.fbm((c[0] + px * c[3]) * f + seed, (c[1] + py * c[3]) * f, (c[2] + pz * c[3]) * f, 2) - 0.5);
                double rr = c[3] * round * swell * bundles * lump;
                double[] q = points[r * (sides + 1) + a];
                q[0] = c[0] + px * rr;
                q[1] = c[1] + py * rr;
                q[2] = c[2] + pz * rr;
                minX = Math.min(minX, q[0]);
                minY = Math.min(minY, q[1]);
                minZ = Math.min(minZ, q[2]);
                maxX = Math.max(maxX, q[0]);
                maxY = Math.max(maxY, q[1]);
                maxZ = Math.max(maxZ, q[2]);
            }
        }
        p.radius = (float) (radiusSum / p.rings);
        p.ox = (minX + maxX) / 2;
        p.oy = minY;
        p.oz = (minZ + maxZ) / 2;
        p.sx = (float) (maxX - minX);
        p.sy = (float) (maxY - minY);
        p.sz = (float) (maxZ - minZ);
        p.pos = new float[n * 3];
        p.uv = new float[n * 2];
        p.normal = new byte[n * 3];
        p.wave = new byte[n * 2];
        double tile = Math.max(0.7, Math.min(2.4, p.radius * 2.6));
        double around = Math.max(1, Math.round(Math.PI * 2 * p.radius / tile));
        for (int r = 0; r < p.rings; r++) {
            int i = from + r;
            double s = along[i];
            double ampBase = Math.min(0.5, line[i][3] * 0.3);
            double end = along[along.length - 1];
            double fade = Math.min(1, Math.min(s, end - s) / Math.max(0.1, line[i][3] * 2));
            for (int a = 0; a <= sides; a++) {
                int v = r * (sides + 1) + a;
                double[] q = points[v];
                p.pos[v * 3] = (float) (q[0] - p.ox);
                p.pos[v * 3 + 1] = (float) (q[1] - p.oy);
                p.pos[v * 3 + 2] = (float) (q[2] - p.oz);
                p.uv[v * 2] = (float) (s / tile);
                p.uv[v * 2 + 1] = (float) (around * a / sides);
                double[] prev = points[Math.max(0, r - 1) * (sides + 1) + a], next = points[Math.min(p.rings - 1, r + 1) * (sides + 1) + a];
                int left = a == 0 ? sides - 1 : a - 1, right = a == sides ? 1 : a + 1;
                double[] l = points[r * (sides + 1) + left], rr = points[r * (sides + 1) + right];
                double[] m = normalize(cross(new double[]{next[0] - prev[0], next[1] - prev[1], next[2] - prev[2]}, new double[]{rr[0] - l[0], rr[1] - l[1], rr[2] - l[2]}));
                double[] c = line[i];
                if (m[0] * (q[0] - c[0]) + m[1] * (q[1] - c[1]) + m[2] * (q[2] - c[2]) < 0) m = new double[]{-m[0], -m[1], -m[2]};
                p.normal[v * 3] = (byte) (m[0] * 127);
                p.normal[v * 3 + 1] = (byte) (m[1] * 127);
                p.normal[v * 3 + 2] = (byte) (m[2] * 127);
                p.wave[v * 2] = (byte) (int) (((s * 0.42 + rope.id() * 1.7) / (Math.PI * 2) % 1) * 255);
                p.wave[v * 2 + 1] = (byte) (int) (ampBase * fade / 0.5 * 255);
            }
        }
        return p;
    }

    /** 向心 Catmull-Rom，每隔大约 0.4 个半径取一点。pts 每项 x、y、z、半径。 */
    private static double[][] spline(double[][] pts) {
        if (pts.length < 2) return new double[0][];
        List<double[]> out = new ArrayList<>();
        int n = pts.length;
        for (int i = 0; i < n - 1; i++) {
            double[] p0 = pts[Math.max(0, i - 1)], p1 = pts[i], p2 = pts[i + 1], p3 = pts[Math.min(n - 1, i + 2)];
            double len = dist(p1, p2);
            int steps = (int) Math.max(1, Math.ceil(len / Math.max(0.1, Math.min(1.6, (p1[3] + p2[3]) * 0.5 * 0.42))));
            for (int s = 0; s < steps; s++) {
                double t = (double) s / steps, t2 = t * t, t3 = t2 * t;
                double[] q = new double[4];
                for (int c = 0; c < 4; c++) {
                    q[c] = 0.5 * (2 * p1[c] + (p2[c] - p0[c]) * t + (2 * p0[c] - 5 * p1[c] + 4 * p2[c] - p3[c]) * t2 + (3 * p1[c] - p0[c] - 3 * p2[c] + p3[c]) * t3);
                }
                q[3] = Math.max(0.02, q[3]);
                out.add(q);
            }
        }
        out.add(pts[n - 1].clone());
        return out.toArray(new double[0][]);
    }

    private static double dist(double[] a, double[] b) {
        return Math.sqrt((a[0] - b[0]) * (a[0] - b[0]) + (a[1] - b[1]) * (a[1] - b[1]) + (a[2] - b[2]) * (a[2] - b[2]));
    }

    private static double[] tangent(double[][] line, int i) {
        double[] a = line[Math.max(0, i - 1)], b = line[Math.min(line.length - 1, i + 1)];
        return normalize(new double[]{b[0] - a[0], b[1] - a[1], b[2] - a[2]});
    }

    private static double[] cross(double[] a, double[] b) {
        return new double[]{a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]};
    }

    private static double[] normalize(double[] v) {
        double len = Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
        return len < 1e-9 ? new double[]{0, 1, 0} : new double[]{v[0] / len, v[1] / len, v[2] / len};
    }

    // ---------- 存盘 ----------

    public void writeBlob(DataOutputStream out) throws IOException {
        out.writeInt(pieces.size());
        for (Piece p : pieces) {
            out.writeInt(p.id);
            out.writeByte(p.texture);
            out.writeBoolean(p.window);
            out.writeByte(p.group);
            out.writeInt(p.sides);
            out.writeInt(p.rings);
            out.writeDouble(p.ox);
            out.writeDouble(p.oy);
            out.writeDouble(p.oz);
            out.writeFloat(p.radius);
            out.writeInt(p.pos.length);
            java.nio.ByteBuffer buffer = java.nio.ByteBuffer.allocate(p.pos.length * 4 + p.uv.length * 4);
            for (float f : p.pos) buffer.putFloat(f);
            for (float f : p.uv) buffer.putFloat(f);
            out.write(buffer.array());
            out.write(p.normal);
            out.write(p.wave);
        }
    }

    public String index() {
        StringBuilder b = new StringBuilder();
        for (Piece p : pieces) {
            b.append(p.id).append(' ').append(p.ox).append(' ').append(p.oy).append(' ').append(p.oz).append(' ').append(p.sx).append(' ').append(p.sy).append(' ').append(p.sz).append('\n');
        }
        return b.toString();
    }
}
