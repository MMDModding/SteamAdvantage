package com.mcmoddev.steamadvantage.machines;

import com.mcmoddev.steamadvantage.init.Power;

import com.mcmoddev.poweradvantage.api.ConduitType;
import com.mcmoddev.poweradvantage.api.PoweredEntity;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class SteamTankBlock extends com.mcmoddev.poweradvantage.api.simple.BlockSimplePowerMachine {

	public SteamTankBlock(){
		super(Material.PISTON, 0.75f, Power.steam_power);
	}

	@Override
	public PoweredEntity createNewTileEntity(World world, int metaDataValue) {
		return new SteamTankTileEntity();
	}

	@Override
	public boolean hasComparatorInputOverride(IBlockState bs) {
		return true;
	}

	@Override
	public int getComparatorInputOverride(IBlockState bs, World world, BlockPos coord) {
		TileEntity te = world.getTileEntity(coord);
		if(te instanceof SteamTankTileEntity){
			return (int)(15 * ((SteamTankTileEntity)te).getSteamLevel());
		} else{
			return 0;
		}
	}

	@Override
	public boolean isPowerSink(ConduitType conduitType) {
		return true;
	}

	@Override
	public boolean isPowerSource(ConduitType conduitType) {
		return true;
	}
}
