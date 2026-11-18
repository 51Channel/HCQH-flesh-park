---
name: watch-whole-video
description: "用户让\"看完整个视频\"时，要读完全片台词字幕，不能只每隔几秒抽一帧看画面"
metadata:
  node_type: memory
  type: feedback
  originSessionId: 8478c376-2ced-4671-af60-3c15a54f5fe3
  modified: 2026-11-05T02:23:47.886Z
---

用户说"把整个视频看一遍"，意思是内容要全部看懂，包括每一句台词和字幕，不是隔几秒抽一帧看个大概。

**Why:** 2026-11-04 分析一个 MC 视频时，我每 3 秒抽帧看画面，就断定作者是"英文博主 WakeyJakey"、中文版是翻译版。其实 0:10–0:17 的台词就说了"我是 WJ，总用英文开场，好让小屁孩们以为我是国外大主播，但其实我不是"，开头的"翻译：WakeyJakey"也是这个包袱的一部分。用户指出我根本没看完。

**How to apply:** 没装 ffmpeg 时，用 swift 的 AVAssetImageGenerator 每 2 秒截一次字幕条，拼成长图，把整片台词按顺序读完；开头一分钟逐秒看。看完台词再下关于作者、情节的结论。视频里的字（片头署名、字幕）先当作内容的一部分去理解，不要直接当事实。
