package net.runelite.client.plugins.microbot.netosailingsalv;

import lombok.extern.slf4j.Slf4j;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.netosailingsalv.features.salvaging.SalvagingScript;
import net.runelite.client.plugins.microbot.netosailingsalv.features.trials.TrialsScript;

import javax.inject.Inject;
import java.util.concurrent.TimeUnit;

@Slf4j
public class NetoSailingSalvScript extends Script {

    private final NetoSailingSalvConfig config;
    private final SalvagingScript salvagingFeature;
    private final TrialsScript trialsFeature;

	@Inject
	public NetoSailingSalvScript(NetoSailingSalvConfig config, SalvagingScript salvagingFeature, TrialsScript trialsFeature) {
		this.config = config;
		this.salvagingFeature = salvagingFeature;
		this.trialsFeature = trialsFeature;
	}

    public boolean run() {
        Microbot.enableAutoRunOn = false;
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn()) return;
                if (!super.run()) return;

                if (config.salvaging()) {
                    salvagingFeature.run(config);
                }

                if (config.trials()) {
                    trialsFeature.run(config);
                }


            } catch (Exception ex) {
                log.trace("Exception in main loop: ", ex);
            }
        }, 0, 100, TimeUnit.MILLISECONDS);
        return true;
    }
    
    @Override
    public void shutdown() {
        super.shutdown();
    }
}
