// 只录游戏（Java 进程）放出来的声音，存成 m4a；旁边写一个 .epoch 文件：第一个采样对应的墙钟毫秒，用来和画面对齐。
// 系统录屏接口（ScreenCaptureKit），不用装虚拟声卡。看到 <输出>.stop 这个文件就停。
// 用法：swift 录游戏声音.swift <输出.m4a>
import AVFoundation
import ScreenCaptureKit

let out = URL(fileURLWithPath: CommandLine.arguments[1])
let stopFile = out.path + ".stop"
try? FileManager.default.removeItem(at: out)
try? FileManager.default.removeItem(atPath: stopFile)

final class Sink: NSObject, SCStreamOutput, SCStreamDelegate {
    let writer: AVAssetWriter
    let input: AVAssetWriterInput
    var started = false
    var samples = 0

    init(url: URL) {
        writer = try! AVAssetWriter(outputURL: url, fileType: .m4a)
        input = AVAssetWriterInput(mediaType: .audio, outputSettings: [
            AVFormatIDKey: kAudioFormatMPEG4AAC, AVSampleRateKey: 48000, AVNumberOfChannelsKey: 2, AVEncoderBitRateKey: 256_000,
        ])
        input.expectsMediaDataInRealTime = true
        writer.add(input)
    }

    func stream(_ stream: SCStream, didOutputSampleBuffer sb: CMSampleBuffer, of type: SCStreamOutputType) {
        guard type == .audio, sb.isValid else { return }
        let pts = sb.presentationTimeStamp
        if !started {
            // 这个采样的时刻 → 墙钟：现在的墙钟减去（现在的主机时钟 − 采样时刻）
            let nowHost = CMClockGetTime(CMClockGetHostTimeClock())
            let lag = CMTimeGetSeconds(nowHost) - CMTimeGetSeconds(pts)
            let epochMs = Int64((Date().timeIntervalSince1970 - lag) * 1000)
            try? String(epochMs).write(toFile: out.path + ".epoch", atomically: true, encoding: .utf8)
            writer.startWriting()
            writer.startSession(atSourceTime: pts)
            started = true
        }
        if input.isReadyForMoreMediaData { input.append(sb); samples += 1 }
    }

    func stream(_ stream: SCStream, didStopWithError error: Error) {
        print("录音停了：\(error)")
        exit(1)
    }
}

let sink = Sink(url: out)
let sem = DispatchSemaphore(value: 0)
var stream: SCStream?
SCShareableContent.getExcludingDesktopWindows(false, onScreenWindowsOnly: false) { content, error in
    guard let content else { print("拿不到屏幕内容：\(String(describing: error))"); exit(1) }
    guard let game = content.applications.first(where: { $0.applicationName.lowercased().contains("java") || $0.bundleIdentifier.lowercased().contains("java") }) else {
        print("找不到游戏进程"); exit(1)
    }
    let display = content.displays[0]
    let filter = SCContentFilter(display: display, including: [game], exceptingWindows: [])
    let cfg = SCStreamConfiguration()
    cfg.capturesAudio = true
    cfg.sampleRate = 48000
    cfg.channelCount = 2
    cfg.width = 2
    cfg.height = 2
    cfg.minimumFrameInterval = CMTime(value: 1, timescale: 1)
    let s = SCStream(filter: filter, configuration: cfg, delegate: sink)
    try! s.addStreamOutput(sink, type: .audio, sampleHandlerQueue: DispatchQueue(label: "audio"))
    s.startCapture { err in
        if let err { print("开不了录音：\(err)"); exit(1) }
        print("开始录 \(game.applicationName)")
        sem.signal()
    }
    stream = s
}
sem.wait()
while !FileManager.default.fileExists(atPath: stopFile) { usleep(100_000) }
let done = DispatchSemaphore(value: 0)
stream!.stopCapture { _ in done.signal() }
done.wait()
sink.input.markAsFinished()
sink.writer.finishWriting { done.signal() }
done.wait()
try? FileManager.default.removeItem(atPath: stopFile)
print("录好 \(sink.samples) 块声音", sink.writer.error.map { "错：\($0)" } ?? "")
