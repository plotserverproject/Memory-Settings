package org.wildeprojekt.modpacksettings;

import org.wildeprojekt.modpacksettings.client.PreLaunchCheck;
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;

public class ModpackSettingsPrelaunch implements PreLaunchEntrypoint {
    @Override
    public void onPreLaunch(){
        if (!System.getProperty("os.name").toLowerCase().contains("mac")){
            PreLaunchCheck.checkAllocatedRam();
        }

    }
}
