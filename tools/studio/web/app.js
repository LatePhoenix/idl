import { avatarCanvas } from './render.js';

// Editor categories, in the order of AVATAR_PROGRAM.md §3.4. `multi` mirrors AssetCategory.multiple.
const SLOTS = [
  { category: 'hair', label: 'Hair' },
  { category: 'facial_hair', label: 'Facial hair', multi: true },
  { category: 'signature_feature', label: 'Face details', multi: true },
  { category: 'face_accessory', label: 'Eyewear' },
  { category: 'head_accessory', label: 'Headwear' },
  { category: 'jewelry', label: 'Jewelry', multi: true },
  { category: 'top', label: 'Top' },
  { category: 'outerwear', label: 'Outerwear' },
  { category: 'foreground_prop', label: 'Prop' },
  { category: 'scene', label: 'Background' },
];
const FACE_PARTS = { face_eye: 'eyes', face_brow: 'brows', face_mouth: 'mouth' };
const STORE_KEY = 'idl-studio-workbench';

let data;           // /api/state
let assets = [];    // every vector asset with a picture
let byId = new Map();
let base;           // the vector base (teardrop)
let catalogFilter = { category: 'all', text: '' };
let selectedId = null;
let bench = loadBench();

const $ = (sel, root = document) => root.querySelector(sel);
const el = (tag, attrs = {}, ...children) => {
  const node = document.createElement(tag);
  for (const [k, v] of Object.entries(attrs)) {
    if (k === 'class') node.className = v;
    else if (k.startsWith('on')) node.addEventListener(k.slice(2), v);
    else if (v !== undefined && v !== null && v !== false) node.setAttribute(k, v === true ? '' : v);
  }
  for (const child of children.flat()) if (child != null) node.append(child.nodeType ? child : document.createTextNode(child));
  return node;
};

// ---------- data ----------

async function load() {
  const response = await fetch('/api/state');
  if (!response.ok) throw new Error(await response.text());
  data = await response.json();
  assets = [];
  for (const pack of data.packs) {
    for (const asset of pack.assets) {
      if (!asset.picture) continue;
      assets.push({ ...asset, packId: pack.packId, packVersion: pack.version });
    }
  }
  byId = new Map(assets.map((a) => [a.id, a]));
  base = assets.find((a) => a.category === 'base');
  $('#summary').textContent = `${assets.length} vector assets · ${data.packs.map((p) => `${p.packId}/v${p.version}`).join(', ')}`;
}

const vectorPack = () => data.packs.find((p) => p.assets.some((a) => a.picture && a.category === 'base'));

/** Expression id → { eyes, brows, mouth, overlays } asset ids on the current base. */
function expressionParts(expressionId) {
  const def = vectorPack().expressions.find((e) => e.id === expressionId);
  if (!def || !base) return null;
  const parts = def.baseOverrides?.[base.id];
  if (!parts) return null;
  const overlays = (def.overlays || []).filter((id) => byId.has(id));
  return { eyes: parts.eyes, brows: parts.brows, mouth: parts.mouth, overlays };
}

/** The asset list for a recipe. `focus` replaces whatever the recipe has in its category. */
function compose(recipe, focus) {
  const list = [base];
  const face = expressionParts(recipe.expression || 'neutral_face') || {};
  const faceIds = { eyes: face.eyes, brows: face.brows, mouth: face.mouth };
  if (focus && FACE_PARTS[focus.category]) faceIds[FACE_PARTS[focus.category]] = focus.id;
  for (const id of Object.values(faceIds)) if (byId.has(id)) list.push(byId.get(id));
  for (const id of face.overlays || []) list.push(byId.get(id));
  for (const slot of SLOTS) {
    if (focus && focus.category === slot.category) continue;
    const value = recipe[slot.category];
    for (const id of [].concat(value || [])) if (byId.has(id)) list.push(byId.get(id));
  }
  if (focus && !FACE_PARTS[focus.category] && focus.category !== 'base') list.push(focus);
  return [...new Set(list.filter(Boolean))];
}

function defaultRecipe() {
  const defaults = vectorPack().defaults || {};
  const recipe = { expression: 'neutral_face', overrides: {} };
  if (byId.has(defaults.top)) recipe.top = defaults.top;
  return recipe;
}

