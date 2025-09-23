package net.runelite.client.plugins.microbot.netoclanhall;

import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.TileObject;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;

@Slf4j
public class NetoClanHallScript extends Script
{
    private static final int CLAN_HALL_PORTAL_ID = 41724;
    private static final String ENTER_MEMBER_OPTION = "Enter-member";
    private static final long INTERACTION_COOLDOWN_MS = 3_000L;

    private long lastInteractionTimestamp = 0L;

    public boolean run()
    {
        Microbot.enableAutoRunOn = false;
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(this::loop, 0, 600, TimeUnit.MILLISECONDS);
        return true;
    }

    private void loop()
    {
        try
        {
            if (!Microbot.isLoggedIn() || !super.run())
            {
                return;
            }

            if (Rs2Player.isMoving() || Rs2Player.isAnimating())
            {
                return;
            }

            long now = System.currentTimeMillis();
            if (now - lastInteractionTimestamp < INTERACTION_COOLDOWN_MS)
            {
                return;
            }

            TileObject clanHallPortal = Rs2GameObject.getGameObject(CLAN_HALL_PORTAL_ID);
            if (clanHallPortal == null)
            {
                return;
            }

            if (Rs2GameObject.interact(clanHallPortal, ENTER_MEMBER_OPTION))
            {
                lastInteractionTimestamp = now;
                sleep(3000, 4000);
            }
        }
        catch (Exception ex)
        {
            log.error("Error while attempting to enter the clan hall", ex);
        }
    }

    @Override
    public void shutdown()
    {
        super.shutdown();
        lastInteractionTimestamp = 0L;
    }
}
