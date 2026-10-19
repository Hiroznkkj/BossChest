package br.hirozn.bauchefe.client;

import br.hirozn.bauchefe.AnimPesca;
import br.hirozn.bauchefe.Lugares;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Caminho do corpo numa troca de lugar (subir e descer do barco, sentar e levantar), recalculado a cada quadro
 * com a posição e o giro de verdade do barco (ou do assento): nada é imaginado. Para subir no barco ele anda até
 * a parede lateral mais perto (nunca pela popa, onde fica o baú; se estiver atrás, contorna o canto), passa por
 * cima da borda naquele ponto e escorrega até o lugar de sentar. Descer é o caminho ao contrário. Para sentar,
 * anda até a frente do assento, vira de costas e abaixa (ou dá um pulinho, se o assento for alto).
 * Espaço do barco: x para a esquerda do barco, y para cima, z para a frente (blocos, a partir do centro).
 */
final class Trajeto {
	/** Fases: andando, passando a perna pela borda, acomodando no lugar, levantando, virando de costas para sentar. */
	static final int ANDA = 0, PASSA = 1, ACOMODA = 2, LEVANTA = 3, VIRA = 4;
	/** Lado de fora da parede do barco, metade do comprimento, altura da borda e lugar dele (blocos). */
	static final double LADO = 0.625, PROA = 0.875, BORDA = 0.5625;
	private static final double ANDAR = 0.11, NADAR = 0.07; // blocos por tick

	final int tipo;
	final float inicio;
	final Vec3 de; // de onde o corpo saiu (mundo; parado)
	final int veiculo; // id do barco (subir/descer) ou -1
	final Vec3 assento; // superfície do assento (sentar/levantar)
	final float assentoYaw;
	final int assentoTipo;
	final Vec3 assentoLocal; // onde ele fica sentado no barco (descer)
	final float[] fases; // tipo de cada fase, na ordem
	final float[] duracoes;
	final float passos;

	private Trajeto(int tipo, float inicio, Vec3 de, int veiculo, Vec3 assento, float assentoYaw, int assentoTipo,
	                Vec3 assentoLocal, float[] fases, float[] duracoes, float passos) {
		this.tipo = tipo; this.inicio = inicio; this.de = de; this.veiculo = veiculo; this.assento = assento;
		this.assentoYaw = assentoYaw; this.assentoTipo = assentoTipo; this.assentoLocal = assentoLocal;
		this.fases = fases; this.duracoes = duracoes; this.passos = passos;
	}

	// ---------- montar ----------

	/** Subir no barco 'b' saindo de 'de' (da terra ou da água). */
	static Trajeto subir(boolean agua, float t, Vec3 de, AbstractBoat b, Vec3 lugar, float partial) {
		List<Vec3> poly = aproximacao(local(b, partial, de));
		double len = comprimento(poly);
		float anda = (float) Mth.clamp(len / (agua ? NADAR : ANDAR), 4, 50);
		return new Trajeto(agua ? MarcioCliente.SUBIR_AGUA : MarcioCliente.SUBIR_TERRA, t, de, b.getId(), null, 0, 0, null,
				new float[]{ANDA, PASSA, ACOMODA}, new float[]{anda, agua ? 20 : 14, 12}, (float) Math.max(1, Math.round(len * 2.2)));
	}

	/** Descer do barco 'b' (estava sentado em 'lugarLocal') até onde ele está agora. */
	static Trajeto descer(float t, Vec3 destino, AbstractBoat b, Vec3 lugarLocal, float partial) {
		List<Vec3> poly = aproximacao(local(b, partial, destino));
		double len = comprimento(poly);
		return new Trajeto(MarcioCliente.DESCER, t, destino, b.getId(), null, 0, 0, lugarLocal,
				new float[]{LEVANTA, PASSA, ANDA}, new float[]{12, 14, (float) Mth.clamp(len / ANDAR, 4, 50)}, (float) Math.max(1, Math.round(len * 2.2)));
	}

