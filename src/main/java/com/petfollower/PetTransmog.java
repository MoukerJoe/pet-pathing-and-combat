package com.petfollower;

/**
 * Pets selectable in the transmog dropdown, by display name. Ids are the
 * follower NPC ids matching PetPoseAnims, so every entry animates.
 */
public enum PetTransmog
{
	OFF("Off", -1),
	ABYSSAL_ORPHAN("Abyssal Orphan", 5883),
	ABYSSAL_PROTECTOR("Abyssal Protector", 11402),
	AKKHITO("Akkhito", 11840),
	ARCHIBALD("Archibald", 14167),
	BABI("Babi", 11841),
	BABY_CHINCHOMPA("Baby Chinchompa", 6718),
	BABY_MOLE("Baby Mole", 6635),
	BABY_MOLE_RAT("Baby Mole-rat", 10650),
	BARON("Baron", 12155),
	BEAVER("Beaver", 12169),
	BLOODHOUND("Bloodhound", 6296),
	BRAN("Bran", 10476),
	BUTCH("Butch", 12154),
	CALLISTO_CUB("Callisto Cub", 497),
	CHAOS_ELEMENTAL_JR("Chaos Elemental Jr.", 2055),
	CHOMPY_CHICK("Chompy Chick", 4001),
	CORPOREAL_CRITTER("Corporeal Critter", 8008),
	CORRUPTED_YOUNGLLEF("Corrupted Youngllef", 8730),
	DAGANNOTH_PRIME_JR("Dagannoth Prime Jr.", 6627),
	DAGANNOTH_REX_JR("Dagannoth Rex Jr.", 6630),
	DAGANNOTH_SUPREME_JR("Dagannoth Supreme Jr.", 6626),
	DARK_CORE("Dark Core", 318),
	DARK_SQUIRREL("Dark Squirrel", 9637),
	ELIDINIS_GUARDIAN("Elidinis' Guardian", 11653),
	ENRAGED_TEKTINY("Enraged Tektiny", 9511),
	FLYING_VESPINA("Flying Vespina", 9512),
	GENERAL_GRAARDOR_JR("General Graardor Jr.", 6632),
	GIANT_SQUIRREL("Giant Squirrel", 7334),
	GREAT_BLUE_HERON("Great Blue Heron", 6817),
	GREATISH_GUARDIAN("Greatish Guardian", 11401),
	HELLPUPPY("Hellpuppy", 964),
	HERBI("Herbi", 7759),
	HERON("Heron", 6715),
	HUBERTE("Huberte", 14033),
	IKKLE_HYDRA("Ikkle Hydra", 8492),
	JAL_NIB_REK("Jal-Nib-Rek", 7674),
	JALREK_JAD("JalRek-Jad", 10620),
	KRIL_TSUTSAROTH_JR("K'ril Tsutsaroth Jr.", 6634),
	KALPHITE_PRINCESS("Kalphite Princess", 6637),
	KEPHRITI("Kephriti", 11842),
	KRAKEN("Kraken", 6640),
	KREEARRA_JR("Kree'arra Jr.", 6631),
	LIL_BLOAT("Lil' Bloat", 10762),
	LIL_CREATOR("Lil' Creator", 2833),
	LIL_DESTRUCTOR("Lil' Destructor", 3564),
	LIL_MAIDEN("Lil' Maiden", 10761),
	LIL_NYLO("Lil' Nylo", 10763),
	LIL_SOT("Lil' Sot", 10764),
	LIL_XARP("Lil' Xarp", 10765),
	LIL_ZIK("Lil' Zik", 8336),
	LILVIATHAN("Lil'viathan", 12156),
	LITTLE_NIGHTMARE("Little Nightmare", 9398),
	LITTLE_PARASITE("Little Parasite", 8183),
	MIDNIGHT("Midnight", 7890),
	MOXI("Moxi", 14034),
	MUPHIN("Muphin", 12005),
	NEXLING("Nexling", 11276),
	NID("Nid", 13681),
	NOON("Noon", 7891),
	OLMLET("Olmlet", 7519),
	PENANCE_PET("Penance Pet", 6642),
	PET_SMOKE_DEVIL("Pet Smoke Devil", 6639),
	PHOENIX("Phoenix", 7370),
	PRINCE_BLACK_DRAGON("Prince Black Dragon", 6636),
	PUPPADILE("Puppadile", 8196),
	QUETZIN("Quetzin", 12768),
	RAX("Rax", 13682),
	RED("Red", 9850),
	RIC("Ric", 12592),
	RIFT_GUARDIAN("Rift Guardian", 7337),
	ROCK_GOLEM("Rock Golem", 2182),
	ROCKY("Rocky", 7336),
	SCORPIAS_OFFSPRING("Scorpia's Offspring", 5547),
	SCURRY("Scurry", 7219),
	SKOTOS("Skotos", 425),
	SMOL_HEREDIT("Smol Heredit", 12767),
	SMOLCANO("Smolcano", 8731),
	SNAKELING("Snakeling", 2045),
	SRARACHA("Sraracha", 2143),
	TANGLEROOT("Tangleroot", 7335),
	TEKTINY("Tektiny", 8197),
	TINY_TEMPOR("Tiny Tempor", 10562),
	TUMEKENS_GUARDIAN("Tumeken's Guardian", 11652),
	TZREK_JAD("TzRek-Jad", 5892),
	TZREK_ZUK("TzRek-Zuk", 8009),
	VANGUARD("Vanguard", 8198),
	VASA_MINIRIO("Vasa Minirio", 8199),
	VENENATIS_SPIDERLING("Venenatis Spiderling", 495),
	VESPINA("Vespina", 8200),
	VETION_JR("Vet'ion Jr.", 5536),
	VORKI("Vorki", 8025),
	WISP("Wisp", 12153),
	YAMI("Yami", 14203),
	YOUNGLLEF("Youngllef", 8729),
	ZEBO("Zebo", 11843),
	ZIGGY("Ziggy", 9851),
	ZILYANA_JR("Zilyana Jr.", 6633);

	private final String displayName;
	private final int npcId;

	PetTransmog(String displayName, int npcId)
	{
		this.displayName = displayName;
		this.npcId = npcId;
	}

	public int getNpcId()
	{
		return npcId;
	}

	@Override
	public String toString()
	{
		return displayName;
	}
}
