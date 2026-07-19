package com.petfollower;

/**
 * How eagerly the pet follows. Only the personality changes — every style
 * uses the same pathing, wall handling, and long-range catch-up, so the pet
 * can never get stuck or left behind.
 *
 * Display names stay one word: the config panel sizes the dropdown to its
 * widest entry and steals that width from the setting's own label, so
 * anything longer pushes "Following style" out of view. The temperament of
 * each style is spelled out in the config description instead.
 */
public enum FollowStyle
{
	STRONG("Strong"),
	MEDIUM("Medium"),
	LOOSE("Loose");

	private final String displayName;

	FollowStyle(String displayName)
	{
		this.displayName = displayName;
	}

	@Override
	public String toString()
	{
		return displayName;
	}
}
