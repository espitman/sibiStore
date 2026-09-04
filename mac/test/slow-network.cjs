// Loopback-only QA proxy. Never advertised; forwards only the local store's read endpoints.
const http = require('node:http');
let delayMs = 250;
process.on('SIGUSR1', () => { delayMs = 0; console.log('QA throttle disabled'); });
const server = http.createServer((req, res) => {
  if (req.method !== 'GET' || !/^\/(api\/v1\/(info|catalog)|artifacts\/[a-f0-9]{64}\.apk)$/.test(req.url)) {
    res.writeHead(404).end(); return;
  }
  const artifact = req.url.startsWith('/artifacts/');
  const upstream = http.get({host:'127.0.0.1',port:8743,path:req.url,headers:req.headers}, async incoming => {
    res.writeHead(incoming.statusCode,incoming.headers);
    let bytes = 0;
    res.on('close', () => { incoming.destroy(); console.log(`Closed ${artifact?'artifact':'catalog'} after ${bytes} bytes`); });
    console.log(`GET ${artifact?'artifact':req.url} Range=${req.headers.range || 'full'} status=${incoming.statusCode}`);
    try {
      for await (const chunk of incoming) {
        for (let offset = 0; offset < chunk.length; offset += 16384) {
          if (res.destroyed) return;
          const part = chunk.subarray(offset,offset+16384);
          res.write(part); bytes += part.length;
          if (artifact && delayMs) await new Promise(resolve => setTimeout(resolve,delayMs));
        }
      }
      res.end();
    } catch (error) { if(!res.destroyed) res.destroy(error); }
  });
  upstream.on('error',error=>{ if(!res.headersSent)res.writeHead(502);res.end(error.message); });
});
server.listen(8744,'127.0.0.1',()=>console.log(`QA proxy PID ${process.pid}: emulator address 10.0.2.2:8744. SIGUSR1 removes the throttle.`));
