package org.wildeprojekt.modpacksettings.client;

import lombok.extern.slf4j.Slf4j;
import org.wildeprojekt.modpacksettings.resourcepack.ResourcePackGrabber;

/**
 * Contains prelaunch validation checks for the Minecraft client environment.
 */
@Slf4j
public class PreLaunchCheck {

    /**
     * Checks whether the JVM has enough maximum heap memory allocated for the modpack.
     */
    public static void checkAllocatedRam() {

        // minRam is set at 7999 to avoid confusion with GiB and GB
        long minRam = 7999;

        long allocatedMB = Runtime.getRuntime().maxMemory() / (1024 * 1024);

        if (allocatedMB < minRam) {
            LOGGER.info("RAM allocated is not sufficient to run this modpack.");
            PanelDialog.showAllocatedRamDialog();
        }
    }

    /**
     * Checks whether the configured resource packs should be updated before launch.
     */
    public static void checkResourcePack() {

        try {

            ResourcePackGrabber.run();

        } catch (Exception exception) {

            LOGGER.warn("Resource pack update failed. Existing resource pack(s) will be used.", exception);
            PanelDialog.showResourcePackFailureDialog(exception.getMessage());
        }
    }
}
