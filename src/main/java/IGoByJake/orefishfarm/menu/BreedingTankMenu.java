package IGoByJake.orefishfarm.menu;

import IGoByJake.orefishfarm.Registry;
import IGoByJake.orefishfarm.block.BreedingTankBlockEntity;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;

public class BreedingTankMenu extends TankMenu {
    public BreedingTankMenu(int id, Inventory inv, BreedingTankBlockEntity be, ContainerData data) {
        super(Registry.BREEDING_TANK_MENU.get(), id, inv, be, data, 2, 8, 17, 8, 35, 8, 53, 152, 35);
    }

    public static BreedingTankMenu fromNetwork(int id, Inventory inv, FriendlyByteBuf buf) {
        if (inv.player.level().getBlockEntity(buf.readBlockPos()) instanceof BreedingTankBlockEntity be) {
            return new BreedingTankMenu(id, inv, be, new SimpleContainerData(4));
        }
        throw new IllegalStateException("Tank block entity missing on the client");
    }
}
