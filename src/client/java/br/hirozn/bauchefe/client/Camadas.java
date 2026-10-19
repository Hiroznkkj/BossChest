package br.hirozn.bauchefe.client;

import br.hirozn.bauchefe.AnimPesca;
import br.hirozn.bauchefe.client.Corpo.Lado;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Camadas procedurais: cada uma diz quanto pesa agora (0 = desligada) e mexe no corpo com a API do Corpo.
 * Rodam em ordem, por cima da pose da ação (pesca, sentar, andar), então se misturam com o que estiver
 * acontecendo: um susto no meio de sentar, uma pancada lendo o livro. As de baixo da lista ganham das de cima
 * quando mexem na mesma parte. Para criar uma nova, ver PROCEDURAL.md.
 */
public final class Camadas {
	/** Uma reação ou gesto procedural. */
	public interface Camada {
		/** Quanto pesa agora (0..1); 0 desliga. */
		float peso(DadosMarcio d);

		/** Mexe no corpo; 'w' é o peso, 'age' o tempo em ticks (para oscilações). */
		void aplicar(Corpo c, float w, float age);
	}

	private static final List<Camada> LISTA = new ArrayList<>();

	private Camadas() {}

	/** Acrescenta uma camada (vai para o fim: ganha das que já existem). */
	public static void registrar(Camada c) { LISTA.add(c); }

	static void aplicar(Corpo c, float age) {
		for (Camada camada : LISTA) {
			float w = camada.peso(c.d);
			if (w > 0) camada.aplicar(c, Math.min(w, 1f), age);
		}
	}

	// ---------- as camadas ----------

	/** Chuva: mão esquerda na cabeça, cabeça um pouco baixa (em terra, sem teto em cima). */
	public static final Camada CHUVA = new Camada() {
		public float peso(DadosMarcio d) { return d.chuva && !d.livro ? 1f : 0f; }
		public void aplicar(Corpo c, float w, float age) {
			c.maoNoCorpo(Lado.ESQUERDO, 2.5f, -9.5f, -1f, w);
			c.cabeca.xRot += 0.15f * w;
		}
	};

	/** Medindo o quarto: olha para os pés e conta nos dedos a cada passo. */
	public static final Camada MEDIR = new Camada() {
		public float peso(DadosMarcio d) { return d.medindo ? 1f : 0f; }
		public void aplicar(Corpo c, float w, float age) {
			c.cabeca.xRot = Mth.lerp(w, c.cabeca.xRot, 0.6f);
			float conta = Math.abs(Mth.sin(age * 0.35f)); // um dedo a cada passo
			c.maoNoCorpo(Lado.ESQUERDO, 3f, 4f - 1.5f * conta, -8f, w);
		}
	};

	/** Esquentando as mãos na fogueira: as duas mãos esticadas para o fogo, esfregando de leve. */
	public static final Camada AQUECER = new Camada() {
		public float peso(DadosMarcio d) { return d.aquecer != null && !d.livro ? 1f : 0f; }
		public void aplicar(Corpo c, float w, float age) {
			Vec3 fogo = c.d.aquecer.add(0, 0.6, 0);
			Vec3 ombros = new Vec3(c.d.mx, c.d.my + (c.d.sentado ? 0.85 : 1.35), c.d.mz);
			Vec3 maos = ombros.lerp(fogo, 0.35); // não encosta no fogo
			float esfrega = 0.05f * Mth.sin(age * 0.5f);
			c.mao(Lado.DIREITO, maos.add(esfrega, 0, 0), w);
			c.mao(Lado.ESQUERDO, maos.add(-esfrega, 0.03, 0), w);
			c.olhar(fogo, w * 0.7f);
		}
	};

