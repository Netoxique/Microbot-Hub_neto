package net.runelite.client.plugins.microbot.netoprayer;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.plugins.microbot.util.misc.SpecialAttackWeaponEnum;

@ConfigGroup("zprayerhelper")
public interface AttackTimerMetronomeConfig extends Config
{
	@Getter
	@RequiredArgsConstructor
        enum PrayerMode {
                NONE("None"),           // New option
                LAZY("Lazy Flick"),
                NORMAL("Normal");

                private final String description;
        }

        @Getter
        @RequiredArgsConstructor
        enum DefensivePrayerMode {
                NONE("None"),
                PERFECT_LAZY_FLICK("Perfect Lazy Flick"),
                CONTINUOUS("Continuous");

                private final String description;
        }


        @ConfigSection(
                        name = "Prayer Settings",
                        description = "Settings",
                        position = 1
        )
        String TickNumberSettings = "Attack Cooldown Tick Settings";

        @ConfigSection(
                        name = "Special Attack",
                        description = "Special attack configuration",
                        position = 2
        )
        String SpecialAttackSettings = "Special Attack Settings";

	@ConfigItem(
				position = 1,
				keyName = "enableLazyFlicking",
				name = "Enable Offensive Prayers",
				description = "Toggle the lazy flicking of offensive prayers based on attack style",
				section = TickNumberSettings

	)
	default PrayerMode enableLazyFlicking()
	{
		return PrayerMode.LAZY;
	}

        @ConfigItem(
                                position = 2,
                                keyName = "defensivePrayerMode",
                                name = "Defensive Prayer Mode",
                                description = "Choose how defensive prayers are activated",
                                section = TickNumberSettings
        )
        default DefensivePrayerMode defensivePrayerMode()
        {
                return DefensivePrayerMode.NONE;
        }

        @ConfigItem(
                                position = 3,
                                keyName = "showTick",
                                name = "Show Attack Cooldown Ticks",
                                description = "Shows number of ticks until next attack",
                                section = TickNumberSettings
        )
        default boolean showTick()
        {
                return true;
        }

        @ConfigItem(
                                position = 1,
                                keyName = "useWeaponSpec",
                                name = "Use weapon sepc.",
                                description = "Automatically use the selected special attack weapon when available.",
                                section = SpecialAttackSettings
        )
        default boolean useWeaponSpec()
        {
                return false;
        }

        @ConfigItem(
                                position = 2,
                                keyName = "specWeapon",
                                name = "Spec weapon",
                                description = "Weapon to use for special attacks.",
                                section = SpecialAttackSettings
        )
        default SpecialAttackWeaponEnum specWeapon()
        {
                return SpecialAttackWeaponEnum.DRAGON_DAGGER;
        }



}
