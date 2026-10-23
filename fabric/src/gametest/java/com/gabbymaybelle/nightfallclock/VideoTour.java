package com.gabbymaybelle.nightfallclock;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import java.util.stream.StreamSupport;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Films the second vertical YouTube Short, "Caught out", with {@link Film}: out chopping a tree at
 * the edge of the woods when "The sun's getting real low" comes up at 6:00 PM, the run home across
 * the lawn in cuts as the hour goes by (the clock showing in each), up the porch steps, and from
 * inside at 7:00 PM the door shut on the night's first zombie coming up the path.
 *
 * <p>Filmed at a cottage: in a saved world that has one ({@code -PvideoWorld=<zip>}, its spot kept as
 * "cottage"), or one built from its .mcfunction ({@code -PvideoCottage=<file>}). Without either the
 * scenes play on open ground by a tree of their own, so the test runs for anyone. Only runs with
 * {@code ./gradlew :fabric:runClientGameTest -PvideoTour}; tools/video turns the frames into the Short.
 */
public class VideoTour implements FabricClientGameTest {
	/** Names the system properties: nightfallclock.videoTour, nightfallclock.videoScenes... */
	private static final String PREFIX = "nightfallclock";
	/** Vanilla ticks: 0 is 6:00 AM, 1000 ticks to the hour, so 12000 is 6:00 PM and 13000 is 7:00 PM. */
	private static final int DUSK = 12000;
	/** The game's own clock minute for 6:00 PM, when the mod warns. */
	private static final int SIX_PM = 18 * 60;
	/** The camera behind the player in third person, a little further back than the game's 4 blocks. */
	private static final double BEHIND = 5.5;

	private Film film;
	/** Where the cottage's front path ends; the cottage goes north (negative z) of it. */
	private int sx, sy, sz;
	/** The tree at the edge of the woods: its trunk from the bottom up, and the blocks as they were. */
	private final List<BlockPos> trunk = new ArrayList<>();
	private final List<BlockState> trunkWas = new ArrayList<>();
	/** Where the player stands to chop it (feet). */
	private Vec3 edge;
	/** The way home, feet points from the tree to the cottage door. */
	private final List<Vec3> route = new ArrayList<>();

	@Override
	public void runTest(ClientGameTestContext context) {
		if (!Film.on(PREFIX)) {
			return;
		}
		film = new Film(context, PREFIX, "video_tour");
		Config.Position position = Config.position;
		Config.TimeFormat format = Config.timeFormat;
		boolean clock = Config.clock;
		boolean dusk = Config.duskWarning;
		boolean autoJump = context.computeOnClient(mc -> mc.options.autoJump().get());
		double fovEffect = context.computeOnClient(mc -> mc.options.fovEffectScale().get());
		boolean countdown = Config.countdown;
		Config.position = Config.Position.ABOVE_HOTBAR;
		Config.timeFormat = Config.TimeFormat.TWELVE_HOUR;
		Config.clock = true;
		Config.duskWarning = true;
		Config.countdown = true;
		// The third Short's countdown reads the video's clock, not the computer's (see VideoClock).
		VideoClock.on = short3();
		// The run home goes up a step or two at the woods and the porch: jump them as a player would.
		context.runOnClient(mc -> mc.options.autoJump().set(true));
		// No sprinting zoom, and none from the slower walk below: the view stays put mid-shot.
		context.runOnClient(mc -> mc.options.fovEffectScale().set(0.0));
		try {
			film.take(() -> film.newWorld("nightfall clock"), this::stage, this::scenes);
		} finally {
			Config.position = position;
			Config.timeFormat = format;
			Config.clock = clock;
			Config.duskWarning = dusk;
			Config.countdown = countdown;
			VideoClock.on = false;
			Config.save();
			context.runOnClient(mc -> {
				mc.options.autoJump().set(autoJump);
				mc.options.fovEffectScale().set(fovEffect);
			});
		}
	}

	// ------------------------------------------------------------------ the set

