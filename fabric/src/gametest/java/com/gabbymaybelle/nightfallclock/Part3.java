package com.gabbymaybelle.nightfallclock;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * Films Part 3 of the series "Small mods that still feel vanilla" (mods-media nightfall-clock/PART-3-PLAN.md)
 * with {@link Film}: 0.4.0's Overworld time in the Nether. Walking back to her portal in a crimson forest,
 * the clock reading "Overworld: Day 4 · 9:12 PM"; at the portal she looks back over the forest; she mines
 * some quartz while the clock goes round to the morning; and out of the portal on the cottage's lawn at
 * sunrise. Only runs with {@code ./gradlew :fabric:runClientGameTest -PvideoTour -PvideoPart3}; {@link
 * VideoTour} steps aside then. Frames go in build/run/clientGameTest/video_tour.
 */
public class Part3 implements FabricClientGameTest {
	private static final String PREFIX = "nightfallclock";
	private static final double BEHIND = 5.5;
	/** The Overworld's clock: 0 is day 1, 6:00 AM, and 1000 ticks to the hour. Day 4, 9:12 PM. */
	private static final int NIGHT = 3 * 24000 + 15200;
	/** Day 4, 5:52 AM: the wait runs from here past 6:00 AM, when day 5 starts. */
	private static final int DAWN = 4 * 24000 - 133;

	private Film film;
	/** Where the cottage's front path ends; the cottage goes north (negative z) of it. */
	private int sx, sy, sz;
	/** The Nether portal's frame's bottom-west corner of its opening, and the lawn portal's. */
	private BlockPos nether, lawn;

	/** Whether this run films Part 3: {@code -PvideoPart3} with {@code -PvideoTour}. */
	public static boolean on() {
		return Film.on(PREFIX) && Boolean.getBoolean(PREFIX + ".videoPart3");
	}

	@Override
	public void runTest(ClientGameTestContext context) {
		if (!on()) {
			return;
		}
		film = new Film(context, PREFIX, "video_tour");
		Config.Position position = Config.position;
		Config.TimeFormat format = Config.timeFormat;
		boolean clock = Config.clock, dusk = Config.duskWarning, overworld = Config.overworldTime;
		double fovEffect = context.computeOnClient(mc -> mc.options.fovEffectScale().get());
		Config.position = Config.Position.ABOVE_HOTBAR;
		Config.timeFormat = Config.TimeFormat.TWELVE_HOUR;
		Config.clock = true;
		// The dusk warning would come up at 6:00 PM on the way: this Short is the Overworld's time, nothing else.
		Config.duskWarning = false;
		Config.overworldTime = true;
		context.runOnClient(mc -> mc.options.fovEffectScale().set(0.0));
		try {
			film.take(() -> film.newWorld("nightfall clock"), this::stage, this::scenes);
		} finally {
			Config.position = position;
			Config.timeFormat = format;
			Config.clock = clock;
			Config.duskWarning = dusk;
			Config.overworldTime = overworld;
			Config.save();
			context.runOnClient(mc -> mc.options.fovEffectScale().set(fovEffect));
		}
	}

	// ------------------------------------------------------------------ the sets

