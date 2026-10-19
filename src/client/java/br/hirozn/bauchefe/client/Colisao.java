package br.hirozn.bauchefe.client;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Colisão de verdade entre as partes do corpo e o mundo. Cada parte (cabeça, tronco, braços, pernas) é uma caixa
 * girada do tamanho do cubo dela no modelo, e o mundo são as caixas de colisão dos blocos (as mesmas que seguram
 * o jogador: laje, degrau, cerca, cadeira de mod...). Serve para qualquer mecânica: saber quanto uma parte entrou
 * num bloco, e achar a superfície mais perto numa direção (chão embaixo do pé, parede atrás das costas).
 */
public final class Colisao {
	private Colisao() {}

	/** As caixas de colisão do mundo numa região (blocos). */
	public interface Mundo { void caixas(AABB regiao, List<AABB> out); }

	/** O mundo do quadro atual (o cliente põe a cada quadro; nos testes, uma lista de caixas). null = sem colisão. */
	public static Mundo mundo;

	/** Caixas de colisão dos blocos de verdade. */
	public static Mundo doNivel(Level level) {
		return (r, out) -> {
			BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
			for (int x = Mth.floor(r.minX); x <= Mth.floor(r.maxX); x++)
				for (int y = Mth.floor(r.minY); y <= Mth.floor(r.maxY); y++)
					for (int z = Mth.floor(r.minZ); z <= Mth.floor(r.maxZ); z++) {
						p.set(x, y, z);
						var st = level.getBlockState(p);
						if (st.isAir()) continue;
						for (AABB b : st.getCollisionShape(level, p).toAabbs()) {
							AABB w = b.move(x, y, z);
							if (w.intersects(r)) out.add(w);
						}
					}
		};
	}

	/** Mundo feito de uma lista fixa de caixas (testes). */
	public static Mundo de(List<AABB> lista) {
		return (r, out) -> { for (AABB b : lista) if (b.intersects(r)) out.add(b); };
	}

	/** Caixa girada: centro, três eixos (unitários) e a metade da medida em cada eixo (blocos). */
	public record Caixa(Vec3 c, Vec3[] eixo, double[] meia) {
		public Vec3 canto(int i) {
			return c.add(eixo[0].scale((i & 1) == 0 ? -meia[0] : meia[0]))
					.add(eixo[1].scale((i & 2) == 0 ? -meia[1] : meia[1]))
					.add(eixo[2].scale((i & 4) == 0 ? -meia[2] : meia[2]));
		}

		/** A caixa alinhada que cobre esta. */
		public AABB envolve() {
			double ex = 0, ey = 0, ez = 0;
			for (int k = 0; k < 3; k++) {
				ex += Math.abs(eixo[k].x) * meia[k]; ey += Math.abs(eixo[k].y) * meia[k]; ez += Math.abs(eixo[k].z) * meia[k];
			}
			return new AABB(c.x - ex, c.y - ey, c.z - ez, c.x + ex, c.y + ey, c.z + ez);
		}
	}

	private static final Vec3[] XYZ = {new Vec3(1, 0, 0), new Vec3(0, 1, 0), new Vec3(0, 0, 1)};

	/**
	 * Quanto a caixa girada entrou na caixa do mundo (blocos, 0 = não encosta): a menor sobreposição entre os 15
	 * eixos que separam duas caixas (teorema do eixo separador), que é o quanto ela teria que andar para sair.
	 */
	public static double entra(Caixa o, AABB b) {
		Vec3 cb = b.getCenter();
		double[] mb = {b.getXsize() / 2, b.getYsize() / 2, b.getZsize() / 2};
		Vec3 t = o.c.subtract(cb);
		double menor = Double.MAX_VALUE;
		for (int k = 0; k < 15; k++) {
			Vec3 l = k < 3 ? XYZ[k] : k < 6 ? o.eixo[k - 3] : XYZ[(k - 6) / 3].cross(o.eixo[(k - 6) % 3]);
			double len = l.length();
			if (len < 1e-6) continue; // eixos paralelos: os outros já cobrem
			l = l.scale(1 / len);
			double ra = mb[0] * Math.abs(l.x) + mb[1] * Math.abs(l.y) + mb[2] * Math.abs(l.z);
			double rb = 0;
			for (int i = 0; i < 3; i++) rb += o.meia[i] * Math.abs(o.eixo[i].dot(l));
			double sobra = ra + rb - Math.abs(t.dot(l));
			if (sobra <= 0) return 0;
			menor = Math.min(menor, sobra);
		}
		return menor;
	}

