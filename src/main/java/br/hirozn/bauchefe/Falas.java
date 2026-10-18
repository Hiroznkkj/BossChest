package br.hirozn.bauchefe;

import net.minecraft.util.RandomSource;

import java.util.Locale;

/**
 * Frases do Márcio. Cada frase é o roteiro de voz (ElevenLabs), com as tags de emoção entre colchetes;
 * o balão mostra o texto sem as tags (balao). %s é o nome do jogador.
 */
public final class Falas {
	public enum Tipo { PEIXE, RARO, VAZIO, NOITE, SEM_DOCA, ACORDOU, SONHO, BEBADO, OI, PINTO, PIADA, CARINHO, BOCEJO, ROUBO, DEVOLVEU }

	static final String[] PEIXE = {
			"[excited] Olha o tamanho desse!", "Mais um pro baú!", "[chuckling] Hoje o peixe tá mordendo!",
			"Janta garantida!", "[excited] Peguei! Peguei!", "Esse não é pro Pinto, hein!"};
	static final String[] RARO = {
			"[gasping] Um baiacu?! Ninguém encosta!", "[excited] Peixe raro! Hoje é meu dia!",
			"Uau! Esse vai pra parede!", "[excited] Pinto, olha isso! Que sorte!"};
	static final String[] VAZIO = {
			"[sighing] Ué, cadê o peixe?", "[angry] Me enganou, safado!", "Roubou a isca de novo...",
			"[sighing] Paciência, Márcio...", "Esse era dos grandes, juro!", "[scoffing] Peixe metido a esperto."};
	static final String[] NOITE = {
			"[yawning] Tá escurecendo, bora pra doca.", "[tired] Chega por hoje, Pinto.", "Hora de atracar!",
			"Amanhã tem mais peixe."};
	static final String[] SEM_DOCA = {
			"[sighing] Sem doca... durmo aqui mesmo.", "Barco também é cama, né Pinto?"};
	static final String[] ACORDOU = {
			"[yawning] Bom dia! Bora pescar!", "[sleepy drowsy voice] Que sono... cadê meu barco?",
			"O peixe não espera!", "[clears throat] Acorda, Pinto!"};
	static final String[] SONHO = {
			"[sleepy drowsy voice] Sonhei que o Pinto pilotava o barco... e pilotava melhor que eu.",
			"Sonhei com um bacalhau do tamanho de uma vaca. [pause] Ele que me pescou.",
			"[softly] Sonhei que eu era um peixe... [pause] e ninguém me pescava. Que tristeza.",
			"[chuckling] Sonhei que um creeper me pedia emprego de pescador.",
			"Sonhei que eu tinha um barco com dois baús. [sighing] Sonho é sonho.",
			"[sleepy drowsy voice] Sonhei que a boia afundava sozinha... [pause] Ah não, isso foi ontem."};
	static final String[] BEBADO = {
			"[drunken voice] Bom dia, poste! Cadê o rio que tava aqui?",
			"[drunken voice] Pinto... por que tem dois Pintos? [pause] Os dois são lindos.",
			"[drunken voice] Eu não bebi... [pause] eu me hidratei com cachaça.",
			"[drunken voice] O peixe... o peixe é tipo uma vaca molhada, né?",
			"[drunken voice] [laughing] Quem colocou o rio de lado?",
			"[drunken voice] Vou pescar a lua. [pause] Ela tá pertinho, ó.",
			"[drunken voice] Cadê minha vara? [pause] Ah, tá na mão.",
			"[drunken voice] Eu amo vocês, sabia? [voice breaking] Amo até o Pinto."};
	static final String[] OI = {
			"[excited] Opa, %s!", "E aí, %s? Tá mordendo hoje!", "Bem-vindo ao riacho, %s!", "[playful] %s! Quer um peixinho?"};
	static final String[] OI_HIROZN = {
			"[excited] Fala, hirozn! Veio pescar?", "hirozn! Trouxe isca?", "[chuckling] Opa, hirozn! O Pinto mandou um oi."};
	static final String[] OI_GIIZANINI = {
			"[excited] Oi, giizanini! Quer um peixinho?", "[softly] giizanini! O Pinto tava com saudade.",
			"Que bom te ver, giizanini!"};
	static final String[] PINTO = {
			"[playful] O Pinto do Márcio é o melhor marujo.", "[angry] Pinto, sai de cima do baú!",
			"[chuckling] Quem pesca é o Márcio, quem come é o Pinto.", "Calma, Pinto, já já tem minhoca.",
			"O Pinto do Márcio não enjoa no barco.", "[whispering] Quieto, Pinto, assusta os peixes!",
			"Esse Pinto já viu mais peixe que muito pescador."};
	static final String[] PIADA = {
			"[humming] Onde será que deixei minha carteira de vacinação?",
			"Por que o peixe não usa celular? [pause] Medo de cair na rede. [chuckling]",
			"[humming] Será que eu desliguei o forno? [pause] Eu nem tenho forno.",
			"O que o rio falou pro peixe? [pause] Nada. [laughing]",
			"[sighing] Trinta anos pescando e o peixe ainda me olha com essa cara.",
			"Se pescar fosse fácil, chamava pegar. [chuckling]",
			"[playful] Pinto, se você botar um ovo de ouro, a gente fica rico.",
			"[humming] Paguei o boleto da vara? [pause] [sighing] Paguei.",
			"Peixe que dorme a correnteza leva. [pause] Igual eu na segunda-feira."};
	static final String[] CARINHO = {
			"[softly] Quem é o Pinto mais lindo?", "[chuckling] Tá bom, Pinto, tá bom.", "[playful] Cocoricó pra você também!"};
	static final String[] BOCEJO = {
			"[yawning] Aaaahh... que preguiça.", "[yawning] Uaaah... pescar cansa.",
			"[sleepy drowsy voice] Hmm... só mais cinco minutinhos."};
	static final String[] ROUBO = {
			"[angry] Ei, %s! Devolve meu barco, seu safado!", "[angry] %s, seu pilantra! Esse barco é meu!",
			"[angry] O Pinto tá aí dentro, %s! Sequestrador de galinha!",
			"[angry] Volta aqui com meu barco, %s, seu filho de uma égua!", "[angry] Ladrão! Ladrão de barco! Pega o %s!",
			"[panting] Espera... [angry] %s, seu vagabundo, eu tô velho pra correr!"};
	static final String[] DEVOLVEU = {
			"Obrigado por devolver... [whispering] animal.", "[sighing] Ufa, meu barquinho... [whispering] pilantra.",
			"Pinto, você tá bem? [whispering] Ele te fez alguma coisa?",
			"[sharp exhale] Valeu... [whispering] da próxima eu chamo o golem."};

