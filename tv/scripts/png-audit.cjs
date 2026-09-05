// Read adb's 8-bit RGB/RGBA PNGs without image-editing dependencies.
const { readFileSync } = require('node:fs');
const { inflateSync } = require('node:zlib');
const assert = require('node:assert/strict');
function readScreenshot(file) {
  const png = readFileSync(file);
  assert.equal(png.subarray(0, 8).toString('hex'), '89504e470d0a1a0a', 'Expected PNG');
  let width, height, channels;
  const chunks = [];
  for (let offset = 8; offset < png.length;) {
    const length = png.readUInt32BE(offset);
    const type = png.toString('ascii', offset + 4, offset + 8);
    const data = png.subarray(offset + 8, offset + 8 + length);
    if (type === 'IHDR') {
      width = data.readUInt32BE(0); height = data.readUInt32BE(4);
      assert(data[8] === 8 && [2, 6].includes(data[9]) && data[12] === 0, 'Expected non-interlaced 8-bit RGB/RGBA screenshot');
      channels = data[9] === 6 ? 4 : 3;
    } else if (type === 'IDAT') chunks.push(data);
    offset += length + 12;
  }
  const encoded = inflateSync(Buffer.concat(chunks));
  const stride = width * channels;
  assert.equal(encoded.length, height * (stride + 1));
  const pixels = Buffer.alloc(height * stride);
  const paeth = (a, b, c) => {
    const p = a + b - c, pa = Math.abs(p - a), pb = Math.abs(p - b), pc = Math.abs(p - c);
    return pa <= pb && pa <= pc ? a : pb <= pc ? b : c;
  };
  for (let y = 0; y < height; y++) {
    const filter = encoded[y * (stride + 1)];
    assert(filter <= 4, 'Unsupported PNG filter');
    for (let x = 0; x < stride; x++) {
      const index = y * stride + x;
      const a = x >= channels ? pixels[index - channels] : 0;
      const b = y ? pixels[index - stride] : 0;
      const c = y && x >= channels ? pixels[index - stride - channels] : 0;
      const prediction = [0, a, b, Math.floor((a + b) / 2), paeth(a, b, c)][filter];
      pixels[index] = (encoded[y * (stride + 1) + 1 + x] + prediction) & 255;
    }
  }
  return { width, height, pixels, channels };
}
function assertVisibleText(file, box, label) {
  const { width, height, pixels, channels } = readScreenshot(file);
  const [x1, y1, x2, y2] = box.map(Math.round);
  assert(x1 >= 0 && y1 >= 0 && x2 <= width && y2 <= height && x2 > x1 && y2 > y1, 'Text bounds outside screenshot');
  let ink = 0;
  for (let y = y1; y < y2; y++) for (let x = x1; x < x2; x++) {
    const p = (y * width + x) * channels;
    if ((channels === 3 || pixels[p + 3] > 200) && pixels[p] > 100 && pixels[p + 1] > 100 && pixels[p + 2] > 100) ink++;
  }
  assert(ink > (x2 - x1) * (y2 - y1) * 0.03, `Screenshot is missing rendered text: ${label}; accessibility bounds alone are insufficient`);
}
module.exports = { assertVisibleText };
