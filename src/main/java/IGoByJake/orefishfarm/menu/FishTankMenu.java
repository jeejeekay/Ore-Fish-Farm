package IGoByJake.orefishfarm.menu;

import IGoByJake.orefishfarm.Registry;
import IGoByJake.orefishfarm.block.FishTankBlockEntity;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;

public class FishTankMenu extends TankMenu {
    public FishTankMenu(int id, Inventory inv, FishTankBlockEntity be, ContainerData data) {
        super(Registry.FISH_TANK_MENU.get(), id, inv, be, data, 1, 8, 17, 8, 53, 152, 35);
    }

    public static FishTankMenu fromNetwork(int id, Inventory inv, FriendlyByteBuf buf) {
        if (inv.player.level().getBlockEntity(buf.readBlockPos()) instanceof FishTankBlockEntity be) {
            return new FishTankMenu(id, inv, be, new SimpleContainerData(3));
        }
        throw new IllegalStateException("Tank block entity missing on the client");
    }
}
