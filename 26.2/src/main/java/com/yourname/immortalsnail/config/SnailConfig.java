package com.yourname.immortalsnail.config;

import com.yourname.immortalsnail.ImmortalSnail;
import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.autoconfig.ConfigData;
import me.shedaniel.autoconfig.annotation.Config;
import me.shedaniel.autoconfig.annotation.ConfigEntry;
import me.shedaniel.autoconfig.serializer.Toml4jConfigSerializer;

import java.util.ArrayList;
import java.util.List;

/**
 * Server-side TOML config for the Immortal Snail mod.
 * Loaded once at server startup, reloadable via /snail reload.
 */
@Config(name = ImmortalSnail.MOD_ID + "-common")
public class SnailConfig implements ConfigData {

    @ConfigEntry.Gui.CollapsibleObject
    public Snail snail = new Snail();

    @ConfigEntry.Gui.CollapsibleObject
    public Starter starter = new Starter();

    @ConfigEntry.Gui.CollapsibleObject
    public Death death = new Death();

    /**
     * Players in this list may use /snail commands even if they are not
     * operators on the server. Entries can be either a player name
     * (case-insensitive) or a UUID with hyphens. Operators are always
     * permitted regardless of this list.
     */
    public List<String> commandAllowedPlayers = new ArrayList<>();

    public static class Snail implements ConfigData {
        public int minDistance = 5000;
        public int maxDistance = 50000;
        public double speedBlocksPerMinute = 1.0;
        /** Spider-style wall climbing when a wall blocks the path. */
        public boolean canClimbWalls = true;
        /**
         * How fast the snail climbs relative to its ground speed.
         * 1.0 = climbs as fast as it walks; 3.0 = scales walls three times
         * faster than it crosses flat ground.
         */
        public double climbSpeedMultiplier = 3.0;
        public boolean canBreakBlocks = true;
        public double breakSpeedBlocksPerSecond = 1.0 / 60.0;  // 1 block per minute
        public List<String> breakBlocksWhitelist = new ArrayList<>();
        public List<String> breakBlocksBlacklist = new ArrayList<>(List.of(
                "minecraft:bedrock",
                "minecraft:obsidian",
                "minecraft:end_portal_frame"
        ));
        public double breakProximityThreshold = 0.5;
        /**
         * How many ticks (20/sec) the snail must make no forward progress
         * before it starts chewing through the block in front of it. At the
         * default 1200 ticks (60 seconds) this only fires after the snail
         * has genuinely been stuck — climbing, sidestepping and 3-block
         * corners all stay quiet until then.
         */
        public int stuckBreakAfterTicks = 1200;
        public int chunkForceRadius = 1;
    }

    public static class Starter implements ConfigData {
        public boolean giveStarterShulker = true;
        public int bargainShulkerCount = 2;
        public int bargainShulkerSize = 27;
        public boolean includeFoodInStarter = true;
        public boolean includeBasicMaterials = true;
    }

    public static class Death implements ConfigData {
        public boolean banOnSnailKill = true;
        public String banMessage = "The snail caught you.";
        /**
         * If true (default), Totems of Undying will save a tracked player
         * from snail-attributed damage (vanilla behavior).
         *
         * If false, the mod will prevent the snail's killing blow from
         * triggering a Totem, so the player will die and be banned (or
         * whatever {@link #banOnSnailKill} is configured to do).
         */
        public boolean totemsWorkAgainstSnail = true;
    }

    // Singleton accessor
    private static SnailConfig INSTANCE;

    public static SnailConfig get() {
        if (INSTANCE == null) {
            INSTANCE = AutoConfig.register(SnailConfig.class, Toml4jConfigSerializer::new).getConfig();
        }
        return INSTANCE;
    }

    public void load() {
        // Force-load (already done by get(), but this method is here for explicit API symmetry)
        get();
    }

    public void reload() {
        // Cloth Config's AutoConfig handles file watching; calling get() returns the latest values.
        get();
    }
}
