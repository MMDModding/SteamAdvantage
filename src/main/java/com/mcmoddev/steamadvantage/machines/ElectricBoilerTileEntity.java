package com.mcmoddev.steamadvantage.machines;

import static com.mcmoddev.steamadvantage.util.SoundHelper.playSoundAtTileEntity;

import com.mcmoddev.steamadvantage.init.Power;

import com.mcmoddev.poweradvantage.api.ConduitType;
import com.mcmoddev.poweradvantage.api.PowerRequest;
import com.mcmoddev.poweradvantage.api.fluid.FluidRequest;
import com.mcmoddev.poweradvantage.init.Fluids;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.SoundCategory;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTank;
import net.minecraftforge.fluids.FluidTankInfo;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidTankProperties;

@SuppressWarnings("deprecation")
public class ElectricBoilerTileEntity extends com.mcmoddev.poweradvantage.api.simple.TileEntitySimplePowerMachine implements IFluidHandler{

	static final ConduitType ELECTRIC_POWER = new ConduitType("electricity");

	public static final float ELECTRICITY_TO_STEAM = 0.03125f;
	
	private final FluidTank tank;
	
	private final ItemStack[] inventory;

	private static final float ELECTRICITY_PER_TICK = 16;
	private static final float MAX_ELECTRICITY = ELECTRICITY_PER_TICK * 10;
	
	private final int[] dataSyncArray = new int[3];
	
	public ElectricBoilerTileEntity() {
		super(new ConduitType[]{Power.steam_power, ELECTRIC_POWER, Fluids.fluidConduit_general},
				new float[]{100, MAX_ELECTRICITY, 1000},
				ElectricBoilerTileEntity.class.getSimpleName());
		tank = new FluidTank(Fluid.BUCKET_VOLUME * 2);
		inventory = new ItemStack[0];
	}

	private boolean redstone = true;
	
	private int timeSinceSound = 0;

    @Override
    public boolean isEmpty() {
        return false;
    }

    @Override
    public IFluidTankProperties[] getTankProperties() {
        return this.tank.getTankProperties();
    }
	
	@Override
	public void tickUpdate(boolean isServerWorld) {
		if(isServerWorld){
			// server-side logic
			if(!redstone && getEnergy(ELECTRIC_POWER) >= ELECTRICITY_PER_TICK){
				boilWater();
				// play steam sounds occasionally
				if(getWorld().rand.nextInt(100) == 0){
					playSoundAtTileEntity( SoundEvents.BLOCK_FIRE_EXTINGUISH, SoundCategory.AMBIENT, 0.5f, 1f, this);
				}
				if(timeSinceSound > 200){
					if(getTank().getFluidAmount() > 0){
						playSoundAtTileEntity( SoundEvents.BLOCK_LAVA_AMBIENT, SoundCategory.AMBIENT, 0.3f, 1f, this);
					}
					timeSinceSound = 0;
				}
				timeSinceSound++;
			}
			energyDecay();
		}
	}



	private boolean hasRedstoneSignal() {
		return getWorld().isBlockPowered(getPos());
	}


	private void energyDecay() {
		if(getEnergy(Power.steam_power) > 0){
			subtractEnergy(Power.ENERGY_LOST_PER_TICK,Power.steam_power);
		}
	}

	@Override
	public boolean isUsableByPlayer(EntityPlayer player) {
		return true;
	}
	
	private void boilWater() {
		if(getTank().getFluidAmount() >= 1 && (getEnergyCapacity(Power.steam_power) - getEnergy(Power.steam_power)) >= 1
				&& getEnergy(ELECTRIC_POWER) >= ELECTRICITY_PER_TICK){
			getTank().drain(1, true);
			subtractEnergy(ELECTRICITY_PER_TICK,ELECTRIC_POWER);
			addEnergy(ELECTRICITY_PER_TICK * ELECTRICITY_TO_STEAM,Power.steam_power);
		}
	}

