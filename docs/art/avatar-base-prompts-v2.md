# Avatar base image prompts, v2

Round 2 of the AI image prompts used to explore avatar base designs. Round 1 output is in `assets/`
(`iDL <base> 1.jpg` and `iDL <base> 2.jpg`). This version rewrites the prompts so each base can show
the full range of standard emoji faces and can be customized, as described in
`IDL_AVATAR_CREATOR_MASTER_PLAN.md` §4.3, §5.3 and §6.

## 1. Review of round 1

| Image | Verdict | Expression range | Customization | Notes |
|---|---|---|---|---|
| Blob 1 (mint gumdrop) | **Keep** | Good: big, open face area | Good: flat fill, easy to recolor | Best overall. The gloss highlight could become a separate layer. |
| Blob 2 (melty drip) | **Redo** | Good | OK | Ignored the coral/gouache brief and came out as a copy of Blob 1 with drips. The drip-feet silhouette is worth keeping. |
| Bot 1 (screen-face bot) | **Keep** | Excellent: the screen can show any glyph face | Good | Body is small next to the head, but the screen face is the main thing that matters. |
| Bot 2 (retro tin bot) | **Redo** | Poor: one camera eye, nowhere for brows or a mouth | Poor: rivets and halftone texture | Tall shape leaves the face tiny in a square crop. |
| Critter 1 (fox) | **Keep, simplify** | Good | OK | The fur tufts are noise at 48px. The muzzle ties the mouth to one spot, which is fine. |
| Critter 2 (felt bunny) | **Redo** | Poor: fixed button eyes can't show emotion | Poor: felt texture, baked stitching | Ears double the height, so the face is too small in a square crop. |
| Ghost 1 (sheet ghost) | **Keep, remove the baked face** | Good | Good | The sleepy eyes and eye bags are part of the base. They need to be a swappable expression. |
| Ghost 2 (flame-tail spirit) | **Redo** | Poor: hollow eyes, no mouth | Poor: gradient fill, baked sparkles | The comma-tail silhouette is great. Keep it. |
| Orb 1 (glow orb) | **Keep, enlarge the face** | OK: face is small | Poor: radial gradient is hard to recolor | Needs a flat inner face disc. |
| Orb 2 (ringed planet) | **Redo** | Poor: the color split runs through the face | Poor | Wide and diagonal, so it fails a square crop. The moon would collide with accessory zones. |
| Pixel 1 (16×16 cyan) | **Keep, move to 24×24** | Limited: 16×16 leaves about 2px per eye | Good | 24×24 gives enough room for brows and mouth shapes. |
| Pixel 2 (Game Boy bean) | **Redo** | Poor: detailed eyes baked in, 3/4 view | OK | Pixel sizes are inconsistent and the dithering is noisy. The leaf sprout is a good signature feature. |

### Problems shared by every image

1. **The face is drawn into the base.** The renderer layers eyes, brows, mouth and overlays
   separately (master plan §4.3). A base therefore needs to be **faceless**, with a clear, empty
   face zone.
2. **Landscape 16:9 output.** Widgets are square. All prompts now ask for 1:1 and a fill of about 70%.
3. **Gradients and textures** stop the palette module from recoloring the body. Use one flat body
   color plus one shade tone.
4. **No space for headwear.** The top of the head needs a clear, simple curve where hats,
   headphones and similar items can sit.
5. **Gemini carried style over from earlier prompts in the same chat** (Blob 2 copied Blob 1).
   **Start a new chat for every prompt.**

## 2. Workflow

Run each step in a **new Gemini chat**:

1. **Base:** run the base prompt from §4. You get a faceless character.
2. **Expression test:** upload that image, plus any round-1 image marked "Keep" in §1, and run the
   expression-sheet prompt from §3. If the base can't show all 16 faces clearly, reject it.
3. **Size check:** shrink the sheet so each cell is about 48px. You should still be able to tell
   happy, sad, angry and sleepy apart.