	private Falas() {}

	static String[] lista(Tipo tipo, String nome) {
		return switch (tipo) {
			case PEIXE -> PEIXE;
			case RARO -> RARO;
			case VAZIO -> VAZIO;
			case NOITE -> NOITE;
			case SEM_DOCA -> SEM_DOCA;
			case ACORDOU -> ACORDOU;
			case SONHO -> SONHO;
			case BEBADO -> BEBADO;
			case OI -> nome == null ? OI : switch (nome.toLowerCase(Locale.ROOT)) {
				case "hirozn" -> OI_HIROZN;
				case "giizanini" -> OI_GIIZANINI;
				default -> OI;
			};
			case PINTO -> PINTO;
			case PIADA -> PIADA;
			case CARINHO -> CARINHO;
			case BOCEJO -> BOCEJO;
			case ROUBO -> ROUBO;
			case DEVOLVEU -> DEVOLVEU;
		};
	}

	/** Roteiro sorteado (com as tags de voz). */
	public static String frase(Tipo tipo, RandomSource r, String nome) {
		String[] l = lista(tipo, nome);
		String f = l[r.nextInt(l.length)];
		return f.contains("%s") ? String.format(f, nome == null ? "ei" : nome) : f;
	}

	/** Texto do balão: sem as tags; o que vem depois de [whispering] aparece entre parênteses, como cochicho. */
	public static String balao(String roteiro) {
		int i = roteiro.indexOf("[whispering]");
		if (i < 0) return limpar(roteiro);
		String antes = limpar(roteiro.substring(0, i)), depois = limpar(roteiro.substring(i)).replaceAll("\\.$", "");
		return (antes.isEmpty() ? "" : antes + " ") + "(" + depois + ")";
	}

	private static String limpar(String s) {
		return s.replaceAll("\\[[^]]*]", "").replaceAll("\\s+", " ").replaceAll(" ([?!.,])", "$1").trim();
	}
}
