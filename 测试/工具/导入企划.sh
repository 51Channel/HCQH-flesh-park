#!/usr/bin/env bash
# 把 企划导入工具/企划清单.json 里的企划装进赫朝启动器。可以重复运行；--check 只检查不安装。
# 先退出启动器再运行。EULA 不在这里同意，要到启动器的测试服页点。
set -euo pipefail

here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
tools="$HOME/.local/share/hechao-tools/dotnet-10"
export DOTNET_ROOT="$tools" PATH="$tools:$PATH" DOTNET_CLI_TELEMETRY_OPTOUT=1 DOTNET_NOLOGO=1

dotnet run --project "$here/企划导入工具/企划导入工具.csproj" -c Release -- "$@" \
  "$here/企划导入工具/企划清单.json" "$here/企划文件"
