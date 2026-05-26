package org.wildeprojekt.modpacksettings.client;

import org.wildeprojekt.modpacksettings.ModpackSettings;

public class PreLaunchCheck {
    public static void checkAllocatedRam(){
        // minRam is set at 7999 to avoid confusion with GiB and GB
        long minRam = 7999;
        long allocatedMB = Runtime.getRuntime().maxMemory() / (1024 * 1024);

        if (allocatedMB < minRam){
            ModpackSettings.LOGGER.info("RAM allocated is not sufficient to run this modpack.");
            PanelDialog.showAllocatedRamDialog();
        }
    }
}
