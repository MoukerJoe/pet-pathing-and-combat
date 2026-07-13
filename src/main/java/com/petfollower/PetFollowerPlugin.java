package com.petfollower;

import com.google.inject.Provides;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Model;
import net.runelite.api.NPC;
import net.runelite.api.NPCComposition;
import net.runelite.api.Perspective;
import net.runelite.api.Player;
import net.runelite.api.Projectile;
import net.runelite.api.Renderable;
import net.runelite.api.RuneLiteObjectController;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.AnimationChanged;
import net.runelite.api.events.BeforeRender;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.NpcDespawned;
import net.runelite.api.events.NpcSpawned;
import net.runelite.api.events.ProjectileMoved;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.callback.Hooks;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;

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
	description = "Cosmetic: your pet keeps pace right behind you and can stand in for Arceuus thralls",
	tags = {"pet", "follower", "pathing", "combat", "thrall", "cosmetic"}
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
	private static final float CATCHUP_DIST = 96f;  // beyond this past gap, sprint to catch up
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

	// While moving, lean the facing partially toward the player (capped) so
	// the pet reads as watching you through turns without ever strafing.
	private static final float PLAYER_BIAS = 0.25f;
	private static final int PLAYER_BIAS_CAP = 80;

	// When resting, turn to face the player if more than this far off (JAU),
	// like a pet watching its owner; deadband stops idle micro-fidgeting.
	private static final int IDLE_FACE_DEADBAND = 72;

	// Docking: how the ghost hands back to the real pet without a visible
	// pop — it strolls onto the pet's exact spot, matches its facing, and
	// only then swaps.
	private static final float DOCK_ARRIVE = 4f;
	private static final int DOCK_FACING = 48;

	// Thrall attack: pets with a known attack animation play it; those
	// without fall back to a short forward lunge.
	private static final float LUNGE_MS = 400f;
	private static final float LUNGE_DIST = 48f;

	// The real pet NPC leaves render range for a few ticks when you run far
	// or teleport. Keep the ghost coasting on its cached model this long
	// before giving up, so it never blinks out and respawns with a spin.
	private static final int PET_GRACE_TICKS = 10;

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
	private float ghostX;
	private float ghostY;
	private float turnV;
	private long lastFrameNanos;
	private int poseState = -1;
	private boolean docking;

	// Thrall impersonation: while a summoned thrall is tracked, it is hidden
	// and the ghost rides its exact position and facing instead of following.
	private NPC thrall;
	private boolean thrallAttached;
	private float thrallSpeed;
	private float prevThrallX;
	private float prevThrallY;
	private float lungeMs;
	private int petMissingTicks;
	private boolean rescanThrall;
	private int ghostSize = 1;
	private float gap = GAP; // effective resting distance for the current pet
	private boolean ghostShown;

	// Thrall impersonation persists across render-range loss: `impersonating`
	// stays true even when the thrall NPC is momentarily gone.
	private boolean impersonating;
	private int thrallLostTicks;
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
	}

	@Override
	protected void shutDown()
	{
		hooks.unregisterRenderableDrawListener(drawListener);
		hidingReal = false;
		clientThread.invoke(() ->
		{
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
		if (thrall != null && renderable == thrall)
		{
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
				// New pet instance (respawn after a scene reload): its anim
				// table is fresh, so re-capture and re-hijack it.
				animsHijacked = false;
				poseState = -1;
			}
			pet = found;
			petMissingTicks = 0;
		}
		else
		{
			petMissingTicks++;
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
		lastPlayerTile = playerTile;

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
			// The real pet must never show while impersonating, even mid-run.
			hidingReal = true;
			NPC t = findNearbyThrall(me, THRALL_REACQUIRE_RADIUS);
			if (t != null)
			{
				thrall = t;
				thrallAttached = false;
				lungeMs = 0f;
				thrallLostTicks = 0;
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
				animsHijacked = false; // fresh NPC instance: recapture anims
				pet = found;
				petMissingTicks = 0;
			}
			else if (!impersonating)
			{
				despawnGhost();
				return;
			}
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

		// --- extend the rendered path -----------------------------------
		// Same-plane jumps of any size just become a long path segment the
		// ghost dashes across — it never teleports. Only a plane change
		// (stairs/ladders) reparks it, since there's nothing to walk through.
		int plane = client.getPlane();
		PathPoint tail = path.peekLast();
		float step = tail == null ? 0f : (float) Math.hypot(p.getX() - tail.x, p.getY() - tail.y);

		if (tail == null || plane != pathPlane)
		{
			resetPathBehind(p, plane);
			return;
		}

		if (step > 0.5f)
		{
			playerCum += step;
			path.addLast(new PathPoint(p.getX(), p.getY(), playerCum));
		}

		// Units per ms, smoothed over ~100 ms.
		playerSpeed += (step / dt - playerSpeed) * Math.min(1f, dt / 100f);

		// --- decide the ghost's target speed ----------------------------
		float error = playerCum - gap - ghostArc;

		float vTarget;
		if (error <= STOP_DIST)
		{
			vTarget = 0f;
		}
		else if (ghostV == 0f && error < START_DIST)
		{
			vTarget = 0f; // reaction beat: let you pull ahead a little first
		}
		else if (error > CATCHUP_DIST)
		{
			// Dash speed scales with the gap so even huge gaps close in
			// under a second — the pet never teleports.
			vTarget = Math.max(CATCHUP_SPEED, error / 700f);
		}
		else
		{
			// Cruise: match your pace (bounded to sane walk..run range).
			vTarget = Math.max(WALK_SPEED * 0.85f, Math.min(RUN_SPEED, playerSpeed));
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
			ghostArc = Math.min(ghostArc + ghostV * dt, playerCum - gap * 0.75f);
			moveGhostToArc(dt);
		}

		// --- pose: driven by the ghost's target state ---------------------
		int newState = vTarget == 0f && ghostV == 0f
			? POSE_IDLE
			: (vTarget <= RUN_SPEED * 0.7f ? POSE_WALK : POSE_RUN);
		applyPose(newState);

		// At rest, gently turn to face you — a pet watching its owner. The
		// wide deadband keeps it from fidgeting at small angles.
		if (newState == POSE_IDLE && config.watchOwner())
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
			if (d > 6f)
			{
				// Jog over to take the thrall's place at a normal run pace
				// (not a jarring sprint), never teleport.
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
			log.debug("Re-acquired thrall id={} after scene load", t.getId());
		}
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
		if (thrall == null)
		{
			return;
		}
		Projectile pr = e.getProjectile();
		// Only react the instant it spawns, and only if it launched from the
		// thrall's tile — magic (ghostly) thralls attack via projectile with
		// no animation, which is why animation-based detection missed them.
		if (pr.getStartCycle() != client.getGameCycle())
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
		NPCComposition comp = pet.getTransformedComposition();
		String name = comp != null ? comp.getName() : null;
		int atk = PetAttackAnims.forPet(pet.getId(), name);
		log.debug("Thrall attack -> pet id={} name={} atk={}", pet.getId(), name, atk);
		if (atk != -1)
		{
			pet.setAnimation(atk);
			pet.setAnimationFrame(0);
		}
		else
		{
			lungeMs = LUNGE_MS;
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

	private int poseFor(int state)
	{
		switch (state)
		{
			case POSE_WALK:
				return origWalk;
			case POSE_RUN:
				return origRun != -1 ? origRun : origWalk;
			default:
				return origIdle;
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

			// Lean the facing a little toward the player so the pet reads
			// as watching you through the turn (capped: never strafes).
			Player me = client.getLocalPlayer();
			LocalPoint mp = config.watchOwner() && me != null ? me.getLocalLocation() : null;
			if (mp != null)
			{
				int toPlayer = jau(mp.getX() - nx, mp.getY() - ny);
				int bias = (int) (wrapAngle(toPlayer - desired) * PLAYER_BIAS);
				bias = Math.max(-PLAYER_BIAS_CAP, Math.min(PLAYER_BIAS_CAP, bias));
				desired = (desired + bias) & 2047;
			}

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

		if (ghost != null)
		{
			ghost.setOrientation(facing);
			place(new LocalPoint(Math.round(ghostX), Math.round(ghostY)), plane);
		}
		turnV = 0f;
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
				}
			}
			return lastModel;
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
		turnV = 0f;
		lastFrameNanos = System.nanoTime();
		ghostX = petPos.getX();
		ghostY = petPos.getY();

		ensureHijack();
		poseState = -1;
		NPCComposition comp = pet.getTransformedComposition();
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
	}

	private void place(LocalPoint at, int plane)
	{
		ghost.setLocation(at, plane);
		// Seat the ghost on the terrain the same way the engine seats the
		// real NPC: footprint-averaged height, so wide flat pets (Scorpia's
		// offspring etc.) don't sink into slopes. Uses the size captured at
		// spawn so it stays correct even while the pet is a render blip.
		ghost.setZ(Perspective.getFootprintTileHeight(client, at, plane, ghostSize));
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
		pet.setPoseAnimation(id);
		pet.setPoseAnimationFrame(0);
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
		animsHijacked = true;
		poseState = -1;
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
		if (s == GameState.LOADING || s == GameState.HOPPING || s == GameState.LOGIN_SCREEN)
		{
			// LocalPoints are invalid across scene loads; rebuild lazily. A
			// thrall that outlives a same-world region change (teleport) keeps
			// its impersonation and is re-found next tick; a world hop / login
			// is a clean slate.
			boolean regionChange = s == GameState.LOADING;
			rescanThrall = regionChange && impersonating && config.thrallMode();
			releasePet();
			despawnGhost();
			hidingReal = false;
			thrall = null;
			thrallAttached = false;
			petMissingTicks = 0;
			if (!regionChange)
			{
				impersonating = false;
				thrallLostTicks = 0;
			}
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
