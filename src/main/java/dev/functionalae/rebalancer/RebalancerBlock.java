package dev.functionalae.rebalancer;

import appeng.block.AEBaseEntityBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** Opens the server-backed configuration menu. */
public final class RebalancerBlock extends AEBaseEntityBlock<RebalancerBlockEntity> {
    public RebalancerBlock(Properties properties) { super(metalProps(properties).strength(2.0f)); }
    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                                        Player player, BlockHitResult hit) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof RebalancerBlockEntity entity)
            openMenu(player, entity);
        return InteractionResult.SUCCESS;
    }
    @Override protected InteractionResult useItemOn(ItemStack held, BlockState state, Level level,
            BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (held.getItem() instanceof appeng.api.implementations.items.IMemoryCard)
            return super.useItemOn(held, state, level, pos, player, hand, hit);
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof RebalancerBlockEntity entity)
            openMenu(player, entity);
        return InteractionResult.SUCCESS;
    }
    private void openMenu(Player player, RebalancerBlockEntity entity) {
        player.openMenu(new net.minecraft.world.SimpleMenuProvider(
                (id, inventory, viewer) -> new dev.functionalae.rebalancer.menu.RebalancerMenu(id, inventory, entity),
                Component.translatable("block.me_rebalancer.me_rebalancer")));
    }
}
