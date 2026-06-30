package net.runelite.client.plugins.microbot.netogeseller;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.PluginConstants;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.ClientUI;
import net.runelite.client.ui.NavigationButton;
import javax.swing.SwingUtilities;

import javax.inject.Inject;
import java.awt.*;

@PluginDescriptor(
        name = "Neto GE Seller",
        description = "Sells items to GE using Flipping Utilities hotkeys",
        tags = {"neto", "ge", "grand exchange", "seller"},
        authors = {"Neto"},
        version = "1.0.0",
        minClientVersion = "2.0.0",
        enabledByDefault = false,
        isExternal = PluginConstants.IS_EXTERNAL
)
@Slf4j
public class NetoGeSellerPlugin extends Plugin {
    public static final String version = "1.0.0";

    @Inject
    private NetoGeSellerConfig config;

    @Inject
    private OverlayManager overlayManager;

    @Inject
    private NetoGeSellerOverlay overlay;

    @Inject
    private NetoGeSellerScript script;

    @Inject
    private ClientUI clientUI;

    @Inject
    private ClientToolbar clientToolbar;

    @Provides
    NetoGeSellerConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(NetoGeSellerConfig.class);
    }

    @Override
    protected void startUp() throws AWTException {
        Microbot.pauseAllScripts.compareAndSet(true, false);
        if (overlayManager != null) {
            overlayManager.add(overlay);
        }
        script.run();
        openFlippingUtilitiesPanel();
    }

    private void openFlippingUtilitiesPanel() {
        SwingUtilities.invokeLater(() -> {
            try {
                java.lang.reflect.Field sidebarEntriesField = ClientUI.class.getDeclaredField("sidebarEntries");
                sidebarEntriesField.setAccessible(true);
                @SuppressWarnings("unchecked")
                java.util.TreeSet<NavigationButton> sidebarEntries = (java.util.TreeSet<NavigationButton>) sidebarEntriesField.get(clientUI);
                
                for (NavigationButton button : sidebarEntries) {
                    if (button.getTooltip() != null && button.getTooltip().equalsIgnoreCase("Flipping Utilities")) {
                        clientToolbar.openPanel(button);
                        log.info("Opened Flipping Utilities panel");
                        break;
                    }
                }
            } catch (Exception e) {
                log.error("Failed to open Flipping Utilities panel", e);
            }
        });
    }

    @Override
    protected void shutDown() {
        script.shutdown();
        if (overlayManager != null) {
            overlayManager.remove(overlay);
        }
    }
}