function loadBench() {
  try {
    const saved = JSON.parse(localStorage.getItem(STORE_KEY) || 'null');
    if (saved && typeof saved === 'object') return saved;
  } catch { /* storage can be unavailable */ }
  return null;
}
function saveBench() {
  try { localStorage.setItem(STORE_KEY, JSON.stringify(bench)); } catch { /* ignore */ }
}

// ---------- shared pieces ----------

function sizeSheet(list, opts) {
  const row = el('div', { class: 'sheet' });
  for (const wall of ['light', 'dark']) {
    const box = el('div', { class: `wall ${wall}` });
    const line = el('div', { class: 'row' });
    // 48 px is drawn at exactly 48 device pixels, then shown at 2× so the pixels are visible.
    const tiny = avatarCanvas(48, list, { ...opts, exactPixels: true });
    tiny.classList.add('pixel');
    tiny.style.width = tiny.style.height = '96px';
    line.append(
      el('div', {}, tiny, el('div', {}, '48 (2×)')),
      el('div', {}, avatarCanvas(96, list, opts), el('div', {}, '96')),
      el('div', {}, avatarCanvas(opts.big || 192, list, opts), el('div', {}, String(opts.big || 192))),
    );
    box.append(line, el('div', {}, `${opts.framing} · ${wall}`));
    row.append(box);
  }
  return row;
}

const tierBadge = (asset) => (String(asset.tier || 'free').toLowerCase() === 'premium'
  ? el('span', { class: 'badge premium' }, 'premium') : el('span', { class: 'badge' }, 'free'));

// ---------- catalog ----------

function renderCatalog() {
  const root = $('#catalog');
  root.replaceChildren();
  const categories = ['all', ...new Set(assets.map((a) => a.category))];
  const filters = el('div', { class: 'filters' },
    categories.map((c) => el('button', {
      class: c === catalogFilter.category ? 'on' : '',
      onclick: () => { catalogFilter.category = c; renderCatalog(); },
    }, c === 'all' ? `all (${assets.length})` : `${c} (${assets.filter((a) => a.category === c).length})`)),
    el('input', {
      type: 'search', placeholder: 'Filter by id or label', value: catalogFilter.text,
      oninput: (e) => { catalogFilter.text = e.target.value; renderGrid(grid); },
    }),
  );
  const grid = el('div', { class: 'grid' });
  const detail = el('aside', { class: 'panel', id: 'detail' }, el('p', { class: 'muted' }, 'Select an item.'));
  root.append(filters, el('div', { class: 'split' }, grid, detail));
  renderGrid(grid);
  if (selectedId && byId.has(selectedId)) renderDetail(byId.get(selectedId));
}

function renderGrid(grid) {
  grid.replaceChildren();
  const text = catalogFilter.text.toLowerCase();
  const recipe = defaultRecipe();
  for (const asset of assets) {
    if (catalogFilter.category !== 'all' && asset.category !== catalogFilter.category) continue;
    if (text && !`${asset.id} ${asset.accessibilityLabel || ''}`.toLowerCase().includes(text)) continue;
    const card = el('div', {
      class: `card${asset.id === selectedId ? ' sel' : ''}`,
      onclick: () => {
        selectedId = asset.id;
        grid.querySelectorAll('.card').forEach((c) => c.classList.toggle('sel', c === card));
        renderDetail(asset);
      },
    },
    avatarCanvas(96, compose(recipe, asset), { framing: asset.category === 'top' || asset.category === 'outerwear' ? 'bust' : 'head' }),
    el('div', { class: 'id' }, asset.id),
    el('div', {}, tierBadge(asset), el('span', { class: 'badge' }, `cv ${asset.contentVersion ?? '?'}`),
      asset.hasSource ? null : el('span', { class: 'badge missing' }, 'no svg')));
    grid.append(card);
  }
}