	/** A new world only: a cottage (when its file is given) on open ground away from spawn. */
	private void stage() {
		BlockPos spawn = film.server.computeOnServer(s -> s.overworld().getRespawnData().pos());
		int x = spawn.getX() + 40, z = spawn.getZ();
		int y = film.ground(x, z);
		String file = film.prop("videoCottage", "");
		if (!file.isEmpty()) {
			List<String> lines;
			try {
				lines = Files.readAllLines(Path.of(file));
			} catch (IOException e) {
				throw new AssertionError("can't read the cottage (" + file + ")", e);
			}
			String at = "execute positioned " + x + " " + y + " " + z + " run ";
			for (String line : lines) {
				if (!line.isBlank() && !line.startsWith("#")) {
					film.server.runCommand(at + line);
				}
			}
		}
		film.keep("cottage", x + " " + y + " " + z);
	}

	private void readSet() {
		String[] s = film.kept("cottage").split(" ");
		sx = Integer.parseInt(s[0]);
		sy = Integer.parseInt(s[1]);
		sz = Integer.parseInt(s[2]);
		BlockPos base = findTree();
		if (base == null) {
			// Open ground with no woods: a tree of our own, as far out as the woods would be.
			int tz = sz + 24;
			film.server.runCommand("place feature minecraft:oak " + sx + " " + film.ground(sx, tz) + " " + tz);
			base = findTree();
		}
		Film.check(base != null, "a tree to chop south of the cottage, with open ground on the way home");
		BlockPos b = base;
		film.server.runOnServer(server -> {
			ServerLevel level = server.overworld();
			for (BlockPos p = b; level.getBlockState(p).is(BlockTags.LOGS); p = p.above()) {
				trunk.add(p.immutable());
				trunkWas.add(level.getBlockState(p));
			}
		});
		edge = new Vec3(b.getX() + 0.5, b.getY(), b.getZ() - 1.6);
		route.add(edge);
		route.add(spot(0, 0, 3.5));
		route.add(spot(0, 0, 0.5));
		route.add(spot(0, 1, -4.5));
		route.add(spot(0, 1, -5.9));
		film.note("tree", b.toShortString() + " (" + trunk.size() + " logs)");
	}

	/** A point offset from the cottage's spot, at a block's middle. */
	private Vec3 spot(double x, double y, double z) {
		return new Vec3(sx + 0.5 + x, sy + y, sz + 0.5 + z);
	}

	/**
	 * The trunk base of the tree nearest the cottage at the edge of the woods to its south: a log
	 * on the ground with another above it, open ground two blocks north of it to stand on, and a way
	 * home from there with no step higher than one block.
	 */
	private BlockPos findTree() {
		return film.server.computeOnServer(s -> {
			ServerLevel level = s.overworld();
			for (int cx = (sx - 14) >> 4; cx <= (sx + 14) >> 4; cx++) {
				for (int cz = (sz - 2) >> 4; cz <= (sz + 42) >> 4; cz++) {
					level.getChunk(cx, cz);
				}
			}
			BlockPos best = null;
			double bestScore = Double.MAX_VALUE;
			for (int x = sx - 12; x <= sx + 12; x++) {
				for (int z = sz + 16; z <= sz + 40; z++) {
					for (int y = sy - 3; y <= sy + 10; y++) {
						BlockPos p = new BlockPos(x, y, z);
						if (!level.getBlockState(p).is(BlockTags.LOGS) || !level.getBlockState(p.above()).is(BlockTags.LOGS)
								|| !solid(level, p.below()) || level.getBlockState(p.below()).is(BlockTags.LOGS)) {
							continue;
						}
						double score = (z - sz) + Math.abs(x - sx) * 1.5;
						if (score < bestScore && standable(level, p.north(2)) && open(level, p.north()) && wayHome(level, p.north(2))) {
							best = p;
							bestScore = score;
						}
					}
				}
			}
			return best;
		});
	}

	private static boolean solid(ServerLevel level, BlockPos p) {
		return !level.getBlockState(p).getCollisionShape(level, p).isEmpty();
	}

	/** Nothing to bump into at feet and head height. */
	private static boolean open(ServerLevel level, BlockPos p) {
		return !solid(level, p) && !solid(level, p.above());
	}

	private static boolean standable(ServerLevel level, BlockPos p) {
		return solid(level, p.below()) && open(level, p);
	}

