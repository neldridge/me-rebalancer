package dev.functionalae.rebalancer.client;

import dev.functionalae.rebalancer.menu.RebalancerMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public final class RebalancerScreen extends AbstractContainerScreen<RebalancerMenu> {
    private Button start, mode, scope, clear, previous, next, test, back, refresh;
    public RebalancerScreen(RebalancerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 248, 232);
    }
    @Override protected void init() {
        super.init();
        start = actionButton(12, 112, 54, 0, "start");
        mode = actionButton(70, 112, 72, 1, "once");
        scope = actionButton(146, 112, 90, 2, "all");
        scope.setTooltip(Tooltip.create(tr("scope_help")));
        test = actionButton(158, 6, 78, 4, "test_run");
        test.setTooltip(Tooltip.create(tr("test_help")));
        back = actionButton(12, 211, 64, 5, "back");
        refresh = actionButton(158, 211, 78, 4, "refresh");
        refresh.setTooltip(Tooltip.create(tr("test_help")));
        clear = actionButton(134, 44, 48, 3, "clear");
        clear.setTooltip(Tooltip.create(tr("clear_help")));
        previous = addRenderableWidget(Button.builder(Component.literal("<"), button -> { if (menu.previewView()) sendAction(6); else menu.changePage(-1); })
                .bounds(leftPos + 188, topPos + 44, 22, 14).build());
        next = addRenderableWidget(Button.builder(Component.literal(">"), button -> { if (menu.previewView()) sendAction(7); else menu.changePage(1); })
                .bounds(leftPos + 214, topPos + 44, 22, 14).build());
        previous.setTooltip(Tooltip.create(tr("previous")));
        next.setTooltip(Tooltip.create(tr("next")));
        updateButtons();
    }
    private Button actionButton(int x, int y, int width, int action, String label) {
        return addRenderableWidget(Button.builder(tr(label), button -> {
            if (action == 4) menu.setPreviewView(true);
            if (action == 5) menu.setPreviewView(false);
            sendAction(action);
            updateButtons();
        }).bounds(leftPos + x, topPos + y, width, y == 44 ? 14 : y == 6 || y > 200 ? 16 : 20).build());
    }
    private void sendAction(int action) {
        if (minecraft.gameMode != null) minecraft.gameMode.handleInventoryButtonClick(menu.containerId, action);
    }
    @Override protected void containerTick() { super.containerTick(); updateButtons(); }
    private void updateButtons() {
        start.setMessage(tr(menu.flag(1) ? "pause" : "start"));
        start.active = !menu.flag(16) && (menu.flag(1) || menu.flag(8));
        mode.setMessage(tr(menu.flag(2) ? "continuous" : "once"));
        scope.setMessage(tr(menu.flag(4) ? "all" : menu.flag(32) ? "blacklist" : "whitelist"));
        mode.active = scope.active = clear.active = !menu.flag(16);
        boolean preview = menu.previewView();
        start.visible = mode.visible = scope.visible = clear.visible = !preview;
        test.visible = !preview;
        test.active = !menu.flag(16) && menu.flag(8) && menu.status() != 5 && menu.buffered() == 0;
        back.visible = refresh.visible = preview;
        refresh.active = test.active && menu.previewState() != 1;
        previous.active = preview ? menu.previewPage() > 0 : menu.page() > 0;
        next.active = preview ? menu.previewPage() + 1 < menu.previewPages() : menu.page() < RebalancerMenu.PAGES - 1;
    }
    @Override public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractBackground(graphics, mouseX, mouseY, delta);
        int x = leftPos, y = topPos;
        graphics.fill(x, y, x + imageWidth, y + imageHeight, 0xff131c26);
        graphics.fill(x + 1, y + 1, x + imageWidth - 1, y + imageHeight - 1, 0xff293542);
        graphics.fill(x + 1, y + 1, x + imageWidth - 1, y + 3, 0xff65cbd3);
        for (var slot : menu.slots) if (slot.isActive()) {
            graphics.fill(x + slot.x - 1, y + slot.y - 1, x + slot.x + 17, y + slot.y + 17, 0xff657789);
            graphics.fill(x + slot.x, y + slot.y, x + slot.x + 16, y + slot.y + 16, 0xff15212d);
        }
    }
    @Override protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        if (menu.previewView()) { extractPreview(graphics); return; }
        graphics.text(font, title, 12, 8, 0xffe5f7f8, false);
        String[] states = {"paused", "running", "settled", "recovering", "faulted", "offline"};
        int status = Math.clamp(menu.status(), 0, states.length - 1);
        Component state = !menu.flag(8) && !menu.flag(16) && !menu.flag(4) && status == 0
                ? tr("choose_items") : Component.translatable("status.me_rebalancer." + states[status]);
        graphics.text(font, font.plainSubstrByWidth(state.getString(), 224), 12, 22, status == 4 ? 0xffff8b83 : 0xffb8d8dc, false);
        graphics.text(font, tr("moved", compact(menu.moved())), 12, 34, 0xffd0d8e0, false);
        graphics.text(font, tr("buffered", compact(menu.buffered())), 140, 34, 0xffd0d8e0, false);
        graphics.text(font, tr("filters", menu.page() + 1, RebalancerMenu.PAGES), 12, 47, 0xffe0e7ed, false);
        graphics.text(font, tr(menu.flag(4) ? "all_help" : menu.flag(32) ? "blacklist_help" : "whitelist_help"), 12, 98, 0xffaabac8, false);
        graphics.text(font, playerInventoryTitle, 43, 138, 0xffc6d0dc, false);
    }
    private void extractPreview(GuiGraphicsExtractor graphics) {
        graphics.text(font, tr("test_run"), 12, 8, 0xffe5f7f8, false);
        String[] states = {"waiting", "scanning", "ready", "limited", "unavailable", "failed", "stale"};
        graphics.text(font, font.plainSubstrByWidth(tr("preview_" + states[Math.clamp(menu.previewState(), 0, 6)]).getString(), 224),
                12, 22, 0xffb8d8dc, false);
        graphics.text(font, tr("preview_summary", menu.previewCount()), 12, 34, 0xffd0d8e0, false);
        graphics.text(font, tr("preview_page", menu.previewPage() + 1, Math.max(1, menu.previewPages())), 12, 47, 0xffe0e7ed, false);
        for (int i = 0; i < RebalancerMenu.PREVIEW_ROWS; i++) {
            var icon = menu.previewIcon(i);
            if (icon.isEmpty()) continue;
            int y = 60 + i * 28;
            var lore = icon.get(net.minecraft.core.component.DataComponents.LORE);
            String amount = lore != null && !lore.lines().isEmpty() ? lore.lines().get(0).getString() : "";
            String name = icon.getHoverName().getString();
            graphics.text(font, font.plainSubstrByWidth(name, 200), 34, y, 0xffe5edf3, false);
            graphics.text(font, font.plainSubstrByWidth(amount, 200), 34, y + 9, 0xffd0d8e0, false);
            if (lore != null && lore.lines().size() > 1)
                graphics.text(font, font.plainSubstrByWidth(lore.lines().get(1).getString(), 200), 34, y + 18, 0xffaabac8, false);
        }
        if (menu.previewCount() == 0 && menu.previewState() == 2)
            graphics.text(font, tr("preview_empty"), 34, 88, 0xffd0d8e0, false);
    }
    @Override protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        super.extractTooltip(graphics, mouseX, mouseY);
        if (!menu.previewView() && mouseX >= leftPos + 12 && mouseX < leftPos + 236 && mouseY >= topPos + 34 && mouseY < topPos + 43) {
            graphics.setTooltipForNextFrame(font, List.of(tr("moved", String.format(Locale.ROOT, "%,d", menu.moved())),
                    tr("buffered", String.format(Locale.ROOT, "%,d", menu.buffered()))), Optional.empty(), mouseX, mouseY);
        }
    }
    private static String compact(long value) {
        if (value < 1000) return Long.toString(value);
        String[] units = {"K", "M", "B", "T", "Q", "Qi"};
        double n = value;
        int unit = -1;
        while (n >= 1000 && unit < units.length - 1) { n /= 1000; unit++; }
        return String.format(Locale.ROOT, "%.1f%s", n, units[unit]);
    }
    private static Component tr(String key, Object... args) {
        return Component.translatable("gui.me_rebalancer." + key, args);
    }
}
