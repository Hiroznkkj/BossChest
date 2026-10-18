package br.hirozn.bauchefe;

import br.hirozn.bauchefe.net.Pacotes;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.item.trading.MerchantOffer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.item.ItemArgument;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.material.MapColor;

import java.util.ArrayList;
import java.util.List;

public class BauChefe implements ModInitializer {
	public static final String MODID = "bauchefe";

	public static Identifier id(String path) { return Identifier.fromNamespaceAndPath(MODID, path); }

	private static final ResourceKey<net.minecraft.world.level.block.Block> BLOCK_KEY = ResourceKey.create(Registries.BLOCK, id("bau_chefe"));
	public static final BauChefeBlock BAU_CHEFE = Registry.register(BuiltInRegistries.BLOCK, BLOCK_KEY,
			new BauChefeBlock(BlockBehaviour.Properties.of().setId(BLOCK_KEY).mapColor(MapColor.GOLD)
					.strength(3.0f).sound(SoundType.WOOD)
					.lightLevel(s -> s.getValue(BauChefeBlock.ATIVO) ? 11 : 4)));
	private static final ResourceKey<Item> ITEM_KEY = ResourceKey.create(Registries.ITEM, id("bau_chefe"));
	public static final Item BAU_CHEFE_ITEM = Registry.register(BuiltInRegistries.ITEM, ITEM_KEY,
			new BlockItem(BAU_CHEFE, new Item.Properties().setId(ITEM_KEY).useBlockDescriptionPrefix()));
	public static final BlockEntityType<BauChefeBlockEntity> BAU_CHEFE_BE = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,
			id("bau_chefe"), FabricBlockEntityTypeBuilder.create(BauChefeBlockEntity::new, BAU_CHEFE).build());

	@Override
	public void onInitialize() {
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.FUNCTIONAL_BLOCKS).register(out -> out.accept(BAU_CHEFE_ITEM));

		Pescador.registrar();

		// livros à venda acima do máximo normal (gerados quando o limite era 255) voltam ao máximo
		UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (!level.isClientSide() && entity instanceof AbstractVillager v) corrigirOfertas(v);
			return InteractionResult.PASS;
		});

		PayloadTypeRegistry.serverboundPlay().register(Pacotes.Abrir.TYPE, Pacotes.Abrir.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(Pacotes.Sacar.TYPE, Pacotes.Sacar.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(Pacotes.Guardar.TYPE, Pacotes.Guardar.CODEC);
		PayloadTypeRegistry.clientboundPlay().registerLarge(Pacotes.Estoque.TYPE, Pacotes.Estoque.CODEC, 8 << 20);

		// /bauchefe abrir: mesmo efeito do Shift+V (útil sem teclado ou em comandos)
		CommandRegistrationCallback.EVENT.register((d, reg, env) -> d.register(Commands.literal("bauchefe")
				.then(Commands.literal("abrir").executes(c -> { responder(c.getSource().getPlayerOrException(), ""); return 1; }))
				.then(Commands.literal("guardar").executes(c -> {
					ServerPlayer pl = c.getSource().getPlayerOrException();
					Rede r = redePerto(pl);
					if (r == null) { foraDaZona(pl); return 0; }
					java.util.Set<String> ficou = r.guardarInventario(pl);
					pl.sendSystemMessage(Component.literal(ficou.isEmpty() ? "§6Inventário guardado no Baú Chefe" : "§c[Baú Chefe] " + Rede.erro(ficou)));
					return 1;
				}))
				.then(Commands.literal("sacar").then(Commands.argument("item", ItemArgument.item(reg))
						.then(Commands.argument("qtd", IntegerArgumentType.integer(1)).executes(c -> {
							ServerPlayer pl = c.getSource().getPlayerOrException();
							Rede r = redePerto(pl);
							if (r == null) { foraDaZona(pl); return 0; }
							var proto = ItemArgument.getItem(c, "item").createItemStack(1);
							int n = r.sacar(pl, proto, IntegerArgumentType.getInteger(c, "qtd"));
							pl.sendSystemMessage(Component.literal("§6Sacou " + n + "x " + proto.getHoverName().getString()));
							return n;
						}))))));

		ServerPlayNetworking.registerGlobalReceiver(Pacotes.Abrir.TYPE, (p, ctx) -> responder(ctx.player(), ""));
		ServerPlayNetworking.registerGlobalReceiver(Pacotes.Guardar.TYPE, (p, ctx) -> {
			Rede r = redePerto(ctx.player());
			if (r == null) { foraDaZona(ctx.player()); return; }
			java.util.Set<String> ficou = r.guardarInventario(ctx.player());
			responder(ctx.player(), ficou.isEmpty() ? "Inventário guardado" : "§c" + Rede.erro(ficou));
		});
		ServerPlayNetworking.registerGlobalReceiver(Pacotes.Sacar.TYPE, (p, ctx) -> {
			Rede r = redePerto(ctx.player());
			if (r == null) { foraDaZona(ctx.player()); return; }
			int pedido = Math.max(1, p.qtd());
			int n = r.sacar(ctx.player(), p.item(), pedido);
			String aviso = n == 0 ? "Sem espaço no inventário" : n < pedido ? "Sacou " + n + " (inventário cheio)" : "Sacou " + n + "x " + p.item().getHoverName().getString();
			responder(ctx.player(), aviso);
		});
	}

	static void corrigirOfertas(AbstractVillager v) {
		for (MerchantOffer o : v.getOffers()) {
			var res = o.getResult();
			ItemEnchantments ench = res.get(DataComponents.STORED_ENCHANTMENTS);
			if (ench == null || ench.isEmpty()) continue;
			ItemEnchantments.Mutable m = new ItemEnchantments.Mutable(ench);
			boolean mudou = false;
			for (var e : ench.entrySet()) {
				int max = e.getKey().value().getMaxLevel();
				if (e.getIntValue() > max) { m.set(e.getKey(), max); mudou = true; }
			}
			if (mudou) res.set(DataComponents.STORED_ENCHANTMENTS, m.toImmutable());
		}
	}

	private static void foraDaZona(ServerPlayer p) {
		p.sendOverlayMessage(Component.literal("§6Você não está na zona de um Baú Chefe"));
	}

	private static void responder(ServerPlayer p, String aviso) {
		Rede r = redePerto(p);
		if (r == null) { foraDaZona(p); return; }
		List<Pacotes.Linha> linhas = new ArrayList<>();
		for (Rede.Total t : r.totais()) linhas.add(new Pacotes.Linha(t.item(), t.qtd()));
		ServerPlayNetworking.send(p, new Pacotes.Estoque(linhas, r.membros.size(), aviso));
	}

	/** Baú Chefe mais próximo dentro do raio do jogador, ou null. */
	static Rede redePerto(ServerPlayer p) {
		ServerLevel level = p.level();
		BlockPos pp = p.blockPosition();
		ChunkPos cp = ChunkPos.containing(pp);
		BauChefeBlockEntity melhor = null;
		double best = Rede.RAIO * Rede.RAIO;
		for (int cx = cp.x() - 2; cx <= cp.x() + 2; cx++)
			for (int cz = cp.z() - 2; cz <= cp.z() + 2; cz++) {
				LevelChunk ch = level.getChunkSource().getChunkNow(cx, cz);
				if (ch == null) continue;
				for (BlockEntity be : ch.getBlockEntities().values())
					if (be instanceof BauChefeBlockEntity b) {
						double d = b.getBlockPos().distSqr(pp);
						if (d <= best) { best = d; melhor = b; }
					}
			}
		return melhor == null ? null : Rede.montar(level, melhor);
	}
}