	/** A walk north from {@code from} to the cottage's front path with no step up or down of more than one block. */
	private boolean wayHome(ServerLevel level, BlockPos from) {
		double x0 = from.getX() + 0.5, z0 = from.getZ() + 0.5, x1 = sx + 0.5, z1 = sz + 3.5;
		int steps = (int) Math.ceil(Math.hypot(x1 - x0, z1 - z0) * 2);
		int y = from.getY();
		for (int i = 1; i <= steps; i++) {
			double t = i / (double) steps;
			BlockPos column = BlockPos.containing(x0 + (x1 - x0) * t, y, z0 + (z1 - z0) * t);
			int found = Integer.MIN_VALUE;
			for (int dy : new int[] { 0, 1, -1 }) {
				if (standable(level, column.atY(y + dy))) {
					found = y + dy;
					break;
				}
			}
			if (found == Integer.MIN_VALUE) {
				return false;
			}
			y = found;
		}
		return true;
	}

	// ------------------------------------------------------------------ the scenes

	/** Whether this take is the third Short, "Dark in 24s" ({@code -PvideoShort3}). */
	private static boolean short3() {
		return Boolean.getBoolean(PREFIX + ".videoShort3");
	}

	private void scenes() {
		readSet();
		film.window(1080, 1920, 3);
		if (short3()) {
			short3Scenes();
			return;
		}
		if (film.scouting()) {
			film.scene("x_set_edge", this::setEdge);
			film.scene("x_set_run", this::setRun);
		}
		film.scene("s_chop", this::chop);
		// Each cut starts past where the one before ended (a frame covers about 0.28 blocks at a sprint),
		// so the way home only goes forward.
		film.scene("s_run1", () -> runCut(0.27, DUSK + 333, 20));
		film.scene("s_run2", () -> runCut(0.52, DUSK + 667, 16));
		film.scene("s_porch", this::porch);
		film.scene("s_door", this::door);
	}

	/** Scout only: the edge of the woods from above the lawn, the tree in the middle, in late gold light. */
	private void setEdge() {
		still(DUSK - 100);
		Vec3 log = Vec3.atCenterOf(trunk.getFirst());
		film.camera(edge.add(0, 7, -9), log);
		film.record(1);
	}

	/** Scout only: the whole way home, from high over the woods behind the tree, looking at the cottage. */
	private void setRun() {
		still(DUSK - 100);
		int over = film.treeline(trunk.getFirst().getX(), trunk.getFirst().getZ(), 4);
		Vec3 from = new Vec3(edge.x + 4, over + 6, edge.z + 8);
		film.camera(from, spot(0, 3, -6));
		film.record(1);
	}

	private void still(int time) {
		film.calm();
		film.hud(false);
		film.spectator();
		film.thirdPerson(false, BEHIND);
		film.server.runCommand("time set " + time);
	}

