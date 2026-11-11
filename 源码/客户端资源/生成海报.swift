// 生成公园里的海报和招牌贴图。用法：swift 生成海报.swift <输出目录>
// 写到 输出目录/assets/fleshpark/textures/item/poster_*.png。全部是自己画的平面图形，字用系统字体。
import AppKit

let root = CommandLine.arguments[1] + "/assets/fleshpark/textures/item/"
try! FileManager.default.createDirectory(atPath: root, withIntermediateDirectories: true)

func color(_ hex: Int) -> NSColor {
    NSColor(srgbRed: CGFloat(hex >> 16 & 255) / 255, green: CGFloat(hex >> 8 & 255) / 255, blue: CGFloat(hex & 255) / 255, alpha: 1)
}

func text(_ s: String, _ x: CGFloat, _ y: CGFloat, _ size: CGFloat, _ hex: Int, bold: Bool = true, center: CGFloat? = nil) {
    let font = bold ? NSFont(name: "Helvetica-Bold", size: size) ?? NSFont.boldSystemFont(ofSize: size) : NSFont(name: "Helvetica", size: size) ?? NSFont.systemFont(ofSize: size)
    let attrs: [NSAttributedString.Key: Any] = [.font: font, .foregroundColor: color(hex)]
    let string = NSAttributedString(string: s, attributes: attrs)
    let width = string.size().width
    string.draw(at: NSPoint(x: center != nil ? center! - width / 2 : x, y: y))
}

func rect(_ x: CGFloat, _ y: CGFloat, _ w: CGFloat, _ h: CGFloat, _ hex: Int) {
    color(hex).setFill()
    NSRect(x: x, y: y, width: w, height: h).fill()
}

func oval(_ x: CGFloat, _ y: CGFloat, _ w: CGFloat, _ h: CGFloat, _ hex: Int) {
    color(hex).setFill()
    NSBezierPath(ovalIn: NSRect(x: x, y: y, width: w, height: h)).fill()
}

func poly(_ points: [(CGFloat, CGFloat)], _ hex: Int) {
    color(hex).setFill()
    let p = NSBezierPath()
    p.move(to: NSPoint(x: points[0].0, y: points[0].1))
    for q in points.dropFirst() { p.line(to: NSPoint(x: q.0, y: q.1)) }
    p.close()
    p.fill()
}

func save(_ name: String, _ w: Int, _ h: Int, _ draw: () -> Void) {
    let rep = NSBitmapImageRep(bitmapDataPlanes: nil, pixelsWide: w, pixelsHigh: h, bitsPerSample: 8, samplesPerPixel: 4, hasAlpha: true, isPlanar: false, colorSpaceName: .deviceRGB, bytesPerRow: 0, bitsPerPixel: 0)!
    NSGraphicsContext.saveGraphicsState()
    NSGraphicsContext.current = NSGraphicsContext(bitmapImageRep: rep)
    draw()
    NSGraphicsContext.restoreGraphicsState()
    try! rep.representation(using: .png, properties: [:])!.write(to: URL(fileURLWithPath: root + name + ".png"))
}

