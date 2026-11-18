// 把客户端录的一串 JPEG（fleshpark-rec/片段名/，带 frames.tsv 每帧毫秒）拼成 H.264 mp4，按真实时刻摆帧。
// 用法：swift 帧拼视频.swift <帧目录> <输出.mp4>
import AVFoundation
import AppKit

let args = CommandLine.arguments
let dir = URL(fileURLWithPath: args[1])
let out = URL(fileURLWithPath: args[2])
try? FileManager.default.removeItem(at: out)

let lines = try! String(contentsOf: dir.appendingPathComponent("frames.tsv"), encoding: .utf8).split(separator: "\n")
var frames: [(String, Int)] = []
for l in lines {
    let f = l.split(separator: "\t")
    if f.count == 2, let n = Int(f[0]), let ms = Int(f[1]) {
        let name = String(format: "f%06d.jpg", n)
        if FileManager.default.fileExists(atPath: dir.appendingPathComponent(name).path) { frames.append((name, ms)) }
    }
}
guard let first = NSImage(contentsOf: dir.appendingPathComponent(frames[0].0))?.cgImage(forProposedRect: nil, context: nil, hints: nil) else { fatalError("读不到第一帧") }
let w = first.width / 2 * 2, h = first.height / 2 * 2

let writer = try! AVAssetWriter(outputURL: out, fileType: .mp4)
let input = AVAssetWriterInput(mediaType: .video, outputSettings: [
    AVVideoCodecKey: AVVideoCodecType.h264, AVVideoWidthKey: w, AVVideoHeightKey: h,
    AVVideoCompressionPropertiesKey: [AVVideoAverageBitRateKey: 40_000_000, AVVideoProfileLevelKey: AVVideoProfileLevelH264HighAutoLevel],
])
input.expectsMediaDataInRealTime = false
let adaptor = AVAssetWriterInputPixelBufferAdaptor(assetWriterInput: input, sourcePixelBufferAttributes: [
    kCVPixelBufferPixelFormatTypeKey as String: kCVPixelFormatType_32ARGB, kCVPixelBufferWidthKey as String: w, kCVPixelBufferHeightKey as String: h,
])
writer.add(input)
writer.startWriting()
writer.startSession(atSourceTime: .zero)

var last = -1
for (name, ms) in frames {
    if ms <= last { continue }
    last = ms
    guard let img = NSImage(contentsOf: dir.appendingPathComponent(name))?.cgImage(forProposedRect: nil, context: nil, hints: nil) else { continue }
    while !input.isReadyForMoreMediaData { usleep(2000) }
    var pb: CVPixelBuffer?
    CVPixelBufferPoolCreatePixelBuffer(nil, adaptor.pixelBufferPool!, &pb)
    CVPixelBufferLockBaseAddress(pb!, [])
    let ctx = CGContext(data: CVPixelBufferGetBaseAddress(pb!), width: w, height: h, bitsPerComponent: 8, bytesPerRow: CVPixelBufferGetBytesPerRow(pb!),
                        space: CGColorSpaceCreateDeviceRGB(), bitmapInfo: CGImageAlphaInfo.noneSkipFirst.rawValue)!
    ctx.draw(img, in: CGRect(x: 0, y: 0, width: w, height: h))
    CVPixelBufferUnlockBaseAddress(pb!, [])
    adaptor.append(pb!, withPresentationTime: CMTime(value: CMTimeValue(ms), timescale: 1000))
}
input.markAsFinished()
let done = DispatchSemaphore(value: 0)
writer.finishWriting { done.signal() }
done.wait()
print("\(out.lastPathComponent)：\(frames.count) 帧，\(Double(last) / 1000) 秒，\(w)×\(h)", writer.error.map { "错：\($0)" } ?? "")
