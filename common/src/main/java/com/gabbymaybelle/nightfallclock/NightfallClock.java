package com.gabbymaybelle.nightfallclock;

import com.gabbymaybelle.nightfallclock.mixin.ChatComponentAccessor;
import com.gabbymaybelle.nightfallclock.mixin.HudAccessor;
import java.util.Locale;
import net.minecraft.client.GuiMessage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.EffectRenderingInventoryScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.ChatVisiblity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A day counter and clock centred just above the hotbar (or in a corner of the screen). At 6:00 PM,
 * an hour before night, that line reads "The sun's getting real low" for a few seconds, and the
 * clock stays gold until it's dark enough for monsters, counting down the seconds ("dark in 42s").
 * When thunder starts, the line reads "Storm's rolling in" for a few seconds. In the Nether and the
 * End, the clock shows the Overworld's day and time.
 *
 * The time is read from where the sun actually is, not from how far through the day's
 * ticks the world is. A pack that makes the day longer, or stretches daylight more than
 * night, still gets 6:00 PM when the sun looks like vanilla's 6:00 PM.
 *
 * Each part can be switched off, the clock can move to a corner and show 12- or
 * 24-hour time (settings screen through Mod Menu or NeoForge's Mods screen, or
 * config/nightfallclock.properties).
 */
public class NightfallClock {
	public static final String MOD_ID = "nightfallclock";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	private static final int MARGIN = 4;
	/** Half of the vanilla hotbar's width (182 GUI pixels), which sits centred at the bottom. */
	private static final int HOTBAR_HALF_WIDTH = 91;
	/** Height of a row of vanilla effect icons, including the gap below it. */
	private static final int EFFECT_ROW = 26;
	private static final int WHITE = 0xFFFFFFFF;
	private static final int GOLD = 0xFFFFAA00;
	/** Vanilla's aqua text colour, for the storm message. */
	private static final int AQUA = 0xFF55FFFF;
	private static final int SUNSET_MINUTE = DuskWatch.SUNSET_MINUTE;
	private static final int NIGHT_MINUTE = 19 * 60;

	/** How long the dusk message stays up, in client ticks (20 per second), including a 1 s fade. */
	private static final int DUSK_TICKS = 8 * 20;
	private static final int FADE_TICKS = 20;
	/**
	 * A vanilla action-bar message lasts 60 ticks. Longer than this without a break means a
	 * server or mod is keeping its own text there, so the clock shows alongside it instead of hiding.
	 */
	private static final int PERMANENT_ACTION_BAR = 80;
	/** Vanilla chat sits 40 above the bottom of the screen and fades lines out after 200 ticks. */
	private static final int CHAT_BOTTOM = 40;
	private static final int CHAT_FADE_TICKS = 200;
	/** Vanilla draws action-bar text 72 GUI pixels above the bottom of the screen. */
	private static final int ACTION_BAR_OFFSET = 72;
	/** And the held item's name 59 above the bottom (in survival and adventure). */
	private static final int ITEM_NAME_OFFSET = 59;

	/**
	 * Vanilla's sun angle (degrees, 0 at noon) at each minute after noon; rises steadily to 360.
	 * The same curve as DimensionType.timeOfDay in 1.21.1, which eases the sun so it lingers near
	 * the horizon: d is how far through the day since noon, and the sun sits at (2d + e) / 3 of a
	 * turn with e = 0.5 - cos(d * pi) / 2.
	 */
	private static final float[] SUN_AT_MINUTE = new float[24 * 60];

	static {
		for (int i = 0; i < SUN_AT_MINUTE.length; i++) {
			double d = i / (double) SUN_AT_MINUTE.length;
			double e = 0.5 - Math.cos(d * Math.PI) / 2.0;
			SUN_AT_MINUTE[i] = (float) (360.0 * (d * 2.0 + e) / 3.0);
		}
	}

	private static final DuskWatch DUSK = new DuskWatch();
	private static final StormWatch STORM = new StormWatch();
	private static final DarkCountdown DARK = new DarkCountdown();
	/** The clock text last made, for which time, format and countdown, so it isn't rebuilt every frame. */
	private static DayTime shownTime;
	private static boolean shownTwelveHour;
	private static int shownSeconds = -1;
	private static Component shownText;
	private static int duskTicksLeft = 0;
	private static int stormTicksLeft = 0;
	/** The world the messages above were for: a message ends when you change worlds (a portal mid-storm). */
	private static ClientLevel messageLevel;
	/** Seconds until monsters can spawn outside, while the clock counts down to dark; -1 otherwise. */
	private static int darkSeconds = -1;
	/** Fades the clock back in (0 to FADE_TICKS) after something else had its line. */
	private static int clockShown = FADE_TICKS;
	/** How long vanilla's action bar has been showing without a break. */
	private static int actionBarTicks = 0;