	/** Sentar no assento (superfície 'a', virado para 'yaw') saindo de 'de'. */
	static Trajeto sentar(float t, Vec3 de, Vec3 a, float yaw, int tipo) {
		Vec3 frente = frente(a, yaw, tipo, de.y);
		double len = Math.sqrt(Math.pow(frente.x - de.x, 2) + Math.pow(frente.z - de.z, 2));
		return new Trajeto(MarcioCliente.SENTAR, t, de, -1, a, yaw, tipo, null, new float[]{ANDA, VIRA, ACOMODA},
				new float[]{(float) Mth.clamp(len / ANDAR, 1, 40), 8, duracaoSentar(tipo)}, (float) Math.max(1, Math.round(len * 2.2)));
	}

	/** Levantar do assento e andar até onde ele está agora. */
	static Trajeto levantar(float t, Vec3 destino, Vec3 a, float yaw, int tipo) {
		Vec3 frente = frente(a, yaw, tipo, destino.y);
		double len = Math.sqrt(Math.pow(frente.x - destino.x, 2) + Math.pow(frente.z - destino.z, 2));
		return new Trajeto(MarcioCliente.LEVANTAR, t, destino, -1, a, yaw, tipo, null, new float[]{LEVANTA, ANDA},
				new float[]{duracaoSentar(tipo), (float) Mth.clamp(len / ANDAR, 1, 40)}, (float) Math.max(1, Math.round(len * 2.2)));
	}

	/** Sentar no chão e deitar levam mais tempo que sentar numa cadeira. */
	private static float duracaoSentar(int tipo) {
		return tipo == Lugares.DEITADO ? 28 : tipo == Lugares.CHAO ? 20 : 14;
	}

	/** Onde ele fica em pé antes de sentar: meio bloco na frente do assento (no chão e no encosto é o próprio lugar). */
	private static Vec3 frente(Vec3 a, float yaw, int tipo, double chao) {
		if (tipo == Lugares.CHAO || tipo == Lugares.ENCOSTO || tipo == Lugares.DEITADO) return a;
		double r = yaw * Mth.DEG_TO_RAD;
		return new Vec3(a.x - Mth.sin((float) r) * 0.55, chao, a.z + Mth.cos((float) r) * 0.55);
	}

	// ---------- andamento ----------

	float duracao() { float s = 0; for (float d : duracoes) s += d; return s; }

	/** Fase atual e quanto dela já foi, em t ticks desde o começo: {fase, v}. */
	float[] fase(float t) {
		float e = t - inicio;
		for (int i = 0; i < duracoes.length; i++) {
			if (e < duracoes[i] || i == duracoes.length - 1) return new float[]{fases[i], Mth.clamp(e / duracoes[i], 0f, 1f), i};
			e -= duracoes[i];
		}
		return new float[]{fases[fases.length - 1], 1f, fases.length - 1};
	}

	/** De pé (1) ou sentado (0) neste momento. */
	float pe(float t) {
		float[] f = fase(t);
		boolean encosto = assentoTipo == Lugares.ENCOSTO;
		return switch ((int) f[0]) {
			case ACOMODA -> encosto ? 1f : 1f - AnimPesca.inOutSine(f[1]);
			case LEVANTA -> encosto ? 1f : AnimPesca.inOutSine(f[1]);
			default -> 1f;
		};
	}

	/**
	 * Onde o corpo está (mundo) em t. 'agora' é a posição de verdade dele (no barco, ou já no destino ao descer);
	 * 'b' é o barco (null para sentar). Devolve null se o barco sumiu (aí vira um pulo comum).
	 */
	Vec3 ponto(float t, Vec3 agora, AbstractBoat b, float partial) {
		float[] f = fase(t);
		int fase = (int) f[0];
		float v = f[1];
		if (tipo == MarcioCliente.SENTAR || tipo == MarcioCliente.LEVANTAR) return pontoAssento(fase, v, agora);
		if (b == null) return null;
		Vec3 lugar = tipo == MarcioCliente.DESCER ? assentoLocal : local(b, partial, agora);
		Vec3 longe = local(b, partial, tipo == MarcioCliente.DESCER ? agora : de); // subir: de onde saiu; descer: o destino
		return mundo(b, partial, noBarco(tipo, fase, v, longe, lugar));
	}

