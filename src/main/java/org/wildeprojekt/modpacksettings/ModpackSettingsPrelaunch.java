package org.wildeprojekt.modpacksettings;

import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;
import org.wildeprojekt.modpacksettings.client.PreLaunchCheck;

/**
 * Fabric prelaunch entrypoint that runs checks before the Minecraft client starts.
 */
public class ModpackSettingsPrelaunch implements PreLaunchEntrypoint {

    /**
     * Performs client-only prelaunch validation before Minecraft reads client options.
     */
    @Override
    public void onPreLaunch() {

        // Client only mod - servers should skip verifications
        if (FabricLoader.getInstance().getEnvironmentType() != EnvType.CLIENT)
            return;

        if (!System.getProperty("os.name").toLowerCase().contains("mac"))
            PreLaunchCheck.checkAllocatedRam();

        PreLaunchCheck.checkResourcePack();
    }
}
