package br.hirozn.bauchefe;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Rede de baús em volta de um Baú Chefe. Montada na hora de cada ação (sem cache, sem estado escondido). */
public final class Rede {
	public static final int RAIO = 16;

	/** Um baú da rede. rotulo = item da moldura (vazio = baú "geral"). */
	public record Membro(Container c, BlockPos pos, ItemStack rotulo, Categoria cat) {
		boolean geral() { return rotulo.isEmpty(); }
	}

	/** Item agregado: protótipo (count 1) + total. */
	public record Total(ItemStack item, long qtd) {}

	public final ServerLevel level;
	public final BauChefeBlockEntity chefe;
	public final List<Membro> membros = new ArrayList<>();

	private Rede(ServerLevel level, BauChefeBlockEntity chefe) {
		this.level = level;
		this.chefe = chefe;
	}

	public static Rede montar(ServerLevel level, BauChefeBlockEntity chefe) {
		Rede r = new Rede(level, chefe);
		BlockPos c = chefe.getBlockPos();
		// molduras: bloco onde estão penduradas -> item
		Map<BlockPos, ItemStack> rotulos = new HashMap<>();
		for (ItemFrame f : level.getEntitiesOfClass(ItemFrame.class, new AABB(c).inflate(RAIO + 1))) {
			ItemStack it = f.getItem();
			if (it.isEmpty()) continue;
			rotulos.put(f.blockPosition().relative(f.getDirection().getOpposite()), it.copyWithCount(1));
		}
		int r2 = RAIO * RAIO;
		ChunkPos cp = ChunkPos.containing(c);
		int cr = (RAIO >> 4) + 1;
		for (int cx = cp.x() - cr; cx <= cp.x() + cr; cx++) {
			for (int cz = cp.z() - cr; cz <= cp.z() + cr; cz++) {
				LevelChunk ch = level.getChunkSource().getChunkNow(cx, cz);
				if (ch == null) continue;
				for (BlockEntity be : ch.getBlockEntities().values()) {
					if (!(be instanceof ChestBlockEntity || be instanceof BarrelBlockEntity)) continue;
					BlockPos p = be.getBlockPos();
					if (p.distSqr(c) > r2) continue;
					ItemStack rot = rotulos.getOrDefault(p, ItemStack.EMPTY);
					if (rot.isEmpty() && be instanceof ChestBlockEntity) {
						// baú duplo: a moldura numa metade vale para as duas
						BlockState s = be.getBlockState();
						if (s.hasProperty(ChestBlock.TYPE) && s.getValue(ChestBlock.TYPE) != ChestType.SINGLE)
							rot = rotulos.getOrDefault(p.relative(ChestBlock.getConnectedDirection(s)), ItemStack.EMPTY);
					}
					r.membros.add(new Membro((Container) be, p, rot, rot.isEmpty() ? null : Categoria.de(rot)));
				}
			}
		}
		r.membros.sort(java.util.Comparator.comparingDouble(m -> m.pos().distSqr(c)));
		return r;
	}

	// ---------- guardar ----------

	/**
	 * Guarda seguindo a ordem: moldura do item exato > moldura da mesma categoria >
	 * baú sem moldura mais próximo que já tenha esse item. Sem destino, o item não sai do lugar.
	 * Devolve o que sobrou.
	 */
	public ItemStack guardar(ItemStack stack) {
		if (stack.isEmpty()) return stack;
		Categoria cat = Categoria.de(stack);
		for (Membro m : membros)
			if (!m.geral() && ItemStack.isSameItemSameComponents(m.rotulo(), stack)) { stack = inserir(m.c(), stack); if (stack.isEmpty()) return stack; }
		if (cat != Categoria.DIVERSOS)
			for (Membro m : membros)
				if (!m.geral() && m.cat() == cat) { stack = inserir(m.c(), stack); if (stack.isEmpty()) return stack; }
		for (Membro m : membros) // já ordenados do mais perto para o mais longe
			if (m.geral() && contem(m.c(), stack)) { stack = inserir(m.c(), stack); if (stack.isEmpty()) return stack; }
		return stack;
	}

	static boolean contem(Container c, ItemStack proto) {
		for (int i = 0; i < c.getContainerSize(); i++)
			if (ItemStack.isSameItemSameComponents(c.getItem(i), proto)) return true;
		return false;
	}

	static ItemStack inserir(Container c, ItemStack stack) {
		boolean mudou = false;
		int max = Math.min(stack.getMaxStackSize(), c.getMaxStackSize());
		for (int i = 0; i < c.getContainerSize() && !stack.isEmpty(); i++) { // junta com pilhas iguais
			ItemStack s = c.getItem(i);
			if (!s.isEmpty() && ItemStack.isSameItemSameComponents(s, stack) && s.getCount() < max) {
				int n = Math.min(max - s.getCount(), stack.getCount());
				s.grow(n); stack.shrink(n); mudou = true;
			}
		}
		for (int i = 0; i < c.getContainerSize() && !stack.isEmpty(); i++) { // depois espaços vazios
			if (c.getItem(i).isEmpty() && c.canPlaceItem(i, stack)) {
				int n = Math.min(max, stack.getCount());
				c.setItem(i, stack.split(n)); mudou = true;
			}
		}
		if (mudou) c.setChanged();
		return stack;
	}

