package org.wildeprojekt.modpacksettings.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import com.google.gson.annotations.SerializedName;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Loads and saves the Modpack Settings configuration file.
 */
@Getter
@Setter
@Slf4j
public class ModConfig {

    /** Gson instance used for all config serialization. */
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** Resource pack updater settings. */
    private ResourcePackConfig resourcePack = new ResourcePackConfig();

    /**
     * Loads the current config, creating or repairing it from defaults when needed.
     *
     * @return loaded config with missing values filled from defaults
     * @throws IOException if the config cannot be created, read, or repaired
     */
    public static ModConfig load() throws IOException {

        Path path = configPath();
        Files.createDirectories(path.getParent());

        if (Files.notExists(path))
            copyDefaultConfig(path);

        try {

            ModConfig loaded = readConfig(path);
            return mergeWithDefaults(loaded);

        } catch (JsonSyntaxException exception) {

            LOGGER.warn("Modpack Settings config is corrupt; backing it up and recreating it.", exception);
            Files.move(path, path.resolveSibling("config.json.bak"), StandardCopyOption.REPLACE_EXISTING);
            copyDefaultConfig(path);

            return mergeWithDefaults(readConfig(path));
        }
    }

    /**
     * Returns the filesystem location of the persisted config file.
     *
     * @return config path inside the Fabric config directory
     */
    public static Path configPath() {
        return FabricLoader.getInstance().getConfigDir().resolve("modpacksettings/config.json");
    }

    /**
     * Copies the bundled default config into the requested path.
     *
     * @param path destination config path
     * @throws IOException if the default config cannot be copied
     */
    private static void copyDefaultConfig(Path path) throws IOException {

        try (InputStream stream = ModConfig.class.getResourceAsStream("/config.json")) {
            if (stream != null) {
                Files.copy(stream, path, StandardCopyOption.REPLACE_EXISTING);
                return;
            }
        }

        LOGGER.warn("Bundled Modpack Settings default config is missing; writing code fallback defaults.");
        Files.writeString(path, GSON.toJson(new ModConfig()), StandardCharsets.UTF_8);
    }

    /**
     * Reads a config JSON file from disk.
     *
     * @param path config path to read
     * @return parsed config
     * @throws IOException if the file cannot be read
     */
    private static ModConfig readConfig(Path path) throws IOException {

        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {

            ModConfig config = GSON.fromJson(reader, ModConfig.class);
            return config == null ? new ModConfig() : config;
        }
    }

    /**
     * Applies default values to fields missing from an older or partial config.
     *
     * @param loaded config loaded from disk
     * @return config with defaults filled in
     */
    private static ModConfig mergeWithDefaults(ModConfig loaded) {
        ModConfig defaults = defaultConfig();
        ModConfig merged = loaded == null ? defaults : loaded;

        if (merged.getResourcePack() == null) {
            merged.setResourcePack(defaults.getResourcePack());
            return merged;
        }

        ResourcePackConfig resourcePack = merged.getResourcePack();
        ResourcePackConfig defaultResourcePack = defaults.getResourcePack();

        if (resourcePack.getRepository() == null)
            resourcePack.setRepository(defaultResourcePack.getRepository());

        if (resourcePack.getAssetName() == null)
            resourcePack.setAssetName(defaultResourcePack.getAssetName());

        if (resourcePack.getTargetFileName() == null)
            resourcePack.setTargetFileName(defaultResourcePack.getTargetFileName());

        if (resourcePack.getLastActivatedFileName() == null)
            resourcePack.setLastActivatedFileName(defaultResourcePack.getLastActivatedFileName());

        return merged;
    }

    /**
     * Loads the bundled default config used for filling missing persisted fields.
     *
     * @return bundled default config, or code defaults if the bundle cannot be parsed
     */
    private static ModConfig defaultConfig() {

        try (InputStream stream = ModConfig.class.getResourceAsStream("/config.json")) {

            if (stream != null) {
                ModConfig config = GSON.fromJson(new String(stream.readAllBytes(), StandardCharsets.UTF_8), ModConfig.class);
                return config == null ? new ModConfig() : config;
            }

        } catch (IOException | JsonSyntaxException exception) {
            LOGGER.warn("Could not load bundled Modpack Settings default config.", exception);
        }

        return new ModConfig();
    }

    /**
     * Saves this config to disk with an atomic replacement step.
     *
     * @throws IOException if the config cannot be written
     */
    public void save() throws IOException {

        Path path = configPath();
        Files.createDirectories(path.getParent());
        Path tempPath = Files.createTempFile(path.getParent(), "config", ".json.tmp");

        try (Writer writer = Files.newBufferedWriter(tempPath, StandardCharsets.UTF_8)) {
            GSON.toJson(this, writer);
        }

        Files.move(tempPath, path, StandardCopyOption.REPLACE_EXISTING);
    }

    /**
     * Configures the prelaunch resource pack downloader.
     *
     * <p>These field initializers are the code-level fallback for values missing
     * from {@code src/main/resources/config.json}; keep them aligned with that
     * bundled template except where the code fallback intentionally remains
     * unconfigured.</p>
     */
    @Getter
    @Setter
    public static class ResourcePackConfig {

        /** Whether the resource pack updater should run at prelaunch. */
        private boolean enabled = true;

        /** GitHub repository in owner/repo format. */
        private String repository = "";

        /** Release asset name to download from the latest GitHub release. */
        private String assetName = "Wildeprojekt_Overlay.zip";

        /** Local resource pack filename to write under the game resourcepacks directory. */
        private String targetFileName = "Wildeprojekt_Overlay.zip";

        /** Whether the updater should add the downloaded pack to Minecraft options. */
        private boolean autoEnable = true;

        /** Whether the updater should force-load the pack through Minecraft's incompatible pack whitelist. */
        @SerializedName("force_load_incompatible")
        private boolean forceLoadIncompatible = true;

        /** Published timestamp of the last successfully downloaded release. */
        private String lastPublishedAt;

        /** Resource pack filename last auto-enabled in Minecraft options. */
        private String lastActivatedFileName;

        /** Internal activation migration version last applied to this config. */
        private int activationVersion;
    }
}
