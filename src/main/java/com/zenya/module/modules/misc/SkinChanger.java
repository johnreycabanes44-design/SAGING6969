package com.zenya.module.modules.misc;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.NativeImage;
import com.zenya.module.Category;
import com.zenya.module.Module;
import com.zenya.setting.Setting;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.ClientAsset;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.entity.player.PlayerSkin;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Renders the local player wearing another account's public skin.
 *
 * <p>Purely client-side: the public Mojang profile endpoints are read off the render
 * thread, the PNG is registered as a dynamic texture and the result is exposed through
 * {@link #getOverrideSkin(UUID)} for the player mixin. The {@link #generation} counter is
 * the only thing keeping a slow lookup from overwriting a newer one — every reply that
 * does not match the current value is dropped, so the name field can be typed into freely.
 */
public class SkinChanger extends Module {
	public static final Duration HTTP_TIMEOUT = Duration.ofSeconds(10L);
	public static final long DEBOUNCE_MS = 600L;
	public static final String MOJANG_PROFILE_URL = "https://api.mojang.com/users/profiles/minecraft/";
	public static final String SESSION_PROFILE_URL = "https://sessionserver.mojang.com/session/minecraft/profile/";
	public static final HttpClient HTTP = HttpClient.newBuilder()
			.connectTimeout(HTTP_TIMEOUT)
			.followRedirects(HttpClient.Redirect.NORMAL)
			.build();

	public static volatile PlayerSkin overrideSkin;
	public static volatile Identifier registeredId;

	public final Setting<String> playerName;
	public final AtomicInteger generation;
	public String lastObserved;
	public String lastRequested;
	public long lastEditTime;

	public SkinChanger() {
		super("SkinChanger", Category.MISC);
		this.playerName = new Setting<>("Player Name", "");
		this.generation = new AtomicInteger();
		this.lastObserved = "";
		this.lastRequested = "";
		this.lastEditTime = 0L;
		this.addSetting(this.playerName);
	}

	@Override
	public void onEnable() {
		super.onEnable();
		this.lastObserved = normalize(this.playerName.getValue());
		this.lastRequested = "";
		this.lastEditTime = System.currentTimeMillis();
		if (!this.lastObserved.isEmpty()) {
			this.fetch(this.lastObserved);
		}
	}

	@Override
	public void onDisable() {
		super.onDisable();
		this.generation.incrementAndGet();
		this.lastRequested = "";
		clearOverride();
	}

	/** Watches the name field and only fires a lookup once typing has settled. */
	@Override
	public void onTick() {
		String current = normalize(this.playerName.getValue());
		if (!Objects.equals(current, this.lastObserved)) {
			this.lastObserved = current;
			this.lastEditTime = System.currentTimeMillis();
			return;
		}
		if (current.isEmpty()) {
			if (overrideSkin != null) {
				this.lastRequested = "";
				clearOverride();
			}
			return;
		}
		if (!Objects.equals(current, this.lastRequested) && System.currentTimeMillis() - this.lastEditTime >= DEBOUNCE_MS) {
			this.fetch(current);
		}
	}

	/** @return the override skin, but only for the local player's own UUID. */
	public static PlayerSkin getOverrideSkin(UUID uuid) {
		if (overrideSkin == null || uuid == null) {
			return null;
		}
		UUID local = localUuid();
		return local != null && local.equals(uuid) ? overrideSkin : null;
	}

	/** Starts a lookup and applies it back on the client thread if it is still the newest one. */
	public void fetch(String name) {
		this.lastRequested = name;
		int requestId = this.generation.incrementAndGet();
		CompletableFuture.supplyAsync(() -> this.fetchSkin(name), Util.ioPool()).whenComplete((result, error) -> mc.execute(() -> {
			if (requestId != this.generation.get() || !this.isEnabled()) {
				return;
			}
			if (error != null) {
				this.chat("[SkinChanger] Failed: " + rootMessage(error));
				return;
			}
			if (result == null) {
				this.chat("[SkinChanger] Skin not found for: " + name);
				return;
			}
			this.applyResult(result, requestId);
		}));
	}

	/** Blocking half of the lookup; runs on the IO pool. */
	public FetchResult fetchSkin(String name) {
		try {
			UUID uuid = this.resolveUuid(name);
			TextureInfo texture = this.resolveTextureInfo(uuid);
			byte[] pngBytes = this.downloadBytes(texture.url());
			return new FetchResult(name, pngBytes, texture.skinType());
		} catch (InterruptedException interrupted) {
			Thread.currentThread().interrupt();
			throw new RuntimeException("Interrupted", interrupted);
		} catch (IOException exception) {
			throw new RuntimeException(exception.getMessage(), exception);
		}
	}

	/** Registers the downloaded PNG, releasing whatever texture the previous skin left behind. */
	public void applyResult(FetchResult result, int requestId) {
		try {
			NativeImage image = NativeImage.read(new ByteArrayInputStream(result.pngBytes()));
			DynamicTexture texture = new DynamicTexture(() -> "skin_changer_skin", image);
			Identifier id = Identifier.fromNamespaceAndPath("zenya", "skins/" + result.name().toLowerCase(Locale.ROOT));
			if (registeredId != null) {
				mc.getTextureManager().release(registeredId);
			}
			mc.getTextureManager().register(id, texture);
			registeredId = id;
			overrideSkin = new PlayerSkin(new ClientAsset.ResourceTexture(id), null, null, result.skinType(), true);
			this.chat("[SkinChanger] Applied skin: " + result.name());
		} catch (Exception exception) {
			this.chat("[SkinChanger] Failed to apply texture: " + exception.getMessage());
		}
	}

	public static synchronized void clearOverride() {
		if (registeredId != null && mc != null) {
			mc.getTextureManager().release(registeredId);
			registeredId = null;
		}
		overrideSkin = null;
	}

	public UUID resolveUuid(String name) throws IOException, InterruptedException {
		JsonObject profile = this.getJson(MOJANG_PROFILE_URL + enc(name));
		if (profile == null || !profile.has("id")) {
			throw new IOException("Player not found: " + name);
		}
		return parseUuid(profile.get("id").getAsString());
	}

	/** Digs the skin URL and model variant out of the base64 "textures" property. */
	public TextureInfo resolveTextureInfo(UUID uuid) throws IOException, InterruptedException {
		String undashed = uuid.toString().replace("-", "");
		JsonObject profile = this.getJson(SESSION_PROFILE_URL + undashed);
		if (profile == null || !profile.has("properties")) {
			throw new IOException("Profile not found for UUID: " + uuid);
		}
		for (JsonElement element : profile.getAsJsonArray("properties")) {
			if (!element.isJsonObject()) {
				continue;
			}
			JsonObject property = element.getAsJsonObject();
			String propertyName = property.has("name") ? property.get("name").getAsString() : "";
			if (!"textures".equalsIgnoreCase(propertyName)) {
				continue;
			}
			if (!property.has("value")) {
				continue;
			}
			String decoded = new String(Base64.getDecoder().decode(property.get("value").getAsString()), StandardCharsets.UTF_8);
			JsonObject payload = JsonParser.parseString(decoded).getAsJsonObject();
			JsonObject textures = payload.has("textures") ? payload.getAsJsonObject("textures") : null;
			JsonObject skin = textures != null && textures.has("SKIN") ? textures.getAsJsonObject("SKIN") : null;
			if (skin == null || !skin.has("url")) {
				break;
			}
			String model = null;
			if (skin.has("metadata")) {
				JsonObject metadata = skin.getAsJsonObject("metadata");
				if (metadata.has("model")) {
					model = metadata.get("model").getAsString();
				}
			}
			// The model field is absent for the classic body type.
			PlayerModelType skinType = "slim".equalsIgnoreCase(model) ? PlayerModelType.SLIM : PlayerModelType.WIDE;
			return new TextureInfo(skin.get("url").getAsString(), skinType);
		}
		throw new IOException("No skin texture found in profile");
	}

	public byte[] downloadBytes(String url) throws IOException, InterruptedException {
		HttpRequest request = HttpRequest.newBuilder(URI.create(url)).timeout(HTTP_TIMEOUT).GET().build();
		HttpResponse<byte[]> response = HTTP.send(request, HttpResponse.BodyHandlers.ofByteArray());
		if (response.statusCode() < 200 || response.statusCode() >= 300) {
			throw new IOException("HTTP " + response.statusCode());
		}
		return response.body();
	}

	/** @return the parsed body, or null when the profile simply does not exist (404/204/empty). */
	public JsonObject getJson(String url) throws IOException, InterruptedException {
		HttpRequest request = HttpRequest.newBuilder(URI.create(url))
				.timeout(HTTP_TIMEOUT)
				.header("Accept", "application/json")
				.GET()
				.build();
		HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
		if (response.statusCode() == 404 || response.statusCode() == 204) {
			return null;
		}
		if (response.statusCode() < 200 || response.statusCode() >= 300) {
			throw new IOException("HTTP " + response.statusCode());
		}
		String body = response.body();
		if (body == null || body.isBlank()) {
			return null;
		}
		return JsonParser.parseString(body).getAsJsonObject();
	}

	/** The API hands back UUIDs without dashes, which {@link UUID#fromString} will not take. */
	public static UUID parseUuid(String id) {
		String undashed = id.replace("-", "");
		return UUID.fromString(undashed.replaceFirst(
				"(\\p{XDigit}{8})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}+)",
				"$1-$2-$3-$4-$5"));
	}

	public static String normalize(String name) {
		return name == null ? "" : name.trim();
	}

	public static String enc(String value) {
		return URLEncoder.encode(value, StandardCharsets.UTF_8);
	}

	/** Unwraps the CompletionException chain so the chat line says something useful. */
	public static String rootMessage(Throwable error) {
		Throwable root = error;
		while (root.getCause() != null) {
			root = root.getCause();
		}
		String message = root.getMessage();
		return message == null || message.isBlank() ? root.getClass().getSimpleName() : message;
	}

	/** Falls back to the session profile id so the override still works on the main menu. */
	public static UUID localUuid() {
		if (mc == null) {
			return null;
		}
		if (mc.player != null) {
			return mc.player.getUUID();
		}
		return mc.getUser() != null ? mc.getUser().getProfileId() : null;
	}

	public void chat(String message) {
		if (mc != null && mc.gui != null) {
			try {
				mc.gui.getChat().addMessage(Component.literal(message));
			} catch (Throwable ignored) {
				// Lookups complete asynchronously, so the GUI can be torn down mid-call; a
				// missed status line is not worth propagating out of a completion handler.
			}
		}
	}

	/** A completed download, carried back from the IO pool to the client thread. */
	public record FetchResult(String name, byte[] pngBytes, PlayerModelType skinType) {}

	/** Skin URL and model variant pulled out of a profile's textures property. */
	public record TextureInfo(String url, PlayerModelType skinType) {}
}
