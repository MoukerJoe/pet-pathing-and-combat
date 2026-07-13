# Pet Pathing and Combat

Your pet finally keeps up with you.

Pets have trailed 2-3 tiles behind players since forever. This plugin makes your pet move like an actual companion — it stays right behind you, walks when you walk, runs when you run, and turns where you turn. Works with every pet in the game (boss pets, metamorphs, skilling pets, all of them) with zero setup.

It can also make your pet fight as your thrall. Summon a thrall and your pet takes its place — it runs over, attacks whatever the thrall is attacking using its own attack animation, then comes back to your side when the thrall expires.

Everything is client-side and visual only. Your real pet and thrall still exist and behave like normal underneath — other players see nothing different, and nothing is sent to the server.

<!-- drag Comparison.gif here -->

<!-- drag Thrall Showcase.gif here -->

## Settings

- **Hand back when idle** — when you stand still for a bit, control goes back to the real pet so you can right-click it (pick up, metamorphosis, etc.)
- **Idle handoff delay** — how long you stand still before that happens
- **Interact on Call Follower** — using Call Follower shows the real pet for a few seconds so you can click it
- **Pet impersonates thralls** — toggle the thrall feature

## Known quirks

- It's cosmetic only — the real pet is still trailing behind under the hood, so nothing about game mechanics changes.
- If your pet leaves render distance (long runs, teleports) it pops back in behind you, same as pets normally do.
- Occasionally a thrall attack won't trigger the pet's attack animation — some magic thrall attacks have no animation to detect.

## Credits

Pet attack animation IDs come from the [Companion Pets plugin](https://github.com/Mrnice98/Companion-Pets-Plugin) by Mrnice98.

Built with some AI assistance.
