package br.hirozn.bauchefe.client;

import br.hirozn.bauchefe.Categoria;
import br.hirozn.bauchefe.net.Pacotes;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import com.mojang.blaze3d.platform.InputConstants;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class BauChefeScreen extends Screen {
	// paleta
	private static final int OURO = 0xFFD9AE3A, OURO_ESC = 0xFF5E4814, FUNDO_A = 0xFA121010, FUNDO_B = 0xFA080707,
			CELULA = 0xFF161413, CELULA_HOVER = 0xFF3A3020, TEXTO = 0xFFE8DFD0, TEXTO_FRACO = 0xFF8C8478,
			BOTAO = 0xFF1C1915, BOTAO_HOVER = 0xFF2E2719, CAMPO = 0xFF0D0C0B;
	private static final int W = 330, COLS = 17, CEL = 18;
	private int ROWS = 8, H = 252; // ajustados ao tamanho da janela em init()

	private enum Ordem { NOME("Nome"), QTD("Quantidade"), TIPO("Tipo");
		final String rotulo; Ordem(String r) { rotulo = r; } }

	private List<Pacotes.Linha> todas = new ArrayList<>();
	private final List<Pacotes.Linha> visiveis = new ArrayList<>();
	private int baus;
	private String aviso = "";
	private long avisoAte;

	private Categoria filtro = null; // null = Tudo
	private Ordem ordem = Ordem.QTD;
	private boolean desc = true;
	private int scroll;

	private int x0, y0;
	private EditBox busca;
	private double abriuX, abriuY, abriuZ; // fecha sozinho ao se afastar

	/** Botão escuro desenhado à mão. */
	private record Bt(int x, int y, int w, int h, java.util.function.Supplier<String> rotulo, Runnable acao) {
		boolean dentro(double mx, double my) { return mx >= x && my >= y && mx < x + w && my < y + h; }
	}
	private final List<Bt> botoes = new ArrayList<>();
	private final List<Bt> botoesQtd = new ArrayList<>();

	// caixa de quantidade (Ctrl+clique)
	private Pacotes.Linha pedindo;
	private EditBox qtdBox;

	public BauChefeScreen(Pacotes.Estoque e) {
		super(Component.literal("Baú Chefe"));
		atualizar(e);
	}

	public void atualizar(Pacotes.Estoque e) {
		todas = new ArrayList<>(e.linhas());
		baus = e.baus();
		if (!e.aviso().isEmpty()) { aviso = e.aviso(); avisoAte = System.currentTimeMillis() + 3000; }
		refiltrar();
	}

	@Override
	protected void init() {
		ROWS = Math.max(3, Math.min(8, (height - 6 - 108) / CEL));
		H = 108 + ROWS * CEL;
		x0 = (width - W) / 2;
		y0 = (height - H) / 2;
		String q = busca == null ? "" : busca.getValue();
		busca = new EditBox(font, x0 + 12, y0 + 26, 166, 12, Component.literal("Pesquisar"));
		busca.setBordered(false);
		busca.setTextColor(TEXTO);
		busca.setHint(Component.literal("Pesquisar...  (@mod)").withColor(TEXTO_FRACO));
		busca.setValue(q);
		busca.setResponder(s -> { scroll = 0; refiltrar(); });
		addRenderableWidget(busca);
		botoes.clear();
		botoes.add(new Bt(x0 + 184, y0 + 21, 112, 18, () -> "Ordem: " + ordem.rotulo, () -> {
			ordem = Ordem.values()[(ordem.ordinal() + 1) % Ordem.values().length];
			desc = ordem == Ordem.QTD;
			refiltrar();
		}));
		botoes.add(new Bt(x0 + 300, y0 + 21, 22, 18, () -> desc ? "↓" : "↑", () -> { desc = !desc; refiltrar(); }));
		botoes.add(new Bt(x0 + W - 98, y0 + H - 24, 90, 18, () -> "Guardar tudo", () -> ClientPlayNetworking.send(new Pacotes.Guardar())));

		qtdBox = new EditBox(font, x0 + W / 2 - 46, y0 + H / 2 + 2, 92, 12, Component.literal("Quantidade"));
		qtdBox.setBordered(false);
		qtdBox.setTextColor(TEXTO);
		qtdBox.setMaxLength(6);
		qtdBox.setResponder(s -> { String d = s.replaceAll("[^0-9]", ""); if (!d.equals(s)) qtdBox.setValue(d); });
		botoesQtd.clear();
		botoesQtd.add(new Bt(x0 + W / 2 - 52, y0 + H / 2 + 20, 50, 18, () -> "Sacar", this::confirmarQtd));
		botoesQtd.add(new Bt(x0 + W / 2 + 2, y0 + H / 2 + 20, 50, 18, () -> "Cancelar", this::fecharQtd));
		var p = minecraft.player;
		if (p != null && abriuX == 0 && abriuY == 0 && abriuZ == 0) { abriuX = p.getX(); abriuY = p.getY(); abriuZ = p.getZ(); }
		setInitialFocus(busca);
		refiltrar();
	}

	private void refiltrar() {
		visiveis.clear();
		String q = busca == null ? "" : busca.getValue().trim().toLowerCase(Locale.ROOT);
		for (Pacotes.Linha l : todas) {
			if (filtro != null && Categoria.de(l.item()) != filtro) continue;
			if (!q.isEmpty()) {
				var id = BuiltInRegistries.ITEM.getKey(l.item().getItem());
				if (q.startsWith("@")) { if (!id.getNamespace().contains(q.substring(1))) continue; }
				else if (!l.item().getHoverName().getString().toLowerCase(Locale.ROOT).contains(q) && !id.getPath().contains(q)) continue;
			}
			visiveis.add(l);
		}
		Comparator<Pacotes.Linha> porNome = Comparator.comparing(l -> l.item().getHoverName().getString().toLowerCase(Locale.ROOT));
		Comparator<Pacotes.Linha> c = switch (ordem) {
			case NOME -> porNome;
			case QTD -> Comparator.<Pacotes.Linha>comparingLong(Pacotes.Linha::qtd).thenComparing(porNome.reversed());
			case TIPO -> Comparator.<Pacotes.Linha, Integer>comparing(l -> Categoria.de(l.item()).ordinal()).thenComparing(porNome);
		};
		visiveis.sort(desc ? c.reversed() : c);
		scroll = Math.max(0, Math.min(scroll, maxScroll()));
	}

	private int maxScroll() { return Math.max(0, (visiveis.size() + COLS - 1) / COLS - ROWS); }

	// ---------- desenho ----------

	private int gx() { return x0 + 8; }
	private int gy() { return y0 + 72; }

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float pt) {
		// painel
		g.fill(x0 - 2, y0 - 2, x0 + W + 2, y0 + H + 2, 0xAA000000);
		g.fill(x0 - 1, y0 - 1, x0 + W + 1, y0 + H + 1, OURO_ESC);
		g.fillGradient(x0, y0, x0 + W, y0 + H, FUNDO_A, FUNDO_B);
		g.fill(x0, y0, x0 + W, y0 + 1, OURO);
		// título
		g.text(font, Component.literal("✦ Baú Chefe"), x0 + 8, y0 + 7, OURO, true);
		long total = 0; for (Pacotes.Linha l : todas) total += l.qtd();
		String info = fmt(total) + " itens · " + todas.size() + " tipos · " + baus + " baús";
		g.text(font, info, x0 + W - 8 - font.width(info), y0 + 7, TEXTO_FRACO, false);

		// abas
		int tx = x0 + 8, ty = y0 + 44;
		for (int i = -1; i < Categoria.values().length; i++) {
			Categoria c = i < 0 ? null : Categoria.values()[i];
			boolean sel = c == filtro, hov = dentro(mx, my, tx, ty, 30, 22);
			g.fill(tx, ty, tx + 30, ty + 22, sel ? 0xFF3A2C14 : hov ? 0xFF2C2216 : 0xFF1A140D);
			if (sel) g.fill(tx, ty + 20, tx + 30, ty + 22, OURO);
			g.item(new ItemStack(c == null ? Items.CHEST : c.icone), tx + 7, ty + 3);
			if (hov) g.setTooltipForNextFrame(font, Component.literal(c == null ? "Tudo" : c.nome), mx, my);
			tx += 32;
		}

		// campo de pesquisa escuro
		g.fill(x0 + 8, y0 + 21, x0 + 180, y0 + 39, busca.isFocused() ? OURO_ESC : 0xFF2A2620);
		g.fill(x0 + 9, y0 + 22, x0 + 179, y0 + 38, CAMPO);
		super.extractRenderState(g, mx, my, pt); // texto da pesquisa
		for (Bt b : botoes) botao(g, b, mx, my, pedindo == null);

		// grade
		int gx = gx(), gy = gy();
		g.fill(gx - 2, gy - 2, gx + COLS * CEL + 2, gy + ROWS * CEL + 2, 0x55000000);
		Pacotes.Linha hover = null;
		for (int r = 0; r < ROWS; r++)
			for (int c = 0; c < COLS; c++) {
				int idx = (scroll + r) * COLS + c, x = gx + c * CEL, y = gy + r * CEL;
				boolean h = pedindo == null && dentro(mx, my, x, y, CEL, CEL);
				g.fill(x + 1, y + 1, x + CEL - 1, y + CEL - 1, h && idx < visiveis.size() ? CELULA_HOVER : CELULA);
				if (idx >= visiveis.size()) continue;
				Pacotes.Linha l = visiveis.get(idx);
				g.item(l.item(), x + 1, y + 1);
				g.itemDecorations(font, l.item(), x + 1, y + 1, ""); // só barra de durabilidade
				quantidade(g, fmt(l.qtd()), x + CEL - 1, y + CEL - 1);
				if (h) hover = l;
			}
		if (visiveis.isEmpty())
			g.centeredText(font, Component.literal(todas.isEmpty() ? "Nenhum item na rede ainda" : "Nada encontrado"), gx + COLS * CEL / 2, gy + ROWS * CEL / 2 - 4, TEXTO_FRACO);
		// barra de rolagem
		int sbx = gx + COLS * CEL + 4, sbh = ROWS * CEL;
		g.fill(sbx, gy, sbx + 4, gy + sbh, 0x55000000);
		int ms = maxScroll();
		int th = ms == 0 ? sbh : Math.max(12, sbh * ROWS / (ROWS + ms));
		int tyb = ms == 0 ? gy : gy + (sbh - th) * scroll / ms;
		g.fill(sbx, tyb, sbx + 4, tyb + th, OURO);

		// rodapé
		g.text(font, "Esq: 1 pack · Dir: 1 · Shift: tudo · Ctrl: qtd", x0 + 8, y0 + H - 19, TEXTO_FRACO, false);
		if (System.currentTimeMillis() < avisoAte)
			g.centeredText(font, Component.literal(aviso), x0 + W / 2, y0 + H - 34, OURO);

		if (hover != null) {
			List<Component> tip = new ArrayList<>();
			tip.add(hover.item().getHoverName());
			tip.add(Component.literal("Quantidade: " + String.format(Locale.ROOT, "%,d", hover.qtd()).replace(',', '.')).withColor(OURO));
			tip.add(Component.literal(Categoria.de(hover.item()).nome).withColor(TEXTO_FRACO));
			g.setComponentTooltipForNextFrame(font, tip, mx, my);
		}

		// caixa de quantidade
		if (pedindo != null) {
			int bx = x0 + W / 2 - 70, by = y0 + H / 2 - 30;
			g.fill(0, 0, width, height, 0x88000000);
			g.fill(bx - 1, by - 1, bx + 141, by + 77, OURO);
			g.fillGradient(bx, by, bx + 140, by + 76, FUNDO_A, FUNDO_B);
			g.item(pedindo.item(), bx + 6, by + 6);
			g.text(font, pedindo.item().getHoverName(), bx + 26, by + 6, TEXTO, false);
			g.text(font, "de " + fmt(pedindo.qtd()), bx + 26, by + 16, TEXTO_FRACO, false);
			g.fill(x0 + W / 2 - 51, y0 + H / 2 - 3, x0 + W / 2 + 51, y0 + H / 2 + 15, OURO_ESC);
			g.fill(x0 + W / 2 - 50, y0 + H / 2 - 2, x0 + W / 2 + 50, y0 + H / 2 + 14, CAMPO);
			qtdBox.extractRenderState(g, mx, my, pt);
			for (Bt b : botoesQtd) botao(g, b, mx, my, true);
		}
	}

	private void botao(GuiGraphicsExtractor g, Bt b, int mx, int my, boolean ativo) {
		boolean h = ativo && b.dentro(mx, my);
		g.fill(b.x(), b.y(), b.x() + b.w(), b.y() + b.h(), h ? OURO : 0xFF2A2620);
		g.fill(b.x() + 1, b.y() + 1, b.x() + b.w() - 1, b.y() + b.h() - 1, h ? BOTAO_HOVER : BOTAO);
		g.centeredText(font, Component.literal(b.rotulo().get()), b.x() + b.w() / 2, b.y() + (b.h() - 8) / 2, h ? OURO : TEXTO);
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float pt) {
		g.fill(0, 0, width, height, 0xC0000000); // fundo escuro, sem o desfoque claro
	}

	@Override
	public void tick() {
		super.tick();
		var p = minecraft.player;
		if (p == null || !p.isAlive() || p.distanceToSqr(abriuX, abriuY, abriuZ) > 16 * 16) onClose();
	}

	/** Quantidade pequena (70%) alinhada no canto inferior direito da célula. */
	private void quantidade(GuiGraphicsExtractor g, String s, int direita, int base) {
		float k = 0.7f;
		g.pose().pushMatrix();
		g.pose().translate(direita - font.width(s) * k, base - 7 * k);
		g.pose().scale(k, k);
		g.nextStratum();
		g.text(font, s, 0, 0, 0xFFFFFFFF, true);
		g.pose().popMatrix();
	}

	private static boolean dentro(double mx, double my, int x, int y, int w, int h) {
		return mx >= x && my >= y && mx < x + w && my < y + h;
	}

	static String fmt(long n) {
		// no máximo 4 caracteres: 999, 1.2k, 12k, 999k, 1.2M, 12M
		if (n < 1000) return Long.toString(n);
		if (n < 10_000) return String.format(Locale.ROOT, "%.1fk", Math.floor(n / 100.0) / 10);
		if (n < 1_000_000) return (n / 1000) + "k";
		if (n < 10_000_000) return String.format(Locale.ROOT, "%.1fM", Math.floor(n / 100_000.0) / 10);
		return (n / 1_000_000) + "M";
	}

	// ---------- entrada ----------

	@Override
	public boolean mouseClicked(MouseButtonEvent e, boolean dbl) {
		double mx = e.x(), my = e.y();
		if (pedindo != null) {
			if (qtdBox.mouseClicked(e, dbl)) { setFocused(qtdBox); return true; }
			for (Bt b : botoesQtd) if (b.dentro(mx, my)) { b.acao().run(); return true; }
			return true; // modal: ignora o resto
		}
		for (Bt b : botoes) if (b.dentro(mx, my)) { b.acao().run(); return true; }
		if (super.mouseClicked(e, dbl)) return true;
		// abas
		int tx = x0 + 8, ty = y0 + 44;
		for (int i = -1; i < Categoria.values().length; i++) {
			if (dentro(mx, my, tx, ty, 30, 22)) { filtro = i < 0 ? null : Categoria.values()[i]; scroll = 0; refiltrar(); return true; }
			tx += 32;
		}
		// grade
		int gx = gx(), gy = gy();
		if (dentro(mx, my, gx, gy, COLS * CEL, ROWS * CEL)) {
			int c = (int) (mx - gx) / CEL, r = (int) (my - gy) / CEL, idx = (scroll + r) * COLS + c;
			if (idx < visiveis.size()) {
				Pacotes.Linha l = visiveis.get(idx);
				if (e.hasControlDown()) { abrirQtd(l); return true; }
				int pack = l.item().getMaxStackSize();
				long q = e.button() == InputConstants.MOUSE_BUTTON_RIGHT ? 1 : e.hasShiftDown() ? l.qtd() : pack;
				ClientPlayNetworking.send(new Pacotes.Sacar(l.item(), (int) Math.min(Integer.MAX_VALUE, q)));
				return true;
			}
		}
		return false;
	}

	@Override
	public boolean mouseScrolled(double mx, double my, double sx, double sy) {
		if (pedindo == null && sy != 0) { scroll = Math.max(0, Math.min(maxScroll(), scroll - (int) Math.signum(sy))); return true; }
		return super.mouseScrolled(mx, my, sx, sy);
	}

	@Override
	public boolean keyPressed(KeyEvent e) {
		if (pedindo != null) {
			if (e.key() == InputConstants.KEY_ESCAPE) { fecharQtd(); return true; }
			if (e.key() == InputConstants.KEY_RETURN || e.key() == InputConstants.KEY_NUMPADENTER) { confirmarQtd(); return true; }
			return qtdBox.keyPressed(e);
		}
		boolean digitando = busca != null && busca.isFocused();
		boolean shift = InputConstants.isKeyDown(InputConstants.KEY_LSHIFT) || InputConstants.isKeyDown(InputConstants.KEY_RSHIFT);
		if ((e.key() == InputConstants.KEY_V && (shift || !digitando)) || (e.key() == InputConstants.KEY_E && !digitando)) { onClose(); return true; }
		return super.keyPressed(e);
	}

	@Override
	public boolean charTyped(net.minecraft.client.input.CharacterEvent e) {
		if (pedindo != null) return qtdBox.charTyped(e);
		return super.charTyped(e);
	}

	private void abrirQtd(Pacotes.Linha l) {
		pedindo = l;
		qtdBox.setValue(Integer.toString(l.item().getMaxStackSize()));
		setFocused(qtdBox);
		qtdBox.setFocused(true);
	}

	private void fecharQtd() {
		pedindo = null;
		qtdBox.setFocused(false);
		setFocused(busca);
	}

	private void confirmarQtd() {
		if (pedindo == null) return;
		String v = qtdBox.getValue();
		if (!v.isEmpty() && Integer.parseInt(v) > 0) ClientPlayNetworking.send(new Pacotes.Sacar(pedindo.item(), Integer.parseInt(v)));
		fecharQtd();
	}

	@Override
	public boolean isPauseScreen() { return false; }
}