	private float oldEnergy = 0;
	private float oldElec = 0;
	private int oldWater = 0;
	@Override
	public void powerUpdate(){
		super.powerUpdate();
		// powerUpdate occurs once every 8 world ticks and is scheduled such that neighboring 
		// machines don't powerUpdate in the same world tick. To reduce network congestion, 
		// I'm doing the synchonization logic here instead of in the tickUpdate method
		boolean updateFlag = false;

		if(oldEnergy != getEnergy(Power.steam_power)){
			oldEnergy = getEnergy(Power.steam_power);
			updateFlag = true;
		}
		if(oldElec != getEnergy(ELECTRIC_POWER)){
			oldElec = getEnergy(ELECTRIC_POWER);
			updateFlag = true;
		}
		if(oldWater != getTank().getFluidAmount()){
			oldWater = getTank().getFluidAmount();
			updateFlag = true;
		}
		
		redstone = hasRedstoneSignal();
		
		if(updateFlag){
			super.sync();
		}
	}
	
	public float getWaterLevel(){
		return ((float)getTank().getFluidAmount()) / ((float)getTank().getCapacity());
	}
	
	public float getSteamLevel(){
		return this.getEnergy(Power.steam_power) / this.getEnergyCapacity(Power.steam_power);
	}
	
	public float getElectricityLevel(){
		return getEnergy(ELECTRIC_POWER) / getEnergyCapacity(ELECTRIC_POWER);
	}
	
	public FluidTank getTank(){
		return tank;
	}

	@Override
	protected ItemStack[] getInventory() {
		return inventory;
	}

	@Override
	public int[] getDataFieldArray() {
		return dataSyncArray;
	}

	@Override
	public void prepareDataFieldsForSync() {
		dataSyncArray[0] = Float.floatToRawIntBits(this.getEnergy(Power.steam_power));
		dataSyncArray[1] = this.getTank().getFluidAmount();
		dataSyncArray[2] = Float.floatToIntBits(getEnergy(ELECTRIC_POWER));
	}

	@Override
	public void onDataFieldUpdate() {
		this.setEnergy(Float.intBitsToFloat(dataSyncArray[0]), Power.steam_power);
		this.getTank().setFluid(new FluidStack(FluidRegistry.WATER,dataSyncArray[1]));
		this.setEnergy(Float.intBitsToFloat(dataSyncArray[2]),ELECTRIC_POWER);
	}
	

	/**
	 * Handles data saving and loading
	 * @param tagRoot An NBT tag
	 */
	@Override
    public NBTTagCompound writeToNBT(final NBTTagCompound tagRoot) {
		super.writeToNBT(tagRoot);
        NBTTagCompound tankTag = new NBTTagCompound();
        this.getTank().writeToNBT(tankTag);
        tagRoot.setTag("Tank", tankTag);
		if(getEnergy(ELECTRIC_POWER) > 0)tagRoot.setFloat("Electricity", getEnergy(ELECTRIC_POWER));
		return tagRoot;
	}
	/**
	 * Handles data saving and loading
	 * @param tagRoot An NBT tag
	 */
	@Override
	public void readFromNBT(final NBTTagCompound tagRoot) {
		super.readFromNBT(tagRoot);
		if (tagRoot.hasKey("Tank")) {
			NBTTagCompound tankTag = tagRoot.getCompoundTag("Tank");
			getTank().readFromNBT(tankTag);
			if(tankTag.hasKey("Empty")){
				// empty the tank if NBT says its empty (not default behavior of Tank.readFromNBT(...) )
				getTank().setFluid(null);
			}
		}
		if(tagRoot.hasKey("Electricity")){
			this.setEnergy(tagRoot.getFloat("Electricity"),ELECTRIC_POWER);
		} else {
			this.setEnergy(0,ELECTRIC_POWER);
		}
	}
	
