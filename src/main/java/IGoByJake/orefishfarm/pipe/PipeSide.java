package IGoByJake.orefishfarm.pipe;

import net.minecraft.util.StringRepresentable;

/**
 * What one side of a Pipe Tank shows: nothing joined (the tube's glass side), a tube running on to the next pipe,
 * a tube ending in a black rim where it meets an inventory (reaching 2 px into the inventory's block), or a capped
 * dead end (the same rim, kept inside the block).
 */
public enum PipeSide implements StringRepresentable {
    NONE("none"), PIPE("pipe"), RIM("rim"), CAP("cap");

    private final String name;

    PipeSide(String name) { this.name = name; }

    @Override
    public String getSerializedName() { return this.name; }
}
