package IGoByJake.orefishfarm.menu;

import IGoByJake.orefishfarm.Registry;
import IGoByJake.orefishfarm.block.PowerTankBlockEntity;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;

public class PowerTankMenu extends TankMenu {
    public PowerTankMenu(int id, Inventory inv, PowerTankBlockEntity be, ContainerData data) {
        super(Registry.POWER_TANK_MENU.get(), id, inv, be, data, 1, 8, 17, 8, 53, -1000, -1000); // no output slot on screen
    }

    public static PowerTankMenu fromNetwork(int id, Inventory inv, FriendlyByteBuf buf) {
        if (inv.player.level().getBlockEntity(buf.readBlockPos()) instanceof PowerTankBlockEntity be) {
            return new PowerTankMenu(id, inv, be, new SimpleContainerData(5));
        }
        throw new IllegalStateException("Tank block entity missing on the client");
    }
}
