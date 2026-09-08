package com.nekyia.bountyfulSeas.fish;

/**
 * One reason a fish definition was rejected.
 *
 * @param file    the file the problem was found in, relative to the fish folder
 * @param fishId  the fish the problem belongs to, or {@code null} when the whole file failed
 * @param message what is wrong, phrased so it can be fixed without reading the source
 */
public record FishProblem(String file, String fishId, String message, boolean fatal) {

    public static FishProblem file(String file, String message) {
        return new FishProblem(file, null, message, true);
    }

    public static FishProblem fish(String file, String fishId, String message) {
        return new FishProblem(file, fishId, message, true);
    }

    /**
     * Something worth saying that does not cost the fish its place.
     *
     * <p>For settings that used to exist. A key removed from the plugin should not
     * turn every config written before it into a rejection - the file is out of
     * date, not wrong, and dropping the line is all that is needed.
     */
    public static FishProblem notice(String file, String fishId, String message) {
        return new FishProblem(file, fishId, message, false);
    }

    @Override
    public String toString() {
        return fishId == null
                ? file + ": " + message
                : file + " -> " + fishId + ": " + message;
    }
}