	/** Lendo: livro na frente do peito com as duas mãos, olhando para ele e virando a página; tira do bolso e guarda. */
	public static final Camada LIVRO = new Camada() {
		public float peso(DadosMarcio d) { return d.livro || d.livroT < 20 || d.guardaT < 18 ? 1f : 0f; }
		public void aplicar(Corpo c, float w, float age) {
			DadosMarcio d = c.d;
			float bx = -5.5f, by = 11.5f, bz = 0.5f, lx = 0f, ly = 8.5f, lz = -7.5f; // bolso e onde segura o livro
			if (d.livroT < 20 && !d.livro) { c.maoNoCorpo(Lado.DIREITO, bx, by, bz, Corpo.envelope(d.livroT, 5, 12, 20)); return; }
			if (d.guardaT < 18) {
				float e = AnimPesca.inOutSine(d.guardaT / 10f);
				c.maoNoCorpo(Lado.DIREITO, Mth.lerp(e, lx, bx), Mth.lerp(e, ly, by), Mth.lerp(e, lz, bz), Corpo.envelope(d.guardaT, 2, 10, 18));
				return;
			}
			float p = d.livroT < 20 ? AnimPesca.inOutSine((d.livroT - 8f) / 10f) : 1f;
			c.maoNoCorpo(Lado.DIREITO, lx - 1f, ly + 0.5f, lz, p);
			float pagina = (age % 90f) < 12f ? Mth.sin(Mth.PI * (age % 90f) / 12f) : 0f;
			c.maoNoCorpo(Lado.ESQUERDO, lx + 1.5f + 2.5f * pagina, ly - 1.5f * pagina, lz - pagina, p);
			c.olharNoCorpo(lx, ly, lz, p * 0.85f);
		}
	};

	/** Viu a armadura nova de alguém: olha dos pés à cabeça, se inclina para trás impressionado e faz joinha. */
	public static final Camada ARMADURA = new Camada() {
		public float peso(DadosMarcio d) { return d.armaduraPes == null ? 0f : Corpo.envelope(d.armaduraT, 6, 55, 70); }
		public void aplicar(Corpo c, float w, float age) {
			DadosMarcio d = c.d;
			float sobe = AnimPesca.inOutSine((d.armaduraT - 4f) / 30f); // passa os olhos de baixo para cima
			c.olhar(d.armaduraPes.lerp(d.armaduraCabeca, sobe), w);
			c.inclinar(-0.08f * sobe, 0f, w);
			float joinha = Corpo.envelope(d.armaduraT - 34f, 5, 25, 34);
			c.maoNoCorpo(Lado.ESQUERDO, 3f, 2f, -8f, joinha * w);
		}
	};

	/** Conversando com alguém: vira a cabeça para a pessoa (os gestos vêm do jeito de falar). */
	public static final Camada CONVERSA = new Camada() {
		public float peso(DadosMarcio d) { return d.conversaCom != null ? 1f : 0f; }
		public void aplicar(Corpo c, float w, float age) { c.olhar(c.d.conversaCom, w * 0.9f); }
	};

	/** "Toca aqui": mão aberta acima da cabeça na direção de quem ele cumprimentou; quando batem, vai ao encontro e volta. */
	public static final Camada HI_FIVE = new Camada() {
		public float peso(DadosMarcio d) {
			return d.maoDe == null ? 0f : Math.max(Corpo.envelope(d.ofereceT, 6, 92, 100), Corpo.envelope(d.bateT, 2, 5, 16));
		}
		public void aplicar(Corpo c, float w, float age) {
			DadosMarcio d = c.d;
			Vec3 olhos = new Vec3(d.mx, d.my + 1.62, d.mz), ate = d.maoDe.subtract(olhos);
			Vec3 lado = new Vec3(ate.x, 0, ate.z);
			lado = lado.lengthSqr() < 1e-4 ? new Vec3(0, 0, 1) : lado.normalize();
			Vec3 oferta = olhos.add(lado.scale(0.42)).add(0, 0.32, 0);
			Vec3 encontro = oferta.lerp(d.maoDe.add(0, 0.2, 0), 0.35);
			float e = d.bateT < 3 ? AnimPesca.inOutSine(d.bateT / 3f) : d.bateT < 16 ? 1f - AnimPesca.inOutSine((d.bateT - 3f) / 13f) : 0f;
			boolean bateu = d.bateT < 16;
			c.alcancar(Lado.ESQUERDO, bateu ? oferta.lerp(encontro, e) : oferta, w, 0.15f);
			c.olhar(d.maoDe, w * 0.8f);
			if (bateu) c.tronco.xRot -= 0.12f * e * w; // o tranco da batida
		}
	};

	/** Calor de fogo perto: braço na frente do rosto do lado do fogo, cabeça virando e corpo recuando. */
	public static final Camada CALOR = new Camada() {
		public float peso(DadosMarcio d) { return d.calor == null ? 0f : Corpo.envelope(d.calorT, 4, 50, 70); }
		public void aplicar(Corpo c, float w, float age) {
			Vec3 fogo = c.d.calor;
			c.recuarDe(fogo, 0.18f, w);
			float[] f = c.direcao(fogo);
			c.maoNoCorpo(f[0] > 0 ? Lado.ESQUERDO : Lado.DIREITO, f[0] * 4f, -4f, -3f + f[1] * 3f, w);
			c.cabeca.yRot -= 0.35f * f[0] * w; // vira o rosto para o outro lado
		}
	};

