package com.astrazoey.indexed.mixins;

import com.astrazoey.indexed.EnchantingAcceptability;
import com.astrazoey.indexed.Indexed;
import com.astrazoey.indexed.MaxEnchantingSlots;
import net.minecraft.client.gui.screen.ingame.AnvilScreen;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.inventory.CraftingResultInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.AnvilScreenHandler;
import net.minecraft.screen.Property;
import net.minecraft.screen.slot.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableText;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(AnvilScreenHandler.class)
public class AnvilScreenHandlerMixin {

    ItemStack itemStack1;
    ItemStack itemStack3;

    boolean overcharged = false;

    //minimum amount of materials to repair an unenchanted tool
    int repairCost = 1;
    //how much repairing scales with number of enchantments. higher values = higher costs for more enchanted items
    int repairScaling = 6;


    //Get the items inside the anvil
    @Redirect(method="updateResult", at = @At(value="INVOKE", target = "Lnet/minecraft/inventory/Inventory;getStack(I)Lnet/minecraft/item/ItemStack;", ordinal = 0))
    public ItemStack getItemStack1(Inventory inventory, int slot) {
        itemStack1 = inventory.getStack(0);
        return itemStack1;
    }

    @Redirect(method="updateResult", at = @At(value="INVOKE", target = "Lnet/minecraft/inventory/Inventory;getStack(I)Lnet/minecraft/item/ItemStack;", ordinal = 1))
    public ItemStack getItemStack3(Inventory inventory, int slot) {
        itemStack3 = inventory.getStack(1);
        return itemStack3;
    }

    @Redirect(method="updateResult", at = @At(value="INVOKE", target = "Lnet/minecraft/enchantment/Enchantment;isAcceptableItem(Lnet/minecraft/item/ItemStack;)Z"))
    public boolean checkAcceptability(Enchantment enchantment, ItemStack stack) {
        EnchantingAcceptability acceptabilityTest = new EnchantingAcceptability();
        return acceptabilityTest.checkAcceptability(enchantment, stack);

        /*
        if(enchantment == Indexed.SLOW_BURN && !stack.isOf(Items.ELYTRA)) {
            return false;
        } else {
            return enchantment.isAcceptableItem(stack);
        }*/
    }

    //Change the amount of materials required for repair
    public int calculateRepairCost() {
        if(MaxEnchantingSlots.getEnchantType(itemStack1) != null) {
            float enchantingRatio = (float) MaxEnchantingSlots.getCurrent(itemStack1) / (float) MaxEnchantingSlots.getEnchantType(itemStack1).getMaxEnchantingSlots();
            float enchantingFactor = enchantingRatio * repairScaling;
            enchantingFactor = Math.round(enchantingFactor);
            enchantingFactor = enchantingFactor * MaxEnchantingSlots.getEnchantType(itemStack1).getRepairScaling();

            //Removes repair cost if forgery is enabled
            enchantingFactor = enchantingFactor - (EnchantmentHelper.getLevel(Indexed.FORGERY, itemStack1) * MaxEnchantingSlots.getEnchantType(itemStack1).getRepairScaling());
            if(enchantingFactor < 0) {
                enchantingFactor = 0;
            }

            return (repairCost + (int) enchantingFactor);
        } else {
            return 4; //the default value
        }
    }

    @ModifyConstant(method= "updateResult", constant = @Constant(intValue = 4, ordinal = 0))
    public int increaseRepairCost(int cost) {
        return calculateRepairCost();
    }

    @ModifyConstant(method= "updateResult", constant = @Constant(intValue = 4, ordinal = 1))
    public int increaseRepairCost2(int cost) {
        return calculateRepairCost();
    }


    //Allow items to be used in the anvil for free
    @ModifyConstant(method= "updateResult", constant = @Constant(expandZeroConditions = {Constant.Condition.LESS_THAN_OR_EQUAL_TO_ZERO}, ordinal = 2))
    public int allowAnyCost(int i) {
        if(itemStack3.isEmpty()) {
            return 0;
        } else {
            return -1;
        }
    }