// 公园旅游海报：老式国家公园海报。黄昏、平顶山、坑里一个发光的肉口，缆车顺着钢缆下到口里，坑边站着几个小人，看得出有多大
func gradient(_ x: CGFloat, _ y: CGFloat, _ w: CGFloat, _ h: CGFloat, _ bottom: Int, _ top: Int) {
    NSGradient(starting: color(bottom), ending: color(top))!.draw(in: NSRect(x: x, y: y, width: w, height: h), angle: 90)
}
func line(_ a: (CGFloat, CGFloat), _ b: (CGFloat, CGFloat), _ width: CGFloat, _ hex: Int) {
    color(hex).setStroke()
    let p = NSBezierPath()
    p.move(to: NSPoint(x: a.0, y: a.1))
    p.line(to: NSPoint(x: b.0, y: b.1))
    p.lineWidth = width
    p.stroke()
}
save("poster_park", 320, 480) {
    gradient(0, 170, 320, 240, 0xF2A04E, 0x3E2C58)
    oval(92, 196, 136, 136, 0xFFD98A)
    poly([(0, 196), (30, 236), (78, 236), (96, 214), (150, 222), (170, 250), (226, 250), (246, 226), (320, 232), (320, 170), (0, 170)], 0xB4553A)
    poly([(0, 170), (0, 206), (40, 206), (58, 190), (110, 196), (128, 178), (320, 186), (320, 170)], 0x7E3324)
    rect(0, 44, 320, 130, 0x9C4A2E)
    // 坑：一圈石头，里面是暗的，正中一张发光的肉口，往外一道道褶子
    oval(36, 78, 248, 86, 0x6A2A1C)
    oval(54, 86, 212, 66, 0x3A120E)
    oval(98, 100, 124, 38, 0xE8634A)
    oval(110, 104, 100, 30, 0xC23A36)
    oval(126, 109, 68, 20, 0x8A1C1C)
    oval(144, 114, 32, 9, 0x2A0505)
    for k in 0..<14 {
        let a = CGFloat(k) / 14 * 2 * .pi
        line((160 + cos(a) * 36, 119 + sin(a) * 11), (160 + cos(a) * 60, 119 + sin(a) * 19), 2, 0x7A1414)
    }
    // 钢缆和缆车
    line((320, 352), (168, 122), 2.2, 0x2A2420)
    line((320, 344), (172, 120), 1.2, 0x2A2420)
    line((262, 266), (262, 248), 1.6, 0x2A2420)
    rect(244, 224, 38, 26, 0xF2E6D0)
    rect(244, 224, 38, 5, 0xC8342E)
    for wx in [248, 260, 272] { rect(CGFloat(wx), 234, 8, 10, 0x22383A) }
    // 坑边的小人
    for (px, h) in [(70, 14), (80, 12), (88, 15), (236, 13)] as [(CGFloat, CGFloat)] {
        rect(px, 150, 4, h, 0x2A1A14)
        oval(px - 1, 150 + h, 6, 6, 0x2A1A14)
    }
    // 上下两条字
    rect(0, 404, 320, 76, 0x1F3A3A)
    text("息壤国家公园", 0, 432, 36, 0xF4E6C4, center: 160)
    text("西得克萨斯  ·  二叠纪盆地", 0, 410, 15, 0xE8B46A, bold: false, center: 160)
    rect(0, 0, 320, 44, 0x1F3A3A)
    text("乘缆车，下到地底！", 0, 16, 20, 0xF4E6C4, center: 160)
    text("安诺丹家族  ·  1976 年开放", 0, 3, 11, 0xBFD8D0, bold: false, center: 160)
}

// T 恤广告
save("poster_survived", 320, 480) {
    rect(0, 0, 320, 480, 0x1F6A6A)
    poly([(80, 340), (120, 370), (200, 370), (240, 340), (290, 290), (250, 250), (225, 275), (225, 110), (95, 110), (95, 275), (70, 250), (30, 290)], 0xF2EEE2)
    oval(124, 180, 72, 44, 0xB02A2A)
    oval(138, 190, 44, 22, 0x3A0C0C)
    text("我从息壤", 0, 248, 22, 0x2A2418, center: 160)
    text("活着回来了", 0, 224, 20, 0x2A2418, center: 160)
    text("公园官方商品", 0, 60, 20, 0xF4E6C4, center: 160)
    text("礼品店  ·  下层游客中心", 0, 34, 14, 0xBFD8D0, bold: false, center: 160)
    rect(0, 410, 320, 70, 0xD8A24A)
    text("安诺丹", 0, 430, 30, 0x2A2418, center: 160)
}

// 心动可乐
save("poster_heartthrob", 320, 480) {
    rect(0, 0, 320, 480, 0xA81E2A)
    oval(80, 300, 160, 140, 0xC8323C)
    rect(115, 90, 90, 210, 0xF0E8E0)
    oval(115, 285, 90, 30, 0xD8D0C8)
    oval(115, 75, 90, 30, 0xF0E8E0)
    rect(115, 150, 90, 70, 0xA81E2A)
    text("心动", 0, 192, 19, 0xF4E6C4, center: 160)
    text("可乐", 0, 170, 19, 0xF4E6C4, center: 160)
    text("心动可乐", 0, 40, 30, 0xF4E6C4, center: 160)
    text("真正的压载液酿造", 0, 16, 15, 0xF4D0C0, bold: false, center: 160)
}

