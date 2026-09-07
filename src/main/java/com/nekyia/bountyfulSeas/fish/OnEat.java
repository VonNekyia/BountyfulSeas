package com.nekyia.bountyfulSeas.fish;

import java.util.List;

/**
 * What eating a fish does.
 *
 * <p>A fish only carries this when its definition has an {@code on_eat} block.
 * Without one the fish is not edible at all, which is why {@link Fish#onEat()}
 * is null rather than an empty default - "no on_eat" and "on_eat that does
 * nothing" are different things and have to stay tellable apart.
 *
 * @param saturation how much saturation eating it restores
 * @param effects    the effects applied on eating, possibly empty
 */
public record OnEat(double saturation, List<FishEffect> effects) {

    public OnEat {
        effects = List.copyOf(effects);
    }
}
