package com.gabbymaybelle.nightfallclock;

import com.gabbymaybelle.nightfallclock.mixin.HudAccessor;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.biome.Biomes;

/**
 * Films the vertical YouTube Short: a screenshot every tick (20 a second) in a tall window, at a
 * cottage when its build file is passed in (`-Pcottage=<.mcfunction>`), or on open ground without
 * it. Frames, `actions.tsv` (what happened at which frame, for the sounds) and `frames.tsv` go in
 * the game folder's `video_tour`. Only runs with `./gradlew :fabric:runClientGameTest -PvideoTour`;
 * tools/video turns the frames into the Short.
 */
public class VideoTour implements FabricClientGameTest {
	/** Vanilla ticks: 0 is 6:00 AM, 1000 ticks to the hour, so 12000 is 6:00 PM. */
	private static final int DUSK = 12000;
	/** Mid-afternoon light for the scenes on the front path. */
	private static final int AFTERNOON = 9000;

	private ClientGameTestContext ctx;
	private Path dir;
	private String scene;
	private int frame;
	private final List<String> actions = new ArrayList<>();
	private final List<String> frames = new ArrayList<>();
	private BlockPos cottage;
	/** Client ticks so far, logged with each frame so the assembly knows the pace. */
	private static final AtomicInteger TICKS = new AtomicInteger();

	static boolean enabled() {
		return Boolean.getBoolean("nightfallclock.videoTour");
	}

	@Override
	public void runTest(ClientGameTestContext context) {
		if (!enabled()) {
			return;
		}
		ctx = context;
		ClientTickEvents.END_CLIENT_TICK.register(mc -> TICKS.incrementAndGet());
		dir = FabricLoader.getInstance().getGameDir().resolve("video_tour");
		try {
			if (Files.isDirectory(dir)) {
				try (var old = Files.list(dir)) {
					for (Path file : old.toList()) {
						Files.delete(file);
					}
				}
			}
			Files.createDirectories(dir);
		} catch (IOException e) {
			throw new AssertionError(e);
		}
		// The game keeps the screen at least 320 GUI pixels wide, so a 1080-wide window can't go past
		// GUI scale 3. The scenes are filmed in a bigger window at scale 4 and cropped to 1080x1440
		// from the bottom middle when assembled, which makes the clock a third bigger on a phone.
		window(1440, 1920, 4);
		Config.Position position = Config.position;
		Config.TimeFormat format = Config.timeFormat;
		Config.position = Config.Position.ABOVE_HOTBAR;
		Config.timeFormat = Config.TimeFormat.TWELVE_HOUR;
		try (TestSingleplayerContext world = ctx.worldBuilder().setUseConsistentSettings(false).adjustSettings(state -> {
			state.setSeed("nightfall clock");
			state.setGameMode(WorldCreationUiState.SelectedGameMode.SURVIVAL);
			state.setDifficulty(Difficulty.PEACEFUL);
			state.setAllowCommands(true);
		}).create()) {
			chunks(world);
			TestServerContext server = world.getServer();
			setUp(world, server);
			drop(world, server);
			items();
			dusk(world, server);
			server.runCommand("tick rate 20");
		} finally {
			Config.position = position;
			Config.timeFormat = format;
			Config.save();
		}
		try {
			Files.write(dir.resolve("actions.tsv"), actions);
			Files.write(dir.resolve("frames.tsv"), frames);
		} catch (IOException e) {
			throw new AssertionError(e);
		}
	}

	// ------------------------------------------------------------------ the set

