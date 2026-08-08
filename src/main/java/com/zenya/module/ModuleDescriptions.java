package com.zenya.module;

import java.util.Locale;
import java.util.Map;

/**
 * Tooltip text for every module, keyed by the module's lower-cased, trimmed name.
 *
 * <p>Lookups are lenient on purpose: a null or unknown name yields an empty string
 * rather than null, so the GUI can hand the result straight to the text renderer.
 */
public class ModuleDescriptions {
	public static final Map<String, String> DESCRIPTIONS = Map.ofEntries(
		Map.entry("anchor macro", "Auto places and breaks respawn anchors"),
		Map.entry("auto crystal", "Automatically attacks with end crystals"),
		Map.entry("auto double hand", "Swaps offhand items mid-combat for double damage"),
		Map.entry("auto hit crystal", "Hits any crystal that lands near you"),
		Map.entry("auto inv totem", "Pulls totems from your inventory automatically"),
		Map.entry("auto totem", "Keeps a totem in your offhand at all times"),
		Map.entry("double anchor", "Plays both anchors in a single tick for max damage"),
		Map.entry("hover totem", "Holds a totem in offhand only when you take damage"),
		Map.entry("safe anchor", "Places anchors only when the blast is safe enough"),
		Map.entry("shield breaker", "Disables enemy shields automatically"),
		Map.entry("spear swap", "Swaps to a trident for combat ranged attacks"),
		Map.entry("triggerbot", "Auto-attacks any entity in your crosshair"),
		Map.entry("silent chunk finder", "Silently detects bases via tall grass density — one chunk overlay only"),
		Map.entry("activity debug", "Logs Donut activity packets for debugging"),
		Map.entry("anti trap", "Detects and avoids common PvP traps"),
		Map.entry("bone dropper bot", "Auto-drops bones in farms"),
		Map.entry("chunk finder", "Finds bases via geological anomalies"),
		Map.entry("chunk reload", "Forces a render-distance chunk reload underground"),
		Map.entry("fake roles", "Spoofs Donut roles client-side"),
		Map.entry("fake stats", "Spoofs your displayed stats client-side"),
		Map.entry("fake pay", "Fakes /pay confirmations locally"),
		Map.entry("base marker", "Highlights chunks with large storage setups"),
		Map.entry("auto relog", "Disconnects at void Y after a join grace period"),
		Map.entry("region map", "Shows your Donut region on a draggable map"),
		Map.entry("deltasensor", "Finds bases via growth + cluster analysis"),
		Map.entry("growth finder", "Finds nearby crops ready for harvest"),
		Map.entry("spawner protect", "Pre-places blocks to protect spawners"),
		Map.entry("spawner tags", "Shows mob type labels above nearby spawners"),
		Map.entry("auto log", "Disconnects automatically when in danger"),
		Map.entry("auto mine", "Automatically mines the block you are looking at"),
		Map.entry("auto tool", "Switches to the best tool for the block you mine"),
		Map.entry("coord snapper", "Snaps your X/Z to round numbers when stopped"),
		Map.entry("fast place", "Removes the place delay so you build faster"),
		Map.entry("freelook", "Look around freely without changing your facing"),
		Map.entry("home setter", "Bind a hotkey to teleport home"),
		Map.entry("name protect", "Hides your username in chat and over your head"),
		Map.entry("nametags", "Customises entity nametags above heads"),
		Map.entry("skinchanger", "Applies another player's skin to you client-side"),
		// ponytail: "skin protect" reuses the skinchanger description verbatim
		Map.entry("skin protect", "Applies another player's skin to you client-side"),
		Map.entry("sprint", "Always sprint when you move forward"),
		Map.entry("swing speed", "Tweaks your hand swing animation speed"),
		Map.entry("tab detector", "Notifies when a player opens their tab list"),
		Map.entry("weather notifier", "Pings you on weather changes"),
		Map.entry("amethyst esp", "Highlights amethyst clusters through walls"),
		Map.entry("block esp", "Highlights configured blocks through walls"),
		Map.entry("freecam", "Detaches the camera from your body"),
		Map.entry("full bright", "Removes all darkness from the world"),
		Map.entry("hole esp", "Highlights one-block holes for crystal PvP"),
		Map.entry("jump circles", "Renders jump-distance circles around players"),
		Map.entry("light esp", "Highlights underground block light sources"),
		Map.entry("mob esp", "Highlights mobs through walls"),
		Map.entry("no render", "Hides selected world elements (rain, fire, ...)"),
		Map.entry("player esp", "Highlights other players through walls"),
		Map.entry("storage esp", "Highlights chests and storage containers"),
		Map.entry("hand view", "Changes first-person hand and item rendering"),
		Map.entry("bedrock holes esp", "Highlights holes in bedrock layers"),
		Map.entry("player chunk finder", "Detects player-modified underground chunks"),
		Map.entry("cloud configs", "Save and load configs from the cloud"),
		Map.entry("friends", "Manage your friends list"),
		Map.entry("zenya +", "Theming and global Frost settings"),
		Map.entry("hud", "Configure on-screen HUD elements"),
		Map.entry("radio", "Plays local Frost radio tracks"),
		Map.entry("rpc", "Shows Frost Client in Discord Rich Presence"),
		Map.entry("spotify hud", "Spotify now-playing widget"),
		Map.entry("themes", "Pick a colour theme for the client")
	);

	public static String get(String moduleName) {
		if (moduleName == null) {
			return "";
		}
		return DESCRIPTIONS.getOrDefault(moduleName.toLowerCase(Locale.ROOT).trim(), "");
	}
}
