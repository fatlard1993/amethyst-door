package justfatlard.amethyst_door;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

/**
 * Building somebody's geode, once.
 *
 * <p>Layered the way a real one is, outside in: basalt skin, calcite rind, then the amethyst you
 * actually see from inside. A floor is cut across the bottom third, because a sphere is a lovely
 * shape to look at and an awful one to put a chest on.
 */
public final class Geode {
	private Geode() {}

	/** How far down the sphere the floor is laid. */
	private static final int FLOOR_DROP = 5;

	/** Roughly one in this many amethyst blocks comes up budding, for the look and the drops. */
	private static final int BUDDING_IN = 14;

	public static void build(ServerLevel level, int plot) {
		BlockPos centre = Pocket.centreOf(plot);
		RandomSource random = RandomSource.create(plot * 31L + 17L);

		int reach = Pocket.OUTER_RADIUS + 1;
		BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();

		for (int x = -reach; x <= reach; x++) {
			for (int y = -reach; y <= reach; y++) {
				for (int z = -reach; z <= reach; z++) {
					at.set(centre.getX() + x, centre.getY() + y, centre.getZ() + z);

					BlockState state = shellAt(Math.sqrt(x * x + y * y + z * z), random);
					if (state == null) continue;

					level.setBlock(at, state, Block.UPDATE_CLIENTS);
				}
			}
		}

		layFloor(level, centre);
		hangDoor(level, plot);
	}

	/** Which layer a given distance from the middle falls in, or null for the hollow and beyond. */
	private static BlockState shellAt(double distance, RandomSource random) {
		if (distance > Pocket.OUTER_RADIUS) return null;
		if (distance >= Pocket.OUTER_RADIUS - 1) return Blocks.SMOOTH_BASALT.defaultBlockState();
		if (distance >= Pocket.OUTER_RADIUS - 2) return Blocks.CALCITE.defaultBlockState();

		if (distance >= Pocket.INNER_RADIUS - 0.5) {
			return random.nextInt(BUDDING_IN) == 0
				? Blocks.BUDDING_AMETHYST.defaultBlockState()
				: Blocks.AMETHYST_BLOCK.defaultBlockState();
		}
		return null;
	}

	/** A flat amethyst floor across the bottom, so the room can actually be used. */
	private static void layFloor(ServerLevel level, BlockPos centre) {
		int y = Pocket.FLOOR_Y;
		BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();

		for (int x = -Pocket.INNER_RADIUS; x <= Pocket.INNER_RADIUS; x++) {
			for (int z = -Pocket.INNER_RADIUS; z <= Pocket.INNER_RADIUS; z++) {
				if (x * x + z * z > Pocket.INNER_RADIUS * Pocket.INNER_RADIUS) continue;

				at.set(centre.getX() + x, y, centre.getZ() + z);
				level.setBlock(at, Blocks.AMETHYST_BLOCK.defaultBlockState(), Block.UPDATE_CLIENTS);

				// Clear whatever the sphere put in the space the floor now occupies
				for (int above = 1; above <= FLOOR_DROP; above++) {
					at.set(centre.getX() + x, y - above, centre.getZ() + z);
					if (Pocket.isShell(at)) continue;
					level.setBlock(at, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
				}
			}
		}
	}

	/** The way out, which is the same door and is never removable. */
	private static void hangDoor(ServerLevel level, int plot) {
		BlockPos bottom = Pocket.doorIn(plot);

		BlockState lower = Main.AMETHYST_DOOR.defaultBlockState()
			.setValue(DoorBlock.FACING, Direction.NORTH)
			.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER)
			.setValue(DoorBlock.HINGE, DoorHingeSide.LEFT);

		level.setBlock(bottom, lower, Block.UPDATE_ALL);
		level.setBlock(bottom.above(), lower.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER),
			Block.UPDATE_ALL);
	}
}