    @ModifyConstant(method="canTakeOutput", constant = @Constant(expandZeroConditions = {Constant.Condition.GREATER_THAN_ZERO}))
    public int allowAnyCostForOutput(int cost) {
        if(overcharged) {
            return 50000; //prevent taking out overcharged items
        }
        if(itemStack3.isEmpty()) {
            return 0;
        } else {
            return -1;
        }
    }

    @Redirect(method="updateResult", at = @At(value="INVOKE", target="Lnet/minecraft/inventory/CraftingResultInventory;setStack(ILnet/minecraft/item/ItemStack;)V", ordinal=4))
    public void denyExpensiveTransactions(CraftingResultInventory craftingResultInventory, int slot, ItemStack stack) {
        //checks if the enchanting hasn't exceeded itself
        if(MaxEnchantingSlots.getEnchantType(stack) != null) {
            if (MaxEnchantingSlots.getCurrent(stack) > MaxEnchantingSlots.getEnchantType(stack).getMaxEnchantingSlots()) {
                overcharged = true;
                craftingResultInventory.setStack(slot, stack);
                //craftingResultInventory.setStack(slot, ItemStack.EMPTY);
            } else {
                overcharged = false;
                craftingResultInventory.setStack(slot, stack);
            }
        } else {
            craftingResultInventory.setStack(slot, stack);
        }
    }


    //Remove "Too Expensive!" stuff by keeping the repair cost of the item under 31.
    @Redirect(method = "updateResult", at = @At(value = "INVOKE", target = "Lnet/minecraft/item/ItemStack;setRepairCost(I)V"))
    public void removeTooExpensiveLimit(ItemStack itemStack, int repairCost) {

        //Reduce repair cost for Forgery enchantments
        if (EnchantmentHelper.getLevel(Indexed.FORGERY, itemStack) > 0) {
            if ((EnchantmentHelper.getLevel(Indexed.FORGERY, itemStack) >= 5) && (itemStack.getRepairCost() > 10)) {
                itemStack.setRepairCost(10);
            } else if ((EnchantmentHelper.getLevel(Indexed.FORGERY, itemStack) >= 3) && (itemStack.getRepairCost() > 20)) {
                itemStack.setRepairCost(20);
            } else if ((EnchantmentHelper.getLevel(Indexed.FORGERY, itemStack) >= 1) && (itemStack.getRepairCost() > 25)) {
                itemStack.setRepairCost(25);
            }
        } else { //remove "too expensive" problem
            if(repairCost > 30) {
                itemStack.setRepairCost(30);
            } else {
                itemStack.setRepairCost(repairCost);
            }
        }
    }

    @Redirect(method = "updateResult", at = @At(value = "INVOKE", target = "Lnet/minecraft/screen/Property;set(I)V", ordinal = 5))
    public void freeRepairCost(Property property, int value) {
        if(itemStack3.getItem() != Items.ENCHANTED_BOOK && itemStack3.getItem() != itemStack1.getItem()) {
            property.set(0);
        } else {
            property.set(value);
        }
    }


}

@Mixin(AnvilScreen.class)
class AnvilScreenMixin {

    ItemStack itemStack;

    //get item stack
    @Redirect(method = "drawForeground", at = @At(value="INVOKE", target = "Lnet/minecraft/screen/AnvilScreenHandler;getSlot(I)Lnet/minecraft/screen/slot/Slot;"))
    public Slot getItemStack(AnvilScreenHandler anvilScreenHandler, int index) {
        Slot slot = anvilScreenHandler.getSlot(index);
        itemStack = slot.getStack();
        return slot;
    }

    //update text
    @Redirect(method = "drawForeground", at = @At(value="INVOKE", target = "Lnet/minecraft/client/font/TextRenderer;drawWithShadow(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/text/Text;FFI)I"))
    public int setText(TextRenderer textRenderer, MatrixStack matrices, Text text, float x, float y, int color) {

        if(MaxEnchantingSlots.getEnchantType(itemStack) != null) {
            if(MaxEnchantingSlots.getEnchantType(itemStack).getMaxEnchantingSlots() < MaxEnchantingSlots.getCurrent(itemStack)) {
                text = new TranslatableText("container.indexed.overcharged");
                String textString = text.getString();
                x = x + 100 - textRenderer.getWidth(textString);
            }
        }

        return textRenderer.drawWithShadow(matrices, (Text)text, (float)x, 69.0F, color);
    }

}