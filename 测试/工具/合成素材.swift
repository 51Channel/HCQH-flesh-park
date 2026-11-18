// 把客户端录的帧（fleshpark-rec/片段名/，frames.tsv 第一行 #epoch 开录墙钟毫秒）拼成固定帧率的 H.264，
// 再按墙钟对齐合上另外录的游戏声音（录游戏声音.swift 出的 m4a 和 .epoch），可以只截一段。
// 用法：swift 合成素材.swift <帧目录> <输出.mp4> [声音.m4a|-] [帧率=60] [入点秒 出点秒]
import AVFoundation
import AppKit

let a = CommandLine.arguments
let dir = URL(fileURLWithPath: a[1])
let out = URL(fileURLWithPath: a[2])
let audioPath = a.count > 3 && a[3] != "-" ? a[3] : nil
let fps = a.count > 4 ? Int(a[4])! : 60
let cutIn = a.count > 6 ? Double(a[5])! : nil
let cutOut = a.count > 6 ? Double(a[6])! : nil

var epoch: Int64 = 0
var frames: [(String, Int)] = []
for l in try! String(contentsOf: dir.appendingPathComponent("frames.tsv"), encoding: .utf8).split(separator: "\n") {
    let f = l.split(separator: "\t")
    if f.count == 2, f[0] == "#epoch" { epoch = Int64(f[1])!; continue }
    if f.count == 2, let n = Int(f[0]), let ms = Int(f[1]) {
        let name = String(format: "f%06d.jpg", n)
        if FileManager.default.fileExists(atPath: dir.appendingPathComponent(name).path) { frames.append((name, ms)) }
    }
}
func load(_ name: String) -> CGImage? {
    NSImage(contentsOf: dir.appendingPathComponent(name))?.cgImage(forProposedRect: nil, context: nil, hints: nil)
}
let first = load(frames[0].0)!
let w = first.width / 2 * 2, h = first.height / 2 * 2

// 第一步：固定帧率的画面。每个输出帧取那一刻之前最近的一张
let tmp = URL(fileURLWithPath: NSTemporaryDirectory()).appendingPathComponent("fp-video-\(getpid()).mp4")
try? FileManager.default.removeItem(at: tmp)
let writer = try! AVAssetWriter(outputURL: tmp, fileType: .mp4)
let input = AVAssetWriterInput(mediaType: .video, outputSettings: [
    AVVideoCodecKey: AVVideoCodecType.h264, AVVideoWidthKey: w, AVVideoHeightKey: h,
    AVVideoCompressionPropertiesKey: [AVVideoAverageBitRateKey: 50_000_000, AVVideoProfileLevelKey: AVVideoProfileLevelH264HighAutoLevel,
                                      AVVideoExpectedSourceFrameRateKey: fps],
])
let adaptor = AVAssetWriterInputPixelBufferAdaptor(assetWriterInput: input, sourcePixelBufferAttributes: [
    kCVPixelBufferPixelFormatTypeKey as String: kCVPixelFormatType_32ARGB, kCVPixelBufferWidthKey as String: w, kCVPixelBufferHeightKey as String: h,
])
writer.add(input)
writer.startWriting()
writer.startSession(atSourceTime: .zero)
let lastMs = frames.last!.1
var k = 0, loaded = -1
var pb: CVPixelBuffer?
var n = 0
while n * 1000 / fps <= lastMs {
    let t = n * 1000 / fps
    while k + 1 < frames.count && frames[k + 1].1 <= t { k += 1 }
    if k != loaded, let img = load(frames[k].0) {
        var fresh: CVPixelBuffer?
        CVPixelBufferPoolCreatePixelBuffer(nil, adaptor.pixelBufferPool!, &fresh)
        CVPixelBufferLockBaseAddress(fresh!, [])
        let ctx = CGContext(data: CVPixelBufferGetBaseAddress(fresh!), width: w, height: h, bitsPerComponent: 8, bytesPerRow: CVPixelBufferGetBytesPerRow(fresh!),
                            space: CGColorSpaceCreateDeviceRGB(), bitmapInfo: CGImageAlphaInfo.noneSkipFirst.rawValue)!
        ctx.draw(img, in: CGRect(x: 0, y: 0, width: w, height: h))
        CVPixelBufferUnlockBaseAddress(fresh!, [])
        pb = fresh
        loaded = k
    }
    while !input.isReadyForMoreMediaData { usleep(2000) }
    adaptor.append(pb!, withPresentationTime: CMTime(value: CMTimeValue(n), timescale: CMTimeScale(fps)))
    n += 1
}
input.markAsFinished()
let sem = DispatchSemaphore(value: 0)
writer.finishWriting { sem.signal() }
sem.wait()

// 第二步：截一段、合上声音
let comp = AVMutableComposition()
let video = AVURLAsset(url: tmp)
var vt: AVAssetTrack?
video.loadTracks(withMediaType: .video) { t, _ in vt = t?.first; sem.signal() }
sem.wait()
let total = CMTime(value: CMTimeValue(n), timescale: CMTimeScale(fps))
let from = cutIn.map { CMTime(seconds: $0, preferredTimescale: 600) } ?? .zero
let to = cutOut.map { CMTime(seconds: $0, preferredTimescale: 600) } ?? total
let range = CMTimeRange(start: from, end: CMTimeMinimum(to, total))
let ctrack = comp.addMutableTrack(withMediaType: .video, preferredTrackID: kCMPersistentTrackID_Invalid)!
try! ctrack.insertTimeRange(range, of: vt!, at: .zero)
var note = "无声"
if let audioPath, let audioEpoch = Int64((try? String(contentsOfFile: audioPath + ".epoch", encoding: .utf8))?.trimmingCharacters(in: .whitespacesAndNewlines) ?? "") {
    let audio = AVURLAsset(url: URL(fileURLWithPath: audioPath))
    var at: AVAssetTrack?
    audio.loadTracks(withMediaType: .audio) { t, _ in at = t?.first; sem.signal() }
    sem.wait()
    // 画面第 0 秒在声音文件里的位置
    let offset = Double(epoch - audioEpoch) / 1000
    let aStart = CMTime(seconds: offset + CMTimeGetSeconds(range.start), preferredTimescale: 48000)
    if let at, CMTimeGetSeconds(aStart) >= 0 {
        let atrack = comp.addMutableTrack(withMediaType: .audio, preferredTrackID: kCMPersistentTrackID_Invalid)!
        try! atrack.insertTimeRange(CMTimeRange(start: aStart, duration: range.duration), of: at, at: .zero)
        note = String(format: "有声（偏移 %.3f 秒）", offset)
    } else {
        note = "声音对不上（偏移 \(offset) 秒）"
    }
}
try? FileManager.default.removeItem(at: out)
let ex = AVAssetExportSession(asset: comp, presetName: AVAssetExportPresetPassthrough)!
ex.outputURL = out
ex.outputFileType = .mp4
ex.exportAsynchronously { sem.signal() }
sem.wait()
try? FileManager.default.removeItem(at: tmp)
print("\(out.lastPathComponent)：\(w)×\(h) \(fps) 帧/秒，\(String(format: "%.2f", CMTimeGetSeconds(range.duration))) 秒，原始 \(frames.count) 张，\(note)", ex.status == .completed ? "" : "失败")
