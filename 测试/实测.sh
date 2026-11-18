#!/bin/zsh
# 用法：实测.sh <步骤文件> <截图输出目录>
# 开一个藏在后台的测试客户端进血肉公园测试服，按步骤文件走，最后把截图拷到输出目录。
# FLESHPARK_SHOW=1：给人看着测，窗口拉到前台、放大、开一半音量（FLESHPARK_VOLUME 可改）。
# 客户端缓存放在 $FLESHPARK_WORK（默认 ~/.cache/fleshpark-test），第一次要下载客户端，会慢。
R=${0:A:h:h}
L=${HECHAO_LAUNCHER:-$HOME/cc/黑箱Boss/启动器}
export HECHAO_GAME_DATA=${HECHAO_GAME_DATA:-${FLESHPARK_WORK:-$HOME/.cache/fleshpark-test}/gamedata}  # 测试用的游戏库，不碰启动器自己的
W=${FLESHPARK_WORK:-$HOME/.cache/fleshpark-test}
S=$W/session/血肉公园
[[ -f $1 ]] || { echo "找不到步骤文件：$1"; exit 1; }
STEPS=${1:A}; OUT=${2:A}
HIDE=-Dhechao.probe.hide=true; [[ -n $FLESHPARK_SHOW ]] && HIDE=
export HECHAO_TEST_JVM_ARG=$HIDE DOTNET_ROOT="$HOME/.local/share/hechao-tools/dotnet-10" PATH="$HOME/.local/share/hechao-tools/dotnet-10:$PATH" DOTNET_CLI_TELEMETRY_OPTOUT=1 DOTNET_NOLOGO=1
mkdir -p $W $OUT; rm -rf $S/game/screenshots
cd $L/企划导入工具 || exit 1
dotnet run --no-build --project 企划导入工具.csproj -c Release -- --client-session 血肉公园 $W/session $L/客户端探针/build/libs/HechaoClientProbe-0.1.0.jar $R/测试/启动器清单.json $L/企划文件 > $W/session.log 2>&1 &
for i in $(seq 1 600); do grep -q '已进服' $W/session.log 2>/dev/null && break; grep -q '失败\|Exception' $W/session.log 2>/dev/null && break; sleep 1; done
tail -3 $W/session.log
if [[ -n $FLESHPARK_SHOW ]]; then
  printf 'front\nvolume %s\n' ${FLESHPARK_VOLUME:-0.5} >> $S/game/probe-commands.txt
  sleep 2
  # macOS 不让后台起的程序自己抢到前台：按进程号把这个客户端的窗口拉到最前面
  for pid in $(pgrep -f java); do
    [[ $(lsof -p $pid -a -d cwd -Fn 2>/dev/null | sed -n 's/^n//p') == $S/game ]] && osascript -e "tell application \"System Events\" to set frontmost of (first process whose unix id is $pid) to true"
  done
fi
python3 $L/企划导入工具/驱动.py $S $STEPS
echo quit >> $S/ctl.txt
wait
cp $S/game/screenshots/*.png(N) $OUT/ 2>/dev/null
ls $OUT | head -50
