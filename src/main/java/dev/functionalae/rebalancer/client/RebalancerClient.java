package dev.functionalae.rebalancer.client;

import dev.functionalae.rebalancer.RebalancerMod;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

@EventBusSubscriber(modid = RebalancerMod.ID, value = Dist.CLIENT)
public final class RebalancerClient {
    @SubscribeEvent
    public static void screens(RegisterMenuScreensEvent event) {
        event.register(RebalancerMod.MENU.get(), RebalancerScreen::new);
    }
    @SubscribeEvent
    public static void started(net.neoforged.neoforge.client.event.lifecycle.ClientStartedEvent event) {
        if (Boolean.getBoolean("me_rebalancer.devWindow"))
            net.minecraft.client.Minecraft.getInstance().getWindow().setTitle("ME Rebalancer - GUI Development");
    }
    private RebalancerClient() {}
}
