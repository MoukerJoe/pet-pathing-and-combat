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
}
