package org.cloudburstmc.server.block.behavior;

import com.nukkitx.math.vector.Vector3f;
import lombok.val;
import org.cloudburstmc.api.block.Block;
import org.cloudburstmc.api.block.BlockStates;
import org.cloudburstmc.api.block.BlockTypes;
import org.cloudburstmc.api.item.ItemStack;
import org.cloudburstmc.api.level.Level;
import org.cloudburstmc.api.util.Direction;
import org.cloudburstmc.api.util.data.BlockColor;
import org.cloudburstmc.server.item.ItemStacks;

public class BlockBehaviorNetherPortal extends FloodableBlockBehavior {

    public static void spawnPortal(Vector3f pos, Level level) {
        int x = pos.getFloorX(), y = pos.getFloorY(), z = pos.getFloorZ();

        for (int xx = -1; xx < 4; xx++) {
            for (int yy = 1; yy < 4; yy++) {
                for (int zz = -1; zz < 3; zz++) {
                    level.setBlockAt(x + xx, y + yy, z + zz, BlockStates.AIR);
                    level.setBlockAt(x + xx, y + yy, z + zz, 1, BlockStates.AIR);
                }
            }
        }

        val obsidian = BlockStates.OBSIDIAN;
        val portal = BlockStates.PORTAL;

        level.setBlockAt(x + 1, y, z, obsidian);
        level.setBlockAt(x + 2, y, z, obsidian);

        z += 1;
        level.setBlockAt(x, y, z, obsidian);
        level.setBlockAt(x + 1, y, z, obsidian);
        level.setBlockAt(x + 2, y, z, obsidian);
        level.setBlockAt(x + 3, y, z, obsidian);

        z += 1;
        level.setBlockAt(x + 1, y, z, obsidian);
        level.setBlockAt(x + 2, y, z, obsidian);

        z -= 1;
        y += 1;
        level.setBlockAt(x, y, z, obsidian);
        level.setBlockAt(x + 1, y, z, portal);
        level.setBlockAt(x + 2, y, z, portal);
        level.setBlockAt(x + 3, y, z, obsidian);

        y += 1;
        level.setBlockAt(x, y, z, obsidian);
        level.setBlockAt(x + 1, y, z, portal);
        level.setBlockAt(x + 2, y, z, portal);
        level.setBlockAt(x + 3, y, z, obsidian);

        y += 1;
        level.setBlockAt(x, y, z, obsidian);
        level.setBlockAt(x + 1, y, z, portal);
        level.setBlockAt(x + 2, y, z, portal);
        level.setBlockAt(x + 3, y, z, obsidian);

        y += 1;
        level.setBlockAt(x, y, z, obsidian);
        level.setBlockAt(x + 1, y, z, obsidian);
        level.setBlockAt(x + 2, y, z, obsidian);
        level.setBlockAt(x + 3, y, z, obsidian);
    }


    @Override
    public boolean onBreak(Block block, ItemStack item) {
        boolean result = super.onBreak(block, item);
        for (Direction face : Direction.values()) {
            Block b = block.getSide(face);
            if (b.getState().getType() == BlockTypes.PORTAL) {
                result &= b.getState().getBehavior().onBreak(b, item);
            }
        }
        return result;
    }

    @Override
    public boolean hasEntityCollision() {
        return true;
    }

    @Override
    public BlockColor getColor(Block block) {
        return BlockColor.AIR_BLOCK_COLOR;
    }

    @Override
    public boolean canBePushed() {
        return false;
    }


//    @Override //TODO: bounding box
//    protected AxisAlignedBB recalculateBoundingBox() {
//        return this;
//    }

    @Override
    public ItemStack toItem(Block block) {
        return ItemStacks.AIR;
    }
}