	/** The cottage (as Short 3's, from {@code videoCottage}) away from spawn, as VideoTour stages it. */
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
		// The lawn's portal: out on the open lawn east of the path, facing it, so she comes home along the
		// lawn to the path (south of the path's end the camera met the garden's hedge: second scout).
		int px = sx + 14, pz = sz + 4;
		lawn = new BlockPos(px, film.ground(px, pz), pz);
		String in = "execute in minecraft:overworld run ";
		film.server.runCommand(in + fill(px, lawn.getY() - 1, pz - 1, px, lawn.getY() + 3, pz + 2, "obsidian"));
		film.server.runCommand(in + fill(px, lawn.getY(), pz, px, lawn.getY() + 2, pz + 1, "nether_portal[axis=z]"));
		// The birches between it and the path go, so she can walk home and the cottage shows (third scout),
		// and the one south of the path's end, where the camera swings as she turns up the path (second take).
		for (String tag : new String[] { "#minecraft:logs", "#minecraft:leaves" }) {
			film.server.runCommand(in + fill(sx - 4, sy - 1, sz + 1, px - 1, sy + 12, sz + 11, "air replace " + tag));
		}
		nether = netherSpot();
		film.note("portals", "lawn " + lawn.toShortString() + ", crimson forest " + nether.toShortString());
		netherSet(nether);
	}

	/**
	 * A spot on the crimson forest's floor nearest the Nether's middle: open ground with room to walk to it
	 * from the south.
	 */
	private BlockPos netherSpot() {
		return film.server.computeOnServer(s -> {
			ServerLevel level = s.getLevel(Level.NETHER);
			var found = level.findClosestBiome3d(b -> b.is(Biomes.CRIMSON_FOREST), new BlockPos(0, 64, 0), 3200, 32, 64);
			Film.check(found != null, "a crimson forest in the Nether");
			BlockPos c = found.getFirst();
			for (int cx = (c.getX() - 24) >> 4; cx <= (c.getX() + 24) >> 4; cx++) {
				for (int cz = (c.getZ() - 24) >> 4; cz <= (c.getZ() + 24) >> 4; cz++) {
					level.getChunk(cx, cz);
				}
			}
			BlockPos best = null;
			int bestScore = -1;
			for (int x = c.getX() - 20; x <= c.getX() + 20; x += 2) {
				for (int z = c.getZ() - 20; z <= c.getZ() + 20; z += 2) {
					for (int y = 32; y < 100; y++) {
						BlockPos p = new BlockPos(x, y, z);
						if (!floor(level, p)) {
							continue;
						}
						// The way in (south of the spot, as wide as the portal) must be nylium floor at the same
						// height give or take one; then the most crimson trees around it, so the forest shows
						// on both sides (the first scout's spot was the biome's bare netherrack edge).
						int lane = 0;
						for (int dz = 0; dz <= 11; dz++) {
							for (int dx = 0; dx <= 1; dx++) {
								for (int dy = -1; dy <= 1; dy++) {
									BlockPos q = p.offset(dx, dy, dz);
									if (floor(level, q) && level.getBlockState(q.below()).is(Blocks.CRIMSON_NYLIUM)) {
										lane++;
										break;
									}
								}
							}
						}
						if (lane < 20) {
							continue;
						}
						int score = lane;
						for (BlockPos q : BlockPos.betweenClosed(p.offset(-7, 0, -2), p.offset(8, 8, 14))) {
							if (level.getBlockState(q).is(Blocks.CRIMSON_STEM) || level.getBlockState(q).is(Blocks.NETHER_WART_BLOCK)) {
								score++;
							}
						}
						if (score > bestScore) {
							best = p;
							bestScore = score;
						}
					}
				}
			}
			Film.check(best != null, "open floor in the crimson forest");
			return best;
		});
	}

	/** Solid ground underfoot (not lava), and room to stand: nothing to bump into (roots and fungi are fine). */
	private static boolean floor(ServerLevel level, BlockPos p) {
		return solid(level, p.below()) && level.getFluidState(p.below()).isEmpty()
				&& !solid(level, p) && !solid(level, p.above()) && !solid(level, p.above(2));
	}

	private static boolean solid(ServerLevel level, BlockPos p) {
		return !level.getBlockState(p).getCollisionShape(level, p).isEmpty();
	}

	/**
	 * The Nether set at {@code p}: a narrow lane cut south of the portal, as wide as it, so she can walk in and
	 * the camera follow (the forest stays on both sides; the first scout's wide clearing left bare netherrack),
	 * its floor made whole with nylium, the portal, and a little quartz beside it to mine.
	 */
	private void netherSet(BlockPos p) {
		String in = "execute in minecraft:the_nether run ";
		int x = p.getX(), y = p.getY(), z = p.getZ();
		// Twenty blocks long: the harvest scenes stand her halfway down it with the camera behind her.
		film.server.runCommand(in + fill(x - 1, y, z - 1, x + 2, y + 4, z + 20, "air"));
		film.server.runCommand(in + fill(x - 1, y - 1, z - 1, x + 2, y - 1, z + 20, "crimson_nylium replace air"));
		film.server.runCommand(in + fill(x - 1, y - 1, z - 1, x + 2, y - 1, z + 20, "crimson_nylium replace lava"));
		film.server.runCommand(in + fill(x - 1, y - 1, z - 1, x + 2, y - 1, z + 20, "crimson_nylium replace netherrack"));
		// Room beside the portal for the quartz and her pickaxe swing.
		film.server.runCommand(in + fill(x + 3, y, z + 1, x + 3, y + 2, z + 3, "air"));
		portal("minecraft:the_nether", p);
		film.server.runCommand(in + String.format(Locale.ROOT, "setblock %d %d %d nether_quartz_ore", x + 3, y, z + 1));
		film.server.runCommand(in + String.format(Locale.ROOT, "setblock %d %d %d nether_quartz_ore", x + 3, y + 1, z + 1));
		film.server.runCommand(in + String.format(Locale.ROOT, "setblock %d %d %d netherrack", x + 4, y, z + 1));
		film.server.runCommand(in + String.format(Locale.ROOT, "setblock %d %d %d netherrack", x + 4, y + 1, z + 1));
	}

	private static String fill(int x0, int y0, int z0, int x1, int y1, int z1, String what) {
		return String.format(Locale.ROOT, "fill %d %d %d %d %d %d %s", x0, y0, z0, x1, y1, z1, what);
	}

	/** An obsidian frame round a lit 2 by 3 opening whose bottom-west block is {@code p}, facing north and south. */
	private void portal(String dimension, BlockPos p) {
		String in = "execute in " + dimension + " run ";
		int x = p.getX(), y = p.getY(), z = p.getZ();
		film.server.runCommand(in + fill(x - 1, y - 1, z, x + 2, y + 3, z, "obsidian"));
		film.server.runCommand(in + fill(x, y, z, x + 1, y + 2, z, "nether_portal[axis=x]"));
	}

	// ------------------------------------------------------------------ the scenes

	private void scenes() {
		readSet();
		film.window(1080, 1920, 3);
		if (film.scouting()) {
			film.scene("x_set_nether", this::setNether);
		}
		// The problem first (Gabby, after the first cut: "too little happens", open on a creeper at night),
		// then a harvest run in the forest with the Overworld's time on the clock.
		film.scene("o_night", this::night);
		film.scene("n_chop", this::chop);
		film.scene("n_gold", this::gold);
		film.scene("n_wait", this::waitForMorning);
		// The ending from in front of her as she steps out of the portal (Gabby, after the face test).
		film.scene("o_face", this::face);
	}

	/** In front of the Nether portal, a step back from it, facing it. */
	private Vec3 front() {
		return new Vec3(nether.getX() + 1, nether.getY(), nether.getZ() + 1.8);
	}

	/** Scout only: the clearing and its portal from above and behind where she walks in. */
	private void setNether() {
		ready("minecraft:the_nether", front().add(0, 0, 8), Vec3.atCenterOf(nether), NIGHT);
		film.hud(false);
		film.spectator();
		place("minecraft:the_nether", front().add(0.5, 4, 10), Vec3.atCenterOf(nether));
		film.record(1);
	}

	/**
	 * The problem: back through the portal onto the lawn at 9:12 PM, still facing it, she turns for home and
	 * a creeper is right there, hissing. Summoned once she's half turned (before that it would stand between
	 * her and the camera); NoAI so it stays put, lit so it swells and hisses, and gone before its fuse ends.
	 */
	private void night() {
		int ox = lawn.getX() - 7;
		Vec3 out = new Vec3(ox + 0.5, film.ground(ox, lawn.getZ() + 1), lawn.getZ() + 1);
		ready("minecraft:overworld", out, new Vec3(lawn.getX() + 0.5, lawn.getY() + 1.5, lawn.getZ() + 1), NIGHT);
		film.server.runCommand("difficulty easy");
		// Long grass round her spot stood right in front of the camera once she turned (first take).
		for (String plant : new String[] { "short_grass", "tall_grass", "fern", "large_fern" }) {
			film.server.runCommand(fill(ox - 7, sy - 4, lawn.getZ() - 3, lawn.getX() - 1, sy + 6, lawn.getZ() + 5, "air replace " + plant));
		}
		// The game's own Brightness at its highest, as many players have it: the night still reads as night,
		// but the creeper shows on a phone (at the default it was lost in the dark: first take).
		double gamma = ctx(mc -> mc.options.gamma().get());
		film.ctx.runOnClient(mc -> mc.options.gamma().set(1.0));
		try {
			film.rolling();
			checkClock(4, 21 * 60 + 12);
			frames(6);
			float from = ctx(mc -> mc.player.getYRot());
			for (int i = 1; i <= 12; i++) {
				yaw(from + 180 * (float) Film.ease(i / 12.0));
				if (i == 6) {
					// Ahead and a little to her left, so she doesn't hide it from the camera (first take).
					Vec3 c = out.add(-3.4, 0, 1.3);
					film.server.runCommand(String.format(Locale.ROOT,
							"summon creeper %.2f %d %.2f {NoAI:1b,ignited:1b,Fuse:200s,Rotation:[-70f,0f],PersistenceRequired:1b}",
							c.x, film.ground((int) Math.floor(c.x), (int) Math.floor(c.z)), c.z));
					film.log("creeper", "");
				}
				tick(1);
			}
			frames(10);
			// Her face as she sees it (Gabby): from just past the creeper, its flashing back in the
			// foreground, looking at her.
			Vec3 c = out.add(-3.4, 0, 1.3);
			Vec3 away = new Vec3(c.x - out.x, 0, c.z - out.z).normalize();
			Vec3 side = new Vec3(-away.z, 0, away.x);
			// Back and to the side of it, so the creeper takes a corner and her face the middle (at 1.5 out
			// it filled half the frame: first shaded take).
			Vec3 eye = c.add(away.scale(2.6)).add(side.scale(-1.5)).add(0, 1.8, 0);
			film.log("face", "");
			camera(eye, out.add(0, 1.5, 0));
			frames(24);
		} finally {
			cameraOff();
			film.ctx.runOnClient(mc -> mc.options.gamma().set(gamma));
		}
		long creepers = film.server.computeOnServer(s -> java.util.stream.StreamSupport.stream(s.overworld().getAllEntities().spliterator(), false)
				.filter(e -> net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).getPath().equals("creeper")).count());
		Film.check(creepers == 1, "the creeper is there, hissing (" + creepers + " creepers)");
	}

	/** A crimson stem standing in the lane, chopped with an axe: the Overworld's 9:14 PM on the clock. */
	private void chop() {
		String in = "execute in minecraft:the_nether run ";
		BlockPos stem = nether.offset(1, 0, 9);
		try {
			Vec3 stand = new Vec3(stem.getX() + 0.5, stem.getY(), stem.getZ() + 2.3);
			ready("minecraft:the_nether", stand, Vec3.atCenterOf(stem.above()), NIGHT + 30);
			// Placed once she's there: from the Overworld the Nether's chunks aren't loaded (first take).
			film.server.runCommand(in + fill(stem.getX(), stem.getY(), stem.getZ(), stem.getX(), stem.getY() + 2, stem.getZ(), "crimson_stem"));
			film.server.runCommand(in + String.format(Locale.ROOT, "setblock %d %d %d nether_wart_block", stem.getX(), stem.getY() + 3, stem.getZ()));
			film.ctx.waitTicks(3);
			film.give("item replace entity @a hotbar.0 with iron_axe");
			film.rolling();
			harvest(stem.above(), Blocks.CRIMSON_STEM, 1);
			frames(6);
			checkClock(4, 21 * 60 + 14);
		} finally {
			film.server.runCommand(in + fill(stem.getX(), stem.getY(), stem.getZ(), stem.getX(), stem.getY() + 3, stem.getZ(), "air"));
		}
	}

	/** A lump of nether gold ore in the lane, mined: the clock still on the Overworld's night. */
	private void gold() {
		String in = "execute in minecraft:the_nether run ";
		BlockPos ore = nether.offset(1, 1, 6);
		try {
			Vec3 stand = new Vec3(ore.getX() + 0.5, ore.getY() - 1, ore.getZ() + 2.3);
			ready("minecraft:the_nether", stand, Vec3.atCenterOf(ore), NIGHT + 60);
			film.server.runCommand(in + fill(ore.getX() - 1, ore.getY() - 1, ore.getZ(), ore.getX(), ore.getY(), ore.getZ(), "netherrack"));
			film.server.runCommand(in + String.format(Locale.ROOT, "setblock %d %d %d nether_gold_ore", ore.getX(), ore.getY(), ore.getZ()));
			film.server.runCommand(in + String.format(Locale.ROOT, "setblock %d %d %d nether_gold_ore", ore.getX() - 1, ore.getY() - 1, ore.getZ()));
			film.ctx.waitTicks(3);
			film.rolling();
			harvest(ore, Blocks.NETHER_GOLD_ORE, 1);
			harvest(ore.offset(-1, -1, 0), Blocks.NETHER_GOLD_ORE, 1);
			frames(4);
			checkClock(4, 21 * 60 + 16);
		} finally {
			film.server.runCommand(in + fill(ore.getX() - 1, ore.getY() - 1, ore.getZ(), ore.getX(), ore.getY(), ore.getZ(), "air"));
			film.server.runCommand("execute in minecraft:the_nether run kill @e[type=item]");
		}
	}

	/** Breaks {@code block} (it must be {@code what}), the clock {@code ticks} on a frame, and keeps the experience off the hotbar. */
	private void harvest(BlockPos block, net.minecraft.world.level.block.Block what, int ticks) {
		film.aim(block);
		film.ctx.getInput().holdKey(o -> o.keyAttack);
		try {
			for (int i = 0; i < 50 && ctx(mc -> mc.level.getBlockState(block).is(what)); i++) {
				tick(ticks);
			}
		} finally {
			film.ctx.getInput().releaseKey(o -> o.keyAttack);
		}
		Film.check(!ctx(mc -> mc.level.getBlockState(block).is(what)), "she broke the " + what.getName().getString() + " at " + block.toShortString());
		film.server.runCommand("kill @e[type=experience_orb]");
		film.server.runCommand("xp set @a 0 points");
		film.server.runCommand("xp set @a 0 levels");
		pickUp();
		film.log("broke", block.toShortString());
	}

	/**
	 * What she just broke drops at her feet and she picks it up, as a player walking past would: left on the
	 * ground it span in the air beside her for the rest of the shot (Gabby, on the first cut).
	 */
	private void pickUp() {
		film.server.runCommand("execute as @e[type=item] at @p run tp @s ~ ~0.3 ~");
	}

	/** Mining the quartz beside the portal while the Overworld's night goes by: the clock turns to day 5, morning. */
	private void waitForMorning() {
		BlockPos low = nether.offset(3, 0, 1), high = low.above();
		ready("minecraft:the_nether", front(), Vec3.atCenterOf(high), DAWN);
		film.give("item replace entity @a hotbar.0 with iron_pickaxe");
		film.rolling();
		for (BlockPos ore : new BlockPos[] { high, low }) {
			film.aim(ore);
			film.ctx.getInput().holdKey(o -> o.keyAttack);
			for (int i = 0; i < 40 && ctx(mc -> mc.level.getBlockState(ore).is(Blocks.NETHER_QUARTZ_ORE)); i++) {
				tick(3);
			}
			film.ctx.getInput().releaseKey(o -> o.keyAttack);
			// The ore's experience never reaches her: no level over the hotbar (first take).
			film.server.runCommand("kill @e[type=experience_orb]");
			film.server.runCommand("xp set @a 0 points");
			film.server.runCommand("xp set @a 0 levels");
			pickUp();
			film.log("mined", ore.toShortString());
		}
		film.turnHead(Vec3.atCenterOf(nether).add(0.5, 0.5, 0));
		while (ctx(mc -> NightfallClock.DayTime.of(mc.level).minute()) < 6 * 60 + 5 || ctx(mc -> NightfallClock.DayTime.of(mc.level).day()) < 5) {
			tick(3);
		}
		tick(3);
		checkClock(5, 6 * 60 + 5);
	}

	/**
	 * Home at sunrise, from in front (Gabby: "my face isn't shown"): she steps straight out of the lawn's
	 * portal and walks towards the camera, which stays ahead of her on the line from the portal through her,
	 * so the portal glows behind her. Her own camera, moved in front of her (FilmCameraMixin).
	 */
	private void face() {
		// Stepping straight out of the lawn's portal: with the camera in front of her, the portal can be
		// right behind her (from behind, the camera would have been looking through it).
		Vec3 portal = new Vec3(lawn.getX() + 0.5, lawn.getY(), lawn.getZ() + 1);
		int ox = lawn.getX() - 1;
		Vec3 out = new Vec3(ox + 0.3, film.ground(ox, lawn.getZ() + 1), lawn.getZ() + 1);
		// Straight out of the portal (it faces west) and only a few unhurried steps: walking on down to the
		// path took her below the bank and the portal dropped out of the shot (Gabby, twice).
		Vec3 stop = out.add(-3.0, 0, 0);
		ready("minecraft:overworld", out, stop.add(0, 1.6, 0), 4 * 24000 + 90);
		film.thirdPerson(false, BEHIND);
		film.server.runCommand("attribute @a minecraft:movement_speed base set 0.02");
		film.ctx.runOnClient(mc -> mc.player.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.02));
		// A still camera out in front, above the bank, framing her and the portal behind her.
		Vec3 eye = new Vec3(portal.x - 7.5, portal.y + 2.4, portal.z + 0.5);
		try {
			film.ctx.getInput().holdKey(o -> o.keyUp);
			for (int i = 0; i < 70; i++) {
				Vec3 at = ctx(mc -> mc.player.position());
				if (flat(at, stop) < 0.4) {
					film.ctx.getInput().releaseKey(o -> o.keyUp);
				}
				// Between her face and the portal's middle, so both stay in the frame.
				camera(eye, at.add(0, 1.5, 0).lerp(portal.add(0, 1.5, 0), 0.3));
				tick(1);
			}
		} finally {
			film.ctx.getInput().releaseKey(o -> o.keyUp);
			cameraOff();
		}
	}

	/** While true, the view is drawn from {@link #freeEye} towards {@link #freeLook} (FilmCameraMixin). */
	public static volatile boolean freeCamera;
	public static volatile Vec3 freeEye, freeLook;

	/** The view from {@code eye} towards {@code look}, her own camera moved there (third person, so she shows). */
	private void camera(Vec3 eye, Vec3 look) {
		film.ctx.runOnClient(mc -> mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK));
		freeEye = eye;
		freeLook = look;
		freeCamera = true;
	}

	/** Back to the view from behind her. */
	private void cameraOff() {
		freeCamera = false;
	}

	// ------------------------------------------------------------------ moves

	/**
	 * Survival, HUD on, in {@code dimension} at {@code feet} looking at {@code look}, the Overworld's clock at
	 * {@code time}, seen from behind, with a pickaxe, quartz and a torch or two on the hotbar.
	 */
	private void ready(String dimension, Vec3 feet, Vec3 look, int time) {
		// The opening's creeper goes before its fuse runs out.
		film.server.runCommand("kill @e[type=creeper]");
		film.server.runCommand("difficulty peaceful");
		film.calm();
		film.hud(true);
		film.survival();
		film.thirdPerson(true, BEHIND);
		// Half speed as VideoTour's: one saved frame is one of the world's ticks but two of the player's.
		film.server.runCommand("attribute @a minecraft:movement_speed base set 0.05");
		film.server.runCommand("attribute @a minecraft:block_break_speed base set 0.5");
		film.ctx.runOnClient(mc -> {
			mc.player.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.05);
			mc.player.getAttribute(Attributes.BLOCK_BREAK_SPEED).setBaseValue(0.5);
		});
		film.server.runCommand("time set " + time);
		film.server.runCommand("effect give @a fire_resistance 600 0 true");
		film.server.runCommand("effect give @a saturation 1 10 true");
		film.server.runCommand("clear @a");
		// Mining quartz gives experience: no level over the hotbar in the next scene.
		film.server.runCommand("xp set @a 0 levels");
		film.server.runCommand("xp set @a 0 points");
		film.server.runCommand("item replace entity @a hotbar.1 with quartz 7");
		film.server.runCommand("item replace entity @a hotbar.2 with torch 12");
		film.server.runCommand("item replace entity @a hotbar.3 with bread 5");
		place(dimension, feet, look);
		film.ctx.getInput().pressKey(o -> o.keyHotbarSlots[0]);
		film.give("item replace entity @a hotbar.0 with iron_pickaxe");
		film.turnHead(look);
		film.ctx.runOnClient(mc -> {
			mc.player.setYBodyRot(mc.player.getYRot());
			// Bigger than the options allow for a 1080-wide window, so the clock reads on a phone (as Short 3).
			mc.getWindow().setGuiScale(4);
		});
		film.ctx.waitTicks(2);
	}

	/** A teleport into {@code dimension}, waiting off camera for the client to arrive there. */
	private void place(String dimension, Vec3 feet, Vec3 look) {
		film.server.runCommand(String.format(Locale.ROOT, "execute in %s run tp @a %.3f %.3f %.3f facing %.3f %.3f %.3f",
				dimension, feet.x, feet.y, feet.z, look.x, look.y, look.z));
		film.until0(mc -> mc.level != null && mc.level.dimension().identifier().toString().equals(dimension)
				&& mc.player.position().distanceTo(feet) < 1.0);
	}

	/** Walks towards {@code to} for up to {@code frames} frames, stopping a little short. */
	private void walkTo(Vec3 to, int frames) {
		film.ctx.getInput().holdKey(o -> o.keyUp);
		film.log("walk", "");
		try {
			for (int i = 0; i < frames; i++) {
				Vec3 at = ctx(mc -> mc.player.position());
				if (flat(at, to) < 0.6) {
					break;
				}
				float want = (float) Math.toDegrees(Math.atan2(-(to.x - at.x), to.z - at.z));
				film.ctx.runOnClient(mc -> {
					float now = mc.player.getYRot();
					float d = ((want - now) % 360 + 540) % 360 - 180;
					float turned = now + Math.max(-6, Math.min(6, d));
					mc.player.setYRot(turned);
					mc.player.setYHeadRot(turned);
					mc.player.setYBodyRot(turned);
					mc.player.setXRot(4);
				});
				tick(1);
			}
		} finally {
			film.ctx.getInput().releaseKey(o -> o.keyUp);
		}
	}

	private void yaw(float yaw) {
		film.ctx.runOnClient(mc -> {
			mc.player.setYRot(yaw);
			mc.player.setYHeadRot(yaw);
			mc.player.setYBodyRot(yaw);
			mc.player.setXRot(4);
		});
	}

	/** {@code n} frames, the clock a game tick on with each. */
	private void frames(int n) {
		for (int i = 0; i < n; i++) {
			tick(1);
		}
	}

	/** One frame, the Overworld's clock {@code ticks} on. */
	private void tick(int ticks) {
		film.server.runCommand("time add " + ticks);
		film.record(1);
	}

	/** The clock reads the Overworld's {@code day} at {@code minute} (give or take three minutes: a scene runs as long as its mining takes), as the mod's own reading. */
	private void checkClock(int day, int minute) {
		NightfallClock.DayTime t = ctx(mc -> NightfallClock.DayTime.of(mc.level));
		boolean nether = ctx(mc -> mc.level.dimension() == Level.NETHER);
		Film.check(t != null && t.day() == day && Math.abs(t.minute() - minute) <= 3 && t.elsewhere() == nether,
				"the clock reads " + (nether ? "the Overworld's " : "") + "day " + day + " at minute " + minute + " (it reads " + t + ")");
		film.note("clock", String.valueOf(t));
	}

	private static double flat(Vec3 a, Vec3 b) {
		return Math.hypot(a.x - b.x, a.z - b.z);
	}

	private <T> T ctx(Function<Minecraft, T> f) {
		return film.ctx.computeOnClient(f::apply);
	}
}
