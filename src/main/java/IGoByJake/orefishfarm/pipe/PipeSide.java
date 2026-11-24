package IGoByJake.orefishfarm.pipe;

import net.minecraft.util.StringRepresentable;

/**
 * What one side of a Pipe Tank shows: nothing joined (the tube's glass side), a tube running on to the next pipe,
 * or a tube ending in a black rim. A rim is drawn where the pipe meets an inventory and at a dead end.
 */
public enum PipeSide implements StringRepresentable {
    NONE("none"), PIPE("pipe"), RIM("rim");

    private final String name;

    PipeSide(String name) { this.name = name; }

    @Override
    public String getSerializedName() { return this.name; }
}
