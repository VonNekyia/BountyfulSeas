package com.nekyia.bountyfulSeas.fish;

import java.util.List;

/**
 * The outcome of reading the fish folder: everything that loaded, and everything
 * that did not. A load never fails as a whole - a broken file costs its own fish
 * and nothing else.
 */
public record FishLoadResult(FishLibrary library, List<FishProblem> problems) {

    public FishLoadResult {
        problems = List.copyOf(problems);
    }

    public boolean hasProblems() {
        return !problems.isEmpty();
    }
}