	/** The clock text for this time, made again only when the time, the format or the countdown changes. */
	private static Component textFor(DayTime time) {
		boolean twelveHour = Config.timeFormat.twelveHour();
		if (!time.equals(shownTime) || twelveHour != shownTwelveHour || darkSeconds != shownSeconds || shownText == null) {
			shownTime = time;
			shownTwelveHour = twelveHour;
			shownSeconds = darkSeconds;
			shownText = describe(time, darkSeconds);
		}
		return shownText;
	}

	/**
	 * The whole clock line: "Day 4 · 6:42 PM · dark in 24s" while counting down, and
	 * "Overworld: Day 4 · 9:12 PM" in the Nether and the End.
	 */
	static Component describe(DayTime time, int seconds) {
		Component text = time.describe();
		if (seconds >= 0) {
			Component left = seconds < 60
					? Component.translatable("hud.nightfallclock.seconds", seconds)
					: Component.translatable("hud.nightfallclock.minutes", seconds / 60, String.format(Locale.ROOT, "%02d", seconds % 60));
			text = Component.translatable("hud.nightfallclock.countdown", text, left);
		}
		return time.elsewhere() ? Component.translatable("hud.nightfallclock.overworld", text) : text;
	}

	/** Whether "The sun's getting real low" is up just now. For the game tests. */
	static boolean duskShowing() {
		return duskTicksLeft > 0;
	}

	/** Whether "Storm's rolling in" is up just now. For the game tests. */
	static boolean stormShowing() {
		return stormTicksLeft > 0;
	}

	/** The seconds the clock is counting down to dark, or -1. For the game tests. */
	static int darkSeconds() {
		return darkSeconds;
	}

	/** Whether the clock above the hotbar has stepped aside for something else on its line. For the game tests. */
	static boolean clockHidden() {
		return clockShown == 0;
	}

