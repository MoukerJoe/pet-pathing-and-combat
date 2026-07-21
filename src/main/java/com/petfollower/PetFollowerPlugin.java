package com.petfollower;

import com.google.inject.Provides;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Actor;
import net.runelite.api.ActorSpotAnim;
import net.runelite.api.Animation;
import net.runelite.api.AnimationController;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.CollisionData;
import net.runelite.api.CollisionDataFlag;
import net.runelite.api.GameState;
import net.runelite.api.ItemComposition;
import net.runelite.api.JagexColor;
import net.runelite.api.Model;
import net.runelite.api.ModelData;
import net.runelite.api.NPC;
import net.runelite.api.NPCComposition;
import net.runelite.api.Perspective;
import net.runelite.api.Player;
import net.runelite.api.Projectile;
import net.runelite.api.Renderable;
import net.runelite.api.RuneLiteObject;
import net.runelite.api.RuneLiteObjectController;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.AnimationChanged;
import net.runelite.api.events.BeforeRender;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.HitsplatApplied;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.NpcDespawned;
import net.runelite.api.events.NpcSpawned;
import net.runelite.api.events.ProjectileMoved;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.callback.Hooks;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.ui.overlay.OverlayPosition;

/**
 * Visual-only pet follower.
 *
 * The real pet NPC is server-authoritative and cannot be moved client-side.
 * Instead, while you're moving, this plugin:
 *   1. Suppresses rendering of your real pet (RenderableDrawListener veto —
 *      same mechanism the core Entity Hider uses).
 *   2. Runs a client-side ghost that chases you along your RENDERED path
 *      (not your server-side true tile, which leads the drawn character).
 *      It keeps a fixed arc-length gap behind you, reacts with a short
 *      natural delay, accelerates/decelerates smoothly between idle, walk,
 *      and run speeds, and turns where you turned because it travels the
 *      same polyline you did.
 *   3. Renders the ghost by mirroring the hidden pet's live model
 *      (NPC.getModel() is already posed, scaled, and recolored by the
 *      engine) while forcing the hidden pet's pose animation to match the
 *      ghost's own speed (idle/walk/run), so animation always matches
 *      motion. Pose frames are preserved across re-pins so cycles never
 *      restart mid-stride.
 *
 * When you stand still and the real pet has caught up server-side, the
 * ghost despawns and the real pet is un-hidden so you can right-click it
 * (Pick up / Metamorphosis). Nothing is sent to the server; no input is
 * generated; other players see nothing different.
 */
@Slf4j
@PluginDescriptor(
	name = "Pet Pathing and Combat",
	description = "Cosmetic: your pet keeps pace right behind you, can stand in for Arceuus thralls, and can transmog into any pet",
	tags = {"pet", "follower", "pathing", "combat", "thrall", "cosmetic", "transmog"}
)
public class PetFollowerPlugin extends Plugin
{
	// Arceuus thrall NPC ids: ghostly (magic), skeletal (ranged),
	// zombified (melee) — lesser/greater/superior of each.
	private static final Set<Integer> THRALL_IDS = new HashSet<>(Arrays.asList(
		10878, 10879, 10880,
		10881, 10882, 10883,
		10884, 10885, 10886));

	// Varp holding your current follower's NPC index. Some clients have seen
	// this packed in the high 16 bits, so matchesFollowerVarp() checks both.
	private static final int FOLLOWER_VARP = 447;

	// Distances in local units (128 = 1 tile), speeds in units/ms. The
	// simulation runs once per RENDERED frame (BeforeRender): the engine
	// interpolates actors every frame at unlocked fps, so a ghost stepping
	// at the 50 Hz client tick visibly stutters ("blur") next to them on a
	// high-refresh display.
	private static final float GAP = 128f;          // base resting distance behind you (1 tile)
	private static final float IDLE_PUSHBACK = 24f; // small extra rest gap so pets don't nose into you
	private static final float START_DIST = 40f;    // reaction: don't move until you pull this far past gap
	private static final float STOP_DIST = 12f;     // settle: stop once within this of gap
	private static final float WALK_SPEED = 128f / 600f;
	private static final float RUN_SPEED = 256f / 600f;
	private static final float CATCHUP_SPEED = RUN_SPEED * 1.25f;

	// Pose thresholds on the ghost's TARGET speed, with idle/walk/run states.
	private static final int POSE_IDLE = 0;
	private static final int POSE_WALK = 1;
	private static final int POSE_RUN = 2;

	// Turning, in JAU (2048 = full circle) per ms. Proportional steering:
	// angular speed scales with the remaining angle (closing it over
	// ~TURN_TAU ms), so it slows as it lines up instead of overshooting and
	// hunting back and forth ("wavy"). TURN_ACCEL ramps changes in angular
	// speed so turns ease in and out. Max speed comes from the config
	// slider (degrees/sec -> JAU/ms).
	private static final float DEG_PER_SEC_TO_JAU_PER_MS = 2048f / 360f / 1000f;
	private static final float TURN_ACCEL = 0.006f;
	private static final float TURN_TAU = 260f;
	private static final int TURN_DEADBAND = 20;

	// Pure-pursuit steering: the ghost aims at a point this far ahead along
	// the recorded path instead of the segment under its feet, so path kinks
	// average out and corners become one smooth arc instead of
	// left-right-left corrections.
	private static final float LOOKAHEAD = 80f;

	// Route straightening ("string pulling"): each frame the ghost bridges
	// straight to a point this far ahead on its route whenever the route is
	// more than BRIDGE_SLACK longer than the straight chord AND the chord
	// is walkable. Real animals cut inside corners rather than tracing your
	// footsteps through the elbow; this collapses 90-degree elbows into the
	// inside diagonal and merges zigzag micro-folds into one line (which
	// also stops the left-right facing whip). Collision checking keeps
	// corners around walls/fences honest — those still trace the path.
	private static final float BRIDGE_LOOK = 192f;
	private static final float BRIDGE_SLACK = 20f;

	// The recorded follow-path is low-pass filtered toward your real position
	// with this time constant (ms) before the ghost follows it. Diagonal
	// running in OSRS is a rapid staircase (one tile forward, one across,
	// repeating); mirrored exactly at a one-tile gap it made the ghost jerk
	// left/right every step. Filtering averages that zigzag into the straight
	// diagonal it approximates, while a sustained turn (rounding an obstacle)
	// still comes through — it just eases in instead of snapping. Bigger =
	// smoother, but the ghost trails a little further back.
	private static final float PATH_SMOOTH_TAU = 150f;

	// A single-frame jump farther than this (local units) is a teleport or
	// render blip, not real walking: snap the smoother to it instead of
	// smearing out a long false trail the ghost would slowly slide along.
	private static final float PATH_SMOOTH_SNAP = 256f;

	// While moving, lean the facing partially toward the player (capped) so
	// the pet reads as watching you through turns without ever strafing.
	private static final float PLAYER_BIAS = 0.25f;
	private static final int PLAYER_BIAS_CAP = 80;

	// When resting, turn to face the player if more than this far off (JAU),
	// like a pet watching its owner; deadband stops idle micro-fidgeting.
	private static final int IDLE_FACE_DEADBAND = 48;

	// Turn-in-place: when the travel heading is far off the body's current
	// facing, scale speed down so the pet pivots first and then walks,
	// instead of gliding sideways while its body slowly rotates — the single
	// most robotic-looking artifact. Starts at ~97 deg off so ordinary 90 deg
	// corners NEVER trigger it (gating corners caused speed rubber-banding);
	// only genuine double-backs pivot. Never fully stops (no deadlock), and
	// is skipped during catch-up dashes so it can never fall behind.
	private static final int TURN_GATE_START = 550;
	private static final int TURN_GATE_FULL = 950;
	private static final float TURN_GATE_MIN = 0.25f;

	// Docking: how the ghost hands back to the real pet without a visible
	// pop — it strolls onto the pet's exact spot, matches its facing, and
	// only then swaps.
	private static final float DOCK_ARRIVE = 4f;
	private static final int DOCK_FACING = 48;

	// Thrall attack: pets with a known attack animation play it; those
	// without fall back to a short forward lunge.
	private static final float LUNGE_MS = 400f;
	private static final float LUNGE_DIST = 48f;

	// A thrall that despawns farther than this from you left your render
	// range (you ran off); nearer than this it expired in view. Governs
	// whether impersonation persists (range loss) or ends (expiry).
	private static final int THRALL_RENDER_EDGE = 13;

	// Give up re-acquiring a lost thrall after this long, in case it expired
	// while off-screen (so we don't stay hidden forever).
	private static final int THRALL_REACQUIRE_TICKS = 120;

	// Tiles within which to grab a thrall. Small on first cast (avoid stealing
	// another player's thrall, which spawns right next to its caster); wide
	// when re-acquiring one we already know is ours, so it snaps back the
	// instant it re-enters render range instead of waiting to close in.
	private static final int THRALL_ACQUIRE_RADIUS = 5;
	private static final int THRALL_REACQUIRE_RADIUS = 16;

	@Inject private Client client;
	@Inject private ClientThread clientThread;
	@Inject private Hooks hooks;
	@Inject private PetFollowerConfig config;
	@Inject private ConfigManager configManager;
	@Inject private ItemManager itemManager;
	@Inject private OverlayManager overlayManager;

	private final Hooks.RenderableDrawListener drawListener = this::shouldDraw;

	private NPC pet;
	private Ghost ghost;
	private boolean hidingReal;
	private WorldPoint lastPlayerTile;
	private int idleTicks;

	// The player's rendered path: polyline of on-screen positions with
	// cumulative arc length. The ghost lives at some arc position on it.
	private final Deque<PathPoint> path = new ArrayDeque<>();
	private float playerCum;
	private float playerSpeed; // smoothed, units/ms
	private int pathPlane;

	private float ghostArc;
	private float ghostV;

	// Set when a scene load interrupts an ACTIVE follow: the next ghost
	// spawn starts already in motion. Spawning stationary put the
	// standing-start reaction beat where it doesn't belong — after a
	// mid-run zone reload the pet popped in and stood staring for the
	// style's whole reaction distance (worst on Loose, ~2 tiles).
	private boolean resumeAtSpeed;

	// Scene-reload translation: LOADING shifts the local coordinate origin;
	// rather than tearing the ghost down we translate its whole local-space
	// state by the base delta on the next rendered frame. lastBaseX/Y hold
	// the PRE-load scene base — they only update on rendered LOGGED_IN
	// frames, which never happen mid-load, so a chain of reloads still
	// resolves as one cumulative delta.
	private boolean pendingSceneShift;
	private int lastBaseX;
	private int lastBaseY;
	// Player's world tile captured the INSTANT loading starts. The
	// walk-vs-teleport classification must compare against this, never
	// against lastPlayerTile at resolve time: a post-load game tick can
	// fire before our first rendered frame and overwrite lastPlayerTile
	// with the arrival position — the comparison then sees "no jump" and
	// mistranslates a teleport (ghost stranded off-scene, then slingshot).
	private WorldPoint pendingShiftFrom;
	private float ghostX;
	private float ghostY;
	private float turnV;
	// Low-pass state for the recorded follow-path (see PATH_SMOOTH_TAU).
	private float pathSmoothX;
	private float pathSmoothY;
	private boolean pathSmoothInit;
	// Travel heading the steering last aimed for; the speed gate compares it
	// against the body's actual facing to decide pivot-in-place vs walk.
	private int desiredHeading;
	private long lastFrameNanos;
	private int poseState = -1;
	private int lastPoseFrame;
	private boolean docking;

	// Cache-built stand-in model, drawn during the moments the real pet NPC
	// doesn't exist (the server teleport-respawns it while you outrun it).
	// Built from the pet's own cache definition — model ids, recolors, and
	// its exact resize values — so it's the same pet, just standing still.
	// This is what lets the ghost stay visible through blips instead of
	// blinking out (the live model buffer can never be drawn then).
	private Model fallbackModel;
	private int fallbackForId = -1;
	// The pet definition's resize values (128 = 1.0x), applied per-frame to
	// the blip stand-in's ANIMATED output — never baked into the model data.
	private int fbWidthScale = 128;
	private int fbHeightScale = 128;

	// Animated stand-in shown during blips: a RuneLiteObject playing the
	// pet's own walk/run/idle cycle on the cache-built model, positioned by
	// the same simulation — so the pet keeps striding through the blip
	// instead of gliding frozen.
	private RuneLiteObject blipObj;
	private int blipAnimId = -1;
	private boolean blipActive;
	// The Model instance currently applied to blipObj — refreshed when the
	// cache model is rebuilt mid-display (transmog switch).
	private Model blipModelApplied;

	// Dwell timer for walk/run pose switches (see the pose section).
	private long lastPoseSwitchMs;
	// Slow-smoothed speed used ONLY for pose decisions, so speed breathing
	// while cruising can't flap the walk/run animation.
	private float poseSpeed;

	// Faux-run for pets with no real run animation (walk id == run id): the
	// engine advances the pose frame on its own clock; we accumulate a
	// fractional boost and push extra frames so the animation cadence
	// follows the ghost's ACTUAL speed — 1x at walk pace ramping to a
	// per-pet cap at full speed. That kills foot-slide continuously (the
	// real tell of a fake run) instead of stepping between fixed rates.
	// The cap adapts to the walk cycle's NATIVE length — a slow lumber can
	// absorb 1.75x, but speeding an already-short cycle just loops it like
	// a wind-up toy (robotic/same-y), so quick cycles get a gentler cap or
	// none. No shared animation data is ever touched.
	private Animation hijackWalkAnim;

	// Cadence accumulators: engine-COOPERATIVE frame boosting. We only ever
	// react to the engine's own frame advances and top them up — never run
	// our own clock. (A free-running clock that overwrote the frame every
	// rendered frame was tried and REVERTED: the engine keeps advancing
	// frames underneath, the two rates stack, and everything played way too
	// fast. Slowing below native speed has the same trap in reverse — it
	// needs control of the engine's frame TIMER, not just the index.)
	private int prevMirrorPoseFrame = -1;
	private float runBoostAcc;
	// Blip speed-up: extra time fed into the AnimationController's OWN
	// clock (ac.tick), never frame-index pushes — setFrame desynced the
	// controller's internal timing, which is why a transmogged pet hitched
	// while the SAME pet as a real follower played smoothly.
	private float blipExtraMs;
	private long blipClockNanos;

	// Pose anims actually used for rendering. Normally the real pet's own
	// (== origX); under the transmog preview, the previewed pet's set. The
	// origX fields always keep the REAL values for restore on release.
	private int poseIdle = -1;
	private int poseWalk = -1;
	private int poseRun = -1;

	// Transmog preview: render the follower as any pet NPC id, built purely
	// from the cache — a test bench for pets you don't own.
	private boolean previewing;
	private int activePreviewId = -1;
	private long blipAttackUntil;
	private long lastPetAttackMs;

	// Following style: parameters swapped per the config enum, applied at
	// the existing use sites. Medium/Loose also give the trailing distance
	// a slow random drift (gapDrift, eased toward a retargeted goal every
	// few seconds) — bounded variance in the resting geometry only, so
	// pathing, wall handling, and catch-up stay exactly as Strong.
	private float followStartDist = START_DIST;
	private float followSlope = 350f;
	private float followSmoothTau = PATH_SMOOTH_TAU;
	private int followSettleTicks = 2;
	private float gapDrift;
	private float gapDriftTarget;
	private long gapDriftRetargetMs;
	private final java.util.Random followRng = new java.util.Random();

