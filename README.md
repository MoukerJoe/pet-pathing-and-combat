# Pet Pathing and Combat

Your pet finally keeps up with you.

Pets have trailed 2-3 tiles behind players since forever. This plugin makes your pet move like an actual companion. They stay right behind you, walks when you walk, runs when you run, and turns where you turn. Zero setup required.

It can also make your pet fight as your thrall. Summon a thrall and your pet takes its place. It runs over, attacks whatever the thrall is attacking using its own attack animation, then comes back to your side when the thrall expires.

There's also a transmog (work in progress) — pick any pet from the dropdown in settings and your follower shows up as that pet, with its own animations. Only you see it.

Everything is client-side and visual only. Your real pet and thrall still exist and behave like normal underneath.

<img width="620" height="240" alt="Comparison" src="https://github.com/user-attachments/assets/7b963a9e-06f0-4af5-8843-8e29ddee9a80" />

<img width="640" height="330" alt="Run Showcase" src="https://github.com/user-attachments/assets/325c0ab3-3f70-4da0-aa33-5c9f457222b6" />

<img width="520" height="308" alt="Thrall Showcase" src="https://github.com/user-attachments/assets/1aed0b70-8be2-4b94-8f95-fe2e36f63038" />

## Settings

- **Hand back when idle** — when you stand still for a bit, control goes back to the real pet so you can right-click it (pick up, metamorphosis, etc.)
- **Idle handoff delay** — how long you stand still before that happens
- **Interact on Call Follower** — using Call Follower shows the real pet for a few seconds so you can click it
- **Pet impersonates thralls** — toggle the thrall feature
- **Turn speed** — how fast the pet rotates when changing direction, if you want lazier or snappier turns
- **Pet watches you** — the pet leans toward you through turns and looks at you when it stops
- **Transmog** — show your follower as any pet (work in progress, see below)

## Known quirks

- It's cosmetic only. The real pet is still trailing behind under the hood, so nothing about game mechanics changes.
- If your pet leaves render distance (long runs, teleports) it pops back in behind you, same as pets normally do.
- Transmog is a work in progress. Most pets look right but a few animate strangely (Chaos Elemental Jr. for example). It also gets better on its own: whenever you have a real pet out, the plugin learns its true animations and uses them for that transmog from then on.
- If you use 117HD, its pet light glows where the real (hidden) pet is rather than on the displayed one. That light belongs to 117HD and can't be moved by this plugin.
- Not all use cases tested. Please submit bug reports if found and will update ASAP.
- I do not own any Post-DT2 pets so I am not entirely sure how they will react with this plugin.
- I do not own many pets so some pets may behave differently or clip because of their model. Please let me know and I will address.

## Credits

Pet attack animation IDs come from the [Companion Pets plugin](https://github.com/Mrnice98/Companion-Pets-Plugin) by Mrnice98.

Built in part with AI assistance.
