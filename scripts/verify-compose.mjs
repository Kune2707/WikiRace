import assert from "node:assert/strict";

const base = (process.argv[2] ?? "http://localhost:8080").replace(/\/$/, "");
async function request(path, {expectedStatus = 200, ...options} = {}) {
  const response = await fetch(base + path, {
    ...options,
    signal: AbortSignal.timeout(15000),
  });
  assert.equal(response.status, expectedStatus);
  return response.json();
}
const health = await request("/api/health");
assert.deepEqual(health, {status: "ok"});
for (const path of ["/", "/room/ABC234"]) {
  const response = await fetch(base + path, {signal: AbortSignal.timeout(15000)});
  assert.equal(response.status, 200);
  assert.match(await response.text(), /id="root"/);
}
const create = displayName => ({method: "POST", expectedStatus: 201, headers: {"Content-Type": "application/json"}, body: JSON.stringify({displayName})});
const host = await request("/api/rooms", create("Docker Host"));
const code = host.view.room.roomCode;
const guest = await request(`/api/rooms/${code}/join`, create("Docker Guest"));
assert.notEqual(host.playerSessionToken, guest.playerSessionToken);
const socketUrl = new URL(base + "/ws");
socketUrl.protocol = socketUrl.protocol === "https:" ? "wss:" : "ws:";
const socket = new WebSocket(socketUrl);
try {
  await new Promise((resolve, reject) => {
    const timeout = setTimeout(() => reject(new Error("Proxied STOMP check timed out")), 20000);
    let started = false;
    const fail = error => {clearTimeout(timeout); reject(error);};
    socket.addEventListener("error", () => fail(new Error("WebSocket upgrade failed")));
    socket.addEventListener("open", () => socket.send(
      `CONNECT\naccept-version:1.2\nheart-beat:0,0\nroomCode:${code}\nX-Player-Token:${host.playerSessionToken}\n\n\0`,
    ));
    socket.addEventListener("message", async event => {
      try {
        const text = typeof event.data === "string" ? event.data : await event.data.text();
        assert.ok(!text.includes(host.playerSessionToken) && !text.includes(guest.playerSessionToken));
        const frame = text.trimStart();
        if (frame.startsWith("ERROR")) throw new Error("STOMP authorization failed");
        if (frame.startsWith("CONNECTED"))
          socket.send(`SUBSCRIBE\nid:smoke\ndestination:/topic/rooms/${code}\nreceipt:subscribed\n\n\0`);
        if (frame.startsWith("RECEIPT") && !started) {
          started = true;
          await request(`/api/rooms/${code}/ready`, {
            method: "POST",
            headers: {"Content-Type": "application/json", "X-Player-Token": host.playerSessionToken},
            body: JSON.stringify({ready: true}),
          });
        }
        if (frame.startsWith("MESSAGE")) {
          const snapshot = JSON.parse(frame.slice(frame.indexOf("\n\n") + 2).replace(/\0.*$/s, ""));
          if (!snapshot.room.players.find(player => player.playerId === host.playerId)?.ready) return;
          assert.equal(snapshot.roomCode, code);
          assert.equal(snapshot.version, snapshot.room.version);
          assert.ok(snapshot.version > host.view.room.version);
          assert.equal(snapshot.room.players.length, 2);
          assert.ok(!("me" in snapshot));
          clearTimeout(timeout);
          resolve();
        }
      } catch (error) {fail(error);}
    });
  });
} finally {
  socket.close();
}
console.log("PASS: proxied health, SPA deep route, REST create/join/ready and authenticated STOMP snapshot; no live Wikipedia calls.");