	/** Draws the clock and the dusk message; each loader calls this once a frame from its own HUD event. */
	public static void extract(GuiGraphics graphics) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || mc.options.hideGui || mc.getDebugOverlay().showDebugScreen()) {
			return;
		}
		DayTime time = DayTime.of(mc.level);
		if (time == null) {
			return;
		}
		boolean clock = Config.clock && mc.player != null;
		Component text = textFor(time);
		// Gold from 6:00 PM until dark (7:00 PM, or later while it's still counting down).
		int colour = time.minute() >= SUNSET_MINUTE && (time.minute() < NIGHT_MINUTE || darkSeconds >= 0) ? GOLD : WHITE;

		if (clock && Config.position != Config.Position.ABOVE_HOTBAR) {
			inCorner(graphics, mc, Config.position, text, colour, mc.screen instanceof ChatScreen);
		}
		if (duskTicksLeft > 0 || stormTicksLeft > 0) {
			// A message has the line to itself, stepping around vanilla's text so it's never missed.
			// Dusk first, if both arrive together.
			boolean dusk = duskTicksLeft > 0;
			int ticksLeft = dusk ? duskTicksLeft : stormTicksLeft;
			int alpha = Math.max(8, Math.min(255, ticksLeft * 255 / FADE_TICKS));
			Component message = Component.translatable(dusk ? "hud.nightfallclock.dusk" : "hud.nightfallclock.storm");
			aboveHotbar(graphics, mc, message, (alpha << 24) | ((dusk ? GOLD : AQUA) & 0xFFFFFF), true);
		} else if (clock && Config.position == Config.Position.ABOVE_HOTBAR) {
			if (actionBarTicks > PERMANENT_ACTION_BAR) {
				// A server keeping its own text in the action bar: show alongside it instead of hiding,
				// fading in over a second like the clock does everywhere else.
				int alpha = Math.max(8, Math.min(255, (actionBarTicks - PERMANENT_ACTION_BAR) * 255 / FADE_TICKS));
				aboveHotbar(graphics, mc, text, (alpha << 24) | (colour & 0xFFFFFF), true);
			} else if (clockShown > 0) {
				// Steps aside while something else is on the line, then fades back in.
				int alpha = Math.max(8, clockShown * 255 / FADE_TICKS);
				aboveHotbar(graphics, mc, text, (alpha << 24) | (colour & 0xFFFFFF), false);
			}
		}
	}

	/**
	 * Whether something else is on the clock's line just now: the held item's name, an
	 * action-bar message, or chat (open, or recent messages reaching up over the clock).
	 */
	private static boolean lineBusy(Minecraft mc, Component text) {
		HudAccessor hud = (HudAccessor) mc.gui;
		boolean itemName = hud.nightfallclock$toolHighlightTimer() > 0 && !hud.nightfallclock$lastToolHighlight().isEmpty();
		return itemName || hud.nightfallclock$overlayMessageTime() > 0 || mc.screen instanceof ChatScreen || chatCovers(mc, text);
	}

	/**
	 * Whether recent chat reaches over the clock's line. Vanilla draws chat from 40 above the
	 * bottom upwards, newest first, one line per 9 x (line spacing + 1) pixels at the chat scale,
	 * and fades each line out 200 ticks after it arrived (ChatComponent).
	 */
	private static boolean chatCovers(Minecraft mc, Component text) {
		if (mc.options.chatVisibility().get() == ChatVisiblity.HIDDEN) {
			return false;
		}
		int guiTicks = mc.gui.getGuiTicks();
		int recent = 0;
		for (GuiMessage.Line line : ((ChatComponentAccessor) mc.gui.getChat()).nightfallclock$trimmedMessages()) {
			if (guiTicks - line.addedTime() >= CHAT_FADE_TICKS) {
				break;
			}
			recent++;
		}
		// Unfocused chat shows only so many lines, however many arrived recently.
		recent = Math.min(recent, mc.gui.getChat().getLinesPerPage());
		if (recent == 0) {
			return false;
		}
		double scale = mc.options.chatScale().get();
		int lineHeight = (int) (9.0 * (mc.options.chatLineSpacing().get() + 1.0));
		int width = mc.getWindow().getGuiScaledWidth();
		int height = mc.getWindow().getGuiScaledHeight();
		double chatTop = height - CHAT_BOTTOM - recent * lineHeight * scale;
		double chatRight = (ChatComponent.getWidth(mc.options.chatWidth().get()) + 12) * scale;
		int y = clockLineY(mc, height);
		int left = (width - mc.font.width(text)) / 2;
		return y + mc.font.lineHeight > chatTop && y < height - CHAT_BOTTOM && left < chatRight;
	}

	/** Just above the hearts and hunger, where vanilla shows the held item's name. */
	private static int clockLineY(Minecraft mc, int guiHeight) {
		return guiHeight - ITEM_NAME_OFFSET + (mc.gameMode != null && !mc.gameMode.canHurtPlayer() ? 14 : 0);
	}

	/**
	 * Centred just above the hearts and hunger, where vanilla shows the held item's name after
	 * you switch slots (Gui.renderSelectedItemName: 59 above the bottom, 14 lower in creative
	 * and spectator, which have no health bars). Drawn by this mod rather than through the
	 * action bar, which HUD mods (such as Coords HUD) overwrite. With stepAround, it moves up
	 * past the item name or an action-bar message (72 above the bottom) while they show.
	 */
	private static void aboveHotbar(GuiGraphics graphics, Minecraft mc, Component text, int colour, boolean stepAround) {
		HudAccessor hud = (HudAccessor) mc.gui;
		int bottom = graphics.guiHeight();
		int line = mc.font.lineHeight + 3;
		int y = clockLineY(mc, bottom);
		if (stepAround) {
			boolean itemName = hud.nightfallclock$toolHighlightTimer() > 0 && !hud.nightfallclock$lastToolHighlight().isEmpty();
			boolean actionBar = hud.nightfallclock$overlayMessageTime() > 0;
			if (itemName) {
				y -= line;
			}
			if (actionBar && Math.abs(y - (bottom - ACTION_BAR_OFFSET)) < line) {
				y = bottom - ACTION_BAR_OFFSET - line;
			}
		}
		// Like vanilla's item name: a dark backdrop when the player has Text Background on
		// (Accessibility settings), so it stays readable over snow and sky.
		int width = mc.font.width(text);
		textWithBackdrop(graphics, mc, text, (graphics.guiWidth() - width) / 2, y, width, colour);
	}

	/**
	 * Text with the dark backdrop vanilla puts behind the held item's name when Text Background is on
	 * (Accessibility settings). GuiGraphics has no helper for it in 1.21.1, so this is vanilla's own
	 * two steps from Gui.renderSelectedItemName: a box tinted by the colour, then the text.
	 */
	private static void textWithBackdrop(GuiGraphics graphics, Minecraft mc, Component text, int x, int y, int width, int colour) {
		int backdrop = mc.options.getBackgroundColor(0.0F);
		if (backdrop != 0) {
			graphics.fill(x - 2, y - 2, x + width + 2, y + mc.font.lineHeight + 2, FastColor.ARGB32.multiply(backdrop, colour));
		}
		graphics.drawString(mc.font, text, x, y, colour);
	}

	/** In a corner of the screen. */
	private static void inCorner(GuiGraphics graphics, Minecraft mc, Config.Position corner, Component text, int colour,
			boolean chatOpen) {
		int width = mc.font.width(text);
		// On narrow screens a bottom corner would run into the hotbar, so use the top one.
		boolean top = corner.top() || graphics.guiWidth() / 2 - HOTBAR_HALF_WIDTH < width + MARGIN * 2;
		// Chat's input box runs along the bottom of the screen while it's open.
		if (!top && chatOpen) {
			return;
		}
		int x = corner.right() ? graphics.guiWidth() - width - MARGIN : MARGIN;
		int y = !top ? graphics.guiHeight() - mc.font.lineHeight - MARGIN : corner.right() ? belowEffects(mc) : MARGIN;
		// The same dark backdrop as above the hotbar, when the player has Text Background on.
		textWithBackdrop(graphics, mc, text, x, y, width, colour);
	}

	/**
	 * Below the effect icons, which vanilla draws in the top-right corner: beneficial effects
	 * in a row at y 1-25, harmful ones in a second row at y 27-51 (both 15 lower in the demo).
	 * Icons are hidden while a screen that lists effects itself (the inventory) is open.
	 */
	private static int belowEffects(Minecraft mc) {
		Screen screen = mc.screen;
		if (mc.player == null || (screen instanceof EffectRenderingInventoryScreen<?> inventory && inventory.canSeeEffects())) {
			return MARGIN;
		}
		boolean beneficial = false;
		boolean harmful = false;
		for (MobEffectInstance effect : mc.player.getActiveEffects()) {
			if (effect.showIcon()) {
				if (effect.getEffect().value().isBeneficial()) {
					beneficial = true;
				} else {
					harmful = true;
				}
			}
		}
		int top = mc.isDemo() ? 16 : 1;
		if (harmful) {
			return top + EFFECT_ROW * 2 + MARGIN;
		}
		if (beneficial) {
			return top + EFFECT_ROW + MARGIN;
		}
		return MARGIN;
	}

	/** Once a client tick, from each loader's own tick event. */
	public static void tick(Minecraft mc) {
		if (mc.level == null || mc.player == null) {
			DUSK.update(null, -1);
			STORM.update(null, false);
			duskTicksLeft = 0;
			stormTicksLeft = 0;
			darkSeconds = -1;
			return;
		}
		if (mc.level != messageLevel) {
			messageLevel = mc.level;
			duskTicksLeft = 0;
			stormTicksLeft = 0;
		}
		if (STORM.update(mc.level, mc.level.isThundering()) && Config.stormWarning) {
			stormTicksLeft = DUSK_TICKS;
		}
		DayTime time = DayTime.of(mc.level);
		if (time == null || time.elsewhere()) {
			// No day here, or only the Overworld's (no sky of its own to watch): no dusk, no countdown.
			DUSK.update(mc.level, -1);
			duskTicksLeft = 0;
			darkSeconds = -1;
		}
		if (time == null) {
			stormTicksLeft = 0;
			return;
		}
		if (mc.isPaused()) {
			// The sun stands still while paused, so the light's fall is timed afresh afterwards.
			DARK.reset();
		} else {
			if (duskTicksLeft > 0) {
				duskTicksLeft--;
			}
			if (stormTicksLeft > 0) {
				stormTicksLeft--;
			}
			if (!time.elsewhere()) {
				darkSeconds = countdown(mc.level, time);
			}
			boolean busy = lineBusy(mc, textFor(time));
			clockShown = busy || duskTicksLeft > 0 || stormTicksLeft > 0 ? 0 : Math.min(FADE_TICKS, clockShown + 1);
			actionBarTicks = ((HudAccessor) mc.gui).nightfallclock$overlayMessageTime() > 0 ? actionBarTicks + 1 : 0;
		}
		if (!time.elsewhere() && DUSK.update(mc.level, time.minute()) && Config.duskWarning) {
			duskTicksLeft = DUSK_TICKS;
		}
	}

	/**
	 * Seconds until monsters can spawn outside, from 6:00 PM until then; -1 at any other time, with
	 * the countdown off, or in a thunderstorm (when they can spawn already). Monsters can spawn under
	 * open sky once the sky light, 15 minus the sky darkening, is at or below the dimension's monster
	 * light limit (Monster.isDarkEnoughToSpawn). The darkening is Level.updateSkyBrightness's, without
	 * its rounding down, so the light falls smoothly enough to time.
	 */
	private static int countdown(ClientLevel level, DayTime time) {
		double rain = 1.0 - level.getRainLevel(1.0F) * 5.0F / 16.0;
		double thunder = 1.0 - level.getThunderLevel(1.0F) * 5.0F / 16.0;
		double sun = 0.5 + 2.0 * Mth.clamp(Mth.cos(level.getTimeOfDay(1.0F) * (float) (Math.PI * 2)), -0.25, 0.25);
		float light = (float) (15.0 - (1.0 - sun * rain * thunder) * 11.0);
		int seconds = DARK.update(level, System.nanoTime(), light, level.dimensionType().monsterSpawnLightTest().getMaxValue());
		return Config.countdown && time.minute() >= SUNSET_MINUTE && !level.isThundering() ? seconds : -1;
	}

	/**
	 * The overworld day's position, or null when there's none to show. Elsewhere is true in a world
	 * without its own day (the Nether, the End), which shows the Overworld's: every world shares the
	 * Overworld's day count, and the server sends it to players wherever they are.
	 */
	record DayTime(int day, int minute, boolean elsewhere) {
		DayTime(int day, int minute) {
			this(day, minute, false);
		}

		static DayTime of(ClientLevel level) {
			int day = (int) (level.getDayTime() / 24000L) + 1;
			// 1.21.1 has no timelines: a dimension with a fixed time (the Nether, the End) has no day of its own.
			if (level.dimensionType().hasFixedTime()) {
				if (!Config.overworldTime) {
					return null;
				}
				// No sun to read here, so the Overworld's vanilla clock: tick 0 is 6:00 AM, 1000 ticks to the hour.
				int minute = (int) ((level.getDayTime() + 6000L) % 24000L * 60L / 1000L);
				return new DayTime(day, minute, true);
			}
			// getSunAngle is in radians, 0 at noon, after the same easing as above.
			float sunAngle = (float) Math.toDegrees(level.getSunAngle(1.0F));
			return new DayTime(day, minuteFromSun(sunAngle));
		}

		/** The vanilla clock time (minutes after midnight) at which the sun stands at this angle. */
		static int minuteFromSun(float angle) {
			float a = ((angle % 360.0F) + 360.0F) % 360.0F;
			int low = 0;
			int high = SUN_AT_MINUTE.length - 1;
			while (low < high) {
				int mid = (low + high + 1) >>> 1;
				if (SUN_AT_MINUTE[mid] <= a) {
					low = mid;
				} else {
					high = mid - 1;
				}
			}
			return (12 * 60 + low) % (24 * 60);
		}

		/**
		 * "Day 4 · 2:35 PM", or "Day 4 · 14:35" with 24-hour time. Locale.ROOT keeps the digits
		 * Latin, matching the day and hour numbers, which the game always writes that way.
		 */
		Component describe() {
			int hour24 = minute / 60;
			String minutes = String.format(Locale.ROOT, "%02d", minute % 60);
			if (!Config.timeFormat.twelveHour()) {
				return Component.translatable("hud.nightfallclock.clock_24h", day, String.format(Locale.ROOT, "%02d", hour24), minutes);
			}
			int hour12 = hour24 % 12 == 0 ? 12 : hour24 % 12;
			return Component.translatable("hud.nightfallclock.clock", day, hour12, minutes,
					Component.translatable(hour24 < 12 ? "hud.nightfallclock.am" : "hud.nightfallclock.pm"));
		}
	}
}