// 封园告示
save("poster_closed", 320, 400) {
    rect(0, 0, 320, 400, 0xE6E0D0)
    rect(0, 340, 320, 60, 0xA02818)
    text("已关闭", 0, 352, 38, 0xF6F0E2, center: 160)
    text("本园已永久关闭", 0, 296, 30, 0x2A2418, center: 160)
    text("奉美国地质生物资源", 0, 250, 15, 0x2A2418, center: 160)
    text("与公共安全委员会之命", 0, 230, 15, 0x2A2418, center: 160)
    text("2007 年 7 月 5 日", 0, 196, 20, 0x2A2418, center: 160)
    rect(30, 170, 260, 2, 0x2A2418)
    text("禁止进入", 0, 128, 30, 0xA02818, center: 160)
    text("非经授权人员请勿靠近入口", 0, 86, 19, 0x2A2418, center: 160)
    text("擅自进入者", 0, 44, 15, 0x4A4438, center: 160)
    text("须自行承担搜救责任", 0, 24, 15, 0x4A4438, center: 160)
}

// 公园地图
save("poster_map", 480, 360) {
    rect(0, 0, 480, 360, 0xE0D4B4)
    rect(0, 316, 480, 44, 0x2C3A3A)
    text("息壤国家公园  ·  游览地图", 0, 327, 20, 0xF4E6C4, center: 240)
    let levels: [(String, Int)] = [("缆车车站", 0x5A6E6E), ("入口管道", 0xA84A3A), ("三号缆车码头", 0x5A6E6E), ("餐饮购物大厅", 0xC88A3A), ("休闲花园层", 0x6A8A4A), ("羊膜温泉", 0xD09A4A), ("支气管森林", 0x8A3A4A)]
    var y: CGFloat = 268
    for (i, level) in levels.enumerated() {
        rect(60, y, 26, 26, level.1)
        text(level.0, 100, y + 3, 18, 0x2A2418)
        if i < levels.count - 1 { rect(71, y - 16, 4, 16, 0x2A2418) }
        y -= 42
    }
    rect(330, 138, 16, 16, 0xD72A1A)
    text("您在这里", 352, 136, 14, 0xD72A1A)
}

// 重开横幅
save("poster_reopen", 1024, 256) {
    rect(0, 0, 1024, 256, 0xC8282A)
    rect(0, 0, 1024, 18, 0xF2D060)
    rect(0, 238, 1024, 18, 0xF2D060)
    text("盛大重开", 0, 92, 112, 0xFFF4DC, center: 512)
    text("全园开放  ·  安诺丹大家庭欢迎您回来", 0, 40, 36, 0xF2D060, center: 512)
}

// 新海报：重开以后的
save("poster_new", 320, 480) {
    rect(0, 0, 320, 480, 0xF4B83A)
    oval(50, 200, 220, 220, 0xFFE28A)
    oval(80, 120, 160, 90, 0xD8342E)
    oval(120, 140, 80, 44, 0x4A0E0E)
    rect(0, 0, 320, 110, 0x1F6A6A)
    text("我们听到了。", 0, 62, 34, 0xFFF4DC, center: 160)
    text("我们改进了。", 0, 20, 34, 0xFFE28A, center: 160)
    text("息壤", 0, 432, 42, 0x7A1E1E, center: 160)
}

