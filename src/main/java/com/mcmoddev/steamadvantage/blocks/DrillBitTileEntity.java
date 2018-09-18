package com.mcmoddev.steamadvantage.blocks;

import com.mcmoddev.basemetals.init.Materials;
import com.mcmoddev.steamadvantage.data.SteamAdvantageNames;
import com.mcmoddev.steamadvantage.init.Blocks;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ITickable;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class DrillBitTileEntity extends TileEntity implements ITickable{

	public final static float ROTATION_PER_TICK = 360f / 20f; // in degrees, 20 ticks per revolution
	public float rotation = 0;
	private EnumFacing direction = EnumFacing.DOWN;
	
	public DrillBitTileEntity(){
		super();
	}
	
	@Override
	public void update(){
		if(getWorld().isRemote){
			rotation = ROTATION_PER_TICK * (getWorld().getTotalWorldTime() % 20);
		}
	}
	public void setDirection(EnumFacing dir){
		this.direction = dir;
	}
	
	public EnumFacing.Axis getDirection(){
		return direction.getAxis();
	}
	
	public static void createDrillBitBlock(World w, BlockPos coord, EnumFacing dir){
		w.setBlockState(coord, Materials.DEFAULT.getBlock(SteamAdvantageNames.DRILLBIT).getDefaultState());
		DrillBitTileEntity te = new DrillBitTileEntity();
		te.direction = dir;
		w.setTileEntity(coord, te);
	}
	
	/**
	 * Destroys all drillbits connected to this one
	 */
	public void destroyLine(){
		this.destroy(this.direction);
		this.destroy(this.direction.getOpposite());
		getWorld().setBlockToAir(getPos()); // redundant because this is being called on block destruction
	}
	/**
	 * destroys upstream drillbit
	 */
	private void destroy(EnumFacing f){
		BlockPos coord = this.getPos().offset(f);
		while(getWorld().getBlockState(coord).getBlock() == Materials.DEFAULT.getBlock(SteamAdvantageNames.DRILLBIT)){
			getWorld().setBlockToAir(coord);
			coord = coord.offset(f);
		}
	}
	
	@Override
	public NBTTagCompound writeToNBT(NBTTagCompound root){
		super.writeToNBT(root);
		root.setByte("dir", (byte)direction.getIndex());
		return root;
	}
	

	@Override
	public void readFromNBT(NBTTagCompound root){
		super.readFromNBT(root);
		if(root.hasKey("dir")){
			this.direction = EnumFacing.byIndex(root.getByte("dir"));
		}
	}

	/**
	 * Turns the data field NBT into a network packet
	 */
	@Override 
	public SPacketUpdateTileEntity getUpdatePacket(){
		NBTTagCompound nbtTag = new NBTTagCompound();
		nbtTag.setByte("d",(byte)direction.getIndex());
		return new SPacketUpdateTileEntity(this.pos, 0, nbtTag);
	}
	/**
	 * Receives the network packet made by <code>getDescriptionPacket()</code>
	 */
	@Override
	public void onDataPacket(NetworkManager net, SPacketUpdateTileEntity packet) {
		NBTTagCompound tag = packet.getNbtCompound();
		if(tag.hasKey("d")){
			this.direction = EnumFacing.byIndex(tag.getByte("d"));
		}
	}
}
