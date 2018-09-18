package com.mcmoddev.steamadvantage.items;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.mcmoddev.steamadvantage.SteamAdvantage;
import com.mcmoddev.steamadvantage.init.Enchantments;
import com.mcmoddev.steamadvantage.init.Power;

import net.minecraft.block.BlockTNT;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.SoundEvents;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.EnumAction;
import net.minecraft.item.IItemPropertyGetter;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.play.server.SPacketEntityVelocity;
import net.minecraft.util.*;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.text.translation.I18n;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import net.minecraftforge.oredict.OreDictionary;

import static com.mcmoddev.steamadvantage.util.SoundHelper.playBigSoundAtPosition;
import static com.mcmoddev.steamadvantage.util.SoundHelper.playSoundAtPosition;

import java.util.List;

@SuppressWarnings("deprecation")
public class MusketItem extends net.minecraft.item.Item{
	
	
	public static final String NBT_DATA_KEY_LOADED = "loaded";
	public static final double MAX_RANGE = 64;
	private static final int maxUseTime = 7200;

	public MusketItem(){
		super();
		this.setMaxStackSize(1);
		this.setMaxDamage(250);
		this.addPropertyOverride(new ResourceLocation("loaded"), new IItemPropertyGetter()
		{
			@SideOnly(Side.CLIENT)
			public float apply(ItemStack item, World w, EntityLivingBase e)
			{
				return isLoaded(item) ? 1.0F : 0.0F;
			}
		});
		this.addPropertyOverride(new ResourceLocation("loading"), new IItemPropertyGetter()
		{
			@SideOnly(Side.CLIENT)
			public float apply(ItemStack item, World w, EntityLivingBase e)
			{
				return e != null && e.isHandActive() && e.getActiveItemStack() == item ? 1.0F : 0.0F;
			}
		});
	}
	

	public static int getReloadTime(){
		return SteamAdvantage.MUSKET_RELOAD;
	}
	public static float getShotDamage(){
		return SteamAdvantage.MUSKET_DAMAGE;
	}
	public static float getMeleeDamage(){
		return 3;
	}
	
	@Override
	public int getItemEnchantability(){
		return 1;
	}
	
	@Override
	public EnumAction getItemUseAction(ItemStack stack){
		if(isLoaded(stack)){
			return EnumAction.BOW;
		} else {
			return EnumAction.BLOCK;
		}
	}
	
	@Override
	public ActionResult<ItemStack> onItemRightClick(World worldIn, EntityPlayer playerIn, EnumHand handIn){
		ItemStack srcItemStack = playerIn.getHeldItem(handIn);
		if(handIn != EnumHand.MAIN_HAND) return ActionResult.newResult(EnumActionResult.PASS,srcItemStack);
		if(!hasAmmo(playerIn, srcItemStack)) return ActionResult.newResult(EnumActionResult.FAIL,srcItemStack);
		if(isNotLoaded(srcItemStack) && EnchantmentHelper.getEnchantmentLevel(Enchantments.rapid_reload, srcItemStack) > 0){
			// instant reload
			decrementAmmo(playerIn,srcItemStack);
			startLoad(srcItemStack);
			playSound(SoundEvents.BLOCK_LEVER_CLICK,worldIn,playerIn);
			finishLoad(srcItemStack);
			return ActionResult.newResult(EnumActionResult.SUCCESS,srcItemStack);
		}
		playerIn.setActiveHand(handIn);
		return ActionResult.newResult(EnumActionResult.SUCCESS,srcItemStack);
	}
	
	@Override
	public void onUsingTick(ItemStack stack, EntityLivingBase player, int count){
		if(player.world.isRemote && count % 7 == 3 && isNotLoaded(stack)){
			// indicator to player that the gun is loading
			player.playSound(SoundType.STONE.getStepSound(), 0.5f, 1.0f);
		}
	}
	/**
	 * This method is invoked after the item has been used for an amount of time equal to the duration 
	 * provided to the EntityPlayer.setItemInUse(stack, duration).
	 */
	@Override
	public ItemStack onItemUseFinish (ItemStack srcItemStack, World world, EntityLivingBase playerEntity)
	{ // 
		if(isNotLoaded(srcItemStack) && hasAmmo(playerEntity,srcItemStack)){
			decrementAmmo(playerEntity,srcItemStack);
			startLoad(srcItemStack);
			playSound(SoundEvents.BLOCK_LEVER_CLICK,world,playerEntity);
		}
		return srcItemStack;
	}
	