	private void setUp(TestSingleplayerContext world, TestServerContext server) {
		server.runCommand("gamerule send_command_feedback false");
		server.runCommand("gamerule advance_time false");
		server.runCommand("gamerule advance_weather false");
		server.runCommand("weather clear");
		server.runCommand("time set 6000");
		BlockPos open = server.computeOnServer(s -> {
			BlockPos from = s.getPlayerList().getPlayers().getFirst().blockPosition();
			var found = s.overworld().findClosestBiome3d(b -> b.is(Biomes.PLAINS) || b.is(Biomes.SUNFLOWER_PLAINS) || b.is(Biomes.MEADOW),
					from, 2000, 32, 64);
			return found == null ? from : found.getFirst();
		});
		server.runCommand("spreadplayers " + open.getX() + " " + open.getZ() + " 0 1 false @a");
		ctx.waitTicks(5);
		chunks(world);
		cottage = ctx.computeOnClient(mc -> mc.player.blockPosition());
		// A flat lawn around the cottage, in slices small enough for a single fill. It reaches well
		// east, where the sunset shot stands, and well west, so no tree stands in front of the sun.
		int y = cottage.getY();
		for (int x = cottage.getX() - 60; x <= cottage.getX() + 42; x += 6) {
			String xs = x + " ", xe = (x + 5) + " ";
			int z0 = cottage.getZ() - 30, z1 = cottage.getZ() + 18;
			server.runCommand("fill " + xs + (y - 3) + " " + z0 + " " + xe + (y - 2) + " " + z1 + " dirt");
			server.runCommand("fill " + xs + (y - 1) + " " + z0 + " " + xe + (y - 1) + " " + z1 + " grass_block");
			server.runCommand("fill " + xs + y + " " + z0 + " " + xe + (y + 20) + " " + z1 + " air");
		}
		build(server, cottage);
		ctx.waitTicks(2);
		server.runCommand("kill @e[type=item]");
		// A vanilla clock in hand to start (the first scene drops it), then something cozy in the
		// next few slots, for the item names.
		server.runCommand("item replace entity @a hotbar.0 with clock");
		server.runCommand("item replace entity @a hotbar.1 with lantern");
		server.runCommand("item replace entity @a hotbar.2 with bread 6");
		server.runCommand("item replace entity @a hotbar.3 with poppy 3");
		ctx.runOnClient(mc -> mc.player.getInventory().setSelectedSlot(0));
		ctx.waitTicks(60);
		chunks(world);
		// On a software renderer the game runs extra ticks to catch up while a frame draws, so frames
		// come an uneven 2-4 ticks apart. Slowed down, every frame is exactly two ticks: the Short
		// plays the scenes where timing matters (the clock fading back in) at half speed, which is real time.
		server.runCommand("tick rate 3");
	}

	/** Waits for the world to draw; a slow software renderer can take longer than the wait allows. */
	private void chunks(TestSingleplayerContext world) {
		try {
			world.getConnection().waitForChunksRender();
		} catch (AssertionError e) {
			NightfallClock.LOGGER.warn("Nightfall Clock: video tour went on before every chunk had drawn");
		}
	}

	/** Replays the cottage's commands with its front path ending at `spot`, if the file was passed in. */
	private void build(TestServerContext server, BlockPos spot) {
		String file = System.getProperty("nightfallclock.cottage", "");
		if (file.isEmpty()) {
			return;
		}
		List<String> lines;
		try {
			lines = Files.readAllLines(Path.of(file));
		} catch (IOException e) {
			throw new AssertionError("can't read the cottage (" + file + ")", e);
		}
		String at = "execute positioned " + spot.getX() + " " + spot.getY() + " " + spot.getZ() + " run ";
		for (String line : lines) {
			if (!line.isBlank() && !line.startsWith("#")) {
				server.runCommand(at + line);
			}
		}
	}

	/** Stands the player at an offset from the cottage's spot, looking at another offset. */
	private void stand(TestServerContext server, double x, double y, double z, double fx, double fy, double fz) {
		server.runCommand(String.format(Locale.ROOT, "tp @a %.2f %.2f %.2f facing %.2f %.2f %.2f",
				cottage.getX() + 0.5 + x, cottage.getY() + y, cottage.getZ() + 0.5 + z,
				cottage.getX() + 0.5 + fx, cottage.getY() + fy, cottage.getZ() + 0.5 + fz));
		ctx.waitTicks(10);
		quiet();
	}

	/**
	 * On the front path, ten blocks out, looking up at the porch, the door and the window boxes.
	 * (The cottage goes north, negative z, of its spot.)
	 */
	private void frontPath(TestServerContext server) {
		stand(server, 1.5, 0, 9, 0, 6, -9);
	}

	/**
	 * Out on the lawn east of the cottage, looking a little north of west: the sun sets left of
	 * middle and the cottage fills the right half, inside the Short's crop.
	 */
	private void sunsetLawn(TestServerContext server) {
		stand(server, 34, 0, 4, -10, 9.5, -5.5);
	}

	// ------------------------------------------------------------------ scenes

	/**
	 * The vanilla clock in hand, its item name long gone and Nightfall Clock's time showing; then the
	 * clock is dropped and the time stays.
	 */
	private void drop(TestSingleplayerContext world, TestServerContext server) {
		server.runCommand("time set " + AFTERNOON);
		frontPath(server);
		chunks(world);
		until0(mc -> ((HudAccessor) mc.gui.hud).nightfallclock$toolHighlightTimer() == 0);
		ctx.waitTicks(20);
		scene = "drop";
		frame = 0;
		// The minutes tick by the whole time, with or without the clock in hand.
		for (int i = 0; i < 76; i++) {
			if (i == 36) {
				ctx.getInput().pressKey(options -> options.keyDrop);
				log("drop", "clock");
			}
			server.runCommand("time add 4");
			record(1);
		}
		check(ctx.computeOnClient(mc -> mc.player.getMainHandItem().isEmpty()), "the clock was dropped");
		// Gone before the next scene, so it isn't picked back up into the hotbar on camera.
		server.runCommand("kill @e[type=item]");
	}