function renderDetail(asset) {
  const panel = $('#detail');
  const list = compose(defaultRecipe(), asset);
  const parts = asset.picture.parts;
  const source = el('pre', { style: 'max-height:240px;overflow:auto;font-size:11px;display:none' });
  panel.replaceChildren(
    el('h2', {}, asset.accessibilityLabel || asset.id),
    el('div', { class: 'muted' }, el('code', {}, asset.id), ` · ${asset.category} · ${asset.packId}/v${asset.packVersion}`),
    el('div', {}, tierBadge(asset), asset.collection ? el('span', { class: 'badge' }, asset.collection) : null,
      asset.license ? el('span', { class: 'badge' }, asset.license) : null),
    el('h3', {}, 'Head framing'),
    sizeSheet(list, { framing: 'head', big: 128 }),
    el('h3', {}, 'Bust framing'),
    sizeSheet(list, { framing: 'bust', big: 128 }),
    el('h3', {}, 'Color slots'),
    el('table', {}, Object.entries(asset.colorSlots || {}).map(([slot, hex]) =>
      el('tr', {}, el('td', {}, el('span', { class: 'swatch', style: `background:${hex}` }), el('code', {}, slot)), el('td', {}, el('code', {}, hex))))),
    el('h3', {}, `Parts (${parts.length}) · picture schema ${asset.picture.schemaVersion}`),
    el('table', {},
      el('tr', {}, el('th', {}, 'part'), el('th', {}, 'band'), el('th', {}, 'fill'), el('th', {}, 'extras')),
      parts.map((p) => el('tr', {},
        el('td', {}, el('code', {}, p.id)),
        el('td', {}, String(p.zBand)),
        el('td', {}, el('code', {}, p.fill.slot || (p.fill.radial ? 'radial' : 'linear'))),
        el('td', {}, [
          p.stroke && `stroke ${p.stroke.slot} ${p.stroke.width}`,
          p.clip && `clip ${p.clip.id}:${p.clip.mode}`,
          p.tags?.length && `tags ${p.tags.join(' ')}`,
          p.publishMask && `publishes ${p.publishMask}`,
          p.clipBy?.length && `clipBy ${p.clipBy.map((c) => `${c.mask}:${c.mode}`).join(' ')}`,
          p.opacity != null && p.opacity !== 1 && `opacity ${p.opacity}`,
          p.fillRule === 'evenodd' && 'evenodd',
        ].filter(Boolean).join(' · '))))),
    el('h3', {}, 'More'),
    el('div', { class: 'row' },
      el('button', { onclick: () => openInWorkbench(asset) }, 'Try in Workbench'),
      asset.hasSource ? el('button', {
        onclick: async () => {
          if (source.style.display === 'none') {
            source.textContent = await (await fetch(`/api/source?id=${encodeURIComponent(asset.id)}`)).text();
            source.style.display = 'block';
          } else source.style.display = 'none';
        },
      }, 'SVG source') : el('span', { class: 'badge missing' }, `art/${asset.packId}/${asset.id}.svg missing`),
      asset.conflictsWith?.length ? el('span', { class: 'muted' }, `conflicts: ${asset.conflictsWith.join(', ')}`) : null),
    source,
  );
}

// ---------- workbench ----------

function openInWorkbench(asset) {
  const slot = SLOTS.find((s) => s.category === asset.category);
  if (slot) bench[slot.category] = slot.multi ? [...new Set([...(bench[slot.category] || []), asset.id])] : asset.id;
  saveBench();
  show('workbench');
}

