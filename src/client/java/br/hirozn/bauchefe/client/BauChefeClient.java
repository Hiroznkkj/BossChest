package br.hirozn.bauchefe.client;

import br.hirozn.bauchefe.BauChefe;
import br.hirozn.bauchefe.net.Pacotes;
import com.mojang.blaze3d.platform.InputConstants;
import br.hirozn.bauchefe.Pescador;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.world.entity.npc.villager.Villager;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.network.chat.Component;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

public class BauChefeClient implements ClientModInitializer {
	public static final KeyMapping.Category CATEGORIA = KeyMapping.Category.register(BauChefe.id("geral"));
	/** V (com Shift segurado) abre a tela. */
	public static final KeyMapping ABRIR = new KeyMapping("key.bauchefe.abrir", InputConstants.Type.KEYBOARD, InputConstants.KEY_V, CATEGORIA);

	@Override
	public void onInitializeClient() {
		KeyMappingHelper.registerKeyMapping(ABRIR);
		EntityRenderers.register(BauChefe.BOIA, BoiaRenderer::new);
		ClientPlayNetworking.registerGlobalReceiver(Pacotes.Fala.TYPE, (p, ctx) -> MarcioCliente.fala(p));
		ClientPlayNetworking.registerGlobalReceiver(Pacotes.Anim.TYPE, (p, ctx) -> MarcioCliente.anim(p));
		ClientPlayNetworking.registerGlobalReceiver(Pacotes.Reacao.TYPE, (p, ctx) -> MarcioCliente.reacao(p));
		EntityRenderers.register(BauChefe.ASSENTO, net.minecraft.client.renderer.entity.NoopRenderer::new); // assento invisível
		Atualizador.iniciar();
		ClientPlayConnectionEvents.JOIN.register((h, sender, mc) -> {
			String a = Atualizador.aviso;
			if (a != null) mc.execute(() -> { if (mc.player != null) mc.player.sendSystemMessage(Component.literal(a)); });
		});
		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			while (ABRIR.consumeClick())
				if (mc.player != null && mc.gui.screen() == null && shift(mc)) ClientPlayNetworking.send(new Pacotes.Abrir());
		});
		ClientCommandRegistrationCallback.EVENT.register((disp, ctx) -> disp.register(ClientCommands.literal("marcioanim")
				.then(ClientCommands.literal("exportar").executes(c -> {
					try {
						var pasta = AnimExportar.exportar();
						c.getSource().sendFeedback(Component.literal("§6Animações do código exportadas em §f" + pasta
								+ "\n§7Abra o marcio.geo.json no Blockbench, importe o marcio.animation.json, mexa e exporte para §f" + AnimCustom.PASTA));
						return 1;
					} catch (Exception e) {
						BauChefe.LOG.warn("[Baú Chefe] Não deu para exportar as animações", e);
						c.getSource().sendError(Component.literal("Não deu para exportar: " + e.getMessage()));
						return 0;
					}
				}))
				.then(ClientCommands.literal("recarregar").executes(c -> { avisarAnims(c.getSource(), AnimCustom.recarregar()); return 1; }))
				.then(ClientCommands.literal("lista").executes(c -> { avisarAnims(c.getSource(), AnimCustom.nomes().size()); return 1; }))
				.then(ClientCommands.literal("tocar").then(ClientCommands.argument("nome", StringArgumentType.word())
						.suggests((c, b) -> SharedSuggestionProvider.suggest(AnimCustom.nomes(), b))
						.executes(c -> {
							String nome = StringArgumentType.getString(c, "nome");
							AnimCustom.Anim a = AnimCustom.get(nome);
							if (a == null) { c.getSource().sendError(Component.literal("Não achei a animação \"" + nome + "\" em " + AnimCustom.PASTA)); return 0; }
							var lista = c.getSource().getLevel().getEntitiesOfClass(Villager.class, c.getSource().getPlayer().getBoundingBox().inflate(32), Pescador::ehPescador);
							Villager v = lista.stream().min(java.util.Comparator.comparingDouble(x -> x.distanceToSqr(c.getSource().getPlayer()))).orElse(null);
							if (v == null) { c.getSource().sendError(Component.literal("Nenhum pescador a até 32 blocos")); return 0; }
							MarcioCliente.tocar(v.getId(), a);
							c.getSource().sendFeedback(Component.literal("§6Tocando §f" + nome + " §7(" + a.duracao() + " s, só no seu PC)"));
							return 1;
						})))));
		ClientPlayNetworking.registerGlobalReceiver(Pacotes.Estoque.TYPE, (p, ctx) -> {
			Minecraft mc = ctx.client();
			if (mc.gui.screen() instanceof BauChefeScreen s) s.atualizar(p);
			else if (mc.gui.screen() == null) mc.gui.setScreen(new BauChefeScreen(p));
		});
	}

	private static void avisarAnims(FabricClientCommandSource src, int n) {
		src.sendFeedback(Component.literal("§6" + n + " animações customizadas" + (n > 0 ? ": §f" + String.join(", ", AnimCustom.nomes()) : "")
				+ " §7(" + AnimCustom.PASTA + ")"));
		if (AnimCustom.ultimoErro != null) src.sendError(Component.literal(AnimCustom.ultimoErro));
	}

	static boolean shift(Minecraft mc) {
		return InputConstants.isKeyDown(InputConstants.KEY_LSHIFT) || InputConstants.isKeyDown(InputConstants.KEY_RSHIFT);
	}
}