	public int getComparatorOutput() {
		return Math.min(Math.max((int)(15 * getEnergy(ELECTRIC_POWER) / getEnergyCapacity(ELECTRIC_POWER)),1),15);
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
	 * Adds "energy" as a fluid to the FluidTank returned by getTank(). This implementation ignores 
	 * all non-fluid energy types.
	 * @param amount amount of energy/fluid to add
	 * @param type the type of energy/fluid being added.
	 * @return The amount that was actually added
	 */
	@Override
	public float addEnergy(float amount, ConduitType type){
		if(Fluids.isFluidType(type)){
			if(Fluids.conduitTypeToFluid(type) == FluidRegistry.WATER){
				if(this.canFill(Fluids.conduitTypeToFluid(type))){
					return this.fill(new FluidStack(Fluids.conduitTypeToFluid(type),(int)amount), true);
				} else {
					return 0;
				}
			} else {
				return 0;
			}
		}else{
			return super.addEnergy(amount, type);
		}
	}
    /**
     * Sets the tank contents using the energy API method
	 * @param amount amount of energy/fluid to add
	 * @param type the type of energy/fluid being added.
     */
	@Override
	public void setEnergy(float amount,ConduitType type) {
		if(Fluids.isFluidType(type) && !ConduitType.areSameType(Fluids.fluidConduit_general,type)){
			getTank().setFluid(new FluidStack(Fluids.conduitTypeToFluid(type),(int)amount));
		}else{
			super.setEnergy(amount, type);
		}
	}
	/**
	 * Subtracts "energy" as a fluid to the FluidTank returned by getTank(). This implementation 
	 * ignores all non-fluid energy types.
	 * @param amount amount of energy/fluid to add
	 * @param type the type of energy/fluid being added.
	 * @return The amount that was actually added
	 */
    @Override
	public float subtractEnergy(float amount, ConduitType type){
		if(Fluids.isFluidType(type)){
			if(this.canDrain(Fluids.conduitTypeToFluid(type))){
				return this.drain(new FluidStack(Fluids.conduitTypeToFluid(type),(int)amount), true).amount;
			} else {
				return 0;
			}
		} else {
			return super.subtractEnergy(amount, type);
		}
	}
	
	@Override
	public PowerRequest getPowerRequest(ConduitType offer) {
		if(Fluids.isFluidType(offer) && Fluids.conduitTypeToFluid(offer) == FluidRegistry.WATER){
			PowerRequest request = new FluidRequest(FluidRequest.MEDIUM_PRIORITY+1,
					(getTank().getCapacity() - getTank().getFluidAmount()),
					this);
			return request;
		} else {
			return super.getPowerRequest(offer);
		}
	}

	///// end multi-type overrides /////

	
	
	///// IFluidHandler /////

	/**
	 * Implementation of IFluidHandler
	 * @param fluid The fluid being added/removed
	 * @param forReal if true, then the fluid in the tank will change
	 */
	@Override
	public int fill(FluidStack fluid, boolean forReal) {
		if(getTank().getFluidAmount() <= 0 || getTank().getFluid().getFluid().equals(fluid.getFluid())){
			return getTank().fill(fluid, forReal);
		} else {
			return 0;
		}
	}
	/**
	 * Implementation of IFluidHandler
	 * @param fluid The fluid being added/removed
	 * @param forReal if true, then the fluid in the tank will change
	 */
	@Override
	public FluidStack drain(FluidStack fluid, boolean forReal) {
		if(getTank().getFluidAmount() > 0 && getTank().getFluid().getFluid().equals(fluid.getFluid())){
			return getTank().drain(fluid.amount,forReal);
		} else {
			return new FluidStack(getTank().getFluid().getFluid(),0);
		}
	}
	/**
	 * Implementation of IFluidHandler
	 * @param amount The amount of fluid being added/removed
	 * @param forReal if true, then the fluid in the tank will change
	 */
	@Override
	public FluidStack drain(int amount, boolean forReal) {
		if(getTank().getFluidAmount() > 0 ){
			return getTank().drain(amount,forReal);
		} else {
			return null;
		}
	}
	/**
	 * Implementation of IFluidHandler
	 * @param fluid The fluid being added/removed
	 */
	public boolean canFill(Fluid fluid) {
		if(fluid != FluidRegistry.WATER) return false;
		if(getTank().getFluid() == null) return true;
		return getTank().getFluidAmount() <= getTank().getCapacity() && fluid.equals(getTank().getFluid().getFluid());
	}
	/**
	 * Implementation of IFluidHandler
	 * @param fluid The fluid being added/removed
	 */
	public boolean canDrain(Fluid fluid) {
		if(getTank().getFluid() == null) return false;
		return getTank().getFluidAmount() > 0 && fluid.equals(getTank().getFluid().getFluid());
	}


	///// end of IFluidHandler methods /////
	
	@Override
	public boolean isItemValidForSlot(final int slot, final ItemStack item) {
		return false;
	}


}

