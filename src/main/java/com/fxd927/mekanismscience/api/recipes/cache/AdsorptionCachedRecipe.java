package com.fxd927.mekanismscience.api.recipes.cache;

import com.fxd927.mekanismscience.api.recipes.AdsorptionRecipe;
import mekanism.api.chemical.gas.GasStack;
import mekanism.api.chemical.merged.BoxedChemicalStack;
import mekanism.api.recipes.cache.CachedRecipe;
import mekanism.api.recipes.inputs.IInputHandler;
import mekanism.api.recipes.inputs.ILongInputHandler;
import mekanism.api.recipes.outputs.BoxedChemicalOutputHandler;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;

public class AdsorptionCachedRecipe extends CachedRecipe<AdsorptionRecipe> {
    private final BoxedChemicalOutputHandler outputHandler;
    private final IInputHandler<@NotNull ItemStack> itemInputHandler;
    private final ILongInputHandler<@NotNull GasStack> fluidInputHandler;
    private final LongSupplier fluidUsage;
    private long fluidUsageMultiplier;

    private ItemStack recipeItem = ItemStack.EMPTY;
    private GasStack recipeFluid = GasStack.EMPTY;
    private BoxedChemicalStack output = BoxedChemicalStack.EMPTY;

    /**
     * @param recipe           Recipe.
     * @param recheckAllErrors Returns {@code true} if processing should be continued even if an error is hit in order to gather all the errors. It is recommended to not
     *                         do this every tick or if there is no one viewing recipes.
     * @param itemInputHandler Item input handler.
     * @param fluidInputHandler  Chemical input handler.
     * @param fluidUsage         Gas usage multiplier.
     * @param outputHandler    Output handler.
     */
    public AdsorptionCachedRecipe(AdsorptionRecipe recipe, BooleanSupplier recheckAllErrors, IInputHandler<@NotNull ItemStack> itemInputHandler,
                                            ILongInputHandler<@NotNull GasStack> fluidInputHandler, LongSupplier fluidUsage, BoxedChemicalOutputHandler outputHandler) {
        super(recipe, recheckAllErrors);
        this.itemInputHandler = Objects.requireNonNull(itemInputHandler, "Item input handler cannot be null.");
        this.fluidInputHandler = Objects.requireNonNull(fluidInputHandler, "Gas input handler cannot be null.");
        this.fluidUsage = Objects.requireNonNull(fluidUsage, "Gas usage cannot be null.");
        this.outputHandler = Objects.requireNonNull(outputHandler, "Input handler cannot be null.");
    }

    @Override
    protected void setupVariableValues() {
        fluidUsageMultiplier = Math.max(fluidUsage.getAsLong(), 0);
    }

    @Override
    protected void calculateOperationsThisTick(OperationTracker tracker) {
        super.calculateOperationsThisTick(tracker);
        if (tracker.shouldContinueChecking()) {
            recipeItem = itemInputHandler.getRecipeInput(recipe.getItemInput());
            if (recipeItem.isEmpty()) {
                tracker.mismatchedRecipe();
            } else {
                recipeFluid = fluidInputHandler.getRecipeInput(recipe.getGasInput());
                if (recipeFluid.isEmpty()) {
                    tracker.updateOperations(0);
                    if (!tracker.shouldContinueChecking()) {
                        return;
                    }
                }
                itemInputHandler.calculateOperationsCanSupport(tracker, recipeItem);
                if (!recipeFluid.isEmpty() && tracker.shouldContinueChecking()) {
                    fluidInputHandler.calculateOperationsCanSupport(tracker, recipeFluid, fluidUsageMultiplier);
                    if (tracker.shouldContinueChecking()) {
                        output = recipe.getOutput(recipeItem, recipeFluid);
                        outputHandler.calculateOperationsRoomFor(tracker, output);
                    }
                }
            }
        }
    }

    @Override
    public boolean isInputValid() {
        ItemStack itemInput = itemInputHandler.getInput();
        if (!itemInput.isEmpty()) {
            GasStack fluidStack = fluidInputHandler.getInput();
            if (!fluidStack.isEmpty() && recipe.test(itemInput, fluidStack)) {
                GasStack recipeFluid = fluidInputHandler.getRecipeInput(recipe.getGasInput());
                return !recipeFluid.isEmpty() && fluidStack.getAmount() >= recipeFluid.getAmount();
            }
        }
        return false;
    }

    @Override
    protected void useResources(int operations) {
        super.useResources(operations);
        if (fluidUsageMultiplier <= 0) {
            return;
        } else if (recipeFluid.isEmpty()) {
            return;
        }
        fluidInputHandler.use(recipeFluid, operations * fluidUsageMultiplier);
    }

    @Override
    protected void finishProcessing(int operations) {
        if (!recipeItem.isEmpty() && !recipeFluid.isEmpty() && !output.isEmpty()) {
            if (fluidUsageMultiplier > 0) {
                fluidInputHandler.use(recipeFluid, operations * fluidUsageMultiplier);
            }
            outputHandler.handleOutput(output, operations);
        }
    }
}