## 3. Universal expression-sheet prompt (use with an uploaded image)

> Using the uploaded character exactly as drawn (same silhouette, colors, outline weight and style;
> do not redesign it), create a character expression sheet: a 4×4 grid of 16 identical copies of
> the character, front-facing, each in its own square cell with generous spacing, on a plain pure
> white background. Only the face changes between cells. Faces are built from simple separate parts:
> eyes, optional eyebrows, mouth and small effect overlays, all drawn in the same thick dark outline
> color, large and centered in the face area so they read at 48 pixels. Row 1: neutral, gentle
> smile, big open grin, laughing with closed happy-arc eyes. Row 2: heart eyes with blush, winking
> with tongue out, surprised with wide eyes and small "o" mouth, sad with a single tear. Row 3:
> crying with streams of tears, angry with sharp slanted brows and a small steam puff, worried with
> raised brows and a sweat drop, sleepy with half-closed eyes and a "Z". Row 4: focused and
> determined, smug and mischievous with a side smirk, embarrassed with big blush, dizzy with spiral
> eyes. No text labels, no shadows, no background elements, flat colors.

## 4. Base prompts

Every base prompt ends with this **style contract**. Paste it after each prompt:

> **Style contract:** Square 1:1 image, character centered and filling about 70% of the frame,
> plain pure white #FFFFFF background, no shadow, no text, no props, no background elements.
> Front-facing, symmetrical pose. **No face drawn: leave a large, clean, empty face area** (no
> eyes, mouth, cheeks or brows) taking up at least 40% of the head's width, so face parts can be
> layered on later. One flat body color plus one darker shade tone and at most one small highlight;
> no gradients, no textures, no patterns, so the body can be recolored. Thick, even, dark outline.
> Simple, smooth top-of-head curve with clear space above it for hats or headphones. Silhouette must
> be recognizable at 48×48 pixels.

### Blob

**Blob A: Gummy gumdrop (refines round-1 Blob 1)**
> A cute blob character mascot shaped like a soft gumdrop, slightly wider at the bottom, two tiny
> rounded nub arms at the sides, glossy candy look shown by a single white highlight on the upper
> left, flat pastel mint body color. Clean vector mascot style.

**Blob B: Melty drip blob (new direction)**
> A blob character shaped like a scoop of melting ice cream: a tall round dome that flows down into
> three thick rounded drips forming stubby feet, matte flat coral orange body, chunky sticker style
> with a thick slightly wobbly ink outline and a cream-colored inner shade band near the bottom.
> Playful and squishy, not glossy.

### Bot

**Bot A: Screen-face companion (refines round-1 Bot 1)**
> A cute small companion robot with a large rounded-square TV-monitor head and a tiny capsule body
> with short stubby arms and legs, one short antenna with a ball tip on top, white plastic casing
> with a flat sky-blue screen. The screen fills most of the front of the head and is completely
> blank and evenly colored (this is the face area). Clean vector toy style.

**Bot B: Retro dome bot with face plate (replaces round-1 Bot 2)**
> A chunky retro 1950s toy robot with a wide round glass-dome head sitting directly on a short
> barrel body, with no visible neck so the head is the dominant shape. Inside the dome is a large,
> flat, pale cream face plate (the face area). Two small round ear bolts at the sides, short stubby
> claw arms, mustard yellow body with charcoal joints and a single red chest light. No rivets, no
> halftone, no small details.

### Ghost

**Ghost A: Sheet ghost (refines round-1 Ghost 1)**
> A cozy bedsheet ghost with a tall rounded dome top that flows down into three gentle scalloped
> waves at the bottom, floating, warm off-white body with a pale lavender shade tone on one side so
> it stands out on white. Soft, huggable, minimal vector style.

