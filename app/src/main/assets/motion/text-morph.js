(() => {
  // The publication DOM/text/line layout stays intact. Only a short-lived, aria-hidden
  // overlay moves glyph runs; ranges hide exactly the ink covered by that overlay.
  const begin = performance.now();
  const root = document.documentElement;
  window.__wexStopTextMorph?.();
  const W = innerWidth, H = innerHeight, MAX = 900;
  const visible = r => r.width > 0 && r.height > 0 && r.right > 0 && r.left < W && r.bottom > 0 && r.top < H;
  const segmenter = typeof Intl.Segmenter === 'function' ? new Intl.Segmenter(undefined, {granularity: 'grapheme'}) : null;
  const walker = document.createTreeWalker(document.body, NodeFilter.SHOW_TEXT);
  const glyphs = [], ranges = [];
  let node, visited = 0, probes = 0;
  while ((node = walker.nextNode()) && visited++ < 2500 && glyphs.length < MAX && probes < 1800) {
    const parent = node.parentElement;
    if (!parent || node.data.length > 16384 || !node.data.trim() || parent.closest('script,style,noscript,svg,canvas,[data-wex-morph]')) continue;
    const range = document.createRange(); range.selectNodeContents(node);
    if (![...range.getClientRects()].some(visible)) continue;
    const style = getComputedStyle(parent);
    if (style.visibility === 'hidden' || style.display === 'none') continue;
    function* codepoints(text) { let index = 0; for (const segment of text) { yield {segment, index}; index += segment.length; } }
    const parts = segmenter ? segmenter.segment(node.data) : codepoints(node.data);
    // Preserve joining in cursive scripts instead of detaching Arabic/Indic letters.
    const cursive = /[\u0590-\u0dff]/u.test(node.data);
    const units = cursive ? [...node.data.matchAll(/\S+\s*/gu)].map(m => ({segment: m[0], index: m.index})) : parts;
    for (const unit of units) {
      if (glyphs.length >= MAX || probes++ >= 1800) break;
      const r = document.createRange(); r.setStart(node, unit.index); r.setEnd(node, unit.index + unit.segment.length);
      const box = r.getBoundingClientRect();
      if (!visible(box)) continue;
      glyphs.push({text: unit.segment, x: box.left, y: box.top, w: box.width, h: box.height,
        font: `${style.fontStyle} ${style.fontWeight} ${style.fontSize} ${style.fontFamily}`, color: style.color, spacing: style.letterSpacing,
        features: style.fontFeatureSettings, direction: style.direction});
      ranges.push(r);
    }
  }
  // Line-local identity matching avoids long-distance flights across unrelated paragraphs.
  let line = -1, lastY = -1000;
  glyphs.forEach(g => { if (Math.abs(g.y - lastY) > Math.max(3, g.h * .2)) { line++; lastY = g.y; } g.line = line; });
  const collected = performance.now();
  const old = window.__wexTextFrame;
  const ratio = devicePixelRatio || 1;
  window.__wexMorphGlyphCount = glyphs.length;
  if (!glyphs.length || window.getSelection()?.toString() || matchMedia('(prefers-reduced-motion: reduce)').matches || W * H * ratio * ratio > 3000000) return;
  // Rasterize a bounded viewport once. No per-letter DOM nodes/compositing layers.
  const atlas = document.createElement('canvas'); atlas.width = Math.ceil(W * ratio); atlas.height = Math.ceil(H * ratio);
  const ink = atlas.getContext('2d'); if (!ink) return; ink.scale(ratio, ratio);
  const metrics = new Map();
  const paintRuns = [];
  for (const g of glyphs) {
    const previous = paintRuns[paintRuns.length - 1];
    if (previous && previous.font === g.font && previous.color === g.color && previous.spacing === g.spacing && Math.abs(previous.y - g.y) < .5 && Math.abs(previous.right - g.x) < .75) {
      previous.text += g.text; previous.right = g.x + g.w;
    } else paintRuns.push({...g, right:g.x + g.w});
  }
  for (const run of paintRuns) {
    ink.font = run.font; ink.fillStyle = run.color; ink.direction = run.direction;
    if ('letterSpacing' in ink) ink.letterSpacing = run.spacing;
    let m = metrics.get(run.font);
    if (!m) { m = ink.measureText('Mg'); metrics.set(run.font, m); }
    const ascent = m.fontBoundingBoxAscent ?? m.actualBoundingBoxAscent;
    const descent = m.fontBoundingBoxDescent ?? m.actualBoundingBoxDescent;
    ink.textBaseline = 'alphabetic';
    ink.fillText(run.text, run.direction === 'rtl' ? run.right : run.x, run.y + (run.h - ascent - descent) / 2 + ascent);
  }
  window.__wexMorphTimings = {collect:collected-begin,raster:performance.now()-collected};
  const current = {width:W, height:H, ratio, glyphs, atlas};
  window.__wexTextFrame = current;
  if (!__WEX_ANIMATE__) return;
  window.__wexMorphCount = (window.__wexMorphCount || 0) + 1;
  if (!CSS.highlights || typeof Highlight !== 'function') return;
  const previousFrame = old?.width === W && old?.height === H && old?.ratio === ratio ? old : null;
  const a = previousFrame?.glyphs || [];
  const moves = [];
  const lines = new Set([...a, ...glyphs].map(g => g.line));
  for (const row of lines) {
    const before = a.filter(g => g.line === row), after = glyphs.filter(g => g.line === row);
    const width = after.length + 1, dp = new Uint16Array((before.length + 1) * width);
    for (let i = before.length - 1; i >= 0; i--) for (let j = after.length - 1; j >= 0; j--)
      dp[i * width + j] = before[i].text === after[j].text ? 1 + dp[(i + 1) * width + j + 1] : Math.max(dp[(i + 1) * width + j], dp[i * width + j + 1]);
    let i = 0, j = 0;
    while (i < before.length || j < after.length) {
      if (i < before.length && j < after.length && before[i].text === after[j].text) moves.push({from:before[i++],to:after[j++]});
      else if (j >= after.length || i < before.length && dp[(i + 1) * width + j] >= dp[i * width + j + 1]) moves.push({from:before[i++],to:null});
      else moves.push({from:null,to:after[j++]});
    }
  }
  const overlay = document.createElement('canvas');
  overlay.width = atlas.width; overlay.height = atlas.height;
  overlay.dataset.wexMorph = ''; overlay.setAttribute('aria-hidden','true');
  overlay.style.cssText = 'position:fixed;inset:0;width:100%;height:100%;pointer-events:none;user-select:none;overflow:hidden;z-index:2147483000;contain:strict;';
  const css = document.createElement('style');
  css.textContent = '::highlight(wex-hidden-ink){color:transparent;text-decoration-color:transparent;}';
  root.append(css, overlay);
  CSS.highlights.set('wex-hidden-ink', new Highlight(...ranges));
  const context = overlay.getContext('2d');
  if (!context) { CSS.highlights.delete('wex-hidden-ink'); overlay.remove(); css.remove(); return; }
  context.scale(ratio, ratio);
  // Merge contiguous identical trajectories into texture runs.
  const runs = [];
  for (const move of moves) {
    const g = move.to || move.from, kind = move.from && move.to ? 0 : move.to ? 1 : 2;
    const dx = kind === 0 ? move.from.x - g.x : 0;
    const dy = kind === 0 ? move.from.y - g.y : g.h * .3 * (kind === 1 ? -1 : 1);
    const previous = runs[runs.length - 1];
    if (previous && previous.kind === kind && previous.font === g.font && Math.abs(previous.y - g.y) < .5 && Math.abs(previous.right - g.x) < .75 && Math.abs(previous.dx - dx) < .5 && Math.abs(previous.dy - dy) < .5) {
      previous.right = g.x + g.w;
    } else runs.push({...g, right:g.x + g.w, kind, dx, dy});
  }
  window.__wexMorphPlaying = true;
  let frame, timer;
  const stop = () => {
    cancelAnimationFrame(frame); clearTimeout(timer); overlay.remove(); css.remove();
    CSS.highlights.delete('wex-hidden-ink'); window.__wexMorphPlaying = false;
    if (window.__wexStopTextMorph === stop) window.__wexStopTextMorph = null;
  };
  window.__wexStopTextMorph = stop;
  function ease(x) {
    let u = x;
    for (let i = 0; i < 6; i++) {
      const q = 1 - u;
      const value = 3*q*q*u*.23 + 3*q*u*u*.32 + u*u*u;
      const derivative = 3*q*q*.23 + 6*q*u*(.32-.23) + 3*u*u*(1-.32);
      u = Math.max(0, Math.min(1, u - (value-x) / Math.max(.001,derivative)));
    }
    return 1 - Math.pow(1-u,3);
  }
  const start = performance.now();
  const draw = now => {
    const progress = Math.min(1,(now-start)/280), t = ease(progress);
    context.clearRect(0,0,W,H);
    for (const run of runs) {
      const source = run.kind === 2 ? previousFrame?.atlas : atlas;
      const width = run.right - run.x;
      if (!source || width <= 0 || run.h <= 0) continue;
      context.globalAlpha = run.kind === 1 ? t : run.kind === 2 ? 1-t : 1;
      const x = run.x + (run.kind === 0 ? run.dx * (1-t) : 0);
      const y = run.y + (run.kind === 2 ? run.dy * t : run.dy * (1-t));
      context.drawImage(source,run.x*ratio,run.y*ratio,width*ratio,run.h*ratio,x,y,width,run.h);
    }
    if (progress < 1) frame = requestAnimationFrame(draw); else stop();
  };
  frame = requestAnimationFrame(draw);
  timer = setTimeout(stop, 400);
})();
