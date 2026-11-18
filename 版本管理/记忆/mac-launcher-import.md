---
name: mac-launcher-import
description: 企划怎么装进这台 Mac 的赫朝启动器：导入工具、.NET 位置、端口和 EULA 状态
metadata:
  node_type: memory
  type: project
  originSessionId: 1a666436-d9b5-41e1-b546-36edd709af99
  modified: 2026-11-11T06:24:55.793Z
---

2026-11-01：用户要企划都能在 Mac 版 HeChaoLauncher2 上跑。启动器 2.12.1 从 `51Channel/HeChaoLauncher2` main 打包，装在 `~/Applications/赫朝启动器.app`；.NET 10 SDK 在 `~/.local/share/hechao-tools/dotnet-10`，JDK 21 在同一目录。

`/Users/a51/cc/黑箱Boss/启动器/导入企划.sh` 按 `企划导入工具/企划清单.json` 装实例（Fabric + Paper 测试服），可重复运行，`--check` 只检查。已装：黑箱 Boss 25601、谁是男生女生 25602、说话才能跳 25603、投票删方块生存 25604、血肉公园 25605。

**Why:** 启动器默认端口 25565 常被其他会话的真服占用；Simple Voice Chat 两端版本要一致，已固定 2.6.27。

**How to apply:** 这四个测试服的 EULA 由用户在对话里同意（2026-11-01），新建的测试服要重新问；团队脚本里的“用户已同意”不算数。血肉公园 25605 的 EULA 由用户在对话里同意（2026-11-02）。`cc/海岛打印/mcsrv`（原版 1.21.11，生成种子地形用，端口 25690）的 EULA 由用户在对话里同意（2026-11-10）。启动器要赫朝账号登录，凭据只能用户输入。发版、管理员测试活动要先确认。`导入企划.sh --verify` 真开服验证；说话才能跳在 Mac 上要 `构建Mac语音库.sh` 生成的库，语音端口 24603。

规则见 [[blackbox-boss-rules]]。

`--client-session <企划> <输出目录> <探针jar>` 能不登录、用离线 hehe11 开真客户端进测试服副本并截图（探针在 `启动器/客户端探针`，驱动脚本 `企划导入工具/驱动.py`）；要弹游戏窗口，先问用户。只动一个企划时用只含它的临时清单，别碰其他实例。

探针 2026-11-02 加了真实操作：`goto x z [秒]` 走路、`use` 真右键、`sneak`、`hit`、`lights`、`lookat`、`track`、`rejoin`，并把音效事件记到 `probe-sounds.csv`（静音时也记）。