	@Override
	public void onPlayerStoppedUsing(ItemStack stack, World worldIn, EntityLivingBase playerIn, int timeLeft) {
		if(isAlmostLoaded(stack)){
			finishLoad(stack);
		} else if(isLoaded(stack)){
			fire(stack,playerIn,worldIn);
		}
	}
	
	protected void fire(ItemStack srcItemStack, EntityLivingBase playerEntity,
			World world) {
		final boolean isPlayer = playerEntity instanceof EntityPlayer;
		unload(srcItemStack);
		if(isPlayer && !((EntityPlayer)playerEntity).capabilities.isCreativeMode){
			srcItemStack.damageItem(1, playerEntity);
		}
		Vec3d lookVector = playerEntity.getLookVec();
		spawnParticle(EnumParticleTypes.EXPLOSION_LARGE, world,
				playerEntity.posX+lookVector.x, playerEntity.posY+playerEntity.getEyeHeight()+lookVector.y, playerEntity.posZ + lookVector.z);
		if(world.isRemote){
			playerEntity.rotationPitch -= 15;
			return;
		}
		playSound(SoundEvents.ITEM_FLINTANDSTEEL_USE,world,playerEntity);
		playBigSoundAtPosition(playerEntity.posX,playerEntity.posY,playerEntity.posZ, SoundEvents.ENTITY_FIREWORK_BLAST,SoundCategory.PLAYERS,2F,0.5F,world);
		
		Vec3d start = new Vec3d(playerEntity.posX, playerEntity.posY+playerEntity.getEyeHeight(),playerEntity.posZ);
		start.add(MAX_RANGE * lookVector.x, MAX_RANGE * lookVector.y, MAX_RANGE * lookVector.z);
		RayTraceResult rayTrace = rayTraceBlocksAndEntities(world,MAX_RANGE,playerEntity);
		if(rayTrace == null){
			// no collisions
			return;
		}
		final float explodeFactor = 0.75f;
		if(rayTrace.typeOfHit == RayTraceResult.Type.ENTITY && rayTrace.entityHit != null){
			Entity e = rayTrace.entityHit;
			e.attackEntityFrom(Power.musket_damage, getShotDamage());
			if(EnchantmentHelper.getEnchantmentLevel(Enchantments.high_explosive, srcItemStack) > 0){
				int lvl = EnchantmentHelper.getEnchantmentLevel(Enchantments.high_explosive, srcItemStack);
				world.createExplosion(playerEntity, e.posX, e.posY+0.5, e.posZ, 
						explodeFactor * lvl, 
						true);
				AxisAlignedBB fireArea = new AxisAlignedBB(e.posX-lvl, e.posY-lvl, e.posZ-lvl,
						e.posX+lvl, e.posY+lvl, e.posZ+lvl);
				List<EntityLivingBase> collateralDamage = world.getEntitiesWithinAABB(EntityLivingBase.class, fireArea);
				for(EntityLivingBase victim : collateralDamage){
					victim.setFire(lvl);
				}
			}
		} else if(rayTrace.typeOfHit == RayTraceResult.Type.BLOCK){
			playSoundAtPosition(rayTrace.hitVec.x, rayTrace.hitVec.y, rayTrace.hitVec.z,
					SoundEvents.BLOCK_GRAVEL_HIT, SoundCategory.BLOCKS, 1f, 1f, world);
			BlockPos coord = rayTrace.getBlockPos();
			if(coord.getY()>= 0 && coord.getY() <= 255 && !world.isAirBlock(coord)){
				IBlockState bs = world.getBlockState(coord);
				Material blockMat = bs.getBlock().getMaterial(bs);
				if(blockMat == net.minecraft.block.material.Material.GLASS
						|| blockMat == net.minecraft.block.material.Material.LEAVES
						|| blockMat == net.minecraft.block.material.Material.CIRCUITS
						|| blockMat == net.minecraft.block.material.Material.PLANTS
						|| blockMat == net.minecraft.block.material.Material.VINE
						|| blockMat == net.minecraft.block.material.Material.WEB){
					world.destroyBlock(coord, true);
				} else if (blockMat == net.minecraft.block.material.Material.TNT){
					if(world.getBlockState(coord).getBlock() instanceof BlockTNT){
						((BlockTNT)world.getBlockState(coord).getBlock()).explode(world, coord, world.getBlockState(coord).withProperty(BlockTNT.EXPLODE, (Boolean)true), playerEntity);
						world.setBlockToAir(coord);
					}
				}
			}
			if(EnchantmentHelper.getEnchantmentLevel(Enchantments.high_explosive, srcItemStack) > 0){
				int lvl = EnchantmentHelper.getEnchantmentLevel(Enchantments.high_explosive, srcItemStack);
				world.createExplosion(playerEntity, rayTrace.hitVec.x, rayTrace.hitVec.y, rayTrace.hitVec.z,
						explodeFactor * lvl, 
						true);
				AxisAlignedBB fireArea = new AxisAlignedBB(rayTrace.hitVec.x-lvl, rayTrace.hitVec.y-lvl, rayTrace.hitVec.z-lvl,
						rayTrace.hitVec.x+lvl, rayTrace.hitVec.y+lvl, rayTrace.hitVec.z+lvl);
				List<EntityLivingBase> collateralDamage = world.getEntitiesWithinAABB(EntityLivingBase.class, fireArea);
				for(EntityLivingBase victim : collateralDamage){
					victim.setFire(lvl);
				}
			}
		}
		if(rayTrace.typeOfHit != RayTraceResult.Type.MISS && rayTrace.hitVec != null){
			spawnParticle(EnumParticleTypes.SMOKE_LARGE, world,
					rayTrace.hitVec.x, rayTrace.hitVec.y, rayTrace.hitVec.z);
			spawnParticle(EnumParticleTypes.EXPLOSION_NORMAL, world,
					rayTrace.hitVec.x, rayTrace.hitVec.y, rayTrace.hitVec.z);
		}
		
		// recoil enchantment kicks you back
		int recoil = EnchantmentHelper.getEnchantmentLevel(Enchantments.recoil, srcItemStack);
		if(recoil > 0){
			double c = -0.3;
			double lateral = 2.0;
			playerEntity.addVelocity(c * recoil * lookVector.x * lateral,
					c * recoil * lookVector.y,
					c * recoil * lookVector.z * lateral);
			if(!world.isRemote){
				// send update packet from server
				((EntityPlayerMP) playerEntity).connection.sendPacket(new SPacketEntityVelocity(playerEntity));
			}
		}
	}


