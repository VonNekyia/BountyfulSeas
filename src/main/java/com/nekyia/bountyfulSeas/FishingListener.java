package com.nekyia.bountyfulSeas;

import com.nekyia.bountyfulSeas.fish.Fish;
import com.nekyia.bountyfulSeas.fish.FishLibrary;
import com.nekyia.bountyfulSeas.fishing.Catch;
import com.nekyia.bountyfulSeas.fishing.FishSelector;
import com.nekyia.bountyfulSeas.nexo.NexoItemFactory;
import com.nekyia.bountyfulSeas.water.WaterMap;
import com.nekyia.bountyfulSeas.water.WaterRegion;
import org.bukkit.Location;
import org.bukkit.entity.Item;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.inventory.ItemStack;

import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

/**
 * Replaces the vanilla catch with whatever lives in this water.
 *
 * <p>Everything the decision needs comes from somewhere else: the map says what
 * the water is, {@link WaterSpot} says what that means to a fish, and the selector
 * picks one. This class only knows how to ask, and when to leave well alone.
 */
final class FishingListener implements Listener {

    private final Supplier<FishLibrary> fish;
    private final Supplier<WaterMap> waterMap;

    FishingListener(Supplier<FishLibrary> fish, Supplier<WaterMap> waterMap) {
        this.fish = fish;
        this.waterMap = waterMap;
    }

    /**
     * Runs late and ignores cancelled events, so anything that wanted to stop the
     * catch outright still wins.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFish(PlayerFishEvent event) {
        if (event.getState() != PlayerFishEvent.State.CAUGHT_FISH) {
            return;
        }
        if (!(event.getCaught() instanceof Item caught)) {
            return;
        }

        WaterMap map = waterMap.get();
        if (map == null) {
            return;
        }

        Location hook = event.getHook().getLocation();
        WaterRegion region = map.regionAt(hook.getBlockX(), hook.getBlockZ());
        if (region == null) {
            // Unclassified water: vanilla keeps its catch.
            return;
        }

        WaterSpot spot = WaterSpot.of(region, hook.getWorld());
        Fish picked = FishSelector.select(fish.get().all(), spot, ThreadLocalRandom.current());
        if (picked == null) {
            return;
        }

        ItemStack stack = itemFor(picked);
        if (stack == null) {
            // Without the real item, handing over vanilla's fish beats handing over nothing.
            return;
        }

        Catch landed = Catch.roll(picked, ThreadLocalRandom.current());
        caught.setItemStack(stack);
        event.getPlayer().sendMessage(CatchMessage.of(landed, stack));
    }

    private ItemStack itemFor(Fish picked) {
        String id = FishNexoItems.nexoIdOf(picked.item());
        return id == null ? null : NexoItemFactory.create(id);
    }
}
