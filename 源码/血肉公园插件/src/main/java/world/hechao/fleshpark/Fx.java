package world.hechao.fleshpark;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.nio.charset.StandardCharsets;

/** 发给客户端模组的演出指令。画面和配音都在客户端演，这里只说什么时候演什么。 */
final class Fx {
    static final String CHANNEL = "fleshpark:fx";
    private final Plugin plugin;

    Fx(Plugin plugin) {
        this.plugin = plugin;
    }

    void send(Player player, String line) {
        player.sendPluginMessage(plugin, CHANNEL, line.getBytes(StandardCharsets.UTF_8));
    }

    void play(Player player, String channel, String clip, float gain, float pitch, boolean loop, int fade) {
        send(player, "audio play " + channel + " " + clip + " " + gain + " " + pitch + " " + (loop ? 1 : 0) + " " + fade);
    }

    /** 广播和电台念的每一句，画面上都要有字幕。 */
    private static final java.util.Map<String, String> LINES = java.util.Map.ofEntries(
            java.util.Map.entry("pa_thanks", "感谢您的评价！安诺丹大家庭祝您下行愉快。"),
            java.util.Map.entry("pa_thanks2", "感谢您的评价。"),
            java.util.Map.entry("pa_improve", "我们会改进。"),
            java.util.Map.entry("pa_recorded", "已记录。"),
            java.util.Map.entry("drv_board", "体验官？上车吧。坐前面，后面的座位没擦。"),
            java.util.Map.entry("drv_wait", "外面冷。上来吧。"),
            java.util.Map.entry("drv_1", "坐稳了。还有十来分钟就到。"),
            java.util.Map.entry("drv_2", "任务书在你旁边座位上。公司让我确认你看过。"),
            java.util.Map.entry("drv_tv", "这片子是公园当年拍的。车上就剩这一盘带子。"),
            java.util.Map.entry("drv_off", "别听了。都是那一套。"),
            java.util.Map.entry("stop_call", "已为您呼叫接驳车。请在黄线后等候。"),
            java.util.Map.entry("guide_1", "欢迎乘坐安诺丹缆车。本次缆车下行至下游客中心，全程约六分钟。请扶好扶手，请勿将手伸出窗外。"),
            java.util.Map.entry("guide_2", "1970 年代，牧场工人在这片洼地里发现了一个温热的洞口。1976 年，安诺丹公司把它带给了全世界。"),
            java.util.Map.entry("guide_3", "前方就是息壤的入口。入口由扩张锚撑开，每天检修两次，请您放心。"),
            java.util.Map.entry("guide_4", "您现在来到的，是息壤的中央腔。下游客中心由六根支撑悬挂在腔顶，是世界上唯一一座建在有机体内部的游客中心。"),
            java.util.Map.entry("guide_5", "右手边是本线路的拍照点。请看向窗外，微笑。纪念照可以在 3 号码头领取。"),
            java.util.Map.entry("guide_6", "缆车即将到站。感谢您乘坐安诺丹缆车。安诺丹大家庭祝您在息壤度过难忘的一天。"),
            java.util.Map.entry("r1", "八一三，门关不上就先走吧，老毛病了。上面还排着三百多号人呢。"),
            java.util.Map.entry("r2", "行。门缝里夹着块肉，拔不下来，回来再说。"),
            java.util.Map.entry("r3", "调度，管子今天收得有点紧啊，一直刮车皮。药是不是还没打？"),
            java.util.Map.entry("r4", "运药车让雨堵路上了。没事，开快点就过去了。"),
            java.util.Map.entry("r5", "停了。……灯也灭了。车顶上有动静，咚，咚的。"),
            java.util.Map.entry("r6", "八一三？八一三回话。周敏？"),
            java.util.Map.entry("r7", "八一三。一切正常。我们会改进。"),
            java.util.Map.entry("r8", "周敏？你车上现在几个人？"),
            java.util.Map.entry("r9", "三十二。"),
            java.util.Map.entry("r10", "八一三进站了。车门开着。……车上没人。人呢？"),
            java.util.Map.entry("promo_1", "欢迎来到息壤国家公园。"),
            java.util.Map.entry("promo_2", "在西得克萨斯的地底下，有一个活着的奇观。它比山更老，比城市更大。"),
            java.util.Map.entry("promo_3", "乘坐安诺丹缆车，一路下到它的身体里。"),
            java.util.Map.entry("promo_4", "下游客中心、羊膜温泉、支气管森林。每一处，都在呼吸。"),
            java.util.Map.entry("promo_5", "您的每一次评价，都会让它变得更好。"),
            java.util.Map.entry("promo_6", "七月四日，独立日烟花晚会。全家一起，不见不散。"),
            java.util.Map.entry("news_1", "本台消息。二叠纪公司今天表示，息壤国家公园将启动重新开放评估。"),
            java.util.Map.entry("news_2", "2007 年 7 月 4 日晚，公园下游客中心垮塌，超过七百五十人遇难。公园关闭至今。"),
            java.util.Map.entry("news_3", "当年的事故报告称，下游客中心是整栋塌下去的。塌下去的地方，至今没有人下去过。"),
            java.util.Map.entry("news_4", "入口周边三公里仍为禁区。二叠纪公司称，评估由公司内部人员完成，目前评分良好。"),
            java.util.Map.entry("drv_5", "回程的单子我这儿没有。到时候你问他们吧。"),
            java.util.Map.entry("drv_6", "到了。我只送到落客区，车不进站，公司规定。"),
            java.util.Map.entry("drv_9", "下车吧。门开着。"),
            java.util.Map.entry("drv_7", "下车吧。我还得赶回去。"),
            java.util.Map.entry("drv_8", "祝您体验愉快。公司让我这么说的。"),
            java.util.Map.entry("pa_board", "请走进八一三号缆车。"),
            java.util.Map.entry("pa_open", "欢迎光临息壤国家公园。今日客流适中，预计排队时间，一百二十分钟。"),
            java.util.Map.entry("pa_count", "感谢光临。您是今日第一千二百四十二位游客。"),
            java.util.Map.entry("pa_depart", "缆车即将发车。请勿倚靠车门。"),
            java.util.Map.entry("pa_next_tram", "下一班缆车将于二十分钟后到达。"),
            java.util.Map.entry("pa_welcome", "欢迎来到下游客中心。"),
            java.util.Map.entry("pa_hold", "缆车临时停车。请勿离开车厢。"),
            java.util.Map.entry("pa_stopped", "缆车已停运。请前往休闲花园层等候。我们会改进。"),
            java.util.Map.entry("pa_locker", "请将个人物品存入柜内。"),
            java.util.Map.entry("pa_pool1", "请先咨询您的医生。"),
            java.util.Map.entry("pa_pool2", "请确认您的信仰能够承受。"),
            java.util.Map.entry("pa_pool3", "从此处起，园方不再负责。"),
            java.util.Map.entry("pa_special", "特别体验。未开发腺泡，请自行爬入。"),
            java.util.Map.entry("pa_rate", "请为本次体验评分。"),
            java.util.Map.entry("pa_sure", "您确定吗？"),
            java.util.Map.entry("pa_reconsider", "请重新考虑。"),
            java.util.Map.entry("pa_window", "感谢您的评价。现在为您开放观景窗。"));

