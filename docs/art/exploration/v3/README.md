# AD-1 · Style exploration (Q17)

Comparison sheets for the avatar’s look. Sandbox SVG sources live under
`tools/studio/explore/<A–G>/` and are copied here beside each sheet. Nothing in this
folder is loaded by the app. Generator: `tools/studio/explore_ad1.py`.

**How to read:** open [`overview.png`](overview.png) first (all directions at 96 and
48), then each [`A/sheet.png`](A/sheet.png) … [`G/sheet.png`](G/sheet.png) for
expressions, hairstyles and accessories. G is marked on the overview as reopening
D-45/D-46.

## A · Current (baseline)

Shipped `emoji_core` art plus the ST-1 beanie and curly drafts. Soft face gradient,
36-unit outline, thin feature strokes. **At 48 px:** the face and tee still read, but
short hair collapses into a flat blot with no clear hairline, part or strands — this
is the user’s main complaint. Long hair helps a little via side volume; curly from
ST-1 is already stronger. Keep A as the control, not the target.

## B · Bold and flat

40–48 unit outlines, flat face fill (no radial gradient), bold hair rim. Improved
hair geometry with volume, a centre part and highlight strands. **At 48 px:** the
strongest silhouette and the clearest hair of A–F; expressions stay graphic. Weakness:
loses the warm “emoji glow” of the brand face; can feel harsh next to soft UI chrome.

## C · Big-eye soft

Eyes scaled ~1.28× and lowered, smaller mouth, brows nudged down, slightly puffed
hair. Keeps the face gradient. **At 48 px:** reads friendlier and more “character”;
surprised and happy land well. Weakness: the lower eye line eats forehead room under
beanies; mouth can disappear on dark wallpaper when it is too small.

## D · Cel-shaded

Two-tone shade on the head and hair plus an upper-left rim light. Same improved hair
as B–F. **At 48 px:** depth and a slight “premium” feel; rim light helps on dark
walls. Weakness: the hard shade split can look like a dirt smear at 48 px if the
shadow band is too wide; more authoring care than flat styles.

## E · Sticker

Thick light sticker border outside a dark outline so the head pops on any wallpaper.
**At 48 px:** best wallpaper contrast of the set (see the dark row on the overview).
Hair and face match the improved set. Weakness: the border spends scarce pixels; at
widget size the face is a bit smaller inside the frame, and the sticker look is a
strong brand choice you may not want on every surface.

## F · Readable volume (proposal)

**Why:** keep the brand teardrop and soft face gradient, but fix hair first — puffed
volume, a centre part and dual highlight strands sized to survive 48 px — with a
slightly larger eye (≈1.12×) and a 40-unit outline. No flat wash (B) and no sticker
tax (E). **At 48 px:** hair finally reads as hair; face stays warm; accessories still
fit. Weakness: less punch than B/E on busy wallpapers; still needs AD-3 polish on
brows and beanie fit.

## G · Rounder head (reopens D-45 / D-46)

Circular head instead of the teardrop, same accessory and hair set. Labelled on every
sheet. **At 48 px:** a familiar “orb emoji” read; hair sits differently on the crown.
Only pick this if you are willing to reopen the head decisions and refit every item.

## Recommendation

**Prefer F.** It fixes the hair complaint without throwing away the brand head or the
soft face, and it stays compatible with the existing kits. If widget contrast on
arbitrary wallpapers is the higher priority, take **E** (or F’s hair rules with E’s
border). Use **B** only if you want a harder graphic system. Keep **G** off the table
unless you deliberately reopen D-45/D-46. Answer Q17 under AD-2 (for example
“F, with E’s border on widgets”).