	/**
	 * Frame 1 of the Short comes from here: chopping the tree at eye height in late light, seen from
	 * behind, when at 6:00 PM (the clock ticking a game minute about every 17 frames) the mod's own
	 * warning takes the clock's line. The chopping comes in bursts, as a player's does (letting go
	 * starts the log's cracks again), so the log only comes down a second or two after the warning,
	 * which gets the screen to itself for a moment (a voice-over line fits there); then a quick
	 * turn for home, and off at a sprint. The Short opens on the warning and ends on the swings before
	 * it, so it loops.
	 */
	private void chop() {
		BlockPos log = trunk.get(1);
		ready(edge, aimAt(log), true, DUSK - 45);
		restoreTree();
		film.ctx.waitTicks(4);
		film.turnHead(aimAt(log));
		film.rolling();
		film.log("chop", "");
		int warned = -1, felled = -1;
		for (int i = 0; i < 160 && (felled < 0 || i < felled + 6); i++) {
			// Bursts of 26 frames (52 of the player's ticks: at half breaking speed a wooden axe needs 60
			// to fell an oak or birch log) until 24 frames after the warning, then held until the log
			// comes down.
			boolean hold = (warned >= 0 && i >= warned + 24) || i % 28 < 26;
			if (hold) {
				film.ctx.getInput().holdKey(o -> o.keyAttack);
			} else {
				film.ctx.getInput().releaseKey(o -> o.keyAttack);
			}
			tick();
			if (warned < 0 && ctx(mc -> NightfallClock.duskShowing())) {
				warned = i;
				int minute = ctx(mc -> NightfallClock.DayTime.of(mc.level).minute());
				// By the mod's own rule: the warning comes as the clock reaches 6:00 PM.
				Film.check(minute == SIX_PM || minute == SIX_PM + 1, "the dusk warning showed at 6:00 PM (the clock read minute " + minute + ")");
				film.log("dusk", "the sun's getting real low");
			}
			if (felled < 0 && !ctx(mc -> mc.level.getBlockState(log).is(BlockTags.LOGS))) {
				felled = i;
				film.ctx.getInput().releaseKey(o -> o.keyAttack);
				film.log("log", "");
			}
		}
		film.ctx.getInput().releaseKey(o -> o.keyAttack);
		Film.check(warned >= 0, "the dusk warning showed while chopping through 6:00 PM");
		Film.check(felled > warned, "the log came down after the warning (at frame " + felled + ", the warning at " + warned + ")");
		// A quick look round for home: the camera swings behind as the player turns.
		float from = ctx(mc -> mc.player.getYRot());
		for (int i = 1; i <= 9; i++) {
			float yaw = from + 180 * (float) Film.ease(i / 9.0);
			yawTo(yaw, 90);
			tick();
		}
		film.log("turn", "");
		// The warning lasts 8 seconds of the player's time (4 of filming), so it may fade back into the
		// clock during the sprint, as it would in play.
		Film.check(ctx(mc -> NightfallClock.duskShowing()), "the warning is still up as the player turns for home");
		run(1, 26);
	}

	/** One cut of the run home, starting {@code along} the way, at {@code time}. */
	private void runCut(double along, int time, int frames) {
		int leg = legAt(along);
		Vec3 at = pointAt(along);
		Vec3 feet = new Vec3(at.x, film.ground((int) Math.floor(at.x), (int) Math.floor(at.z)), at.z);
		Vec3 ahead = route.get(leg + 1);
		ready(feet, new Vec3(ahead.x, feet.y + 1.5, ahead.z), true, time);
		clockBack();
		run(leg + 1, frames);
	}

	/** The last stretch: up the front path and the porch steps to the open door, as the light goes. */
	private void porch() {
		double along = 0.72;
		int leg = legAt(along);
		Vec3 at = pointAt(along);
		Vec3 feet = new Vec3(at.x, film.ground((int) Math.floor(at.x), (int) Math.floor(at.z)), at.z);
		ready(feet, spot(0, 1.5, -4), true, DUSK + 920);
		door(true);
		clockBack();
		run(leg + 1, 80);
		for (int i = 0; i < 12; i++) {
			tick();
		}
		Film.check(ctx(mc -> mc.player.getZ()) < sz - 4, "the player made it onto the porch");
	}

