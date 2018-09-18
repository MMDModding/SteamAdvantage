package com.mcmoddev.steamadvantage.machines;

import com.mcmoddev.poweradvantage.api.ConduitType;
import com.mcmoddev.poweradvantage.api.PowerRequest;
import com.mcmoddev.poweradvantage.api.fluid.FluidRequest;
import com.mcmoddev.poweradvantage.init.Fluids;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntityFurnace;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.SoundCategory;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTank;
import net.minecraftforge.fluids.FluidTankInfo;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidTankProperties;

import static com.mcmoddev.steamadvantage.util.SoundHelper.playSoundAtTileEntity;

import java.util.Arrays;
import java.util.Objects;

import com.mcmoddev.steamadvantage.init.Power;

@SuppressWarnings("deprecation")
public class CoalBoilerTileEntity extends com.mcmoddev.poweradvantage.api.simple.TileEntitySimplePowerMachine implements IFluidHandler{

	
	public static final int LAVA_REQUEST_SIZE = 100;
	public static final float LAVA_TO_BURN_TICKS = 16;
	
	private final FluidTank tank;
	
	private final ItemStack[] inventory;
	
	private int burnTime = 0;
	private int totalBurnTime = 0;
	
	private final int[] dataSyncArray = new int[4];
	
	public CoalBoilerTileEntity() {
		super(new ConduitType[]{Power.steam_power,Fluids.fluidConduit_general}, new float[]{1000,1000}, CoalBoilerTileEntity.class.getSimpleName());
		tank = new FluidTank(Fluid.BUCKET_VOLUME * 4);
		inventory = new ItemStack[] {ItemStack.EMPTY};
	}

	private boolean redstone = true;
	
	private int timeSinceSound = 0;
	
