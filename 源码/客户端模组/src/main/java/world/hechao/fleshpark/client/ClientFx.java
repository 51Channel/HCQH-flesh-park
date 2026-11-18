package world.hechao.fleshpark.client;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;

/** 服务端只发事件，画面和声音都在这里演。 */
final class ClientFx {
    /** 肉自己透出来的光有多强：大空腔里 1，其它地方 0。 */
    static float ember;

    /** 黑场、温泉的暖光、呼气的病黄，都是 0 到 1。 */
    static float black, warm, sick;
    private static float blackTarget, blackStep, warmTarget, warmStep, sickTarget, sickStep;
    /** 夹车：中心和程度。窗外的肉往玻璃上顶的程度。心跳。 */
    static float squeeze, heave, pulse;
    static double squeezeX, squeezeY, squeezeZ;
    /** 麻醉科里它吸气：肉跟着往人这边收。用的是夹车那套形变，中心跟着人走。 */
    private static float breath, breathTarget, breathStep;
    private static boolean breathing;
    static long ticks;

    /** 发疯最后那几秒：视野往外撑开（倍数）、画面一层层变红。按真实时间插，每一帧都顺。 */
    private static float fovFrom = 1, fovTo = 1, redFrom, redTo;
    private static long fovAt, fovMs = 1, redAt, redMs = 1;

    static float fov() {
        return ramp(fovFrom, fovTo, fovAt, fovMs);
    }

    static float red() {
        return ramp(redFrom, redTo, redAt, redMs);
    }

    private static float ramp(float from, float to, long at, long ms) {
        float t = Math.max(0, Math.min(1, (System.currentTimeMillis() - at) / (float) ms));
        // 先慢后快：越撑越猛
        return from + (to - from) * t * t;
    }

    /** 现在这一段能看见哪些整块的肉：位 0 零碎的肉，1 缆车腔，2 森林，3 穹顶，4 腺泡。 */
    static int chambers = ~0;
    /** 1 大厅层，2 楼下，3 森林那一头。隔着楼板和岩层的零碎肉不画。 */
    static int storey;
    static boolean poolsNear = true;

    private ClientFx() {
    }

    private static void zone(String z) {
        switch (z) {
            case "station" -> chambers(0b01001, 0);
            case "out", "throat" -> chambers(0b01011, 0);
            case "cavity" -> chambers(0b00011, 0);
            case "dock", "hall" -> chambers(0b00011, 1);
            case "garden", "tunnel", "lockers", "pools" -> chambers(0b00001, 2);
            case "forest", "burrow" -> chambers(0b10101, 3);
            // 深井和井底那座楼：只画井的肉腔，和井里这一片的肉
            case "well", "real" -> chambers(0b100001, 4);
            default -> chambers(~0, 0);
        }
        poolsNear = z.equals("pools") || z.equals("lockers") || z.equals("tunnel") || z.equals("real") || z.equals("well");
    }

    private static void chambers(int mask, int level) {
        chambers = mask;
        storey = level;
    }

