# Pet Pathing and Combat

A cosmetic RuneLite plugin that makes your follower pet **keep pace with you** instead of trailing a couple of tiles behind, and (optionally) lets your pet **stand in for your  thralls** in combat.

Everything is purely client-side and visual. Nothing is sent to the server, no input is generated, and other players see your pet exactly as they normally would.

## What it does

- **Tighter following.** While you move, the real (server-controlled) pet is hidden and a client-side "ghost" copy of it is drawn following your on-screen path about one tile back, matching your walk/run speed with natural acceleration, turning, and idle settling. When you stop, control is handed back to the real pet so you can right-click it as usual.
- **Pixel-accurate.** The ghost mirrors your real pet's own model, so it is the correct size, colour, and animation for any pet — including metamorphosis/boss pets — with no per-pet setup.
- **Thrall impersonation (optional).** When you cast an Arceuus Resurrection spell, the summoned thrall is hidden and your pet takes its place: it rides the thrall's real position and facing (so pathing and targeting stay authentic) and plays its own attack animation each time the thrall attacks. When the thrall expires, your pet returns to your side. Works across teleports and render-range loss.
- **Call Follower interaction (optional).** Using the *Call Follower* option briefly reveals the real pet so you can right-click it (Pick up / Metamorphosis).

<img width="620" height="240" alt="Comparison" src="https://github.com/user-attachments/assets/7b963a9e-06f0-4af5-8843-8e29ddee9a80" />

<img width="640" height="330" alt="Run Showcase" src="https://github.com/user-attachments/assets/325c0ab3-3f70-4da0-aa33-5c9f457222b6" />

<img width="520" height="308" alt="Thrall Showcase" src="https://github.com/user-attachments/assets/1aed0b70-8be2-4b94-8f95-fe2e36f63038" />



## Settings

| Option | Default | Description |
| --- | --- | --- |
| Hand back when idle | On | When you stand still, walk the pet onto the real pet's spot and hand control back so it's clickable. |
| Idle handoff delay | 25 ticks | How long you must stand still before the hand-back begins. |
| Interact on Call Follower | On | Briefly show the real pet when you use *Call Follower*. |
| Pet impersonates thralls | On | Let your pet stand in for Arceuus thralls. |

## Limitations

- It's cosmetic only — it does **not** change how far the *real* pet is, only what you see. The real pet still follows at its normal speed underneath.
- Magic (ghostly) thralls sometimes attack with only a projectile and no animation; those attacks still fire the pet's attack animation via projectile detection, but very unusual thralls may not.
- During a scene reload (region change / long teleport) the real pet isn't rendered for a moment; the ghost briefly disappears and reappears with a small reorient when the pet renders again, matching how the game itself pops pets in.

## Credits

Pet attack-animation IDs are derived from the data table in the
[Companion Pets plugin](https://github.com/Mrnice98/Companion-Pets-Plugin) by
Mrnice98 (BSD 2-Clause).

Portions of this plugin were developed with the assistance of AI.
