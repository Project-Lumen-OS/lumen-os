import Cocoa
import WebKit

// usage: gm3dsnap in.html outdir [env]
// Loads the export page (export.js), waits for window.READY, then writes outdir/meta.json, part_NN.bin (float32
// pos|nor|uv|tan then uint32 indices), tex_NN.png and with "env" env.f32 (the room environment, float32 RGB layers).
let args = CommandLine.arguments
if args.count < 3 { print("usage: gm3dsnap in.html outdir [env]"); exit(64) }
let inURL = URL(fileURLWithPath: args[1])
let outDir = URL(fileURLWithPath: args[2])
let wantEnv = args.count > 3 && args[3] == "env"
try? FileManager.default.createDirectory(at: outDir, withIntermediateDirectories: true)
let app = NSApplication.shared
app.setActivationPolicy(.prohibited)

let ENV_W = 128, ENV_H = 64
let ROUGH = "[0.0, 0.05, 0.1, 0.15, 0.2, 0.25, 0.3, 0.4, 0.5, 0.6, 0.8, 1.0]"

class D: NSObject, WKNavigationDelegate {
    let wv: WKWebView
    let win: NSWindow
    var nParts = 0, nTex = 0
    init(_ u: URL) {
        let rect = NSRect(x: 0, y: 0, width: 1400, height: 900)
        let cfg = WKWebViewConfiguration()
        cfg.preferences.setValue(true, forKey: "allowFileAccessFromFileURLs")
        cfg.setValue(true, forKey: "allowUniversalAccessFromFileURLs")
        win = NSWindow(contentRect: rect, styleMask: [.borderless], backing: .buffered, defer: false)
        wv = WKWebView(frame: rect, configuration: cfg)
        super.init()
        win.contentView = wv
        wv.navigationDelegate = self
        wv.loadFileURL(u, allowingReadAccessTo: URL(fileURLWithPath: "/"))
    }
    func webView(_ w: WKWebView, didFinish n: WKNavigation!) { poll() }
    func poll() {
        wv.evaluateJavaScript("JSON.stringify({r:!!window.READY,e:window.ERR||null})") { res, err in
            let s = (res as? String) ?? "{}"
            if s.contains("\"e\":\"") { print("PAGE ERR \(s)"); exit(3) }
            if s.contains("\"r\":true") { self.meta(); return }
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.25) { self.poll() }
        }
    }
    func write(_ name: String, _ data: Data) {
        try? data.write(to: outDir.appendingPathComponent(name))
    }
    func meta() {
        wv.evaluateJavaScript("window.exportMeta()") { res, err in
            guard let s = res as? String, let d = s.data(using: .utf8),
                  let j = try? JSONSerialization.jsonObject(with: d) as? [String: Any] else {
                print("ERR meta \(String(describing: err))"); exit(4)
            }
            self.write("meta.json", d)
            self.nParts = (j["parts"] as? [Any])?.count ?? 0
            self.nTex = (j["textures"] as? [Any])?.count ?? 0
            print("meta parts \(self.nParts) textures \(self.nTex)")
            self.part(0)
        }
    }
    func part(_ i: Int) {
        if i >= nParts { tex(0); return }
        wv.evaluateJavaScript("window.exportPart(\(i))") { res, err in
            guard let s = res as? String, let data = Data(base64Encoded: s) else { print("ERR part \(i) \(String(describing: err))"); exit(4) }
            self.write(String(format: "part_%03d.bin", i), data)
            self.part(i + 1)
        }
    }
    func tex(_ i: Int) {
        if i >= nTex { env(); return }
        wv.evaluateJavaScript("window.exportTex(\(i))") { res, err in
            guard let s = res as? String, let comma = s.firstIndex(of: ","),
                  let data = Data(base64Encoded: String(s[s.index(after: comma)...])) else { print("ERR tex \(i) \(String(describing: err))"); exit(4) }
            self.write(String(format: "tex_%02d.png", i), data)
            self.tex(i + 1)
        }
    }
    func env() {
        if !wantEnv { print("done"); exit(0) }
        wv.evaluateJavaScript("window.exportEnv(\(ENV_W), \(ENV_H), \(ROUGH))") { res, err in
            guard let s = res as? String, let data = Data(base64Encoded: s) else { print("ERR env \(String(describing: err))"); exit(4) }
            self.write("env.f32", data)
            self.write("env.json", "{\"w\": \(ENV_W), \"h\": \(ENV_H), \"rough\": \(ROUGH)}".data(using: .utf8)!)
            print("done")
            exit(0)
        }
    }
}
let d = D(inURL)
DispatchQueue.main.asyncAfter(deadline: .now() + 300) { print("timeout"); exit(2) }
app.run()