function renderWorkbench() {
  const root = $('#workbench');
  bench = { ...defaultRecipe(), ...(bench || {}) };
  bench.overrides ||= {};
  bench.framing ||= 'head';
  bench.frameStyle ||= 'squircle';
  const list = compose(bench);
  const controls = el('div', { class: 'panel controls' });
  const expressions = vectorPack().expressions;
  controls.append(
    el('label', {}, 'Expression'),
    el('select', { onchange: (e) => update({ expression: e.target.value }) },
      expressions.map((x) => el('option', { value: x.id, selected: x.id === bench.expression }, x.label || x.id))),
  );
  for (const slot of SLOTS) {
    const options = assets.filter((a) => a.category === slot.category);
    if (!options.length) continue;
    controls.append(el('label', {}, `${slot.label}${slot.multi ? ' (several)' : ''}`));
    if (slot.multi) {
      const chosen = new Set(bench[slot.category] || []);
      controls.append(el('div', { class: 'row' }, options.map((a) => el('label', { style: 'margin:0;color:inherit' },
        el('input', {
          type: 'checkbox', checked: chosen.has(a.id),
          onchange: (e) => { e.target.checked ? chosen.add(a.id) : chosen.delete(a.id); update({ [slot.category]: [...chosen] }); },
        }), ` ${a.accessibilityLabel || a.id}`))));
    } else {
      controls.append(el('select', { onchange: (e) => update({ [slot.category]: e.target.value || null }) },
        el('option', { value: '' }, '(none)'),
        options.map((a) => el('option', { value: a.id, selected: a.id === bench[slot.category] }, `${a.accessibilityLabel || a.id}${String(a.tier).toLowerCase() === 'premium' ? ' ★' : ''}`))));
    }
  }
  controls.append(
    el('label', {}, 'Frame'),
    el('div', { class: 'row' }, ['squircle', 'circle', 'none'].map((f) => el('button', { class: f === bench.frameStyle ? 'on' : '', onclick: () => update({ frameStyle: f }) }, f))),
    el('label', {}, 'Colors (overrides; shadow and highlight follow their primary unless set)'),
  );
  const slotsTable = el('div', { class: 'slots' });
  const declared = new Map();
  for (const a of list) for (const [slot, hex] of Object.entries(a.colorSlots || {})) if (!declared.has(slot)) declared.set(slot, hex);
  for (const [slot, hex] of [...declared].sort()) {
    slotsTable.append(
      el('code', {}, slot),
      el('input', { type: 'color', value: (bench.overrides[slot] || hex).slice(0, 7), oninput: (e) => update({ overrides: { ...bench.overrides, [slot]: e.target.value.toUpperCase() } }, false) }),
      bench.overrides[slot] ? el('button', { onclick: () => { const o = { ...bench.overrides }; delete o[slot]; update({ overrides: o }); } }, '×') : el('span'),
    );
  }
  controls.append(slotsTable, el('div', { class: 'row', style: 'margin-top:10px' },
    el('button', { onclick: () => { bench = defaultRecipe(); saveBench(); renderWorkbench(); } }, 'Reset'),
    el('button', { onclick: () => navigator.clipboard?.writeText(JSON.stringify(recipeJson(), null, 2)) }, 'Copy recipe JSON')));

  const slotLinks = vectorPack().defaults?.slotLinks || {};
  const preview = el('div', {},
    el('h3', {}, 'Head framing (widgets)'), sizeSheet(list, { framing: 'head', frameStyle: bench.frameStyle, overrides: bench.overrides, slotLinks }),
    el('h3', {}, 'Bust framing (profile, editor, export)'), sizeSheet(list, { framing: 'bust', frameStyle: bench.frameStyle, overrides: bench.overrides, slotLinks }),
    el('h3', {}, '512'),
    el('div', { class: 'row' },
      el('div', { class: 'wall light' }, avatarCanvas(512, list, { framing: bench.framing, frameStyle: bench.frameStyle, overrides: bench.overrides, slotLinks })),
      el('div', {}, ['head', 'bust'].map((f) => el('button', { class: f === bench.framing ? 'on' : '', onclick: () => update({ framing: f }) }, f)))),
    el('h3', {}, 'Layers in draw order'),
    el('div', { class: 'muted' }, list.map((a) => a.id).join(' · ')),
  );
  root.replaceChildren(el('div', { class: 'bench' }, controls, preview));
}

function recipeJson() {
  const { overrides, expression } = bench;
  const items = {};
  for (const slot of SLOTS) if (bench[slot.category]?.length) items[slot.category] = bench[slot.category];
  return { base: base.id, expression, items, colorOverrides: overrides };
}

let pending = 0;
function update(patch, immediate = true) {
  bench = { ...bench, ...patch };
  saveBench();
  // Color pickers fire continuously; coalesce to one render per frame.
  if (immediate) return renderWorkbench();
  cancelAnimationFrame(pending);
  pending = requestAnimationFrame(renderWorkbench);
}

// ---------- drafts ----------

