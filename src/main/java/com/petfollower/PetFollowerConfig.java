package com.petfollower;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Range;

@ConfigGroup("petfollower")
public interface PetFollowerConfig extends Config
{
	@ConfigItem(
		keyName = "handoffEnabled",
		name = "Hand back when idle",
		description = "When you stand still, the ghost walks onto the real pet's spot and hands control back so you can right-click it (Pick up etc.). Disable for maximum smoothness — the pet stays ghosted and is not clickable until you toggle this back on or the plugin resets.",
		position = 0
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
		position = 1
	)
	default int handoffTicks()
	{
		return 25;
	}

	@ConfigItem(
		keyName = "callFollowerInteract",
		name = "Interact on Call Follower",
		description = "When you use the Call Follower option, briefly show the real pet so you can right-click it (Pick up / Metamorphosis).",
		position = 2
	)
	default boolean callFollowerInteract()
	{
		return true;
	}

	@ConfigItem(
		keyName = "thrallMode",
		name = "Pet impersonates thralls",
		description = "When you summon an Arceuus thrall, your pet takes its place: the thrall is hidden, the pet fights from its spot and plays its own attack animation, and returns to your side when it expires.",
		position = 3
	)
	default boolean thrallMode()
	{
		return true;
	}

	@Range(min = 60, max = 360)
	@ConfigItem(
		keyName = "turnSpeed",
		name = "Turn speed (deg/sec)",
		description = "How fast the pet rotates when changing direction. Lower = lazier, sweeping turns; higher = snappier.",
		position = 5
	)
	default int turnSpeed()
	{
		return 180;
	}

	@ConfigItem(
		keyName = "transmogPet",
		name = "Transmog pet",
		description = "Render your follower as this pet, built from the game cache. Cosmetic and client-side only — only you see it.",
		position = 6
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
		position = 8
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
		position = 9
	)
	default int petSpeed()
	{
		return 100;
	}

	@ConfigItem(
		keyName = "cadenceMessages",
		name = "Pet speed chat messages",
		description = "Show a chat message with the loaded speed setting whenever your<br>"
			+ "displayed pet changes (pet swap or transmog), so you always know<br>"
			+ "what the current pet is set to.",
		position = 10
	)
	default boolean cadenceMessages()
	{
		return true;
	}
}