	/**
	 * Through the player's eyes, inside at 7:00 PM, the door still open on the dusk: the night's first
	 * zombie comes up the path, and the door shuts on it. The zombie is summoned for the shot (no
	 * monsters spawn while filming), only now that it's dark, and off camera before the first frame.
	 */
	private void door() {
		BlockPos lower = new BlockPos(sx, sy + 1, sz - 7);
		Vec3 straight = spot(0, 2.3, -6.5);
		ready(spot(0, 1, -8.6), straight, false, DUSK + 1000);
		door(true);
		film.ctx.waitTicks(2);
		Vec3 panel = shapeCentre(lower.above());
		film.turnHead(straight);
		clockBack();
		film.server.runCommand("difficulty easy");
		Vec3 z = spot(-1, 0, 5);
		film.server.runCommand(String.format(Locale.ROOT, "summon zombie %.2f %d %.2f {Rotation:[180f,0f],PersistenceRequired:1b}",
				z.x, film.ground((int) Math.floor(z.x), (int) Math.floor(z.z)), z.z));
		// It's dusk-dark, not midnight: keep it from catching fire in the last of the light.
		film.server.runCommand("effect give @e[type=zombie] fire_resistance 120 0 true");
		film.rolling();
		film.log("zombie", "");
		// Long enough for it to come up into the porch light (and for a voice-over line).
		for (int i = 0; i < 46; i++) {
			tick();
		}
		double near = film.server.computeOnServer(s -> StreamSupport.stream(s.overworld().getAllEntities().spliterator(), false)
				.filter(e -> BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).getPath().equals("zombie"))
				.mapToDouble(e -> e.position().distanceTo(new Vec3(sx + 0.5, sy + 1, sz - 8.1))).min().orElse(99));
		Film.check(near < 14, "the zombie is coming up the path (it is " + Math.round(near) + " blocks from the door)");
		for (int i = 1; i <= 5; i++) {
			film.turnHead(straight.lerp(panel, Film.ease(i / 5.0)));
			tick();
		}
		String on = ctx(VideoTour::crosshair);
		Film.check(on.equals(lower.toShortString()) || on.equals(lower.above().toShortString()), "the crosshair is on the door (it is on " + on + ")");
		film.ctx.getInput().pressKey(o -> o.keyUse);
		film.log("door_close", "");
		for (int i = 1; i <= 6; i++) {
			tick();
		}
		for (int i = 1; i <= 6; i++) {
			film.turnHead(panel.lerp(straight, Film.ease(i / 6.0)));
			tick();
		}
		for (int i = 0; i < 16; i++) {
			tick();
		}
		Film.check(!ctx(mc -> mc.level.getBlockState(lower).getValue(DoorBlock.OPEN)), "the door is shut");
	}

	// ------------------------------------------------------------------ the third Short, "Dark in 24s" (0.4.0)

	/** Vanilla's dark point in clear weather: the sky light reaches the monster limit (7) at about this tick. */
	private static final int DARK = 13178;

	/**
	 * The third Short (plan: mods-media nightfall-clock/SHORT-3-PLAN.md): 0.4.0's countdown to dark, a
	 * race home. Walking up from the woods at sunset with the countdown ticking on the clock, the run up
	 * the path and the porch as it drops, and inside, into bed as it runs out. Each cut starts later in
	 * the evening, so the number only goes down.
	 */
	private void short3Scenes() {
		if (film.scouting()) {
			film.scene("x_set_run", this::setRun);
		}
		// The way home is about 20 blocks: walking at the usual pace covered all of it in 4 s (take 3), so
		// the walk is slower and covers the first half, from the woods into the garden, and the run the rest.
		film.scene("c_walk", () -> countdownCut(0.0, 12540, 70, false));
		film.scene("c_run", () -> countdownCut(0.55, 12800, 90, true));
		film.scene("c_bed", this::bed);
	}

	/** One cut of the way home, {@code along} it, from {@code time}: walking or at a sprint, the countdown on the clock. */
	private void countdownCut(double along, int time, int frames, boolean sprint) {
		int leg = legAt(along);
		Vec3 at = pointAt(along);
		Vec3 feet = new Vec3(at.x, film.ground((int) Math.floor(at.x), (int) Math.floor(at.z)), at.z);
		Vec3 ahead = route.get(leg + 1);
		ready(feet, new Vec3(ahead.x, feet.y + 1.5, ahead.z), true, time);
		door(true);
		if (!sprint) {
			// An unhurried evening walk: 0.035 against ready()'s 0.05 (both halved for the filming pace).
			film.server.runCommand("attribute @a minecraft:movement_speed base set 0.035");
			film.ctx.runOnClient(mc -> mc.player.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.035));
		}
		leadIn();
		film.rolling();
		run(leg + 1, frames, sprint);
		Film.check(shownSeconds() >= 0, "the countdown is still on the clock at the end of the cut");
	}

	/**
	 * Inside at the end of the evening, the door shut behind her: she crosses the room to the cottage's
	 * own bed as the countdown runs out on the clock (4, 3, 2, 1, gone), and then gets into bed (vanilla
	 * lets her from about 6:32 PM in clear weather) and the sleep fades in. In that order because in bed
	 * vanilla's own screen takes the clock's line, so a countdown running out there isn't seen (take 4);
	 * walking, because standing still by the bed for the whole countdown held the shot still for 4.6 s
	 * (Gabby, take 5). The bed is vanilla; nothing says the mod tells you when to sleep, and it doesn't.
	 */
	private void bed() {
		Vec3 start = spot(0, 1, -8.6);
		BlockPos foot = cottageBed(start);
		Film.check(foot != null, "the cottage has a bed on the ground floor");
		Vec3 pillow = Vec3.atCenterOf(foot).add(0, -0.3, 0);
		// She stops a step short of the bed's foot, on her side of it.
		Vec3 bedside = Vec3.atBottomCenterOf(foot).add(start.subtract(Vec3.atBottomCenterOf(foot)).normalize().scale(1.3));
		bedside = new Vec3(bedside.x, start.y, bedside.z);
		ready(start, pillow, true, DARK - 106);
		door(false);
		leadIn();
		// An unhurried walk across the room, so it takes most of the countdown (the room is a few blocks).
		film.server.runCommand("attribute @a minecraft:movement_speed base set 0.018");
		film.ctx.runOnClient(mc -> mc.player.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.018));
		film.rolling();
		film.ctx.getInput().holdKey(o -> o.keyUp);
		try {
			for (int i = 0; i < 120 && shownSeconds() >= 0; i++) {
				Vec3 at = ctx(mc -> mc.player.position());
				if (flat(at, bedside) < 0.4) {
					film.ctx.getInput().releaseKey(o -> o.keyUp);
				} else {
					float yaw = (float) Math.toDegrees(Math.atan2(-(bedside.x - at.x), bedside.z - at.z));
					yawTo(yaw, 6);
				}
				tick();
			}
		} finally {
			film.ctx.getInput().releaseKey(o -> o.keyUp);
		}
		film.note("bedside", "she stopped " + String.format(Locale.ROOT, "%.1f", flat(ctx(mc -> mc.player.position()), bedside)) + " blocks from the bedside");
		Film.check(shownSeconds() < 0, "the countdown ran out on the clock (it shows " + shownSeconds() + ")");
		film.log("dark", "");
		for (int i = 0; i < 12; i++) {
			tick();
		}
		film.aim(foot);
		film.ctx.getInput().pressKey(o -> o.keyUse);
		film.log("bed", "");
		boolean slept = false;
		// Vanilla wakes her after 5 s asleep, since the night can't pass here: the fade is filmed before then.
		for (int i = 0; i < 44; i++) {
			tick();
			// Getting into bed says "Respawn point set" in chat; it goes before it's filmed.
			film.quiet();
			if (!slept && ctx(mc -> mc.player.isSleeping())) {
				slept = true;
				// Her own view from the bed, as a player sees it: from behind, the camera sat in the pillow (scout).
				film.thirdPerson(false, BEHIND);
			}
		}
		Film.check(slept, "she got into bed");
	}

	/** The foot of the cottage's own bed on the ground floor, the one nearest {@code from}, or null. */
	private BlockPos cottageBed(Vec3 from) {
		return film.server.computeOnServer(s -> {
			ServerLevel level = s.overworld();
			BlockPos best = null;
			double bestD = Double.MAX_VALUE;
			for (BlockPos p : BlockPos.betweenClosed(sx - 9, sy + 1, sz - 22, sx + 9, sy + 1, sz - 7)) {
				BlockState state = level.getBlockState(p);
				if (state.getBlock() instanceof net.minecraft.world.level.block.BedBlock
						&& state.getValue(net.minecraft.world.level.block.BedBlock.PART) == net.minecraft.world.level.block.state.properties.BedPart.FOOT) {
					double d = flat(Vec3.atCenterOf(p), from);
					if (d < bestD) {
						bestD = d;
						best = p.immutable();
					}
				}
			}
			return best;
		});
	}

	/**
	 * Off camera, a cut's first half second of evening, so the countdown has light readings to time
	 * (it shows nothing for its first half second) and its number has settled when filming starts.
	 */
	private void leadIn() {
		VideoClock.cut();
		for (int i = 0; i < 16; i++) {
			film.server.runCommand("time add 1");
			VideoClock.tick();
			film.ctx.waitTicks(1);
		}
		int now = ctx(mc -> (int) (mc.level.getDefaultClockTime() % 24000));
		int expected = (int) Math.ceil((DARK - now) / 20.0);
		int shown = shownSeconds();
		film.note("countdown", "at tick " + now + " it shows " + shown + " s (about " + expected + " s by vanilla's dark point)");
		Film.check(Math.abs(shown - expected) <= 2, "the countdown reads about " + expected + " s at tick " + now + " (it shows " + shown + ")");
	}

	/** The countdown the clock shows just now, in seconds; -1 when it shows none. */
	private static int shownSeconds() {
		try {
			java.lang.reflect.Field f = NightfallClock.class.getDeclaredField("darkSeconds");
			f.setAccessible(true);
			return f.getInt(null);
		} catch (ReflectiveOperationException e) {
			throw new AssertionError("can't read the countdown", e);
		}
	}

	// ------------------------------------------------------------------ moves

	/**
	 * Survival, HUD on, stood at {@code feet} looking at {@code look} at {@code time}, a wooden axe in
	 * hand (day one), seen from behind ({@code third}) or through the player's eyes.
	 */
	private void ready(Vec3 feet, Vec3 look, boolean third, int time) {
		film.calm();
		film.server.runCommand("difficulty peaceful");
		film.server.runCommand("kill @e[type=zombie]");
		film.hud(true);
		film.survival();
		film.thirdPerson(third, BEHIND);
		// A game test saves a frame every two of the player's ticks but only one of the world's (the
		// player isn't slowed by /tick rate). Half speed, set as attributes so no effect icon shows,
		// makes the player's walking, running and chopping match the world at 20 frames a second.
		// The client player moves and digs itself, so it needs them too (the server's alone never reach
		// it: Not Now, Shield's second Short).
		film.server.runCommand("attribute @a minecraft:movement_speed base set 0.05");
		film.server.runCommand("attribute @a minecraft:block_break_speed base set 0.5");
		film.ctx.runOnClient(mc -> {
			mc.player.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.05);
			mc.player.getAttribute(Attributes.BLOCK_BREAK_SPEED).setBaseValue(0.5);
		});
		film.server.runCommand("time set " + time);
		film.server.runCommand("effect give @a saturation 1 10 true");
		film.server.runCommand("clear @a");
		film.server.runCommand("item replace entity @a hotbar.1 with oak_log 9");
		film.server.runCommand("item replace entity @a hotbar.2 with bread 5");
		film.server.runCommand("item replace entity @a hotbar.3 with torch 12");
		film.camera(feet, look);
		film.ctx.getInput().pressKey(o -> o.keyHotbarSlots[0]);
		// The new item's name over the hotbar fades before filming starts.
		film.give("item replace entity @a hotbar.0 with wooden_axe");
		film.turnHead(look);
		film.ctx.runOnClient(mc -> {
			mc.player.setYBodyRot(mc.player.getYRot());
			// The window takes a bigger GUI than the options allow (they cap a 1080-wide window at
			// 3): the clock reads on a phone.
			mc.getWindow().setGuiScale(4);
		});
		film.ctx.waitTicks(2);
	}

	/** Waits off camera until the warning is over and the clock has faded back onto its line. */
	private void clockBack() {
		film.until0(mc -> !NightfallClock.duskShowing());
		film.ctx.waitTicks(22);
	}

	/**
	 * Sprints along the route from waypoint {@code next}, turning towards each in turn, for {@code
	 * frames} frames or until the last waypoint.
	 */
	private void run(int next, int frames) {
		run(next, frames, true);
	}

	/** As {@link #run(int, int)}, walking instead of sprinting when {@code sprint} is false. */
	private void run(int next, int frames, boolean sprint) {
		film.ctx.getInput().holdKey(o -> o.keyUp);
		if (sprint) {
			film.ctx.getInput().holdKey(o -> o.keySprint);
		}
		film.log("run", "");
		int target = next;
		try {
			for (int i = 0; i < frames && target < route.size(); i++) {
				Vec3 at = ctx(mc -> mc.player.position());
				// Past a waypoint that's close, or already behind (the one after it is nearer): a cut can
				// start just short of one, and turning back for it sent the player round in a circle.
				while (target + 1 < route.size() && (flat(route.get(target), at) < 2.0 || flat(route.get(target + 1), at) < flat(route.get(target), at))) {
					target++;
				}
				Vec3 to = route.get(target);
				if (target + 1 == route.size() && flat(to, at) < 1.0) {
					break;
				}
				float yaw = (float) Math.toDegrees(Math.atan2(-(to.x - at.x), to.z - at.z));
				yawTo(yaw, 8);
				film.ctx.runOnClient(mc -> mc.player.setSprinting(sprint));
				tick();
			}
		} finally {
			film.ctx.getInput().releaseKey(o -> o.keyUp);
			film.ctx.getInput().releaseKey(o -> o.keySprint);
		}
	}

	/** Distance on the ground, ignoring height. */
	private static double flat(Vec3 a, Vec3 b) {
		return Math.hypot(a.x - b.x, a.z - b.z);
	}

	/** Turns the player (and the camera behind them) towards {@code yaw}, at most {@code step} degrees a frame, level. */
	private void yawTo(float yaw, float step) {
		film.ctx.runOnClient(mc -> {
			float now = mc.player.getYRot();
			float d = ((yaw - now) % 360 + 540) % 360 - 180;
			float turned = now + Math.max(-step, Math.min(step, d));
			if (step >= 90) {
				turned = yaw;
			}
			mc.player.setYRot(turned);
			mc.player.setYHeadRot(turned);
			mc.player.setYBodyRot(turned);
			mc.player.setXRot(4);
		});
	}

	/** One frame, with a game tick's worth of time passing: the clock ticks along in real time. */
	private void tick() {
		film.server.runCommand("time add 1");
		VideoClock.tick();
		film.record(1);
	}

	/** Puts back any logs chopped in an earlier take of the tree. */
	private void restoreTree() {
		film.server.runOnServer(s -> {
			ServerLevel level = s.overworld();
			for (int i = 0; i < trunk.size(); i++) {
				level.setBlockAndUpdate(trunk.get(i), trunkWas.get(i));
			}
		});
	}

	/** The cottage's front door, open or shut. */
	private void door(boolean open) {
		for (String half : new String[] { "upper", "lower" }) {
			int y = half.equals("upper") ? sy + 2 : sy + 1;
			film.server.runCommand("setblock " + sx + " " + y + " " + (sz - 7) + " spruce_door[facing=south,half=" + half + ",hinge=left,open=" + open + "]");
		}
	}

	/** A point on a log's north face near its west edge: from behind, the chopped face shows past the head. */
	private static Vec3 aimAt(BlockPos log) {
		return new Vec3(log.getX() + 0.3, log.getY() + 0.55, log.getZ() + 0.02);
	}

	/** The middle of a block's shape as the client sees it (an open door is a thin panel at one side). */
	private Vec3 shapeCentre(BlockPos p) {
		return ctx(mc -> mc.level.getBlockState(p).getShape(mc.level, p).bounds().getCenter().add(p.getX(), p.getY(), p.getZ()));
	}

	private int legAt(double along) {
		double want = along * length(), sum = 0;
		for (int i = 0; i + 1 < route.size(); i++) {
			double d = route.get(i).distanceTo(route.get(i + 1));
			if (sum + d >= want) {
				return i;
			}
			sum += d;
		}
		return route.size() - 2;
	}

	private Vec3 pointAt(double along) {
		double want = along * length(), sum = 0;
		for (int i = 0; i + 1 < route.size(); i++) {
			Vec3 a = route.get(i), b = route.get(i + 1);
			double d = a.distanceTo(b);
			if (sum + d >= want) {
				return a.lerp(b, (want - sum) / d);
			}
			sum += d;
		}
		return route.getLast();
	}

	private double length() {
		double sum = 0;
		for (int i = 0; i + 1 < route.size(); i++) {
			sum += route.get(i).distanceTo(route.get(i + 1));
		}
		return sum;
	}

	private static String crosshair(Minecraft mc) {
		return mc.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK
				? hit.getBlockPos().toShortString() : "nothing";
	}

	private <T> T ctx(Function<Minecraft, T> f) {
		return film.ctx.computeOnClient(f::apply);
	}
}
