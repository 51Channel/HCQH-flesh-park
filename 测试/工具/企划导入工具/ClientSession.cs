using System.Diagnostics;
using CmlLib.Core.Auth;
using CmlLib.Core.ProcessBuilder;
using Hechao.GameLibrary;
using Hechao.GameLibrary.LocalServers;

// 不用登录的真客户端测试：用实例下载好的游戏文件，以离线身份 hehe11 进一份测试服副本。
// 实例和它的测试服都不动：服务端和游戏目录都是输出目录里的副本，只有副本改成 online-mode=false。
// 启动后读 <输出目录>/ctl.txt：每行发给服务端控制台，quit 结束。客户端操作写进 game/probe-commands.txt。
internal static class ClientSession
{
    private const string Player = "hehe11";

    public static async Task RunAsync(GameLibraryContext library, ProjectEntry project, string outputRoot, string probeJar)
    {
        var instance = library.Store.List().First(candidate => candidate.Name == project.Name);
        var state = library.Servers.TryRead(instance.Id) ?? throw new InvalidOperationException("没有测试服。");
        if (!state.EulaAccepted) throw new InvalidOperationException("还没有同意 EULA。");

        var root = Path.Combine(Path.GetFullPath(outputRoot), project.Files);
        Directory.CreateDirectory(root);
        var serverCopy = Path.Combine(root, "server");
        CopyTree(library.Servers.GetServerDirectory(instance.Id), serverCopy);
        LocalServerProperties.Apply(serverCopy, project.Port + 100);
        SetProperty(Path.Combine(serverCopy, "server.properties"), "online-mode", "false");
        SetProperty(Path.Combine(serverCopy, "server.properties"), "enforce-secure-profile", "false");
        SetProperty(Path.Combine(serverCopy, "server.properties"), "allow-flight", "true");

        var java = await library.ServerInstaller.ResolveJavaAsync(instance, CancellationToken.None);
        using var server = LocalServerProcess.Start(java, serverCopy, state with { Port = project.Port + 100 });
        var serverLog = Path.Combine(serverCopy, "logs", "latest.log");
        await WaitLineAsync(() => server.Log, "Done (", TimeSpan.FromMinutes(5), () => server.HasExited);
        Console.WriteLine("服务端就绪");
        server.SendCommand("op " + Player);

        var game = Path.Combine(root, "game");
        var mods = Path.Combine(game, "mods");
        Directory.CreateDirectory(mods);
        foreach (var jar in Directory.GetFiles(Path.Combine(library.Layout.GetGameDirectory(instance.Id), "mods"), "*.jar"))
        {
            File.Copy(jar, Path.Combine(mods, Path.GetFileName(jar)), overwrite: true);
        }

        // 探针 jar 可以用逗号隔开传多个（比如探针 + 要测的模组）
        foreach (var jar in probeJar.Split(',', StringSplitOptions.RemoveEmptyEntries | StringSplitOptions.TrimEntries))
        {
            File.Copy(jar, Path.Combine(mods, Path.GetFileName(jar)), overwrite: true);
        }
        // 窗口被挡住也不暂停、不弹首次引导；声音关掉，测试时不吵人。
        File.WriteAllLines(Path.Combine(game, "options.txt"),
        [
            "onboardAccessibility:false", "pauseOnLostFocus:false", "tutorialStep:none", "renderDistance:12",
            "soundCategory_master:0.0", "joinedFirstServer:true", "skipMultiplayerWarning:true"
        ]);

        using var client = await StartClientAsync(library, instance, game, project.Port + 100);
        var clientLog = Path.Combine(root, "client.log");
        await using var clientWriter = new StreamWriter(clientLog) { AutoFlush = true };
        client.OutputDataReceived += (_, data) => { if (data.Data is not null) lock (clientWriter) clientWriter.WriteLine(data.Data); };
        client.ErrorDataReceived += (_, data) => { if (data.Data is not null) lock (clientWriter) clientWriter.WriteLine(data.Data); };
        client.Start();
        client.BeginOutputReadLine();
        client.BeginErrorReadLine();
        Console.WriteLine($"客户端 pid {client.Id}，输出目录 {root}");

        try
        {
            await WaitLineAsync(() => ServerVerification.ReadLog(serverLog), Player + " joined the game", TimeSpan.FromMinutes(5), () => client.HasExited || server.HasExited);
            Console.WriteLine("已进服");
            await ControlLoopAsync(server, client, Path.Combine(root, "ctl.txt"));
        }
        finally
        {
            if (!client.HasExited) client.Kill(entireProcessTree: true);
            await server.StopAsync(TimeSpan.FromSeconds(90));
        }

        Console.WriteLine($"结束，客户端退出码 {client.ExitCode}");
    }

