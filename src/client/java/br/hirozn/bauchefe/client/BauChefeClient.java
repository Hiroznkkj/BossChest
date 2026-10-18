package br.hirozn.bauchefe.client;

import br.hirozn.bauchefe.BauChefe;
import br.hirozn.bauchefe.net.Pacotes;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
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
		Atualizador.iniciar();
		ClientPlayConnectionEvents.JOIN.register((h, sender, mc) -> {
			String a = Atualizador.aviso;
			if (a != null) mc.execute(() -> { if (mc.player != null) mc.player.sendSystemMessage(Component.literal(a)); });
		});
		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			while (ABRIR.consumeClick())
				if (mc.player != null && mc.gui.screen() == null && shift(mc)) ClientPlayNetworking.send(new Pacotes.Abrir());
		});
		ClientPlayNetworking.registerGlobalReceiver(Pacotes.Estoque.TYPE, (p, ctx) -> {
			Minecraft mc = ctx.client();
			if (mc.gui.screen() instanceof BauChefeScreen s) s.atualizar(p);
			else if (mc.gui.screen() == null) mc.gui.setScreen(new BauChefeScreen(p));
		});
	}

	static boolean shift(Minecraft mc) {
		return InputConstants.isKeyDown(InputConstants.KEY_LSHIFT) || InputConstants.isKeyDown(InputConstants.KEY_RSHIFT);
	}
}
