const { execFile } = require('node:child_process');
const { promisify } = require('node:util');
const path = require('node:path');
const run = promisify(execFile);
const ICON_REVISION = 1;
const escape = value => String(value).replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&apos;'}[c]));

function parseTree(text) {
  const stack = []; let root;
  for (const line of text.split('\n')) {
    const element = line.match(/^(\s*)E: ([\w-]+)/);
    if (element) {
      const node = { tag: element[2], attrs: {}, children: [], indent: element[1].length };
      while (stack.length && stack.at(-1).indent >= node.indent) stack.pop();
      if (stack.length) stack.at(-1).children.push(node); else root = node;
      stack.push(node);
    } else {
      const attr = line.match(/^\s*A: (?:[^\s]*:)?([\w]+)(?:\([^)]*\))?=(.*)$/);
      if (attr && stack.length) {
        const quoted = attr[2].match(/^"((?:\\.|[^"\\])*)"/);
        stack.at(-1).attrs[attr[1]] = quoted ? JSON.parse(`"${quoted[1]}"`) : attr[2].trim();
      }
    }
  }
  if (!root) throw new Error('Icon XML has no drawable');
  return root;
}
function parseResources(text) {
  const result = new Map(); let id;
  for (const line of text.split('\n')) {
    const resource = line.match(/^\s*resource (0x[\da-f]+) /i);
    if (resource) { id = resource[1].toLowerCase(); result.set(id, []); }
    else {
      const value = line.match(/^\s+\(([^)]*)\) (.+)$/);
      if (id && value) result.get(id).push({ config: value[1], value: value[2] });
    }
  }
  return result;
}
function color(value) {
  if (/^#[\da-f]{8}$/i.test(value)) return `#${value.slice(3)}${value.slice(1,3)}`;
  if (/^#[\da-f]{6}$/i.test(value)) return value;
  throw new Error(`Unsupported icon color: ${value}`);
}
const number = (value, fallback = 0) => {
  if (value == null) return fallback;
  const n = Number.parseFloat(value);
  if (!Number.isFinite(n)) throw new Error('Invalid drawable number');
  return n;
};

async function iconSvg(file, iconPath, toolDir) {
  let resources; let sequence = 0;
  const dump = async (...args) => (await run(path.join(toolDir,'aapt2'), ['dump',...args], { timeout: 45000, maxBuffer: 16 * 1024 * 1024 })).stdout;
  async function resolve(value, depth) {
    if (depth > 20) throw new Error('Icon resource nesting is too deep');
    if (!value?.startsWith('@')) return value;
    const frameworkColor = {'@0x0106000b':'#ffffffff','@0x0106000c':'#ff000000','@0x0106000d':'#00000000'}[value.toLowerCase()];
    if (frameworkColor) return frameworkColor;
    resources ||= parseResources(await dump('resources', file));
    const choices = resources.get(value.slice(1).toLowerCase());
    if (!choices?.length) throw new Error(`Missing icon resource ${value}`);
    // Prefer the default/day resource at a high raster density.
    const rank = c => (c.config.includes('night') ? -10000 : 0) +
      (c.config === '' ? 1000 : 0) + (c.config.includes('anydpi') ? 900 : 0) +
      ({xxxhdpi:640,xxhdpi:480,xhdpi:320,hdpi:240,mdpi:160}[c.config.split('-')[0]] || 0);
    const chosen = [...choices].sort((a,b) => rank(b)-rank(a))[0].value;
    const entry = chosen.match(/^\(file\) (\S+)/)?.[1] || chosen.replace(/^\(reference\) /,'');
    return resolve(entry, depth + 1);
  }
  async function paint(value, fallback = '#00000000') { return color(await resolve(value ?? fallback, 0)); }
  async function drawable(value, depth = 0) {
    value = await resolve(value, depth);
    if (value.startsWith('#')) return `<rect width="108" height="108" fill="${await paint(value)}"/>`;
    if (!/^res\/[\w./-]+$/.test(value) || value.includes('..')) throw new Error('Invalid icon entry');
    if (/\.(png|webp|jpe?g)$/i.test(value)) {
      const {stdout} = await run('/usr/bin/unzip',['-p',file,value],{encoding:'buffer',maxBuffer:4*1024*1024,timeout:10000});
      const mime = path.extname(value).slice(1).replace('jpg','jpeg');
      return `<image width="108" height="108" href="data:image/${mime};base64,${stdout.toString('base64')}"/>`;
    }
    if (depth > 20) throw new Error('Icon drawable nesting is too deep');
    return nodeSvg(parseTree(await dump('xmltree',file,'--file',value)),depth+1);
  }
  async function nodeSvg(node, depth) {
    const a = node.attrs;
    const children = async () => (await Promise.all(node.children.map(n => nodeSvg(n,depth+1)))).join('');
    if (depth > 30) throw new Error('Icon nesting is too deep');
    if (node.tag === 'vector') return `<svg width="108" height="108" viewBox="0 0 ${number(a.viewportWidth,108)} ${number(a.viewportHeight,108)}" opacity="${number(a.alpha,1)}">${await children()}</svg>`;
    if (node.tag === 'group') return `<g transform="translate(${number(a.translateX)+number(a.pivotX)} ${number(a.translateY)+number(a.pivotY)}) rotate(${number(a.rotation)}) scale(${number(a.scaleX,1)} ${number(a.scaleY,1)}) translate(${-number(a.pivotX)} ${-number(a.pivotY)})">${await children()}</g>`;
    if (node.tag === 'path') {
      let fill = await paint(a.fillColor); let definitions = '';
      const gradient = node.children.flatMap(n => n.children).find(n => n.tag === 'gradient');
      if (gradient) {
        const g = gradient.attrs; const id = `gradient${sequence++}`;
        const stops = gradient.children.length ? gradient.children.map(n=>n.attrs) : [
          {offset:0,color:g.startColor}, ...(g.centerColor ? [{offset:0.5,color:g.centerColor}] : []), {offset:1,color:g.endColor}];
        const body = (await Promise.all(stops.map(async s => `<stop offset="${number(s.offset)}" stop-color="${await paint(s.color)}"/>`))).join('');
        if (number(g.type) === 2) throw new Error('Sweep gradient icon is unsupported');
        definitions = number(g.type) === 1 ? `<radialGradient id="${id}" gradientUnits="userSpaceOnUse" cx="${number(g.centerX)}" cy="${number(g.centerY)}" r="${number(g.gradientRadius)}">${body}</radialGradient>` : `<linearGradient id="${id}" gradientUnits="userSpaceOnUse" x1="${number(g.startX)}" y1="${number(g.startY)}" x2="${number(g.endX)}" y2="${number(g.endY)}">${body}</linearGradient>`;
        fill = `url(#${id})`;
      }
      return `${definitions}<path d="${escape(a.pathData || '')}" fill="${fill}" fill-opacity="${number(a.fillAlpha,1)}" fill-rule="${number(a.fillType)===1?'evenodd':'nonzero'}" stroke="${await paint(a.strokeColor)}" stroke-opacity="${number(a.strokeAlpha,1)}" stroke-width="${number(a.strokeWidth)}" stroke-linecap="${['butt','round','square'][number(a.strokeLineCap)] || 'butt'}" stroke-linejoin="${['miter','round','bevel'][number(a.strokeLineJoin)] || 'miter'}"/>`;
    }
    if (node.tag === 'adaptive-icon') {
      const layers = node.children.filter(n => ['background','foreground'].includes(n.tag));
      const body = (await Promise.all(layers.map(n => n.attrs.drawable ? drawable(n.attrs.drawable,depth+1) : nodeSvg(n.children[0],depth+1)))).join('');
      // Android adaptive drawables have a 108dp canvas with a central 72dp mask.
      return `<svg width="108" height="108" viewBox="18 18 72 72">${body}</svg>`;
    }
    if (node.tag === 'inset') return `<g transform="translate(${number(a.insetLeft)} ${number(a.insetTop)})">${a.drawable ? await drawable(a.drawable,depth+1) : await children()}</g>`;
    if (node.tag === 'bitmap') return drawable(a.src,depth+1);
    if (node.tag === 'layer-list' || node.tag === 'item') return a.drawable ? drawable(a.drawable,depth+1) : children();
    throw new Error(`Unsupported icon drawable: ${node.tag}`);
  }
  return `<svg xmlns="http://www.w3.org/2000/svg" width="256" height="256" viewBox="0 0 108 108">${await drawable(iconPath)}</svg>`;
}

async function rasterize(svg) {
  const { BrowserWindow, app } = require('electron');
  const keepAlive = () => {};
  app.on('window-all-closed',keepAlive);
  const window = new BrowserWindow({show:false,width:256,height:256,webPreferences:{sandbox:true,contextIsolation:true,nodeIntegration:false,offscreen:true}});
  try {
    await window.loadURL('data:text/html,<meta http-equiv="Content-Security-Policy" content="default-src %27none%27; img-src data:">');
    const uri = `data:image/svg+xml;base64,${Buffer.from(svg).toString('base64')}`;
    return await window.webContents.executeJavaScript(`new Promise((resolve,reject)=>{
      const image=new Image(); const timer=setTimeout(()=>reject(new Error('Icon render timeout')),10000);
      image.onerror=()=>{clearTimeout(timer);reject(new Error('Icon render failed'))};
      image.onload=()=>{clearTimeout(timer);const c=document.createElement('canvas');c.width=256;c.height=256;c.getContext('2d').drawImage(image,0,0,256,256);resolve(c.toDataURL('image/png'))};
      image.src=${JSON.stringify(uri)};
    })`);
  } finally { window.destroy(); app.removeListener('window-all-closed',keepAlive); }
}
async function extractIcon(file, iconPath, toolDir) {
  if (!iconPath) throw new Error('APK has no application icon');
  if (/\.(png|webp|jpe?g)$/i.test(iconPath)) {
    const {stdout} = await run('/usr/bin/unzip',['-p',file,iconPath],{encoding:'buffer',maxBuffer:4*1024*1024,timeout:10000});
    return `data:image/${path.extname(iconPath).slice(1).replace('jpg','jpeg')};base64,${stdout.toString('base64')}`;
  }
  return rasterize(await iconSvg(file,iconPath,toolDir));
}
module.exports = {ICON_REVISION,parseTree,parseResources,color,iconSvg,rasterize,extractIcon};