    /** 字幕停多久按这句的长短给（tick），旁白用：念完就收，不拖到下一个镜头。 */
    void subtitle(Player player, String who, String clip, int ticks) {
        String text = LINES.get(clip);
        if (text != null) send(player, "sub " + who + "|" + text + "|" + ticks);
    }

    void subtitle(Player player, String who, String clip) {
        String text = LINES.get(clip);
        if (text != null) send(player, "sub " + who + "|" + text + "|" + (50 + text.length() * 6));
    }

    /** 模拟恐怖的公告卡：一个标题，几行字。 */
    /** 手机短信：发件人、时间（空着就是现在）、正文。正文里不能有竖线。 */
    void sms(Player player, String from, String time, String body) {
        send(player, "sms " + from + "|" + time + "|" + body.replace('|', '/'));
    }

    /** 园区短信：现在跑这套系统的是它，它以为今天还是 2007 年 7 月 4 日，时刻照着真实的走。 */
    void parkSms(Player player, String body) {
        java.time.LocalTime t = java.time.LocalTime.now();
        sms(player, "息壤国家公园", String.format("07-04 %02d:%02d", t.getHour(), t.getMinute()), body);
    }

    void notice(Player player, String title, String... lines) {
        send(player, "notice " + title + "|" + String.join("|", lines));
    }

    void pa(Player player, String clip) {
        subtitle(player, "广播", clip);
        play(player, "voice", clip, 1f, 1f, false, 0);
    }

    void gain(Player player, String channel, float gain, int ticks) {
        send(player, "audio gain " + channel + " " + gain + " " + ticks);
    }

    void pitch(Player player, String channel, float pitch, int ticks) {
        send(player, "audio pitch " + channel + " " + pitch + " " + ticks);
    }

    void black(Player player, float alpha, int ticks) {
        send(player, "black " + alpha + " " + ticks);
    }

    void fog(Player player, float start, float end, float shade, String rgb, float mix) {
        send(player, "fog " + start + " " + end + " " + shade + " " + rgb + " " + mix);
    }
}
