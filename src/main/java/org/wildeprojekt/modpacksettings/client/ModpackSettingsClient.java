package org.wildeprojekt.modpacksettings.client;

import lombok.extern.slf4j.Slf4j;
import net.fabricmc.api.ClientModInitializer;

/**
 * Client-side Fabric entrypoint for Modpack Settings.
 */
@Slf4j
public class ModpackSettingsClient implements ClientModInitializer {

    /**
     * Initializes client-only Modpack Settings behaviour after Minecraft starts loading.
     */
    @Override
    public void onInitializeClient() {
        LOGGER.info("WildeProjekt Modpack settings initialized.");
    }
}
