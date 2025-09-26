package net.runelite.client.plugins.microbot.banksbankstander;

import com.google.common.base.Strings;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.PluginConstants;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.ImageUtil;

import javax.inject.Inject;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

@PluginDescriptor(
        name = PluginDescriptor.Bank + "Bank's BankStander",
        description = "For Skilling at the Bank",
        tags = {"bankstander", "bank.js", "bank", "eXioStorm", "storm"},
        authors = {"Bankjs"},
        version = BanksBankStanderPlugin.version,
        minClientVersion = "2.0.7",
        iconUrl = "https://chsami.github.io/Microbot-Hub/BanksBankStanderPlugin/assets/icon.png",
        cardUrl = "https://chsami.github.io/Microbot-Hub/BanksBankStanderPlugin/assets/card.png",
        enabledByDefault = PluginConstants.DEFAULT_ENABLED,
        isExternal = PluginConstants.IS_EXTERNAL
)
@Slf4j
public class BanksBankStanderPlugin extends Plugin {
    public static final String version = "3.0.0";
    static final String CONFIG_GROUP = "BankStander";
    private static final String SAVED_STATES_KEY = "savedStates";
    private static final String LAST_STATE_KEY = "lastState";
    private static final Type SAVED_STATES_TYPE = new TypeToken<LinkedHashMap<String, BanksBankStanderState>>() {
    }.getType();

    private final Gson gson = new Gson();
    private final Map<String, BanksBankStanderState> savedStates = new LinkedHashMap<>();

    private BanksBankStanderPanel panel;
    private NavigationButton navButton;
    private String lastSelectedState;
    private volatile boolean overlayActive;
    private volatile long overlayHideAt;

    @Inject
    private BanksBankStanderConfig config;

    @Inject
    private ConfigManager configManager;