	/** Esvazia o Baú Chefe para a rede. Devolve os nomes dos itens que ficaram sem destino. */
	public java.util.Set<String> distribuir() {
		Container cc = chefe;
		boolean mudou = false;
		java.util.Set<String> semLugar = new java.util.LinkedHashSet<>();
		for (int i = 0; i < cc.getContainerSize(); i++) {
			ItemStack s = cc.getItem(i);
			if (s.isEmpty()) continue;
			int antes = s.getCount();
			ItemStack resto = guardar(s.copy());
			if (resto.getCount() != antes) { cc.setItem(i, resto); mudou = true; }
			if (!resto.isEmpty()) semLugar.add(resto.getHoverName().getString());
		}
		if (mudou) cc.setChanged();
		return semLugar;
	}

	/** Guarda o inventário do jogador (menos a barra de itens, armadura e mão secundária). Devolve o que ficou. */
	public java.util.Set<String> guardarInventario(ServerPlayer p) {
		Inventory inv = p.getInventory();
		java.util.Set<String> semLugar = new java.util.LinkedHashSet<>();
		for (int i = 9; i < 36; i++) {
			ItemStack s = inv.getItem(i);
			if (s.isEmpty()) continue;
			ItemStack resto = guardar(s.copy());
			inv.setItem(i, resto);
			if (!resto.isEmpty()) semLugar.add(resto.getHoverName().getString());
		}
		inv.setChanged();
		return semLugar;
	}

	/** Texto curto de erro: "Sem lugar para: Terra, Pedra (+2)". */
	public static String erro(java.util.Set<String> nomes) {
		java.util.List<String> l = new ArrayList<>(nomes);
		String base = String.join(", ", l.subList(0, Math.min(3, l.size())));
		return "Sem lugar para: " + base + (l.size() > 3 ? " (+" + (l.size() - 3) + ")" : "");
	}

	// ---------- listar / sacar ----------

	private List<Container> todos() {
		List<Container> l = new ArrayList<>();
		l.add(chefe);
		for (Membro m : membros) l.add(m.c());
		return l;
	}

	public List<Total> totais() {
		List<Total> out = new ArrayList<>();
		Map<Integer, List<Integer>> idx = new HashMap<>();
		for (Container c : todos()) {
			for (int i = 0; i < c.getContainerSize(); i++) {
				ItemStack s = c.getItem(i);
				if (s.isEmpty()) continue;
				int h = ItemStack.hashItemAndComponents(s);
				List<Integer> cand = idx.computeIfAbsent(h, k -> new ArrayList<>());
				boolean achou = false;
				for (int j : cand) {
					Total t = out.get(j);
					if (ItemStack.isSameItemSameComponents(t.item(), s)) { out.set(j, new Total(t.item(), t.qtd() + s.getCount())); achou = true; break; }
				}
				if (!achou) { cand.add(out.size()); out.add(new Total(s.copyWithCount(1), s.getCount())); }
			}
		}
		return out;
	}

	/** Quanto desse item ainda cabe no inventário principal do jogador. */
	static int cabe(Inventory inv, ItemStack proto) {
		int max = proto.getMaxStackSize(), n = 0;
		for (int i = 0; i < 36; i++) {
			ItemStack s = inv.getItem(i);
			if (s.isEmpty()) n += max;
			else if (ItemStack.isSameItemSameComponents(s, proto)) n += Math.max(0, max - s.getCount());
		}
		return n;
	}

	/** Saca até 'qtd' do item, nunca mais do que cabe no inventário (nada cai no chão). Devolve quanto sacou. */
	public int sacar(ServerPlayer p, ItemStack proto, int qtd) {
		Inventory inv = p.getInventory();
		int alvo = Math.min(qtd, cabe(inv, proto));
		int falta = alvo;
		for (Container c : todos()) {
			boolean mudou = false;
			for (int i = 0; i < c.getContainerSize() && falta > 0; i++) {
				ItemStack s = c.getItem(i);
				if (s.isEmpty() || !ItemStack.isSameItemSameComponents(s, proto)) continue;
				int n = Math.min(falta, s.getCount());
				ItemStack tirado = s.split(n);
				falta -= n; mudou = true;
				inv.add(tirado);
				if (!tirado.isEmpty()) { s.grow(tirado.getCount()); falta += tirado.getCount(); } // não coube: devolve ao baú
			}
			if (mudou) c.setChanged();
			if (falta <= 0) break;
		}
		inv.setChanged();
		return alvo - falta;
	}
}