	/** O caminho no espaço do barco: 'longe' é o ponto fora (de onde sai ou para onde vai), 'lugar' onde senta. */
	static Vec3 noBarco(int tipo, int fase, float v, Vec3 longe, Vec3 lugar) {
		int lado = longe.x >= 0 ? 1 : -1;
		double ez = Mth.clamp(longe.z, -0.1, 0.55);
		Vec3 fora = new Vec3(lado * (LADO + 0.33), longe.y, ez), dentro = new Vec3(lado * 0.3, lugar.y, ez);
		Vec3 l;
		if (tipo == MarcioCliente.DESCER) {
			l = switch (fase) {
				case LEVANTA -> lugar.lerp(dentro, AnimPesca.inOutSine(Mth.clamp((v - 0.5f) * 2f, 0f, 1f)) * 0.6);
				case PASSA -> passar(lugar.lerp(dentro, 0.6), fora, v);
				default -> aoLongo(aproximacao(longe), 1f - AnimPesca.inOutSine(v), true);
			};
		} else {
			l = switch (fase) {
				case ANDA -> aoLongo(aproximacao(longe), AnimPesca.inOutSine(v), false);
				case PASSA -> passar(fora, dentro, v);
				default -> dentro.lerp(lugar, AnimPesca.inOutSine(v));
			};
		}
		return l;
	}

	/** Passa por cima da borda: anda de 'a' até 'b' subindo o bastante para os pés passarem acima dela. */
	private static Vec3 passar(Vec3 a, Vec3 b, float v) {
		float e = AnimPesca.inOutSine(v);
		double meio = (a.y + b.y) / 2, arco = Math.max(0.06, BORDA + 0.08 - meio);
		return new Vec3(Mth.lerp(e, a.x, b.x), Mth.lerp(e, a.y, b.y) + arco * Mth.sin(Mth.PI * v), Mth.lerp(e, a.z, b.z));
	}

	private Vec3 pontoAssento(int fase, float v, Vec3 agora) {
		boolean sentar = tipo == MarcioCliente.SENTAR;
		Vec3 longe = sentar ? de : agora; // em pé, longe do assento
		Vec3 frente = frente(assento, assentoYaw, assentoTipo, longe.y);
		return switch (fase) {
			case ANDA -> sentar ? de.lerp(frente, AnimPesca.inOutSine(v)) : frente.lerp(agora, AnimPesca.inOutSine(v));
			case VIRA -> frente;
			default -> { // acomoda (sentar) ou levanta: da frente até o assento; se for alto, num pulinho
				float e = AnimPesca.inOutSine(sentar ? v : 1f - v);
				double sobe = assento.y - frente.y, pulo = sobe > 0.6 ? 0.22 * Mth.sin(Mth.PI * e) : 0;
				// abaixar até o assento é a pose (pe); o corpo só desce junto quando o assento está abaixo dos pés
				double y = Mth.lerp(e, frente.y, sobe > 0 ? assento.y : frente.y) + pulo;
				yield new Vec3(Mth.lerp(e, frente.x, assento.x), y, Mth.lerp(e, frente.z, assento.z));
			}
		};
	}

	/** Para onde o corpo está virado agora (graus, como o yaw), ou NaN para usar o giro de verdade dele. */
	float rumo(float t, Vec3 agora, AbstractBoat b, float partial) {
		float[] f = fase(t);
		int fase = (int) f[0];
		if (tipo == MarcioCliente.SENTAR || tipo == MarcioCliente.LEVANTAR) {
			if (fase == ANDA && passos > 0) {
				Vec3 a = ponto(t, agora, b, partial), c = ponto(t + 1, agora, b, partial);
				if (a != null && c != null && c.distanceToSqr(a) > 1e-5) return yaw(c.subtract(a));
			}
			return assentoYaw;
		}
		if (b == null) return Float.NaN;
		float barco = Mth.rotLerp(partial, b.yRotO, b.getYRot());
		if (fase == ACOMODA || fase == LEVANTA) return barco;
		Vec3 a = ponto(t, agora, b, partial), c = ponto(t + 1, agora, b, partial);
		if (fase == PASSA) { // de frente para a borda (entrando) ou de costas para o barco (saindo)
			Vec3 l = local(b, partial, tipo == MarcioCliente.DESCER ? agora : de);
			float lado = l.x >= 0 ? 1 : -1;
			double r = barco * Mth.DEG_TO_RAD;
			Vec3 fora = new Vec3(Math.cos(r) * lado, 0, Math.sin(r) * lado); // esquerda do barco = (cos, 0, sen)
			return yaw(tipo == MarcioCliente.DESCER ? fora : fora.scale(-1));
		}
		if (a == null || c == null || c.distanceToSqr(a) < 1e-5) return Float.NaN;
		return yaw(c.subtract(a));
	}

