---
name: test-in-foreground
description: 用户要看着游戏实测时，测试客户端要开在前台（放大、开声音），不能藏在后台静默跑完再报结果
metadata:
  node_type: memory
  type: feedback
  originSessionId: 445becb6-095e-48fc-b18d-3f234224e224
  modified: 2026-11-15T10:17:38.541Z
---

《血肉公园》深井螺旋测完后（2026-11-15），用户说"我要你前台打开测试我看着，而不是在后台静默抛完"。

**Why:** 后台跑完只给截图和数字，用户看不到真实走起来的感觉（压迫感、节奏、肉的反应），没法判断做得对不对。

**How to apply:**
- 血肉公园实测用 `FLESHPARK_SHOW=1 ./测试/实测.sh …`：不带隐藏参数，进服后探针 `front`（1200×720 拉到前台）和 `volume 0.5`。
- 别的项目的游戏实测，用户要看时同理：窗口前台、放大、开声音，开跑前告诉用户别在窗口里按键。
- 这和 [[game-runs-in-background]] 不冲突：那条是录片时藏窗口；实测给用户看时开前台。
- 探针命令 front/volume 改在 黑箱Boss/启动器/客户端探针，不在血肉公园仓库里。

相关：[[flesh-park-repo]]
