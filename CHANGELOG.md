# Changelog

## July 19, 2026

**Movement & feel**
- Three following styles — **Strong / Medium / Loose**. Strong is the classic at-your-heel behavior; Medium and Loose react later, let their trailing distance drift naturally over time, and take longer to settle. Every style uses the same pathing, wall handling, and long-range catch-up underneath — only the temperament changes.
- Route straightening: the pet now cuts inside corners and across quick zigzags like a real animal instead of tracing your exact footsteps through every elbow. Fully collision-checked — it still paths honestly around walls, fences, and doorways.
- Slingshot fixes: the pet never sprints at point-blank range, and when you double back through it, it stops, lets you pass, and falls in behind — no more darting to your old tile and whipping around.
- Loading lines: the pet now glides straight through region crossings mid-stride — no more despawn/respawn pop at every loading line. Real teleports still re-park it cleanly behind you, and it sets off at speed instead of a standing start.
- Settling polish: no more delayed "nudge" toward you a moment after it stops.
- New toggle: **Pet always faces you** — body locked on you at all times (great on hovering pets).

**Pets & looks**
- Per-pet saved settings: animation speed, ground clearance, and thrall projectile color are all remembered per pet — the sliders always show and edit the pet on screen (transmogs included).
- New: **Pet ground clearance** — lifts the displayed pet slightly off the terrain so base glows and auras (Youngllef etc.) stop sinking into the ground.
- 117HD compatibility (experimental): your pet's glow no longer floats at the hidden real pet's position. Reads 117HD internals to relocate the light; fail-safe — if a 117HD update changes things it simply turns itself off.

**Thrall mode**
- New: **Tint thrall projectiles** — recolor the thrall's bolt (and its impact splash) to a per-pet color, so a red pet isn't shooting blue bolts. Shape, animation, and shading preserved.
- New: **Pet damage icon** — your pet's picture appears next to the damage it deals as your thrall, positioned correctly even among stacked hitsplats.
- Settings reorganized into sections.

## July 14, 2026

- Initial submission: cosmetic ghost pet that keeps pace with you (smooth per-frame movement, look-ahead steering, collision-checked resting), Arceuus thrall impersonation with pet attack animations, transmog with self-learning animations, per-pet animation speed.
