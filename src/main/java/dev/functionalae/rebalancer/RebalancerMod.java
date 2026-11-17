package dev.functionalae.rebalancer;

import appeng.api.AECapabilities;
import appeng.blockentity.AEBaseBlockEntity;
import com.mojang.logging.LogUtils;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.*;
import org.slf4j.Logger;
import java.util.Set;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.flag.FeatureFlags;
import dev.functionalae.rebalancer.menu.RebalancerMenu;

@Mod(RebalancerMod.ID)
public final class RebalancerMod {
    public static final String ID = "me_rebalancer";
    public static final Logger LOGGER = LogUtils.getLogger();
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(ID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(ID);
    public static final DeferredRegister<BlockEntityType<?>> ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, ID);
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, ID);
    public static final DeferredHolder<MenuType<?>, MenuType<RebalancerMenu>> MENU = MENUS.register("me_rebalancer",
            () -> new MenuType<>(RebalancerMenu::new, FeatureFlags.DEFAULT_FLAGS));
    public static final DeferredBlock<RebalancerBlock> BLOCK = BLOCKS.registerBlock("me_rebalancer", RebalancerBlock::new);
    public static final DeferredItem<BlockItem> ITEM = ITEMS.registerSimpleBlockItem("me_rebalancer", BLOCK);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<RebalancerBlockEntity>> ENTITY =
            ENTITIES.register("me_rebalancer", () -> new BlockEntityType<>(RebalancerBlockEntity::new, Set.of(BLOCK.get())));

    public RebalancerMod(IEventBus bus, ModContainer container) {
        BLOCKS.register(bus); ITEMS.register(bus); ENTITIES.register(bus); MENUS.register(bus);
        if (Boolean.getBoolean("me_rebalancer.gameTests"))
            dev.functionalae.rebalancer.testing.MenuGameTests.register(bus);
        container.registerConfig(ModConfig.Type.SERVER, RebalancerConfig.SPEC);
        bus.addListener(this::setup);
        bus.addListener(this::capabilities);
        bus.addListener(this::creative);
    }
    private void setup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            dev.functionalae.rebalancer.integration.Ae2StorageAccess.verifyBridge();
            LOGGER.info("ME Rebalancer: AE2 storage bridge verified");
            BLOCK.get().setBlockEntity(RebalancerBlockEntity.class, ENTITY.get(), null, null);
            AEBaseBlockEntity.registerBlockEntityItem(ENTITY.get(), ITEM.get());
        });
    }
    private void capabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(AECapabilities.IN_WORLD_GRID_NODE_HOST, ENTITY.get(), (entity, context) -> entity);
    }
    private void creative(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS) event.accept(ITEM.get());
    }
}
