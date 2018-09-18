package com.mcmoddev.steamadvantage.gui;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

import com.mcmoddev.steamadvantage.SteamAdvantage;
import com.mcmoddev.steamadvantage.machines.BlastFurnaceTileEntity;

import com.mcmoddev.poweradvantage.api.simple.SimpleMachineGUI;
import com.mcmoddev.poweradvantage.math.Integer2D;

public class BlastFurnaceGUI extends SimpleMachineGUI{

	public BlastFurnaceGUI() {
		super(
				new ResourceLocation(SteamAdvantage.MODID+":textures/gui/container/steam_furnace.png"), 
				Integer2D.fromCoordinates(29,80, 93,19, 111,19, 129,19, 93,80, 111,80, 129,80)
		);
		for(int i = 4; i < 7; i++)super.specialSlots.put(i, (SlotContext c)->new Slot(c.machineInventory,c.slotIndex,c.screenPositionX,c.screenPositionY){
			@Override
			public ItemStack onTake(EntityPlayer player, ItemStack stack)
			{
				stack.onCrafting(player.getEntityWorld(), player, stack.getCount());
				return super.onTake(player, stack);
			}
			@Override
			public boolean isItemValid(ItemStack stack){ return false; }

		});
	}
	
	
	private static final float maxNeedleSpeed = 0.015625f;
	
	private float oldTemp = 0;
	

	private long lastUpdate = 0;
	/**
	 * Override this method to draw on the GUI window.
	 * <br><br>
	 * This method is invoked when drawing the GUI so that you can draw 
	 * animations and other foreground decorations to the GUI.
	 * @param srcEntity This is the TileEntity (or potentially a LivingEntity) 
	 * for whom we are drawing this interface
	 * @param guiContainer This is the instance of GUIContainer that is drawing 
	 * the GUI. You need to use it to draw on the screen. For example:<br>
	   <pre>
guiContainer.mc.renderEngine.bindTexture(arrowTexture);
guiContainer.drawTexturedModalRect(x+79, y+35, 0, 0, arrowLength, 17); // x, y, textureOffsetX, textureOffsetY, width, height)
	   </pre>
	 * @param x This is the x coordinate (in pixels) from the top-left corner of 
	 * the GUI
	 * @param y This is the y coordinate (in pixels) from the top-left corner of 
	 * the GUI
	 * @param z This is the z coordinate (no units) into the depth of the screen
	 */
	@Override
	public void drawGUIDecorations(Object srcEntity, GUIContainer guiContainer, int x, int y, float  z){
		
		if(srcEntity.getClass() == BlastFurnaceTileEntity.class){
			BlastFurnaceTileEntity target = (BlastFurnaceTileEntity)srcEntity;

			
			float tempPivotX = 37f;
			float tempPivotY = 43f;
			
			int arrowX = x+93;
			int arrowY = y+42;
			
			
			float temp = target.getTemperatureLevel(); 
			float[] progress = target.getProgress(); 

			long t = System.currentTimeMillis();
			if((t - lastUpdate) < 1000L){
				// slow the needles if it has been less than 1 second since last invocation
				temp = GUIHelper.maxDelta(temp, oldTemp, maxNeedleSpeed);
			}
			oldTemp = temp;
			
			lastUpdate = t;

			GUIHelper.drawFlameProgress(x+30, y+46, 1f - target.getBurnLevel(), guiContainer);
			
			GUIHelper.drawNeedle(x+tempPivotX, y+tempPivotY, z, temp);
			for(int i = 0; i < 3; i++){
				GUIHelper.drawDownArrowProgress(arrowX+18*i,arrowY,progress[i],guiContainer);
			}
		}
		
	}


	

}
