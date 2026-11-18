using System.IO.Compression;
using System.Net.Http.Headers;
using System.Text.Json;
using Hechao.GameLibrary;
using Hechao.GameLibrary.LocalServers;
using Hechao.GameLibrary.Resources;

// 把企划装进赫朝启动器的游戏库：建实例、装 Paper 测试服、放插件和模组。可以重复运行。
// 绝不替玩家同意 EULA：EulaAccepted 只由启动器界面写入，这里不碰。

var arguments = args.ToList();
var checkOnly = arguments.Remove("--check");
var verify = arguments.Remove("--verify");
// 只有用户本人同意了 Minecraft EULA 才能传这个参数。
var acceptEula = arguments.Remove("--accept-eula");
string[]? clientSession = null;
var sessionFlag = arguments.IndexOf("--client-session");
if (sessionFlag >= 0)
{
    clientSession = [arguments[sessionFlag + 1], arguments[sessionFlag + 2], arguments[sessionFlag + 3]];
    arguments.RemoveRange(sessionFlag, 4);
}

if (arguments.Count != 2)
{
    Console.Error.WriteLine("用法：hechao-import [--check | --verify [--accept-eula] | --client-session <企划> <输出目录> <探针jar>] <企划清单.json> <企划文件目录>");
    return 2;
}

var manifest = JsonSerializer.Deserialize<Manifest>(
    File.ReadAllText(arguments[0]),
    new JsonSerializerOptions { PropertyNameCaseInsensitive = true })
    ?? throw new InvalidDataException("企划清单是空的。");
var filesRoot = Path.GetFullPath(arguments[1]);
var dataRoot = Environment.GetEnvironmentVariable("HECHAO_GAME_DATA")
    ?? Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.UserProfile), "Library", "Application Support", "Hechao", "GameData");

using var http = GameLibraryHttp.Create(
    () => DownloadSourcePreference.OfficialFirst,
    new ProductInfoHeaderValue("HechaoProjectImport", "1.0"),
    useSystemProxy: true);
var library = new GameLibraryContext(dataRoot, http, curseForgeGateway: null);
var failed = 0;

if (clientSession is not null)
{
    var target = manifest.Projects.First(project => project.Name == clientSession[0]);
    await ClientSession.RunAsync(library, target, clientSession[1], clientSession[2]);
    return 0;
}

foreach (var project in manifest.Projects)
{
    try
    {
        if (checkOnly) Check(library, project, filesRoot);
        else if (verify) await ServerVerification.RunAsync(library, project, filesRoot, acceptEula);
        else await ImportAsync(library, manifest, project, filesRoot);
    }
    catch (Exception error) when (error is not OperationCanceledException)
    {
        failed++;
        Console.WriteLine($"[{project.Name}] 失败：{error.Message}");
    }
}

return failed == 0 ? 0 : 1;