	private void spawnParticle(EnumParticleTypes p, World w, double x, double y, double z) {
		w.spawnParticle(p,
				x, y, z,
				0f, 0f, 0f,
				null);
	}
	
	@Override
	public boolean hitEntity(ItemStack stack, EntityLivingBase target, EntityLivingBase attacker)
	{
		stack.damageItem(1, attacker);
		return true;
	}
	
	/** plays a sound at the player location */
	protected void playSound(SoundEvent sound, World world, Entity e){
		if (!world.isRemote)
		{
			playSoundAtPosition(e.posX, e.posY, e.posZ, sound,SoundCategory.PLAYERS,1F,1F,world);
		}
	}
	
	public static void startLoad(ItemStack musket){
		NBTTagCompound data;
		if(musket.hasTagCompound()){
			data = musket.getTagCompound();
		} else {
			data = new NBTTagCompound();
		}
		data.setByte(NBT_DATA_KEY_LOADED, (byte)2);
		musket.setTagCompound(data);
	}
	
	public static void finishLoad(ItemStack musket){
		NBTTagCompound data;
		if(musket.hasTagCompound()){
			data = musket.getTagCompound();
		} else {
			data = new NBTTagCompound();
		}
		data.setByte(NBT_DATA_KEY_LOADED, (byte)1);
		musket.setTagCompound(data);
	}
	
	public static void unload(ItemStack musket){
		NBTTagCompound data;
		if(musket.hasTagCompound()){
			data = musket.getTagCompound();
		} else {
			data = new NBTTagCompound();
		}
		data.setByte(NBT_DATA_KEY_LOADED, (byte)0);
		musket.setTagCompound(data);
	}
	
