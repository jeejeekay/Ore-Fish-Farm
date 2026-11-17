package IGoByJake.orefishfarm.block;

import net.minecraft.util.StringRepresentable;

/** What a tank does: shed scales and breed (fish), or make FE from power fish (power, set by a Dynamo Coil). */
public enum TankRole implements StringRepresentable {
    FISH("fish"), POWER("power");

    private final String name;

    TankRole(String name) { this.name = name; }

    @Override
    public String getSerializedName() { return this.name; }
}