	/** Quase caindo da beirada: braços girando, corpo jogado para trás, olhando para o buraco. */
	public static final Camada BEIRADA = new Camada() {
		public float peso(DadosMarcio d) { return d.beirada; }
		public void aplicar(Corpo c, float w, float age) {
			float g = age * 0.9f;
			misturar(c.bracoD, -1.6f + 1.3f * Mth.sin(g), 0f, 0.9f + 0.3f * Mth.cos(g), w);
			misturar(c.bracoE, -1.6f + 1.3f * Mth.sin(g + Mth.PI), 0f, -0.9f - 0.3f * Mth.cos(g + Mth.PI), w);
			c.inclinar(-0.16f + 0.07f * Mth.sin(age * 0.7f), 0.04f * Mth.sin(age * 0.5f), w);
			c.cabeca.xRot += 0.4f * w;
		}
	};

	/** Pegando fogo: bate nas roupas com as duas mãos, alternando, e o corpo se debate. */
	public static final Camada PEGANDO_FOGO = new Camada() {
		public float peso(DadosMarcio d) { return d.emChamas ? 1f : 0f; }
		public void aplicar(Corpo c, float w, float age) {
			float a = Mth.sin(age * 1.4f), b = Mth.sin(age * 1.4f + 1.7f);
			c.maoNoCorpo(Lado.DIREITO, -1f, 4f + 4f * a, -3f, w);
			c.maoNoCorpo(Lado.ESQUERDO, 1f, 6f + 4f * b, -3f, w);
			c.inclinar(0.06f * a, 0.05f * b, w);
			c.cabeca.yRot += 0.3f * Mth.sin(age * 0.9f) * w;
		}
	};

	/** Susto: pulinho, recua para longe da coisa, mãos protegendo o rosto daquele lado, olha para ela e treme. */
	public static final Camada SUSTO = new Camada() {
		public float peso(DadosMarcio d) { return d.susto == null ? 0f : Corpo.envelope(d.sustoT, 3, 14, 50); }
		public void aplicar(Corpo c, float w, float age) {
			DadosMarcio d = c.d;
			float[] f = c.direcao(d.susto);
			float pulo = d.sustoT < 7 ? Mth.sin(Mth.PI * d.sustoT / 7f) : 0f;
			c.root.y -= 1.6f * pulo;
			c.recuarDe(d.susto, 0.2f, w);
			if (d.sustoT > 10) c.root.x += 0.2f * Mth.sin(age * 2.6f) * w; // tremendo
			c.maoNoCorpo(Lado.DIREITO, f[0] * 3.5f - 2.5f, -4f, -3f + f[1] * 3.5f, w * 0.9f);
			c.maoNoCorpo(Lado.ESQUERDO, f[0] * 3.5f + 2.5f, -4f, -3f + f[1] * 3.5f, w * 0.9f);
			c.olhar(d.susto, w);
		}
	};

	/** Pancada: recua para o lado contrário de onde veio, encolhe a cabeça e abre os braços, em meio segundo. */
	public static final Camada DOR = new Camada() {
		public float peso(DadosMarcio d) { return Corpo.envelope(d.dorT, 2, 3, 10); }
		public void aplicar(Corpo c, float w, float age) {
			if (c.d.dorDe != null) c.recuarDe(c.d.dorDe, 0.24f, w);
			else c.inclinar(-0.24f, 0f, w); // sem saber de onde veio: para trás
			c.cabeca.xRot += 0.25f * w;
			c.bracoD.zRot += 0.3f * w;
			c.bracoE.zRot -= 0.3f * w;
		}
	};

	private static void misturar(net.minecraft.client.model.geom.ModelPart p, float x, float y, float z, float w) {
		p.xRot = Mth.lerp(w, p.xRot, x);
		p.yRot = Mth.lerp(w, p.yRot, y);
		p.zRot = Mth.lerp(w, p.zRot, z);
	}

	static {
		for (Camada c : new Camada[]{CHUVA, MEDIR, AQUECER, LIVRO, ARMADURA, CONVERSA, HI_FIVE, CALOR, BEIRADA, PEGANDO_FOGO, SUSTO, DOR})
			registrar(c);
	}
}
