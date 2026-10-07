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

function srgbToLinear(c) {
  return c <= 0.04045 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4;
}

function linearToSrgb(c) {
  const clipped = Math.max(0, Math.min(1, c));
  const encoded = clipped <= 0.0031308 ? 12.92 * clipped : 1.055 * clipped ** (1 / 2.4) - 0.055;
  return Math.max(0, Math.min(255, Math.trunc(encoded * 255)));
}

function fromArgb(c) {
  const r = srgbToLinear(c.r / 255);
  const g = srgbToLinear(c.g / 255);
  const b = srgbToLinear(c.b / 255);
  const l_ = Math.cbrt(0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b);
  const m_ = Math.cbrt(0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b);
  const s_ = Math.cbrt(0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b);
  const L = 0.2104542553 * l_ + 0.7936177850 * m_ - 0.0040720468 * s_;
  const a = 1.9779984951 * l_ - 2.4285922050 * m_ + 0.4505937099 * s_;
  const bLab = 0.0259040371 * l_ + 0.7827717662 * m_ - 0.8086757660 * s_;
  const C = Math.sqrt(a * a + bLab * bLab);
  let H = (Math.atan2(bLab, a) * 180) / Math.PI;
  if (H < 0) H += 360;
  return { l: L, c: C, h: H };
}

function toOpaque(ok) {
  const hRad = (ok.h * Math.PI) / 180;
  const a = ok.c * Math.cos(hRad);
  const bLab = ok.c * Math.sin(hRad);
  const l_ = ok.l + 0.3963377774 * a + 0.2158037573 * bLab;
  const m_ = ok.l - 0.1055613458 * a - 0.0638541728 * bLab;
  const s_ = ok.l - 0.0894841775 * a - 1.2914855480 * bLab;
  const l = l_ * l_ * l_;
  const m = m_ * m_ * m_;
  const s = s_ * s_ * s_;
  return {
    r: linearToSrgb(+4.0767416621 * l - 3.3077115913 * m + 0.2309699292 * s),
    g: linearToSrgb(-1.2684380046 * l + 2.6097574011 * m - 0.3413193965 * s),
    b: linearToSrgb(-0.0041960863 * l - 0.7034186147 * m + 1.7076147010 * s),
    a: 255,
  };
}

function deriveShadow(primary) {
  const src = fromArgb(primary);
  return toOpaque({ l: Math.max(0, Math.min(1, src.l - 0.12)), c: src.c * 1.05, h: src.h });
}

function deriveHighlight(primary) {
  const src = fromArgb(primary);
  return toOpaque({ l: Math.max(0, Math.min(1, src.l + 0.10)), c: src.c * 0.9, h: src.h });
}

/**
 * ColorSlots.resolve (AP-9): overrides, pack slot links, OKLCH-derived shadow/highlight
 * from an overridden primary, then asset defaults.
 */
export function resolveColors(assets, overrides = {}, unlinked = [], slotLinks = {}) {
  const declared = new Map();
  for (const asset of assets) {
    if (!asset.picture) continue;
    for (const [slot, hex] of Object.entries(asset.colorSlots || {})) if (!declared.has(slot)) declared.set(slot, hex);
  }
  const slots = new Set([...declared.keys(), ...Object.keys(overrides), ...Object.keys(slotLinks)]);
  const memo = {};
  const visiting = new Set();
  function resolveOne(slot) {
    if (memo[slot]) return memo[slot];
    if (visiting.has(slot)) return parseHex(NEUTRAL);
    visiting.add(slot);
    try {
      const override = parseHex(overrides[slot]);
      if (override) return (memo[slot] = override);
      if (!unlinked.includes(slot)) {
        const source = slotLinks[slot];
        if (source) return (memo[slot] = resolveOne(source));
        const suffix = slot.endsWith('.shadow') ? '.shadow' : slot.endsWith('.highlight') ? '.highlight' : null;
        if (suffix) {
          const primarySlot = `${slot.slice(0, -suffix.length)}.primary`;
          const primaryOverride = parseHex(overrides[primarySlot]);
          if (primaryOverride) {
            const primary = resolveOne(primarySlot);
            return (memo[slot] = suffix === '.shadow' ? deriveShadow(primary) : deriveHighlight(primary));
          }
        }
      }
      return (memo[slot] = parseHex(declared.get(slot)) || parseHex(NEUTRAL));
    } finally {
      visiting.delete(slot);
    }
  }
  for (const slot of [...slots].sort()) resolveOne(slot);
  return memo;
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
 * opts: { framing: 'head'|'bust', frameStyle: 'squircle'|'circle'|'none', overrides, unlinked, slotLinks }
 */
export function drawAvatar(ctx, size, assets, opts = {}) {
  const framing = FRAMING[opts.framing || 'head'];
  const colors = resolveColors(assets, opts.overrides, opts.unlinked, opts.slotLinks);
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
