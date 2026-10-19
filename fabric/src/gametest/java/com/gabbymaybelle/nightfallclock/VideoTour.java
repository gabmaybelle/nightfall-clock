package com.gabbymaybelle.nightfallclock;

import com.gabbymaybelle.nightfallclock.mixin.HudAccessor;
import com.mojang.blaze3d.platform.InputConstants;
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
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.biome.Biomes;

/**
 * Films the vertical YouTube Short: a screenshot every tick (20 a second) in a tall window, on the
 * porch of a cottage when its build file is passed in (`-Pcottage=<.mcfunction>`), or on open
 * ground without it. Frames, `actions.tsv` (what happened at which frame, for the sounds) and
 * `frames.tsv` (where the pointer was) go in the game folder's `video_tour`. Only runs with
 * `./gradlew :fabric:runClientGameTest -PvideoTour`; tools/video turns the frames into the Short.
 */
public class VideoTour implements FabricClientGameTest {
	/** Vanilla ticks: 0 is 6:00 AM, 1000 ticks to the hour, so 12000 is 6:00 PM. */
	private static final int DUSK = 12000;

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
		// GUI scale 3. The world scenes are filmed in a bigger window at scale 4 and cropped to
		// 1080x1440 from the bottom middle when assembled, which makes the clock a third bigger on a
		// phone; the settings scene is filmed at 1080x1440 itself, so the whole screen shows.
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
			dusk(world, server);
			morning(server);
			items();
			settings();
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
		// A flat lawn around the cottage, in slices small enough for a single fill.
		int y = cottage.getY();
		// Cleared well to the west, so no tree stands in front of the setting sun.
		for (int x = cottage.getX() - 60; x <= cottage.getX() + 24; x += 6) {
			String xs = x + " ", xe = (x + 5) + " ";
			int z0 = cottage.getZ() - 30, z1 = cottage.getZ() + 18;
			server.runCommand("fill " + xs + (y - 3) + " " + z0 + " " + xe + (y - 2) + " " + z1 + " dirt");
			server.runCommand("fill " + xs + (y - 1) + " " + z0 + " " + xe + (y - 1) + " " + z1 + " grass_block");
			server.runCommand("fill " + xs + y + " " + z0 + " " + xe + (y + 20) + " " + z1 + " air");
		}
		build(server, cottage);
		ctx.waitTicks(2);
		server.runCommand("kill @e[type=item]");
		// Something cozy in the first few slots, for the item names; an empty hand to start.
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
	 * On the front path by the porch steps, looking west over the lawn to where the sun sets, with
	 * the cottage's corner at the edge. (The porch itself is too narrow to see the sunset from.)
	 */
	private void porch(TestServerContext server) {
		stand(server, 1.5, 0, 1.5, -14, 4, -2);
	}

	// ------------------------------------------------------------------ scenes

	/**
	 * Late afternoon racing towards 6:00 PM, the warning arriving as it's played through, then the
	 * sunset going on slowly behind it. The Short opens partway into this take (the warning already
	 * up) and ends just before that point, so it loops.
	 */
	private void dusk(TestSingleplayerContext world, TestServerContext server) {
		server.runCommand("time set " + (DUSK - 1300));
		porch(server);
		chunks(world);
		ctx.waitTicks(20);
		scene = "dusk";
		frame = 0;
		record(6);
		while (!ctx.computeOnClient(mc -> NightfallClock.duskShowing())) {
			check(frame < 300, "the dusk warning showed while racing through 6:00 PM");
			server.runCommand("time add 20");
			record(1);
		}
		log("dusk", "the sun's getting real low");
		for (int i = 0; i < 150; i++) {
			server.runCommand("time add 3");
			record(1);
		}
	}

