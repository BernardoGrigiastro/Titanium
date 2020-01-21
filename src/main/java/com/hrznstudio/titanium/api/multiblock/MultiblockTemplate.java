package com.hrznstudio.titanium.api.multiblock;

import com.google.common.collect.ImmutableList;
import com.hrznstudio.titanium.api.multiblock.matcher.IMatch;
import com.hrznstudio.titanium.api.multiblock.matcher.Matcher;
import com.hrznstudio.titanium.util.BlockPosUtil;
import com.hrznstudio.titanium.util.RotationUtil;
import com.hrznstudio.titanium.util.StaticTemplateUtil;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Direction;
import net.minecraft.util.Mirror;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.Rotation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3i;
import net.minecraft.world.World;
import net.minecraft.world.gen.feature.template.PlacementSettings;
import net.minecraft.world.gen.feature.template.Template;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.List;

/**
 * Big shout-outs and credits to Immersive Engineering for providing most of the inspiration and example implementation code for us to figure out this implementation.
 * You should go check out their github repo:
 *
 * @Link https://github.com/BluSunrize/ImmersiveEngineering
 */
public abstract class MultiblockTemplate implements IMultiblockHandler {

    private final ResourceLocation id;
    private final List<IMatch> additionalPredicates;
    @Nullable
    private Template template;
    private BlockPos masterPos;
    private BlockPos triggerPos;
    @Nullable
    private List<BlockState> blockStates;
    private BlockState triggerState;

    public MultiblockTemplate(ResourceLocation id, BlockPos masterPos, BlockPos triggerPos, List<IMatch> additionalPredicates) {
        this.id = id;
        this.masterPos = masterPos;
        this.triggerPos = triggerPos;
        this.additionalPredicates = additionalPredicates;
    }

    public MultiblockTemplate(ResourceLocation id, BlockPos masterPos, BlockPos triggerPos) {
        this.id = id;
        this.masterPos = masterPos;
        this.triggerPos = triggerPos;
        this.additionalPredicates = ImmutableList.of();
    }

    @Override
    public boolean isBlockTrigger(BlockState state) {
        getTemplate();
        return state.getBlock().equals(triggerState.getBlock());
    }

    @Override
    public boolean createStructure(World world, BlockPos controllerPos, Direction direction, PlayerEntity playerEntity) {

        if (direction.getAxis() == Direction.Axis.Y) {
            direction = Direction.fromAngle(playerEntity.rotationYaw);
        }

        Rotation rotation = RotationUtil.getRotationBetweenFacings(Direction.NORTH, direction.getOpposite());
        if (rotation == null) {
            return false;
        }

        Template template = getTemplate();
        List<Mirror> mirrorStates;
        if (canBeMirrored()) {
            mirrorStates = ImmutableList.of(Mirror.NONE, Mirror.FRONT_BACK);
        } else {
            mirrorStates = ImmutableList.of(Mirror.NONE);
        }

        mirrorLoop:
        for (Mirror mirror : mirrorStates) {
            PlacementSettings placeSet = new PlacementSettings().setMirror(mirror).setRotation(rotation);
            BlockPos origin = controllerPos.subtract(Template.transformedBlockPos(placeSet, triggerPos));
            for (Template.BlockInfo info : template.blocks.get(0)) {
                BlockPos realRelPos = Template.transformedBlockPos(placeSet, info.pos);
                BlockPos here = origin.add(realRelPos);
                BlockState expected = info.state.mirror(mirror).rotate(rotation);
                BlockState inWorld = world.getBlockState(here);

                if (!Matcher.matches(expected, inWorld, world, here, additionalPredicates).isAllowed()) {
                    continue mirrorLoop;
                }
            }
            formStructure(world, origin, rotation, mirror, direction);
            return true;
        }
        return false;
    }

    @Override
    public void formStructure(World world, BlockPos originPos, Rotation rotation, Mirror mirror, Direction direction) {
        BlockPos truePos = BlockPosUtil.withSettingsAndOffset(originPos, masterPos, mirror, rotation);
        for (Template.BlockInfo blockInfo : template.blocks.get(0)) {
            BlockPos actualPos = BlockPosUtil.withSettingsAndOffset(truePos, blockInfo.pos, mirror, rotation);
        }
    }

    protected abstract void replaceStructureBlock(Template.BlockInfo info, World world, BlockPos originPos, boolean mirrored, Direction clickDirection, Vec3i offsetFromMaster);

    @Override
    public void breakStructure(World world, BlockPos originPos, Direction direction, boolean mirrored) {
        Mirror mirror = mirrored ? Mirror.FRONT_BACK : Mirror.NONE;
        Rotation rot = RotationUtil.getRotationBetweenFacings(Direction.NORTH, direction);
        if (rot != null) {
            for (Template.BlockInfo info : getTemplate().blocks.get(0)) {
                BlockPos actualPos = BlockPosUtil.withSettingsAndOffset(originPos, info.pos, mirror, rot);
                prepareBlockForDisassembly(world, actualPos);
                world.setBlockState(actualPos, info.state.mirror(mirror).rotate(rot));
            }
        }
    }

    protected void prepareBlockForDisassembly(World world, BlockPos originPos) {}

    @Override
    public boolean canRenderFormedMultiblock() {
        return false;
    }

    @Override
    public void renderFormedMultiblock() {
    }

    public ResourceLocation getId() {
        return id;
    }

    @Nullable
    private Template getTemplate() {
        if (template == null) {
            try {
                template = StaticTemplateUtil.loadStaticTemplate(id);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        return template;
    }

    private boolean canBeMirrored() {
        return true;
    }
}