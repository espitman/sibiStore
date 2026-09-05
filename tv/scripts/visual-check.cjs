const { execFileSync } = require('node:child_process');
const { mkdirSync, writeFileSync } = require('node:fs');
const path = require('node:path');
const assert = require('node:assert/strict');
const { assertVisibleText } = require('./png-audit.cjs');
const [serial, ...extra] = process.argv.slice(2);
assert(serial && !extra.length, 'Usage: bash tv/scripts/visual-check.sh <QA-device-serial>');
const output = path.resolve(__dirname, '../test-results');
mkdirSync(output, { recursive: true });
const call = (...args) => execFileSync('bash', [path.join(__dirname, 'device.sh'), serial, ...args], { encoding: 'utf8', maxBuffer: 8 * 1024 * 1024 });
const settle = () => Atomics.wait(new Int32Array(new SharedArrayBuffer(4)), 0, 0, 5000);
const launch = () => {
  call('shell', 'am', 'start', '-S', '-W', '-n', 'com.sibi.store.tv/.MainActivity');
  settle(); // Activity launch completion precedes Compose's first fully rendered frame.
};
function hierarchy() {
  const xml = call('ui'); // uiautomator waits for an idle UI before returning.
  return [...xml.matchAll(/<node\b([^>]+)>?/g)].map(match => {
    const node = Object.fromEntries([...match[1].matchAll(/([\w-]+)="([^"]*)"/g)].map(a => [a[1], a[2]]));
    node.box = (node.bounds?.match(/\d+/g) || []).map(Number);
    return node;
  });
}
launch();
const nodes = hierarchy();
assert(nodes.some(n => n.text === 'Mac connected'), 'Start the Mac server and connect the QA TV before checking action focus.');
const root = nodes.find(n => n.box[0] === 0 && n.box[1] === 0 && n.box[2] > n.box[3]);
assert(root, 'A landscape TV viewport is required');
const [,, width, height] = root.box;
const find = text => {
  const node = nodes.find(n => n.text === text);
  assert(node, `Missing visible text: ${text}. Use a populated library with a normal-length selected title.`);
  return node.box;
};
const near = (actual, target, tolerance, message) => assert(Math.abs(actual - target) <= tolerance, `${message}: ${actual} vs ${target}`);
near(find('My apps')[0] / width, 344 / 1680, 0.01, 'Heading left edge');
near(find('Search')[0] / width, 1132 / 1680, 0.02, 'Search must stay near the grid/inspector junction');
near((find('My apps')[3] - find('My apps')[1]) / height, 0.061, 0.008, 'Heading text box size');
near(find('Select')[0] / width, 833 / 1680, 0.02, 'Select legend column');
near(find('Back')[0] / width, 1254 / 1680, 0.02, 'Back legend column');
assert(find('From your Mac')[3] < height * 0.86, 'Inspector footer must be fully visible');
for (const node of nodes.filter(n => /^(Installed|Update available|Ready to install)$/.test(n.text) && n.box[0] < width * 0.66)) {
  assert(node.box[3] - node.box[1] > height * 0.024, 'Card status text must not be vertically clipped');
}
const focused = nodes.find(n => n.focused === 'true');
assert(focused && focused.box[0] > width * 0.19 && focused.box[2] < width * 0.66, 'Initial focus must be in the app grid');
writeFileSync(path.join(output, 'reference-aligned-ui.json'), JSON.stringify(nodes, null, 2));
call('screenshot', path.join(output, 'reference-aligned-library.png'));
assertVisibleText(path.join(output, 'reference-aligned-library.png'), find('My apps'), 'My apps');
assertVisibleText(path.join(output, 'reference-aligned-library.png'), find('From your Mac'), 'From your Mac');
call('shell', 'input', 'keyevent', 'KEYCODE_DPAD_CENTER');
settle();
const action = hierarchy().find(n => n.focused === 'true');
assert(action && action.box[0] > width * 0.68, 'D-pad OK must move from the app card to its inspector action');
call('screenshot', path.join(output, 'reference-aligned-action.png'));
assertVisibleText(path.join(output, 'reference-aligned-action.png'), find('My apps'), 'My apps with action focused');
assertVisibleText(path.join(output, 'reference-aligned-action.png'), find('From your Mac'), 'From your Mac with action focused');
launch();
call('screenshot', path.join(output, 'reference-aligned-library.png'));
assertVisibleText(path.join(output, 'reference-aligned-library.png'), find('My apps'), 'My apps after restart');
assertVisibleText(path.join(output, 'reference-aligned-library.png'), find('From your Mac'), 'From your Mac after restart');
console.log('TV layout bounds, unclipped labels/footer and D-pad action focus passed. Review captured PNGs for color, icon and font fidelity.');