// 售票机屏幕
save("poster_ticket", 256, 192) {
    rect(0, 0, 256, 192, 0x12262A)
    rect(0, 150, 256, 42, 0x1F6A6A)
    text("售票处", 0, 158, 24, 0xF4E6C4, center: 128)
    text("下行  ·  成人  $12", 0, 110, 18, 0x8AE0C0, center: 128)
    text("暂停服务", 0, 62, 28, 0xE04030, center: 128)
    text("自 2007 年起", 0, 34, 15, 0x8AE0C0, center: 128)
}
// 2007 年独立日的一日游门票：温泉里那排人形胸前挂的。它照着游客长的时候，连门票一起长了出来
save("poster_badge", 96, 140) {
    rect(0, 0, 96, 140, 0xF2E8CC)
    rect(0, 106, 96, 34, 0x1B5A5A)
    text("息壤", 0, 112, 20, 0xF2E8CC, center: 48)
    text("一日游", 0, 80, 16, 0x24211F, center: 48)
    rect(10, 72, 76, 2, 0x24211F)
    text("2007.07.04", 0, 50, 13, 0x24211F, center: 48)
    text("独立日", 0, 28, 14, 0xD74735, center: 48)
    rect(16, 10, 64, 4, 0x24211F)
}
// 你自己的工牌
save("poster_badge_self", 96, 140) {
    rect(0, 0, 96, 140, 0xFFFBF5)
    rect(0, 110, 96, 30, 0xD74735)
    text("体验官", 0, 114, 18, 0xFFFBF5, center: 48)
    rect(24, 44, 48, 56, 0x8A8480)
    oval(36, 70, 24, 26, 0x5E5854)
    text("#0011", 0, 22, 14, 0xD74735, center: 48)
    rect(16, 10, 64, 4, 0x24211F)
}
// 前十个体验官的工牌，长在样板区那些“员工”身上：版式一样，号码它没学会，糊成两团
save("poster_badge_staff", 96, 140) {
    rect(0, 0, 96, 140, 0xFFFBF5)
    rect(0, 110, 96, 30, 0xD74735)
    text("体验官", 0, 114, 18, 0xFFFBF5, center: 48)
    rect(24, 44, 48, 56, 0x8A8480)
    oval(36, 70, 24, 26, 0x5E5854)
    text("#00", 14, 22, 14, 0xD74735)
    oval(50, 22, 14, 16, 0xC9786C)
    oval(62, 20, 15, 18, 0xB8665A)
    rect(16, 10, 64, 4, 0x24211F)
}
// 独立日烟花晚会：出事那天晚上本来要放的
save("poster_july4", 320, 440) {
    rect(0, 0, 320, 440, 0x1A2238)
    for (cx, cy, r, c) in [(90, 330, 70, 0xD8342E), (230, 300, 60, 0xF4D48E), (160, 230, 50, 0x8AE0C0)] as [(CGFloat, CGFloat, CGFloat, Int)] {
        for k in 0..<16 {
            let a = CGFloat(k) / 16 * 2 * .pi
            rect(cx + cos(a) * r, cy + sin(a) * r, 6, 6, c)
            rect(cx + cos(a) * r * 0.55, cy + sin(a) * r * 0.55, 4, 4, c)
        }
    }
    rect(0, 0, 320, 150, 0xF4E6C4)
    text("独立日烟花晚会", 0, 104, 30, 0xA81E2A, center: 160)
    text("7 月 4 日  晚 9 点", 0, 70, 22, 0x2A2418, center: 160)
    text("下层游客中心  ·  中庭", 0, 42, 16, 0x2A2418, bold: false, center: 160)
    text("末班缆车 20:30 发车", 0, 16, 14, 0x6B645E, bold: false, center: 160)
}