    private static async Task<Process> StartClientAsync(GameLibraryContext library, CustomInstance instance, string game, int port)
    {
        var launcher = library.Installer.CreateLauncher(instance.Id);
        var version = await launcher.GetVersionAsync(instance.VersionId ?? throw new InvalidOperationException("实例没装完。"));
        await launcher.InstallAsync(version, null, null, CancellationToken.None);
        var natives = launcher.NativeLibraryExtractor.Extract(launcher.MinecraftPath, version, launcher.RulesContext);
        var process = launcher.BuildProcess(version, new MLaunchOption
        {
            Session = MSession.CreateOfflineSession(Player),
            MaximumRamMb = 4096,
            MinimumRamMb = 512,
            NativesDirectory = natives,
            GameLauncherName = "Hechao Launcher Test",
            ServerIp = "127.0.0.1",
            ServerPort = port,
            ScreenWidth = 1280,
            ScreenHeight = 720
        });
        var original = Path.GetFullPath(library.Layout.GetGameDirectory(instance.Id));
        var info = process.StartInfo;
        info.UseShellExecute = false;
        info.RedirectStandardOutput = true;
        info.RedirectStandardError = true;
        info.WorkingDirectory = game;
        info.Arguments = info.Arguments.Replace(original, game, StringComparison.Ordinal);
        for (var index = 0; index < info.ArgumentList.Count; index++)
        {
            info.ArgumentList[index] = info.ArgumentList[index].Replace(original, game, StringComparison.Ordinal);
        }

        // 测试用：HECHAO_TEST_JVM_ARG 里的一个 JVM 参数放到最前面（比如 -Dhechao.chat.session=...）
        if (Environment.GetEnvironmentVariable("HECHAO_TEST_JVM_ARG") is { Length: > 0 } extra)
        {
            if (info.ArgumentList.Count > 0) info.ArgumentList.Insert(0, extra);
            else info.Arguments = $"\"{extra}\" {info.Arguments}";
        }

        return process;
    }

    private static async Task ControlLoopAsync(LocalServerProcess server, Process client, string controlFile)
    {
        while (!client.HasExited && !server.HasExited)
        {
            if (File.Exists(controlFile))
            {
                var lines = await File.ReadAllLinesAsync(controlFile);
                File.Delete(controlFile);
                foreach (var line in lines)
                {
                    if (line.Trim() == "quit") return;
                    server.SendCommand(line);
                }
            }

            await Task.Delay(200);
        }
    }

    private static async Task WaitLineAsync(Func<IReadOnlyList<string>> lines, string text, TimeSpan limit, Func<bool> gone)
    {
        var deadline = DateTime.UtcNow + limit;
        while (DateTime.UtcNow < deadline && !gone())
        {
            if (lines().Any(line => line.Contains(text, StringComparison.Ordinal))) return;
            await Task.Delay(500);
        }

        throw new TimeoutException($"等不到「{text}」");
    }

    private static void SetProperty(string path, string key, string value)
    {
        var lines = File.ReadAllLines(path).Where(line => !line.StartsWith(key + "=", StringComparison.Ordinal)).Append(key + "=" + value);
        File.WriteAllLines(path, lines.ToArray());
    }

    private static void CopyTree(string source, string target)
    {
        Directory.CreateDirectory(target);
        foreach (var file in Directory.GetFiles(source))
        {
            // 实例的测试服开着时，存档里的 session.lock 被它锁住；副本服会自己再建一个
            if (Path.GetFileName(file) == "session.lock") continue;
            File.Copy(file, Path.Combine(target, Path.GetFileName(file)), overwrite: true);
        }
        foreach (var directory in Directory.GetDirectories(source))
        {
            if (Path.GetFileName(directory) != "logs") CopyTree(directory, Path.Combine(target, Path.GetFileName(directory)));
        }
    }
}
