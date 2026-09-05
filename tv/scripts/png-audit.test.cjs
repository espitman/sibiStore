const { test, after } = require('node:test');
const assert = require('node:assert/strict');
const { mkdtempSync, writeFileSync, rmSync } = require('node:fs');
const { tmpdir } = require('node:os');
const path = require('node:path');
const { deflateSync } = require('node:zlib');
const { assertVisibleText } = require('./png-audit.cjs');
const directory = mkdtempSync(path.join(tmpdir(), 'sibi-png-audit-'));
after(() => rmSync(directory, { recursive: true }));
let sequence = 0;
const crc32 = bytes => {
  let crc = 0xffffffff;
  for (const byte of bytes) {
    crc ^= byte;
    for (let bit = 0; bit < 8; bit++) crc = (crc >>> 1) ^ ((crc & 1) ? 0xedb88320 : 0);
  }
  return (crc ^ 0xffffffff) >>> 0;
};
function chunk(name, data) {
  const type = Buffer.from(name);
  const length = Buffer.alloc(4); length.writeUInt32BE(data.length);
  const crc = Buffer.alloc(4); crc.writeUInt32BE(crc32(Buffer.concat([type, data])));
  return Buffer.concat([length, type, data, crc]);
}
function fixture({ channels = 4, filter = 0, ink = true, alpha = 255 } = {}) {
  const width = 32, height = 16, stride = width * channels;
  const raw = Buffer.alloc(height * stride);
  for (let y = 0; y < height; y++) for (let x = 0; x < width; x++) {
    const offset = y * stride + x * channels;
    // Light strokes on black, with an intentionally blank right half.
    const value = ink && x >= 3 && x <= 12 && y >= 3 && y <= 12 && (x % 3 === 0 || y === 3) ? 184 : 5;
    raw.fill(value, offset, offset + 3);
    if (channels === 4) raw[offset + 3] = alpha;
  }
  const encoded = Buffer.alloc(height * (stride + 1));
  for (let y = 0; y < height; y++) {
    encoded[y * (stride + 1)] = filter;
    for (let x = 0; x < stride; x++) {
      const at = y * stride + x;
      const left = x >= channels ? raw[at - channels] : 0;
      const above = y > 0 ? raw[at - stride] : 0;
      const upperLeft = y > 0 && x >= channels ? raw[at - stride - channels] : 0;
      let prediction = 0;
      if (filter === 1) prediction = left;
      if (filter === 2) prediction = above;
      if (filter === 3) prediction = Math.floor((left + above) / 2);
      if (filter === 4) {
        const estimate = left + above - upperLeft;
        prediction = [left, above, upperLeft].reduce((best, candidate) =>
          Math.abs(estimate - candidate) < Math.abs(estimate - best) ? candidate : best);
      }
      encoded[y * (stride + 1) + 1 + x] = (raw[at] - prediction) & 255;
    }
  }
  const header = Buffer.alloc(13);
  header.writeUInt32BE(width, 0); header.writeUInt32BE(height, 4);
  header[8] = 8; header[9] = channels === 4 ? 6 : 2;
  const file = path.join(directory, `${sequence++}.png`);
  const compressed = deflateSync(encoded);
  const split = Math.floor(compressed.length / 2);
  writeFileSync(file, Buffer.concat([Buffer.from('89504e470d0a1a0a', 'hex'), chunk('IHDR', header),
    chunk('IDAT', compressed.subarray(0, split)), chunk('IDAT', compressed.subarray(split)), chunk('IEND', Buffer.alloc(0))]));
  return file;
}
for (const channels of [3, 4]) for (const filter of [0, 1, 2, 3, 4]) {
  test(`${channels === 4 ? 'RGBA' : 'RGB'} filter ${filter}: visible strokes pass, empty crop fails`, () => {
    const file = fixture({ channels, filter });
    assert.doesNotThrow(() => assertVisibleText(file, [0, 0, 16, 16], 'present'));
    assert.throws(() => assertVisibleText(file, [16, 0, 32, 16], 'missing'), /missing rendered text/);
  });
}
test('a fully missing label fails even with valid bounds', () => {
  assert.throws(() => assertVisibleText(fixture({ ink: false }), [0, 0, 16, 16], 'missing'), /missing rendered text/);
});
test('transparent RGB values cannot count as visible ink', () => {
  assert.throws(() => assertVisibleText(fixture({ alpha: 0 }), [0, 0, 16, 16], 'transparent'), /missing rendered text/);
});
test('invalid and out-of-image text bounds fail', () => {
  const file = fixture();
  for (const bounds of [[-1, 0, 10, 10], [0, 0, 40, 16], [5, 5, 5, 8]]) {
    assert.throws(() => assertVisibleText(file, bounds, 'outside'), /bounds outside screenshot/);
  }
});
test('non-PNG input fails instead of silently passing', () => {
  const file = path.join(directory, 'not-png'); writeFileSync(file, 'not an image');
  assert.throws(() => assertVisibleText(file, [0, 0, 16, 16], 'invalid'), /Expected PNG/);
});