    static void handle(String line) {
        String[] a = line.split(" ");
        try {
            switch (a[0]) {
                case "audio" -> audio(a);
                case "black" -> {
                    blackTarget = f(a[1]);
                    blackStep = Math.abs(blackTarget - black) / Math.max(1, i(a[2]));
                }
                case "warm" -> {
                    warmTarget = f(a[1]);
                    warmStep = Math.abs(warmTarget - warm) / Math.max(1, i(a[2]));
                }
                case "sick" -> {
                    sickTarget = f(a[1]);
                    sickStep = Math.abs(sickTarget - sick) / Math.max(1, i(a[2]));
                }
                case "fog" -> ParkFog.target(f(a[1]), f(a[2]), f(a[3]), Integer.parseInt(a[4], 16), f(a[5]));
                case "deform" -> {
                    squeezeX = Double.parseDouble(a[1]);
                    squeezeY = Double.parseDouble(a[2]);
                    squeezeZ = Double.parseDouble(a[3]);
                    squeeze = f(a[4]);
                    heave = f(a[5]);
                }
                case "pulse" -> pulse = f(a[1]);
                case "fov" -> {
                    fovFrom = fov();
                    fovTo = f(a[1]);
                    fovAt = System.currentTimeMillis();
                    fovMs = Math.max(1, i(a[2]) * 50L);
                }
                case "red" -> {
                    redFrom = red();
                    redTo = f(a[1]);
                    redAt = System.currentTimeMillis();
                    redMs = Math.max(1, i(a[2]) * 50L);
                }
                case "breath" -> {
                    breathTarget = f(a[1]);
                    breathStep = Math.abs(breathTarget - breath) / Math.max(1, i(a[2]));
                    breathing = true;
                }
                case "wipe" -> MistRenderer.wipe(a[1]);
                case "ember" -> ember = f(a[1]);
                case "zone" -> zone(a[1]);
                case "sub" -> Overlay.subtitle(line.substring(4).split("\\|"));
                case "notice" -> Overlay.notice(line.substring(7).split("\\|"));
                case "stamp" -> Overlay.stamp(line.substring(6).split("\\|"));
                case "glitch" -> Overlay.glitch(Integer.parseInt(a[1]));
                case "jolt" -> Overlay.jolt();
                case "flash" -> Overlay.flash();
                case "cine" -> Overlay.cine(a.length > 1 && a[1].equals("on"));
                case "cam" -> Cam.set(line);
                case "camshake" -> Cam.shake = f(a[1]);
                case "helmet" -> Overlay.helmet(a.length > 1 ? a[1] : "off");
                case "doc" -> Overlay.doc(a.length > 1 ? a[1] : "off");
                case "intro" -> Overlay.intro(line.length() > 6 ? line.substring(6).split("\\|") : null);
                case "title" -> Overlay.title();
                case "report" -> Overlay.report(line.substring(7).split("\\|"));
                case "diary" -> Overlay.diary(line.substring(6).split("\\|"));
                case "sms" -> Phone.receive(line.substring(4).split("\\|", -1));
                case "tag" -> Tags.add(line.substring(4).split("\\|", -1));
                case "untag" -> Tags.remove(a[1]);
                case "taganchor" -> Tags.anchor(Double.parseDouble(a[1]), Double.parseDouble(a[2]), Double.parseDouble(a[3]));
                case "phone" -> Phone.command(a.length > 1 ? a[1] : "");
                // 测试用：看向车厢里相对缆车的一点
                case "aimcab" -> {
                    var player = Minecraft.getInstance().player;
                    if (player != null && player.getVehicle() != null) {
                        player.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, player.getVehicle().position().add(Double.parseDouble(a[1]), Double.parseDouble(a[2]), Double.parseDouble(a[3])));
                    }
                }
                case "cabin" -> Cabin.set(a);
                case "director" -> Minecraft.getInstance().setScreen(new DirectorScreen());
                case "closescreen" -> Minecraft.getInstance().setScreen(null);
                case "depth" -> Overlay.depth(a[1].equals("1"));
                case "endcard" -> Overlay.endCard();
                case "term" -> Overlay.term(line.length() > 5 ? line.substring(5) : "off");
                case "road" -> Road.show(a[1].equals("on"));
                case "burst" -> Road.burst(f(a[1]), f(a[2]), f(a[3]), Integer.parseInt(a[4], 16), f(a[5]));
                case "tape" -> Overlay.setTape(a.length < 2 || !a[1].equals("off"));
                case "tapelabel" -> Overlay.setTapeLabel(line.substring(a[0].length()).trim());
                case "snap" -> Snaps.take(Integer.parseInt(a[1]));
                case "photo" -> Snaps.photo(Integer.parseInt(a[1]));
                case "shaderopt" -> shaderOptions(line.substring(a[0].length()).trim());
                case "meshstat" -> meshStat = a.length < 2 || !a[1].equals("off");
                case "shadowcull" -> ShadowPass.cull = a.length < 2 || !a[1].equals("off");
                case "figlod" -> FigureRenderer.coarse = a.length < 2 || !a[1].equals("off");
                case "quadcull" -> MeshRenderer.quadCull = a.length < 2 || !a[1].equals("off");
                case "lean" -> LeanVertices.on = a.length < 2 || !a[1].equals("off");
                case "clear" -> reset();
                default -> System.err.println("[血肉公园] 不认识的指令：" + line);
            }
        } catch (RuntimeException error) {
            System.err.println("[血肉公园] 指令有误：" + line + "：" + error);
        }
    }

    private static void audio(String[] a) {
        switch (a[1]) {
            case "play" -> Audio.play(a[2], a[3], f(a[4]), f(a[5]), a[6].equals("1"), i(a[7]));
            case "gain" -> Audio.gain(a[2], f(a[3]), i(a[4]));
            case "pitch" -> Audio.pitch(a[2], f(a[3]), i(a[4]));
            case "stopall" -> Audio.stopAll();
            case "stat" -> Audio.status();
            case "slider" -> Audio.slider(a[2], Double.parseDouble(a[3]));
            default -> System.err.println("[血肉公园] 不认识的声音指令：" + a[1]);
        }
    }

    /** 光影开着吗。开着的话肉上的高光由光影算，服务端也不用摆手电的光方块。 */
    static boolean shaderOn;
    private static int helloIn;
    private static boolean told, toldValue;

    /** 测帧数用：每五秒把肉每帧发了多少个面写进日志。 */
    private static boolean meshStat;
    private static long meshStatAt, meshStatFrames;

    /** 每帧调一次。 */
    static void meshFrame() {
        if (!meshStat) return;
        meshStatFrames++;
        long now = System.currentTimeMillis();
        if (meshStatAt == 0) meshStatAt = now;
        if (now - meshStatAt < 5000) return;
        long frames = Math.max(1, meshStatFrames);
        org.slf4j.LoggerFactory.getLogger("fleshpark").info("肉 每帧 " + MeshRenderer.mainQuads / frames + " 面，阴影 " + MeshRenderer.shadowQuads / frames + " 面，人形 " + MeshRenderer.figureQuads / frames + " 面，" + frames * 1000 / (now - meshStatAt) + " 帧/秒");
        MeshRenderer.mainQuads = MeshRenderer.shadowQuads = MeshRenderer.figureQuads = 0;
        meshStatFrames = 0;
        meshStatAt = now;
    }

    private static java.lang.reflect.Method shadowQuery;
    private static Object irisApi;
    private static boolean noIris;

    /**
     * 测帧数用：把光影选项整个换成这几项（空格隔开的 名字=值，什么都不写就是全用默认），再让光影重新载入。
     * 只给测试和导演用，正常游玩用不到。
     */
    private static void shaderOptions(String options) {
        try {
            java.nio.file.Path file = Minecraft.getInstance().gameDirectory.toPath().resolve("shaderpacks").resolve("FleshPark.zip.txt");
            java.nio.file.Files.writeString(file, String.join("\n", options.isEmpty() ? new String[0] : options.split(" ")) + "\n");
            Class.forName("net.irisshaders.iris.Iris").getMethod("reload").invoke(null);
            org.slf4j.LoggerFactory.getLogger("fleshpark").info("光影选项 " + (options.isEmpty() ? "默认" : options));
        } catch (Exception | LinkageError e) {
            org.slf4j.LoggerFactory.getLogger("fleshpark").warn("光影选项没换成：" + e);
        }
    }

    static boolean shadowPass() {
        if (noIris) return false;
        try {
            if (shadowQuery == null) {
                Class<?> api = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
                irisApi = api.getMethod("getInstance").invoke(null);
                shadowQuery = api.getMethod("isRenderingShadowPass");
            }
            return (boolean) shadowQuery.invoke(irisApi);
        } catch (ReflectiveOperationException | LinkageError missing) {
            noIris = true;
            return false;
        }
    }

    /** 只有我们自己的光影才认得顶点色里的记号和饱食度里的手电。 */
    private static boolean ownPackInUse() {
        try {
            Class<?> api = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
            Object instance = api.getMethod("getInstance").invoke(null);
            if (!(boolean) api.getMethod("isShaderPackInUse").invoke(instance)) return false;
            Object name = Class.forName("net.irisshaders.iris.Iris").getMethod("getCurrentPackName").invoke(null);
            return name != null && name.toString().startsWith("FleshPark");
        } catch (ReflectiveOperationException | LinkageError noIris) {
            return false;
        }
    }

    static void tick(Minecraft client) {
        Audio.tick();
        ticks++;
        Phone.tick();
        DirectorScreen.tick(client);
        MeshStore.relight(client, ticks);
        if (client.level == null) {
            told = false;
        } else if (--helloIn <= 0) {
            helloIn = 20;
            shaderOn = ownPackInUse();
            if ((!told || toldValue != shaderOn) && ClientPlayNetworking.canSend(HelloPayload.TYPE)) {
                ClientPlayNetworking.send(new HelloPayload(shaderOn));
                told = true;
                toldValue = shaderOn;
            }
        }
        black = approach(black, blackTarget, blackStep);
        warm = approach(warm, warmTarget, warmStep);
        sick = approach(sick, sickTarget, sickStep);
        if (breathing) {
            breath = approach(breath, breathTarget, breathStep);
            if (client.player != null) {
                squeezeX = client.player.getX();
                squeezeY = client.player.getEyeY();
                squeezeZ = client.player.getZ();
            }
            squeeze = breath;
            if (breath <= 0 && breathTarget <= 0) {
                breathing = false;
                squeeze = 0;
            }
        }
    }

    /** 重新进服、回到开局前：所有演出都清掉。 */
    static void reset() {
        black = blackTarget = warm = warmTarget = sick = sickTarget = 0;
        fovFrom = fovTo = 1;
        redFrom = redTo = 0;
        if (breathing) squeeze = 0;
        breath = breathTarget = 0;
        breathing = false;
        chambers(~0, 0);
        poolsNear = true;
        Cabin.reset();
        Cam.reset();
        Road.show(false);
        Audio.stopAll();
        Overlay.clear();
        ParkFog.reset();
        Snaps.reset();
    }

    private static float approach(float value, float target, float step) {
        if (step <= 0) return target;
        return value < target ? Math.min(target, value + step) : Math.max(target, value - step);
    }

    private static float f(String s) {
        return Float.parseFloat(s);
    }

    private static int i(String s) {
        return Integer.parseInt(s);
    }
}
