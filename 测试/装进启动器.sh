#!/bin/zsh
# 把构建好的插件和模组装进赫朝启动器的血肉公园实例（只动这一个企划）。先构建插件，再构建模组，再跑这个。
# 看到“检查通过”就是装好了。启动器要先退出。
R=${0:A:h:h}
L=${HECHAO_LAUNCHER:-$HOME/cc/黑箱Boss/启动器}
F=$L/企划文件/血肉公园
export DOTNET_ROOT="$HOME/.local/share/hechao-tools/dotnet-10" PATH="$HOME/.local/share/hechao-tools/dotnet-10:$PATH" DOTNET_CLI_TELEMETRY_OPTOUT=1 DOTNET_NOLOGO=1
mkdir -p $R/交付/客户端_mods $R/交付/服务端_plugins $F
cp $R/源码/客户端模组/build/libs/FleshParkAssets-0.2.0.jar $R/交付/客户端_mods/ && cp $R/源码/客户端模组/build/libs/FleshParkAssets-0.2.0.jar $F/ || exit 1
cp $R/源码/血肉公园插件/build/libs/FleshPark-0.2.0.jar $R/交付/服务端_plugins/ && cp $R/源码/血肉公园插件/build/libs/FleshPark-0.2.0.jar $F/ || exit 1
cd $L/企划导入工具 || exit 1
for try in 1 2 3; do
  out=$(dotnet run --no-build --project 企划导入工具.csproj -c Release -- $R/测试/启动器清单.json $L/企划文件 2>&1 | tail -1)
  echo "$out"; [[ "$out" == *检查通过* ]] && exit 0
  echo "!!! 导入启动器失败（第 $try 次），测试会用旧 jar"; sleep 5
done
exit 1
