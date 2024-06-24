package mcinterface1201;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableMultimap.Builder;
import com.google.common.collect.Multimap;

import minecrafttransportsimulator.baseclasses.Point3D;
import minecrafttransportsimulator.blocks.components.ABlockBase.Axis;
import minecrafttransportsimulator.items.components.AItemBase;
import minecrafttransportsimulator.items.components.IItemFood;
import minecrafttransportsimulator.items.instances.ItemItem;
import minecrafttransportsimulator.items.instances.ItemPartGun;
import minecrafttransportsimulator.jsondefs.JSONPotionEffect;
import minecrafttransportsimulator.mcinterface.InterfaceManager;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.block.BlockState;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.core.BlockPos;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.attributes.Attribute;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.ai.attributes.Attributes;
import net.minecraft.entity.player.Player;
import net.minecraft.entity.player.ServerPlayer;
import net.minecraft.inventory.EquipmentSlotType;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUseContext;
import net.minecraft.item.UseAction;
import net.minecraft.network.chat.Component;
import net.minecraft.potion.EffectInstance;
import net.minecraft.potion.Potion;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.ActionResultType;
import net.minecraft.util.Hand;
import net.minecraft.util.SoundSource;
import net.minecraft.util.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.Component;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.World;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Builder for MC items.  Constructing a new item with this builder This will automatically
 * construct the item and will add it to the appropriate maps for automatic registration.
 * When interfacing with MC systems use this class, but when doing code in MTS use the item,
 * NOT the builder!
 *
 * @author don_bruce
 */