	public static boolean isLoaded(ItemStack musket){
		NBTTagCompound data = musket.getTagCompound();
		if(data == null) return false;
		return data.hasKey(NBT_DATA_KEY_LOADED) && data.getByte(NBT_DATA_KEY_LOADED) == 1;
	}
	
	public static boolean isNotLoaded(ItemStack musket){
		NBTTagCompound data = musket.getTagCompound();
		if(data == null || !data.hasKey(NBT_DATA_KEY_LOADED)) return true;
		return data.hasKey(NBT_DATA_KEY_LOADED) && data.getByte(NBT_DATA_KEY_LOADED) == 0;
	}
	
	public static boolean isAlmostLoaded(ItemStack musket){
		NBTTagCompound data = musket.getTagCompound();
		if(data == null) return false;
		return data.hasKey(NBT_DATA_KEY_LOADED) && data.getByte(NBT_DATA_KEY_LOADED) == 2;
	}


	
	public static boolean hasAmmo(EntityLivingBase e, ItemStack musket) {
		if(e instanceof EntityPlayer) {
			EntityPlayer playerEntity = (EntityPlayer) e;
			if (playerEntity.capabilities.isCreativeMode
					|| EnchantmentHelper.getEnchantmentLevel(net.minecraft.init.Enchantments.INFINITY, musket) > 0
					|| EnchantmentHelper.getEnchantmentLevel(Enchantments.powderless, musket) > 0) return true;
			List<ItemStack> ammoItems = OreDictionary.getOres("ammoBlackpowder");
			for (int i = 0; i < playerEntity.inventory.mainInventory.size(); i++) {
				if (playerEntity.inventory.mainInventory.get(i) == null) continue;
				for (int n = 0; n < ammoItems.size(); n++) {
					if (OreDictionary.itemMatches(ammoItems.get(n), playerEntity.inventory.mainInventory.get(i), false)) {
						return true;
					}
				}
			}
		}
		return false;
	}
	

	public static void decrementAmmo(EntityLivingBase e, ItemStack musket) {
		if(e instanceof EntityPlayer) {
			EntityPlayer playerEntity = (EntityPlayer) e;
			if (playerEntity.capabilities.isCreativeMode
					|| EnchantmentHelper.getEnchantmentLevel(net.minecraft.init.Enchantments.INFINITY, musket) > 0
					|| EnchantmentHelper.getEnchantmentLevel(Enchantments.powderless, musket) > 0) return;
			List<ItemStack> ammoItems = OreDictionary.getOres("ammoBlackpowder");
			for (int i = 0; i < playerEntity.inventory.mainInventory.size(); i++) {
				if (playerEntity.inventory.mainInventory.get(i) == null) continue;
				for (int n = 0; n < ammoItems.size(); n++) {
					if (OreDictionary.itemMatches(ammoItems.get(n), playerEntity.inventory.mainInventory.get(i), false)) {
						playerEntity.inventory.mainInventory.get(i).shrink(1);
						if (playerEntity.inventory.mainInventory.get(i).getCount() <= 0) {
							playerEntity.inventory.mainInventory.set(i, null);
						}
						return;
					}
				}
			}
		}
	}
	
	
	public static RayTraceResult rayTraceBlocksAndEntities(World w, double maxRange, EntityLivingBase source){
		double rangeSqr = maxRange * maxRange;
		Vec3d rayOrigin = new Vec3d(source.posX, source.posY + source.getEyeHeight(), source.posZ);
		Vec3d rayDirection = source.getLookVec();
		BlockPos srcPos = source.getPosition();
		AxisAlignedBB aoi = new AxisAlignedBB(srcPos.getX() - maxRange, srcPos.getY() - maxRange, srcPos.getZ() - maxRange,
				srcPos.getX() + maxRange, srcPos.getY() + maxRange, srcPos.getZ() + maxRange);
		List<Entity> entities = w.getEntitiesWithinAABBExcludingEntity(source, aoi);
		double closestDistSqr = Double.MAX_VALUE;
		Entity closestEntity = null;
		for(Entity e : entities){
			double distSqr = e.getDistanceSq(source.posX, source.posY, source.posZ);
			if(distSqr < rangeSqr){
				// e is within range
				AxisAlignedBB box = e.getEntityBoundingBox();
				if(rayIntersectsBoundingBox(rayOrigin,rayDirection, box)){
					// e is in cross-hairs
					if(distSqr < closestDistSqr){
						// e is closest entity in line of fire
						closestDistSqr = distSqr;
						closestEntity = e;
					}
				}
			}
		}
		if(closestEntity == null) {
			return w.rayTraceBlocks(rayOrigin, rayOrigin.add(mul(rayDirection, maxRange)), true, false, false);
		} else {
			Vec3d pos = new Vec3d(closestEntity.posX, closestEntity.posY+closestEntity.getEyeHeight(), closestEntity.posZ);
			RayTraceResult entityCollision = new RayTraceResult(closestEntity, pos);
			return entityCollision;
		}
	}