// 满意度终端试运行：公园学会“改进”的开头
save("poster_rating", 320, 440) {
    rect(0, 0, 320, 440, 0xF4F0E6)
    rect(0, 360, 320, 80, 0x1F6A6A)
    text("您的评价", 0, 396, 30, 0xF4E6C4, center: 160)
    text("让息壤更好", 0, 368, 22, 0xF4E6C4, center: 160)
    rect(60, 170, 200, 150, 0x2A2418)
    let colors = [0xD8342E, 0xE8862E, 0xF4D040, 0x8AC040, 0x2E9A4A]
    for i in 0..<5 { rect(CGFloat(76 + i * 36), 196, 28, 28, colors[i]); text("\(i + 1)", CGFloat(76 + i * 36), 230, 16, 0xF4E6C4, center: CGFloat(90 + i * 36)) }
    text("您对本站满意吗？", 0, 282, 18, 0xF4E6C4, center: 160)
    text("游客满意度终端  试运行", 0, 128, 18, 0x2A2418, center: 160)
    text("每一次评分都会被认真对待", 0, 100, 15, 0x2A2418, bold: false, center: 160)
    text("园区将根据您的评分实时改进", 0, 78, 15, 0x2A2418, bold: false, center: 160)
    text("安诺丹公园管理处  2007.06", 0, 24, 13, 0x6B645E, bold: false, center: 160)
}
// 接驳大巴上的电视：公园 2007 年的宣传片，一张一张换。4:3，左下角有台标
func tvBug() {
    rect(10, 212, 92, 18, 0x000000)
    text("息壤  宣传片", 16, 214, 11, 0xF4E6C4)
}
func caption(_ s: String) {
    rect(0, 0, 320, 38, 0x101418)
    text(s, 0, 10, 17, 0xF4E6C4, center: 160)
}
save("tv_1", 320, 240) {
    rect(0, 0, 320, 240, 0x1F6A6A)
    oval(214, 118, 76, 76, 0xF4D48E)
    poly([(0, 60), (70, 100), (130, 76), (200, 108), (320, 70), (320, 0), (0, 0)], 0x17504F)
    text("息壤", 0, 120, 52, 0xFFF4DC, center: 160)
    text("国 家 公 园", 0, 88, 20, 0xF4D48E, center: 160)
    text("安诺丹家族  出品", 0, 40, 13, 0xBFD8D0, bold: false, center: 160)
    tvBug()
}
save("tv_2", 320, 240) {
    rect(0, 0, 320, 240, 0xE88A4A)
    rect(0, 150, 320, 90, 0xF4B86A)
    oval(200, 150, 70, 70, 0xFFE2A0)
    poly([(0, 110), (40, 150), (90, 150), (120, 120), (200, 128), (240, 160), (290, 160), (320, 130), (320, 0), (0, 0)], 0x9A4428)
    poly([(0, 70), (320, 84), (320, 0), (0, 0)], 0x6E2A1A)
    oval(100, 52, 120, 44, 0x3E0C0C)
    oval(116, 60, 88, 30, 0xB02A2A)
    oval(140, 68, 40, 14, 0x2A0808)
    caption("西得克萨斯  ·  甘普申县")
    tvBug()
}
save("tv_3", 320, 240) {
    rect(0, 0, 320, 240, 0x4A1014)
    oval(-60, -80, 440, 400, 0x7A1E22)
    oval(20, 0, 280, 240, 0x5A1418)
    rect(0, 168, 320, 3, 0x2A2A2A)
    rect(110, 104, 110, 56, 0xEDE4D2)
    rect(118, 128, 22, 20, 0x2A3A3A)
    rect(146, 128, 22, 20, 0x2A3A3A)
    rect(174, 128, 22, 20, 0x2A3A3A)
    rect(110, 104, 110, 8, 0xD07A2A)
    rect(162, 160, 4, 10, 0x2A2A2A)
    oval(222, 120, 10, 10, 0xFFE28A)
    caption("乘坐安诺丹缆车，下到地底")
    tvBug()
}
save("tv_4", 320, 240) {
    rect(0, 0, 320, 240, 0x241A18)
    rect(8, 46, 96, 150, 0xC88A3A)
    rect(20, 60, 72, 100, 0xF4E6C4)
    rect(30, 70, 52, 4, 0xC88A3A)
    rect(30, 90, 52, 4, 0xC88A3A)
    rect(112, 46, 96, 150, 0x7A2A22)
    oval(120, 70, 80, 40, 0xE8A04A)
    oval(132, 78, 56, 22, 0xF4C46A)
    rect(216, 46, 96, 150, 0x3A1418)
    for x in stride(from: 228, through: 296, by: 14) { rect(CGFloat(x), 60, 5, 120, 0x8A2A30) }
    caption("游客中心  ·  羊膜温泉  ·  支气管森林")
    tvBug()
}
save("tv_5", 320, 240) {
    rect(0, 0, 320, 240, 0xF4F0E6)
    rect(70, 70, 180, 120, 0x2A2418)
    text("您满意吗？", 0, 160, 16, 0xF4E6C4, center: 160)
    let colors = [0xD8342E, 0xE8862E, 0xF4D040, 0x8AC040, 0x2E9A4A]
    for i in 0..<5 { rect(CGFloat(84 + i * 32), 96, 24, 24, colors[i]); text("\(i + 1)", 0, 124, 12, 0xF4E6C4, center: CGFloat(96 + i * 32)) }
    caption("您的每一次评价，都会让它更好")
    tvBug()
}
save("tv_6", 320, 240) {
    rect(0, 0, 320, 240, 0x141A30)
    for (cx, cy, r, c) in [(80, 170, 44, 0xD8342E), (230, 160, 40, 0xF4D48E), (160, 120, 34, 0x8AE0C0)] as [(CGFloat, CGFloat, CGFloat, Int)] {
        for k in 0..<14 {
            let a = CGFloat(k) / 14 * 2 * .pi
            rect(cx + cos(a) * r, cy + sin(a) * r, 5, 5, c)
            rect(cx + cos(a) * r * 0.5, cy + sin(a) * r * 0.5, 3, 3, c)
        }
    }
    rect(60, 38, 200, 30, 0xC88A3A)
    rect(80, 68, 160, 6, 0xF4E6C4)
    caption("7 月 4 日  独立日烟花晚会  不见不散")
    tvBug()
}
save("tv_static", 320, 240) {
    var seed: UInt32 = 38
    for y in stride(from: 0, to: 240, by: 4) {
        for x in stride(from: 0, to: 320, by: 4) {
            seed = seed &* 1664525 &+ 1013904223
            let v = Int(seed >> 24)
            rect(CGFloat(x), CGFloat(y), 4, 4, v << 16 | v << 8 | v)
        }
    }
}
// 结尾车上的宣传片放完：和 2007 年那张“您满意吗”一个样子，问的是看片子的人
save("tv_rate", 320, 240) {
    rect(0, 0, 320, 240, 0xF4F0E6)
    rect(52, 64, 216, 130, 0x2A2418)
    text("您对本视频满意吗？", 0, 158, 17, 0xF4E6C4, center: 160)
    let colors = [0xD8342E, 0xE8862E, 0xF4D040, 0x8AC040, 0x2E9A4A]
    for i in 0..<5 { rect(CGFloat(84 + i * 32), 92, 24, 24, colors[i]); text("\(i + 1)", 0, 120, 12, 0xF4E6C4, center: CGFloat(96 + i * 32)) }
    caption("您的每一次评价，都会让它更好")
    tvBug()
}
save("tv_off", 320, 240) {
    rect(0, 0, 320, 240, 0x1A3A9A)
    text("无信号", 0, 108, 30, 0xF4F4F4, center: 160)
    text("AV 1", 0, 20, 14, 0xC8D0F0, bold: false, center: 160)
}
// 缆车纪念照：拍照点闪光灯拍的车厢。车上的人坐满了，都是灰的。前排那个被闪光灯照得发白
save("ride_photo", 320, 240) {
    rect(0, 0, 320, 240, 0xF2EEE4)
    rect(8, 8, 304, 224, 0x1A201E)
    // 两边的车窗：外面是被闪光照亮的肉
    poly([(8, 150), (120, 128), (120, 176), (8, 214)], 0x5A1A1C)
    poly([(312, 150), (200, 128), (200, 176), (312, 214)], 0x5A1A1C)
    poly([(8, 150), (120, 128), (120, 132), (8, 158)], 0x8A2A2A)
    poly([(312, 150), (200, 128), (200, 132), (312, 158)], 0x8A2A2A)
    // 车尾的玻璃上一团闪光灯的反光
    rect(120, 120, 80, 64, 0x2A3230)
    oval(140, 138, 40, 28, 0xE8E4D8)
    oval(150, 144, 20, 14, 0xFFFFFF)
    // 一排一排的座位，越远越小，每个座位上一个灰的人
    for row in 0..<6 {
        let k = CGFloat(row) / 6
        let y = 30 + k * 96, scale = 1 - k * 0.72, half = 140 * scale
        for side in [-1, 1] as [CGFloat] {
            for seat in 0..<3 {
                let x = 160 + side * (half * 0.25 + CGFloat(seat) * half * 0.26)
                let w = 36 * scale, h = 44 * scale
                rect(x - w * 0.6, y, w * 1.2, h * 0.5, 0x1F4A4A)
                oval(x - w * 0.5, y + h * 0.35, w, h * 0.55, 0x6E6A64)
                oval(x - w * 0.28, y + h * 0.78, w * 0.56, h * 0.5, 0x8A8680)
            }
        }
    }
    // 前排的人：闪光灯打在脸上，什么都看不清，只有一片白
    oval(30, 8, 120, 90, 0x9A968E)
    oval(58, 70, 66, 74, 0xF6F2E8)
    // 相机印上去的日期
    text("'07 7 4  15:58", 196, 16, 15, 0xFF8A2A)
}
// 井底 3 号码头照片墙上的十张：前十个体验官，一个月一个，都是在拍照点从车窗外拍进来的。
// 人坐在车里，穿着公司的背心，脸被闪光灯照得发白；身后那面车窗外是肉壁，贴着一排灰脸。
// 越往后的照片，那排灰脸里穿背心的越多：第 n 张里有 n - 1 个。
let testerDates = ["01 09", "02 06", "03 06", "04 10", "05 08", "06 05", "07 10", "08 07", "09 04", "10 29"]
let testerTops = [0x1E2A44, 0x222222, 0x3A4430, 0x2E3E5A, 0x1E2A44, 0x2A2A2A, 0x3A4430, 0x2E3E5A, 0x222222, 0x1E2A44]
let testerHair = [0x1A1410, 0x3A2A1A, 0x1A1410, 0x5A5450, 0x2A1E14, 0x1A1410, 0x3A2A1A, 0x1A1410, 0x2A1E14, 0x1A1410]
for n in 1...10 {
    save("tester_photo_\(n)", 320, 240) {
        rect(0, 0, 320, 240, 0xF2EEE4)
        rect(8, 8, 304, 224, 0x14181A)
        // 对面那扇车窗外：闪光照亮的肉壁
        rect(24, 112, 272, 96, 0x5A1A1C)
        rect(24, 190, 272, 18, 0x7A2426)
        // 一排灰脸贴在肉壁上，有的穿着背心
        for k in 0..<9 {
            let x = CGFloat(34 + k * 30), y: CGFloat = 132 + CGFloat(k % 2) * 6
            let vest = k < n - 1
            if vest { rect(x - 2, y - 18, 24, 20, 0xE06A1A); rect(x - 2, y - 12, 24, 3, 0xC8C8C0) }
            oval(x, y, 20, 26, 0x7A7670)
            oval(x + 5, y + 9, 3, 4, 0x1A1410)
            oval(x + 12, y + 9, 3, 4, 0x1A1410)
            rect(x + 7, y + 4, 6, 2, 0x1A1410)
        }
        // 车厢里：座椅、扶手杆、窗框
        rect(8, 100, 304, 12, 0x1F4A4A)
        rect(8, 208, 304, 24, 0x1F4A4A)
        rect(150, 8, 6, 224, 0x2A3230)
        // 坐在前面的那个人：深色上衣、橙色背心，脸一片白
        let top = testerTops[n - 1]
        rect(96, 8, 128, 92, top)
        poly([(104, 8), (216, 8), (206, 86), (114, 86)], 0xE06A1A)
        rect(104, 46, 112, 8, 0xC8C8C0)
        rect(152, 60, 18, 24, 0xF2EEE4)
        rect(156, 64, 10, 3, 0xA02818)
        oval(118, 96, 84, 96, 0xE8E2D6)
        oval(126, 104, 68, 80, 0xFAF6EE)
        oval(116, 160, 88, 40, testerHair[n - 1])
        // 相机印上去的日期
        text("'26 \(testerDates[n - 1])  813", 186, 16, 15, 0xFF8A2A)
    }
}
// 真游客中心观景窗内侧的手印：一层压一层，2007 年 21:41 水漫进来的时候，人都在往玻璃上按
save("handprints", 256, 192) {
    rect(0, 0, 256, 192, 0x1C2422)
    var seed: UInt64 = 7
    func rnd() -> CGFloat {
        seed = seed &* 6364136223846793005 &+ 1442695040888963407
        return CGFloat(seed >> 33) / CGFloat(UInt64(1) << 31)
    }
    for i in 0..<46 {
        let x = rnd() * 236, y = rnd() * 172, s = 0.55 + rnd() * 0.7
        let tone = i < 30 ? 0x3A4240 : 0x6E7470
        oval(x, y, 18 * s, 22 * s, tone)
        for f in 0..<4 { oval(x + CGFloat(f) * 4.6 * s, y + 18 * s, 4 * s, 12 * s, tone) }
        oval(x - 5 * s, y + 6 * s, 4 * s, 10 * s, tone)
    }
}
print("posters ok")
