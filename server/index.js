const http = require("http");
const fs = require("fs");
const path = require("path");
const { WebSocketServer } = require("ws");

const srv = http.createServer((q, s) => {
  const f = path.join(__dirname, "public", q.url === "/" ? "viewer.html" : q.url.split("?")[0]);
  fs.readFile(f, (e, b) => {
    if (e) { s.writeHead(404); return s.end("404"); }
    s.writeHead(200, { "content-type": f.endsWith(".html") ? "text/html; charset=utf-8" : "application/octet-stream" });
    s.end(b);
  });
});
const wss = new WebSocketServer({ server: srv });
const rooms = new Map();

wss.on("connection", (s) => {
  s.on("message", (m, bin) => {
    if (bin) { if (s.room) rooms.get(s.room)?.viewer?.send(m); return; }
    const j = JSON.parse(m);
    if (j.t === "hello" && j.role === "phone") {
      const code = String(Math.floor(100000 + Math.random() * 900000));
      s.role = "phone"; s.room = code;
      rooms.set(code, { phone: s, viewer: null });
      s.send(JSON.stringify({ t: "code", code }));
    } else if (j.t === "join") {
      const r = rooms.get(j.code);
      if (!r) return s.send(JSON.stringify({ t: "err", msg: "no room" }));
      r.viewer = s; s.room = j.code;
      s.send(JSON.stringify({ ok: true }));
      r.phone.send(JSON.stringify({ t: "viewer" }));
    } else if (s.room) rooms.get(s.room)?.phone?.send(m);
  });
  s.on("close", () => { if (s.room) rooms.delete(s.room); });
});
srv.listen(8080, () => console.log("relay http://localhost:8080"));