	public static boolean rayIntersectsBoundingBox(Vec3d rayOrigin, Vec3d rayDirection, AxisAlignedBB box){
		if(box == null) return false;
		// algorithm from http://gamedev.stackexchange.com/questions/18436/most-efficient-aabb-vs-ray-collision-algorithms
		Vec3d inverse = new Vec3d(1.0 / rayDirection.x, 1.0 / rayDirection.y, 1.0 / rayDirection.z);
		double t1 = (box.minX - rayOrigin.x)*inverse.x;
		double t2 = (box.maxX- rayOrigin.x)*inverse.x;
		double t3 = (box.minY - rayOrigin.y)*inverse.y;
		double t4 = (box.maxY - rayOrigin.y)*inverse.y;
		double t5 = (box.minZ - rayOrigin.z)*inverse.z;
		double t6 = (box.maxZ - rayOrigin.z)*inverse.z;

		double tmin = max(max(min(t1, t2), min(t3, t4)), min(t5, t6));
		double tmax = min(min(max(t1, t2), max(t3, t4)), max(t5, t6));

		// if tmax < 0, ray (line) is intersecting AABB, but whole AABB is behing us
		if (tmax < 0)
		{
			return false;
		}

		// if tmin > tmax, ray doesn't intersect AABB
		if (tmin > tmax)
		{
			return false;
		}

		return true;
	}
	
	
	private static Vec3d mul(Vec3d a, double b){
		return new Vec3d(a.x * b, a.y * b, a.z * b);
	}
	private static double max(double a, double b){
		return Math.max(a, b);
	}
	private static double min(double a, double b){
		return Math.min(a, b);
	}
	
	/**
	 * Return whether this item is repairable in an anvil.
	 */
	@Override public boolean getIsRepairable(ItemStack srcItemStack, ItemStack rawMaterial)
	{
		// repair with steel ingots
		List<ItemStack> repairItems = OreDictionary.getOres("ingotSteel"); 
		for(int i = 0; i < repairItems.size(); i++){
			if(OreDictionary.itemMatches(repairItems.get(i),rawMaterial,false)) return true;
		}
		return false;
	}
//	@SuppressWarnings("deprecation")
//	@Override
//	public void addInformation(ItemStack stack, EntityPlayer player, List<String> list, boolean b){
//		super.addInformation(stack,player,list,b);
//		list.add(I18n.translateToLocal("tooltip.musket.damage").replace("%x", String.valueOf((int)this.getShotDamage())));
//		if(isLoaded(stack)){
//			list.add(I18n.translateToLocal("tooltip.musket.loaded"));
//		} else {
//			list.add(I18n.translateToLocal("tooltip.musket.unloaded"));
//			list.add(I18n.translateToLocal("tooltip.musket.ammo").replace("%x",I18n.translateToLocal("item.steamadvantage.blackpowder_cartridge.name")));
//		}
//	}

	/** Sets melee attack damage */
	@Override
	public Multimap<String, AttributeModifier> getAttributeModifiers(EntityEquipmentSlot slot, ItemStack stack)
	{
		Multimap<String, AttributeModifier> multimap = HashMultimap.create();
		if(slot != EntityEquipmentSlot.MAINHAND) return multimap;
		multimap.put(SharedMonsterAttributes.ATTACK_DAMAGE.getName(), new AttributeModifier(ATTACK_DAMAGE_MODIFIER, "Weapon modifier", (double)getMeleeDamage(), 0));
		return multimap;
	}
	
	@Override
	public int getMaxItemUseDuration(ItemStack stack)
	{
		if(isLoaded(stack)){
			return maxUseTime;
		} else {
			return getReloadTime();
		}
	}
}