	private static float yaw(Vec3 d) { return (float) (Mth.atan2(-d.x, d.z) * Mth.RAD_TO_DEG); }

	// ---------- geometria do barco ----------

	static Vec3 local(AbstractBoat b, float partial, Vec3 w) {
		Vec3 o = b.getPosition(partial);
		double r = Mth.rotLerp(partial, b.yRotO, b.getYRot()) * Mth.DEG_TO_RAD;
		double fx = -Math.sin(r), fz = Math.cos(r), lx = Math.cos(r), lz = Math.sin(r);
		double dx = w.x - o.x, dz = w.z - o.z;
		return new Vec3(dx * lx + dz * lz, w.y - o.y, dx * fx + dz * fz);
	}

	static Vec3 mundo(AbstractBoat b, float partial, Vec3 l) {
		Vec3 o = b.getPosition(partial);
		double r = Mth.rotLerp(partial, b.yRotO, b.getYRot()) * Mth.DEG_TO_RAD;
		double fx = -Math.sin(r), fz = Math.cos(r), lx = Math.cos(r), lz = Math.sin(r);
		return new Vec3(o.x + l.x * lx + l.z * fx, o.y + l.y, o.z + l.x * lz + l.z * fz);
	}

	/**
	 * Caminho por fora do barco, de 'longe' (espaço do barco) até o ponto da parede lateral mais perto,
	 * contornando o canto se a linha reta passar por dentro do casco.
	 */
	private static List<Vec3> aproximacao(Vec3 longe) {
		int lado = longe.x >= 0 ? 1 : -1;
		double ez = Mth.clamp(longe.z, -0.1, 0.55);
		Vec3 fora = new Vec3(lado * (LADO + 0.33), longe.y, ez);
		List<Vec3> p = new ArrayList<>();
		p.add(longe);
		boolean corta = false;
		for (int i = 1; i < 10; i++) {
			Vec3 q = longe.lerp(fora, i / 10.0);
			if (Math.abs(q.x) < LADO + 0.15 && Math.abs(q.z) < PROA + 0.2) { corta = true; break; }
		}
		if (corta) p.add(new Vec3(lado * (LADO + 0.33), longe.y, Math.signum(longe.z) * (PROA + 0.3)));
		p.add(fora);
		return p;
	}

	private static double comprimento(List<Vec3> p) {
		double s = 0;
		for (int i = 1; i < p.size(); i++) s += Math.sqrt(Math.pow(p.get(i).x - p.get(i - 1).x, 2) + Math.pow(p.get(i).z - p.get(i - 1).z, 2));
		return s;
	}

	/** Ponto na fração 's' do comprimento do caminho (de trás para a frente se 'inverso'). */
	private static Vec3 aoLongo(List<Vec3> p, float s, boolean inverso) {
		if (inverso) { List<Vec3> r = new ArrayList<>(p); java.util.Collections.reverse(r); p = r; s = 1f - s; }
		double alvo = comprimento(p) * Mth.clamp(s, 0f, 1f);
		for (int i = 1; i < p.size(); i++) {
			Vec3 a = p.get(i - 1), c = p.get(i);
			double seg = Math.sqrt(Math.pow(c.x - a.x, 2) + Math.pow(c.z - a.z, 2));
			if (alvo <= seg || i == p.size() - 1) return seg < 1e-6 ? c : a.lerp(c, Math.min(1, alvo / seg));
			alvo -= seg;
		}
		return p.get(p.size() - 1);
	}

	/** Lado de fora da borda (para a mão): ponto da parede onde ele passa, na altura da borda. */
	Vec3 bordaDaPassagem(Vec3 agora, AbstractBoat b, float partial) {
		Vec3 longe = local(b, partial, tipo == MarcioCliente.DESCER ? agora : de);
		int lado = longe.x >= 0 ? 1 : -1;
		return mundo(b, partial, new Vec3(lado * (LADO - 0.05), BORDA, Mth.clamp(longe.z, -0.1, 0.55) + 0.15));
	}
}
