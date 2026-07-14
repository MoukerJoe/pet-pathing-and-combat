package com.petfollower;

import java.util.HashMap;
import java.util.Map;

/**
 * Pet NPC id (and name) -> that pet's idle/walk/run pose animation ids.
 *
 * Extracted from the Companion Pets hub plugin's PetData table
 * (github.com/Mrnice98/Companion-Pets-Plugin). Used by the transmog preview
 * to animate pets built purely from the cache (the API exposes pose anims
 * only for spawned NPCs, not arbitrary definitions).
 */
final class PetPoseAnims
{
	static final class Pose
	{
		final int idle;
		final int walk;
		final int run;

		Pose(int idle, int walk, int run)
		{
			this.idle = idle;
			this.walk = walk;
			this.run = run;
		}
	}

	private static final Map<Integer, Pose> BY_ID = new HashMap<>();
	private static final Map<String, Pose> BY_NAME = new HashMap<>();

	static Pose forPet(int npcId, String name)
	{
		Pose p = BY_ID.get(npcId);
		if (p != null)
		{
			return p;
		}
		return name != null ? BY_NAME.get(name.toLowerCase()) : null;
	}

	private static void putId(int npcId, int idle, int walk, int run)
	{
		BY_ID.put(npcId, new Pose(idle, walk, run));
	}

	private static void putName(String name, int idle, int walk, int run)
	{
		BY_NAME.put(name, new Pose(idle, walk, run));
	}

	private PetPoseAnims()
	{
	}

