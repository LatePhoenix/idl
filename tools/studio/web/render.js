// Preview renderer: draws picture JSON on a 2D canvas the way the app's AvatarRenderer and
// CanvasVectorAssetRenderer do. It's the Studio's tier-1 preview (ART_STUDIO.md §5.6). The
// contact sheets recorded by PackContactSheetTest stay the source of truth.

export const FRAMING = {
  head: { originX: -40, originY: 0, size: 1104 },
  bust: { originX: -128, originY: 32, size: 1280 },
};

// AssetCategory.defaultZ, the tie-break inside a band (CompositeOrder).
const CATEGORY_Z = {
  base: 20, signature_feature: 30, face_eye: 50, face_brow: 60, face_mouth: 70,
  expression_overlay: 80, face_accessory: 90, head_accessory: 100, body_accessory: 110,
  foreground_prop: 120, scene: 10, frame: 130, hair: 25, facial_hair: 35, jewelry: 95,
  top: 36, outerwear: 38,
};
const NEUTRAL = '#9E9E9E';
const CHROME = 200;

function parseHex(raw) {
  if (typeof raw !== 'string') return null;
  const hex = raw.replace(/^#/, '');
  if (!/^[0-9a-fA-F]{6}([0-9a-fA-F]{2})?$/.test(hex)) return null;
  if (hex.length === 6) return { r: parseInt(hex.slice(0, 2), 16), g: parseInt(hex.slice(2, 4), 16), b: parseInt(hex.slice(4, 6), 16), a: 255 };
  return { a: parseInt(hex.slice(0, 2), 16), r: parseInt(hex.slice(2, 4), 16), g: parseInt(hex.slice(4, 6), 16), b: parseInt(hex.slice(6, 8), 16) };
}

function mix(c, toward, t) {
  const ch = (a, b) => Math.max(0, Math.min(255, Math.trunc(a * (1 - t) + b * t)));
  return { r: ch(c.r, toward.r), g: ch(c.g, toward.g), b: ch(c.b, toward.b), a: 255 };
}

/** ColorSlots.resolve: manifest defaults in layer order, overrides, then derived shadow and highlight. */
export function resolveColors(assets, overrides = {}, unlinked = []) {
  const declared = new Map();
  for (const asset of assets) {
    if (!asset.picture) continue;
    for (const [slot, hex] of Object.entries(asset.colorSlots || {})) if (!declared.has(slot)) declared.set(slot, hex);
  }
  const colors = {};
  for (const slot of [...declared.keys()].sort()) {
    const override = parseHex(overrides[slot]);
    colors[slot] = override || derived(slot, overrides, unlinked) || parseHex(declared.get(slot)) || parseHex(NEUTRAL);
  }
  return colors;
}

function derived(slot, overrides, unlinked) {
  const suffix = slot.endsWith('.shadow') ? '.shadow' : slot.endsWith('.highlight') ? '.highlight' : null;
  if (!suffix || unlinked.includes(slot)) return null;
  const primary = parseHex(overrides[slot.slice(0, -suffix.length) + '.primary']);
  if (!primary) return null;
  return suffix === '.shadow' ? mix(primary, { r: 0, g: 0, b: 0 }, 0.25) : mix(primary, { r: 255, g: 255, b: 255 }, 0.30);
}

const css = (c, opacity = 1) => `rgba(${c.r},${c.g},${c.b},${((c.a / 255) * opacity).toFixed(4)})`;

/** CompositeOrder.ops for vector assets: band, category z, asset id, part index. */
export function drawOps(assets) {
  const ops = [];
  for (const asset of [...assets].sort((a, b) => (a.id < b.id ? -1 : a.id > b.id ? 1 : 0))) {
    if (!asset.picture) continue;
    asset.picture.parts.forEach((part, index) => {
      ops.push({ band: part.zBand, z: CATEGORY_Z[asset.category] ?? 0, asset, index, part });
    });
  }
  return ops.sort((a, b) => a.band - b.band || a.z - b.z || (a.asset.id < b.asset.id ? -1 : a.asset.id > b.asset.id ? 1 : 0) || a.index - b.index);
}

const pathCache = new WeakMap();
function pathOf(picture, commands) {
  let cache = pathCache.get(picture);
  if (!cache) pathCache.set(picture, (cache = new Map()));
  if (!cache.has(commands)) cache.set(commands, new Path2D(commands));
  return cache.get(commands);
}

const OUTSIDE = 1 << 14;
function clipTo(ctx, path, mode) {
  if (mode === 'difference') {
    // Canvas has no clipOut. Subtract with an even-odd ring around the path. Exact unless the
    // clip path overlaps itself.
    const ring = new Path2D();
    ring.rect(-OUTSIDE, -OUTSIDE, 2 * OUTSIDE, 2 * OUTSIDE);
    ring.addPath(path);
    ctx.clip(ring, 'evenodd');
  } else {
    ctx.clip(path);
  }
}

/**
 * Draws one avatar into a size×size canvas context.
 * opts: { framing: 'head'|'bust', frameStyle: 'squircle'|'circle'|'none', overrides, unlinked }
 */
export function drawAvatar(ctx, size, assets, opts = {}) {
  const framing = FRAMING[opts.framing || 'head'];
  const colors = resolveColors(assets, opts.overrides, opts.unlinked);
  const ops = drawOps(assets);
  // AP-8 masks: a part may publish its path; other parts subscribe with clipBy.
  const masks = new Map();
  for (const op of ops) if (op.part.publishMask) masks.set(op.part.publishMask, { op, path: pathOf(op.asset.picture, op.part.commands) });

  ctx.save();
  ctx.clearRect(0, 0, size, size);
  const frame = opts.frameStyle || 'squircle';
  if (frame !== 'none') {
    const clip = new Path2D();
    if (frame === 'circle') clip.arc(size / 2, size / 2, size / 2, 0, Math.PI * 2);
    else clip.roundRect(0, 0, size, size, size * 0.26);
    ctx.clip(clip);
  }
  for (const op of ops) {
    if (op.band >= CHROME) continue;
    const { part, asset } = op;
    const picture = asset.picture;
    ctx.save();
    if (part.zBand !== 0) {
      const scale = size / framing.size;
      ctx.scale(scale, scale);
      ctx.translate(-framing.originX, -framing.originY);
    } else {
      ctx.scale(size / 1024, size / 1024);
    }
    applyTransform(ctx, asset.transform);
    applyTransform(ctx, asset.defaultTransform);
    if (part.clip) {
      const clip = (picture.clipPaths || []).find((c) => c.id === part.clip.id);
      if (clip) clipTo(ctx, pathOf(picture, clip.commands), part.clip.mode);
    }
    for (const sub of part.clipBy || []) {
      const mask = masks.get(sub.mask);
      if (mask && mask.op.asset !== asset) clipTo(ctx, mask.path, sub.mode);
    }
    const path = pathOf(picture, part.commands);
    const opacity = part.opacity ?? 1;
    const fill = part.fill || {};
    if (fill.slot) {
      ctx.fillStyle = css(colors[fill.slot] || parseHex(NEUTRAL), opacity);
    } else if (fill.linear || fill.radial) {
      const g = fill.linear
        ? ctx.createLinearGradient(fill.linear.x1, fill.linear.y1, fill.linear.x2, fill.linear.y2)
        : ctx.createRadialGradient(fill.radial.cx, fill.radial.cy, 0, fill.radial.cx, fill.radial.cy, fill.radial.r);
      for (const stop of (fill.linear || fill.radial).stops) {
        const c = colors[stop.slot] || parseHex(NEUTRAL);
        g.addColorStop(stop.offset, css(stop.alpha == null ? c : { ...c, a: Math.round(stop.alpha * 255) }));
      }
      ctx.fillStyle = g;
      ctx.globalAlpha = opacity;
    }
    ctx.fill(path, part.fillRule === 'evenodd' ? 'evenodd' : 'nonzero');
    ctx.globalAlpha = 1;
    if (part.stroke) {
      ctx.lineWidth = part.stroke.width;
      ctx.lineCap = part.stroke.cap || 'round';
      ctx.lineJoin = part.stroke.join || 'round';
      ctx.strokeStyle = css(colors[part.stroke.slot] || parseHex(NEUTRAL), opacity);
      ctx.stroke(path);
    }
    ctx.restore();
  }
  ctx.restore();
  return colors;
}

// CanvasVectorAssetRenderer.apply: flip, scale and rotate about the centre, then translate.
function applyTransform(ctx, t) {
  if (!t) return;
  if (t.flipHorizontal) { ctx.translate(1024, 0); ctx.scale(-1, 1); }
  const s = t.scale ?? 1;
  if (s !== 1) { ctx.translate(512, 512); ctx.scale(s, s); ctx.translate(-512, -512); }
  if (t.rotationDeg) { ctx.translate(512, 512); ctx.rotate((t.rotationDeg * Math.PI) / 180); ctx.translate(-512, -512); }
  ctx.translate(t.translateX || 0, t.translateY || 0);
}

/** A canvas at device resolution showing `size` CSS pixels. */
export function avatarCanvas(size, assets, opts) {
  const canvas = document.createElement('canvas');
  const dpr = opts?.exactPixels ? 1 : window.devicePixelRatio || 1;
  canvas.width = Math.round(size * dpr);
  canvas.height = Math.round(size * dpr);
  canvas.style.width = `${size}px`;
  canvas.style.height = `${size}px`;
  const ctx = canvas.getContext('2d');
  drawAvatar(ctx, canvas.width, assets, opts);
  return canvas;
}