    @Provides
    BanksBankStanderConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(BanksBankStanderConfig.class);
    }

    @Inject
    private OverlayManager overlayManager;
    @Inject
    private BanksBankStanderOverlay banksBankStanderOverlay;

    @Inject
    private ClientToolbar clientToolbar;

    @Inject
    BanksBankStanderScript banksBankStanderScript;


    @Override
    protected void startUp() throws AWTException {
        if (overlayManager != null) {
            overlayManager.add(banksBankStanderOverlay);
        }
        loadSavedStates();

        BanksBankStanderState initialState = !Strings.isNullOrEmpty(lastSelectedState)
                ? savedStates.get(lastSelectedState)
                : null;

        if (initialState != null) {
            applyState(initialState, false);
        }

        addPanel();
    }
    ///* Added by Storm
    @Subscribe
    public void onItemContainerChanged(ItemContainerChanged inventory){
        if(inventory.getContainerId()==93){
            if (Rs2Bank.isOpen()) {
                return;
            }

            BanksBankStanderScript.itemsProcessed++;
            if (BanksBankStanderScript.secondItemId != null) { // Use secondItemId if it's available
                if (Arrays.stream(inventory.getItemContainer().getItems())
                        .anyMatch(x -> x.getId() == BanksBankStanderScript.secondItemId)) {
                    // average is 1800, max is 2400~
                    BanksBankStanderScript.previousItemChange = System.currentTimeMillis();
                    //System.out.println("still processing items");
                } else {
                    BanksBankStanderScript.previousItemChange = (System.currentTimeMillis() - 2500);
                }
            } else { // Use secondItemIdentifier if secondItemId is null
                Rs2ItemModel item = Rs2Inventory.get(config.secondItemIdentifier());
                if (item != null) {
                    // average is 1800, max is 2400~
                    BanksBankStanderScript.previousItemChange = System.currentTimeMillis();
                    //System.out.println("still processing items");
                } else {
                    BanksBankStanderScript.previousItemChange = (System.currentTimeMillis() - 2500);
                }
            }
        }
    }
    @Subscribe
    public void onWidgetLoaded(WidgetLoaded widget){
        if (widget.getGroupId()==270) {
            if(BanksBankStanderScript.isWaitingForPrompt) {
                BanksBankStanderScript.isWaitingForPrompt = false;
            }
        }
    }
    //*/ Added by Storm
    protected void shutDown() {
        stopScript();
        overlayActive = false;
        overlayHideAt = 0;
        overlayManager.remove(banksBankStanderOverlay);
        removePanel();
        savedStates.clear();
        lastSelectedState = null;
    }

    private void addPanel() {
        if (clientToolbar == null || panel != null) {
            return;
        }

        panel = new BanksBankStanderPanel(this);

        final BufferedImage icon = ImageUtil.loadImageResource(BanksBankStanderPlugin.class, "icon.png");
        navButton = NavigationButton.builder()
                .tooltip("Bank's BankStander")
                .icon(icon)
                .priority(5)
                .panel(panel)
                .build();

        clientToolbar.addNavigation(navButton);
    }

    private void removePanel() {
        if (clientToolbar != null && navButton != null) {
            clientToolbar.removeNavigation(navButton);
            navButton = null;
        }
        panel = null;
    }

    private void loadSavedStates() {
        savedStates.clear();

        if (configManager == null) {
            return;
        }

        try {
            String raw = configManager.getConfiguration(CONFIG_GROUP, SAVED_STATES_KEY);
            if (!Strings.isNullOrEmpty(raw)) {
                Map<String, BanksBankStanderState> loaded = gson.fromJson(raw, SAVED_STATES_TYPE);
                if (loaded != null) {
                    loaded.forEach((name, state) -> {
                        if (state != null) {
                            state.setName(name);
                            savedStates.put(name, state);
                        }
                    });
                }
            }
        } catch (Exception ex) {
            log.warn("Failed to load saved BankStander states", ex);
        }

        String storedState = configManager.getConfiguration(CONFIG_GROUP, LAST_STATE_KEY);
        if (!Strings.isNullOrEmpty(storedState) && savedStates.containsKey(storedState)) {
            lastSelectedState = storedState;
        } else {
            lastSelectedState = null;
        }
    }

    private void persistSavedStates() {
        if (configManager == null) {
            return;
        }

        if (savedStates.isEmpty()) {
            configManager.unsetConfiguration(CONFIG_GROUP, SAVED_STATES_KEY);
            return;
        }

        String json = gson.toJson(savedStates, SAVED_STATES_TYPE);
        configManager.setConfiguration(CONFIG_GROUP, SAVED_STATES_KEY, json);
    }

    public void saveState(String name, BanksBankStanderState state) {
        if (Strings.isNullOrEmpty(name) || state == null) {
            return;
        }

        state.setName(name);
        savedStates.put(name, state);
        persistSavedStates();
        setLastSelectedState(name);
        applyState(state, true);
    }

    public void deleteState(String name) {
        if (Strings.isNullOrEmpty(name)) {
            return;
        }

        BanksBankStanderState removed = savedStates.remove(name);
        if (removed != null) {
            persistSavedStates();
            if (Objects.equals(lastSelectedState, name)) {
                setLastSelectedState(null);
            }
        }
    }

    public void applyState(BanksBankStanderState state, boolean restartScript) {
        if (state == null || configManager == null) {
            return;
        }

        state.apply(configManager);

        if (restartScript) {
            restartScript();
        }
    }

    private void restartScript() {
        if (!isScriptRunning()) {
            return;
        }

        banksBankStanderScript.shutdown();
        banksBankStanderScript.run(config);
    }

    public Collection<String> getSavedStateNames() {
        return new ArrayList<>(savedStates.keySet());
    }

    public BanksBankStanderState getSavedState(String name) {
        return savedStates.get(name);
    }

    public BanksBankStanderState getCurrentConfigState() {
        return BanksBankStanderState.fromConfig(config);
    }

    public String getLastSelectedState() {
        return lastSelectedState;
    }

    public void setLastSelectedState(String name) {
        if (configManager == null) {
            lastSelectedState = name;
            return;
        }

        if (Strings.isNullOrEmpty(name)) {
            lastSelectedState = null;
            configManager.unsetConfiguration(CONFIG_GROUP, LAST_STATE_KEY);
        } else {
            lastSelectedState = name;
            configManager.setConfiguration(CONFIG_GROUP, LAST_STATE_KEY, name);
        }
    }

    public boolean shouldDisplayOverlay()
    {
        if (!overlayActive)
        {
            return false;
        }

        if (isScriptRunning())
        {
            return true;
        }

        if (overlayHideAt == 0L)
        {
            overlayHideAt = System.currentTimeMillis() + 5000L;
        }

        if (System.currentTimeMillis() < overlayHideAt)
        {
            return true;
        }

        overlayActive = false;
        overlayHideAt = 0L;
        return false;
    }

    public void startScript()
    {
        if (banksBankStanderScript == null || banksBankStanderScript.isRunning())
        {
            return;
        }

        banksBankStanderScript.run(config);
        overlayActive = true;
        overlayHideAt = 0L;
        if (panel != null)
        {
            panel.updateStartStopButton();
        }
    }

    public void stopScript()
    {
        if (banksBankStanderScript == null || !banksBankStanderScript.isRunning())
        {
            return;
        }

        banksBankStanderScript.shutdown();
        overlayActive = true;
        overlayHideAt = System.currentTimeMillis() + 5000L;
        if (panel != null)
        {
            panel.updateStartStopButton();
        }
    }

    public boolean isScriptRunning()
    {
        return banksBankStanderScript != null && banksBankStanderScript.isRunning();
    }
}
