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
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
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

	public static final BlockItem AMETHYST_DOOR_ITEM = new BlockItem(
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
			// The same bar the suite's admin tools use: somebody else's room is not your problem
			// unless you are the one running the server.
			if (player.permissions().hasPermission(
					net.minecraft.server.permissions.Permissions.COMMANDS_GAMEMASTER)) return true;

			boolean theirs = !Pocket.isShell(pos) && !(state.getBlock() instanceof AmethystDoorBlock);
			if (theirs) return true;

			if (player instanceof ServerPlayer told) {
				told.sendSystemMessage(Component.translatable("amethyst-door-justfatlard.geode.solid"));
			}
			return false;
		});

		System.out.println("[" + MOD_ID + "] Loaded (server-side with Pandorical)");
	}
}