let draftsState = { list: [], selected: null, detail: null, showGuides: false, overlaySvg: null };
let draftsPoll = 0;

async function fetchDrafts() {
  const response = await fetch('/api/drafts');
  if (!response.ok) throw new Error(await response.text());
  return response.json();
}

async function fetchDraft(id) {
  const response = await fetch(`/api/drafts/${encodeURIComponent(id)}`);
  if (!response.ok) throw new Error(await response.text());
  return response.json();
}

async function fetchGuidesOverlay() {
  if (draftsState.overlaySvg) return draftsState.overlaySvg;
  const response = await fetch('/api/guides');
  if (!response.ok) throw new Error(await response.text());
  const data = await response.json();
  draftsState.overlaySvg = data.overlaySvg;
  return data.overlaySvg;
}

function draftAsAsset(detail) {
  const meta = detail.meta;
  return {
    id: meta.id,
    category: meta.category,
    accessibilityLabel: meta.label || meta.id,
    colorSlots: meta.colorSlots || {},
    tier: meta.tier || 'free',
    contentVersion: meta.contentVersion || 1,
    picture: detail.picture,
    draft: true,
  };
}

function draftPreviewList(detail) {
  if (!detail?.picture || !base) return [base].filter(Boolean);
  const asset = draftAsAsset(detail);
  return compose(defaultRecipe(), asset);
}

async function selectDraft(id) {
  draftsState.selected = id;
  draftsState.detail = await fetchDraft(id);
  renderDrafts();
}

async function revertDraft(id, n) {
  const response = await fetch(`/api/drafts/${encodeURIComponent(id)}/revert`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ n }),
  });
  if (!response.ok) throw new Error(await response.text());
  await selectDraft(id);
}

function renderDrafts() {
  const root = $('#drafts');
  const byGroup = new Map();
  for (const meta of draftsState.list) {
    const g = meta.group || meta.id;
    if (!byGroup.has(g)) byGroup.set(g, []);
    byGroup.get(g).push(meta);
  }
  const list = el('div', { class: 'panel', style: 'position:static' });
  list.append(el('h2', {}, 'Drafts'), el('p', { class: 'muted' },
    'Work in .studio/drafts/. Polls every 1.5s so CLI edits appear here.'));
  if (!draftsState.list.length) list.append(el('p', { class: 'muted' }, 'No drafts yet. Use studio.py draft new …'));
  for (const [group, rows] of [...byGroup].sort((a, b) => a[0].localeCompare(b[0]))) {
    list.append(el('h3', {}, group));
    for (const meta of rows) {
      list.append(el('div', {
        class: `card${meta.id === draftsState.selected ? ' sel' : ''}`,
        style: 'text-align:left;margin-bottom:6px',
        onclick: () => selectDraft(meta.id),
      },
      el('div', { class: 'id' }, meta.id),
      el('div', { class: 'muted' }, `${meta.category} · rev ${meta.current || 0}`),
      meta.prompt ? el('div', { class: 'muted' }, meta.prompt) : null));
    }
  }

  const detail = el('aside', { class: 'panel' });
  const d = draftsState.detail;
  if (!d) {
    detail.append(el('p', { class: 'muted' }, 'Select a draft.'));
  } else {
    const meta = d.meta;
    detail.append(
      el('h2', {}, meta.label || meta.id),
      el('div', { class: 'muted' }, el('code', {}, meta.id), ` · ${meta.category} · rev ${meta.current || 0}`),
      meta.prompt ? el('p', {}, meta.prompt) : null,
      el('div', { class: 'row' },
        el('button', {
          class: draftsState.showGuides ? 'on' : '',
          onclick: async () => {
            draftsState.showGuides = !draftsState.showGuides;
            if (draftsState.showGuides) await fetchGuidesOverlay();
            renderDrafts();
          },
        }, 'Guides overlay')),
    );
    if (d.error) detail.append(el('div', { class: 'error' }, d.error));
    else if (d.picture) {
      const listAssets = draftPreviewList(d);
      const previewWrap = el('div', { class: 'draft-preview' });
      const canvas = avatarCanvas(192, listAssets, { framing: 'head' });
      previewWrap.append(canvas);
      if (draftsState.showGuides && draftsState.overlaySvg) {
        const overlay = el('div', { class: 'guides-overlay' });
        overlay.innerHTML = draftsState.overlaySvg;
        const svg = overlay.querySelector('svg');
        if (svg) {
          svg.setAttribute('width', '192');
          svg.setAttribute('height', '192');
          // Character grid 0..1024 maps into head framing (-40,0,1104).
          svg.setAttribute('viewBox', '-40 0 1104 1104');
        }
        previewWrap.append(overlay);
      }
      detail.append(el('h3', {}, 'Current revision'), previewWrap);
    } else {
      detail.append(el('p', { class: 'muted' }, 'No revisions yet.'));
    }
    detail.append(el('h3', {}, 'Lint'));
    if (!d.lint?.length) detail.append(el('p', { class: 'muted' }, d.picture ? 'clean' : '—'));
    else {
      detail.append(el('table', {}, d.lint.map((i) => el('tr', {},
        el('td', {}, el('span', { class: `badge ${i.level === 'error' ? 'missing' : ''}` }, i.level)),
        el('td', {}, el('code', {}, i.rule)),
        el('td', {}, i.part ? `[${i.part}] ` : '', i.message)))));
    }
    detail.append(el('h3', {}, 'Revisions'));
    if (!meta.revisions?.length) detail.append(el('p', { class: 'muted' }, 'none'));
    else {
      detail.append(el('table', {},
        el('tr', {}, el('th', {}, 'rev'), el('th', {}, 'note'), el('th', {}, '')),
        meta.revisions.slice().reverse().map((r) => el('tr', {},
          el('td', {}, String(r.n) + (r.n === meta.current ? ' ●' : '')),
          el('td', {}, r.note || r.time || ''),
          el('td', {}, r.n === meta.current ? null : el('button', {
            onclick: () => revertDraft(meta.id, r.n),
          }, 'Revert'))))));
    }
  }
  root.replaceChildren(el('div', { class: 'split' }, list, detail));
}