	// Per-pet run-cadence tuning. The one config slider always shows and
	// edits the pet currently displayed; each pet's value is persisted under
	// its own config key ("petSpeed_<npcId>") so every pet remembers its own
	// setting. syncingSpeedUi guards the slider-refresh round trip.
	private int petSpeedPct = 100;
	private int speedKeyId = -1;
	private boolean syncingSpeedUi;

	// Beside-the-owner rest: when you stop, the ghost breaks off the walked
	// line for its last half-tile and settles on a free adjacent tile
	// (flanks preferred), like a vanilla pet that wanders to your side.
	private boolean settling;
	private boolean restDone;
	private float restX;
	private float restY;

	// Thrall impersonation: while a summoned thrall is tracked, it is hidden
	// and the ghost rides its exact position and facing instead of following.
	private NPC thrall;
	private boolean thrallAttached;
	private float thrallSpeed;
	private float prevThrallX;
	private float prevThrallY;
	private float lungeMs;
	private Projectile lastThrallProjectile;
	private boolean rescanThrall;

	// Thrall projectile tint: the REAL projectile keeps flying, animating,
	// and rendering itself — every frame we repaint its live posed model's
	// face colors (hue/saturation to the config color, luminance kept, so
	// the shading survives). Same philosophy as mirroring the pet's live
	// model: never rebuild what the engine already built, just touch it up.
	// (Building a tinted copy from the cache spotanim def was tried and
	// fails: getIndexConfig().loadData returns null for spotanim configs.)
	private final Set<Projectile> tintedBolts = new HashSet<>();
	private boolean tintFailed;

	// The engine CACHES posed spotanim models and reuses them, so everything
	// we repaint is snapshotted first and restored on toggle-off / color
	// change / scene load. CRUCIAL: posed animation frames SHARE one set of
	// face-color arrays (posing moves vertices, never colors), so the
	// snapshot is keyed by the color ARRAY's identity, not the Model's —
	// Model-keyed snapshots capture already-tinted colors as "originals"
	// and make restoration order-dependent.
	private final IdentityHashMap<int[], TintSnapshot> tintOriginals = new IdentityHashMap<>();

	private static final class TintSnapshot
	{
		final int[] a1, a2, a3; // the live (shared) arrays we repaint
		final int[] o1, o2, o3; // pristine copies taken before any tint

		TintSnapshot(int[] a1, int[] a2, int[] a3)
		{
			this.a1 = a1;
			this.a2 = a2;
			this.a3 = a3;
			o1 = a1.clone();
			o2 = a2.clone();
			o3 = a3.clone();
		}
	}

	// Impact splash on the thrall's victim: the spotanim that starts on the
	// target actor at the projectile's landing cycle IS our splash (matched
	// by cycle, no hardcoded ids), and ActorSpotAnim is a Renderable with
	// its own getModel() — so it gets the same per-frame repaint.
	private final List<SplashTint> splashTints = new ArrayList<>();

	// One projectile deals ONE hitsplat, so each landing is claimable by
	// exactly one hitsplat event — otherwise the player's own hit landing
	// in the same window got tagged too and a duplicate pet icon appeared.
	private final Set<Projectile> claimedBolts = new HashSet<>();

	private static final class SplashTint
	{
		final Actor target;
		final int impactCycle;
		boolean hitClaimed;

		SplashTint(Actor target, int impactCycle, boolean hitClaimed)
		{
			this.target = target;
			this.impactCycle = impactCycle;
			this.hitClaimed = hitClaimed;
		}
	}

	// Pet damage icon: a hitsplat that appears on a thrall projectile's
	// victim within a breath of the landing cycle is the pet's own damage —
	// show the pet's picture beside it. The picture is the pet's
	// inventory-item sprite, resolved by scanning item names for the
	// displayed pet's name (exact first, else the longest item name the pet
	// name contains: "Corrupted Youngllef" pet -> "Youngllef" item), and
	// persisted per pet under petItem_<npcId> so the scan runs once ever.
	private final List<PetHit> petHits = new ArrayList<>();
	private Overlay petHitOverlay;
	private BufferedImage petIcon;
	private int petIconKeyId = -1;

	// The engine stacks up to 4 simultaneous hitsplats per actor in fixed
	// slots, and the API exposes neither the slots nor their positions —
	// so this mirrors the engine's bookkeeping from HitsplatApplied events
	// (first free slot, else evict the oldest; expiry from the hitsplat's
	// own disappear cycle). Our hit's slot index then positions the icon
	// beside the RIGHT hitsplat when the player's own damage shares the
	// target. Weak keys: dead actors clean themselves up.
	private final java.util.WeakHashMap<Actor, int[]> hitSlots = new java.util.WeakHashMap<>();

	// Stacked-hitsplat geometry, measured from the user's recordings: the
	// FIRST active hitsplat draws AT the half-logical-height anchor and
	// each additional one stacks one height (~25 px) straight up — the
	// stack is NOT centered on the anchor (a centered model put the icon
	// midway between two splats). A slot's position is its rank among the
	// currently ACTIVE slots, recomputed every frame as splats expire.
	private static final int HIT_SLOT_SPACING = 20;

	// No thrall hits above 3 (superior max). A bigger hitsplat can never be
	// the pet's, which disambiguates most same-tick player+thrall landings.
	private static final int THRALL_MAX_HIT = 3;

	private static final class PetHit
	{
		final Actor target;
		final int untilCycle; // the hitsplat's own disappear cycle
		final int slot;

		PetHit(Actor target, int untilCycle, int slot)
		{
			this.target = target;
			this.untilCycle = untilCycle;
			this.slot = slot;
		}
	}
	private int ghostSize = 1;
	private float gap = GAP; // effective resting distance for the current pet
	private boolean ghostShown;

	// Thrall impersonation persists across render-range loss: `impersonating`
	// stays true even when the thrall NPC is momentarily gone.
	private boolean impersonating;
	private int thrallLostTicks;
	private int thrallDeadlineTick;
	private int suppressTakeoverTicks;

	// The pet's original pose-animation table, restored on handback. While
	// ghosted, all three entries point at the one animation the ghost needs,
	// so the engine can never interrupt the cycle no matter which it picks.
	private boolean animsHijacked;
	private int origIdle = -1;
	private int origWalk = -1;
	private int origRun = -1;

