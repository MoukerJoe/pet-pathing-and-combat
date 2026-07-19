package com.petfollower;

import java.awt.Color;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;

@ConfigGroup("petfollower")
public interface PetFollowerConfig extends Config
{
	// ------------------------------------------------------------------
	// Sections
	// ------------------------------------------------------------------

	@ConfigSection(
		name = "Following",
		description = "How the pet moves and turns while following you.",
		position = 0
	)
	String followingSection = "following";

	@ConfigSection(
		name = "Appearance",
		description = "Transmog and per-pet visual tuning.",
		position = 1
	)
	String appearanceSection = "appearance";

	@ConfigSection(
		name = "Thrall combat",
		description = "Your pet standing in for Arceuus thralls.",
		position = 2
	)
	String thrallSection = "thrall";

	@ConfigSection(
		name = "Interaction",
		description = "Handing control back to the real pet so you can click it.",
		position = 3
	)
	String interactionSection = "interaction";

	// ------------------------------------------------------------------
	// Following
	// ------------------------------------------------------------------

	@ConfigItem(
		keyName = "followStyle",
		name = "Following style",
		description = "The pet's personality when following.<br>"
			+ "Strong — right at your heel, instant reactions (the classic).<br>"
			+ "Medium — relaxed: lets you pull a tile ahead before reacting,<br>"
			+ "and its trailing distance drifts a little over time.<br>"
			+ "Loose — its own creature: reacts late, trails at a wandering<br>"
			+ "distance, takes its time settling when you stop.<br>"
			+ "Every style paths identically around walls and always catches<br>"
			+ "up at range — only the temperament changes.",
		position = 0,
		section = followingSection
	)
	default FollowStyle followStyle()
	{
		return FollowStyle.MEDIUM;
	}

	@Range(min = 60, max = 360)
	@ConfigItem(
		keyName = "turnSpeed",
		name = "Turn speed (deg/sec)",
		description = "How fast the pet rotates when changing direction. Lower = lazier, sweeping turns; higher = snappier.",
		position = 1,
		section = followingSection
	)
	default int turnSpeed()
	{
		return 180;
	}

	@ConfigItem(
		keyName = "alwaysFaceOwner",
		name = "Pet always faces you",
		description = "The pet's body points at you at ALL times, even while moving.<br>"
			+ "Hovering/floaty pets look great with this; four-legged pets will<br>"
			+ "visibly sidestep while walking (OSRS models have no strafe<br>"
			+ "animation), which is why it's off by default.",
		position = 2,
		section = followingSection
	)
	default boolean alwaysFaceOwner()
	{
		return false;
	}

	// ------------------------------------------------------------------
	// Appearance
	// ------------------------------------------------------------------

	@ConfigItem(
		keyName = "transmogPet",
		name = "Transmog pet",
		description = "Render your follower as this pet, built from the game cache. Cosmetic and client-side only — only you see it.",
		position = 0,
		section = appearanceSection
	)
	default PetTransmog transmog()
	{
		return PetTransmog.OFF;
	}

	@ConfigItem(
		keyName = "speedTweaksEnabled",
		name = "Adjust animation speed",
		description = "Speed up the walk cycle of pets that have no real run animation,<br>"
			+ "so their feet keep up with the ground.<br>"
			+ "Turn off to leave every animation exactly as the game plays it.",
		position = 1,
		section = appearanceSection
	)
	default boolean speedTweaksEnabled()
	{
		return true;
	}

	@Range(min = 100, max = 300)
	@ConfigItem(
		keyName = "petSpeed",
		name = "Pet animation speed (%)",
		description = "How fast this pet's run animation plays.<br>"
			+ "100% = vanilla: the animation is not touched at all.<br>"
			+ "Raise it to speed up the run cycle of pets that have no real<br>"
			+ "run animation, so their feet keep up with the ground.<br>"
			+ "The slider always shows and edits the pet currently on screen<br>"
			+ "(real or transmog), and each pet's value is saved and restored<br>"
			+ "whenever that pet is out.",
		position = 2,
		section = appearanceSection
	)
	default int petSpeed()
	{
		return 100;
	}

