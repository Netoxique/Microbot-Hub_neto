package net.runelite.client.plugins.microbot.netoherbrun;

import lombok.Getter;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.questhelper.helpers.mischelpers.farmruns.CropState;
import net.runelite.client.plugins.microbot.questhelper.helpers.mischelpers.farmruns.FarmingHandler;
import net.runelite.client.plugins.microbot.questhelper.helpers.mischelpers.farmruns.FarmingPatch;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.enums.Herbs;

import java.util.HashMap;
import java.util.Objects;

@Getter
public class HerbPatch {
    private final FarmingPatch patch;
    private final String regionName;
    private final CropState prediction;
    private final WorldPoint location;
    private boolean enabled;
    private final HashMap<String, Integer> items = new HashMap<>();

    public HerbPatch(FarmingPatch patch, FarmingHandler farmingHandler) {
        this.patch = patch;
        this.regionName = patch.getRegion().getName();
        this.prediction = farmingHandler.predictPatch(patch);
        this.location = getHerbFromName(regionName).getWorldPoint();
        switch (regionName) {
            case "Ardougne":
//                if (Rs2Bank.hasItem("Ardougne cloak")) {
//                    this.items.put("Ardougne cloak", 1);
//                }
                this.enabled = true;
                break;
            case "Catherby":
                this.items.put("Camelot teleport", 1);
                this.enabled = true;
                break;
            case "Civitas illa Fortis":
                this.items.put("Civitas illa fortis teleport", 1);
                this.enabled = Rs2Player.getQuestState(Quest.CHILDREN_OF_THE_SUN) == QuestState.FINISHED;
                break;
            case "Falador":
                this.items.put("Explorer's ring", 1);
                this.enabled = true;
                break;
            case "Farming Guild":
                this.items.put("Skills necklace(", 1);
                this.enabled = Rs2Player.getRealSkillLevel(Skill.FARMING) >= 65;
                break;
            case "Kourend":
                this.items.put("Xeric's talisman", 1);
                this.enabled = true;
                break;
            case "Morytania":
                this.items.put("Ectophial", 1);
                this.enabled = Rs2Player.getQuestState(Quest.PRIEST_IN_PERIL) == QuestState.FINISHED;
                break;
            case "Troll Stronghold":
                this.items.put("Stony basalt", 1);
                this.enabled = Rs2Player.getQuestState(Quest.MY_ARMS_BIG_ADVENTURE) == QuestState.FINISHED;
                break;
            case "Weiss":
                this.items.put("Icy basalt", 1);
                this.enabled = Rs2Player.getQuestState(Quest.MAKING_FRIENDS_WITH_MY_ARM) == QuestState.FINISHED;
                break;
            case "Harmony":
                this.enabled = false;
                break;

        }
    }

    /**
     * Gets a Herbs enum value from its string name
     * @param regionName The region name (e.g., "Ardougne")
     * @return The matching Herbs enum value, or NONE if not found
     */
    private static Herbs getHerbFromName(String regionName) {
        for (Herbs herb : Herbs.values()) {
            if (herb.getName().equalsIgnoreCase(regionName)) {
                return herb;
            }
        }
        return Herbs.NONE;
    }

    public boolean isInRange(int distance) {
        if(Objects.equals(regionName, "Weiss")) {
         return Rs2Player.getWorldLocation().getRegionID() == 11325;

        } else if(Objects.equals(regionName, "Troll Stronghold")) {
            return Rs2Player.getWorldLocation().getRegionID() == 11321;
        } else {
            return Rs2Player.getWorldLocation().distanceTo(location) < distance;
        }
    }


    public boolean contains(WorldPoint worldPoint) {
        return location.equals(worldPoint);
    }

    public boolean contains(String regionName) {
        return this.regionName.equals(regionName);
    }

}
