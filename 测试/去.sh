#!/bin/zsh
# 用法：去.sh <地点>   或   去.sh <任意 /fp 指令，不带 fp>，比如 去.sh walkie、去.sh unlock
# 给已经用 打开看看.sh 开着的游戏换地方：让玩家自己执行一条 /fp 指令。
W=${FLESHPARK_WORK:-$HOME/.cache/fleshpark-test}
G=$W/session/血肉公园/game
[[ -d $G ]] || { echo "游戏没开，先跑 打开看看.sh"; exit 1; }
places=(entry station door dock hall atrium window figure garden lockers pool1 pool2 pool3 burrow forest vehicle exit cab well rim truss rope car bottom dock2 hall2 window2 garden2 ending)
if (( ${places[(Ie)$1]} )); then cmd="fp tp $1"; else cmd="fp $*"; fi
echo "cmd $cmd" >> $G/probe-commands.txt
echo "已发：/$cmd"
