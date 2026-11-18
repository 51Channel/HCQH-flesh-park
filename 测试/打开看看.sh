#!/bin/zsh
# 用法：打开看看.sh [地点]
# 给人看的：在前台开一个测试客户端进血肉公园测试服（离线、本机），进服后传送到 <地点>，然后一直开着，
# 关掉游戏窗口才结束。地点就是 /fp tp 的地点（station hall window garden pool1 forest well rim truss rope car
# dock2 hall2 window2 garden2 ending …），也可以直接写一条 /fp 指令，比如 "walkie"、"skip"、"go"。
# 开着的时候想换地方：去.sh <地点>。
# 测试服和游戏数据都放在 $FLESHPARK_WORK（默认 ~/.cache/fleshpark-test），不碰赫朝启动器自己的游戏库。
R=${0:A:h:h}
L=${HECHAO_LAUNCHER:-$HOME/cc/黑箱Boss/启动器}
W=${FLESHPARK_WORK:-$HOME/.cache/fleshpark-test}
S=$W/session/血肉公园
export HECHAO_GAME_DATA=${HECHAO_GAME_DATA:-$W/gamedata}
export DOTNET_ROOT="$HOME/.local/share/hechao-tools/dotnet-10" PATH="$HOME/.local/share/hechao-tools/dotnet-10:$PATH" DOTNET_CLI_TELEMETRY_OPTOUT=1 DOTNET_NOLOGO=1
if pgrep -f "client-session 血肉公园" >/dev/null; then
  echo "已经开着了，直接换地方"; [[ -n $1 ]] && exec $R/测试/去.sh "$@"; exit 0
fi
mkdir -p $W; rm -f $S/ctl.txt $S/game/probe-commands.txt
cd $L/企划导入工具 || exit 1
dotnet run --no-build --project 企划导入工具.csproj -c Release -- --client-session 血肉公园 $W/session $L/客户端探针/build/libs/HechaoClientProbe-0.1.0.jar $R/测试/启动器清单.json $L/企划文件 > $W/session.log 2>&1 &
RUN=$!
for i in $(seq 1 900); do grep -q '已进服' $W/session.log 2>/dev/null && break; grep -q '失败\|Exception' $W/session.log 2>/dev/null && break; kill -0 $RUN 2>/dev/null || break; sleep 1; done
if ! grep -q '已进服' $W/session.log; then echo "没进得去服："; tail -20 $W/session.log; exit 1; fi
# 拉到前台、放大、开声音
printf 'front\nvolume %s\n' ${FLESHPARK_VOLUME:-0.5} >> $S/game/probe-commands.txt
sleep 2
for pid in $(pgrep -f java); do
  [[ $(lsof -p $pid -a -d cwd -Fn 2>/dev/null | sed -n 's/^n//p') == $S/game ]] && osascript -e "tell application \"System Events\" to set frontmost of (first process whose unix id is $pid) to true"
done
[[ -n $1 ]] && $R/测试/去.sh "$@"
echo "游戏开着，关掉窗口就结束"
wait $RUN
echo "游戏已关"
