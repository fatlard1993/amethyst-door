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

	/** Roughly one in this many amethyst blocks comes up budding, for the look and the drops. */
	private static final int BUDDING_IN = 14;

	/**
	 * Cut the geode, or cut it again.
	 *
	 * <p>Safe to run over an existing one: the shell and the floor are rewritten to what they
	 * should be, and the hollow above the floor is left alone, so whatever the owner built in it
	 * stays. It runs again only when the way out is found missing, which a working geode never
	 * has.
	 */
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

	/**
	 * A flat amethyst floor across the bottom, so the room can actually be used.
	 *
	 * <p>Solid from the floor down to the shell rather than a disc with a cavity under it, so the
	 * bottom of the geode reads as one mass of crystal and there is nothing to fall through.
	 */
	private static void layFloor(ServerLevel level, BlockPos centre) {
		BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
		for (int y = centre.getY() - Pocket.INNER_RADIUS; y <= Pocket.FLOOR_Y; y++) {
			for (int x = -Pocket.INNER_RADIUS; x <= Pocket.INNER_RADIUS; x++) {
				for (int z = -Pocket.INNER_RADIUS; z <= Pocket.INNER_RADIUS; z++) {
					at.set(centre.getX() + x, y, centre.getZ() + z);
					if (Pocket.isShell(at)) continue;
					level.setBlock(at, Blocks.AMETHYST_BLOCK.defaultBlockState(), Block.UPDATE_CLIENTS);
				}
			}
		}
	}

	/**
	 * The way out, which is the same door and is never removable, set into a frame cut for it.
	 *
	 * <p>The sphere is not trusted to make a doorway on its own: where the door stands the wall
	 * curves away, so the blocks either side, above and behind the door are laid as amethyst
	 * whatever the shell arithmetic made of them, and the sill under it too. Then the door goes
	 * in, and it is a door in a wall.
	 */
	private static void hangDoor(ServerLevel level, int plot) {
		BlockPos bottom = Pocket.doorIn(plot);
		BlockState frame = Blocks.AMETHYST_BLOCK.defaultBlockState();

		for (BlockPos half : new BlockPos[] {bottom, bottom.above()}) {
			level.setBlock(half.west(), frame, Block.UPDATE_CLIENTS);
			level.setBlock(half.east(), frame, Block.UPDATE_CLIENTS);
			level.setBlock(half.north(), frame, Block.UPDATE_CLIENTS);
		}
		level.setBlock(bottom.above(2), frame, Block.UPDATE_CLIENTS);
		level.setBlock(bottom.below(), frame, Block.UPDATE_CLIENTS);

		// An earlier build stood the door a block further into the room. A geode cut then still
		// has it there, right in front of where the door goes now, so it is taken down first: a
		// door in the doorway of a door is no way out.
		for (BlockPos old : new BlockPos[] {bottom.south(), bottom.above().south()}) {
			if (level.getBlockState(old).getBlock() instanceof AmethystDoorBlock) {
				level.setBlock(old, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
			}
		}

		BlockState lower = Main.AMETHYST_DOOR.defaultBlockState()
			.setValue(DoorBlock.FACING, Direction.NORTH)
			.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER)
			.setValue(DoorBlock.HINGE, DoorHingeSide.LEFT);

		level.setBlock(bottom, lower, Block.UPDATE_ALL);
		level.setBlock(bottom.above(), lower.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER),
			Block.UPDATE_ALL);
	}
}
