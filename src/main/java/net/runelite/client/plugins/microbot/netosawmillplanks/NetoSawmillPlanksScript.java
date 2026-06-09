package net.runelite.client.plugins.microbot.netosawmillplanks;

import net.runelite.api.NPC;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.bank.enums.BankLocation;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

import java.util.concurrent.TimeUnit;

public class NetoSawmillPlanksScript extends Script {
//    public static final int LOG_BASKET = 28386;
    public static final String LOG_BASKET = "Log basket"; // Item name
    private static final WorldPoint EARTH_ALTAR_TELEPORT = new WorldPoint(3288, 3467, 0);
    State state = State.BANKING;
    boolean hasPerformedInitialCleanup = false;

    public boolean run() {
        hasPerformedInitialCleanup = false;
        state = State.BANKING;
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!super.run()) return;
                if (Rs2Player.isMoving() || Rs2Player.isAnimating()) return;

                switch (state) {
                    case BANKING:
                        handleBanking();
                        break;
                    case WALKING_TO_SAWMILL:
                        handleWalking();
                        break;
                    case BUYING:
                        handleBuying();
                        break;
                }
            } catch (Exception ex) {
                System.out.println(ex.getMessage());
            }
        }, 0, 100, TimeUnit.MILLISECONDS);
        return true;
    }

    private void handleBanking() {
        // 1. Bank Open/Proximity
        if (!Rs2Bank.isOpen()) {
            BankLocation nearestBank = Rs2Bank.getNearestBank();
            if (Rs2Bank.isNearBank(nearestBank, 10)) {
                Rs2Bank.openBank();
            } else {
                Rs2Bank.walkToBankAndUseBank(nearestBank);
            }
            return;
        }

        // 2. Stamina Check
        if (Rs2Player.getRunEnergy() < 10) {
            if (Rs2Bank.hasItem("Stamina potion")) {
                Rs2Bank.withdrawItem(false, "Stamina potion");
                sleepUntil(() -> Rs2Inventory.hasItem("Stamina potion"), 3000);
                sleepGaussian(400, 50);
                Rs2Inventory.interact("Stamina potion", "Drink");
            }
        }

        // 3. Deposit Excess
        if (Rs2Inventory.hasItem("Plank sack")) {
            Rs2Inventory.interact("Plank sack", "Empty");
            sleepUntil(() -> Rs2Inventory.hasItem("Mahogany plank"));
            sleepGaussian(150, 25);
        }

        if (Rs2Inventory.hasItem("Mahogany plank")) {
            Rs2Bank.depositAll("Mahogany plank");
            sleepGaussian(400, 50);
        }
        
        // Deposit anything that's not allowed
        Rs2Bank.depositAllExcept(
                "Plank sack",
                "Log basket",
                "Coins",
                "Ring of dueling",
                "Ring of the elements",
                "Crafting cape",
                "Farming cape",
                "Sailors' amulet"
        );
        
        // 4. Required Items Check
        if (!Rs2Equipment.isWearing("Ring of the elements")) {
            if (Rs2Inventory.hasItem("Ring of the elements")) {
                Rs2Inventory.interact("Ring of the elements", "Wear");
            } else {
                Rs2Bank.withdrawAndEquip("Ring of the elements");
            }
            sleepGaussian(400, 50);
        }
        if (!Rs2Inventory.hasItem("Coins")) {
            Rs2Bank.withdrawAll("Coins");
            sleepGaussian(400, 50);
        }
        if (!Rs2Inventory.hasItem("Plank sack")) {
            Rs2Bank.withdrawItem("Plank sack");
            sleepGaussian(400, 50);
        }
        if (!Rs2Inventory.hasItem(LOG_BASKET)) {
            Rs2Bank.withdrawItem(LOG_BASKET);
            sleepGaussian(400, 50);
        }

        // 4.5 Initial Cleanup
        if (!hasPerformedInitialCleanup) {
            sleepUntil(() -> Rs2Inventory.hasItem(LOG_BASKET), 3000);
            Rs2Inventory.interact(LOG_BASKET, "Empty to bank");
            sleepGaussian(400, 50);
            hasPerformedInitialCleanup = true;
        }

        // 5. Teleport Items Check
        boolean hasTeleport = Rs2Equipment.isWearing("Crafting cape") || Rs2Equipment.isWearing("Farming cape") || 
                             Rs2Equipment.isWearing("Sailors' amulet") || Rs2Equipment.isWearing("Ring of dueling") ||
                             Rs2Inventory.hasItem("Crafting cape") || Rs2Inventory.hasItem("Farming cape") || 
                             Rs2Inventory.hasItem("Sailors' amulet") || Rs2Inventory.hasItem("Ring of dueling");
        
        if (!hasTeleport) {
            if (Rs2Bank.hasItem("Crafting cape")) Rs2Bank.withdrawAndEquip("Crafting cape");
            else if (Rs2Bank.hasItem("Farming cape")) Rs2Bank.withdrawAndEquip("Farming cape");
            else if (Rs2Bank.hasItem("Sailors' amulet")) Rs2Bank.withdrawAndEquip("Sailors' amulet");
            else Rs2Bank.withdrawItem(true, "Ring of dueling");
            sleepGaussian(400, 50);
        }
        
        if (Rs2Inventory.hasItem("Crafting cape")) { Rs2Inventory.interact("Crafting cape", "Wear"); return; }
        if (Rs2Inventory.hasItem("Farming cape")) { Rs2Inventory.interact("Farming cape", "Wear"); return; }
        if (Rs2Inventory.hasItem("Sailors' amulet")) { Rs2Inventory.interact("Sailors' amulet", "Wear"); return; }

        // 6. Withdraw Logs
        if (!Rs2Inventory.isFull()) {
            Rs2Bank.withdrawAll("Mahogany logs");
            sleepGaussian(400, 50);
        }

        // 7. Close Bank
        Rs2Bank.closeBank();
        sleepUntil(() -> !Rs2Bank.isOpen());

        // 8. Fill Basket
        sleepUntil(() -> Rs2Inventory.hasItem("Mahogany logs"), 3000);
        sleepGaussian(150, 25);
        Rs2Inventory.interact(LOG_BASKET, "Fill");
        sleepGaussian(400, 50);

        // 9. Open Bank
        Rs2Bank.openBank();
        sleepUntil(Rs2Bank::isOpen);

        // 10. Withdraw logs again
        sleepGaussian(400, 50);
        Rs2Bank.withdrawAll("Mahogany logs");

        // 11. Close Bank
        Rs2Bank.closeBank();
        sleepUntil(() -> !Rs2Bank.isOpen());
        sleepGaussian(150, 25);
        
        state = State.WALKING_TO_SAWMILL;
    }

    private void handleWalking() {
        // Teleport to Earth Altar
        if (Rs2Equipment.isWearing("Ring of the elements")) {
            Rs2Equipment.interact("Ring of the elements", "Earth Altar");
            sleepUntil(() -> Rs2Player.getWorldLocation().distanceTo(EARTH_ALTAR_TELEPORT) <= 10, 10000);
        }

        // Walk to sawmill
        WorldPoint sawmillPoint = new WorldPoint(3302, 3491, 0);
        logWalk(sawmillPoint);
        state = State.BUYING;

//        if (Rs2Player.getWorldLocation().distanceTo(sawmillPoint) > 15) {
//            logWalk(sawmillPoint);
//        } else {
//            state = State.BUYING;
//        }
    }

    private void logWalk(WorldPoint dst) {
        WorldPoint myLocation = Rs2Player.getWorldLocation();
        if (myLocation == null) {
            Microbot.log("MyLocation is null");
            return;
        }
        Microbot.log("Walking from (" + myLocation.getX() + "," + myLocation.getY() + "," + myLocation.getPlane() + ") to (" + dst.getX() + "," + dst.getY() + "," + dst.getPlane() + ")");

        var future = scheduledExecutorService.submit(() -> Rs2Walker.walkTo(dst));

        while (!future.isDone()) {
            if (Rs2Player.getWorldLocation().distanceTo(dst) <= 10) {
                Rs2Walker.setTarget(null);
                future.cancel(true);
                break;
            }
            sleep(50);
        }
    }

    private void handleBuying() {
        // Sawmill Interaction
        NPC sawmillOperator = Rs2Npc.getNpc("Sawmill operator");
        if (sawmillOperator != null) {
            Rs2Npc.interact(sawmillOperator, "Buy-Plank");
            sleepUntil(() -> Rs2Widget.findWidget("Mahogany - 1,500gp") != null, 5000);
        }

        // Buy First Batch
        if (Rs2Widget.findWidget("Mahogany - 1,500gp") != null) {
            Rs2Widget.clickWidget("Mahogany - 1,500gp");
            sleepUntil(() -> !Rs2Inventory.hasItem("Mahogany logs"), 3000); // Wait for Mahogany logs to be spent
        }

//        // Planks automatically go into the sack, no need to manually fill them
//        // Fill Sack
//        if (Rs2Inventory.hasItem("Mahogany plank")) {
//            Rs2Inventory.combine("Mahogany plank", "Plank sack");
//            sleepUntil(() -> !Rs2Inventory.isFull(), 3000);
//        }

        // Empty Basket
        Rs2Inventory.interact(LOG_BASKET, "Empty");
        sleepUntil(() -> Rs2Inventory.hasItem("Mahogany logs"), 3000);
//        if (Rs2Inventory.hasItem(LOG_BASKET)) {
//            Rs2Inventory.interact(LOG_BASKET, "Empty");
//            sleepUntil(() -> Rs2Inventory.hasItem("Mahogany logs"), 3000);
//        }

        // Second Sawmill Interaction
        Rs2Npc.interact(sawmillOperator, "Buy-Plank");
        sleepUntil(() -> Rs2Widget.findWidget("Mahogany - 1,500gp") != null, 5000);

        // Buy Second Batch
        Rs2Widget.clickWidget("Mahogany - 1,500gp");
        sleepUntil(() -> Rs2Inventory.hasItem("Mahogany plank"), 3000);

        // Transition
        state = State.BANKING;
    }

    @Override
    public void shutdown() {
        super.shutdown();
    }
}