**Ghost B: Comma-tail wisp (replaces round-1 Ghost 2)**
> A whimsical spirit with a large round head that tapers into a single curling wispy tail sweeping
> down and to the left, like a comma or a candle flame, flat teal body with a darker indigo shade
> tone on the tail only, no sparkles. The round head carries the face area, centered on the head and
> not on the tail.

### Critter

**Critter A: Chibi fox (refines round-1 Critter 1)**
> A chibi fox critter sitting front-facing, a very large round head on a small body, big triangular
> ears with flat cream inner color, a fluffy tail curled around the front of the body, flat orange
> body with a cream chest and a cream muzzle patch on the lower half of the face, a small dark nose
> at the top of the muzzle. Fur shown only by the outline shape: at most two simple tufts on the
> cheeks, no fur strokes.

**Critter B: Round bear-bunny (replaces round-1 Critter 2)**
> A chubby round critter that is part bear and part bunny, a big round head merged with a pear-shaped
> sitting body, two short upright rounded ears (no taller than half the head height), flat dusty
> sage green body with a round cream belly patch and cream inner ears, tiny paws. Plush-toy proportions
> but flat colors, with no felt texture, no stitching and no button eyes.

### Orb

**Orb A: Glow orb (refines round-1 Orb 1)**
> A minimal orb character: a perfect sphere with a crisp darker violet rim, a flat lavender outer
> ring, and a large flat pale inner disc covering the central 60% of the sphere (the face area),
> suggesting a soft inner glow without using a gradient. Elegant and calm, geometric vector style.

**Orb B: Ringed planet (replaces round-1 Orb 2)**
> A small planet orb character: a perfect round sphere in one flat deep-blue color with a single thin
> sunny-yellow ring passing horizontally behind and below the sphere, so the ring never crosses
> the upper two thirds of the sphere where the face area is. Horizontal ring, not tilted, staying
> within the square frame. No moon, no stars, no color split on the sphere.

### Pixel sprite

**Pixel A: 24×24 round sprite (refines round-1 Pixel 1)**
> A 24×24 pixel art character sprite: a round creature with a big head-body and two short stubby
> feet, a strict 4-color palette (deep navy outline, bright cyan body, darker teal shade, white
> highlight), a crisp 1-pixel outline, every pixel exactly the same size on a single consistent grid,
> no anti-aliasing, no dithering, upscaled with nearest-neighbor. The face area is a clean flat cyan
> block of at least 12×8 pixels in the center.

**Pixel B: 24×24 sprout bean (replaces round-1 Pixel 2)**
> A 24×24 pixel art character sprite: a squat bean-shaped creature standing front-facing with a
> single two-leaf sprout on top of its head and two tiny feet, a strict 4-tone Game Boy green palette
> (darkest green outline, mid green body, darker green shade, pale green highlight), a crisp
> 1-pixel outline, every pixel exactly the same size on a single consistent grid, no anti-aliasing,
> no dithering, no belly spot, upscaled with nearest-neighbor. The face area is a clean flat
> mid-green block of at least 12×8 pixels in the center.

## 5. Optional: face-parts kit for one base family

Once a base is chosen, this prompt gives raw material for the separate eye, brow and mouth layers.
Pixel bases need their own pixel-art version of this kit.

> A flat vector sprite sheet of cartoon face parts for a mascot, all drawn in the same thick dark
> charcoal outline style with rounded line ends, on a plain pure white background, arranged in
> clearly separated rows with generous spacing, no character, no text. Row 1, eyes: dot, oval with
> highlight, happy arc, half-lidded, closed line, wide, heart, spiral, star, X, crying eye with tear,
> wink. Row 2, eyebrows: relaxed, raised, worried, angry, determined, asymmetric. Row 3, mouths:
> neutral line, small smile, big open grin, laugh, tiny "o", frown, pout, side smirk, tongue out,
> small fang. Row 4, overlays: blush ovals, sweat drop, single tear, sparkle, "Z", steam puff,
> small bandage.
