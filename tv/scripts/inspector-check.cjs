// Exercise an existing paused/failed transfer without starting or installing it.
const { execFileSync } = require('node:child_process');
const { mkdirSync } = require('node:fs');
const path = require('node:path');
const assert = require('node:assert/strict');
const { assertVisibleText } = require('./png-audit.cjs');
const [serial, title, ...extra] = process.argv.slice(2);
assert(serial && title && !extra.length, 'Usage: bash tv/scripts/inspector-check.sh <QA-serial> <app-title-with-existing-paused-transfer>');
const output = path.resolve(__dirname, '../test-results');
mkdirSync(output, { recursive: true });
const call = (...args) => execFileSync('bash', [path.join(__dirname, 'device.sh'), serial, ...args], { encoding: 'utf8', maxBuffer: 8 * 1024 * 1024 });
const settle = () => Atomics.wait(new Int32Array(new SharedArrayBuffer(4)), 0, 0, 1500);
function hierarchy() {
  return [...call('ui').matchAll(/<node\b([^>]+)>?/g)].map(match => {
    const node = Object.fromEntries([...match[1].matchAll(/([\w-]+)="([^"]*)"/g)].map(a => [a[1], a[2]]));
    node.box = (node.bounds?.match(/\d+/g) || []).map(Number);
    return node;
  });
}
call('shell', 'am', 'start', '-S', '-W', '-n', 'com.sibi.store.tv/.MainActivity');
settle();
const initial = hierarchy();
const root = initial.find(n => n.box[0] === 0 && n.box[1] === 0 && n.box[2] > n.box[3]);
assert(root, 'Expected landscape viewport');
const [,, width, height] = root.box;
const card = initial.find(n => n.text === title && n.box[0] > width * 0.19 && n.box[2] < width * 0.66);
assert(card, 'Requested app card must be visible in the library');
const [x1, y1, x2, y2] = card.box;
const centerX = (x1 + x2) / 2;
const centerY = (y1 + y2) / 2;
let reached = false;
for (let step = 0; step < 8; step++) {
  const focused = hierarchy().find(n => n.focused === 'true');
  if (focused && focused.box[0] <= centerX && focused.box[2] >= centerX && focused.box[1] <= centerY && focused.box[3] >= centerY) {
    reached = true;
    break;
  }
  // Use remote keys, not touch input (touch mode clears non-touch focus on Android TV).
  const direction = !focused || focused.box[2] < centerX ? 'KEYCODE_DPAD_RIGHT' : 'KEYCODE_DPAD_LEFT';
  call('shell', 'input', 'keyevent', direction);
  settle();
}
assert(reached, 'Could not focus the requested visible app with the remote');
// OK on a confirmed library card only moves to its action; never press OK again there.
call('shell', 'input', 'keyevent', 'KEYCODE_DPAD_CENTER');
settle();
const before = hierarchy();
assert(before.some(n => n.text === 'Resume'), 'Use a paused/failed transfer so this test never needs to download');
assert(before.some(n => n.focused === 'true' && n.box[0] > width * 0.68), 'Inspector action must have focus');
const footer = before.find(n => n.text === 'From your Mac');
assert(footer && footer.box[3] < height * 0.86 && footer.box[3] - footer.box[1] > height * 0.024, 'Progress must not push the footer out of view');
const header = before.find(n => n.text === 'Android app' && n.box[0] > width * 0.68);
assert(header, 'Expected initial inspector header');
call('screenshot', path.join(output, 'paused-inspector.png'));
assertVisibleText(path.join(output, 'paused-inspector.png'), footer.box, 'From your Mac before scroll');
call('shell', 'input', 'keyevent', 'KEYCODE_DPAD_DOWN');
settle();
const down = hierarchy();
const movedHeader = down.find(n => n.text === 'Android app' && n.box[0] > width * 0.68);
assert(!movedHeader || movedHeader.box[1] < header.box[1], 'D-pad Down must scroll overflowing details');
assert.deepEqual(down.find(n => n.text === 'From your Mac')?.box, footer.box, 'Footer must remain fixed while scrolling');
assert(down.some(n => n.text === 'Resume'), 'Scrolling must not start a download');
call('screenshot', path.join(output, 'paused-inspector-scrolled.png'));
assertVisibleText(path.join(output, 'paused-inspector-scrolled.png'), footer.box, 'From your Mac after scroll');
call('shell', 'input', 'keyevent', 'KEYCODE_DPAD_UP');
settle();
const up = hierarchy();
assert.deepEqual(up.find(n => n.text === 'Android app' && n.box[0] > width * 0.68)?.box, header.box, 'D-pad Up must restore the inspector position');
console.log('Paused-transfer footer visibility, D-pad Down/Up scrolling and unchanged Resume state passed.');
