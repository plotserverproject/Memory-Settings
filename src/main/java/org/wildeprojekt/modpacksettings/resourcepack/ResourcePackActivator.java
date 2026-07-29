package org.wildeprojekt.modpacksettings.resourcepack;

import com.google.gson.*;
import lombok.extern.slf4j.Slf4j;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/**
 * Enables a downloaded resource pack in Minecraft's options file before startup.
 */
@Slf4j
public final class ResourcePackActivator {

    /** Gson instance used for compact options array serialization. */
    private static final Gson GSON = new Gson();

    /** Prefix for the Minecraft options line that stores selected resource packs. */
    private static final String RESOURCE_PACKS_PREFIX = "resourcePacks:";

    /** Prefix for the Minecraft options line that stores incompatible resource packs. */
    private static final String INCOMPATIBLE_RESOURCE_PACKS_PREFIX = "incompatibleResourcePacks:";

    /** Private constructor for this utility class. */
    private ResourcePackActivator() {
    }

    /**
     * Adds the target resource pack to Minecraft's selected resource packs.
     *
     * @param targetFileName        resource pack zip filename under the resourcepacks directory
     * @param forceLoadIncompatible true to also whitelist the pack in incompatibleResourcePacks
     * @return true if options.txt was changed
     * @throws IOException if the options file cannot be read or written
     */
    public static boolean activate(String targetFileName, boolean forceLoadIncompatible) throws IOException {

        Path optionsPath = FabricLoader.getInstance().getGameDir().resolve("options.txt");
        String packEntry = "file/" + targetFileName;

        if (Files.notExists(optionsPath)) {
            JsonArray resourcePacks = new JsonArray();
            resourcePacks.add("vanilla");
            resourcePacks.add(packEntry);
            List<String> lines = new ArrayList<>();
            lines.add(RESOURCE_PACKS_PREFIX + GSON.toJson(resourcePacks));
            if (forceLoadIncompatible) {
                JsonArray incompatibleResourcePacks = new JsonArray();
                incompatibleResourcePacks.add(packEntry);
                lines.add(INCOMPATIBLE_RESOURCE_PACKS_PREFIX + GSON.toJson(incompatibleResourcePacks));
            }
            writeOptions(optionsPath, lines);
            return true;
        }

        List<String> lines = Files.readAllLines(optionsPath, StandardCharsets.UTF_8);
        int resourcePacksIndex = findLine(lines, RESOURCE_PACKS_PREFIX);
        int incompatibleResourcePacksIndex = findLine(lines, INCOMPATIBLE_RESOURCE_PACKS_PREFIX);
        JsonArray resourcePacks = resourcePacksIndex >= 0
                ? parseArrayLine(lines.get(resourcePacksIndex), RESOURCE_PACKS_PREFIX)
                : defaultResourcePacks(packEntry);
        JsonArray incompatibleResourcePacks = forceLoadIncompatible && incompatibleResourcePacksIndex >= 0
                ? parseArrayLine(lines.get(incompatibleResourcePacksIndex), INCOMPATIBLE_RESOURCE_PACKS_PREFIX)
                : null;

        if (resourcePacks == null || forceLoadIncompatible && incompatibleResourcePacksIndex >= 0 && incompatibleResourcePacks == null) {
            LOGGER.warn("Could not parse resource pack options; leaving options.txt unchanged.");
            return false;
        }

        boolean changed = resourcePacksIndex < 0;
        if (!contains(resourcePacks, packEntry)) {
            resourcePacks.add(packEntry);
            changed = true;
        }

        if (forceLoadIncompatible) {
            if (incompatibleResourcePacks == null) {
                incompatibleResourcePacks = new JsonArray();
                incompatibleResourcePacks.add(packEntry);
                changed = true;
            } else if (!contains(incompatibleResourcePacks, packEntry)) {
                incompatibleResourcePacks.add(packEntry);
                changed = true;
            }
        }

        if (!changed)
            return false;

        List<String> updatedLines = new ArrayList<>(lines);
        String resourcePacksLine = RESOURCE_PACKS_PREFIX + GSON.toJson(resourcePacks);

        if (resourcePacksIndex >= 0)
            updatedLines.set(resourcePacksIndex, resourcePacksLine);
        else
            updatedLines.add(resourcePacksLine);

        if (forceLoadIncompatible && incompatibleResourcePacksIndex >= 0)
            updatedLines.set(incompatibleResourcePacksIndex, INCOMPATIBLE_RESOURCE_PACKS_PREFIX + GSON.toJson(incompatibleResourcePacks));
        else if (forceLoadIncompatible)
            updatedLines.add(INCOMPATIBLE_RESOURCE_PACKS_PREFIX + GSON.toJson(incompatibleResourcePacks));

        writeOptions(optionsPath, updatedLines);
        return true;
    }

    /**
     * Writes options lines through a temporary file and replaces the original.
     *
     * @param optionsPath options file path
     * @param lines       options lines to write
     * @throws IOException if writing or moving the file fails
     */
    private static void writeOptions(Path optionsPath, List<String> lines) throws IOException {

        Files.createDirectories(optionsPath.getParent());

        Path tempPath = Files.createTempFile(optionsPath.getParent(), "options", ".txt.tmp");

        Files.write(tempPath, lines, StandardCharsets.UTF_8);
        Files.move(tempPath, optionsPath, StandardCopyOption.REPLACE_EXISTING);
    }

    /**
     * Finds the first options line with the requested prefix.
     *
     * @param lines  options file lines
     * @param prefix line prefix to find
     * @return matching line index, or -1 when absent
     */
    private static int findLine(List<String> lines, String prefix) {

        for (int index = 0; index < lines.size(); index++)
            if (lines.get(index).startsWith(prefix))
                return index;

        return -1;
    }

    /**
     * Parses a JSON array from an options line.
     *
     * @param line   full options line
     * @param prefix options key prefix
     * @return parsed array, or null when the line is malformed
     */
    private static JsonArray parseArrayLine(String line, String prefix) {

        try {

            JsonElement element = JsonParser.parseString(line.substring(prefix.length()));
            return element.isJsonArray() ? element.getAsJsonArray() : null;

        } catch (JsonParseException | IllegalStateException exception) {

            return null;
        }
    }

    /**
     * Builds the selected pack array for an options file missing the resourcePacks line.
     *
     * @param packEntry resource pack entry to add
     * @return default selected resource packs array
     */
    private static JsonArray defaultResourcePacks(String packEntry) {

        JsonArray resourcePacks = new JsonArray();
        resourcePacks.add("vanilla");
        resourcePacks.add(packEntry);

        return resourcePacks;
    }

    /**
     * Checks whether a JSON string array contains a value.
     *
     * @param array array to inspect
     * @param value value to find
     * @return true when the array contains the value
     */
    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    private static boolean contains(JsonArray array, String value) {

        for (JsonElement element : array)
            if (element.isJsonPrimitive() && value.equals(element.getAsString()))
                return true;

        return false;
    }
}
