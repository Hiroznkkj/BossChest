package br.hirozn.bauchefe.client;

import br.hirozn.bauchefe.BauChefe;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Voz do Márcio presa na boca dele, com o volume calculado aqui a cada tick (a ideia vem do Plasmo Voice,
 * de Plasmo, LGPL-3.0; código próprio):
 * - distância: queda cúbica, (1 - d/alcance)³, que soa natural;
 * - direção: de costas para quem ouve ele fica mais baixo;
 * - paredes: cada bloco sólido entre a boca e quem ouve abafa a voz, mudando aos poucos.
 * Toca junto duas versões do áudio, a normal e uma abafada com eco: longe ou atrás de parede, pesa mais a com eco.
 */
public class MarcioSom extends AbstractTickableSoundInstance {
	private static final float ALCANCE = 32f, CONE = 72.5f;
	private final Entity quem;
	private final boolean eco;
	private float oclusao = -1f;

	public MarcioSom(Entity quem, String som, boolean eco) {
		super(SoundEvent.createVariableRangeEvent(BauChefe.id("marcio." + som + (eco ? "_eco" : ""))), SoundSource.VOICE, RandomSource.create());
		this.quem = quem;
		this.eco = eco;
		this.attenuation = SoundInstance.Attenuation.NONE; // a distância é calculada aqui; a posição continua dando o lado (panning)
		this.looping = false;
		atualizar();
	}

	public static void tocar(Entity quem, String som) {
		var sm = Minecraft.getInstance().getSoundManager();
		sm.play(new MarcioSom(quem, som, false));
		sm.play(new MarcioSom(quem, som, true));
	}

	@Override
	public boolean canStartSilent() { return true; } // o eco começa mudo quando a pessoa está perto

	@Override
	public void tick() {
		if (quem.isRemoved()) { stop(); return; }
		atualizar();
	}

	private void atualizar() {
		Vec3 boca = quem.getEyePosition();
		x = boca.x; y = boca.y; z = boca.z;
		Minecraft mc = Minecraft.getInstance();
		Entity ouvinte = mc.getCameraEntity() != null ? mc.getCameraEntity() : mc.player;
		if (ouvinte == null || mc.level == null) { volume = 0; return; }
		Vec3 ouvido = ouvinte.getEyePosition();
		float d = (float) boca.distanceTo(ouvido);

		float ganho = Mth.clamp(1f - d / ALCANCE, 0f, 1f);
		ganho = ganho * ganho * ganho;

		// direção: dentro de um cone de 145° na frente dele fica igual; atrás cai até 35%
		Vec3 olhar = quem.getViewVector(1f), praOuvinte = ouvido.subtract(boca).normalize();
		float angulo = (float) Math.toDegrees(Math.acos(Mth.clamp(olhar.dot(praOuvinte), -1, 1)));
		if (angulo > CONE) {
			float f = 1f - (angulo - CONE) / (180f - CONE);
			ganho *= 0.35f + 0.65f * f * f;
		}

		// paredes: muda no máximo 0,05 por tick para não dar tranco
		float alvo = oclusao(mc.level, boca, ouvido);
		oclusao = oclusao < 0 ? alvo : oclusao + Mth.clamp(alvo - oclusao, -0.05f, 0.05f);
		ganho *= 1f - 0.6f * oclusao;

		float molhado = Mth.clamp((d - 3f) / 20f, 0f, 0.7f) + 0.5f * oclusao; // longe ou atrás de parede: mais eco
		molhado = Math.min(molhado, 0.9f);
		volume = eco ? ganho * molhado * 1.1f : ganho * (1f - molhado);
	}

	/** Quanto as paredes entre a boca e o ouvido abafam: o primeiro bloco sólido 0,5, os outros +0,125 (até 0,95). */
	private static float oclusao(Level level, Vec3 de, Vec3 para) {
		float o = 0f;
		BlockPos ultimo = null;
		double len = de.distanceTo(para);
		for (double t = 0.5; t < len - 0.5; t += 0.25) {
			BlockPos p = BlockPos.containing(de.lerp(para, t / len));
			if (p.equals(ultimo)) continue;
			ultimo = p;
			BlockState st = level.getBlockState(p);
			if (st.isAir() || st.getCollisionShape(level, p).isEmpty()) continue;
			float bloco = st.isSolidRender() ? 0.5f : 0.25f; // vidro, folhas, cercas abafam menos
			o += o > 0 ? bloco / 4f : bloco;
			if (o >= 0.95f) return 0.95f;
		}
		return o;
	}
}
