package mc.ardacraft.ardasettings.client;

import mc.ardacraft.ardasettings.ArdaSettings;

public class PreLaunchCheck {
    public static void checkAllocatedRam(){
        // minRam is set at 7999 to avoid confusion with GiB and GB
        long minRam = 7999;
        long allocatedMB = Runtime.getRuntime().maxMemory() / (1024 * 1024);

        if (allocatedMB < minRam){
            ArdaSettings.LOGGER.info("RAM allocated is not sufficient to run this modpack.");
            PanelDialog.showAllocatedRamDialog();
        }
    }
}