	/** Morning on the same porch, the clock racing through the hours. */
	private void morning(TestServerContext server) {
		server.runCommand("time set 23000");
		porch(server);
		ctx.waitTicks(20);
		scene = "morning";
		frame = 0;
		for (int i = 0; i < 110; i++) {
			server.runCommand("time add 40");
			record(1);
		}
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
	 * The settings, opened from Mod Menu's Mods screen when Mod Menu is there (the video run adds
	 * it): the top-left corner and 24-hour time, then back to the world to see it. (The bottom
	 * corners would sit under the Shorts title.)
	 */
	private void settings() {
		int width = 1080;
		int height = 1440;
		window(width, height, 3);
		boolean modMenu = FabricLoader.getInstance().isModLoaded("modmenu");
		if (modMenu) {
			ctx.setScreen(() -> (Screen) construct("com.terraformersmc.modmenu.gui.ModsScreen"));
			ctx.getInput().typeChars("nightfall clock");
			ctx.runOnClient(mc -> selectInModMenu(mc.gui.screen()));
		} else {
			ctx.setScreen(() -> new SettingsScreen(null));
		}
		ctx.waitTicks(5);
		scene = "settings";
		frame = 0;
		ctx.getInput().setCursorPos(width * 0.62, height * 0.55);
		record(4);
		if (modMenu) {
			int[] gear = ctx.computeOnClient(mc -> {
				AbstractWidget button = (AbstractWidget) field(mc.gui.screen(), "configureButton");
				return new int[] { button.getX() + button.getWidth() / 2, button.getY() + button.getHeight() / 2 };
			});
			moveTo(gear[0], gear[1], 5);
			ctx.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
			log("click", "configure");
			until(mc -> mc.gui.screen() instanceof SettingsScreen);
			record(2);
		}
		for (int i = 0; i < 2; i++) {
			clickOption("nightfallclock.options.position", i == 0 ? 5 : 1);
		}
		clickOption("nightfallclock.options.timeFormat", 4);
		check(Config.position == Config.Position.TOP_LEFT, "two clicks move the clock to the top left");
		check(!Config.timeFormat.twelveHour(), "one click switches to 24-hour time");
		record(6);
		// Straight back to the world (Escape would step back through Mod Menu first).
		ctx.setScreen(() -> null);
		log("key", "close");
		record(30);
		Config.position = Config.Position.ABOVE_HOTBAR;
		Config.timeFormat = Config.TimeFormat.TWELVE_HOUR;
	}

	/** Selects Nightfall Clock in Mod Menu's list, as clicking it would, so its settings button shows. */
	private static void selectInModMenu(Screen screen) {
		try {
			Object list = field(screen, "modList");
			for (Object entry : (List<?>) list.getClass().getMethod("children").invoke(list)) {
				Object mod = entry.getClass().getMethod("getMod").invoke(entry);
				if (NightfallClock.MOD_ID.equals(mod.getClass().getMethod("getId").invoke(mod))) {
					Class<?> type = Class.forName("com.terraformersmc.modmenu.gui.widget.entries.ModListEntry");
					list.getClass().getMethod("setSelected", type).invoke(list, entry);
					screen.getClass().getMethod("updateSelectedEntry", type).invoke(screen, entry);
					return;
				}
			}
		} catch (ReflectiveOperationException e) {
			throw new AssertionError("couldn't select Nightfall Clock in Mod Menu", e);
		}
		throw new AssertionError("Failed: Mod Menu lists Nightfall Clock");
	}

	private static Object construct(String type) {
		try {
			return Class.forName(type).getConstructor(Screen.class).newInstance((Screen) null);
		} catch (ReflectiveOperationException e) {
			throw new AssertionError("couldn't make " + type, e);
		}
	}

	/** A private field of Mod Menu's (which the game test doesn't compile against). */
	private static Object field(Object owner, String name) {
		try {
			var f = owner.getClass().getDeclaredField(name);
			f.setAccessible(true);
			return f.get(owner);
		} catch (ReflectiveOperationException e) {
			throw new AssertionError("no field " + name + " on " + owner.getClass().getSimpleName(), e);
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

	/** Moves the pointer to the option button whose caption key is `key` and clicks it. */
	private void clickOption(String key, int steps) {
		String label = Component.translatable(key).getString();
		int[] at = ctx.computeOnClient(mc -> {
			AbstractWidget button = find(mc.gui.screen(), label);
			return button == null ? null : new int[] { button.getX() + button.getWidth() / 2, button.getY() + button.getHeight() / 2 };
		});
		check(at != null, "the settings screen has " + label);
		moveTo(at[0], at[1], steps);
		ctx.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
		log("click", label);
		record(2);
	}

	/** The first widget, at any depth (the options sit inside a list), whose caption starts with `label`. */
	private static AbstractWidget find(ContainerEventHandler parent, String label) {
		for (GuiEventListener child : parent.children()) {
			if (child instanceof AbstractWidget widget && widget.getMessage().getString().startsWith(label)) {
				return widget;
			}
			if (child instanceof ContainerEventHandler inner) {
				AbstractWidget found = find(inner, label);
				if (found != null) {
					return found;
				}
			}
		}
		return null;
	}

	private void moveTo(int guiX, int guiY, int steps) {
		double scale = ctx.computeOnClient(mc -> (double) mc.getWindow().getGuiScale());
		double fromX = ctx.computeOnClient(mc -> mc.mouseHandler.xpos());
		double fromY = ctx.computeOnClient(mc -> mc.mouseHandler.ypos());
		for (int i = 1; i <= steps; i++) {
			double t = (double) i / steps;
			t = t * t * (3 - 2 * t);
			ctx.getInput().setCursorPos(fromX + (guiX * scale - fromX) * t, fromY + (guiY * scale - fromY) * t);
			record(1);
		}
	}

	private static void check(boolean ok, String what) {
		if (!ok) {
			throw new AssertionError("Failed: " + what);
		}
	}
}
