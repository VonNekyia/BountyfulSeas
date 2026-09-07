package com.nekyia.bountyfulSeas.fish;

/**
 * One reason a fish definition was rejected.
 *
 * @param file    the file the problem was found in, relative to the fish folder
 * @param fishId  the fish the problem belongs to, or {@code null} when the whole file failed
 * @param message what is wrong, phrased so it can be fixed without reading the source
 */
public record FishProblem(String file, String fishId, String message) {

    public static FishProblem file(String file, String message) {
        return new FishProblem(file, null, message);
    }

    public static FishProblem fish(String file, String fishId, String message) {
        return new FishProblem(file, fishId, message);
    }

    @Override
    public String toString() {
        return fishId == null
                ? file + ": " + message
                : file + " -> " + fishId + ": " + message;
    }
}
