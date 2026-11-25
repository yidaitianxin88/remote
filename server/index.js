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
const phones = new Map();
const viewers = new Set();
const list = () => JSON.stringify({ t: "list", items: [...phones].map(([id, p]) => ({ id, name: p.name })) });

wss.on("connection", (s) => {
  s.on("message", (m, bin) => {
    if (bin) { if (s.peer) s.peer.send(m); return; }
    let j; try { j = JSON.parse(m); } catch { return; }
    if (j.t === "hello" && j.role === "phone") {
      s.role = "phone"; s.id = j.id; console.log("phone up", j.id, j.name);
      phones.set(j.id, { ws: s, name: j.name || j.id });
      viewers.forEach(v => v.send(list()));
    } else if (j.t === "list") {
      viewers.add(s);
      s.send(list());
    } else if (j.t === "join") {
      const p = phones.get(j.id);
      if (!p) return s.send(JSON.stringify({ t: "err", msg: "offline" }));
      s.peer = p.ws; p.ws.peer = s;
      s.send(JSON.stringify({ t: "ok" }));
    } else if (s.peer) s.peer.send(m);
  });
  s.on("close", () => { console.log("sock close", s.role, s.id);
    viewers.delete(s);
    if (s.id) { phones.delete(s.id); viewers.forEach(v => v.send(list())); }
  });
});
srv.listen(8080, () => console.log("relay http://localhost:8080"));