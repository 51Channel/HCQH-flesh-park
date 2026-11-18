// 按清单把几段素材的某一截首尾相接拼成一条（硬切，不重新压缩；素材带声音就连声音一起接）。
// 用法：swift 拼粗剪.swift <输出.mp4> <文件 入点秒 出点秒> ...
import AVFoundation
let a = CommandLine.arguments
let out = URL(fileURLWithPath: a[1])
try? FileManager.default.removeItem(at: out)
let comp = AVMutableComposition()
let track = comp.addMutableTrack(withMediaType: .video, preferredTrackID: kCMPersistentTrackID_Invalid)!
// 素材带声音就把声音也接上
let audioTrack = comp.addMutableTrack(withMediaType: .audio, preferredTrackID: kCMPersistentTrackID_Invalid)!
var anyAudio = false
var at = CMTime.zero
var i = 2
let sem = DispatchSemaphore(value: 0)
while i + 2 < a.count + 0 && i + 2 <= a.count - 1 {
    let asset = AVURLAsset(url: URL(fileURLWithPath: a[i]))
    var src: AVAssetTrack?
    asset.loadTracks(withMediaType: .video) { t, _ in src = t?.first; sem.signal() }
    sem.wait()
    let from = CMTime(seconds: Double(a[i + 1])!, preferredTimescale: 600), to = CMTime(seconds: Double(a[i + 2])!, preferredTimescale: 600)
    try! track.insertTimeRange(CMTimeRange(start: from, end: to), of: src!, at: at)
    var asrc: AVAssetTrack?
    asset.loadTracks(withMediaType: .audio) { t, _ in asrc = t?.first; sem.signal() }
    sem.wait()
    if let asrc {
        try? audioTrack.insertTimeRange(CMTimeRange(start: from, end: to), of: asrc, at: at)
        anyAudio = true
    }
    at = at + (to - from)
    i += 3
}
if !anyAudio { comp.removeTrack(audioTrack) }
let ex = AVAssetExportSession(asset: comp, presetName: AVAssetExportPresetPassthrough)!
ex.outputURL = out
ex.outputFileType = .mp4
ex.exportAsynchronously { sem.signal() }
sem.wait()
print(ex.status == .completed ? "拼好 \(CMTimeGetSeconds(at)) 秒" : "失败 \(String(describing: ex.error))")
