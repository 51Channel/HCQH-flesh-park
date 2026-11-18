package world.hechao.fleshpark.core;

import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** 构建时跑：算出肉的网格和深井的钢结构，网格放进客户端模组，锚点表放进插件。用法：MeshBaker <肉网格> <肉锚点表> <钢结构网格> <钢结构锚点表> */
public final class MeshBaker {
    private MeshBaker() {
    }

    public static void main(String[] args) throws IOException {
        long start = System.currentTimeMillis();
        ParkPlan plan = new ParkPlan();
        FleshMesh mesh = new FleshMesh(plan);
        Path blob = Path.of(args[0]), index = Path.of(args[1]);
        Files.createDirectories(blob.getParent());
        Files.createDirectories(index.getParent());
        try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(blob)))) {
            mesh.writeBlob(out);
        }
        Files.writeString(index, mesh.index(), StandardCharsets.UTF_8);
        mesh.writeObj(index.resolveSibling("肉腔.obj"));
        long walls = 0, tubes = 0;
        for (FleshMesh.Piece p : mesh.pieces) {
            if (p.sides == 0) walls += p.pos.length / 12;
            else tubes += (long) (p.rings - 1) * p.sides;
        }
        System.out.printf("肉的网格：%d 段，肉壁 %d 个面，肉索 %d 个面，文件 %.1f MB，用了 %d 毫秒%n", mesh.pieces.size(), walls, tubes, Files.size(blob) / 1048576.0, System.currentTimeMillis() - start);
        // 深井的钢结构和残骸：网格进客户端模组，锚点表进插件，再存一份 OBJ
        if (args.length >= 4) {
            Rig rig = new Rig();
            Path steel = Path.of(args[2]), steelIndex = Path.of(args[3]);
            Files.createDirectories(steel.getParent());
            try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(steel)))) {
                rig.writeBlob(out);
            }
            Files.writeString(steelIndex, rig.index(), StandardCharsets.UTF_8);
            rig.writeObj(steelIndex.resolveSibling("钢结构.obj"));
            int quads = 0;
            for (Rig.Piece p : rig.pieces) for (int m = 0; m < Rig.MATERIALS.length; m++) quads += p.count(m);
            System.out.printf("钢结构：%d 段，%d 个面，文件 %.2f MB%n", rig.pieces.size(), quads, Files.size(steel) / 1048576.0);
        }
    }
}