static async Task ImportAsync(GameLibraryContext library, Manifest manifest, ProjectEntry project, string filesRoot)
{
    Console.WriteLine($"[{project.Name}] 开始");
    var instance = library.Store.List().FirstOrDefault(candidate => candidate.Name == project.Name);
    if (instance is null)
    {
        var loaders = await library.LoaderVersions.GetVersionsAsync(GameLoaderKind.Fabric, manifest.Minecraft);
        var loader = loaders.FirstOrDefault(entry => entry.IsStable) ?? loaders.First();
        instance = library.Store.Create(project.Name, manifest.Minecraft, GameLoaderKind.Fabric, loader.Version);
    }

    if (instance.Status != CustomInstanceStatus.Ready)
    {
        Console.WriteLine($"[{project.Name}] 下载 Minecraft {manifest.Minecraft} 和 Fabric {instance.LoaderVersion}");
        instance = await library.Installer.InstallAsync(instance.Id);
    }

    var modsDirectory = Path.Combine(library.Layout.GetGameDirectory(instance.Id), "mods");
    Directory.CreateDirectory(modsDirectory);
    await InstallClientModsAsync(library, manifest, project, instance);
    foreach (var mod in project.Mods) CopyJar(Path.Combine(filesRoot, project.Files, mod), modsDirectory);

    if (library.Servers.TryRead(instance.Id)?.Software != LocalServerSoftware.Paper)
    {
        Console.WriteLine($"[{project.Name}] 安装 Paper 测试服");
        await library.ServerInstaller.InstallAsync(instance.Id, LocalServerSoftware.Paper);
    }

    var state = library.Servers.TryRead(instance.Id) ?? throw new InvalidOperationException("测试服状态没有写入。");
    string[] jvm = project.JvmArguments ?? [];
    library.Servers.Save(state with
    {
        Port = project.Port,
        LaunchArguments = [.. jvm.Except(state.LaunchArguments), .. state.LaunchArguments]
    });
    var serverDirectory = library.Servers.GetServerDirectory(instance.Id);
    if (project.ServerFolder is { } folder) CopyFolder(Path.Combine(filesRoot, project.Files, folder), Path.Combine(serverDirectory, folder));
    var pluginsDirectory = Path.Combine(serverDirectory, "plugins");
    Directory.CreateDirectory(pluginsDirectory);
    foreach (var plugin in project.Plugins) CopyJar(Path.Combine(filesRoot, project.Files, plugin), pluginsDirectory);
    await InstallServerPluginsAsync(library, manifest, project, pluginsDirectory);

    if (project.WorldZip is { } worldZip) ExtractWorld(Path.Combine(filesRoot, project.Files, worldZip), serverDirectory);
    LocalServerProperties.Apply(serverDirectory, project.Port);
    if (project.ServerProperties is { } properties) SetProperties(Path.Combine(serverDirectory, "server.properties"), properties);
    if (project.VoiceChatPort is { } voicePort)
    {
        var voiceConfig = Path.Combine(pluginsDirectory, "voicechat", "voicechat-server.properties");
        Directory.CreateDirectory(Path.GetDirectoryName(voiceConfig)!);
        SetProperties(voiceConfig, new Dictionary<string, string> { ["port"] = voicePort.ToString() });
    }
    WriteOps(serverDirectory, manifest.Ops);
    Check(library, project, filesRoot);
}

static async Task InstallClientModsAsync(GameLibraryContext library, Manifest manifest, ProjectEntry project, CustomInstance instance)
{
    if (project.ModrinthMods.Length == 0) return;
    var entries = project.ModrinthMods.Select(ParseModrinth).ToArray();
    var projects = await library.Modrinth.GetCompatibleProjectsAsync(entries.Select(entry => entry.Slug).ToArray(), ResourceKind.Mod, manifest.Minecraft, GameLoaderKind.Fabric, CancellationToken.None);
    foreach (var (slug, pinned) in entries)
    {
        var summary = projects.FirstOrDefault(candidate => candidate.Slug == slug)
            ?? throw new InvalidOperationException($"Modrinth 上找不到适用的 {slug}。");
        var existing = library.Index.Read(instance.Id).FirstOrDefault(record => record.ProjectId == summary.ProjectId);
        if (existing is not null && (pinned is null || existing.VersionNumber == pinned)) continue;
        if (existing is not null) await library.Content.DeleteAsync(instance.Id, ResourceKind.Mod, existing.FileName);
        var version = pinned is null
            ? await library.ResolveBestVersionAsync(summary, instance.Id, CancellationToken.None)
            : (await library.Modrinth.GetVersionsAsync(summary.ProjectId, ResourceKind.Mod, manifest.Minecraft, GameLoaderKind.Fabric, CancellationToken.None))
                .FirstOrDefault(candidate => candidate.VersionNumber == pinned);
        if (version is null) throw new InvalidOperationException($"{slug} 没有适用于 {manifest.Minecraft} 的版本 {pinned}。");
        await library.Resources.InstallAsync(instance.Id, summary, version);
        Console.WriteLine($"[{project.Name}] 客户端模组 {slug} {version.VersionNumber}");
    }
}

