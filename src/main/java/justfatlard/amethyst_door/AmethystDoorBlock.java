package justfatlard.amethyst_door;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.phys.BlockHitResult;

/**
 * A door that does not open.
 *
 * <p>One block, two meanings, decided by which side of it you are standing on. Out in the world it
 * is a way in, and stepping through writes down where you were standing. Inside a geode it is the
 * way back to that spot - which is why the door you came in by can be mined while you are in
 * there and you still come out somewhere sensible.
 *
 * <p>Still a real door in every other respect: two halves, a hinge, placed and broken the way a
 * door is. Only the hand on the handle does something else.
 */
public class AmethystDoorBlock extends DoorBlock {
	public AmethystDoorBlock(Properties settings) {
		super(BlockSetType.STONE, settings);
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
			Player player, BlockHitResult hit) {
		if (!(level instanceof ServerLevel serverLevel)) return InteractionResult.SUCCESS;
		if (!(player instanceof ServerPlayer serverPlayer)) return InteractionResult.PASS;

		if (level.dimension().equals(Pocket.DIMENSION)) {
			leave(serverLevel, serverPlayer);
		} else {
			enter(serverLevel, serverPlayer, pos);
		}
		return InteractionResult.SUCCESS;
	}

	/** In: remember the doorstep, build the geode if this is the first time, and go. */
	private static void enter(ServerLevel level, ServerPlayer player, BlockPos door) {
		ServerLevel pocket = Pocket.level(level.getServer());
		if (pocket == null) {
			player.sendSystemMessage(Component.translatable("amethyst-door-justfatlard.door.no_pocket"));
			return;
		}

		knock(level, door);
		goIn(level, pocket, player);
	}

	/**
	 * Into the player's own geode from wherever they are standing, as if through a door there:
	 * the way out inside leads back to this spot. For another mod that offers the trip.
	 *
	 * <p>Only to a geode that exists. A geode is had by going through an amethyst door, and a
	 * way in that made one would be a way round making the door.
	 *
	 * @return false when there is nowhere to go, having said why
	 */
	public static boolean visit(ServerPlayer player) {
		ServerLevel level = player.level();
		if (level.dimension().equals(Pocket.DIMENSION)) {
			player.sendSystemMessage(Component.literal("You are already in the geodes."));
			return false;
		}
		ServerLevel pocket = Pocket.level(level.getServer());
		if (pocket == null) {
			player.sendSystemMessage(Component.translatable("amethyst-door-justfatlard.door.no_pocket"));
			return false;
		}
		if (!PocketVault.get(level.getServer()).hasPlot(player.getUUID())) {
			player.sendSystemMessage(Component.literal("You have no geode yet. Go through an amethyst door first."));
			return false;
		}
		goIn(level, pocket, player);
		return true;
	}

	/** Remember the doorstep, build the geode if this is the first time or it lost its door, and go. */
	private static void goIn(ServerLevel level, ServerLevel pocket, ServerPlayer player) {
		PocketVault vault = PocketVault.get(level.getServer());
		boolean first = !vault.hasPlot(player.getUUID());
		vault.plotFor(player.getUUID());
		Pocket.Site mine = Pocket.siteOf(vault, player.getUUID());

		vault.rememberDoorstep(player.getUUID(), new PocketVault.Doorstep(
			level.dimension(), player.getX(), player.getY(), player.getZ(),
			player.getYRot(), player.getXRot()));

		// Cut once, or cut again if the way out is not where it should be: a geode without its
		// door is a geode somebody is going to be stuck in.
		if (first || !Pocket.hasDoor(pocket, mine)) Geode.build(pocket, Pocket.clusterOf(vault, player.getUUID()));

		BlockPos arrival = mine.arrival();
		player.teleportTo(pocket, arrival.getX() + 0.5, arrival.getY(), arrival.getZ() + 0.5,
			java.util.Set.<Relative>of(), mine.arrivalYaw(), 0F, true);
	}

	/**
	 * Somebody standing in the pocket whose geode has no way out.
	 *
	 * <p>Called as players load into the pocket. A geode cut by an earlier build had its floor,
	 * door and doorstep outside the shell, and anyone who went through was left standing beside
	 * their geode with no door to come back by. Cutting it again and moving them onto the floor
	 * is how they get out, without anybody having to find them.
	 */
	public static void rescue(ServerLevel pocket, ServerPlayer player) {
		PocketVault vault = PocketVault.get(pocket.getServer());
		if (!vault.hasPlot(player.getUUID())) return;

		Pocket.Site mine = Pocket.siteOf(vault, player.getUUID());
		if (Pocket.hasDoor(pocket, mine)) return;

		Geode.build(pocket, Pocket.clusterOf(vault, player.getUUID()));
		BlockPos arrival = mine.arrival();
		player.teleportTo(pocket, arrival.getX() + 0.5, arrival.getY(), arrival.getZ() + 0.5,
			java.util.Set.<Relative>of(), mine.arrivalYaw(), 0F, true);
	}

	/** Out: back to the spot they were standing on, whatever has happened to the door since. */
	private static void leave(ServerLevel level, ServerPlayer player) {
		PocketVault.Doorstep doorstep = PocketVault.get(level.getServer()).doorstepOf(player.getUUID());

		ServerLevel home = doorstep == null ? null : level.getServer().getLevel(doorstep.dimension());
		if (home == null) {
			// Nowhere recorded, or the world it named is gone. Spawn is the one place that always
			// exists, and being put there beats being left in a sealed room.
			home = level.getServer().overworld();
			BlockPos spawn = home.getRespawnData().pos();
			player.teleportTo(home, spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5,
				java.util.Set.<Relative>of(), 0F, 0F, true);
			return;
		}

		player.teleportTo(home, doorstep.x(), doorstep.y(), doorstep.z(),
			java.util.Set.<Relative>of(), doorstep.yaw(), doorstep.pitch(), true);
	}

	private static void knock(ServerLevel level, BlockPos pos) {
		level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 0.8F, 1.0F);
	}
}
