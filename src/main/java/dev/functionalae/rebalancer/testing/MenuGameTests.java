package dev.functionalae.rebalancer.testing;

import dev.functionalae.rebalancer.*;
import dev.functionalae.rebalancer.menu.RebalancerMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import java.util.function.Consumer;

/** Enabled only by the isolated gameTestServer run configuration, never ordinary play. */
public final class MenuGameTests {
    public static void register(IEventBus bus) {
        var functions = DeferredRegister.<Consumer<GameTestHelper>>create(Registries.TEST_FUNCTION, RebalancerMod.ID);
        functions.register("destination_labels", () -> MenuGameTests::destinationLabels);
        functions.register("menu_safety", () -> MenuGameTests::menuSafety);
        functions.register(bus);
        bus.addListener((RegisterGameTestsEvent event) -> {
            var environment = event.registerEnvironment(id("menu_environment"), new TestEnvironmentDefinition.AllOf());
            event.registerTest(id("destination_labels"), new FunctionGameTestInstance(ResourceKey.create(Registries.TEST_FUNCTION,
                    id("destination_labels")), new TestData<>(environment, Identifier.withDefaultNamespace("empty"), 100, 0, true)));
            event.registerTest(id("menu_safety"), new FunctionGameTestInstance(ResourceKey.create(Registries.TEST_FUNCTION,
                    id("menu_safety")), new TestData<>(environment, Identifier.withDefaultNamespace("empty"), 100, 0, true)));
        });
    }
    private static Identifier id(String path) { return Identifier.fromNamespaceAndPath(RebalancerMod.ID, path); }
    private static void menuSafety(GameTestHelper helper) {
        helper.setBlock(BlockPos.ZERO, RebalancerMod.BLOCK.get());
        var entity = (RebalancerBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(BlockPos.ZERO));
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        var pos = helper.absolutePos(BlockPos.ZERO);
        player.setPos(pos.getX() + .5, pos.getY() + 1, pos.getZ() + .5);
        var menu = new RebalancerMenu(1, player.getInventory(), entity);
        helper.assertTrue(menu.stillValid(player), "Nearby menu is valid");
        menu.clickMenuButton(player, 0);
        helper.assertTrue(!entity.running(), "Empty filter cannot start");
        player.getInventory().setItem(9, new ItemStack(Items.IRON_INGOT, 64));
        menu.quickMoveStack(player, 64);
        helper.assertTrue(player.getInventory().getItem(9).getCount() == 64, "Shift-click preserves all real items");
        helper.assertTrue(entity.filterStack(0).is(Items.IRON_INGOT) && entity.filterStack(0).getCount() == 1,
                "Shift-click adds a one-item ghost");
        menu.setCarried(new ItemStack(Items.GOLD_INGOT, 12));
        menu.clicked(19, 0, ContainerInput.PICKUP, player);
        helper.assertTrue(menu.getCarried().getCount() == 12, "Copying a ghost never consumes the cursor stack");
        helper.assertTrue(entity.filterStack(19).is(Items.GOLD_INGOT), "Other pages accept ghost copies");
        for (var input : new ContainerInput[]{ContainerInput.CLONE, ContainerInput.THROW,
                ContainerInput.SWAP, ContainerInput.PICKUP_ALL, ContainerInput.QUICK_CRAFT}) {
            menu.clicked(19, 0, input, player);
            helper.assertTrue(menu.getCarried().getCount() == 12 && player.getInventory().getItem(9).getCount() == 64,
                    "Special click cannot materialize or consume ghosts: " + input);
        }
        menu.clicked(19, 1, ContainerInput.PICKUP, player);
        menu.clicked(0, 1, ContainerInput.PICKUP, player);
        helper.assertTrue(!entity.canStart() && menu.getCarried().getCount() == 12, "Clearing last ghost disables start");
        menu.clickMenuButton(player, 2);
        menu.clickMenuButton(player, 0);
        helper.assertTrue(entity.allItems() && entity.running(), "All items requires explicit enable and Start");
        menu.clickMenuButton(player, 2);
        helper.assertTrue(!entity.allItems() && entity.running(), "Scope changes preserve Running, even with an empty selection");
        entity.setFilter(0, new ItemStack(Items.IRON_INGOT));
        helper.assertTrue(entity.running(), "Adding a ghost filter preserves Running");
        entity.addFilter(new ItemStack(Items.GOLD_INGOT));
        helper.assertTrue(entity.running(), "Shift-click adding a filter preserves Running");
        entity.setFilter(0, ItemStack.EMPTY);
        helper.assertTrue(entity.running(), "Removing a filter preserves Running");
        entity.menuAction(3);
        helper.assertTrue(entity.running() && !entity.canStart(), "Clear changes selection without pausing Running");
        entity.menuAction(0);
        entity.addFilter(new ItemStack(Items.IRON_INGOT));
        helper.assertTrue(!entity.running(), "Editing a paused node does not start it");
        var named = new ItemStack(Items.DIAMOND);
        named.set(DataComponents.CUSTOM_NAME, Component.literal("Component test"));
        entity.setFilter(63, named);
        entity.menuAction(1);
        var output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, helper.getLevel().registryAccess());
        entity.saveAdditional(output);
        var restored = new RebalancerBlockEntity(pos, RebalancerMod.BLOCK.get().defaultBlockState());
        restored.loadTag(TagValueInput.create(ProblemReporter.DISCARDING, helper.getLevel().registryAccess(), output.buildResult()));
        helper.assertTrue(ItemStack.isSameItemSameComponents(named, restored.filterStack(63)), "Last-page filter components survive save/reload");
        helper.assertTrue(restored.continuous() && !restored.allItems(), "Scope and run mode survive save/reload");
        var legacy = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, helper.getLevel().registryAccess());
        legacy.putBoolean("running", true);
        restored.loadTag(TagValueInput.create(ProblemReporter.DISCARDING, helper.getLevel().registryAccess(), legacy.buildResult()));
        helper.assertTrue(!restored.running() && !restored.allItems(), "Legacy empty/all mode upgrades safely to paused");
        var clientMenu = new RebalancerMenu(2, player.getInventory());
        long large = 9_000_000_123_456_789L;
        for (int i = 0; i < 4; i++) clientMenu.setData(2 + i, (int)(large >>> (16 * i)) & 0xffff);
        helper.assertTrue(clientMenu.moved() == large, "GUI count sync preserves all 64 bits");
        menu.setPreviewView(true);
        helper.assertTrue(!menu.slots.get(0).isActive() && !menu.slots.get(64).isActive(), "Preview hides filter/player slots");
        helper.assertTrue(menu.slots.get(RebalancerMenu.PREVIEW_SLOT_START).isActive(), "Preview rows are active");
        for (var input : ContainerInput.values()) menu.clicked(RebalancerMenu.PREVIEW_SLOT_START, 0, input, player);
        helper.assertTrue(menu.getCarried().getCount() == 12 && player.getInventory().getItem(9).getCount() == 64,
                "Preview rows cannot create, remove or consume real items");
        if (!entity.running()) entity.menuAction(0);
        helper.assertTrue(entity.running(), "Start the fixture before Test Run");
        menu.clickMenuButton(player, 4);
        helper.assertTrue(!entity.running() && menu.previewState() == 4, "Test Run pauses and refuses an offline fixture");
        menu.clickMenuButton(player, 5);
        helper.assertTrue(menu.previewState() == 0, "Back clears preview session");
        player.setPos(pos.getX() + 100, pos.getY(), pos.getZ());
        helper.assertTrue(!menu.stillValid(player) && !menu.clickMenuButton(player, 2), "Remote menu actions are rejected");
        helper.succeed();
    }
    private static void destinationLabels(GameTestHelper helper) {
        helper.setBlock(BlockPos.ZERO, RebalancerMod.BLOCK.get());
        var entity = helper.getLevel().getBlockEntity(helper.absolutePos(BlockPos.ZERO));
        var target = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getValue(
                Identifier.fromNamespaceAndPath("functionalstorage", "storage_controller"));
        if (target == null || target == net.minecraft.world.level.block.Blocks.AIR)
            target = net.minecraft.world.level.block.Blocks.CHEST;
        helper.setBlock(new BlockPos(1, 0, 0), target);
        var bus = appeng.core.definitions.AEParts.STORAGE_BUS.get().createPart();
        bus.setPartHostInfo(net.minecraft.core.Direction.EAST, null, entity);
        var node = (appeng.api.networking.IGridNode) java.lang.reflect.Proxy.newProxyInstance(
                MenuGameTests.class.getClassLoader(), new Class[]{appeng.api.networking.IGridNode.class},
                (proxy, method, arguments) -> {
                    if (method.getName().equals("getOwner")) return bus;
                    throw new UnsupportedOperationException(method.getName());
                });
        var grid = (appeng.api.networking.IGrid) java.lang.reflect.Proxy.newProxyInstance(
                MenuGameTests.class.getClassLoader(), new Class[]{appeng.api.networking.IGrid.class},
                (proxy, method, arguments) -> {
                    if (method.getName().equals("getNodes")) return java.util.List.of(node);
                    throw new UnsupportedOperationException(method.getName());
                });
        var labels = new dev.functionalae.rebalancer.integration.DestinationLabels(grid);
        try {
            var field = appeng.parts.storagebus.StorageBusPart.class.getDeclaredField("handler");
            field.setAccessible(true);
            var mounted = (appeng.api.storage.MEStorage) field.get(bus);
            var description = labels.describe(mounted, 123).getString();
            var destination = entity.getBlockPos().east();
            helper.assertTrue(description.contains(target.getName().getString()), "Preview names the storage-bus target block");
            helper.assertTrue(description.contains(Integer.toString(destination.getX())) && description.contains("123"),
                    "Preview includes destination coordinates and priority");
            helper.assertTrue(((dev.functionalae.rebalancer.mixin.DelegatingStorageAccessor) mounted).rebalancer$getDelegate()
                    == bus.getInternalHandler(), "Bus subclass resolves its current delegate correctly");
        } catch (ReflectiveOperationException exception) { throw new IllegalStateException(exception); }
        helper.succeed();
    }
    private MenuGameTests() {}
}