static async Task InstallServerPluginsAsync(GameLibraryContext library, Manifest manifest, ProjectEntry project, string pluginsDirectory)
{
    foreach (var (slug, pinned) in project.ModrinthPlugins.Select(ParseModrinth))
    {
        // 这里不能按实例的 Fabric 筛：要的是同一个项目的 Paper 版。
        var versions = await library.Modrinth.GetVersionsAsync(slug, ResourceKind.Mod, manifest.Minecraft, null, CancellationToken.None);
        var version = versions.FirstOrDefault(candidate => candidate.Loaders.Contains("paper") && candidate.PrimaryFile is not null && (pinned is null || candidate.VersionNumber == pinned))
            ?? throw new InvalidOperationException($"{slug} 没有适用于 {manifest.Minecraft} 的 Paper 版 {pinned}。");
        var file = version.PrimaryFile!;
        var target = Path.Combine(pluginsDirectory, file.FileName);
        if (File.Exists(target)) continue;
        await library.Downloader.DownloadAsync(file, target, null, CancellationToken.None);
        RemoveOlderVersions(target);
        Console.WriteLine($"[{project.Name}] 服务端插件 {file.FileName}");
    }
}

// 清单写法：slug 取最合适的版本，slug@版本号 固定版本。
static (string Slug, string? Pinned) ParseModrinth(string text)
{
    var at = text.IndexOf('@');
    return at < 0 ? (text, null) : (text[..at], text[(at + 1)..]);
}

// 换版本时把同名旧版拿掉，否则两个版本会一起加载。
static void RemoveOlderVersions(string jarPath)
{
    var name = Path.GetFileName(jarPath);
    var dash = name.LastIndexOf('-');
    if (dash <= 0) return;
    foreach (var old in Directory.EnumerateFiles(Path.GetDirectoryName(jarPath)!, name[..dash] + "-*.jar"))
    {
        if (Path.GetFileName(old) != name) File.Delete(old);
    }
}

static void CopyJar(string source, string directory)
{
    if (!File.Exists(source)) throw new FileNotFoundException($"缺少文件：{source}");
    var target = Path.Combine(directory, Path.GetFileName(source));
    File.Copy(source, target, overwrite: true);
    RemoveOlderVersions(target);
}

static void CopyFolder(string source, string target)
{
    if (!Directory.Exists(source)) throw new DirectoryNotFoundException($"缺少目录：{source}");
    Directory.CreateDirectory(target);
    foreach (var file in Directory.GetFiles(source)) File.Copy(file, Path.Combine(target, Path.GetFileName(file)), overwrite: true);
}

static void ExtractWorld(string zipPath, string serverDirectory)
{
    if (Directory.Exists(Path.Combine(serverDirectory, "world"))) return;
    var root = Path.GetFullPath(serverDirectory) + Path.DirectorySeparatorChar;
    using var zip = ZipFile.OpenRead(zipPath);
    foreach (var entry in zip.Entries.Where(entry => entry.FullName.StartsWith("world/", StringComparison.Ordinal)))
    {
        var target = Path.GetFullPath(Path.Combine(serverDirectory, entry.FullName));
        if (!target.StartsWith(root, StringComparison.Ordinal)) throw new InvalidDataException("地图压缩包里有越界路径。");
        if (entry.FullName.EndsWith('/'))
        {
            Directory.CreateDirectory(target);
            continue;
        }

        Directory.CreateDirectory(Path.GetDirectoryName(target)!);
        entry.ExtractToFile(target, overwrite: true);
    }
}

static void SetProperties(string path, Dictionary<string, string> properties)
{
    var lines = File.Exists(path) ? File.ReadAllLines(path).ToList() : [];
    foreach (var (key, value) in properties)
    {
        var index = lines.FindIndex(line => line.StartsWith(key + "=", StringComparison.Ordinal));
        if (index >= 0) lines[index] = key + "=" + value;
        else lines.Add(key + "=" + value);
    }

    File.WriteAllLines(path, lines);
}