	@Provides
	PetFollowerConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(PetFollowerConfig.class);
	}

	@Override
	protected void startUp()
	{
		hooks.registerRenderableDrawListener(drawListener);
		petHitOverlay = new PetHitOverlay();
		overlayManager.add(petHitOverlay);
	}

	@Override
	protected void shutDown()
	{
		hooks.unregisterRenderableDrawListener(drawListener);
		overlayManager.remove(petHitOverlay);
		hidingReal = false;
		clientThread.invoke(() ->
		{
			clearTintedBolts();
			releasePet();
			despawnGhost();
			pet = null;
		});
	}

	// ------------------------------------------------------------------
	// Rendering veto: hide the real pet while the ghost is active
	// ------------------------------------------------------------------

	private boolean shouldDraw(Renderable renderable, boolean drawingUI)
	{
		if (hidingReal && renderable == pet)
		{
			return false;
		}
		if (thrall != null && ghost != null && renderable == thrall)
		{
			// Hide the thrall only while a ghost actually stands in for it —
			// with no pet out there is no ghost, and vetoing the thrall then
			// makes it silently vanish.
			return false;
		}
		return true;
	}

	// ------------------------------------------------------------------
	// Game-tick logic: follower detection, takeover, handoff
	// ------------------------------------------------------------------

	@Subscribe
	public void onGameTick(GameTick e)
	{
		Player me = client.getLocalPlayer();
		if (me == null)
		{
			return;
		}

		if (suppressTakeoverTicks > 0)
		{
			suppressTakeoverTicks--;
		}

		if (rescanThrall)
		{
			rescanThrall = false;
			reacquireThrall(me);
		}

		NPC found = findMyPet();
		if (found != null)
		{
			if (found != pet)
			{
				// New pet instance (server teleport-respawn while you outrun
				// it, or a scene reload). This game-tick path usually wins
				// the race against the render-loop one, so it MUST do the
				// same careful mid-cycle resume — when it just reset the
				// pose state instead, every pet teleport restarted the run
				// animation at frame 0 and let the materialize animation
				// play on the mirror (the "animation repeats while
				// sustained-running" bug: teleports only happen when the
				// pet can't keep up, which is why walking and short steps
				// never glitched).
				relinkPet(found);
			}
			pet = found;
		}

		// Only tear down when the follower is actually GONE (dismissed / picked
		// up) — the varp tells us this. Running out of render range leaves the
		// varp set, so the ghost stays alive and the real (slow) pet stays
		// hidden until it comes back. Never tear down mid-impersonation.
		if (!isFollowerActive() && thrall == null && !impersonating)
		{
			releasePet();
			pet = null;
			hidingReal = false;
			despawnGhost();
			return;
		}

		WorldPoint playerTile = me.getWorldLocation();
		idleTicks = playerTile.equals(lastPlayerTile) ? idleTicks + 1 : 0;
		// An instant jump this far is a teleport. Thralls never follow
		// through teleports — the summon is stranded where it was cast.
		boolean teleported = lastPlayerTile != null
			&& playerTile.distanceTo2D(lastPlayerTile) > 25;
		lastPlayerTile = playerTile;
		if (idleTicks > 2)
		{
			// Standing after the reload: the natural reaction beat applies
			// again to the next start.
			resumeAtSpeed = false;
		}

		if (hidingReal)
		{
			ensureHijack();
		}

		// Thrall impersonation is independent of the follow state and survives
		// pet render blips.
		if (thrall != null)
		{
			if (!hidingReal || ghost == null)
			{
				hidingReal = true;
				spawnGhost(me);
			}
			return;
		}

		// Thrall left render range while still active: keep impersonating and
		// re-acquire it when it (or a fresh cast) comes back, rather than
		// reverting to a normal pet. Give up only after a long timeout.
		if (impersonating)
		{
			// Unrecoverable: you teleported away (the stranded thrall can
			// never re-enter range — waiting the full window just kept the
			// pet hidden for over a minute), or the summon's own timer ran
			// out while it was off-screen. Resume normal following now.
			if (teleported || client.getTickCount() > thrallDeadlineTick)
			{
				log.debug("Thrall unrecoverable ({}); resuming follow",
					teleported ? "teleported away" : "timer expired");
				endThrall();
				return;
			}
			// The real pet must never show while impersonating, even mid-run.
			hidingReal = true;
			NPC t = findNearbyThrall(me, THRALL_REACQUIRE_RADIUS);
			if (t != null)
			{
				thrall = t;
				thrallAttached = false;
				lungeMs = 0f;
				thrallLostTicks = 0;
				armThrallDeadline();
				log.debug("Re-acquired thrall id={}", t.getId());
			}
			else if (++thrallLostTicks > THRALL_REACQUIRE_TICKS)
			{
				log.debug("Thrall gone for good; resuming follow");
				endThrall();
			}
			return;
		}

		if (!hidingReal)
		{
			if (idleTicks == 0 && found != null && suppressTakeoverTicks == 0)
			{
				// You started moving: take over visually from where the real
				// pet currently is; it will run up behind you.
				hidingReal = true;
				spawnGhost(me);
			}
			return;
		}

		// The rest is normal-follow handback, which needs a live pet. During a
		// render blip just coast — no handback.
		if (found == null)
		{
			return;
		}

		// Handback: once you're idle, the ghost settled, and the real pet has
		// arrived nearby, start docking — stroll onto the pet's exact spot
		// and swap invisibly (see dockTick).
		if (idleTicks == 0)
		{
			if (docking)
			{
				abortDock();
			}
		}
		else if (config.handoffEnabled()
			&& !docking
			&& !settling
			&& !previewing // never reveal the real (different-looking) pet mid-preview
			&& ghost != null
			&& ghostV == 0f
			&& idleTicks >= Math.max(1, config.handoffTicks())
			&& pet.getWorldLocation().distanceTo(playerTile) <= 2)
		{
			LocalPoint pp = pet.getLocalLocation();
			if (pp != null
				&& Math.hypot(pp.getX() - ghostX, pp.getY() - ghostY) <= 2.5 * Perspective.LOCAL_TILE_SIZE)
			{
				docking = true;
			}
		}
	}

	// ------------------------------------------------------------------
	// Per-frame follower simulation (BeforeRender: once per rendered frame)
	// ------------------------------------------------------------------

	@Subscribe
	public void onBeforeRender(BeforeRender e)
	{
		if (client.getGameState() == GameState.LOGGED_IN)
		{
			// Resolve a pending scene-origin shift BEFORE anything touches
			// local coordinates, then record this frame's base as the new
			// reference (never updated mid-load, so it stays the PRE-load
			// base until the shift is applied).
			if (pendingSceneShift)
			{
				pendingSceneShift = false;
				applySceneShift();
			}
			lastBaseX = client.getBaseX();
			lastBaseY = client.getBaseY();
		}
		// Bolts in flight outlive state changes (handoff, thrall expiry), so
		// they update before any early return below.
		updateTintedBolts();
		if (!hidingReal || client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}

		Player me = client.getLocalPlayer();
		if (me == null)
		{
			return;
		}

		LocalPoint p = me.getLocalLocation();
		if (p == null)
		{
			return;
		}

		// --- transmog preview state --------------------------------------
		// When a preview id is set, the follower renders as THAT pet: the
		// mirror stays hidden and the cache-built stand-in (with the
		// previewed pet's own pose anims) is shown full-time, driven by the
		// same simulation.
		int cfgPreview = config.transmog().getNpcId();
		if ((cfgPreview >= 0) != previewing || (previewing && cfgPreview != activePreviewId))
		{
			previewing = cfgPreview >= 0;
			activePreviewId = previewing ? cfgPreview : -1;
			// Rebuild everything for the new appearance. Restore the pet's
			// TRUE animation table before the re-capture: mid-transmog the
			// table holds the previous transmog's anim ids, and re-capturing
			// those as "original" froze the real pet's animations after
			// turning transmog off (its skeleton can't play foreign anims)
			// until a fresh NPC instance arrived with a clean table.
			releasePet();
			fallbackModel = null;
			fallbackForId = -1;
			blipAnimId = -1;
			if (pet != null)
			{
				ensureHijack();
				buildFallbackModel();
			}
			// The displayed pet changed, so its cadence profile changes with
			// it — even if the real pet NPC isn't linked right now.
			loadPetSpeed();
		}

		// The ghost mirrors the live pet NPC's model, so it can only be drawn
		// while that NPC is being rendered. When the real pet isn't rendered
		// (scene reload / far off screen), drop the ghost rather than draw its
		// recycled buffer (which would flash into other players/NPCs) — the
		// same brief absence the vanilla game shows while a pet teleports in.
		// Re-link EVERY FRAME (the game tick is too coarse: it added up to
		// 0.6 s of extra absence) so the ghost respawns the instant the pet
		// NPC renders again.
		if (pet == null)
		{
			NPC found = findMyPet();
			if (found != null)
			{
				relinkPet(found);
			}
			else if (!impersonating)
			{
				// Render blip: when you outrun render range the real pet NPC
				// despawns for a moment and the server teleports it back to
				// you. Keep ALL ghost state and keep simulating. The mirror
				// can't be drawn (recycled live buffer), so swap in the
				// animated cache-built stand-in — it keeps striding along
				// the same path until the NPC is back.
				if (ghost == null)
				{
					return;
				}
				showGhost(false);
				if (fallbackModel != null)
				{
					showBlip(true);
					updateBlipAnim();
					syncBlip();
				}
			}
		}
		// Previewing: never show the mirror; the stand-in IS the pet.
		if (previewing && ghost != null)
		{
			showGhost(false);
			if (fallbackModel != null)
			{
				showBlip(true);
				updateBlipAnim();
				syncBlip();
			}
		}
		else if (pet != null && ghost != null && !ghostShown && !impersonating)
		{
			showBlip(false);
			showGhost(true);
		}
		if (pet != null)
		{
			// Faux-run: each time the ENGINE advances the walk cycle,
			// accumulate the speed-proportional boost and push extra frames
			// as it overflows — cadence follows actual ground speed, and
			// because we only react to the engine's own advances the two
			// clocks can never stack.
			int curFrame = pet.getPoseAnimationFrame();
			float factor = cadenceFactor();
			if (cadenceEligible(factor) && hijackWalkAnim != null
				&& pet.getPoseAnimation() == poseWalk
				&& prevMirrorPoseFrame != -1 && curFrame != prevMirrorPoseFrame)
			{
				runBoostAcc += factor - 1f;
				int n = hijackWalkAnim.getNumFrames();
				while (runBoostAcc >= 1f && n > 0)
				{
					runBoostAcc -= 1f;
					curFrame = (curFrame + 1) % n;
					pet.setPoseAnimationFrame(curFrame);
				}
			}
			prevMirrorPoseFrame = curFrame;

			// Remember where the pose cycle is, so a blip re-link can resume
			// it mid-stride instead of restarting the animation.
			lastPoseFrame = curFrame;
		}
		if (ghost == null)
		{
			if (pet != null)
			{
				spawnGhost(me);
			}
			return;
		}

		long nowNanos = System.nanoTime();
		float dt = (nowNanos - lastFrameNanos) / 1_000_000f; // ms
		lastFrameNanos = nowNanos;
		dt = Math.max(0.1f, Math.min(100f, dt));
		updateFollowStyle(dt);

		if (thrall != null)
		{
			if (!config.thrallMode())
			{
				endThrall();
			}
			else
			{
				thrallTick(dt);
				return;
			}
		}

		// Impersonating but the thrall is out of render range: try to
		// re-acquire every frame (instant snap-back on re-entering); until
		// then keep the ghost frozen and hidden (it's off-screen anyway) so
		// it never briefly reverts to a following pet.
		if (impersonating)
		{
			NPC t = findNearbyThrall(me, THRALL_REACQUIRE_RADIUS);
			if (t != null)
			{
				thrall = t;
				thrallAttached = false;
				lungeMs = 0f;
				thrallLostTicks = 0;
				armThrallDeadline();
				showGhost(true);
				return;
			}
			showGhost(false);
			return;
		}

		if (docking)
		{
			dockTick(dt);
			return;
		}

		// --- beside-the-owner rest state ----------------------------------
		if (settling || restDone)
		{
			if (idleTicks == 0)
			{
				// You set off again: resume following from wherever the
				// ghost stands (it left the recorded path to settle).
				settling = false;
				restDone = false;
				reseedFromGhost();
				// fall through into normal following
			}
			else if (settling)
			{
				settleTick(dt);
				return;
			}
			else
			{
				// Resting beside you: just keep watching you.
				applyPose(POSE_IDLE);
				int toPlayer = jau(p.getX() - ghostX, p.getY() - ghostY);
				if (Math.abs(wrapAngle(toPlayer - ghost.getOrientation())) > IDLE_FACE_DEADBAND)
				{
					steer(toPlayer, dt);
				}
				return;
			}
		}

		// --- extend the rendered path -----------------------------------
		// Same-plane jumps of any size just become a long path segment the
		// ghost dashes across — it never teleports. Only a plane change
		// (stairs/ladders) reparks it, since there's nothing to walk through.
		int plane = client.getPlane();
		PathPoint tail = path.peekLast();

		if (tail == null || plane != pathPlane)
		{
			resetPathBehind(p, plane);
			return;
		}

		// Follow a gently smoothed version of your path, not your exact
		// tile-by-tile positions. Diagonal running is a rapid staircase (one
		// tile forward, one across, repeating); mirrored exactly at a one-tile
		// gap the ghost jerked left/right every step. Low-pass filtering the
		// recorded points averages that zigzag into the straight diagonal it
		// approximates; a genuine sustained turn still comes through because
		// it doesn't alternate. A big single-frame jump (teleport/blip) snaps
		// through rather than smearing a long false trail.
		if (!pathSmoothInit)
		{
			pathSmoothX = p.getX();
			pathSmoothY = p.getY();
			pathSmoothInit = true;
		}
		else if ((float) Math.hypot(p.getX() - pathSmoothX, p.getY() - pathSmoothY) > PATH_SMOOTH_SNAP)
		{
			pathSmoothX = p.getX();
			pathSmoothY = p.getY();
		}
		else
		{
			float sc = Math.min(1f, dt / followSmoothTau);
			pathSmoothX += (p.getX() - pathSmoothX) * sc;
			pathSmoothY += (p.getY() - pathSmoothY) * sc;
		}

		float step = (float) Math.hypot(pathSmoothX - tail.x, pathSmoothY - tail.y);
		if (step > 0.5f)
		{
			playerCum += step;
			path.addLast(new PathPoint(pathSmoothX, pathSmoothY, playerCum));
		}

		// Units per ms, smoothed over ~100 ms.
		playerSpeed += (step / dt - playerSpeed) * Math.min(1f, dt / 100f);

		// Straight-line distance to you — used by the double-back rule and
		// the short-step patience below.
		float directDX = p.getX() - ghostX;
		float directDY = p.getY() - ghostY;
		float directDist = (float) Math.hypot(directDX, directDY);

		// You doubled back INTO the ghost: a real pet doesn't keep running
		// away along its old line and then whip around — it stops, lets you
		// pass, and falls in behind. While you're heading at the ghost and
		// already inside resting distance, re-anchor its route straight to
		// you; the reseeded gap is negative, so the normal stop logic holds
		// it still (pivoting to watch you) until you've pulled ahead again.
		// Corners never trigger this: after a 90-degree turn the ghost is
		// behind your shoulder, well outside the 60-degree facing cone.
		if (idleTicks == 0 && directDist > 1f && directDist < gap)
		{
			double prad = me.getCurrentOrientation() * Math.PI / 1024.0;
			float pfx = -(float) Math.sin(prad);
			float pfy = -(float) Math.cos(prad);
			if ((pfx * -directDX + pfy * -directDY) / directDist > 0.5f)
			{
				reseedFromGhost();
			}
		}

		// --- route straightening (string pulling) -------------------------
		// See BRIDGE_LOOK. Runs continuously: one bridge per bend — after a
		// bridge the head of the route IS the chord, so it can't re-trigger
		// until a new bend enters the window.
		float bridgeArc = Math.min(ghostArc + BRIDGE_LOOK, playerCum);
		if (bridgeArc - ghostArc > 64f && path.size() >= 2)
		{
			float bx = ghostX;
			float by = ghostY;
			PathPoint bprev = path.peekFirst();
			boolean bfound = false;
			for (PathPoint pp : path)
			{
				if (pp.cum >= bridgeArc)
				{
					float span = pp.cum - bprev.cum;
					float f = span <= 0f ? 1f : (bridgeArc - bprev.cum) / span;
					bx = bprev.x + (pp.x - bprev.x) * f;
					by = bprev.y + (pp.y - bprev.y) * f;
					bfound = true;
					break;
				}
				bprev = pp;
			}
			if (!bfound)
			{
				bx = bprev.x;
				by = bprev.y;
				bridgeArc = bprev.cum;
			}
			float along = bridgeArc - ghostArc;
			float chord = (float) Math.hypot(bx - ghostX, by - ghostY);
			if (along - chord > BRIDGE_SLACK
				&& strollClear(ghostX, ghostY, bx, by))
			{
				// Rebuild the head of the route as the straight chord; keep
				// everything beyond the bridge point, distances shifted.
				Deque<PathPoint> straightened = new ArrayDeque<>();
				straightened.addLast(new PathPoint(ghostX, ghostY, 0f));
				straightened.addLast(new PathPoint(bx, by, chord));
				for (PathPoint pp : path)
				{
					if (pp.cum > bridgeArc + 0.5f)
					{
						straightened.addLast(new PathPoint(pp.x, pp.y, chord + (pp.cum - bridgeArc)));
					}
				}
				path.clear();
				path.addAll(straightened);
				playerCum = chord + (playerCum - bridgeArc);
				ghostArc = 0f;
			}
		}

		// --- decide the ghost's target speed ----------------------------
		float error = playerCum - (gap + gapDrift) - ghostArc;

		// --- break off and settle beside the owner when they stop ---------
		// Once you're standing and the ghost is within its last half-tile of
		// its on-path stop, divert to a free tile beside you instead of
		// always parking dead on your walked line (vanilla pets end up at
		// your flank as often as behind you). If no side tile passes the
		// collision checks it simply rests on the path like before.
		// Patience beat (idleTicks >= 2): the pet waits ~a second before
		// repositioning, and also settles from a short-step hold (within a
		// tile of resting range) — not just from an on-path stop.
		if (idleTicks >= followSettleTicks && !settling && !restDone
			&& (error <= STOP_DIST + 64f || directDist < gap + Perspective.LOCAL_TILE_SIZE))
		{
			if (chooseRestSpot(me))
			{
				settling = true;
				settleTick(dt);
				return;
			}
			if (ghostV == 0f)
			{
				restDone = true;
				applyPose(POSE_IDLE);
				return;
			}
		}

		float vTarget;
		if (error <= STOP_DIST)
		{
			vTarget = 0f;
		}
		else if (ghostV == 0f
			&& (error < (resumeAtSpeed ? START_DIST : followStartDist)
				|| (idleTicks >= 1 && directDist < gap + Perspective.LOCAL_TILE_SIZE)))
		{
			// Reaction beat — and short-step patience: a standing owner
			// shuffling a tile or two doesn't send the pet scurrying after
			// them at full pace. It waits, and the settle logic repositions
			// it calmly (or not at all) once you've been still a moment.
			// After a mid-run zone reload (resumeAtSpeed) the style's full
			// reaction distance is suppressed for the FIRST start: the ghost
			// respawns AT its follow spot, so the stop branch above zeroes
			// it instantly and a Loose/Medium pet then stood staring for its
			// whole reaction distance right after popping in.
			vTarget = 0f;
		}
		else if (idleTicks >= 1 && error <= 512f)
		{
			// You've stopped: approach at a pace set by the REMAINING
			// distance, like a vanilla follower — walk short corrections,
			// only run when genuinely far behind. (Inheriting your last
			// running speed here made a two-tile catch-up look like a
			// sprint next to the real thing.)
			float far = Math.min(1f, Math.max(0f, (error - 96f) / 256f));
			vTarget = WALK_SPEED + (RUN_SPEED - WALK_SPEED) * far;
		}
		else
		{
			// Continuous cruise: match your pace plus a gentle proportional
			// catch-up term, capped by a dash ceiling that scales with the
			// gap (huge gaps still close in under a second, never teleport).
			// One smooth curve — the old hard cruise/dash threshold at
			// CATCHUP_DIST banged the speed up and down around corners,
			// which read as rubber-banding.
			float v = playerSpeed + error / followSlope;
			float cap = Math.max(CATCHUP_SPEED, error / 700f);
			vTarget = Math.max(WALK_SPEED * 0.85f, Math.min(cap, v));
		}
		if (vTarget > 0f)
		{
			// First start after the reload has happened; the style's normal
			// reaction beat applies from here on.
			resumeAtSpeed = false;
		}

		// Pivot before walking: if the path wants the ghost to head somewhere
		// its body isn't facing yet, slow it down (proportionally, floored at
		// TURN_GATE_MIN) so it visibly turns in place and then sets off,
		// rather than sliding sideways while rotating. Only a huge gap (a
		// teleport-scale 4+ tiles) overrides the gate — exempting ordinary
		// catch-up let the gap that builds DURING the pivot disable the gate
		// mid-turn, and the ghost dashed off sideways half-rotated, then
		// whipped around (the stall-then-burst artifact).
		if (vTarget > 0f && error <= 512f)
		{
			int mis = Math.abs(wrapAngle(desiredHeading - ghost.getOrientation()));
			if (mis > TURN_GATE_START)
			{
				float t = Math.min(1f, (mis - TURN_GATE_START) / (float) (TURN_GATE_FULL - TURN_GATE_START));
				vTarget *= 1f - t * (1f - TURN_GATE_MIN);
			}
		}

		// Never sprint at point-blank range: catch-up overspeed right next
		// to you is what reads as the pet LUNGING/slingshotting through
		// your character whenever its route swings. Far away it can still
		// dash; nearby it arrives at an honest run.
		if (directDist < gap * 1.5f)
		{
			vTarget = Math.min(vTarget, RUN_SPEED);
		}

		// Exponential approach (~120 ms time constant) so any target speed,
		// including big dashes, ramps smoothly.
		ghostV += (vTarget - ghostV) * Math.min(1f, dt / 120f);
		if (vTarget == 0f && ghostV < 0.02f)
		{
			ghostV = 0f;
		}

		// --- advance along the path --------------------------------------
		if (ghostV > 0f)
		{
			ghostArc = Math.min(ghostArc + ghostV * dt, playerCum - (gap + gapDrift) * 0.75f);
			moveGhostToArc(dt);
		}

		// --- pose: driven by a SLOW-smoothed speed --------------------------
		// The diagnostics showed the instantaneous speed "breathing" across
		// the walk/run threshold about twice a second while cruising (the
		// feedback between matching your pace and closing the gap), and each
		// crossing flipped the pose. Deciding on a ~400 ms-smoothed speed
		// removes the breathing entirely; hysteresis and a longer dwell
		// handle what's left. (applyPose additionally never restarts the
		// cycle unless the animation id truly changes.)
		poseSpeed += (ghostV - poseSpeed) * Math.min(1f, dt / 400f);
		int newState;
		if (vTarget == 0f && ghostV == 0f)
		{
			newState = POSE_IDLE;
		}
		else if (poseState == POSE_RUN)
		{
			newState = poseSpeed < RUN_SPEED * 0.55f ? POSE_WALK : POSE_RUN;
		}
		else
		{
			newState = poseSpeed > RUN_SPEED * 0.75f ? POSE_RUN : POSE_WALK;
		}
		long nowMs = nowNanos / 1_000_000L;
		boolean walkRunSwap = (newState == POSE_WALK && poseState == POSE_RUN)
			|| (newState == POSE_RUN && poseState == POSE_WALK);
		if (walkRunSwap && nowMs - lastPoseSwitchMs < 600)
		{
			newState = poseState;
		}
		if (newState != poseState)
		{
			lastPoseSwitchMs = nowMs;
		}
		applyPose(newState);

		// At rest, gently turn to face you — a pet watching its owner. The
		// wide deadband keeps it from fidgeting at small angles.
		if (newState == POSE_IDLE)
		{
			int toPlayer = jau(p.getX() - ghostX, p.getY() - ghostY);
			if (Math.abs(wrapAngle(toPlayer - ghost.getOrientation())) > IDLE_FACE_DEADBAND)
			{
				steer(toPlayer, dt);
			}
		}
	}

	/**
	 * Hand back to the real pet without a visible pop: stroll the ghost onto
	 * the pet's exact position, sweep to its facing, then swap.
	 */
	private void dockTick(float dt)
	{
		if (pet == null)
		{
			docking = false;
			return;
		}

		LocalPoint pp = pet.getLocalLocation();
		if (pp == null)
		{
			docking = false;
			return;
		}

		float dx = pp.getX() - ghostX;
		float dy = pp.getY() - ghostY;
		float d = (float) Math.hypot(dx, dy);

		if (d > DOCK_ARRIVE)
		{
			applyPose(d > 24f ? POSE_WALK : poseState);
			float sp = Math.min(WALK_SPEED * dt, d);
			ghostX += dx / d * sp;
			ghostY += dy / d * sp;
			steer(jau(dx, dy), dt);
			place(new LocalPoint(Math.round(ghostX), Math.round(ghostY)), pathPlane);
			return;
		}

		applyPose(POSE_IDLE);
		int facing = pet.getCurrentOrientation();
		int diff = wrapAngle(facing - ghost.getOrientation());
		if (Math.abs(diff) > DOCK_FACING)
		{
			steer(facing, dt);
			return;
		}

		// Aligned in place, pose, and facing — swap is invisible.
		releasePet();
		hidingReal = false;
		docking = false;
		despawnGhost();
	}

	/**
	 * The player moved again mid-dock: resume following from wherever the
	 * ghost stands by reseeding the path from it to the player.
	 */
	private void abortDock()
	{
		docking = false;
		reseedFromGhost();
	}

	/** Rebuild the follow path as [ghost, player] from wherever the ghost is. */
	private void reseedFromGhost()
	{
		Player me = client.getLocalPlayer();
		LocalPoint p = me != null ? me.getLocalLocation() : null;
		if (p == null)
		{
			return;
		}
		path.clear();
		pathPlane = client.getPlane();
		float d = (float) Math.hypot(p.getX() - ghostX, p.getY() - ghostY);
		path.addLast(new PathPoint(ghostX, ghostY, 0f));
		if (d > 1f)
		{
			path.addLast(new PathPoint(p.getX(), p.getY(), d));
		}
		playerCum = d;
		ghostArc = 0f;
		ghostV = 0f;
		playerSpeed = 0f;
		pathSmoothInit = false;
		if (ghost != null)
		{
			desiredHeading = ghost.getOrientation();
		}
	}

	/** Stroll the last leg onto the chosen rest tile, then watch the owner. */
	private void settleTick(float dt)
	{
		float dx = restX - ghostX;
		float dy = restY - ghostY;
		float d = (float) Math.hypot(dx, dy);

		if (d > DOCK_ARRIVE)
		{
			applyPose(d > 24f ? POSE_WALK : poseState);
			float sp = Math.min(WALK_SPEED * dt, d);
			ghostX += dx / d * sp;
			ghostY += dy / d * sp;
			int face = jau(dx, dy);
			if (config.alwaysFaceOwner())
			{
				Player owner = client.getLocalPlayer();
				LocalPoint op = owner != null ? owner.getLocalLocation() : null;
				if (op != null)
				{
					face = jau(op.getX() - ghostX, op.getY() - ghostY);
				}
			}
			steer(face, dt);
			place(new LocalPoint(Math.round(ghostX), Math.round(ghostY)), pathPlane);
			return;
		}

		ghostX = restX;
		ghostY = restY;
		place(new LocalPoint(Math.round(restX), Math.round(restY)), pathPlane);
		applyPose(POSE_IDLE);
		ghostV = 0f;
		settling = false;
		restDone = true;
	}

	/**
	 * Pick a free tile beside the owner to rest on. Prefers the flank
	 * nearest the ghost, then the rear diagonals, then directly behind (the
	 * old default). Every candidate is collision-checked — footprint
	 * walkable and the stroll line clear — so the ghost never settles into
	 * a hedge or slides through a wall. Returns false when nothing beats
	 * standing where we already are.
	 */
	private boolean chooseRestSpot(Player me)
	{
		LocalPoint p = me.getLocalLocation();
		if (p == null)
		{
			return false;
		}

		double rad = me.getCurrentOrientation() * Math.PI / 1024.0;
		float fx = -(float) Math.sin(rad); // owner's forward
		float fy = -(float) Math.cos(rad);
		float lx = -fy; // owner's left flank
		float ly = fx;

		// Center-to-center distance that puts the pet's footprint adjacent
		// to the owner for any pet size.
		float r = 64f + ghostSize * 64f;

		boolean leftFirst =
			Math.hypot(p.getX() + lx * r - ghostX, p.getY() + ly * r - ghostY)
			<= Math.hypot(p.getX() - lx * r - ghostX, p.getY() - ly * r - ghostY);
		float s = leftFirst ? 1f : -1f;

		// Preference order: directly behind first (the classic follower
		// spot), then the rear diagonals, then the flanks — nearer side
		// first in each pair. But if the ghost already stands on ANY
		// acceptable spot, it stays put: where its approach lands it is
		// where it rests, which is what makes side-resting happen naturally
		// without pointless shuffling.
		final float diag = 0.7071f;
		float[][] dirs = {
			{-fx, -fy},                                         // directly behind
			{(lx * s - fx) * diag, (ly * s - fy) * diag},       // near rear diagonal
			{(-lx * s - fx) * diag, (-ly * s - fy) * diag},     // far rear diagonal
			{lx * s, ly * s},                                   // near flank
			{-lx * s, -ly * s},                                 // far flank
		};

		boolean corner = (ghostSize % 2) == 0; // even-size pets center on tile corners
		float gsx = snapCoord(ghostX, corner);
		float gsy = snapCoord(ghostY, corner);
		float bestX = 0f;
		float bestY = 0f;
		boolean haveBest = false;
		int psx = (int) Math.floor(p.getX() / (float) Perspective.LOCAL_TILE_SIZE);
		int psy = (int) Math.floor(p.getY() / (float) Perspective.LOCAL_TILE_SIZE);
		for (float[] dir : dirs)
		{
			float cx = snapCoord(p.getX() + dir[0] * r, corner);
			float cy = snapCoord(p.getY() + dir[1] * r, corner);

			// "Beside you" must mean actually beside you: a candidate with a
			// fence or wall edge between it and your tile is out, even
			// though both tiles are walkable.
			int csx = (int) Math.floor(cx / (float) Perspective.LOCAL_TILE_SIZE);
			int csy = (int) Math.floor(cy / (float) Perspective.LOCAL_TILE_SIZE);
			if (!canStep(psx, psy, csx, csy))
			{
				continue;
			}

			if (!footprintWalkable(cx, cy) || !strollClear(ghostX, ghostY, cx, cy))
			{
				continue;
			}

			// Already on an acceptable spot: STAY PUT unless meaningfully off
			// the tile (near a tile edge). The natural follow stop lands ~24
			// units shy of the tile center, and strolling that tiny recenter
			// after the settle pause read as a delayed "nudge toward you"
			// every time you stopped.
			if (cx == gsx && cy == gsy)
			{
				if (Math.hypot(cx - ghostX, cy - ghostY) <= 48f)
				{
					return false;
				}
				restX = cx;
				restY = cy;
				return true;
			}

			if (!haveBest)
			{
				bestX = cx;
				bestY = cy;
				haveBest = true;
			}
		}
		if (haveBest)
		{
			restX = bestX;
			restY = bestY;
			return true;
		}
		return false;
	}

	/** Snap a local coordinate to a tile center (or corner for even sizes). */
	private static float snapCoord(float v, boolean corner)
	{
		return corner
			? Math.round(v / Perspective.LOCAL_TILE_SIZE) * (float) Perspective.LOCAL_TILE_SIZE
			: (float) Math.floor(v / Perspective.LOCAL_TILE_SIZE) * Perspective.LOCAL_TILE_SIZE + Perspective.LOCAL_TILE_SIZE / 2f;
	}

	/** True if the pet's whole footprint centered at (cx, cy) is walkable. */
	private boolean footprintWalkable(float cx, float cy)
	{
		int swX = (int) Math.floor((cx - (ghostSize - 1) * 64f) / Perspective.LOCAL_TILE_SIZE);
		int swY = (int) Math.floor((cy - (ghostSize - 1) * 64f) / Perspective.LOCAL_TILE_SIZE);
		for (int i = 0; i < ghostSize; i++)
		{
			for (int j = 0; j < ghostSize; j++)
			{
				if (!tileWalkable(swX + i, swY + j))
				{
					return false;
				}
			}
		}
		return true;
	}

	/**
	 * The straight settle stroll must be steppable tile-to-tile. Checking
	 * only tile walkability let the ghost slide straight through fences:
	 * walls and fences live on tile EDGES in the collision map, and the
	 * tiles either side of a fence are both perfectly walkable.
	 */
	private boolean strollClear(float x0, float y0, float x1, float y1)
	{
		float d = (float) Math.hypot(x1 - x0, y1 - y0);
		int steps = Math.max(1, (int) (d / 32f));
		int px = (int) Math.floor(x0 / Perspective.LOCAL_TILE_SIZE);
		int py = (int) Math.floor(y0 / Perspective.LOCAL_TILE_SIZE);
		for (int i = 1; i <= steps; i++)
		{
			float t = i / (float) steps;
			int cx = (int) Math.floor((x0 + (x1 - x0) * t) / Perspective.LOCAL_TILE_SIZE);
			int cy = (int) Math.floor((y0 + (y1 - y0) * t) / Perspective.LOCAL_TILE_SIZE);
			if (cx == px && cy == py)
			{
				continue;
			}
			if (!canStep(px, py, cx, cy))
			{
				return false;
			}
			px = cx;
			py = cy;
		}
		return true;
	}

	/**
	 * One tile step is passable: both tiles walkable and no wall/fence edge
	 * between them. Diagonal steps require both cardinal corners open, like
	 * real movement, so nothing ever cuts through a fence corner.
	 */
	private boolean canStep(int ax, int ay, int bx, int by)
	{
		int dx = Integer.signum(bx - ax);
		int dy = Integer.signum(by - ay);
		if (dx == 0 && dy == 0)
		{
			return true;
		}
		if (dx != 0 && dy != 0)
		{
			return canStep(ax, ay, bx, ay) && canStep(bx, ay, bx, by)
				&& canStep(ax, ay, ax, by) && canStep(ax, by, bx, by);
		}
		if (!tileWalkable(ax, ay) || !tileWalkable(bx, by))
		{
			return false;
		}
		int fa = flagsAt(ax, ay);
		int fb = flagsAt(bx, by);
		if (dx == 1)
		{
			return (fa & CollisionDataFlag.BLOCK_MOVEMENT_EAST) == 0
				&& (fb & CollisionDataFlag.BLOCK_MOVEMENT_WEST) == 0;
		}
		if (dx == -1)
		{
			return (fa & CollisionDataFlag.BLOCK_MOVEMENT_WEST) == 0
				&& (fb & CollisionDataFlag.BLOCK_MOVEMENT_EAST) == 0;
		}
		if (dy == 1)
		{
			return (fa & CollisionDataFlag.BLOCK_MOVEMENT_NORTH) == 0
				&& (fb & CollisionDataFlag.BLOCK_MOVEMENT_SOUTH) == 0;
		}
		return (fa & CollisionDataFlag.BLOCK_MOVEMENT_SOUTH) == 0
			&& (fb & CollisionDataFlag.BLOCK_MOVEMENT_NORTH) == 0;
	}

	/** Collision-map check for a single scene tile on the current plane. */
	private boolean tileWalkable(int sceneX, int sceneY)
	{
		int f = flagsAt(sceneX, sceneY);
		int block = CollisionDataFlag.BLOCK_MOVEMENT_OBJECT
			| CollisionDataFlag.BLOCK_MOVEMENT_FLOOR
			| CollisionDataFlag.BLOCK_MOVEMENT_FLOOR_DECORATION;
		return f != -1 && (f & block) == 0;
	}

	/** Raw collision flags for a scene tile, or -1 (treat as blocked) when unavailable. */
	private int flagsAt(int sceneX, int sceneY)
	{
		CollisionData[] maps = client.getCollisionMaps();
		if (maps == null)
		{
			return -1;
		}
		int plane = client.getPlane();
		if (plane < 0 || plane >= maps.length || maps[plane] == null)
		{
			return -1;
		}
		int[][] flags = maps[plane].getFlags();
		if (sceneX < 0 || sceneY < 0 || sceneX >= flags.length || sceneY >= flags[sceneX].length)
		{
			return -1;
		}
		return flags[sceneX][sceneY];
	}

	// ------------------------------------------------------------------
	// Thrall impersonation
	// ------------------------------------------------------------------

	/**
	 * While a thrall is tracked it is hidden, and the ghost rides its exact
	 * position and facing — pathing, targeting, and turning are all the
	 * engine's own, so they stay native. The pet has no attack animation,
	 * so each time the thrall starts an action animation the ghost lunges
	 * forward and back instead.
	 */
	private void thrallTick(float dt)
	{
		LocalPoint tp = thrall.getLocalLocation();
		if (tp == null)
		{
			endThrall();
			return;
		}
		int plane = client.getPlane();

		float dx = tp.getX() - ghostX;
		float dy = tp.getY() - ghostY;
		float d = (float) Math.hypot(dx, dy);

		if (!thrallAttached)
		{
			if (d > Perspective.LOCAL_TILE_SIZE * 12f)
			{
				// The ghost is stranded far away (offscreen impersonation
				// loss, or a resummon while it rode the old thrall): jogging
				// across the map would leave the new thrall invisible with
				// nothing standing in — materialize on it instead. The
				// showGhost also un-hides a ghost hidden during the loss.
				ghostX = tp.getX();
				ghostY = tp.getY();
				showGhost(true);
			}
			else if (d > 6f)
			{
				// Jog over to take the thrall's place at a normal run pace
				// (not a jarring sprint), never teleport.
				showGhost(true);
				applyPose(POSE_RUN);
				float sp = Math.min(RUN_SPEED * dt, d);
				ghostX += dx / d * sp;
				ghostY += dy / d * sp;
				steer(jau(dx, dy), dt);
				place(new LocalPoint(Math.round(ghostX), Math.round(ghostY)), plane);
				prevThrallX = tp.getX();
				prevThrallY = tp.getY();
				return;
			}
			thrallAttached = true;
			prevThrallX = tp.getX();
			prevThrallY = tp.getY();
			thrallSpeed = 0f;
		}

		// Pose from the thrall's own movement speed.
		float moved = (float) Math.hypot(tp.getX() - prevThrallX, tp.getY() - prevThrallY);
		prevThrallX = tp.getX();
		prevThrallY = tp.getY();
		thrallSpeed += (moved / dt - thrallSpeed) * Math.min(1f, dt / 100f);

		if (thrallSpeed > 0.02f)
		{
			applyPose(thrallSpeed > RUN_SPEED * 0.7f ? POSE_RUN : POSE_WALK);
		}
		else
		{
			applyPose(POSE_IDLE);
		}

		ghostX = tp.getX();
		ghostY = tp.getY();
		int facing = thrall.getCurrentOrientation();
		ghost.setOrientation(facing);

		float ox = 0f;
		float oy = 0f;
		if (lungeMs > 0f)
		{
			lungeMs = Math.max(0f, lungeMs - dt);
			float k = (float) Math.sin((1f - lungeMs / LUNGE_MS) * Math.PI) * LUNGE_DIST;
			double rad = facing * Math.PI / 1024.0;
			ox = (float) -Math.sin(rad) * k;
			oy = (float) -Math.cos(rad) * k;
		}

		place(new LocalPoint(Math.round(ghostX + ox), Math.round(ghostY + oy)), plane);
	}

	/** Thrall done for good (expired/toggled off): jog back to the player's side. */
	private void endThrall()
	{
		thrall = null;
		thrallAttached = false;
		thrallSpeed = 0f;
		lungeMs = 0f;
		lastThrallProjectile = null;
		docking = false;
		impersonating = false;
		thrallLostTicks = 0;
		if (ghost != null)
		{
			reseedFromGhost();
		}
	}

	/**
	 * After a scene reload the thrall NPC is re-indexed; re-find it near the
	 * player so impersonation survives teleports/region changes. If it's no
	 * longer around it may just be a render blip, so keep impersonating and
	 * let the per-tick re-acquire (or timeout) handle it.
	 */
	private void reacquireThrall(Player me)
	{
		if (!config.thrallMode())
		{
			endThrall();
			return;
		}
		NPC t = findNearbyThrall(me, THRALL_REACQUIRE_RADIUS);
		if (t != null)
		{
			thrall = t;
			thrallAttached = false;
			lungeMs = 0f;
			thrallLostTicks = 0;
			armThrallDeadline();
			log.debug("Re-acquired thrall id={} after scene load", t.getId());
		}
	}

	/**
	 * Latest game tick this summon could still be alive: thralls last your
	 * Magic level in seconds (level * 5/3 ticks), plus a small buffer.
	 * Re-armed on every bind — an overestimate only delays the fallback
	 * release, never hides anything longer than THRALL_REACQUIRE_TICKS.
	 */
	private void armThrallDeadline()
	{
		int lvl = Math.max(1, client.getBoostedSkillLevel(net.runelite.api.Skill.MAGIC));
		thrallDeadlineTick = client.getTickCount() + lvl * 5 / 3 + 10;
	}

	/** Nearest tracked-type thrall within the given tile radius, or null. */
	private NPC findNearbyThrall(Player me, int radius)
	{
		for (NPC npc : client.getNpcs())
		{
			if (THRALL_IDS.contains(npc.getId())
				&& npc.getWorldLocation().distanceTo(me.getWorldLocation()) <= radius)
			{
				return npc;
			}
		}
		return null;
	}

	@Subscribe
	public void onNpcSpawned(NpcSpawned e)
	{
		if (!config.thrallMode() || thrall != null)
		{
			return;
		}
		Player me = client.getLocalPlayer();
		if (me == null)
		{
			return;
		}
		NPC npc = e.getNpc();
		if (!THRALL_IDS.contains(npc.getId()))
		{
			return;
		}
		// On first cast a thrall materializes right next to its caster, so a
		// tight radius avoids grabbing another player's thrall. When we're
		// re-acquiring one we already know is ours, accept it anywhere in
		// render range so it snaps back instantly on re-entering the scene.
		int radius = impersonating ? THRALL_REACQUIRE_RADIUS : THRALL_ACQUIRE_RADIUS;
		int dist = npc.getWorldLocation().distanceTo(me.getWorldLocation());
		if (dist > radius)
		{
			log.debug("Ignoring thrall {} spawned {} tiles away", npc.getId(), dist);
			return;
		}
		log.debug("Tracking thrall id={} at distance {} (impersonating={})", npc.getId(), dist, impersonating);
		thrall = npc;
		thrallAttached = false;
		lungeMs = 0f;
		docking = false;
		impersonating = true;
		thrallLostTicks = 0;
		armThrallDeadline();
	}

	/**
	 * Thrall attack: play the pet's own attack animation on the hidden pet
	 * (the ghost mirrors it), falling back to a lunge for pets without one.
	 * Note: magic thralls sometimes attack with no animation (projectile
	 * only), so ghostly thrall attacks may not always trigger this.
	 */
	@Subscribe
	public void onAnimationChanged(AnimationChanged e)
	{
		// Melee (zombified) and ranged (skeletal) thralls animate on attack.
		if (thrall != null && e.getActor() == thrall && thrall.getAnimation() != -1)
		{
			triggerPetAttack();
		}
	}

	@Subscribe
	public void onProjectileMoved(ProjectileMoved e)
	{
		// Everything downstream (attack anim, bolt tint, splash tint, the
		// damage icon) is pet flavor — a bare thrall fighting as itself
		// keeps its own colors and gets no icon.
		if (thrall == null || ghost == null)
		{
			return;
		}
		Projectile pr = e.getProjectile();
		// React the moment the projectile is REGISTERED (cast time), not when
		// it launches: the server creates it with startCycle a few cycles in
		// the future, and that gap is the caster's wind-up. Waiting for
		// startCycle == gameCycle (the old check) meant the bolt was already
		// flying before the pet began its swing. The event re-fires every
		// cycle the projectile exists, so dedupe by identity; the startCycle
		// bound rejects mid-flight projectiles that drifted over the tile.
		if (pr == lastThrallProjectile || pr.getStartCycle() < client.getGameCycle())
		{
			return;
		}
		LocalPoint tl = thrall.getLocalLocation();
		if (tl == null
			|| Math.abs(pr.getX1() - tl.getX()) > 64
			|| Math.abs(pr.getY1() - tl.getY()) > 64)
		{
			return;
		}

		lastThrallProjectile = pr;
		// Track regardless of the tint toggle: the flight's landing cycle
		// also attributes hitsplats for the pet damage icon.
		log.debug("Thrall projectile id={} start={} end={} now={}",
			pr.getId(), pr.getStartCycle(), pr.getEndCycle(), client.getGameCycle());
		tintedBolts.add(pr);
		triggerPetAttack();
	}

	/**
	 * The thrall attacked: play the pet's own attack animation on the hidden
	 * pet (the mirrored model reflects it over the movement pose), or lunge
	 * for pets that have no attack animation.
	 */
	private void triggerPetAttack()
	{
		if (pet == null || ghost == null)
		{
			return;
		}
		// One trigger per cast: a thrall that animates fires BOTH paths —
		// onAnimationChanged at cast, then the projectile at launch — and
		// the second call restarted the pet's attack animation mid-swing.
		// First signal wins. The window must beat the LONGEST anim-to-launch
		// gap: the skeletal thrall's arrow launches well over 900 ms after
		// its draw animation (the ghostly bolt is much quicker), which is
		// why the pet repeated its swing on skeleton casts only. Thralls
		// attack every 4 ticks (2400 ms), so 1800 ms cannot eat a real cast.
		long now = System.currentTimeMillis();
		if (now - lastPetAttackMs < 1800)
		{
			return;
		}
		lastPetAttackMs = now;
		int atkId = previewing ? activePreviewId : pet.getId();
		NPCComposition comp = previewing
			? client.getNpcDefinition(activePreviewId)
			: pet.getTransformedComposition();
		String name = comp != null ? comp.getName() : null;
		int atk = PetAttackAnims.forPet(atkId, name);
		log.debug("Thrall attack -> pet id={} name={} atk={}", atkId, name, atk);
		if (atk == -1)
		{
			lungeMs = LUNGE_MS;
		}
		else if (previewing && blipActive && blipObj != null)
		{
			// Preview renders through the stand-in: play the attack there
			// and hold off pose updates until it has had time to finish.
			blipObj.setAnimation(client.loadAnimation(atk));
			blipAnimId = -1;
			blipAttackUntil = System.currentTimeMillis() + 900;
		}
		else
		{
			pet.setAnimation(atk);
			pet.setAnimationFrame(0);
		}
	}

	// ------------------------------------------------------------------
	// Thrall projectile tint
	// ------------------------------------------------------------------

	/**
	 * Repaint each tracked projectile's live posed model to the config
	 * color, every rendered frame. The engine flies, animates, and draws
	 * its own bolt; we only touch the face colors of the model it is about
	 * to render. Reapplying is idempotent (hue/saturation are overwritten,
	 * luminance is preserved), so it doesn't matter whether the engine
	 * hands back a cached posed model or a freshly built one.
	 */
	private void updateTintedBolts()
	{
		if (tintedBolts.isEmpty() && splashTints.isEmpty())
		{
			return;
		}
		int cycle = client.getGameCycle();
		// Painting is gated on the toggle; flight/impact TRACKING is not,
		// because the pet damage icon attributes hitsplats from it.
		boolean paint = config.tintThrallBolt() && !tintFailed;
		short target = JagexColor.rgbToHSL(config.thrallBoltColor().getRGB(), 1.0d);
		int hue = JagexColor.unpackHue(target);
		int sat = JagexColor.unpackSaturation(target);
		int lum = JagexColor.unpackLuminance(target);
		// How achromatic the target is (black/grey/white have sat 0). For
		// those, hue carries nothing — the LUMINANCE is the color, so blend
		// the model's brightness toward it (scaled by its own shading).
		// Fully saturated picks keep the original luminance untouched,
		// which is the look that already read well.
		float achroma = 1f - sat / (float) JagexColor.SATURATION_MAX;
		try
		{
			for (Iterator<Projectile> it = tintedBolts.iterator(); it.hasNext(); )
			{
				Projectile pr = it.next();
				if (cycle >= pr.getEndCycle())
				{
					// Landing: the spotanim that starts on the victim at
					// this cycle is our impact splash — tint it too. The
					// hit-claim carries over from the in-flight phase.
					boolean claimed = claimedBolts.remove(pr);
					Actor victim = pr.getTargetActor();
					if (victim != null && splashTints.size() < 16)
					{
						splashTints.add(new SplashTint(victim, pr.getEndCycle(), claimed));
					}
					it.remove();
					continue;
				}
				Model m = paint ? pr.getModel() : null;
				if (m != null)
				{
					tintModel(m, hue, sat, lum, achroma);
				}
			}
			for (Iterator<SplashTint> it = splashTints.iterator(); it.hasNext(); )
			{
				SplashTint st = it.next();
				if (cycle > st.impactCycle + 250)
				{
					it.remove(); // splash long over (5 s hard cap)
					continue;
				}
				if (!paint)
				{
					continue;
				}
				for (ActorSpotAnim sa : st.target.getSpotAnims())
				{
					if (Math.abs(sa.getStartCycle() - st.impactCycle) <= 4)
					{
						Model m = sa.getModel();
						if (m != null)
						{
							tintModel(m, hue, sat, lum, achroma);
						}
					}
				}
			}
		}
		catch (Exception ex)
		{
			tintFailed = true;
			clearTintedBolts();
			log.debug("Thrall bolt tint disabled", ex);
		}
	}

	/**
	 * Move every face color's hue and saturation to the target while
	 * keeping its luminance (the shading) — the bolt keeps its exact shape,
	 * animation, and shading, just in the new color. Face-color conventions
	 * from the engine: faceColors3 == -2 means the face is hidden, -1 means
	 * flat shading (only faceColors1 is used). Idempotent, so per-frame
	 * reapplication is safe. Originals are snapshotted for restore.
	 */
	private void tintModel(Model m, int hue, int sat, int lum, float achroma)
	{
		int[] c1 = m.getFaceColors1();
		int[] c2 = m.getFaceColors2();
		int[] c3 = m.getFaceColors3();
		// ALWAYS recompute from the snapshotted originals, never from the
		// current (already-tinted) colors: the luminance blend is not
		// idempotent, so cumulative per-frame reapplication decays every
		// not-fully-saturated color to near-black within one flight.
		TintSnapshot snap = tintOriginals.get(c1);
		if (snap == null)
		{
			if (tintOriginals.size() >= 128)
			{
				return; // snapshot budget exhausted; leave this model alone
			}
			snap = new TintSnapshot(c1, c2, c3);
			tintOriginals.put(c1, snap);
		}
		for (int i = 0; i < c1.length; i++)
		{
			int f3 = snap.o3[i];
			if (f3 == -2)
			{
				continue;
			}
			c1[i] = tintHsl(snap.o1[i], hue, sat, lum, achroma);
			if (f3 != -1)
			{
				c2[i] = tintHsl(snap.o2[i], hue, sat, lum, achroma);
				c3[i] = tintHsl(f3, hue, sat, lum, achroma);
			}
		}
	}

	/** One packed HSL value re-hued; see tintModel for the luminance rule. */
	private static int tintHsl(int hsl, int hue, int sat, int lum, float achroma)
	{
		int lo = JagexColor.unpackLuminance((short) hsl);
		if (achroma > 0f)
		{
			// Target luminance carrying the model's own shading pattern.
			int scaled = Math.min(JagexColor.LUMINANCE_MAX, lum * lo / 96);
			lo = Math.round(lo * (1f - achroma) + scaled * achroma);
		}
		return JagexColor.packHSL(hue, sat, lo) & 0xFFFF;
	}

	/** Un-tint every repainted model and stop tracking bolts and splashes. */
	private void clearTintedBolts()
	{
		for (TintSnapshot s : tintOriginals.values())
		{
			System.arraycopy(s.o1, 0, s.a1, 0, s.o1.length);
			System.arraycopy(s.o2, 0, s.a2, 0, s.o2.length);
			System.arraycopy(s.o3, 0, s.a3, 0, s.o3.length);
		}
		tintOriginals.clear();
		tintedBolts.clear();
		claimedBolts.clear();
		splashTints.clear();
		petHits.clear();
	}

	// ------------------------------------------------------------------
	// Pet damage icon
	// ------------------------------------------------------------------

	/**
	 * A hitsplat landing on a tracked projectile's victim within ~0.7 s of
	 * the landing cycle is the pet's damage. (The player hitting the same
	 * target in that same instant would also match — cosmetic and rare.)
	 */
	@Subscribe
	public void onHitsplatApplied(HitsplatApplied e)
	{
		if (thrall == null && !impersonating && tintedBolts.isEmpty() && splashTints.isEmpty())
		{
			return;
		}
		// Slot bookkeeping for EVERY hitsplat on actors we might tag — the
		// player's own damage occupies slots too, and our icon's position
		// depends on which slot OUR hit ends up in.
		int cycle = client.getGameCycle();
		int[] slots = hitSlots.computeIfAbsent(e.getActor(), a -> new int[4]);
		int slot = -1;
		for (int i = 0; i < 4; i++)
		{
			if (slots[i] <= cycle)
			{
				slot = i;
				break;
			}
		}
		if (slot == -1)
		{
			slot = 0;
			for (int i = 1; i < 4; i++)
			{
				if (slots[i] < slots[slot])
				{
					slot = i;
				}
			}
		}
		slots[slot] = e.getHitsplat().getDisappearsOnGameCycle();

		if (!config.petHitIcon() || petIcon == null)
		{
			return;
		}
		if (e.getHitsplat().getAmount() > THRALL_MAX_HIT)
		{
			return; // can't be the thrall's damage
		}
		boolean ours = false;
		for (SplashTint st : splashTints)
		{
			if (!st.hitClaimed && st.target == e.getActor() && Math.abs(cycle - st.impactCycle) <= 35)
			{
				st.hitClaimed = true;
				ours = true;
				break;
			}
		}
		if (!ours)
		{
			for (Projectile pr : tintedBolts)
			{
				if (!claimedBolts.contains(pr) && pr.getTargetActor() == e.getActor()
					&& Math.abs(cycle - pr.getEndCycle()) <= 35)
				{
					claimedBolts.add(pr);
					ours = true;
					break;
				}
			}
		}
		if (ours && petHits.size() < 8)
		{
			// The icon lives and dies with its hitsplat, to the cycle.
			petHits.add(new PetHit(e.getActor(), e.getHitsplat().getDisappearsOnGameCycle(), slot));
		}
	}

	/**
	 * Resolve the displayed pet's inventory-item sprite (see the field
	 * comment for the matching rules). The one-time name scan runs on the
	 * client thread and is persisted per pet, misses included.
	 */
	private void resolvePetIcon(int npcId)
	{
		if (npcId == petIconKeyId)
		{
			return;
		}
		petIconKeyId = npcId;
		petIcon = null;
		Integer saved = configManager.getConfiguration("petfollower", "petItem_" + npcId, Integer.class);
		int itemId = saved != null ? saved : findPetItem(displayedPetName());
		if (saved == null)
		{
			configManager.setConfiguration("petfollower", "petItem_" + npcId, itemId);
		}
		if (itemId >= 0)
		{
			petIcon = itemManager.getImage(itemId);
		}
	}

	/** Scan item names for the pet's item: exact match, else longest contained. */
	private int findPetItem(String petName)
	{
		String want = petName.toLowerCase();
		int best = -1;
		int bestLen = 3; // ignore trivially short names
		for (int i = 0; i < 30500; i++)
		{
			try
			{
				ItemComposition ic = itemManager.getItemComposition(i);
				String n = ic != null ? ic.getName() : null;
				if (n == null || "null".equalsIgnoreCase(n))
				{
					continue;
				}
				if (n.equalsIgnoreCase(petName))
				{
					return i;
				}
				if (n.length() > bestLen && want.contains(n.toLowerCase()))
				{
					best = i;
					bestLen = n.length();
				}
			}
			catch (Exception ex)
			{
				// invalid id — skip
			}
		}
		log.debug("Pet icon for '{}' -> item {}", petName, best);
		return best;
	}

	/** Draws the pet icon beside its recent hitsplats. */
	private class PetHitOverlay extends Overlay
	{
		PetHitOverlay()
		{
			setPosition(OverlayPosition.DYNAMIC);
			setLayer(OverlayLayer.ABOVE_SCENE);
		}

		@Override
		public Dimension render(Graphics2D g)
		{
			if (petHits.isEmpty() || petIcon == null || !config.petHitIcon())
			{
				return null;
			}
			int cycle = client.getGameCycle();
			for (Iterator<PetHit> it = petHits.iterator(); it.hasNext(); )
			{
				PetHit ph = it.next();
				if (cycle >= ph.untilCycle)
				{
					it.remove();
					continue;
				}
				try
				{
					// Our hitsplat's rank among the ACTIVE slots gives how
					// many splats sit under it; the icon's midline lands on
					// that splat's midline (anchor minus rank spacings).
					int rank = 0;
					int[] slots = hitSlots.get(ph.target);
					if (slots != null)
					{
						for (int i = 0; i < ph.slot; i++)
						{
							if (slots[i] > cycle)
							{
								rank++;
							}
						}
					}
					net.runelite.api.Point p = ph.target.getCanvasImageLocation(
						petIcon, ph.target.getLogicalHeight() / 2);
					if (p != null)
					{
						int cx = p.getX() + petIcon.getWidth() / 2;
						int cy = p.getY() + petIcon.getHeight() / 2;
						int w = Math.max(8, config.petHitIconSize());
						int h = w * 22 / 26;
						g.drawImage(petIcon, cx + 12, cy - rank * HIT_SLOT_SPACING - h / 2, w, h, null);
					}
				}
				catch (Exception ex)
				{
					it.remove(); // actor gone mid-render
				}
			}
			return null;
		}
	}

	@Subscribe
	public void onMenuOptionClicked(MenuOptionClicked e)
	{
		if (!config.callFollowerInteract())
		{
			return;
		}
		String opt = e.getMenuOption();
		if (opt == null)
		{
			return;
		}
		String o = opt.toLowerCase();
		if (o.contains("call") && o.contains("follower"))
		{
			// Show the real pet so it's right-clickable, and don't re-ghost it
			// for a few seconds so you can interact.
			handBackNow();
			suppressTakeoverTicks = 8;
			log.debug("Call Follower: handed back for interaction");
		}
	}

	/** Immediately reveal the real pet (end any ghosting/impersonation). */
	private void handBackNow()
	{
		if (impersonating)
		{
			endThrall();
		}
		releasePet();
		hidingReal = false;
		docking = false;
		despawnGhost();
	}

	/**
	 * Persist the true pose-animation table captured from a real pet, keyed
	 * by both NPC id and (sanitized) name so every id variant of the same
	 * pet benefits. Transmogs prefer this over the imported table.
	 */
	private void learnPetAnims()
	{
		if (pet == null || origWalk <= 0)
		{
			return;
		}
		String val = origIdle + "," + origWalk + "," + origRun;
		configManager.setConfiguration("petfollower", "anims_" + pet.getId(), val);
		NPCComposition comp = pet.getTransformedComposition();
		String name = comp != null ? comp.getName() : null;
		if (name != null && !name.isEmpty())
		{
			configManager.setConfiguration("petfollower", "anims_name_" + sanitizeKey(name), val);
		}
	}

	/** {idle, walk, run} learned from a real sighting of this pet, or null. */
	private int[] learnedAnims(int npcId, String name)
	{
		String v = configManager.getConfiguration("petfollower", "anims_" + npcId);
		if (v == null && name != null && !name.isEmpty())
		{
			v = configManager.getConfiguration("petfollower", "anims_name_" + sanitizeKey(name));
		}
		if (v == null)
		{
			return null;
		}
		try
		{
			String[] p = v.split(",");
			return new int[]{Integer.parseInt(p[0]), Integer.parseInt(p[1]), Integer.parseInt(p[2])};
		}
		catch (Exception ex)
		{
			return null;
		}
	}

	private static String sanitizeKey(String s)
	{
		return s.toLowerCase().replaceAll("[^a-z0-9]", "_");
	}

	private int poseFor(int state)
	{
		switch (state)
		{
			case POSE_WALK:
				return poseWalk;
			case POSE_RUN:
				return poseRun != -1 ? poseRun : poseWalk;
			default:
				return poseIdle;
		}
	}

	/**
	 * Position the ghost at ghostArc along the recorded polyline, dropping
	 * path points it has already passed, and steer it along the segment.
	 */
	private void moveGhostToArc(float dt)
	{
		// Drop points the ghost has fully passed, always keeping the point
		// just behind it so the bracket [a, b] straddles ghostArc.
		while (true)
		{
			PathPoint second = secondOf(path);
			if (second == null || second.cum > ghostArc)
			{
				break;
			}
			path.pollFirst();
		}

		PathPoint a = path.peekFirst();
		PathPoint b = secondOf(path);
		if (a == null)
		{
			return;
		}

		float nx;
		float ny;
		float dirX = 0f;
		float dirY = 0f;
		if (b == null || b.cum <= a.cum)
		{
			nx = a.x;
			ny = a.y;
		}
		else
		{
			float f = (ghostArc - a.cum) / (b.cum - a.cum);
			f = Math.max(0f, Math.min(1f, f));
			nx = a.x + (b.x - a.x) * f;
			ny = a.y + (b.y - a.y) * f;
			dirX = b.x - a.x;
			dirY = b.y - a.y;
		}

		// Pure pursuit: aim at a point LOOKAHEAD units further along the
		// path rather than this tiny segment, turning corners into arcs.
		float lx = nx;
		float ly = ny;
		{
			float lookArc = ghostArc + LOOKAHEAD;
			PathPoint prev = a;
			boolean found = false;
			for (PathPoint pp : path)
			{
				if (pp.cum >= lookArc)
				{
					float span = pp.cum - prev.cum;
					float f2 = span <= 0f ? 1f : (lookArc - prev.cum) / span;
					lx = prev.x + (pp.x - prev.x) * f2;
					ly = prev.y + (pp.y - prev.y) * f2;
					found = true;
					break;
				}
				prev = pp;
			}
			if (!found)
			{
				// Path doesn't extend that far (slowing to a stop): aim at
				// its end.
				lx = prev.x;
				ly = prev.y;
			}
		}

		float ldx = lx - nx;
		float ldy = ly - ny;
		if (Math.hypot(ldx, ldy) > 4)
		{
			int desired = jau(ldx, ldy);

			if (config.alwaysFaceOwner() && pathSmoothInit)
			{
				// Full owner-lock: the body points at you even mid-stride.
				// Aimed at the SMOOTHED position so sidesteps can't twitch it.
				desired = jau(pathSmoothX - nx, pathSmoothY - ny);
			}
			else if (pathSmoothInit)
			{
				// Lean the facing a little toward the player so the pet reads
				// as watching you through the turn (capped: never strafes).
				// Aim the lean at the SMOOTHED player position: leaning at the
				// raw position twitched the whole body the instant you sidestep,
				// which read as the pet reacting inhumanly fast.
				int toPlayer = jau(pathSmoothX - nx, pathSmoothY - ny);
				int bias = (int) (wrapAngle(toPlayer - desired) * PLAYER_BIAS);
				bias = Math.max(-PLAYER_BIAS_CAP, Math.min(PLAYER_BIAS_CAP, bias));
				desired = (desired + bias) & 2047;
			}
			desiredHeading = desired;

			int diff = wrapAngle(desired - ghost.getOrientation());
			if (Math.abs(diff) > TURN_DEADBAND)
			{
				steer(desired, dt);
			}
			else
			{
				turnV *= 0.6f;
			}
		}

		ghostX = nx;
		ghostY = ny;
		place(new LocalPoint(Math.round(nx), Math.round(ny)), pathPlane);
	}

	/**
	 * A scene reload shifted the local coordinate origin. Translate ALL of
	 * the ghost's local-space state into the new frame so it glides through
	 * loading lines mid-stride — speed, route, facing, and rest state all
	 * untouched. A big world jump at the same moment is a real teleport
	 * instead: clean repark (gliding across the world map would be worse),
	 * resuming at speed rather than with the standing-start beat.
	 */
	private void applySceneShift()
	{
		if (ghost == null)
		{
			pendingShiftFrom = null;
			return;
		}
		Player me = client.getLocalPlayer();
		WorldPoint now = me != null ? me.getWorldLocation() : null;
		// Compare against the position captured when loading STARTED (see
		// pendingShiftFrom). Different planes yield MAX_VALUE, so ladders
		// and stairs that reload the scene classify as teleports and repark.
		int jump = now != null && pendingShiftFrom != null
			? pendingShiftFrom.distanceTo(now)
			: Integer.MAX_VALUE;
		pendingShiftFrom = null;
		if (jump > 10)
		{
			reparkAfterTeleport();
			return;
		}

		float dx = -(client.getBaseX() - lastBaseX) * (float) Perspective.LOCAL_TILE_SIZE;
		float dy = -(client.getBaseY() - lastBaseY) * (float) Perspective.LOCAL_TILE_SIZE;
		if (dx != 0f || dy != 0f)
		{
			ghostX += dx;
			ghostY += dy;
			pathSmoothX += dx;
			pathSmoothY += dy;
			restX += dx;
			restY += dy;
			Deque<PathPoint> shifted = new ArrayDeque<>(path.size());
			for (PathPoint pp : path)
			{
				shifted.addLast(new PathPoint(pp.x + dx, pp.y + dy, pp.cum));
			}
			path.clear();
			path.addAll(shifted);
		}

		// Belt and braces: if the translation left the ghost far from you
		// (or off the scene grid entirely), the load was NOT a simple
		// walk-crossing — whatever the classifier thought. Repark rather
		// than strand an invisible ghost that later slingshots in.
		LocalPoint mp = me != null ? me.getLocalLocation() : null;
		if (mp == null
			|| Math.hypot(mp.getX() - ghostX, mp.getY() - ghostY) > 15 * Perspective.LOCAL_TILE_SIZE)
		{
			reparkAfterTeleport();
			return;
		}

		// Registration can be scene-scoped: cycle it, then reseat on the
		// new scene's terrain.
		if (ghostShown)
		{
			client.removeRuneLiteObject(ghost);
			client.registerRuneLiteObject(ghost);
		}
		pathPlane = client.getPlane();
		place(new LocalPoint(Math.round(ghostX), Math.round(ghostY)), pathPlane);
		if (blipActive)
		{
			syncBlip();
		}
	}

	/**
	 * A real teleport (or anything that looks like one): tear the follow
	 * state down cleanly; the next takeover reparks the ghost behind you,
	 * setting off at speed instead of the standing-start beat.
	 */
	private void reparkAfterTeleport()
	{
		resumeAtSpeed = hidingReal;
		releasePet();
		despawnGhost();
		hidingReal = false;
	}

	/**
	 * Restart the path after a teleport/plane change: park the ghost right
	 * behind your rendered position, facing the same way you do.
	 */
	private void resetPathBehind(LocalPoint p, int plane)
	{
		Player me = client.getLocalPlayer();
		int facing = me != null ? me.getCurrentOrientation() : 0;
		// Park one tile behind the direction you're facing, with the path
		// seeded [ghost, you] so the arc bookkeeping stays consistent.
		double rad = facing * Math.PI / 1024.0;
		ghostX = p.getX() + (float) (Math.sin(rad) * gap);
		ghostY = p.getY() + (float) (Math.cos(rad) * gap);

		path.clear();
		pathPlane = plane;
		path.addLast(new PathPoint(ghostX, ghostY, 0f));
		path.addLast(new PathPoint(p.getX(), p.getY(), gap));
		playerCum = gap;
		playerSpeed = 0f;
		ghostArc = 0f;
		ghostV = 0f;
		pathSmoothInit = false;

		if (ghost != null)
		{
			ghost.setOrientation(facing);
			place(new LocalPoint(Math.round(ghostX), Math.round(ghostY)), plane);
		}
		turnV = 0f;
		desiredHeading = facing;
		applyPose(POSE_IDLE);
	}

	private static PathPoint secondOf(Deque<PathPoint> deque)
	{
		java.util.Iterator<PathPoint> it = deque.iterator();
		if (!it.hasNext())
		{
			return null;
		}
		it.next();
		return it.hasNext() ? it.next() : null;
	}

	// ------------------------------------------------------------------
	// Ghost lifecycle
	// ------------------------------------------------------------------

	/**
	 * Renders exactly what the hidden pet NPC renders this frame:
	 * NPC.getModel() is already posed, scaled, and recolored by the engine,
	 * so the ghost is pixel-identical to the real pet — only its position and
	 * facing are ours. When the real pet isn't being drawn (reload gap) the
	 * ghost is despawned rather than drawing this (recycled) buffer.
	 */
	private final class Ghost extends RuneLiteObjectController
	{
		private Model lastModel;

		Ghost(Model initial)
		{
			lastModel = initial;
		}

		@Override
		public Model getModel()
		{
			NPC p = pet;
			if (p != null)
			{
				Model m = p.getModel();
				if (m != null)
				{
					// The normal NPC draw path computes bounds before face
					// sorting; doing it here keeps the triangle sort stable
					// (otherwise a blur-like shimmer).
					m.calculateBoundsCylinder();
					setRadius(m.getRadius());
					lastModel = m;
					return m;
				}
			}
			if (fallbackModel != null)
			{
				// The real NPC is momentarily gone (teleport-respawn blip):
				// draw our own cache-built copy — never the recycled live
				// buffer, which flashes other actors' geometry.
				setRadius(fallbackModel.getRadius());
				return fallbackModel;
			}
			return lastModel;
		}
	}

	/**
	 * Build our own copy of the pet's model from the game cache: the pet's
	 * definition lists its model parts, recolors, and exact resize values
	 * (128 = 1.0x), so the copy matches the real pet without guessing at
	 * scale. Drawn only while the real NPC is momentarily absent.
	 */
	private void buildFallbackModel()
	{
		int wantId = previewing ? activePreviewId : (pet != null ? pet.getId() : -1);
		if (wantId == -1 || (fallbackForId == wantId && fallbackModel != null))
		{
			return;
		}
		fallbackModel = null;
		fallbackForId = wantId;

		NPCComposition comp = previewing
			? client.getNpcDefinition(activePreviewId)
			: pet.getTransformedComposition();
		if (comp == null)
		{
			return;
		}
		int[] ids = comp.getModels();
		if (ids == null || ids.length == 0)
		{
			return;
		}
		ModelData[] parts = new ModelData[ids.length];
		for (int i = 0; i < ids.length; i++)
		{
			parts[i] = client.loadModelData(ids[i]);
			if (parts[i] == null)
			{
				return;
			}
		}
		// ALWAYS merge, even a single part: mergeModels emits a fresh,
		// animation-ready copy (labels intact) that's safe to mutate. Never
		// touch the shared cache entries directly, and never reassign
		// through the clone chain — a chained clone loses the animation
		// skeleton mapping, and animating it shreds the vertices (the
		// black exploded-geometry glitch). This mirrors exactly how the
		// battle-tested Companion Pets plugin builds its models.
		ModelData md = client.mergeModels(parts);
		if (md == null)
		{
			return;
		}

		short[] find = comp.getColorToReplace();
		short[] repl = comp.getColorToReplaceWith();
		if (find != null && repl != null)
		{
			int n = Math.min(find.length, repl.length);
			for (int i = 0; i < n; i++)
			{
				md.recolor(find[i], repl[i]);
			}
		}

		// NO pre-scaling: animating a pre-scaled model distorts resize pets
		// (animation offsets are authored for the full-size skeleton). The
		// def's resize is applied per-frame to the ANIMATED output instead,
		// in the blip object's getModel() override.
		fbWidthScale = comp.getWidthScale();
		fbHeightScale = comp.getHeightScale();

		// NPC-style lighting (64 ambient / 850 contrast, standard light
		// vector) as the baseline...
		Model m = md.light(64, 850, -30, -50, -30);
		if (m == null)
		{
			return;
		}

		// ...then copy the EXACT per-face colors from the live engine-lit
		// model. Every NPC has its own ambient/contrast in its definition
		// (not exposed through the API), so any fixed lighting numbers are
		// wrong for someone — but the engine already lit the real pet
		// perfectly, and our stand-in is built from the same parts in the
		// same order, so the face arrays line up one-to-one. Works for
		// every pet with zero per-pet tuning. (Not possible for previews —
		// there's no live model of a pet you don't own — so previews use
		// the baseline lighting, which can read slightly flat.)
		if (!previewing && pet != null)
		{
			Model live = pet.getModel();
			if (live != null)
			{
				copyFaceColors(live.getFaceColors1(), m.getFaceColors1());
				copyFaceColors(live.getFaceColors2(), m.getFaceColors2());
				copyFaceColors(live.getFaceColors3(), m.getFaceColors3());
			}
		}

		m.calculateBoundsCylinder();
		fallbackModel = m;
	}

	private static void copyFaceColors(int[] src, int[] dst)
	{
		if (src != null && dst != null && src.length == dst.length)
		{
			System.arraycopy(src, 0, dst, 0, dst.length);
		}
	}

	private void spawnGhost(Player me)
	{
		despawnGhost();
		if (pet == null)
		{
			return;
		}

		Model m = pet.getModel();
		LocalPoint petPos = pet.getLocalLocation();
		LocalPoint playerPos = me.getLocalLocation();
		if (m == null || petPos == null || playerPos == null)
		{
			hidingReal = false;
			return; // not renderable yet; retry next takeover
		}

		int plane = client.getPlane();
		path.clear();
		pathPlane = plane;
		// Seed the path with the leg from the pet's current spot to you, so
		// the ghost visibly runs up from where the real pet stood.
		float d = (float) Math.hypot(playerPos.getX() - petPos.getX(), playerPos.getY() - petPos.getY());
		path.addLast(new PathPoint(petPos.getX(), petPos.getY(), 0f));
		if (d > 1f)
		{
			path.addLast(new PathPoint(playerPos.getX(), playerPos.getY(), d));
		}
		playerCum = d;
		playerSpeed = 0f;
		ghostArc = 0f;
		ghostV = 0f;
		if (resumeAtSpeed)
		{
			// Interrupted mid-run: appear already walking. A nonzero speed
			// bypasses the standing-start reaction gate, and the normal
			// speed curve takes over from the very next frame (it also
			// eases straight back to a stop if you actually stood still).
			ghostV = WALK_SPEED;
			resumeAtSpeed = false;
		}
		turnV = 0f;
		lastFrameNanos = System.nanoTime();
		ghostX = petPos.getX();
		ghostY = petPos.getY();

		ensureHijack();
		poseState = -1;
		buildFallbackModel();
		NPCComposition comp = previewing
			? client.getNpcDefinition(activePreviewId)
			: pet.getTransformedComposition();
		ghostSize = comp != null ? comp.getSize() : 1;
		// Bigger/longer pets sit further back at rest so they don't clip into
		// the player when idle (Scorpia's offspring, Callisto cub, etc.).
		gap = GAP + (ghostSize - 1) * 96f + IDLE_PUSHBACK;

		ghost = new Ghost(m);

		// If we're (re)spawning while impersonating a thrall — e.g. after a
		// teleport — appear directly on the thrall already "attached", so it
		// doesn't jog/rubberband across to it on reappear.
		LocalPoint thrallPos = thrall != null ? thrall.getLocalLocation() : null;
		if (thrallPos != null)
		{
			ghostX = thrallPos.getX();
			ghostY = thrallPos.getY();
			prevThrallX = thrallPos.getX();
			prevThrallY = thrallPos.getY();
			thrallAttached = true;
			place(thrallPos, plane);
			ghost.setOrientation(thrall.getCurrentOrientation());
			client.registerRuneLiteObject(ghost);
			ghostShown = true;
			return;
		}

		// The real pet pops in at your server-side tile, which can be ahead
		// of your rendered character or far behind it. Spawning the ghost
		// there looks wrong (falls back from in front / sprints up from way
		// back). If the pet materialized in front of you or far away, appear
		// directly at the proper follow position behind you — like vanilla
		// pets teleporting in. Only a close, behind-you pet (the normal
		// standing-start takeover) is used as the spawn point for the
		// seamless swap.
		double rad = me.getCurrentOrientation() * Math.PI / 1024.0;
		float fx = -(float) Math.sin(rad);
		float fy = -(float) Math.cos(rad);
		float relX = petPos.getX() - playerPos.getX();
		float relY = petPos.getY() - playerPos.getY();
		float ahead = relX * fx + relY * fy;
		boolean moving = idleTicks == 0;

		client.registerRuneLiteObject(ghost);
		ghostShown = true;

		if (d > 2.5f * Perspective.LOCAL_TILE_SIZE || (moving && ahead > 48f))
		{
			resetPathBehind(playerPos, plane);
			return;
		}

		place(petPos, plane);
		ghost.setOrientation(pet.getOrientation());
		desiredHeading = pet.getOrientation();
	}

	/** Activate/deactivate the animated blip stand-in. */
	private void showBlip(boolean show)
	{
		if (show)
		{
			if (blipObj == null)
			{
				// Companion Pets' proven pattern: apply the def's resize to
				// the ANIMATED output each frame (never to the model data),
				// so resize pets animate cleanly at the right size.
				blipObj = new RuneLiteObject(client)
				{
					@Override
					public Model getModel()
					{
						Model m = super.getModel();
						if (m != null && (fbWidthScale != 128 || fbHeightScale != 128))
						{
							m = m.scale(fbWidthScale, fbHeightScale, fbWidthScale);
						}
						return m;
					}
				};
				blipObj.setShouldLoop(true);
			}
			if (!blipActive)
			{
				blipObj.setModel(fallbackModel);
				blipModelApplied = fallbackModel;
				blipActive = true;
				// Note: blipAnimId is NOT reset — rapid blip on/off chains
				// (outrunning the pet) keep the same animation running
				// instead of restarting it each time.
				updateBlipAnim();
				syncBlipPhase();
				syncBlip();
				blipObj.setActive(true);
			}
			else if (blipModelApplied != fallbackModel && fallbackModel != null)
			{
				// The cache model was rebuilt while the stand-in was live
				// (transmog switch): push the new model AND its own anims.
				// Leaving the old model up while the new pet's animation
				// played on it warped it into a morphed hybrid.
				blipObj.setModel(fallbackModel);
				blipModelApplied = fallbackModel;
				blipAnimId = -1;
				updateBlipAnim();
			}
		}
		else if (blipActive)
		{
			blipObj.setActive(false);
			blipActive = false;
		}
	}

	/** Hand the mirror's current animation phase to the stand-in. */
	private void syncBlipPhase()
	{
		AnimationController ac = blipObj != null ? blipObj.getAnimationController() : null;
		Animation anim = ac != null ? ac.getAnimation() : null;
		if (anim != null && lastPoseFrame > 0 && lastPoseFrame < anim.getNumFrames())
		{
			ac.setFrame(lastPoseFrame);
		}
	}

	/** Keep the stand-in on the ghost's position, height, and facing. */
	private void syncBlip()
	{
		if (!blipActive || blipObj == null || ghost == null)
		{
			return;
		}
		LocalPoint at = new LocalPoint(Math.round(ghostX), Math.round(ghostY));
		blipObj.setLocation(at, pathPlane);
		blipObj.setZ(Perspective.getFootprintTileHeight(client, at, pathPlane, ghostSize)
			- config.groundClearance());
		blipObj.setOrientation(ghost.getOrientation());

		// Speed-up for the stand-in: feed extra time into the controller's
		// OWN clock so it advances through its native duration logic —
		// smooth, even pacing. Frame-index pushes (setFrame) desynced its
		// internal timing and produced the transmog-only hitching.
		long nnBlip = System.nanoTime();
		float bdt = blipClockNanos == 0 ? 0f : Math.min(100f, (nnBlip - blipClockNanos) / 1_000_000f);
		blipClockNanos = nnBlip;
		float factor = cadenceFactor();
		if (cadenceEligible(factor))
		{
			AnimationController ac = blipObj.getAnimationController();
			if (ac != null)
			{
				blipExtraMs += (factor - 1f) * bdt;
				int extraTicks = (int) (blipExtraMs / 20f);
				if (extraTicks > 0)
				{
					blipExtraMs -= extraTicks * 20f;
					ac.tick(extraTicks);
				}
			}
		}
		else
		{
			blipExtraMs = 0f;
		}
	}

	/** Play the pose animation matching the ghost's current motion. */
	private void updateBlipAnim()
	{
		if (!blipActive || blipObj == null)
		{
			return;
		}
		// An attack is playing on the stand-in (preview mode): let it finish
		// before the pose animation takes the channel back.
		if (System.currentTimeMillis() < blipAttackUntil)
		{
			return;
		}
		// Follow the SAME smoothed + dwell-debounced pose state machine as
		// the mirror. Computing a state here from raw ghostV re-created the
		// exact threshold-flapping bug the mirror was cured of: every flap
		// called setAnimation (= restart at frame 0), so TRANSMOGS
		// stutter-looped while real pets looked perfect — the stand-in is
		// all you see in preview.
		if (poseState == -1)
		{
			return;
		}
		int id = poseFor(poseState);
		if (id != -1 && id != blipAnimId)
		{
			blipAnimId = id;
			blipObj.setAnimation(client.loadAnimation(id));
			// Pick the cycle up where the mirrored pet left off — every
			// blip restarting the stride at frame 0 was the visible
			// "feet reset" while outrunning the pet.
			syncBlipPhase();
		}
	}

	/** Register/unregister the ghost so it stops drawing without losing state. */
	private void showGhost(boolean show)
	{
		if (ghost == null || show == ghostShown)
		{
			return;
		}
		if (show)
		{
			client.registerRuneLiteObject(ghost);
		}
		else
		{
			client.removeRuneLiteObject(ghost);
		}
		ghostShown = show;
	}

	private void despawnGhost()
	{
		showBlip(false);
		if (ghost != null)
		{
			client.removeRuneLiteObject(ghost);
			ghost = null;
		}
		ghostShown = false;
		path.clear();
		playerCum = 0f;
		playerSpeed = 0f;
		ghostArc = 0f;
		ghostV = 0f;
		turnV = 0f;
		poseState = -1;
		docking = false;
		settling = false;
		restDone = false;
		pathSmoothInit = false;
	}

	private void place(LocalPoint at, int plane)
	{
		ghost.setLocation(at, plane);
		// Seat the ghost on the terrain the same way the engine seats the
		// real NPC: footprint-averaged height, so wide flat pets (Scorpia's
		// offspring etc.) don't sink into slopes. Uses the size captured at
		// spawn so it stays correct even while the pet is a render blip.
		// Negative Z is up; the clearance lifts base-level glows/auras just
		// above the terrain plane so the ground can't swallow them.
		int z = Perspective.getFootprintTileHeight(client, at, plane, ghostSize)
			- config.groundClearance();
		ghost.setZ(z);
		if (blipActive && blipObj != null)
		{
			blipObj.setLocation(at, plane);
			blipObj.setZ(z);
			blipObj.setOrientation(ghost.getOrientation());
			updateBlipAnim();
		}
	}

	/**
	 * Make the ghost's mirrored model play exactly one animation at native
	 * speed: rewrite the hidden pet's pose-animation TABLE (idle/walk/run
	 * all point at the id the ghost needs). The engine re-decides the pose
	 * whenever the real pet moves, but every choice lands on the same id, so
	 * the cycle is never reset — overriding pose/frame after the fact
	 * (previous approach) restarted or fast-forwarded the animation, which
	 * showed as sped-up, jittery playback.
	 */
	private void applyPose(int state)
	{
		applyPose(state, 0);
	}

	private void applyPose(int state, int frame)
	{
		if (pet == null || state == poseState)
		{
			return;
		}
		int id = poseFor(state);
		if (id == -1)
		{
			return;
		}
		poseState = state;
		pet.setIdlePoseAnimation(id);
		pet.setWalkAnimation(id);
		pet.setRunAnimation(id);
		// Only restart the cycle when the ANIMATION actually changes. A
		// state change that resolves to the same id (many pets share one
		// walk/run animation — the beaver's walk and run are both 10010)
		// must never touch the frame: resetting it on every borderline
		// walk<->run flip was the visible quarter-second animation-restart
		// rhythm while running.
		if (pet.getPoseAnimation() != id)
		{
			pet.setPoseAnimation(id);
			pet.setPoseAnimationFrame(frame);
		}
	}

	/**
	 * Adopt a fresh pet NPC instance (server teleport-respawn or scene
	 * reload) without any visible animation restart: re-capture its factory
	 * anim table, resume the pose MID-CYCLE (taking the phase from the blip
	 * stand-in if it was carrying it), and cancel the spawn/materialize
	 * action animation so it never plays on the mirror. Used by BOTH
	 * re-link paths — having the game-tick path skip these steps was the
	 * "run animation repeats while outrunning the pet" bug.
	 */
	private void relinkPet(NPC found)
	{
		int prevState = poseState;
		animsHijacked = false;
		pet = found;
		ensureHijack();
		buildFallbackModel();
		if (prevState != -1)
		{
			if (blipActive && blipObj != null && blipObj.getAnimationController() != null)
			{
				lastPoseFrame = blipObj.getAnimationController().getFrame();
			}
			applyPose(prevState, lastPoseFrame);
		}
		pet.setAnimation(-1);
	}

	/**
	 * Capture the pet's real pose-animation table once, before applyPose
	 * rewires it. Re-runs after a pet instance change (animsHijacked reset).
	 */
	private void ensureHijack()
	{
		if (pet == null || animsHijacked)
		{
			return;
		}
		origIdle = pet.getIdlePoseAnimation();
		origWalk = pet.getWalkAnimation();
		origRun = pet.getRunAnimation();

		// LEARN: these are the pet's TRUE pose anims, straight from the
		// game. Persist them so transmogs of this pet (by anyone's id
		// variant — also keyed by name) use the real thing instead of the
		// imported table, whose rows are sometimes the BOSS's animations
		// (chaos elemental jr's row was the boss's frantic loop — the
		// "animation repeat" look on transmogs).
		learnPetAnims();

		// Pose anims actually USED for rendering: the real pet's, unless the
		// transmog preview is active — then the best data we have for the
		// previewed pet: learned-from-a-real-sighting first, imported table
		// as fallback (origX stay untouched for restore).
		poseIdle = origIdle;
		poseWalk = origWalk;
		poseRun = origRun;
		if (previewing)
		{
			NPCComposition pc = client.getNpcDefinition(activePreviewId);
			String pname = pc != null ? pc.getName() : null;
			int[] learned = learnedAnims(activePreviewId, pname);
			if (learned != null)
			{
				poseIdle = learned[0];
				poseWalk = learned[1];
				poseRun = learned[2];
			}
			else
			{
				PetPoseAnims.Pose pp = PetPoseAnims.forPet(activePreviewId, pname);
				if (pp != null)
				{
					poseIdle = pp.idle;
					poseWalk = pp.walk;
					poseRun = pp.run;
				}
			}
		}

		hijackWalkAnim = poseWalk != -1 ? client.loadAnimation(poseWalk) : null;
		prevMirrorPoseFrame = -1;
		runBoostAcc = 0f;
		blipExtraMs = 0f;
		blipClockNanos = 0;
		animsHijacked = true;
		poseState = -1;
		loadPetSpeed();
	}

	/** Apply the selected follow style, easing the trailing-distance drift. */
	private void updateFollowStyle(float dt)
	{
		float driftAmp;
		switch (config.followStyle())
		{
			case LOOSE:
				followStartDist = 256f;
				followSlope = 700f;
				followSmoothTau = 320f;
				followSettleTicks = 5;
				driftAmp = 90f;
				break;
			case MEDIUM:
				followStartDist = 128f;
				followSlope = 500f;
				followSmoothTau = 230f;
				followSettleTicks = 3;
				driftAmp = 45f;
				break;
			default:
				followStartDist = START_DIST;
				followSlope = 350f;
				followSmoothTau = PATH_SMOOTH_TAU;
				followSettleTicks = 2;
				driftAmp = 0f;
				break;
		}

		if (driftAmp <= 0f)
		{
			gapDrift = 0f;
			gapDriftTarget = 0f;
			return;
		}
		long now = System.currentTimeMillis();
		if (now >= gapDriftRetargetMs)
		{
			// Only ever ADDS distance (drifting closer than the base gap
			// would nose into the player); eased over ~1.5 s so the change
			// reads as the pet ambling, not snapping.
			gapDriftTarget = followRng.nextFloat() * driftAmp;
			gapDriftRetargetMs = now + 3000 + followRng.nextInt(5000);
		}
		gapDrift += (gapDriftTarget - gapDrift) * Math.min(1f, dt / 1500f);
	}

	/**
	 * Load the displayed pet's remembered settings (cadence, clearance) and
	 * reflect them in the config sliders, so the sliders always show and
	 * edit the pet you actually have out (or are transmogged to).
	 */
	private void loadPetSpeed()
	{
		int id = previewing ? activePreviewId : (pet != null ? pet.getId() : -1);
		if (id < 0)
		{
			return;
		}
		boolean petChanged = id != speedKeyId;
		speedKeyId = id;
		Integer saved = configManager.getConfiguration("petfollower", "petSpeed_" + id, Integer.class);
		// Clamp: values below 100 could be saved by older builds that tried
		// (and failed) to support below-native slowing.
		petSpeedPct = Math.max(100, saved != null ? saved : 100);
		if (config.petSpeed() != petSpeedPct)
		{
			syncingSpeedUi = true;
			configManager.setConfiguration("petfollower", "petSpeed", petSpeedPct);
			syncingSpeedUi = false;
		}
		// Ground clearance is per-pet too — synced silently, no chat line.
		Integer savedClr = configManager.getConfiguration("petfollower", "clearance_" + id, Integer.class);
		int clr = Math.max(0, Math.min(32, savedClr != null ? savedClr : 0));
		if (config.groundClearance() != clr)
		{
			syncingSpeedUi = true;
			configManager.setConfiguration("petfollower", "groundClearance", clr);
			syncingSpeedUi = false;
		}
		// Projectile tint color is per-pet as well (red Hunllef, green Olm
		// jr, ...). A pet with no saved color keeps whatever is set, and
		// only gets its own entry once the user picks a color for it.
		java.awt.Color savedTint = configManager.getConfiguration("petfollower", "boltColor_" + id, java.awt.Color.class);
		if (savedTint != null && !savedTint.equals(config.thrallBoltColor()))
		{
			syncingSpeedUi = true;
			configManager.setConfiguration("petfollower", "thrallBoltColor", savedTint);
			syncingSpeedUi = false;
		}
		// Keep the damage-icon sprite in step with the displayed pet.
		resolvePetIcon(id);
		// Announce the loaded profile in the chatbox whenever the displayed
		// pet changes: an already-OPEN config panel does not repaint when a
		// plugin changes a value under it, so the slider can look stale
		// until reopened — the chat line is the always-visible truth.
		if (petChanged && config.cadenceMessages())
		{
			chat("Pet cadence: " + displayedPetName() + " — " + petSpeedPct + "%"
				+ (saved != null ? " (saved)" : " (default)") + ".");
		}
	}

	/** Name of the pet currently displayed (transmog wins over the real pet). */
	private String displayedPetName()
	{
		NPCComposition comp = previewing
			? client.getNpcDefinition(activePreviewId)
			: (pet != null ? pet.getTransformedComposition() : null);
		String name = comp != null ? comp.getName() : null;
		return name != null && !name.isEmpty() ? name : "pet";
	}

	private void chat(String msg)
	{
		if (client.getGameState() == GameState.LOGGED_IN)
		{
			client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", msg, null);
		}
	}


	@Subscribe
	public void onConfigChanged(ConfigChanged e)
	{
		if (!"petfollower".equals(e.getGroup()))
		{
			return;
		}
		// User moved the cadence slider: persist it for the displayed pet.
		if ("petSpeed".equals(e.getKey()) && !syncingSpeedUi)
		{
			petSpeedPct = config.petSpeed();
			if (speedKeyId >= 0)
			{
				configManager.setConfiguration("petfollower", "petSpeed_" + speedKeyId, petSpeedPct);
			}
		}
		// Same for the ground-clearance slider.
		if ("groundClearance".equals(e.getKey()) && !syncingSpeedUi && speedKeyId >= 0)
		{
			configManager.setConfiguration("petfollower", "clearance_" + speedKeyId, config.groundClearance());
		}
		// User picked a tint color: persist it for the displayed pet.
		if ("thrallBoltColor".equals(e.getKey()) && !syncingSpeedUi && speedKeyId >= 0)
		{
			configManager.setConfiguration("petfollower", "boltColor_" + speedKeyId, config.thrallBoltColor());
		}
		// Bolt tint toggled or recolored: restore every repainted model to
		// its original colors (the engine caches posed models — merely
		// stopping the repaint would leave them tinted), drop tracking, and
		// give a failed tint another chance. In-flight bolts revert; the
		// next cast uses the new settings.
		if ("tintThrallBolt".equals(e.getKey()) || "thrallBoltColor".equals(e.getKey()))
		{
			clientThread.invoke(() ->
			{
				clearTintedBolts();
				tintFailed = false;
			});
		}
	}

	/**
	 * Faux-run applies only while moving, for pets whose used pose set has
	 * no distinct run animation — everything else already looks right.
	 * Gated by the master toggle, and the per-pet percentage can push a
	 * pet's cap up (or squash it to nothing at low percentages).
	 */
	/**
	 * Cadence multiplier for right now, RUN pose only. 100% = vanilla: no
	 * automatic tiers, no touching the animation at all — the slider is a
	 * pure per-pet opt-in speed-up. Floored at 1x: the engine-cooperative
	 * mechanism can only add frames, never slow below native.
	 */
	private float cadenceFactor()
	{
		if (poseState != POSE_RUN)
		{
			return 1f;
		}
		return Math.max(1f, Math.min(4f, petSpeedPct / 100f));
	}

	/** Boost only pets with no distinct run animation, per the master toggle. */
	private boolean cadenceEligible(float factor)
	{
		return config.speedTweaksEnabled()
			&& factor > 1.001f
			&& (poseRun == -1 || poseRun == poseWalk);
	}

	/** Restore the pet's real animation table before it becomes visible. */
	private void releasePet()
	{
		if (pet != null && animsHijacked)
		{
			pet.setIdlePoseAnimation(origIdle);
			pet.setWalkAnimation(origWalk);
			pet.setRunAnimation(origRun);
			pet.setPoseAnimation(origIdle);
		}
		animsHijacked = false;
	}

	/**
	 * Sweep the ghost's facing toward the desired heading with angular
	 * accel/decel, so turns ease in and out instead of pivoting at full
	 * rate from the first frame.
	 */
	private void steer(int desired, float dt)
	{
		int cur = ghost.getOrientation();
		int diff = wrapAngle(desired - cur);

		// Proportional target angular velocity: aim to close the remaining
		// angle over ~TURN_TAU ms (so it decelerates into alignment rather
		// than slamming past it), capped by the config slider, ramped by
		// TURN_ACCEL.
		float turnMax = config.turnSpeed() * DEG_PER_SEC_TO_JAU_PER_MS;
		if (Math.abs(diff) > 512)
		{
			// Full reversal: whip around at double rate — an about-face that
			// takes a whole second reads as a robot on rails, and every ms
			// spent pivoting is a ms the owner is running away.
			turnMax *= 2f;
		}
		float want = Math.max(-turnMax, Math.min(turnMax, diff / TURN_TAU));
		float maxDv = TURN_ACCEL * dt;
		turnV += Math.max(-maxDv, Math.min(maxDv, want - turnV));

		int step = Math.round(turnV * dt);
		if (step == 0)
		{
			return;
		}
		if ((diff > 0 && step >= diff) || (diff < 0 && step <= diff))
		{
			ghost.setOrientation(desired);
			turnV = 0f;
			return;
		}
		ghost.setOrientation((cur + step) & 2047);
	}

	private static int wrapAngle(int diff)
	{
		diff &= 2047;
		return diff > 1024 ? diff - 2048 : diff;
	}

	// ------------------------------------------------------------------
	// Events that invalidate the ghost
	// ------------------------------------------------------------------

	@Subscribe
	public void onNpcDespawned(NpcDespawned e)
	{
		if (e.getNpc() == pet)
		{
			// Just drop the stale reference so the ghost freezes on its cached
			// model. onGameTick's grace period re-links the pet when it comes
			// back, or tears down if it's really gone — no blink/respawn.
			pet = null;
			animsHijacked = false;
		}
		else if (e.getNpc() == thrall)
		{
			// Distinguish expiry (vanished in view, near you) from render-range
			// loss (vanished at the edge because you ran off). Only the former
			// ends impersonation; the latter keeps it and re-acquires later.
			Player me = client.getLocalPlayer();
			int dist = me != null ? thrall.getWorldLocation().distanceTo(me.getWorldLocation()) : 99;
			thrall = null;
			thrallAttached = false;
			if (dist <= THRALL_RENDER_EDGE)
			{
				log.debug("Thrall expired in view (dist {}); resuming follow", dist);
				endThrall();
			}
			else
			{
				log.debug("Thrall left render range (dist {}); holding impersonation", dist);
				thrallLostTicks = 0;
			}
		}
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged e)
	{
		GameState s = e.getGameState();
		if (s == GameState.LOADING)
		{
			// Region crossing (or teleport). The ghost is NOT torn down: its
			// local-space state gets translated into the new scene's frame on
			// the next rendered frame (applySceneShift), so it glides through
			// loading lines mid-stride — speed, route, and facing untouched.
			// (Tearing it down here was the visible "pet respawns at every
			// loading line" pop, worst on loose follow styles.) Thrall and
			// bolt tracking still reset — NPC/projectile instances don't
			// survive the reload; they re-acquire right after.
			rescanThrall = impersonating && config.thrallMode();
			clearTintedBolts();
			thrall = null;
			thrallAttached = false;
			if (ghost != null)
			{
				pendingSceneShift = true;
				pendingShiftFrom = lastPlayerTile;
			}
		}
		else if (s == GameState.HOPPING || s == GameState.LOGIN_SCREEN)
		{
			// World hop / logout: a clean slate.
			rescanThrall = false;
			resumeAtSpeed = false;
			releasePet();
			despawnGhost();
			hidingReal = false;
			clearTintedBolts();
			thrall = null;
			thrallAttached = false;
			impersonating = false;
			thrallLostTicks = 0;
		}
	}

	// ------------------------------------------------------------------
	// Follower detection
	// ------------------------------------------------------------------

	private NPC findMyPet()
	{
		Player me = client.getLocalPlayer();
		if (me == null)
		{
			return null;
		}

		int varp = client.getVarpValue(FOLLOWER_VARP);
		if (varp <= 0)
		{
			return null; // no follower is out
		}

		NPC nearest = null;
		int nearestDist = Integer.MAX_VALUE;
		int followerCount = 0;

		for (NPC npc : client.getNpcs())
		{
			NPCComposition comp = npc.getTransformedComposition();
			if (comp == null || !comp.isFollower())
			{
				continue;
			}
			// Exact index match from the varp is always trusted.
			if (matchesFollowerVarp(varp, npc.getIndex()))
			{
				return npc;
			}
			followerCount++;
			int d = npc.getWorldLocation().distanceTo(me.getWorldLocation());
			if (d < nearestDist)
			{
				nearestDist = d;
				nearest = npc;
			}
		}

		// No exact match: only guess when it's unambiguous — a single adjacent
		// follower. In a crowd (other players' pets around) refuse to guess,
		// so the ghost never briefly mirrors someone else's pet.
		if (followerCount == 1 && nearestDist <= 2)
		{
			return nearest;
		}
		return null;
	}

	/** A follower pet is currently summoned (may be out of render range). */
	private boolean isFollowerActive()
	{
		return client.getVarpValue(FOLLOWER_VARP) > 0;
	}

	private static boolean matchesFollowerVarp(int varpValue, int npcIndex)
	{
		return varpValue == npcIndex
			|| (varpValue >>> 16) == npcIndex
			|| (varpValue & 0xFFFF) == npcIndex;
	}

	// JAU orientation (0 = south, 512 = west, 1024 = north, 1536 = east)
	private static int jau(float dx, float dy)
	{
		return ((int) Math.round(Math.atan2(-dx, -dy) * 1024.0 / Math.PI)) & 2047;
	}

	private static final class PathPoint
	{
		final float x;
		final float y;
		final float cum;

		PathPoint(float x, float y, float cum)
		{
			this.x = x;
			this.y = y;
			this.cum = cum;
		}
	}
}
