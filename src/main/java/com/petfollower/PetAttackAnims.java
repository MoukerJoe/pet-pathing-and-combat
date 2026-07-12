package com.petfollower;

import java.util.HashMap;
import java.util.Map;

/**
 * Pet NPC id (and name) -> that pet's own attack animation id.
 *
 * Extracted from the Companion Pets hub plugin's PetData table
 * (github.com/Mrnice98/Companion-Pets-Plugin). Every follower NPC id
 * variant is mapped; a lowercase-name fallback covers any variant that
 * slipped through. Pets absent here have no attack animation and fall back
 * to a lunge.
 */
final class PetAttackAnims
{
	private static final Map<Integer, Integer> BY_ID = new HashMap<>();
	private static final Map<String, Integer> BY_NAME = new HashMap<>();

	/** Attack anim for a follower, or -1 if it has none. */
	static int forPet(int npcId, String name)
	{
		Integer a = BY_ID.get(npcId);
		if (a != null)
		{
			return a;
		}
		if (name != null)
		{
			a = BY_NAME.get(name.toLowerCase());
		}
		return a == null ? -1 : a;
	}

	private static void putId(int npcId, int anim)
	{
		BY_ID.put(npcId, anim);
	}

	private static void putName(String name, int anim)
	{
		BY_NAME.put(name, anim);
	}

	private PetAttackAnims()
	{
	}