	/** Quanto a caixa girada entrou no mundo, somando todas as caixas (blocos). */
	public static double entra(Caixa o, List<AABB> mundo) {
		AABB env = o.envolve();
		double soma = 0;
		for (AABB b : mundo) if (b.intersects(env)) soma += entra(o, b);
		return soma;
	}

	private static final double[][] DIRS = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};

	/**
	 * Quão fundo o ponto está dentro dos blocos (blocos; 0 = fora): o menor caminho reto para sair, contando os
	 * blocos colados (sair de um bloco de parede para dentro do bloco do lado não é sair). Mais suave que a caixa
	 * inteira, por isso o resolvedor usa pontos espalhados em cada parte do corpo.
	 */
	public static double fundo(List<AABB> mundo, double x, double y, double z) {
		if (dentroDe(mundo, x, y, z) == null) return 0;
		double menor = 2;
		for (double[] d : DIRS) {
			double t = 0;
			for (int passo = 0; passo < 6 && t < menor; passo++) {
				AABB b = dentroDe(mundo, x + d[0] * t, y + d[1] * t, z + d[2] * t);
				if (b == null) break;
				double sai = d[0] > 0 ? b.maxX - x : d[0] < 0 ? x - b.minX : d[1] > 0 ? b.maxY - y : d[1] < 0 ? y - b.minY : d[2] > 0 ? b.maxZ - z : z - b.minZ;
				t = sai + 1e-4;
			}
			menor = Math.min(menor, t);
		}
		return menor;
	}

	private static AABB dentroDe(List<AABB> mundo, double x, double y, double z) {
		for (AABB b : mundo)
			if (x > b.minX && x < b.maxX && y > b.minY && y < b.maxY && z > b.minZ && z < b.maxZ) return b;
		return null;
	}

	/** Onde um raio bate numa superfície: o ponto, a normal da face e a distância. */
	public record Toque(Vec3 ponto, Vec3 normal, double dist) {}

	/**
	 * A primeira superfície a partir de 'de', na direção 'dir' (unitária), até 'max' blocos; null se não tem.
	 * Se 'de' já está dentro de uma caixa, a superfície é a face dela por onde o raio sai voltando (dist negativa).
	 */
	public static Toque raio(List<AABB> mundo, Vec3 de, Vec3 dir, double max) {
		Toque melhor = null;
		for (AABB b : mundo) {
			double t0 = -Double.MAX_VALUE, t1 = Double.MAX_VALUE;
			int eixo0 = -1;
			boolean fora = false;
			for (int k = 0; k < 3 && !fora; k++) {
				double o = k == 0 ? de.x : k == 1 ? de.y : de.z, d = k == 0 ? dir.x : k == 1 ? dir.y : dir.z;
				double mn = k == 0 ? b.minX : k == 1 ? b.minY : b.minZ, mx = k == 0 ? b.maxX : k == 1 ? b.maxY : b.maxZ;
				if (Math.abs(d) < 1e-9) { if (o < mn || o > mx) fora = true; continue; }
				double a = (mn - o) / d, c = (mx - o) / d;
				if (a > c) { double x = a; a = c; c = x; }
				if (a > t0) { t0 = a; eixo0 = k; }
				t1 = Math.min(t1, c);
				if (t0 > t1) fora = true;
			}
			if (fora || t1 < 0 || eixo0 < 0 || t0 > max) continue;
			// dentro da caixa (t0 < 0): a face de entrada fica para trás, a distância é negativa
			if (t0 < -0.5) continue; // fundo demais: é um bloco em que a parte já estava, não uma superfície
			if (melhor != null && t0 >= melhor.dist) continue;
			double s = -Math.signum(eixo0 == 0 ? dir.x : eixo0 == 1 ? dir.y : dir.z);
			Vec3 n = new Vec3(eixo0 == 0 ? s : 0, eixo0 == 1 ? s : 0, eixo0 == 2 ? s : 0);
			melhor = new Toque(de.add(dir.scale(t0)), n, t0);
		}
		return melhor;
	}
}
