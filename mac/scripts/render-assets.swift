import AppKit
import Foundation

let root = URL(fileURLWithPath: CommandLine.arguments[1])
let gold = NSColor(calibratedRed: 1, green: 193/255, blue: 7/255, alpha: 1)
let black = NSColor(calibratedWhite: 0.02, alpha: 1)
func png(width: Int, height: Int, draw: () -> Void) -> Data {
    let bitmap = NSBitmapImageRep(bitmapDataPlanes: nil, pixelsWide: width, pixelsHigh: height, bitsPerSample: 8, samplesPerPixel: 4, hasAlpha: true, isPlanar: false, colorSpaceName: .deviceRGB, bytesPerRow: 0, bitsPerPixel: 0)!
    NSGraphicsContext.saveGraphicsState()
    NSGraphicsContext.current = NSGraphicsContext(bitmapImageRep: bitmap)
    draw()
    NSGraphicsContext.restoreGraphicsState()
    return bitmap.representation(using: .png, properties: [:])!
}
func bag(x: CGFloat, y: CGFloat, size: CGFloat) {
    NSGraphicsContext.saveGraphicsState()
    let t = NSAffineTransform(); t.translateX(by: x, yBy: y); t.concat()
    let scale = NSAffineTransform(); scale.scale(by: size / 100); scale.concat()
    gold.setStroke()
    let body = NSBezierPath(roundedRect: NSRect(x: 17, y: 7, width: 66, height: 65), xRadius: 9, yRadius: 9)
    body.lineWidth = 6; body.stroke()
    let handle = NSBezierPath(); handle.move(to: NSPoint(x: 35,y: 64)); handle.line(to: NSPoint(x:35,y:81)); handle.curve(to:NSPoint(x:65,y:81),controlPoint1:NSPoint(x:35,y:101),controlPoint2:NSPoint(x:65,y:101)); handle.line(to:NSPoint(x:65,y:64)); handle.lineWidth=6; handle.lineCapStyle = .round; handle.stroke()
    let check = NSBezierPath(); check.move(to:NSPoint(x:34,y:42)); check.line(to:NSPoint(x:46,y:30)); check.line(to:NSPoint(x:67,y:51)); check.lineWidth=6;check.lineJoinStyle = .round;check.lineCapStyle = .round;check.stroke()
    NSGraphicsContext.restoreGraphicsState()
}
let assets=root.appendingPathComponent("mac/assets")
let iconset=assets.appendingPathComponent("sibi.iconset")
try FileManager.default.createDirectory(at:iconset,withIntermediateDirectories:true)
for base in [16,32,128,256,512] {
    for factor in [1,2] {
        let size=base*factor
        let data=png(width:size,height:size) {
            let s=CGFloat(size); black.setFill()
            NSBezierPath(roundedRect:NSRect(x:s*0.08,y:s*0.08,width:s*0.84,height:s*0.84),xRadius:s*0.2,yRadius:s*0.2).fill()
            bag(x:s*0.24,y:s*0.24,size:s*0.52)
        }
        try data.write(to:iconset.appendingPathComponent("icon_\(base)x\(base)\(factor==2 ? "@2x" : "").png"))
    }
}
let bannerDir=root.appendingPathComponent("tv/app/src/main/res/drawable-nodpi")
try FileManager.default.createDirectory(at:bannerDir,withIntermediateDirectories:true)
let banner=png(width:640,height:360) {
    black.setFill(); NSRect(x:0,y:0,width:640,height:360).fill()
    bag(x:50,y:110,size:135)
    let white=[NSAttributedString.Key.font:NSFont.systemFont(ofSize:70,weight:.bold),.foregroundColor:NSColor.white] as [NSAttributedString.Key:Any]
    ("sibi" as NSString).draw(at:NSPoint(x:208,y:157),withAttributes:white)
    ("store" as NSString).draw(at:NSPoint(x:333,y:160),withAttributes:[.font:NSFont.systemFont(ofSize:52,weight:.semibold),.foregroundColor:gold])
}
try banner.write(to:bannerDir.appendingPathComponent("store_banner.png"))
