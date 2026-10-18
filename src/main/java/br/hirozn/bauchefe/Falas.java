package br.hirozn.bauchefe;

import net.minecraft.util.RandomSource;

import java.util.Locale;

/** Frases do Márcio. %s é o nome do jogador. */
public final class Falas {
	public enum Tipo { PEIXE, RARO, VAZIO, NOITE, SEM_DOCA, ACORDOU, OI, PINTO, CARINHO, BOCEJO, ROUBO, DEVOLVEU }

	private static final String[] PEIXE = {
			"Olha o tamanho desse!", "Mais um pro baú!", "Hoje o peixe tá mordendo!", "Janta garantida!",
			"Peguei! Peguei!", "Esse não é pro Pinto, hein!"};
	private static final String[] RARO = {
			"Um baiacu?! Ninguém encosta!", "Peixe raro! Hoje é meu dia!", "Uau! Esse vai pra parede!",
			"Pinto, olha isso! Que sorte!"};
	private static final String[] VAZIO = {
			"Ué, cadê o peixe?", "Me enganou, safado!", "Roubou a isca de novo...", "Paciência, Márcio...",
			"Esse era dos grandes, juro!"};
	private static final String[] NOITE = {
			"Tá escurecendo, bora pra doca.", "Chega por hoje, Pinto.", "Hora de atracar!", "Amanhã tem mais peixe."};
	private static final String[] SEM_DOCA = {
			"Sem doca... durmo aqui mesmo.", "Barco também é cama, né Pinto?"};
	private static final String[] ACORDOU = {
			"Bom dia! Bora pescar!", "Que sono... cadê meu barco?", "O peixe não espera!", "Acorda, Pinto!"};
	private static final String[] OI = {
			"Opa, %s!", "E aí, %s? Tá mordendo hoje!", "Bem-vindo ao riacho, %s!", "%s! Quer um peixinho?"};
	private static final String[] OI_HIROZN = {
			"Fala, hirozn! Veio pescar?", "hirozn! Trouxe isca?", "Opa, hirozn! O Pinto mandou um oi."};
	private static final String[] OI_GIIZANINI = {
			"Oi, giizanini! Quer um peixinho?", "giizanini! O Pinto tava com saudade.", "Que bom te ver, giizanini!"};
	private static final String[] PINTO = {
			"O Pinto do Márcio é o melhor marujo.", "Pinto, sai de cima do baú!", "Quem pesca é o Márcio, quem come é o Pinto.",
			"Calma, Pinto, já já tem minhoca.", "O Pinto do Márcio não enjoa no barco.", "Quieto, Pinto, assusta os peixes!",
			"Esse Pinto já viu mais peixe que muito pescador."};
	private static final String[] CARINHO = {
			"Quem é o Pinto mais lindo?", "Tá bom, Pinto, tá bom.", "Cocoricó pra você também!"};
	private static final String[] BOCEJO = {
			"Aaaahh... que preguiça.", "Uaaah... pescar cansa.", "Hmm... só mais cinco minutinhos."};
	private static final String[] ROUBO = {
			"Ei, %s! Devolve meu barco!", "%s, esse barco é meu!", "O Pinto tá aí dentro, %s!",
			"Volta aqui com meu barco, %s!", "Ladrão de barco! %s!"};
	private static final String[] DEVOLVEU = {
			"Valeu por devolver!", "Ufa, meu barquinho!", "Pinto, você tá bem?"};

	private Falas() {}

	public static String frase(Tipo tipo, RandomSource r, String nome) {
		String[] lista = switch (tipo) {
			case PEIXE -> PEIXE;
			case RARO -> RARO;
			case VAZIO -> VAZIO;
			case NOITE -> NOITE;
			case SEM_DOCA -> SEM_DOCA;
			case ACORDOU -> ACORDOU;
			case OI -> nome == null ? OI : switch (nome.toLowerCase(Locale.ROOT)) {
				case "hirozn" -> OI_HIROZN;
				case "giizanini" -> OI_GIIZANINI;
				default -> OI;
			};
			case PINTO -> PINTO;
			case CARINHO -> CARINHO;
			case BOCEJO -> BOCEJO;
			case ROUBO -> ROUBO;
			case DEVOLVEU -> DEVOLVEU;
		};
		String f = lista[r.nextInt(lista.length)];
		return f.contains("%s") ? String.format(f, nome == null ? "ei" : nome) : f;
	}
}
