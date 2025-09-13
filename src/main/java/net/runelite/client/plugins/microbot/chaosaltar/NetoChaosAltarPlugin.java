package net.runelite.client.plugins.microbot.chaosaltar;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.events.ChatMessage;
import net.runelite.client.config.ConfigDescriptor;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.PluginConstants;
import net.runelite.client.plugins.microbot.pluginscheduler.api.SchedulablePlugin;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.logical.AndCondition;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.logical.LockCondition;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.logical.LogicalCondition;
import net.runelite.client.plugins.microbot.pluginscheduler.event.PluginScheduleEntryPostScheduleTaskEvent;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;

@PluginDescriptor(
        name = "Neto Chaos Altar",
        description = "Automates bone offering at the Chaos Altar",
        tags = {"prayer", "bones", "altar"},
        enabledByDefault = false,
        authors = {"Neoxic"},
        version = "1.0.0",
        minClientVersion = "2.0.0",
        isExternal = PluginConstants.IS_EXTERNAL
)
@Slf4j
public class NetoChaosAltarPlugin extends Plugin implements SchedulablePlugin {
    @Inject
    private NetoChaosAltarScript netoChaosAltarScript;
    @Inject
    private NetoChaosAltarConfig config;
    @Provides
    NetoChaosAltarConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(NetoChaosAltarConfig.class);
    }

    @Inject
    private OverlayManager overlayManager;
    @Inject
    NetoChaosAltarOverlay netoChaosAltarOverlay;

    LogicalCondition stopCondition = new AndCondition();
    LockCondition lockCondition = new LockCondition("ChaosAlterPlugin",false, true);


    @Override
    protected void startUp() {
        if (overlayManager != null) {
            overlayManager.add(netoChaosAltarOverlay);
        }
        netoChaosAltarScript.run(config, this);
    }

    protected void shutDown() {
        netoChaosAltarScript.shutdown();
        if (lockCondition != null && lockCondition.isLocked()) {
            lockCondition.unlock();
        }
        overlayManager.remove(netoChaosAltarOverlay);
    }

    @Subscribe
    public void onPluginScheduleEntryPostScheduleTaskEvent(PluginScheduleEntryPostScheduleTaskEvent event) {
        try{
            if (event.getPlugin() == this) {
                Microbot.stopPlugin(this);
            }
        } catch (Exception e) {
            log.error("Error stopping plugin: ", e);
        }
    }

    public LockCondition getLockCondition(){
        return lockCondition;
    }

    @Override
    public LogicalCondition getStopCondition() {
        // Create a new stop condition
        return this.stopCondition;
    }

    @Subscribe
    public void onChatMessage(ChatMessage chatMessage) {
        if (chatMessage.getType() != ChatMessageType.GAMEMESSAGE) {
            return;
        }

        String msg = chatMessage.getMessage();
        //need to add the chat message we get when we try to attack an NPC with an empty staff.

        if (msg.contains("Oh dear, you are dead!")) {
            NetoChaosAltarScript.didWeDie = true;
        }


    }

    @Override
    public ConfigDescriptor getConfigDescriptor() {
        if (Microbot.getConfigManager() == null) {
            return null;
        }
        NetoChaosAltarConfig conf = Microbot.getConfigManager().getConfig(NetoChaosAltarConfig.class);
        return Microbot.getConfigManager().getConfigDescriptor(conf);
    }

}