	@Range(min = 0, max = 32)
	@ConfigItem(
		keyName = "groundClearance",
		name = "Pet ground clearance",
		description = "Raises the displayed pet slightly off the terrain.<br>"
			+ "Some pets (Youngllef etc.) have a glow/aura at their base that<br>"
			+ "sits exactly at ground level and gets swallowed by the terrain<br>"
			+ "when rendered as the ghost. A few units of clearance keeps it<br>"
			+ "visible; a tile is 128 units, so values under ~10 are invisible<br>"
			+ "as \"floating\". 0 = off. Saved per pet.",
		position = 3,
		section = appearanceSection
	)
	default int groundClearance()
	{
		return 0;
	}

	@ConfigItem(
		keyName = "cadenceMessages",
		name = "Pet speed chat messages",
		description = "Show a chat message with the loaded speed setting whenever your<br>"
			+ "displayed pet changes (pet swap or transmog), so you always know<br>"
			+ "what the current pet is set to.",
		position = 4,
		section = appearanceSection
	)
	default boolean cadenceMessages()
	{
		return true;
	}

	// ------------------------------------------------------------------
	// Thrall combat
	// ------------------------------------------------------------------

	@ConfigItem(
		keyName = "thrallMode",
		name = "Pet impersonates thralls",
		description = "When you summon an Arceuus thrall, your pet takes its place: the thrall is hidden, the pet fights from its spot and plays its own attack animation, and returns to your side when it expires.",
		position = 0,
		section = thrallSection
	)
	default boolean thrallMode()
	{
		return true;
	}

	@ConfigItem(
		keyName = "tintThrallBolt",
		name = "Tint thrall projectiles",
		description = "Recolor the thrall's projectile to the color below, so a red<br>"
			+ "pet isn't shooting blue bolts. The bolt keeps its exact shape,<br>"
			+ "animation, and shading — only the color changes. Client-side<br>"
			+ "and cosmetic, like everything else here.",
		position = 1,
		section = thrallSection
	)
	default boolean tintThrallBolt()
	{
		return false;
	}

	@ConfigItem(
		keyName = "thrallBoltColor",
		name = "Projectile color",
		description = "The color the thrall's projectile (and its impact splash on the<br>"
			+ "target) is tinted to. Saved per pet: the picker always shows and<br>"
			+ "edits the pet currently displayed, like the speed slider.",
		position = 2,
		section = thrallSection
	)
	default Color thrallBoltColor()
	{
		return new Color(228, 48, 24);
	}

	@ConfigItem(
		keyName = "petHitIcon",
		name = "Pet damage icon",
		description = "Show your pet's picture next to the damage it deals as your<br>"
			+ "thrall, so its hits read as its own. Uses the pet's inventory-item<br>"
			+ "sprite, matched by name automatically.",
		position = 3,
		section = thrallSection
	)
	default boolean petHitIcon()
	{
		return true;
	}

	@Range(min = 8, max = 36)
	@ConfigItem(
		keyName = "petHitIconSize",
		name = "Damage icon size",
		description = "Width of the pet damage icon in pixels.",
		position = 4,
		section = thrallSection
	)
	default int petHitIconSize()
	{
		return 13;
	}

	// ------------------------------------------------------------------
	// Interaction
	// ------------------------------------------------------------------

	@ConfigItem(
		keyName = "handoffEnabled",
		name = "Hand back when idle",
		description = "When you stand still, the ghost walks onto the real pet's spot and hands control back so you can right-click it (Pick up etc.). Disable for maximum smoothness — the pet stays ghosted and is not clickable until you toggle this back on or the plugin resets.",
		position = 0,
		section = interactionSection
	)
	default boolean handoffEnabled()
	{
		return true;
	}

	@Range(min = 1, max = 100)
	@ConfigItem(
		keyName = "handoffTicks",
		name = "Idle handoff delay (game ticks)",
		description = "How long you must stand still before the hand-back starts. 25 ticks = 15 seconds.",
		position = 1,
		section = interactionSection
	)
	default int handoffTicks()
	{
		return 25;
	}

	@ConfigItem(
		keyName = "callFollowerInteract",
		name = "Interact on Call Follower",
		description = "When you use the Call Follower option, briefly show the real pet so you can right-click it (Pick up / Metamorphosis).",
		position = 2,
		section = interactionSection
	)
	default boolean callFollowerInteract()
	{
		return true;
	}
}
