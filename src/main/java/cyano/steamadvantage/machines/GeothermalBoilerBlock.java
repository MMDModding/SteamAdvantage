package cyano.steamadvantage.machines;

import cyano.poweradvantage.api.ConduitType;
import cyano.poweradvantage.api.PoweredEntity;
import cyano.poweradvantage.conduitnetwork.ConduitRegistry;
import cyano.poweradvantage.init.Fluids;
import cyano.steamadvantage.init.Power;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Explosion;
import net.minecraft.world.World;

public class GeothermalBoilerBlock extends cyano.poweradvantage.api.simple.BlockSimplePowerMachine{

	public GeothermalBoilerBlock() {
		super(Material.PISTON, 0.75f, Power.steam_power, Fluids.fluidConduit_general);
	}

	@Override
	public PoweredEntity createNewTileEntity(World world, int metaDataValue) {
		return new GeothermalBoilerTileEntity();
	}

	@Override
	public boolean hasComparatorInputOverride(IBlockState bs) {
		return true;
	}

	@Override
	public int getComparatorInputOverride(IBlockState bs, World world, BlockPos coord) {
		if(world.getTileEntity(coord) instanceof GeothermalBoilerTileEntity){
			return ((GeothermalBoilerTileEntity)world.getTileEntity(coord)).getComparatorOutput();
		}
		return 0;
	}
///// Overrides to make this a multi-type block /////
@Override
public boolean isPowerSink(ConduitType type){
	return !ConduitType.areSameType(Power.steam_power, type);
}

	@Override
	public boolean isPowerSource(ConduitType type){
		return ConduitType.areSameType(Power.steam_power, type);
	}
	/**
	 * This method is called whenever the block is placed into the world
	 */
	@Override
	public void onBlockAdded(World w, BlockPos coord, IBlockState state){
		super.onBlockAdded(w, coord, state);
		ConduitRegistry.getInstance().conduitBlockPlacedEvent(w, w.provider.getDimension(), coord, cyano.poweradvantage.init.Fluids.fluidConduit_general);
	}
	
	/**
	 * This method is called when the block is removed from the world by an entity.
	 */
	@Override
	public void onPlayerDestroy(World w, BlockPos coord, IBlockState state){
		super.onPlayerDestroy(w, coord, state);
		ConduitRegistry.getInstance().conduitBlockPlacedEvent(w, w.provider.getDimension(), coord, cyano.poweradvantage.init.Fluids.fluidConduit_general);
	}
	/**
	 * This method is called when the block is destroyed by an explosion.
	 */
	@Override
	public void onExplosionDestroy(World w, BlockPos coord, Explosion boom){
		super.onExplosionDestroy(w, coord, boom);
		ConduitRegistry.getInstance().conduitBlockPlacedEvent(w, w.provider.getDimension(), coord, Fluids.fluidConduit_general);
	}
	

	///// end multi-type overrides /////

}