	static
	{
		putId(7519, 7396, 7395, 7395); // OLMLET
		putId(7520, 7396, 7395, 7395); // OLMLET
		putId(8196, 7417, 7982, 7982); // PUPPADILE
		putId(8201, 7417, 7982, 7982); // PUPPADILE
		putId(8197, 7476, 7983, 7983); // TEKTINY
		putId(8202, 7476, 7983, 7983); // TEKTINY
		putId(9511, 7485, 8637, 8637); // ENRAGED_TEKTINY
		putId(9513, 7485, 8637, 8637); // ENRAGED_TEKTINY
		putId(7525, 7430, 7984, 7984); // VANGUARD
		putId(7527, 7430, 7984, 7984); // VANGUARD
		putId(7528, 7430, 7984, 7984); // VANGUARD
		putId(7526, 7430, 7984, 7984); // VANGUARD
		putId(8203, 7430, 7984, 7984); // VANGUARD
		putId(8198, 7430, 7984, 7984); // VANGUARD
		putId(7529, 7430, 7984, 7984); // VANGUARD
		putId(8199, 7416, 7985, 7985); // VASA_MINIRIO
		putId(8204, 7416, 7985, 7985); // VASA_MINIRIO
		putId(8200, 7449, 7986, 7986); // VESPINA
		putId(8205, 7449, 7986, 7986); // VESPINA
		putId(9512, 8639, 8639, 8639); // FLYING_VESPINA
		putId(9514, 8639, 8639, 8639); // FLYING_VESPINA
		putId(8336, 8120, 8122, 8122); // LIL_ZIK
		putId(8337, 8120, 8122, 8122); // LIL_ZIK
		putId(10761, 8090, 8090, 8090); // LIL_MAIDEN
		putId(10870, 8090, 8090, 8090); // LIL_MAIDEN
		putId(10762, 8080, 9031, 9031); // LIL_BLOAT
		putId(10871, 8080, 9031, 9031); // LIL_BLOAT
		putId(10763, 8002, 8003, 8003); // LIL_NYLO
		putId(10872, 8002, 8003, 8003); // LIL_NYLO
		putId(10764, 8137, 9032, 9032); // LIL_SOT
		putId(10873, 8137, 9032, 9032); // LIL_SOT
		putId(10765, 9033, 9033, 9033); // LIL_XARP
		putId(10874, 9033, 9033, 9033); // LIL_XARP
		putId(11652, 9655, 9651, 9651); // TUMEKENS_GUARDIAN
		putId(11812, 9655, 9651, 9651); // TUMEKENS_GUARDIAN
		putId(11844, 9420, 9420, 9420); // TUMEKENS_DAMAGED_GUARDIAN
		putId(11850, 9420, 9420, 9420); // TUMEKENS_DAMAGED_GUARDIAN
		putId(11653, 9656, 9652, 9652); // ELIDINIS_GUARDIAN
		putId(11813, 9656, 9652, 9652); // ELIDINIS_GUARDIAN
		putId(11845, 9420, 9420, 9420); // ELIDINIS_DAMAGED_GUARDIAN
		putId(11851, 9420, 9420, 9420); // ELIDINIS_DAMAGED_GUARDIAN
		putId(11840, 9760, 9421, 9421); // AKKHITO
		putId(11846, 9760, 9421, 9421); // AKKHITO
		putId(11841, 9741, 9739, 9739); // BABI
		putId(11847, 9741, 9739, 9739); // BABI
		putId(11842, 9572, 9419, 9419); // KEPHRITI
		putId(11848, 9572, 9419, 9419); // KEPHRITI
		putId(11843, 2037, 2036, 2036); // ZEBO
		putId(11849, 2037, 2036, 2036); // ZEBO
		putId(5883, 7125, 7124, 7124); // ABYSSAL_ORPHAN
		putId(5884, 7125, 7124, 7124); // ABYSSAL_ORPHAN
		putId(964, 6561, 6560, 6560); // HELLPUPPY
		putId(3099, 6561, 6560, 6560); // HELLPUPPY
		putId(7891, 7768, 7768, 7768); // NOON
		putId(7892, 7768, 7768, 7768); // NOON
		putId(7890, 7807, 7806, 7806); // MIDNIGHT
		putId(7893, 7807, 7806, 7806); // MIDNIGHT
		putId(8492, 8233, 8296, 8296); // IKKLE_HYDRA
		putId(8518, 8233, 8296, 8296); // IKKLE_HYDRA
		putId(8517, 8233, 8296, 8296); // IKKLE_HYDRA
		putId(8495, 8233, 8296, 8296); // IKKLE_HYDRA
		putId(8519, 8233, 8296, 8296); // IKKLE_HYDRA
		putId(8493, 8233, 8296, 8296); // IKKLE_HYDRA
		putId(8494, 8233, 8296, 8296); // IKKLE_HYDRA
		putId(8520, 8233, 8296, 8296); // IKKLE_HYDRA
		putId(8493, 8298, 8297, 8297); // IKKLE_HYDRA_8493
		putId(8494, 8247, 8299, 8299); // IKKLE_HYDRA_8494
		putId(8495, 8254, 8300, 8300); // IKKLE_HYDRA_8495
		putId(6639, 1829, 1828, 1828); // SMOKE_DEVIL_6639
		putId(498, 1829, 1828, 1828); // SMOKE_DEVIL
		putId(6639, 1829, 1828, 1828); // SMOKE_DEVIL
		putId(8482, 1829, 1828, 1828); // SMOKE_DEVIL
		putId(6655, 1829, 1828, 1828); // SMOKE_DEVIL
		putId(8483, 1829, 1828, 1828); // SMOKE_DEVIL
		putId(494, 3989, 3989, 3989); // KRAKEN
		putId(6656, 3989, 3989, 3989); // KRAKEN
		putId(6640, 3989, 3989, 3989); // KRAKEN
		putId(13682, 8340, 9139, 9139); // RAX
		putId(13684, 8340, 9139, 9139); // RAX
		putId(13681, 11473, 11474, 11474); // NID
		putId(13683, 11473, 11474, 11474); // NID
		putId(6632, 7017, 7016, 7016); // GENERAL_GRAARDOR_JR
		putId(6644, 7017, 7016, 7016); // GENERAL_GRAARDOR_JR
		putId(6631, 7166, 7167, 7167); // KREEARRA_JR
		putId(6643, 7166, 7167, 7167); // KREEARRA_JR
		putId(6633, 6966, 6965, 6965); // ZILYANA_JR
		putId(6646, 6966, 6965, 6965); // ZILYANA_JR
		putId(6634, 6935, 4070, 4070); // KRIL_TSUTSAROTH_JR
		putId(6647, 6935, 4070, 4070); // KRIL_TSUTSAROTH_JR
		putId(11276, 9177, 9176, 9176); // NEXLING
		putId(11277, 9177, 9176, 9176); // NEXLING
		putId(12154, 10337, 10339, 10339); // BUTCH
		putId(12158, 10337, 10339, 10339); // BUTCH
		putId(12155, 10217, 10218, 10218); // BARON
		putId(12159, 10217, 10218, 10218); // BARON
		putId(12156, 10277, 10292, 10292); // LILVIATHAN
		putId(12160, 10277, 10292, 10292); // LILVIATHAN
		putId(12153, 10230, 10233, 10233); // WISP
		putId(12157, 10230, 10233, 10233); // WISP
		putId(7219, 10687, 10715, 10715); // SCURRY
		putId(7616, 10687, 10715, 10715); // SCURRY
		putId(14034, 11528, 11529, 11529); // MOXI
		putId(14046, 11528, 11529, 11529); // MOXI
		putId(14033, 11732, 11733, 11733); // HUBERTE
		putId(14045, 11732, 11733, 11733); // HUBERTE
		putId(7674, 7573, 7572, 7572); // JALNIBREK
		putId(7675, 7573, 7572, 7572); // JALNIBREK
		putId(8009, 7975, 7977, 7977); // TZREKZUK
		putId(8011, 7975, 7977, 7977); // TZREKZUK
		putId(5892, 2650, 5805, 5805); // TZREKJAD
		putId(5893, 2650, 5805, 5805); // TZREKJAD
		putId(10620, 7589, 8857, 8857); // JALREKJAD
		putId(10625, 7589, 8857, 8857); // JALREKJAD
		putId(12767, 10874, 10880, 10880); // SMOL_HEREDIT
		putId(12857, 10874, 10880, 10880); // SMOL_HEREDIT
		putId(8729, 8417, 8428, 8428); // YOUNGLLEF
		putId(8737, 8417, 8428, 8428); // YOUNGLLEF
		putId(8730, 8417, 8428, 8428); // CORRUPTED_YOUNGLLEF
		putId(8738, 8417, 8428, 8428); // CORRUPTED_YOUNGLLEF
		putId(318, 7980, 2417, 2417); // DARK_CORE
		putId(388, 7980, 2417, 2417); // DARK_CORE
		putId(8008, 1678, 7974, 7974); // CORPOREAL_CRITTER
		putId(8010, 1678, 7974, 7974); // CORPOREAL_CRITTER
		putId(2129, 1721, 2405, 2405); // SNAKELING_2129
		putId(2128, 1721, 2405, 2405); // SNAKELING_2128
		putId(2045, 1721, 2405, 2405); // SNAKELING
		putId(2047, 1721, 2405, 2405); // SNAKELING
		putId(2129, 1721, 2405, 2405); // SNAKELING
		putId(15163, 1721, 2405, 2405); // SNAKELING
		putId(2128, 1721, 2405, 2405); // SNAKELING
		putId(2130, 1721, 2405, 2405); // SNAKELING
		putId(2131, 1721, 2405, 2405); // SNAKELING
		putId(2046, 1721, 2405, 2405); // SNAKELING
		putId(2127, 1721, 2405, 2405); // SNAKELING
		putId(2132, 1721, 2405, 2405); // SNAKELING
		putId(9398, 8593, 8634, 8634); // LITTLE_NIGHTMARE
		putId(9399, 8593, 8634, 8634); // LITTLE_NIGHTMARE
		putId(8183, 8553, 8553, 8553); // LITTLE_PARASITE
		putId(8541, 8553, 8553, 8553); // LITTLE_PARASITE
		putId(6635, 3309, 3313, 3313);
		putId(10650, 3309, 3313, 3313); // BABY_MOLERAT
		putId(10651, 3309, 3313, 3313); // BABY_MOLERAT
		putId(6637, 6239, 6238, 6238); // KALPHITE_PRINCESS (4635 is Prince Black Dragon's anim — "cursed" morphing)
		putId(6653, 6239, 6238, 6238); // KALPHITE_PRINCESS
		putId(6654, 6239, 6238, 6238); // KALPHITE_PRINCESS
		putId(6638, 6239, 6238, 6238); // KALPHITE_PRINCESS
		putId(6653, 6236, 6236, 6236); // KALPHITE_PRINCESS_6653
		putId(12005, 9913, 9915, 9915); // MUPHIN
		putId(12006, 9913, 9915, 9915); // MUPHIN
		putId(12016, 9913, 9915, 9915); // MUPHIN
		putId(12014, 9913, 9915, 9915); // MUPHIN
		putId(12007, 9913, 9915, 9915); // MUPHIN
		putId(12015, 9913, 9915, 9915); // MUPHIN
		putId(12006, 9913, 9915, 9915); // MUPHIN_12006
		putId(12007, 9913, 9915, 9915); // MUPHIN_12007
		putId(2143, 8320, 8319, 8319); // SRARACHA
		putId(11158, 8320, 8319, 8319); // SRARACHA
		putId(11157, 8320, 8319, 8319); // SRARACHA
		putId(11159, 8320, 8319, 8319); // SRARACHA
		putId(2144, 8320, 8319, 8319); // SRARACHA
		putId(11160, 8320, 8319, 8319); // SRARACHA
		putId(11157, 8320, 8319, 8319); // SRARACHA_11157
		putId(11158, 8320, 8319, 8319); // SRARACHA_11158
		putId(425, 6935, 4070, 4070); // SKOTOS
		putId(7671, 6935, 4070, 4070); // SKOTOS
		putId(8025, 7948, 7959, 7959); // VORKI
		putId(8029, 7948, 7959, 7959); // VORKI
		putId(6626, 2850, 2849, 2849); // DAGANNOTH_SUPREME_JR
		putId(6628, 2850, 2849, 2849); // DAGANNOTH_SUPREME_JR
		putId(6627, 2850, 2849, 2849); // DAGANNOTH_PRIME_JR
		putId(6629, 2850, 2849, 2849); // DAGANNOTH_PRIME_JR
		putId(6630, 2850, 2849, 2849); // DAGANNOTH_REX_JR
		putId(6641, 2850, 2849, 2849); // DAGANNOTH_REX_JR
		putId(6636, 90, 4635, 4635); // PRINCE_BLACK_DRAGON
		putId(6652, 90, 4635, 4635); // PRINCE_BLACK_DRAGON
		putId(2055, 3144, 3145, 3145); // CHAOS_ELEMENTAL_JR
		putId(5907, 3144, 3145, 3145); // CHAOS_ELEMENTAL_JR
		putId(495, 9986, 9987, 9987); // VENENATIS_SPIDERLING
		putId(12000, 9986, 9987, 9987); // VENENATIS_SPIDERLING
		putId(11985, 9986, 9987, 9987); // VENENATIS_SPIDERLING
		putId(5557, 9986, 9987, 9987); // VENENATIS_SPIDERLING
		putId(11981, 9986, 9987, 9987); // VENENATIS_SPIDERLING
		putId(11981, 5326, 5325, 5325); // VENENATIS_SPIDERLING_11981
		putId(497, 10011, 10010, 10010); // CALLISTO_CUB
		putId(11982, 10011, 10010, 10010); // CALLISTO_CUB
		putId(11986, 10011, 10010, 10010); // CALLISTO_CUB
		putId(5558, 10011, 10010, 10010); // CALLISTO_CUB
		putId(11982, 4919, 4923, 4923); // CALLISTO_CUB_11982
		putId(5536, 9965, 9967, 9967); // VETION_JR
		putId(5559, 9965, 9967, 9967); // VETION_JR
		putId(5560, 9965, 9967, 9967); // VETION_JR
		putId(5537, 9965, 9967, 9967); // VETION_JR
		putId(11987, 9965, 9967, 9967); // VETION_JR
		putId(11984, 9965, 9967, 9967); // VETION_JR
		putId(11988, 9965, 9967, 9967); // VETION_JR
		putId(11983, 9965, 9967, 9967); // VETION_JR
		putId(5537, 9965, 9967, 9967); // VETION_JR_5537
		putId(11983, 5505, 5497, 5497); // VETION_JR_11983
		putId(11984, 5505, 5497, 5497); // VETION_JR_11984
		putId(5547, 6258, 6257, 6257); // SCORPIAS_OFFSPRING
		putId(5561, 6258, 6257, 6257); // SCORPIAS_OFFSPRING
		putId(6616, 6258, 6257, 6257); // SCORPIAS_OFFSPRING
		putId(10476, 11970, 11972, 11972); // BRAN
		putId(12593, 11970, 11972, 11972); // BRAN
		putId(12592, 11969, 11971, 11971); // RIC (11972 is BRAN's run — wrong skeleton exploded the model)
		putId(12595, 11969, 11971, 11971); // RIC
		putId(14203, 12140, 12143, 12143); // YAMI
		putId(14204, 12140, 12143, 12143); // YAMI
		putId(12768, 10952, 10952, 10952); // QUETZIN
		putId(12858, 10952, 10952, 10952); // QUETZIN
		putId(7335, 7312, 7313, 7313); // TANGLEROOT
		putId(9496, 7312, 7313, 7313); // TANGLEROOT
		putId(9499, 7312, 7313, 7313); // TANGLEROOT
		putId(9501, 7312, 7313, 7313); // TANGLEROOT
		putId(9492, 7312, 7313, 7313); // TANGLEROOT
		putId(9495, 7312, 7313, 7313); // TANGLEROOT
		putId(7352, 7312, 7313, 7313); // TANGLEROOT
		putId(9497, 7312, 7313, 7313); // TANGLEROOT
		putId(9494, 7312, 7313, 7313); // TANGLEROOT
		putId(9500, 7312, 7313, 7313); // TANGLEROOT
		putId(9498, 7312, 7313, 7313); // TANGLEROOT
		putId(9493, 7312, 7313, 7313); // TANGLEROOT
		putId(9492, 7312, 7313, 7313); // TANGLEROOT_9492
		putId(9493, 7312, 7313, 7313); // TANGLEROOT_9493
		putId(9494, 7312, 7313, 7313); // TANGLEROOT_9494
		putId(9495, 7312, 7313, 7313); // TANGLEROOT_9495
		putId(9496, 7312, 7313, 7313); // TANGLEROOT_9496
		putId(7334, 7309, 7310, 7310); // GIANT_SQUIRREL
		putId(7351, 7309, 7310, 7310); // GIANT_SQUIRREL
		putId(9666, 7309, 7310, 7310); // GIANT_SQUIRREL
		putId(9637, 7309, 7310, 7310); // DARK_SQUIRREL
		putId(9638, 7309, 7310, 7310); // DARK_SQUIRREL
		putId(7337, 7307, 7306, 7306); // RIFT_GUARDIAN
		putId(7356, 7307, 7306, 7306); // RIFT_GUARDIAN
		putId(7345, 7307, 7306, 7306); // RIFT_GUARDIAN
		putId(7363, 7307, 7306, 7306); // RIFT_GUARDIAN
		putId(7347, 7307, 7306, 7306); // RIFT_GUARDIAN
		putId(7359, 7307, 7306, 7306); // RIFT_GUARDIAN
		putId(7350, 7307, 7306, 7306); // RIFT_GUARDIAN
		putId(7361, 7307, 7306, 7306); // RIFT_GUARDIAN
		putId(7339, 7307, 7306, 7306); // RIFT_GUARDIAN
		putId(7348, 7307, 7306, 7306); // RIFT_GUARDIAN
		putId(8024, 7307, 7306, 7306); // RIFT_GUARDIAN
		putId(7341, 7307, 7306, 7306); // RIFT_GUARDIAN
		putId(7355, 7307, 7306, 7306); // RIFT_GUARDIAN
		putId(7349, 7307, 7306, 7306); // RIFT_GUARDIAN
		putId(7364, 7307, 7306, 7306); // RIFT_GUARDIAN
		putId(7343, 7307, 7306, 7306); // RIFT_GUARDIAN
		putId(7344, 7307, 7306, 7306); // RIFT_GUARDIAN
		putId(7357, 7307, 7306, 7306); // RIFT_GUARDIAN
		putId(7366, 7307, 7306, 7306); // RIFT_GUARDIAN
		putId(7362, 7307, 7306, 7306); // RIFT_GUARDIAN
		putId(7346, 7307, 7306, 7306); // RIFT_GUARDIAN
		putId(7358, 7307, 7306, 7306); // RIFT_GUARDIAN
		putId(8028, 7307, 7306, 7306); // RIFT_GUARDIAN
		putId(7360, 7307, 7306, 7306); // RIFT_GUARDIAN
		putId(7338, 7307, 7306, 7306); // RIFT_GUARDIAN
		putId(7367, 7307, 7306, 7306); // RIFT_GUARDIAN
		putId(7340, 7307, 7306, 7306); // RIFT_GUARDIAN
		putId(7342, 7307, 7306, 7306); // RIFT_GUARDIAN
		putId(7354, 7307, 7306, 7306); // RIFT_GUARDIAN
		putId(7365, 7307, 7306, 7306); // RIFT_GUARDIAN
		putId(7338, 7307, 7306, 7306); // RIFT_GUARDIAN_7338
		putId(7339, 7307, 7306, 7306); // RIFT_GUARDIAN_7339
		putId(7340, 7307, 7306, 7306); // RIFT_GUARDIAN_7340
		putId(7341, 7307, 7306, 7306); // RIFT_GUARDIAN_7341
		putId(7342, 7307, 7306, 7306); // RIFT_GUARDIAN_7342
		putId(7343, 7307, 7306, 7306); // RIFT_GUARDIAN_7343
		putId(7344, 7307, 7306, 7306); // RIFT_GUARDIAN_7344
		putId(7345, 7307, 7306, 7306); // RIFT_GUARDIAN_7345
		putId(7346, 7307, 7306, 7306); // RIFT_GUARDIAN_7346
		putId(7347, 7307, 7306, 7306); // RIFT_GUARDIAN_7347
		putId(7348, 7307, 7306, 7306); // RIFT_GUARDIAN_7348
		putId(7349, 7307, 7306, 7306); // RIFT_GUARDIAN_7349
		putId(7350, 7307, 7306, 7306); // RIFT_GUARDIAN_7350
		putId(8024, 7307, 7306, 7306); // RIFT_GUARDIAN_8024
		putId(11401, 9379, 9378, 9378); // GREATISH_GUARDIAN
		putId(11428, 9379, 9378, 9378); // GREATISH_GUARDIAN
		putId(2182, 7180, 7181, 7181); // ROCK_GOLEM
		putId(7445, 7180, 7181, 7181); // ROCK_GOLEM
		putId(7646, 7180, 7181, 7181); // ROCK_GOLEM
		putId(14924, 7180, 7181, 7181); // ROCK_GOLEM
		putId(7439, 7180, 7181, 7181); // ROCK_GOLEM
		putId(7648, 7180, 7181, 7181); // ROCK_GOLEM
		putId(7739, 7180, 7181, 7181); // ROCK_GOLEM
		putId(7454, 7180, 7181, 7181); // ROCK_GOLEM
		putId(7449, 7180, 7181, 7181); // ROCK_GOLEM
		putId(7741, 7180, 7181, 7181); // ROCK_GOLEM
		putId(7737, 7180, 7181, 7181); // ROCK_GOLEM
		putId(7442, 7180, 7181, 7181); // ROCK_GOLEM
		putId(15052, 7180, 7181, 7181); // ROCK_GOLEM
		putId(7440, 7180, 7181, 7181); // ROCK_GOLEM
		putId(7643, 7180, 7181, 7181); // ROCK_GOLEM
		putId(6729, 7180, 7181, 7181); // ROCK_GOLEM
		putId(6726, 7180, 7181, 7181); // ROCK_GOLEM
		putId(6730, 7180, 7181, 7181); // ROCK_GOLEM
		putId(7645, 7180, 7181, 7181); // ROCK_GOLEM
		putId(7453, 7180, 7181, 7181); // ROCK_GOLEM
		putId(6727, 7180, 7181, 7181); // ROCK_GOLEM
		putId(7446, 7180, 7181, 7181); // ROCK_GOLEM
		putId(7647, 7180, 7181, 7181); // ROCK_GOLEM
		putId(7451, 7180, 7181, 7181); // ROCK_GOLEM
		putId(6725, 7180, 7181, 7181); // ROCK_GOLEM
		putId(7444, 7180, 7181, 7181); // ROCK_GOLEM
		putId(14925, 7180, 7181, 7181); // ROCK_GOLEM
		putId(7711, 7180, 7181, 7181); // ROCK_GOLEM
		putId(7455, 7180, 7181, 7181); // ROCK_GOLEM
		putId(7443, 7180, 7181, 7181); // ROCK_GOLEM
		putId(15051, 7180, 7181, 7181); // ROCK_GOLEM
		putId(7738, 7180, 7181, 7181); // ROCK_GOLEM
		putId(14923, 7180, 7181, 7181); // ROCK_GOLEM
		putId(7441, 7180, 7181, 7181); // ROCK_GOLEM
		putId(7448, 7180, 7181, 7181); // ROCK_GOLEM
		putId(15053, 7180, 7181, 7181); // ROCK_GOLEM
		putId(7452, 7180, 7181, 7181); // ROCK_GOLEM
		putId(6728, 7180, 7181, 7181); // ROCK_GOLEM
		putId(7736, 7180, 7181, 7181); // ROCK_GOLEM
		putId(7447, 7180, 7181, 7181); // ROCK_GOLEM
		putId(7644, 7180, 7181, 7181); // ROCK_GOLEM
		putId(7642, 7180, 7181, 7181); // ROCK_GOLEM
		putId(7450, 7180, 7181, 7181); // ROCK_GOLEM
		putId(7740, 7180, 7181, 7181); // ROCK_GOLEM
		putId(7439, 7180, 7181, 7181); // ROCK_GOLEM_7439
		putId(7440, 7180, 7181, 7181); // ROCK_GOLEM_7440
		putId(7441, 7180, 7181, 7181); // ROCK_GOLEM_7441
		putId(7442, 7180, 7181, 7181); // ROCK_GOLEM_7442
		putId(7443, 7180, 7181, 7181); // ROCK_GOLEM_7443
		putId(7444, 7180, 7181, 7181); // ROCK_GOLEM_7444
		putId(7445, 7180, 7181, 7181); // ROCK_GOLEM_7445
		putId(7446, 7180, 7181, 7181); // ROCK_GOLEM_7446
		putId(7447, 7180, 7181, 7181); // ROCK_GOLEM_7447
		putId(7448, 7180, 7181, 7181); // ROCK_GOLEM_7448
		putId(7449, 7180, 7181, 7181); // ROCK_GOLEM_7449
		putId(7450, 7180, 7181, 7181); // ROCK_GOLEM_7450
		putId(7451, 7180, 7181, 7181); // ROCK_GOLEM_7451
		putId(7452, 7180, 7181, 7181); // ROCK_GOLEM_7452
		putId(7453, 7180, 7181, 7181); // ROCK_GOLEM_7453
		putId(6715, 6772, 6774, 6774); // HERON
		putId(6722, 6772, 6774, 6774); // HERON
		putId(6817, 6772, 6774, 6774); // GREAT_BLUE_HERON
		putId(10636, 6772, 6774, 6774); // GREAT_BLUE_HERON
		putId(12169, 7177, 7178, 7178); // BEAVER
		putId(12181, 7177, 7178, 7178); // BEAVER
		putId(12175, 7177, 7178, 7178); // BEAVER
		putId(12184, 7177, 7178, 7178); // BEAVER
		putId(14929, 7177, 7178, 7178); // BEAVER
		putId(12190, 7177, 7178, 7178); // BEAVER
		putId(12172, 7177, 7178, 7178); // BEAVER
		putId(15057, 7177, 7178, 7178); // BEAVER
		putId(15055, 7177, 7178, 7178); // BEAVER
		putId(12186, 7177, 7178, 7178); // BEAVER
		putId(12170, 7177, 7178, 7178); // BEAVER
		putId(14928, 7177, 7178, 7178); // BEAVER
		putId(12189, 7177, 7178, 7178); // BEAVER
		putId(14926, 7177, 7178, 7178); // BEAVER
		putId(15056, 7177, 7178, 7178); // BEAVER
		putId(12173, 7177, 7178, 7178); // BEAVER
		putId(15054, 7177, 7178, 7178); // BEAVER
		putId(12185, 7177, 7178, 7178); // BEAVER
		putId(12176, 7177, 7178, 7178); // BEAVER
		putId(12171, 7177, 7178, 7178); // BEAVER
		putId(12187, 7177, 7178, 7178); // BEAVER
		putId(12188, 7177, 7178, 7178); // BEAVER
		putId(12182, 7177, 7178, 7178); // BEAVER
		putId(12178, 7177, 7178, 7178); // BEAVER
		putId(12183, 7177, 7178, 7178); // BEAVER
		putId(14927, 7177, 7178, 7178); // BEAVER
		putId(12174, 7177, 7178, 7178); // BEAVER
		putId(12177, 7177, 7178, 7178); // BEAVER
		putId(6756, 5182, 5181, 5181); // BABY_CHINCHOMPA_6756
		putId(6718, 5182, 5181, 5181); // BABY_CHINCHOMPA
		putId(6756, 5182, 5181, 5181); // BABY_CHINCHOMPA
		putId(6721, 5182, 5181, 5181); // BABY_CHINCHOMPA
		putId(6720, 5182, 5181, 5181); // BABY_CHINCHOMPA
		putId(6757, 5182, 5181, 5181); // BABY_CHINCHOMPA
		putId(6719, 5182, 5181, 5181); // BABY_CHINCHOMPA
		putId(6758, 5182, 5181, 5181); // BABY_CHINCHOMPA
		putId(6759, 5182, 5181, 5181); // BABY_CHINCHOMPA
		putId(6758, 5182, 5181, 5181); // BABY_CHINCHOMPA_6758
		putId(6759, 5182, 5181, 5181); // BABY_CHINCHOMPA_6759
		putId(7336, 7315, 7316, 7316); // ROCKY
		putId(7353, 7315, 7316, 7316); // ROCKY
		putId(9850, 7315, 7316, 7316); // RED
		putId(9852, 7315, 7316, 7316); // RED
		putId(9851, 7315, 7316, 7316); // ZIGGY
		putId(9853, 7315, 7316, 7316); // ZIGGY
		putId(14539, 7315, 7316, 7316); // ZIGGY
		putId(7370, 6809, 6808, 6808); // PHOENIX_7370
		putId(3081, 6809, 6808, 6808); // PHOENIX_3081
		putId(3078, 6809, 6808, 6808); // PHOENIX_3078
		putId(3079, 6809, 6808, 6808); // PHOENIX_3079
		putId(3080, 6809, 6808, 6808); // PHOENIX_3080
		putId(7759, 7694, 7695, 7695); // HERBI
		putId(7760, 7694, 7695, 7695); // HERBI
		putId(8731, 8429, 8447, 8447); // SMOLCANO
		putId(8739, 8429, 8447, 8447); // SMOLCANO
		putId(10562, 8895, 8895, 8895); // TINY_TEMPOR
		putId(10637, 8895, 8895, 8895); // TINY_TEMPOR
		putId(11402, 2185, 2184, 2184); // ABYSSAL_PROTECTOR
		putId(11429, 2185, 2184, 2184); // ABYSSAL_PROTECTOR
		putId(6642, 5410, 5409, 5409); // PENANCE_PET
		putId(6674, 5410, 5409, 5409); // PENANCE_PET
		putId(6296, 7269, 7280, 7280); // BLOODHOUND
		putId(7232, 7269, 7280, 7280); // BLOODHOUND
		putId(4001, 6764, 6765, 6765); // CHOMPY_CHICK
		putId(4002, 6764, 6765, 6765); // CHOMPY_CHICK
		putId(2833, 8842, 8846, 8846); // LIL_CREATOR
		putId(3566, 8842, 8846, 8846); // LIL_CREATOR
		putId(3564, 3079, 8847, 8847); // LIL_DESTRUCTOR
		putId(5008, 3079, 8847, 8847); // LIL_DESTRUCTOR
		putId(14167, 7269, 6577, 6577); // ARCHIBALD
		putId(15596, 7269, 6577, 6577); // ARCHIBALD
		putId(15590, 7269, 6577, 6577); // ARCHIBALD
		putId(15591, 7269, 6577, 6577); // ARCHIBALD
		putId(15587, 7269, 6577, 6577); // ARCHIBALD
		putId(15599, 7269, 6577, 6577); // ARCHIBALD
		putId(15588, 7269, 6577, 6577); // ARCHIBALD
		putId(15592, 7269, 6577, 6577); // ARCHIBALD
		putId(15595, 7269, 6577, 6577); // ARCHIBALD
		putId(15597, 7269, 6577, 6577); // ARCHIBALD
		putId(15600, 7269, 6577, 6577); // ARCHIBALD
		putId(15586, 7269, 6577, 6577); // ARCHIBALD
		putId(15598, 7269, 6577, 6577); // ARCHIBALD
		putId(15593, 7269, 6577, 6577); // ARCHIBALD
		putId(15594, 7269, 6577, 6577); // ARCHIBALD

		putName("olmlet", 7396, 7395, 7395);
		putName("puppadile", 7417, 7982, 7982);
		putName("tektiny", 7476, 7983, 7983);
		putName("enraged tektiny", 7485, 8637, 8637);
		putName("vanguard", 7430, 7984, 7984);
		putName("vasa minirio", 7416, 7985, 7985);
		putName("vespina", 7449, 7986, 7986);
		putName("flying vespina", 8639, 8639, 8639);
		putName("lil' zik", 8120, 8122, 8122);
		putName("lil' maiden", 8090, 8090, 8090);
		putName("lil' bloat", 8080, 9031, 9031);
		putName("lil' nylo", 8002, 8003, 8003);
		putName("lil' sot", 8137, 9032, 9032);
		putName("lil' xarp", 9033, 9033, 9033);
		putName("tumeken's guardian", 9655, 9651, 9651);
		putName("tumeken's damaged guardian", 9420, 9420, 9420);
		putName("elidinis' guardian", 9656, 9652, 9652);
		putName("elidinis' damaged guardian", 9420, 9420, 9420);
		putName("akkhito", 9760, 9421, 9421);
		putName("babi", 9741, 9739, 9739);
		putName("kephriti", 9572, 9419, 9419);
		putName("zebo", 2037, 2036, 2036);
		putName("abyssal orphan", 7125, 7124, 7124);
		putName("hellpuppy", 6561, 6560, 6560);
		putName("noon", 7768, 7768, 7768);
		putName("midnight", 7807, 7806, 7806);
		putName("ikkle hydra", 8233, 8296, 8296);
		putName("ikkle hydra", 8298, 8297, 8297);
		putName("ikkle hydra", 8247, 8299, 8299);
		putName("ikkle hydra", 8254, 8300, 8300);
		putName("pet smoke devil", 1829, 1828, 1828);
		putName("kraken", 3989, 3989, 3989);
		putName("rax", 8340, 9139, 9139);
		putName("nid", 11473, 11474, 11474);
		putName("general graardor jr.", 7017, 7016, 7016);
		putName("kree'arra jr.", 7166, 7167, 7167);
		putName("zilyana jr.", 6966, 6965, 6965);
		putName("k'ril tsutsaroth jr.", 6935, 4070, 4070);
		putName("nexling", 9177, 9176, 9176);
		putName("butch", 10337, 10339, 10339);
		putName("baron", 10217, 10218, 10218);
		putName("lil'viathan", 10277, 10292, 10292);
		putName("wisp", 10230, 10233, 10233);
		putName("scurry", 10687, 10715, 10715);
		putName("moxi", 11528, 11529, 11529);
		putName("huberte", 11732, 11733, 11733);
		putName("jal-nib-rek", 7573, 7572, 7572);
		putName("tzrek-zuk", 7975, 7977, 7977);
		putName("tzrek-jad", 2650, 5805, 5805);
		putName("jalrek-jad", 7589, 8857, 8857);
		putName("smol heredit", 10874, 10880, 10880);
		putName("youngllef", 8417, 8428, 8428);
		putName("corrupted youngllef", 8417, 8428, 8428);
		putName("dark core", 7980, 2417, 2417);
		putName("corporeal critter", 1678, 7974, 7974);
		putName("snakeling", 1721, 2405, 2405);
		putName("little nightmare", 8593, 8634, 8634);
		putName("little parasite", 8553, 8553, 8553);
		putName("baby mole", 3309, 3313, 3313);
		putName("baby mole-rat", 3309, 3313, 3313);
		putName("kalphite princess", 6239, 6238, 6238);
		putName("kalphite princess", 6236, 6236, 6236);
		putName("muphin", 9913, 9915, 9915);
		putName("sraracha", 8320, 8319, 8319);
		putName("skotos", 6935, 4070, 4070);
		putName("vorki", 7948, 7959, 7959);
		putName("dagannoth supreme jr.", 2850, 2849, 2849);
		putName("dagannoth prime jr.", 2850, 2849, 2849);
		putName("dagannoth rex jr.", 2850, 2849, 2849);
		putName("prince black dragon", 90, 4635, 4635);
		putName("chaos elemental jr.", 3144, 3145, 3145);
		putName("venenatis spiderling", 9986, 9987, 9987);
		putName("venenatis spiderling", 5326, 5325, 5325);
		putName("callisto cub", 10011, 10010, 10010);
		putName("callisto cub", 4919, 4923, 4923);
		putName("vet'ion jr.", 9965, 9967, 9967);
		putName("vet'ion jr.", 5505, 5497, 5497);
		putName("scorpia's offspring", 6258, 6257, 6257);
		putName("bran", 11970, 11972, 11972);
		putName("ric", 11969, 11971, 11971);
		putName("yami", 12140, 12143, 12143);
		putName("quetzin", 10952, 10952, 10952);
		putName("tangleroot", 7312, 7313, 7313);
		putName("giant squirrel", 7309, 7310, 7310);
		putName("dark squirrel", 7309, 7310, 7310);
		putName("rift guardian", 7307, 7306, 7306);
		putName("greatish guardian", 9379, 9378, 9378);
		putName("rock golem", 7180, 7181, 7181);
		putName("heron", 6772, 6774, 6774);
		putName("great blue heron", 6772, 6774, 6774);
		putName("beaver", 7177, 7178, 7178);
		putName("baby chinchompa", 5182, 5181, 5181);
		putName("rocky", 7315, 7316, 7316);
		putName("red", 7315, 7316, 7316);
		putName("ziggy", 7315, 7316, 7316);
		putName("phoenix", 6809, 6808, 6808);
		putName("herbi", 7694, 7695, 7695);
		putName("smolcano", 8429, 8447, 8447);
		putName("tiny tempor", 8895, 8895, 8895);
		putName("abyssal protector", 2185, 2184, 2184);
		putName("penance pet", 5410, 5409, 5409);
		putName("bloodhound", 7269, 7280, 7280);
		putName("chompy chick", 6764, 6765, 6765);
		putName("lil' creator", 8842, 8846, 8846);
		putName("lil' destructor", 3079, 8847, 8847);
		putName("mochi", 7269, 6577, 6577);
	}
}