async function refreshDrafts(silent = false) {
  try {
    const list = await fetchDrafts();
    const prev = JSON.stringify(draftsState.list);
    draftsState.list = list;
    if (draftsState.selected) {
      const detail = await fetchDraft(draftsState.selected);
      const same = JSON.stringify(draftsState.detail) === JSON.stringify(detail);
      draftsState.detail = detail;
      if (!silent || prev !== JSON.stringify(list) || !same) renderDrafts();
    } else if (!silent || prev !== JSON.stringify(list)) {
      renderDrafts();
    }
  } catch (error) {
    if (!silent) {
      $('#drafts').replaceChildren(el('div', { class: 'error' }, String(error.message || error)));
    }
  }
}

function startDraftsPoll() {
  clearInterval(draftsPoll);
  draftsPoll = setInterval(() => {
    if (current === 'drafts') refreshDrafts(true);
  }, 1500);
}

// ---------- expressions ----------

function renderExpressions() {
  const root = $('#expressions');
  const catalog = data.catalog;
  const pack = vectorPack();
  const drawn = new Map(pack.expressions.map((e) => [e.id, e]));
  // Shape id → asset id, learned from the expressions the pack already draws.
  const shapeAsset = { eyes: {}, brows: {}, mouths: {} };
  for (const ex of catalog.expressions) {
    const parts = drawn.get(ex.expressionId)?.baseOverrides?.[base.id];
    if (!parts) continue;
    if (ex.parts.eyes) shapeAsset.eyes[ex.parts.eyes] = parts.eyes;
    if (ex.parts.brows) shapeAsset.brows[ex.parts.brows] = parts.brows;
    if (ex.parts.mouth) shapeAsset.mouths[ex.parts.mouth] = parts.mouth;
  }
  const kinds = [['eyes', 'eyes'], ['brows', 'brows'], ['mouths', 'mouth']];
  const shapeDone = (kind, id) => id === 'none' || Boolean(shapeAsset[kind][id]);
  let composable = 0;
  const cards = catalog.expressions.map((ex) => {
    const isDrawn = drawn.has(ex.expressionId);
    const missing = kinds.filter(([kind, key]) => ex.parts[key] && !shapeDone(kind, ex.parts[key])).map(([, key]) => `${key}: ${ex.parts[key]}`);
    let face;
    if (isDrawn) {
      face = avatarCanvas(96, compose({ ...defaultRecipe(), expression: ex.expressionId }), { framing: 'head' });
    } else if (!missing.length) {
      composable += 1;
      // Every shape exists: preview the combination, without overlays.
      const ids = [shapeAsset.eyes[ex.parts.eyes], shapeAsset.brows[ex.parts.brows], shapeAsset.mouths[ex.parts.mouth]].filter(Boolean);
      face = avatarCanvas(96, [base, ...ids.map((id) => byId.get(id))], { framing: 'head' });
    } else {
      face = el('div', { class: 'muted' }, '—');
    }
    return el('div', { class: `excard${isDrawn ? '' : ' todo'}` },
      el('div', { class: 'face' }, face),
      el('div', {}, el('strong', {}, ex.cldrName)),
      el('div', { class: 'muted' }, el('code', {}, ex.expressionId)),
      el('div', {}, ex.mood ? el('span', { class: 'badge' }, `mood ${ex.mood}`) : null, el('span', { class: 'badge' }, `p${ex.priority}`),
        isDrawn ? el('span', { class: 'badge' }, 'in pack') : !missing.length ? el('span', { class: 'badge' }, 'shapes exist') : null),
      el('div', { class: 'muted' }, Object.entries(ex.parts).map(([k, v]) => `${k} ${v}`).join(' · ')),
      ex.overlays.length ? el('div', { class: 'muted' }, `overlays: ${ex.overlays.join(', ')}`) : null,
      missing.length ? el('div', { class: 'need' }, `needs ${missing.join(', ')}`) : null);
  });
  const shapeChips = kinds.map(([kind]) => el('div', {},
    el('h3', {}, kind),
    el('div', { class: 'shapes' }, catalog.shapes[kind].map((s) => el('span', {
      class: `shape${shapeDone(kind, s.id) ? ' done' : ''}`,
      title: shapeAsset[kind][s.id] || (s.id === 'none' ? 'no part' : 'not drawn yet'),
    }, `${s.id} ×${s.count}`)))));
  const overlayChips = el('div', {}, el('h3', {}, 'overlays'), el('div', { class: 'shapes' },
    catalog.shapes.overlays.map((s) => el('span', { class: 'shape' }, `${s.id} ×${s.count}`))));
  root.replaceChildren(
    el('p', {}, `${drawn.size} of ${catalog.expressions.length} catalog expressions are in the pack; ${composable} more can be composed from existing shapes. `,
      el('span', { class: 'muted' }, 'Green shapes have art on the teardrop. Overlay coverage is not mapped yet.')),
    ...shapeChips, overlayChips,
    el('h3', {}, 'Catalog'),
    el('div', { class: 'exgrid' }, cards),
  );
}

// ---------- shell ----------

const RENDER = {
  catalog: renderCatalog,
  workbench: renderWorkbench,
  drafts: () => { refreshDrafts(false); },
  expressions: renderExpressions,
};
let current = 'catalog';
function show(view) {
  current = view;
  document.querySelectorAll('#tabs button').forEach((b) => b.classList.toggle('on', b.dataset.view === view));
  document.querySelectorAll('.view').forEach((v) => v.classList.toggle('on', v.id === view));
  try { localStorage.setItem('idl-studio-view', view); } catch { /* ignore */ }
  RENDER[view]();
}

async function start() {
  try {
    await load();
    if (!base) throw new Error('No vector base found in any pack.');
    startDraftsPoll();
    let view = 'catalog';
    try { view = localStorage.getItem('idl-studio-view') || view; } catch { /* ignore */ }
    show(RENDER[view] ? view : 'catalog');
  } catch (error) {
    $('main').replaceChildren(el('div', { class: 'error' }, String(error.stack || error)));
  }
}

document.querySelectorAll('#tabs button').forEach((b) => b.addEventListener('click', () => show(b.dataset.view)));
$('#reload').addEventListener('click', async () => { await load(); show(current); });
start();