static void WriteOps(string serverDirectory, OpEntry[] ops)
{
    var path = Path.Combine(serverDirectory, "ops.json");
    if (File.Exists(path)) return; // 玩家可能在控制台加过人。
    var entries = ops.Select(op => new Dictionary<string, object>
    {
        ["uuid"] = Guid.ParseExact(op.Uuid, "N").ToString(),
        ["name"] = op.Name,
        ["level"] = 4,
        ["bypassesPlayerLimit"] = false
    });
    File.WriteAllText(path, JsonSerializer.Serialize(entries, new JsonSerializerOptions { WriteIndented = true }));
}

static void Check(GameLibraryContext library, ProjectEntry project, string filesRoot)
{
    var instance = library.Store.List().FirstOrDefault(candidate => candidate.Name == project.Name)
        ?? throw new InvalidOperationException("启动器里没有这个实例。");
    var state = library.Servers.TryRead(instance.Id) ?? throw new InvalidOperationException("没有测试服。");
    var serverDirectory = library.Servers.GetServerDirectory(instance.Id);
    var mods = Path.Combine(library.Layout.GetGameDirectory(instance.Id), "mods");
    var plugins = Path.Combine(serverDirectory, "plugins");
    var problems = new List<string>();
    if (instance.Status != CustomInstanceStatus.Ready) problems.Add("实例没装完");
    if (state.Software != LocalServerSoftware.Paper) problems.Add("测试服不是 Paper");
    if (state.Port != project.Port) problems.Add($"端口是 {state.Port}");
    if (!File.Exists(Path.Combine(serverDirectory, "paper.jar"))) problems.Add("缺 paper.jar");
    if (!File.ReadAllLines(Path.Combine(serverDirectory, "server.properties")).Contains($"server-port={project.Port}")) problems.Add("server.properties 端口不对");
    foreach (var mod in project.Mods) if (!SameFile(Path.Combine(filesRoot, project.Files, mod), Path.Combine(mods, mod))) problems.Add("模组不一致：" + mod);
    foreach (var plugin in project.Plugins) if (!SameFile(Path.Combine(filesRoot, project.Files, plugin), Path.Combine(plugins, plugin))) problems.Add("插件不一致：" + plugin);
    if (project.WorldZip is not null && !File.Exists(Path.Combine(serverDirectory, "world", "level.dat"))) problems.Add("缺地图");
    var modNames = Directory.Exists(mods) ? Directory.GetFiles(mods, "*.jar").Select(Path.GetFileName) : [];
    var pluginNames = Directory.Exists(plugins) ? Directory.GetFiles(plugins, "*.jar").Select(Path.GetFileName) : [];
    Console.WriteLine($"[{project.Name}] {instance.Id} Fabric {instance.LoaderVersion} Paper build {state.SoftwareBuild} 端口 {state.Port} EULA {(state.EulaAccepted ? "已同意" : "未同意")}");
    Console.WriteLine($"  模组：{string.Join("、", modNames)}");
    Console.WriteLine($"  插件：{string.Join("、", pluginNames)}");
    if (problems.Count > 0) throw new InvalidOperationException(string.Join("；", problems));
    Console.WriteLine("  检查通过");
}

static bool SameFile(string left, string right) =>
    File.Exists(left) && File.Exists(right) && File.ReadAllBytes(left).AsSpan().SequenceEqual(File.ReadAllBytes(right));

internal sealed record Manifest(string Minecraft, OpEntry[] Ops, ProjectEntry[] Projects);

internal sealed record OpEntry(string Name, string Uuid);

internal sealed record ProjectEntry(
    string Name,
    int Port,
    string Files,
    string[] Plugins,
    string[] Mods,
    string[] ModrinthMods,
    string[] ModrinthPlugins,
    string? WorldZip = null,
    Dictionary<string, string>? ServerProperties = null,
    string? WaitForLog = null,
    bool DummyRehearsal = false,
    string? ServerFolder = null,
    string[]? JvmArguments = null,
    int? VoiceChatPort = null);
