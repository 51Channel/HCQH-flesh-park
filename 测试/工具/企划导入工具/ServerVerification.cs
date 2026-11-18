using System.IO.Compression;
using System.Text;
using System.Text.RegularExpressions;
using Hechao.GameLibrary;
using Hechao.GameLibrary.LocalServers;

// 用启动器自己的开服代码真的开一次服：看插件有没有启用、日志有没有报错，黑箱 Boss 再跑假人整局。
// 没有真人客户端，看不到画面，也测不出客户端帧数。
internal static partial class ServerVerification
{
    private static readonly TimeSpan BootLimit = TimeSpan.FromMinutes(5);

    public static async Task RunAsync(GameLibraryContext library, ProjectEntry project, string filesRoot, bool acceptEula)
    {
        var instance = library.Store.List().First(candidate => candidate.Name == project.Name);
        var state = library.Servers.TryRead(instance.Id) ?? throw new InvalidOperationException("没有测试服。");
        if (!state.EulaAccepted)
        {
            if (!acceptEula) throw new InvalidOperationException("还没有同意 EULA。");
            state = state with { EulaAccepted = true };
            library.Servers.Save(state);
        }

        var directory = library.Servers.GetServerDirectory(instance.Id);
        var logPath = Path.Combine(directory, "logs", "latest.log");
        var java = await library.ServerInstaller.ResolveJavaAsync(instance, CancellationToken.None);
        Console.WriteLine($"[{project.Name}] 开服，端口 {state.Port}");
        using var server = LocalServerProcess.Start(java, directory, state);
        var problems = new List<string>();
        try
        {
            // 新一次开服前的 latest.log 可能还在，先等控制台输出，再读文件。
            await WaitAsync(() => server.Log, "Done (", 0, BootLimit, server, problems);
            foreach (var plugin in project.Plugins)
            {
                var name = PluginName(Path.Combine(filesRoot, project.Files, plugin));
                if (!ReadLog(logPath).Any(line => line.Contains($"[{name}] Enabling {name}", StringComparison.Ordinal))) problems.Add($"没看到插件 {name} 启用");
                else Console.WriteLine($"  插件 {name} 已启用");
            }

            if (project.WaitForLog is { } text) await WaitAsync(() => ReadLog(logPath), text, 0, BootLimit, server, problems);
            if (project.DummyRehearsal)
            {
                await RehearseAsync(server, logPath, 12, problems);
                await RehearseAsync(server, logPath, 50, problems);
            }
        }
        finally
        {
            await server.StopAsync(TimeSpan.FromSeconds(90));
        }

        var errors = ReadLog(logPath).Where(line => ErrorLine().IsMatch(line)).ToArray();
        foreach (var line in errors.Take(12)) Console.WriteLine("  日志：" + line);
        if (errors.Length > 0) problems.Add($"日志有 {errors.Length} 行报错");
        foreach (var line in ReadLog(logPath).Where(line => line.Contains("要画的方块实体", StringComparison.Ordinal) || line.Contains("Boss 方块", StringComparison.Ordinal)).Take(8)) Console.WriteLine("  " + line);
        Console.WriteLine($"  退出码 {server.ExitCode}，日志 {logPath}");
        if (problems.Count > 0) throw new InvalidOperationException(string.Join("；", problems));
        Console.WriteLine("  验证通过");
    }

    private static async Task RehearseAsync(LocalServerProcess server, string logPath, int size, List<string> problems)
    {
        Console.WriteLine($"  ---- 假人 {size} ----");
        var from = ReadLog(logPath).Count;
        Task<bool> Wait(string text, int seconds) => WaitAsync(() => ReadLog(logPath), text, from, TimeSpan.FromSeconds(seconds), server, problems);

        server.SendCommand($"boss rehearsal {size} 1");
        if (!await Wait("阶段：建造中", 30)) return;
        await Task.Delay(TimeSpan.FromSeconds(3));
        server.SendCommand("boss merge");
        if (!await Wait("阶段：收作品", 30) || !await Wait("合体：", 60) || !await Wait("阶段：等开战", 90)) return;
        server.SendCommand("boss status");
        await Task.Delay(TimeSpan.FromSeconds(2));
        server.SendCommand("boss fight");
        if (!await Wait("阶段：开打", 10)) return;
        server.SendCommand("boss siege 600");
        if (!await Wait("阶段：结算", 180) || !await Wait("谁造的是哪里", 5)) return;
        server.SendCommand("boss reset");
        await Task.Delay(TimeSpan.FromSeconds(1));
        server.SendCommand("boss reset");
        await Wait("阶段：开局前", 10);
        await Task.Delay(TimeSpan.FromSeconds(2));
    }

    private static async Task<bool> WaitAsync(Func<IReadOnlyList<string>> lines, string text, int from, TimeSpan limit, LocalServerProcess server, List<string> problems)
    {
        var deadline = DateTime.UtcNow + limit;
        var started = DateTime.UtcNow;
        while (DateTime.UtcNow < deadline && !server.HasExited)
        {
            if (lines().Skip(from).Any(line => line.Contains(text, StringComparison.Ordinal)))
            {
                Console.WriteLine($"  通过：{text}（{(int)(DateTime.UtcNow - started).TotalSeconds} 秒）");
                return true;
            }

            await Task.Delay(TimeSpan.FromSeconds(1));
        }

        problems.Add($"等不到「{text}」");
        Console.WriteLine($"  失败：等不到「{text}」");
        return false;
    }

    internal static IReadOnlyList<string> ReadLog(string path)
    {
        if (!File.Exists(path)) return [];
        using var stream = new FileStream(path, FileMode.Open, FileAccess.Read, FileShare.ReadWrite | FileShare.Delete);
        using var reader = new StreamReader(stream, Encoding.UTF8);
        var lines = new List<string>();
        while (reader.ReadLine() is { } line) lines.Add(line);
        return lines;
    }

    private static string PluginName(string jarPath)
    {
        using var zip = ZipFile.OpenRead(jarPath);
        using var reader = new StreamReader(zip.GetEntry("plugin.yml")!.Open());
        while (reader.ReadLine() is { } line)
        {
            if (line.StartsWith("name:", StringComparison.Ordinal)) return line[5..].Trim().Trim('"', '\'');
        }

        throw new InvalidDataException($"{jarPath} 的 plugin.yml 没有 name。");
    }

    [GeneratedRegex(@"Exception|Caused by|\[ERROR\]|ERROR\]:|Could not pass event|Could not load|骑不上")]
    private static partial Regex ErrorLine();
}