	@Override
	public void tickUpdate(boolean isServerWorld) {
		if(isServerWorld){
			// server-side logic
			if(burnTime > 0){
				burnTime--;
				boilWater();
				// play steam sounds occasionally
				if(getWorld().rand.nextInt(100) == 0){
					playSoundAtTileEntity(SoundEvents.BLOCK_FIRE_EXTINGUISH, SoundCategory.AMBIENT, 0.5f, 1f, this);
				}
				if(timeSinceSound > 200){
					if(getTank().getFluidAmount() > 0){
						playSoundAtTileEntity( SoundEvents.BLOCK_LAVA_AMBIENT, SoundCategory.AMBIENT, 0.3f, 1f, this);
					}
					timeSinceSound = 0;
				}
				timeSinceSound++;
			} else {
				if(!hasRedstoneSignal()){
					int fuel = getFuelBurnTime();
					if( fuel > 0 && (!redstone) && this.getTank().getFluidAmount() > 0){
						burnTime = fuel;
						totalBurnTime = fuel;
						decrementFuel();
					}
				}
				energyDecay();
			}
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


	private int getFuelBurnTime() {
		if(inventory[0] == ItemStack.EMPTY) return 0;
		return TileEntityFurnace.getItemBurnTime(inventory[0]);
	}
	

	private void decrementFuel() {
		if(inventory[0].getCount() == 1 && inventory[0].getItem().getContainerItem(inventory[0]) != ItemStack.EMPTY){
			inventory[0] = inventory[0].getItem().getContainerItem(inventory[0]);
		} else {
			this.decrStackSize(0, 1);
		}
	}


	private void boilWater() {
		if(getTank().getFluidAmount() >= 1 && (getEnergyCapacity(Power.steam_power) - getEnergy(Power.steam_power)) >= 1){
			getTank().drain(1, true);
			addEnergy(1,Power.steam_power);
		}
	}

	private float oldEnergy = 0;
	private int oldBurnTime = 0;
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
		if(oldBurnTime != burnTime){
			oldBurnTime = burnTime;
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
	
	public float getBurnLevel(){
		if(burnTime == 0){
			return 0;
		} else if (totalBurnTime == 0){
			return 1;
		}
		return Math.max(0,Math.min(1,((float)burnTime)/((float)totalBurnTime)));
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
		dataSyncArray[2] = this.burnTime;
		dataSyncArray[3] = this.totalBurnTime;
	}

	@Override
	public void onDataFieldUpdate() {
		this.setEnergy(Float.intBitsToFloat(dataSyncArray[0]), Power.steam_power);
		this.getTank().setFluid(new FluidStack(FluidRegistry.WATER,dataSyncArray[1]));
		this.burnTime = dataSyncArray[2];
		this.totalBurnTime = dataSyncArray[3];
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
		if(this.burnTime > 0)tagRoot.setInteger("BurnTime", this.burnTime);
		if(this.totalBurnTime > 0)tagRoot.setInteger("BurnTimeTotal", this.totalBurnTime);
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
		if(tagRoot.hasKey("BurnTime")){
			this.burnTime = tagRoot.getInteger("BurnTime");
		} else {
			this.burnTime = 0;
		}
		if(tagRoot.hasKey("BurnTimeTotal")){
			this.totalBurnTime = tagRoot.getInteger("BurnTimeTotal");
		} else {
			this.totalBurnTime = 0;
		}
    }
	
	public int getComparatorOutput() {
		if(inventory[0] == null) return 0;
		return Math.min(Math.max(15 * inventory[0].getCount() * inventory[0].getMaxStackSize() / inventory[0].getMaxStackSize(),1),15);
	}
	
	///// Overrides to make this a multi-type block /////
	@Override
	public boolean isPowerSink(ConduitType type) {
		return ConduitType.areSameType(Fluids.fluidConduit_general, type)
				|| ConduitType.areSameType(Fluids.fluidConduit_water, type);
	}

	@Override
	public boolean isPowerSource(ConduitType type){
		return ConduitType.areSameType(Power.steam_power,type);
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
				if(this.canFill(null, Fluids.conduitTypeToFluid(type))){
					return this.fill(new FluidStack(Fluids.conduitTypeToFluid(type),(int)amount), true);
				} else {
					return 0;
				}
			} else if(Fluids.conduitTypeToFluid(type) == FluidRegistry.LAVA){
				burnTime += (int)(amount * LAVA_TO_BURN_TICKS);
				totalBurnTime = (int)(LAVA_REQUEST_SIZE * LAVA_TO_BURN_TICKS);
				return amount;
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
			if(this.canDrain(null, Fluids.conduitTypeToFluid(type))){
				return this.drain(new FluidStack(Fluids.conduitTypeToFluid(type),(int)amount), true).amount;
			} else {
				return 0;
			}
		}else{
			return super.subtractEnergy(amount, type);
		}
	}
	
	@Override
	public PowerRequest getPowerRequest(ConduitType offer) {
		if(Fluids.conduitTypeToFluid(offer) == FluidRegistry.WATER){
			PowerRequest request = new FluidRequest(FluidRequest.MEDIUM_PRIORITY+1,
					(getTank().getCapacity() - getTank().getFluidAmount()),
					this);
			return request;
		} else if(Fluids.conduitTypeToFluid(offer) == FluidRegistry.LAVA 
				&& burnTime <= 0 && getFuelBurnTime() <= 0 
				&& getTank().getFluidAmount() > 0
				&& !redstone){
			PowerRequest request = new FluidRequest(FluidRequest.MEDIUM_PRIORITY+1,
					LAVA_REQUEST_SIZE,
					this);
			return request;
		} else {
			return PowerRequest.REQUEST_NOTHING;
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
	 * @param face Face of the block being polled
	 * @param fluid The fluid being added/removed
	 */
	public boolean canFill(EnumFacing face, Fluid fluid) {
		if(fluid != FluidRegistry.WATER) return false;
		if(getTank().getFluid() == null) return true;
		return getTank().getFluidAmount() <= getTank().getCapacity() && fluid.equals(getTank().getFluid().getFluid());
	}
	/**
	 * Implementation of IFluidHandler
	 * @param face Face of the block being polled
	 * @param fluid The fluid being added/removed
	 */
	public boolean canDrain(EnumFacing face, Fluid fluid) {
		if(getTank().getFluid() == null) return false;
		return getTank().getFluidAmount() > 0 && fluid.equals(getTank().getFluid().getFluid());
	}


	///// end of IFluidHandler methods /////
	
	@Override
	public boolean isItemValidForSlot(final int slot, final ItemStack item) {
		return super.isItemValidForSlot(slot, item) && TileEntityFurnace.getItemBurnTime(item) > 0;
	}

	@Override
	public boolean isUsableByPlayer(EntityPlayer player) {
		return true;
	}

	@Override
	public boolean isEmpty() {
		return Arrays.stream(inventory).allMatch(x -> x == ItemStack.EMPTY);
	}

	@Override
	public IFluidTankProperties[] getTankProperties() {
			return this.tank.getTankProperties();
	}
}
