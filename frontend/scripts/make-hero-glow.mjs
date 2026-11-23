import { deflateSync } from "node:zlib";
import { writeFileSync } from "node:fs";

const W = 1400;
const H = 900;

function crc32(buf) {
  let c = ~0;
  for (let i = 0; i < buf.length; i++) {
    c ^= buf[i];
    for (let k = 0; k < 8; k++) c = (c >>> 1) ^ (0xedb88320 & -(c & 1));
  }
  return ~c >>> 0;
}

function chunk(type, data) {
  const t = Buffer.from(type);
  const len = Buffer.alloc(4);
  len.writeUInt32BE(data.length);
  const body = Buffer.concat([t, data]);
  const crc = Buffer.alloc(4);
  crc.writeUInt32BE(crc32(body));
  return Buffer.concat([len, body, crc]);
}

function mix(a, b, t) {
  return a + (b - a) * t;
}

function over(dr, dg, db, sr, sg, sb, a) {
  return [sr * a + dr * (1 - a), sg * a + dg * (1 - a), sb * a + db * (1 - a)];
}

const raw = Buffer.alloc(H * (1 + W * 3));
for (let y = 0; y < H; y++) {
  const row = y * (1 + W * 3);
  raw[row] = 0;
  const ty = y / (H - 1);
  for (let x = 0; x < W; x++) {
    const tx = x / (W - 1);
    let r = 20;
    let g = 9;
    let b = 30;
    const linA = (1 - ty / 0.88) * 0.15;
    if (linA > 0) [r, g, b] = over(r, g, b, 32, 8, 48, linA);

    const pd = Math.hypot((tx - 0.48) / 0.6, ty / 0.8);
    if (pd < 0.68) {
      const a = 0.55 * (1 - pd / 0.68);
      [r, g, b] = over(r, g, b, 110, 28, 170, a);
    }

    const od = Math.hypot((tx - 0.08) / 0.72, ty / 0.9);
    if (od < 0.7) {
      let sr;
      let sg;
      let sb;
      let a;
      if (od <= 0.42) {
        const u = od / 0.42;
        sr = mix(255, 214, u);
        sg = mix(112, 42, u);
        sb = mix(36, 120, u);
        a = mix(0.92, 0.28, u);
      } else {
        const u = (od - 0.42) / (0.7 - 0.42);
        sr = 214;
        sg = 42;
        sb = 120;
        a = mix(0.28, 0, u);
      }
      [r, g, b] = over(r, g, b, sr, sg, sb, Math.min(1, Math.max(0, a)));
    }

    const i = row + 1 + x * 3;
    raw[i] = Math.round(r);
    raw[i + 1] = Math.round(g);
    raw[i + 2] = Math.round(b);
  }
}

const ihdr = Buffer.alloc(13);
ihdr.writeUInt32BE(W, 0);
ihdr.writeUInt32BE(H, 4);
ihdr[8] = 8;
ihdr[9] = 2;
const png = Buffer.concat([
  Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]),
  chunk("IHDR", ihdr),
  chunk("IDAT", deflateSync(raw)),
  chunk("IEND", Buffer.alloc(0)),
]);
writeFileSync(new URL("../public/hero-glow.png", import.meta.url), png);

const corner = (x, y) => {
  const i = y * (1 + W * 3) + 1 + x * 3;
  return [raw[i], raw[i + 1], raw[i + 2]];
};
console.log("bytes", png.length);
console.log("corner", corner(0, 0));
console.log("hotspot", corner(Math.round(0.08 * W), 8));
console.log("mid", corner(Math.round(W / 2), Math.round(H / 2)));
console.log("bottom", corner(10, H - 1));
