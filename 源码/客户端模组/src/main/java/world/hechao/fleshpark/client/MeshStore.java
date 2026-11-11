package world.hechao.fleshpark.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * 肉的网格：构建时算好放在模组里，这里读进内存。
 * 光不能每帧每个顶点都查，所以按面或按圈存一份，隔几帧更新一点，灯灭了肉也跟着暗下去。
 */
final class MeshStore {
    static final class Piece {
        private float reach = -1;

        /** 从中心到最远一点的距离：算远近时用。 */
        float reach() {
            if (reach < 0) {
                float best = 0;
                for (int i = 0; i + 2 < pos.length; i += 3) best = Math.max(best, pos[i] * pos[i] + pos[i + 1] * pos[i + 1] + pos[i + 2] * pos[i + 2]);
                reach = (float) Math.sqrt(best);
            }
            return reach;
        }

        int id, texture, sides, rings;
        boolean window;
        int group;
        double ox, oy, oz;
        float radius;
        float[] pos, uv;
        byte[] normal, wave;
        /** 肉壁每个面一个，肉索每圈一个。 */
        int[] light;
        int cursor;
        boolean lit;
        long drawnAt;
    }

    private static volatile Piece[] pieces;
    private static final List<Piece> ACTIVE = new ArrayList<>();
    private static final int BUDGET_PER_TICK = 14000;

    private MeshStore() {
    }

    static void loadInBackground() {
        Thread thread = new Thread(MeshStore::load, "血肉公园网格");
        thread.setDaemon(true);
        thread.start();
    }

    private static void load() {
        try (InputStream raw = MeshStore.class.getResourceAsStream("/assets/fleshpark/mesh/flesh.bin")) {
            if (raw == null) {
                System.err.println("[血肉公园] 模组里没有网格");
                return;
            }
            DataInputStream in = new DataInputStream(new BufferedInputStream(raw, 1 << 20));
            int count = in.readInt();
            Piece[] all = new Piece[count];
            for (int i = 0; i < count; i++) {
                Piece p = new Piece();
                p.id = in.readInt();
                p.texture = in.readByte();
                p.window = in.readBoolean();
                p.group = in.readByte();
                p.sides = in.readInt();
                p.rings = in.readInt();
                p.ox = in.readDouble();
                p.oy = in.readDouble();
                p.oz = in.readDouble();
                p.radius = in.readFloat();
                int n = in.readInt();
                byte[] floats = new byte[n * 4 + n / 3 * 2 * 4];
                in.readFully(floats);
                ByteBuffer buffer = ByteBuffer.wrap(floats);
                p.pos = new float[n];
                p.uv = new float[n / 3 * 2];
                buffer.asFloatBuffer().get(p.pos);
                buffer.position(n * 4);
                buffer.asFloatBuffer().get(p.uv);
                p.normal = new byte[n];
                in.readFully(p.normal);
                p.wave = new byte[n / 3 * 2];
                in.readFully(p.wave);
                p.light = new int[p.sides == 0 ? n / 12 : p.rings];
                all[p.id] = p;
            }
            pieces = all;
            System.out.println("[血肉公园] 网格读好了：" + count + " 段");
        } catch (IOException | RuntimeException error) {
            System.err.println("[血肉公园] 读不了网格：" + error);
        }
    }

    static Piece get(int id) {
        Piece[] all = pieces;
        return all == null || id < 0 || id >= all.length ? null : all[id];
    }

    static void drawn(Piece p, long frame, int fallback) {
        if (!p.lit) {
            p.lit = true;
            java.util.Arrays.fill(p.light, fallback);
            synchronized (ACTIVE) {
                ACTIVE.add(p);
            }
        }
        if (p.drawnAt == 0) {
            synchronized (ACTIVE) {
                if (!ACTIVE.contains(p)) ACTIVE.add(p);
            }
        }
        p.drawnAt = frame;
    }

    /** 每 tick 更新一点。 */
    static void relight(Minecraft client, long frame) {
        ClientLevel level = client.level;
        if (level == null) return;
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        synchronized (ACTIVE) {
            int budget = BUDGET_PER_TICK;
            for (Iterator<Piece> it = ACTIVE.iterator(); it.hasNext() && budget > 0; ) {
                Piece p = it.next();
                if (frame - p.drawnAt > 60) {
                    p.drawnAt = 0;
                    it.remove();
                    continue;
                }
                int work = Math.min(budget, Math.max(300, p.light.length / 8));
                budget -= work;
                for (int i = 0; i < work; i++) {
                    int k = p.cursor;
                    p.cursor = (p.cursor + 1) % p.light.length;
                    int v = p.sides == 0 ? k * 4 : k * (p.sides + 1) + p.sides / 4;
                    double nudge = p.sides == 0 ? 1.1 : 0;
                    double x = p.ox + p.pos[v * 3] + p.normal[v * 3] / 127.0 * (p.sides == 0 ? nudge : p.radius + 0.9);
                    double y = p.oy + p.pos[v * 3 + 1] + p.normal[v * 3 + 1] / 127.0 * (p.sides == 0 ? nudge : p.radius + 0.9);
                    double z = p.oz + p.pos[v * 3 + 2] + p.normal[v * 3 + 2] / 127.0 * (p.sides == 0 ? nudge : p.radius + 0.9);
                    at.set(x, y, z);
                    p.light[k] = LevelRenderer.getLightColor(level, at);
                }
            }
        }
    }
}
