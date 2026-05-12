package to.wildeprojekt.modpacksettings.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;

import javax.swing.*;
import java.lang.reflect.InvocationTargetException;

public class ModpackSettingsClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
//        if (System.getProperty("os.name").toLowerCase().contains("mac")){
//            ClientLifecycleEvents.CLIENT_STARTED.register(client -> {
//                PreLaunchCheck.checkAllocatedRam();
//
//            });
//        }
    }
}
