package br.hirozn.bauchefe.client;

import br.hirozn.bauchefe.BauChefe;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * Voz do Márcio presa na boca dele. Toca junto duas versões do mesmo áudio: a normal e uma abafada
 * com eco. Quanto mais longe, mais baixo e mais eco (o volume é recalculado a cada tick).
 */
public class MarcioSom extends AbstractTickableSoundInstance {
	/** Até onde dá para ouvir (blocos). */
	private static final float ALCANCE = 40f;
	private final Entity quem;
	private final boolean eco;

	public MarcioSom(Entity quem, String som, boolean eco) {
		super(SoundEvent.createVariableRangeEvent(BauChefe.id("marcio." + som + (eco ? "_eco" : ""))), SoundSource.VOICE, RandomSource.create());
		this.quem = quem;
		this.eco = eco;
		this.attenuation = SoundInstance.Attenuation.NONE; // a distância é calculada aqui
		this.looping = false;
		seguir();
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
		seguir();
	}

	private void seguir() {
		Vec3 boca = quem.getEyePosition();
		x = boca.x; y = boca.y; z = boca.z;
		var cam = Minecraft.getInstance().player;
		float d = cam == null ? 0f : (float) cam.getEyePosition().distanceTo(boca);
		float perto = Mth.clamp(1f - d / ALCANCE, 0f, 1f);
		float alto = perto * (float) Math.sqrt(perto); // cai mais rápido no começo, como a vanilla
		float molhado = Mth.clamp((d - 4f) / 24f, 0f, 0.85f); // proporção de eco: 0 colado, 85% longe
		volume = eco ? alto * molhado * 1.1f : alto * (1f - molhado);
	}
}
