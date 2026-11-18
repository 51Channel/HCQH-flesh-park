---
name: flesh-park-repo
description: 血肉公园的 GitHub 私有仓库 51Channel/HCQH-flesh-park，jar 挂 Releases，测试脚本在 测试/
metadata:
  node_type: memory
  type: reference
  originSessionId: 71cfcf87-92b4-49e3-8bc5-95c4ba441ba2
  modified: 2026-11-11T03:56:26.422Z
---

《血肉公园》的本地目录 /Users/a51/cc/血肉公园 从 2026-11-10 起是 git 仓库，远程是 GitHub 私有仓库 51Channel/HCQH-flesh-park（main 分支）。这是用户要求建的。

- 不进仓库的东西：build/、.gradle/、插件构建时烘出来的 flesh.bin（56 MB）、交付/。
- 构建好的两个 jar 一个版本一个 Release，挂在 Releases 上。jar 文件名仍是 0.2.0，因为启动器清单按这个名字认。v0.5.0 是第一个 Release。
- 部署和后台实测的脚本已经从临时目录挪进 测试/（装进启动器.sh、实测.sh、启动器清单.json、步骤/），说明在 测试/说明.md。
- 本仓库的提交作者用 51Channel 的 noreply 邮箱，在仓库本地 git config 里设好了。全局 git 没配身份。
- 只在用户要求时提交和推送。

相关：[[flesh-park-concept]]
