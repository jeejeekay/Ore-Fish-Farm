package IGoByJake.orefishfarm.block;

import net.minecraft.util.StringRepresentable;

/**
 * How a tank's fill is drawn. Habitats are data (orefishfarm_habitat/*.json); each one picks one of these looks,
 * and water-look habitats also pick a tint, so a pack can add new habitats without new models.
 */
public enum TankLook implements StringRepresentable {
    WATER("water", 0), LAVA("lava", 12), VOID("void", 4);

    private final String name;
    public final int light;

    TankLook(String name, int light) {
        this.name = name;
        this.light = light;
    }

    @Override
    public String getSerializedName() { return this.name; }

    public static TankLook of(String s) {
        for (TankLook l : values()) if (l.name.equals(s)) return l;
        return WATER;
    }
}
