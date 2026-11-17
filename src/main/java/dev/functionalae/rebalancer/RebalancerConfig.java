package dev.functionalae.rebalancer;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class RebalancerConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.IntValue ITEMS_PER_TICK;
    public static final ModConfigSpec.IntValue CHECKS_PER_TICK;
    public static final ModConfigSpec.IntValue MAX_MOUNTS;
    public static final ModConfigSpec.IntValue IDLE_TICKS;
    public static final ModConfigSpec.DoubleValue AE_PER_ITEM;
    static {
        var builder = new ModConfigSpec.Builder();
        ITEMS_PER_TICK = builder.comment("Maximum migrated items per active tick.").defineInRange("itemsPerTick", 4096, 1, 65536);
        CHECKS_PER_TICK = builder.comment("Maximum source keys or exhausted sources examined per tick.")
                .defineInRange("checksPerTick", 64, 1, 1024);
        MAX_MOUNTS = builder.comment("Pause on larger networks to bound destination checks per tick.")
                .defineInRange("maximumMountedInventories", 512, 2, 8192);
        IDLE_TICKS = builder.comment("Delay between settled scans in continuous mode.").defineInRange("idleTicks", 40, 1, 1200);
        AE_PER_ITEM = builder.comment("AE energy per attempted item transfer, in addition to 2 AE/t idle usage.")
                .defineInRange("aePerItem", 0.125, 0.001, 128.0);
        SPEC = builder.build();
    }
    private RebalancerConfig() {}
}
