package justfatlard.amethyst_door;

import justfatlard.pandorical.api.BlockRegistration;
import justfatlard.pandorical.api.ItemRegistration;
import justfatlard.pandorical.api.PandoricalApi;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.DoubleHighBlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;

public class Main implements ModInitializer {
	public static final String MOD_ID = "amethyst-door-justfatlard";

	public static final Identifier AMETHYST_DOOR_ID = Identifier.fromNamespaceAndPath(MOD_ID, "amethyst_door");

	public static final ResourceKey<Block> AMETHYST_DOOR_KEY = ResourceKey.create(Registries.BLOCK, AMETHYST_DOOR_ID);
	public static final ResourceKey<Item> AMETHYST_DOOR_ITEM_KEY = ResourceKey.create(Registries.ITEM, AMETHYST_DOOR_ID);

	public static final AmethystDoorBlock AMETHYST_DOOR = new AmethystDoorBlock(
		BlockBehaviour.Properties.of()
			.strength(1.5F, 6.0F)
			.sound(SoundType.AMETHYST)
			.noOcclusion()
			.setId(AMETHYST_DOOR_KEY)
	);

	/** Two-high like every vanilla door's item, so it clears the block above the way they do. */
	public static final BlockItem AMETHYST_DOOR_ITEM = new DoubleHighBlockItem(
		AMETHYST_DOOR,
		new Item.Properties().setId(AMETHYST_DOOR_ITEM_KEY).useBlockDescriptionPrefix()
	);

	@Override
	public void onInitialize() {
		if (PandoricalApi.isAvailable()) {
			PandoricalApi.content().registerBlock(MOD_ID + ":amethyst_door", new BlockRegistration()
				.baseBlock("minecraft:iron_door")
				// A right-click travels rather than opens, so the client must not predict either.
				.interactive()
				// The base is an iron door for its shape and states, not its hardness: without
				// these the client digs at the iron door's five-and-a-pickaxe while the server
				// breaks it at this block's own, and the two disagree for the whole dig.
				.strength(1.5F)
				.requiresCorrectTool(false)
				.model(MOD_ID + ":block/amethyst_door_bottom_left"));
			PandoricalApi.content().registerItem(MOD_ID + ":amethyst_door", new ItemRegistration()
				.model(MOD_ID + ":item/amethyst_door"));
			PandoricalApi.content().registerModAssets(MOD_ID);
		}

		Registry.register(BuiltInRegistries.BLOCK, AMETHYST_DOOR_ID, AMETHYST_DOOR);
		Registry.register(BuiltInRegistries.ITEM, AMETHYST_DOOR_ID, AMETHYST_DOOR_ITEM);

		// The geode is the room, not the furniture. Everything inside the hollow is the player's
		// to do as they like with; the shell and the way out are not, which is what stops somebody
		// mining a hole into the void they cannot climb back out of.
		PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, blockEntity) -> {
			if (!level.dimension().equals(Pocket.DIMENSION)) return true;
			// Creative is the one way through, for repairs. Being an op used to be another, and
			// an op in survival found their own shell gave way under a pickaxe like anyone's
			// furniture; the shell is meant to be the one thing in here that does not.
			if (player.isCreative()) return true;

			// What grows off the shell is the shell, cluster included: a harvestable cluster on a
			// budding block that cannot be broken is a shard farm, and the geode is a room.
			boolean shell = (level instanceof ServerLevel serverLevel && Pocket.isShell(serverLevel, pos))
				|| state.getBlock() instanceof AmethystDoorBlock
				|| state.is(Blocks.SMALL_AMETHYST_BUD) || state.is(Blocks.MEDIUM_AMETHYST_BUD)
				|| state.is(Blocks.LARGE_AMETHYST_BUD) || state.is(Blocks.AMETHYST_CLUSTER);
			if (!shell) return true;

			if (player instanceof ServerPlayer told) {
				told.sendSystemMessage(Component.translatable("amethyst-door-justfatlard.geode.solid"));
			}
			return false;
		});

		// Anyone loading into the pocket whose geode has lost its door gets it cut again and is
		// stood on the floor. Cheap: a block read per player load in one dimension.
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents.ENTITY_LOAD.register(
			(entity, level) -> {
				if (!level.dimension().equals(Pocket.DIMENSION)) return;
				if (entity instanceof ServerPlayer player) AmethystDoorBlock.rescue(level, player);
			});

		net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback.EVENT.register(
			(dispatcher, registry, environment) -> GeodeCommands.register(dispatcher));
		// Separate from the commands, which are registered per world load: the answer handler is
		// registered once, and registering it again each time would stack copies of it.
		GeodeCommands.listen();
		System.out.println("[" + MOD_ID + "] Loaded (server-side with Pandorical)");
	}
}