	static
	{
		putId(7519, 7398); // OLMLET
		putId(7520, 7398); // OLMLET
		putId(8196, 7422); // PUPPADILE
		putId(8201, 7422); // PUPPADILE
		putId(8197, 7483); // TEKTINY
		putId(8202, 7483); // TEKTINY
		putId(9511, 7493); // ENRAGED_TEKTINY
		putId(9513, 7493); // ENRAGED_TEKTINY
		putId(7525, 7433); // VANGUARD
		putId(7527, 7433); // VANGUARD
		putId(7528, 7433); // VANGUARD
		putId(7526, 7433); // VANGUARD
		putId(8203, 7433); // VANGUARD
		putId(8198, 7433); // VANGUARD
		putId(7529, 7433); // VANGUARD
		putId(8199, 7409); // VASA_MINIRIO
		putId(8204, 7409); // VASA_MINIRIO
		putId(8200, 7450); // VESPINA
		putId(8205, 7450); // VESPINA
		putId(9512, 7454); // FLYING_VESPINA
		putId(9514, 7454); // FLYING_VESPINA
		putId(8336, 8123); // LIL_ZIK
		putId(8337, 8123); // LIL_ZIK
		putId(10761, 8092); // LIL_MAIDEN
		putId(10870, 8092); // LIL_MAIDEN
		putId(10763, 8004); // LIL_NYLO
		putId(10872, 8004); // LIL_NYLO
		putId(10764, 8138); // LIL_SOT
		putId(10873, 8138); // LIL_SOT
		putId(10765, 8059); // LIL_XARP
		putId(10874, 8059); // LIL_XARP
		putId(11652, 9660); // TUMEKENS_GUARDIAN
		putId(11812, 9660); // TUMEKENS_GUARDIAN
		putId(11844, 9660); // TUMEKENS_DAMAGED_GUARDIAN
		putId(11850, 9660); // TUMEKENS_DAMAGED_GUARDIAN
		putId(11653, 9660); // ELIDINIS_GUARDIAN
		putId(11813, 9660); // ELIDINIS_GUARDIAN
		putId(11845, 9660); // ELIDINIS_DAMAGED_GUARDIAN
		putId(11851, 9660); // ELIDINIS_DAMAGED_GUARDIAN
		putId(11840, 9770); // AKKHITO
		putId(11846, 9770); // AKKHITO
		putId(11841, 9744); // BABI
		putId(11847, 9744); // BABI
		putId(11842, 9578); // KEPHRITI
		putId(11848, 9578); // KEPHRITI
		putId(11843, 2039); // ZEBO
		putId(11849, 2039); // ZEBO
		putId(5883, 7126); // ABYSSAL_ORPHAN
		putId(5884, 7126); // ABYSSAL_ORPHAN
		putId(964, 6562); // HELLPUPPY
		putId(3099, 6562); // HELLPUPPY
		putId(7891, 7770); // NOON
		putId(7892, 7770); // NOON
		putId(7890, 7808); // MIDNIGHT
		putId(7893, 7808); // MIDNIGHT
		putId(8492, 8234); // IKKLE_HYDRA
		putId(8518, 8234); // IKKLE_HYDRA
		putId(8517, 8234); // IKKLE_HYDRA
		putId(8495, 8234); // IKKLE_HYDRA
		putId(8519, 8234); // IKKLE_HYDRA
		putId(8493, 8234); // IKKLE_HYDRA
		putId(8494, 8234); // IKKLE_HYDRA
		putId(8520, 8234); // IKKLE_HYDRA
		putId(8493, 8241); // IKKLE_HYDRA_8493
		putId(8494, 8248); // IKKLE_HYDRA_8494
		putId(8495, 8256); // IKKLE_HYDRA_8495
		putId(6639, 3847); // SMOKE_DEVIL_6639
		putId(498, 3847); // SMOKE_DEVIL
		putId(6639, 3847); // SMOKE_DEVIL
		putId(8482, 3847); // SMOKE_DEVIL
		putId(6655, 3847); // SMOKE_DEVIL
		putId(8483, 3847); // SMOKE_DEVIL
		putId(494, 3992); // KRAKEN
		putId(6656, 3992); // KRAKEN
		putId(6640, 3992); // KRAKEN
		putId(13682, 8147); // RAX
		putId(13684, 8147); // RAX
		putId(13681, 11476); // NID
		putId(13683, 11476); // NID
		putId(6632, 7021); // GENERAL_GRAARDOR_JR
		putId(6644, 7021); // GENERAL_GRAARDOR_JR
		putId(6631, 6981); // KREEARRA_JR
		putId(6643, 6981); // KREEARRA_JR
		putId(6633, 6967); // ZILYANA_JR
		putId(6646, 6967); // ZILYANA_JR
		putId(6634, 6948); // KRIL_TSUTSAROTH_JR
		putId(6647, 6948); // KRIL_TSUTSAROTH_JR
		putId(11276, 9182); // NEXLING
		putId(11277, 9182); // NEXLING
		putId(12154, 10340); // BUTCH
		putId(12158, 10340); // BUTCH
		putId(12155, 10219); // BARON
		putId(12159, 10219); // BARON
		putId(12156, 10283); // LILVIATHAN
		putId(12160, 10283); // LILVIATHAN
		putId(12153, 10234); // WISP
		putId(12157, 10234); // WISP
		putId(7219, 10693); // SCURRY
		putId(7616, 10693); // SCURRY
		putId(14034, 11013); // MOXI
		putId(14046, 11013); // MOXI
		putId(14033, 11734); // HUBERTE
		putId(14045, 11734); // HUBERTE
		putId(7674, 7574); // JALNIBREK
		putId(7675, 7574); // JALNIBREK
		putId(8009, 7978); // TZREKZUK
		putId(8011, 7978); // TZREKZUK
		putId(5892, 2652); // TZREKJAD
		putId(5893, 2652); // TZREKJAD
		putId(10620, 7593); // JALREKJAD
		putId(10625, 7593); // JALREKJAD
		putId(12767, 10882); // SMOL_HEREDIT
		putId(12857, 10882); // SMOL_HEREDIT
		putId(8729, 8418); // YOUNGLLEF
		putId(8737, 8418); // YOUNGLLEF
		putId(8730, 8418); // CORRUPTED_YOUNGLLEF
		putId(8738, 8418); // CORRUPTED_YOUNGLLEF
		putId(318, 7980); // DARK_CORE
		putId(388, 7980); // DARK_CORE
		putId(8008, 1679); // CORPOREAL_CRITTER
		putId(8010, 1679); // CORPOREAL_CRITTER
		putId(2129, 1741); // SNAKELING_2129
		putId(2128, 1741); // SNAKELING_2128
		putId(2045, 1741); // SNAKELING
		putId(2047, 1741); // SNAKELING
		putId(2129, 1741); // SNAKELING
		putId(15163, 1741); // SNAKELING
		putId(2128, 1741); // SNAKELING
		putId(2130, 1741); // SNAKELING
		putId(2131, 1741); // SNAKELING
		putId(2046, 1741); // SNAKELING
		putId(2127, 1741); // SNAKELING
		putId(2132, 1741); // SNAKELING
		putId(9398, 8596); // LITTLE_NIGHTMARE
		putId(9399, 8596); // LITTLE_NIGHTMARE
		putId(8183, 8554); // LITTLE_PARASITE
		putId(8541, 8554); // LITTLE_PARASITE
		putId(6635, 3312);
		putId(10650, 3312); // BABY_MOLERAT
		putId(10651, 3312); // BABY_MOLERAT
		putId(6637, 6224); // KALPHITE_PRINCESS
		putId(6653, 6224); // KALPHITE_PRINCESS
		putId(6654, 6224); // KALPHITE_PRINCESS
		putId(6638, 6224); // KALPHITE_PRINCESS
		putId(6653, 6235); // KALPHITE_PRINCESS_6653
		putId(12005, 9918); // MUPHIN
		putId(12006, 9918); // MUPHIN
		putId(12016, 9918); // MUPHIN
		putId(12014, 9918); // MUPHIN
		putId(12007, 9918); // MUPHIN
		putId(12015, 9918); // MUPHIN
		putId(12006, 9918); // MUPHIN_12006
		putId(12007, 9918); // MUPHIN_12007
		putId(2143, 8004); // SRARACHA
		putId(11158, 8004); // SRARACHA
		putId(11157, 8004); // SRARACHA
		putId(11159, 8004); // SRARACHA
		putId(2144, 8004); // SRARACHA
		putId(11160, 8004); // SRARACHA
		putId(11157, 8004); // SRARACHA_11157
		putId(11158, 8004); // SRARACHA_11158
		putId(425, 69); // SKOTOS
		putId(7671, 69); // SKOTOS
		putId(8025, 7960); // VORKI
		putId(8029, 7960); // VORKI
		putId(6636, 81); // PRINCE_BLACK_DRAGON
		putId(6652, 81); // PRINCE_BLACK_DRAGON
		putId(2055, 3148); // CHAOS_ELEMENTAL_JR
		putId(5907, 3148); // CHAOS_ELEMENTAL_JR
		putId(495, 9989); // VENENATIS_SPIDERLING
		putId(12000, 9989); // VENENATIS_SPIDERLING
		putId(11985, 9989); // VENENATIS_SPIDERLING
		putId(5557, 9989); // VENENATIS_SPIDERLING
		putId(11981, 9989); // VENENATIS_SPIDERLING
		putId(11981, 5319); // VENENATIS_SPIDERLING_11981
		putId(497, 10012); // CALLISTO_CUB
		putId(11982, 10012); // CALLISTO_CUB
		putId(11986, 10012); // CALLISTO_CUB
		putId(5558, 10012); // CALLISTO_CUB
		putId(11982, 4921); // CALLISTO_CUB_11982
		putId(5536, 9971); // VETION_JR
		putId(5559, 9971); // VETION_JR
		putId(5560, 9971); // VETION_JR
		putId(5537, 9971); // VETION_JR
		putId(11987, 9971); // VETION_JR
		putId(11984, 9971); // VETION_JR
		putId(11988, 9971); // VETION_JR
		putId(11983, 9971); // VETION_JR
		putId(5537, 9971); // VETION_JR_5537
		putId(11983, 5499); // VETION_JR_11983
		putId(11984, 5499); // VETION_JR_11984
		putId(5547, 6254); // SCORPIAS_OFFSPRING
		putId(5561, 6254); // SCORPIAS_OFFSPRING
		putId(6616, 6254); // SCORPIAS_OFFSPRING
		putId(10476, 11978); // BRAN
		putId(12593, 11978); // BRAN
		putId(12592, 11977); // RIC
		putId(12595, 11977); // RIC
		putId(14203, 12146); // YAMI
		putId(14204, 12146); // YAMI
		putId(12768, 10953); // QUETZIN
		putId(12858, 10953); // QUETZIN
		putId(7335, 7314); // TANGLEROOT
		putId(9496, 7314); // TANGLEROOT
		putId(9499, 7314); // TANGLEROOT
		putId(9501, 7314); // TANGLEROOT
		putId(9492, 7314); // TANGLEROOT
		putId(9495, 7314); // TANGLEROOT
		putId(7352, 7314); // TANGLEROOT
		putId(9497, 7314); // TANGLEROOT
		putId(9494, 7314); // TANGLEROOT
		putId(9500, 7314); // TANGLEROOT
		putId(9498, 7314); // TANGLEROOT
		putId(9493, 7314); // TANGLEROOT
		putId(9492, 7314); // TANGLEROOT_9492
		putId(9493, 7314); // TANGLEROOT_9493
		putId(9494, 7314); // TANGLEROOT_9494
		putId(9495, 7314); // TANGLEROOT_9495
		putId(9496, 7314); // TANGLEROOT_9496
		putId(7334, 7311); // GIANT_SQUIRREL
		putId(7351, 7311); // GIANT_SQUIRREL
		putId(9666, 7311); // GIANT_SQUIRREL
		putId(9637, 7311); // DARK_SQUIRREL
		putId(9638, 7311); // DARK_SQUIRREL
		putId(7337, 7308); // RIFT_GUARDIAN
		putId(7356, 7308); // RIFT_GUARDIAN
		putId(7345, 7308); // RIFT_GUARDIAN
		putId(7363, 7308); // RIFT_GUARDIAN
		putId(7347, 7308); // RIFT_GUARDIAN
		putId(7359, 7308); // RIFT_GUARDIAN
		putId(7350, 7308); // RIFT_GUARDIAN
		putId(7361, 7308); // RIFT_GUARDIAN
		putId(7339, 7308); // RIFT_GUARDIAN
		putId(7348, 7308); // RIFT_GUARDIAN
		putId(8024, 7308); // RIFT_GUARDIAN
		putId(7341, 7308); // RIFT_GUARDIAN
		putId(7355, 7308); // RIFT_GUARDIAN
		putId(7349, 7308); // RIFT_GUARDIAN
		putId(7364, 7308); // RIFT_GUARDIAN
		putId(7343, 7308); // RIFT_GUARDIAN
		putId(7344, 7308); // RIFT_GUARDIAN
		putId(7357, 7308); // RIFT_GUARDIAN
		putId(7366, 7308); // RIFT_GUARDIAN
		putId(7362, 7308); // RIFT_GUARDIAN
		putId(7346, 7308); // RIFT_GUARDIAN
		putId(7358, 7308); // RIFT_GUARDIAN
		putId(8028, 7308); // RIFT_GUARDIAN
		putId(7360, 7308); // RIFT_GUARDIAN
		putId(7338, 7308); // RIFT_GUARDIAN
		putId(7367, 7308); // RIFT_GUARDIAN
		putId(7340, 7308); // RIFT_GUARDIAN
		putId(7342, 7308); // RIFT_GUARDIAN
		putId(7354, 7308); // RIFT_GUARDIAN
		putId(7365, 7308); // RIFT_GUARDIAN
		putId(7338, 7308); // RIFT_GUARDIAN_7338
		putId(7339, 7308); // RIFT_GUARDIAN_7339
		putId(7340, 7308); // RIFT_GUARDIAN_7340
		putId(7341, 7308); // RIFT_GUARDIAN_7341
		putId(7342, 7308); // RIFT_GUARDIAN_7342
		putId(7343, 7308); // RIFT_GUARDIAN_7343
		putId(7344, 7308); // RIFT_GUARDIAN_7344
		putId(7345, 7308); // RIFT_GUARDIAN_7345
		putId(7346, 7308); // RIFT_GUARDIAN_7346
		putId(7347, 7308); // RIFT_GUARDIAN_7347
		putId(7348, 7308); // RIFT_GUARDIAN_7348
		putId(7349, 7308); // RIFT_GUARDIAN_7349
		putId(7350, 7308); // RIFT_GUARDIAN_7350
		putId(8024, 7308); // RIFT_GUARDIAN_8024
		putId(11401, 9382); // GREATISH_GUARDIAN
		putId(11428, 9382); // GREATISH_GUARDIAN
		putId(6715, 6775); // HERON
		putId(6722, 6775); // HERON
		putId(6817, 6775); // GREAT_BLUE_HERON
		putId(10636, 6775); // GREAT_BLUE_HERON
		putId(6756, 5185); // BABY_CHINCHOMPA_6756
		putId(6718, 5185); // BABY_CHINCHOMPA
		putId(6756, 5185); // BABY_CHINCHOMPA
		putId(6721, 5185); // BABY_CHINCHOMPA
		putId(6720, 5185); // BABY_CHINCHOMPA
		putId(6757, 5185); // BABY_CHINCHOMPA
		putId(6719, 5185); // BABY_CHINCHOMPA
		putId(6758, 5185); // BABY_CHINCHOMPA
		putId(6759, 5185); // BABY_CHINCHOMPA
		putId(6758, 5185); // BABY_CHINCHOMPA_6758
		putId(6759, 5185); // BABY_CHINCHOMPA_6759
		putId(7336, 7318); // ROCKY
		putId(7353, 7318); // ROCKY
		putId(9850, 7318); // RED
		putId(9852, 7318); // RED
		putId(9851, 7318); // ZIGGY
		putId(9853, 7318); // ZIGGY
		putId(14539, 7318); // ZIGGY
		putId(7370, 6811); // PHOENIX_7370
		putId(3081, 6811); // PHOENIX_3081
		putId(3078, 6811); // PHOENIX_3078
		putId(3079, 6811); // PHOENIX_3079
		putId(3080, 6811); // PHOENIX_3080
		putId(7759, 7696); // HERBI
		putId(7760, 7696); // HERBI
		putId(8731, 8433); // SMOLCANO
		putId(8739, 8433); // SMOLCANO
		putId(10562, 8905); // TINY_TEMPOR
		putId(10637, 8905); // TINY_TEMPOR
		putId(11402, 2186); // ABYSSAL_PROTECTOR
		putId(11429, 2186); // ABYSSAL_PROTECTOR
		putId(6642, 5411); // PENANCE_PET
		putId(6674, 5411); // PENANCE_PET
		putId(6296, 6559); // BLOODHOUND
		putId(7232, 6559); // BLOODHOUND
		putId(4001, 6761); // CHOMPY_CHICK
		putId(4002, 6761); // CHOMPY_CHICK
		putId(2833, 8844); // LIL_CREATOR
		putId(3566, 8844); // LIL_CREATOR
		putId(3564, 8840); // LIL_DESTRUCTOR
		putId(5008, 8840); // LIL_DESTRUCTOR
		putId(14167, 6562); // ARCHIBALD
		putId(15596, 6562); // ARCHIBALD
		putId(15590, 6562); // ARCHIBALD
		putId(15591, 6562); // ARCHIBALD
		putId(15587, 6562); // ARCHIBALD
		putId(15599, 6562); // ARCHIBALD
		putId(15588, 6562); // ARCHIBALD
		putId(15592, 6562); // ARCHIBALD
		putId(15595, 6562); // ARCHIBALD
		putId(15597, 6562); // ARCHIBALD
		putId(15600, 6562); // ARCHIBALD
		putId(15586, 6562); // ARCHIBALD
		putId(15598, 6562); // ARCHIBALD
		putId(15593, 6562); // ARCHIBALD
		putId(15594, 6562); // ARCHIBALD

		putName("olmlet", 7398);
		putName("puppadile", 7422);
		putName("tektiny", 7483);
		putName("enraged tektiny", 7493);
		putName("vanguard", 7433);
		putName("vasa minirio", 7409);
		putName("vespina", 7450);
		putName("flying vespina", 7454);
		putName("lil' zik", 8123);
		putName("lil' maiden", 8092);
		putName("lil' nylo", 8004);
		putName("lil' sot", 8138);
		putName("lil' xarp", 8059);
		putName("tumeken's guardian", 9660);
		putName("tumeken's damaged guardian", 9660);
		putName("elidinis' guardian", 9660);
		putName("elidinis' damaged guardian", 9660);
		putName("akkhito", 9770);
		putName("babi", 9744);
		putName("kephriti", 9578);
		putName("zebo", 2039);
		putName("abyssal orphan", 7126);
		putName("hellpuppy", 6562);
		putName("noon", 7770);
		putName("midnight", 7808);
		putName("ikkle hydra", 8234);
		putName("ikkle hydra", 8241);
		putName("ikkle hydra", 8248);
		putName("ikkle hydra", 8256);
		putName("pet smoke devil", 3847);
		putName("kraken", 3992);
		putName("rax", 8147);
		putName("nid", 11476);
		putName("general graardor jr.", 7021);
		putName("kree'arra jr.", 6981);
		putName("zilyana jr.", 6967);
		putName("k'ril tsutsaroth jr.", 6948);
		putName("nexling", 9182);
		putName("butch", 10340);
		putName("baron", 10219);
		putName("lil'viathan", 10283);
		putName("wisp", 10234);
		putName("scurry", 10693);
		putName("moxi", 11013);
		putName("huberte", 11734);
		putName("jal-nib-rek", 7574);
		putName("tzrek-zuk", 7978);
		putName("tzrek-jad", 2652);
		putName("jalrek-jad", 7593);
		putName("smol heredit", 10882);
		putName("youngllef", 8418);
		putName("corrupted youngllef", 8418);
		putName("dark core", 7980);
		putName("corporeal critter", 1679);
		putName("snakeling", 1741);
		putName("little nightmare", 8596);
		putName("little parasite", 8554);
		putName("baby mole", 3312);
		putName("baby mole-rat", 3312);
		putName("kalphite princess", 6224);
		putName("kalphite princess", 6235);
		putName("muphin", 9918);
		putName("sraracha", 8004);
		putName("skotos", 69);
		putName("vorki", 7960);
		putName("prince black dragon", 81);
		putName("chaos elemental jr.", 3148);
		putName("venenatis spiderling", 9989);
		putName("venenatis spiderling", 5319);
		putName("callisto cub", 10012);
		putName("callisto cub", 4921);
		putName("vet'ion jr.", 9971);
		putName("vet'ion jr.", 5499);
		putName("scorpia's offspring", 6254);
		putName("bran", 11978);
		putName("ric", 11977);
		putName("yami", 12146);
		putName("quetzin", 10953);
		putName("tangleroot", 7314);
		putName("giant squirrel", 7311);
		putName("dark squirrel", 7311);
		putName("rift guardian", 7308);
		putName("greatish guardian", 9382);
		putName("heron", 6775);
		putName("great blue heron", 6775);
		putName("baby chinchompa", 5185);
		putName("rocky", 7318);
		putName("red", 7318);
		putName("ziggy", 7318);
		putName("phoenix", 6811);
		putName("herbi", 7696);
		putName("smolcano", 8433);
		putName("tiny tempor", 8905);
		putName("abyssal protector", 2186);
		putName("penance pet", 5411);
		putName("bloodhound", 6559);
		putName("chompy chick", 6761);
		putName("lil' creator", 8844);
		putName("lil' destructor", 8840);
		putName("mochi", 6562);
	}
}