public class BuilderItem extends Item implements IBuilderItemInterface {
    protected static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, InterfaceLoader.MODID);

    /**
     * Map of created items linked to their builder instances.  Used for interface operations.
     **/
    protected static final Map<AItemBase, BuilderItem> itemMap = new LinkedHashMap<>();

    /**
     * Current item we are built around.
     **/
    private final AItemBase item;

    /** Modifiers applied when the item is in the mainhand of a user. */
    private final Multimap<Attribute, AttributeModifier> defaultModifiers;

    public BuilderItem(Item.Properties properties, AItemBase item) {
        super(properties);
        if (category != null) {
            ((BuilderCreativeTab) category).addItem(item, this);
        }
        this.item = item;
        itemMap.put(item, this);

        Builder<Attribute, AttributeModifier> builder = ImmutableMultimap.builder();
        if (item instanceof ItemItem && ((ItemItem) item).definition.weapon != null) {
            ItemItem weapon = (ItemItem) item;
            if (weapon.definition.weapon.attackDamage != 0) {
                builder.put(Attributes.ATTACK_DAMAGE, new AttributeModifier(BASE_ATTACK_DAMAGE_UUID, "Weapon modifier", weapon.definition.weapon.attackDamage - 1, AttributeModifier.Operation.ADDITION));
            }
            if (weapon.definition.weapon.attackCooldown != 0) {
                builder.put(Attributes.ATTACK_SPEED, new AttributeModifier(BASE_ATTACK_SPEED_UUID, "Weapon modifier", 20D / weapon.definition.weapon.attackCooldown - 4.0, AttributeModifier.Operation.ADDITION));
            }
        }
        this.defaultModifiers = builder.build();
    }

    @Override
    public AItemBase getWrappedItem() {
        return item;
    }

    /**
     * This is called by the main MC system to get the displayName for the item.
     * Normally this is a translated version of the unlocalized name, but we
     * allow for use of the wrapper to decide what name we translate.
     */
    @Override
    protected String getOrCreateDescriptionId() {
        return item.getItemName();
    }

    /**
     * This is called by the main MC system to add tooltip lines to the item.
     * The ItemStack is passed-in here as it contains NBT data that may be used
     * to change the display of the tooltip.  We convert the NBT into wrapper form
     * to prevent excess odd calls and allow for a more raw serialization system.
     * Also prevents us from using a MC class with a changing name.
     */
    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltipComponents, TooltipFlag isAdvanced) {
        List<String> textLines = new ArrayList<>();
        //tooltipLines.forEach(line -> textLines.add(line.getString()));
        if (stack.hasTag()) {
            item.addTooltipLines(textLines, new WrapperNBT(stack.getTag()));
        } else {
            item.addTooltipLines(textLines, InterfaceManager.coreInterface.getNewNBTWrapper());
        }
        textLines.forEach(line -> tooltipComponents.add(Component.literal(line)));
    }

    /**
     * This is called by the main MC system to determine how long it takes to eat it.
     * If we are a food item, this should match our eating time.
     */
    @Override
    public int getUseDuration(ItemStack stack) {
        return item instanceof IItemFood ? ((IItemFood) item).getTimeToEat() : 0;
    }

    /**
     * This is called by the main MC system do do item use actions.
     * If we are a food item, and can be eaten, return eating here.
     */
    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        if (item instanceof IItemFood) {
            IItemFood food = (IItemFood) item;
            if (food.getTimeToEat() > 0) {
                return food.isDrink() ? UseAnim.DRINK : UseAnim.EAT;
            }
        }
        return UseAnim.NONE;
    }

    @Override
    public Multimap<Attribute, AttributeModifier> getAttributeModifiers(EquipmentSlot slot, ItemStack stack) {
        return slot == EquipmentSlot.MAINHAND ? this.defaultModifiers : super.getAttributeModifiers(slot, stack);
    }

    /**
     * This is called by the main MC system to "use" this item on a block.
     * Forwards this to the main item for processing.
     */
    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (context.getHand() == InteractionHand.MAIN_HAND) {
            InteractionResult result = item.onBlockClicked(WrapperWorld.getWrapperFor(context.getLevel()), WrapperPlayer.getWrapperFor(context.getPlayer()), new Point3D(context.getClickedPos().getX(), context.getClickedPos().getY(), context.getClickedPos().getZ()), Axis.valueOf(context.getClickedFace().name())) ? InteractionResult.SUCCESS : InteractionResult.FAIL;
            if (result == InteractionResult.FAIL && context.getPlayer().isCrouching()) {
                //Forward sneak click too, since blocks don't get these.
                if (!context.getLevel().isClientSide) {
                    BlockEntity be = context.getLevel().getBlockEntity(context.getClickedPos());
                    if (be instanceof BuilderTileEntity) {
                        if (((BuilderTileEntity) be).tileEntity != null) {
                            return ((BuilderTileEntity) be).tileEntity.interact(WrapperPlayer.getWrapperFor(context.getPlayer())) ? InteractionResult.SUCCESS : InteractionResult.FAIL;
                        }
                    }
                }
                return InteractionResult.FAIL;
            }
            return result;
        } else {
            return super.useOn(context);
        }
    }

    /**
     * This is called by the main MC system to "use" this item.
     * Forwards this to the main item for processing.
     */
    @Override
    public InteractionResultHolder<ItemStack> use(Level world, Player player, InteractionHand hand) {
        if (hand == InteractionHand.MAIN_HAND) {
            //If we are a food item, set our hand to start eating.
            //If we are a gun item, set our hand to prevent attacking.
            if ((item instanceof IItemFood && ((IItemFood) item).getTimeToEat() > 0 && player.canEat(true)) || (item instanceof ItemPartGun && ((ItemPartGun) item).definition.gun.handHeld)) {
                player.startUsingItem(hand);
            }
            return item.onUsed(WrapperWorld.getWrapperFor(world), WrapperPlayer.getWrapperFor(player)) ? InteractionResultHolder.success(player.getItemInHand(hand)) : InteractionResultHolder.fail(player.getItemInHand(hand));
        } else {
            return super.use(world, player, hand);
        }
    }

    /**
     * This is called by the main MC system after the item's use timer has expired.
     * This is normally instant, as {@link #getUseDuration(ItemStack)} is 0.
     * If this item is food, and a player is holding the item, have it apply to them.
     */
    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level world, LivingEntity entityLiving) {
        if (item instanceof IItemFood) {
            if (entityLiving instanceof Player) {
                IItemFood food = ((IItemFood) item);
                Player player = (Player) entityLiving;

                //Add hunger and saturation.
                player.getFoodData().eat(food.getHungerAmount(), food.getSaturationAmount());

                //Add effects.
                List<JSONPotionEffect> effects = food.getEffects();
                if (!world.isClientSide && effects != null) {
                    for (JSONPotionEffect effect : effects) {
                        Potion potion = Potion.byName(effect.name);
                        if (potion != null) {
                            potion.getEffects().forEach(mcEffect -> {
                                entityLiving.addEffect(new MobEffectInstance(mcEffect.getEffect(), effect.duration, effect.amplifier, false, true));
                            });
                        } else {
                            throw new NullPointerException("Potion " + effect.name + " does not exist.");
                        }
                    }
                }

                //Play sound of food being eaten and add stats.
                world.playSound(player, player.position().x, player.position().y, player.position().z, SoundEvents.PLAYER_BURP, SoundSource.PLAYERS, 0.5F, world.random.nextFloat() * 0.1F + 0.9F);
                if (player instanceof ServerPlayer) {
                    CriteriaTriggers.CONSUME_ITEM.trigger((ServerPlayer) player, stack);
                }
            }
            //Remove 1 item due to it being eaten.
            stack.shrink(1);
        }
        return stack;
    }

    @Override
    public boolean canAttackBlock(BlockState state, Level world, BlockPos pos, Player player) {
        return item.canBreakBlocks();
    }
}