	/** Switching hotbar slots: the item's name takes the clock's line, then the clock fades back. */
	private void items() {
		scene = "items";
		frame = 0;
		record(8);
		press(1, "lantern");
		record(8);
		press(2, "bread");
		until(mc -> ((HudAccessor) mc.gui.hud).nightfallclock$toolHighlightTimer() == 0);
		log("clear", "item name gone");
		record(40);
	}

	/**
	 * Early afternoon racing towards 6:00 PM with the sun sinking towards the cottage, the warning
	 * arriving as it's played through, then the sunset going on slowly behind it. The Short opens
	 * partway into the warning and ends just before that point, so it loops.
	 */
	private void dusk(TestSingleplayerContext world, TestServerContext server) {
		ctx.getInput().pressKey(options -> options.keyHotbarSlots[0]);
		server.runCommand("time set " + (DUSK - 5000));
		sunsetLawn(server);
		chunks(world);
		ctx.waitTicks(20);
		scene = "dusk";
		frame = 0;
		record(6);
		while (!ctx.computeOnClient(mc -> NightfallClock.duskShowing())) {
			check(frame < 300, "the dusk warning showed while racing through 6:00 PM");
			server.runCommand("time add 40");
			record(1);
		}
		log("dusk", "the sun's getting real low");
		for (int i = 0; i < 150; i++) {
			server.runCommand("time add 3");
			record(1);
		}
	}

	// ------------------------------------------------------------------ filming

	/** Sizes the window and the GUI, and checks both took (a screen too small for the window can shrink it). */
	private void window(int width, int height, int scale) {
		ctx.getInput().resizeWindow(width, height);
		ctx.waitTicks(3);
		ctx.runOnClient(mc -> {
			mc.options.guiScale().set(scale);
			mc.resizeGui();
		});
		ctx.waitTicks(2);
		String got = ctx.computeOnClient(mc -> mc.getWindow().getWidth() + "x" + mc.getWindow().getHeight() + " at " + mc.getWindow().getGuiScale());
		check(got.equals(width + "x" + height + " at " + scale), "the window is " + width + "x" + height + " at GUI scale " + scale + " (it is " + got + ")");
	}

	private void record(int count) {
		for (int i = 0; i < count; i++) {
			shoot();
			ctx.waitTick();
		}
	}

	private void until(Predicate<Minecraft> done) {
		for (int i = 0; i < 200; i++) {
			if (ctx.computeOnClient(done::test)) {
				return;
			}
			shoot();
			ctx.waitTick();
		}
		throw new AssertionError("Failed: " + scene + " waited ten seconds at frame " + frame);
	}

	/** Waits like `until`, but off camera, before a scene starts. */
	private void until0(Predicate<Minecraft> done) {
		for (int i = 0; i < 200; i++) {
			if (ctx.computeOnClient(done::test)) {
				return;
			}
			ctx.waitTick();
		}
		throw new AssertionError("Failed: waited ten seconds before filming");
	}

	private void shoot() {
		ctx.takeScreenshot(TestScreenshotOptions.of(String.format(Locale.ROOT, "%s_%04d", scene, frame)).disableCounterPrefix().withDestinationDir(dir));
		String cursor = ctx.computeOnClient(mc -> mc.gui.screen() == null ? "-\t-"
				: (int) mc.mouseHandler.xpos() + "\t" + (int) mc.mouseHandler.ypos());
		frames.add(scene + "\t" + frame + "\t" + cursor + "\t" + TICKS.get());
		frame++;
	}

	private void log(String kind, String what) {
		actions.add(scene + "\t" + frame + "\t" + kind + "\t" + what);
	}

	private void quiet() {
		ctx.runOnClient(mc -> {
			mc.gui.toastManager().clear();
			mc.gui.hud.getChat().clearMessages(false);
		});
	}

	/** Presses a hotbar key (slot 1-9), as a player switching items does. */
	private void press(int slot, String what) {
		ctx.getInput().pressKey(options -> options.keyHotbarSlots[slot]);
		log("key", what);
	}

	private static void check(boolean ok, String what) {
		if (!ok) {
			throw new AssertionError("Failed: " + what);
		}
	}
}
