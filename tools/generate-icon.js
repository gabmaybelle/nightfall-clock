// Draws the mod icon: a round gold clock with a cream face and its hands at about 6 o'clock
// (dusk), with a soft drop shadow on a transparent background. Pixel art on a 19x19 grid,
// drawn at 512x512. Run: node tools/generate-icon.js
const fs = require("fs");
const path = require("path");
const zlib = require("zlib");

const N = 512, G = 19, S = 26, OFF = Math.floor((N - G * S) / 2);
const cells = Array.from({ length: G }, () => Array(G).fill(null));
const K = [58, 42, 30], W = [244, 238, 222], Cc = [222, 210, 182];

// Round clock, 17x17: gold rim, cream face (shaded lower right), dark hands at about 6 o'clock.
const R = 8.4, cx = 8, cy = 8, GOLD = [240, 170, 40], GOLDD = [196, 124, 24], TICK = [150, 135, 110];
for (let y = 0; y < 17; y++) for (let x = 0; x < 17; x++) {
  const d = Math.hypot(x - cx, y - cy);
  let c = null;
  if (d <= R - 2.2) c = d > R - 3.6 && x + y > cx + cy + 3 ? Cc : W;
  else if (d <= R - 1) c = x + y > cx + cy ? GOLDD : GOLD;
  else if (d <= R) c = K;
  if (c) cells[y + 1][x + 1] = c;
}
for (const [x, y] of [[cx, cy - 5], [cx + 5, cy], [cx, cy + 5], [cx - 5, cy]]) cells[y + 1][x + 1] = TICK;
for (let i = 0; i <= 4; i++) cells[cy - i + 1][cx + 1] = K; // minute hand to 12
for (const [x, y] of [[cx - 1, cy + 1], [cx - 2, cy + 2], [cx - 3, cy + 3]]) cells[y + 1][x + 1] = K; // hour hand toward 7
cells[cy + 1][cx + 1] = K;

// Draw with a soft drop shadow one cell down and right.
const img = new Uint8Array(N * N * 4);
const fill = (gx, gy, rgb, a) => {
  for (let yy = 0; yy < S; yy++) for (let xx = 0; xx < S; xx++) {
    const X = OFF + gx * S + xx, Y = OFF + gy * S + yy;
    if (X < 0 || Y < 0 || X >= N || Y >= N) continue;
    const i = (Y * N + X) * 4;
    img[i] = rgb[0]; img[i + 1] = rgb[1]; img[i + 2] = rgb[2]; img[i + 3] = a;
  }
};
for (let y = 0; y < G; y++) for (let x = 0; x < G; x++)
  if (cells[y][x] && (y + 1 >= G || x + 1 >= G || !cells[y + 1][x + 1])) fill(x + 1, y + 1, [0, 20, 12], 100);
for (let y = 0; y < G; y++) for (let x = 0; x < G; x++) if (cells[y][x]) fill(x, y, cells[y][x], 255);

// Encode as an RGBA PNG.
const rows = [];
for (let y = 0; y < N; y++) { const r = Buffer.alloc(1 + N * 4); r.set(img.subarray(y * N * 4, (y + 1) * N * 4), 1); rows.push(r); }
const T = []; for (let n = 0; n < 256; n++) { let c = n; for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1; T[n] = c >>> 0; }
const crc = (b) => { let x = 0xffffffff; for (const v of b) x = T[(x ^ v) & 255] ^ (x >>> 8); return (x ^ 0xffffffff) >>> 0; };
const chunk = (t, d) => { const l = Buffer.alloc(4); l.writeUInt32BE(d.length); const td = Buffer.concat([Buffer.from(t), d]); const c = Buffer.alloc(4); c.writeUInt32BE(crc(td)); return Buffer.concat([l, td, c]); };
const ih = Buffer.alloc(13); ih.writeUInt32BE(N, 0); ih.writeUInt32BE(N, 4); ih[8] = 8; ih[9] = 6;
const out = path.join(__dirname, "..", "src", "main", "resources", "assets", "nightfallclock", "icon.png");
fs.writeFileSync(out, Buffer.concat([Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]), chunk("IHDR", ih), chunk("IDAT", zlib.deflateSync(Buffer.concat(rows), { level: 9 })), chunk("IEND", Buffer.alloc(0))]));
console.log("wrote", out);
