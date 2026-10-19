package br.hirozn.bauchefe;

import net.minecraft.util.RandomSource;

import java.util.Locale;

/**
 * Frases do Márcio. As que têm áudio usam o texto exatamente como foi gravado (o balão acompanha a voz);
 * as que ainda não têm áudio guardam o roteiro para o ElevenLabs, com as tags de emoção entre colchetes,
 * e o balão mostra o texto sem as tags (balao). %s é o nome do jogador. Lista completa em FALAS.md.
 */
public final class Falas {
	public enum Tipo { PEIXE, RARO, VAZIO, NOITE, SEM_DOCA, ACORDOU, SONHO, BEBADO, OI, PINTO, PIADA, CANCAO, CARINHO, BOCEJO, ROUBO, DEVOLVEU, MEDO, BUSCA, CORDA, TOCA, BATEU, RAIO, APANHOU, LEITURA, PAPO, QUENTE, FOGO, ARMADURA, QUARTO, CONVERSA, CHUVA }

	/** Uma fala: o texto (ou roteiro) e o som em assets/bauchefe/sounds/marcio (null se ainda não tem áudio). */
	public record Linha(String texto, String som) {}

	static final Linha[] PEIXE = { // gravadas
			new Linha("Olha o tamanho desse!", "peixe_1"),
			new Linha("Mais um pro baú.", "peixe_2"),
			new Linha("Hoje o peixe tá mordendo.", "peixe_3"),
			new Linha("Janta garantida!", "peixe_4"),
			new Linha("Peguei! Peguei!", "peixe_6")};
	static final Linha[] RARO = { // gravadas
			new Linha("Um baiacu?! Ninguém encosta!", "raro_1"),
			new Linha("Um peixe raro!", "raro_2"),
			new Linha("Uau, esse vai pra parede!", "raro_3"),
			new Linha("Pinto! Olha isso! Que sorte!", "raro_4")};
	static final Linha[] VAZIO = { // gravadas
			new Linha("Ué, cadê o peixe?", "vazio_1"),
			new Linha("Me enganou! Eu te pego, Nemo!", "vazio_2"),
			new Linha("Roubou a isca de novo?", "vazio_3"),
			new Linha("Paciência, Márcio. Paciência.", "vazio_4"),
			new Linha("E esse era um dos grandes.", "vazio_5"),
			new Linha("O peixe metido a esperto.", "vazio_6")};
	static final Linha[] NOITE = { // gravadas
			new Linha("Ah, tá escurecendo. Vamo pra doca.", "noite_1"),
			new Linha("Chega por hoje, Pinto.", "noite_2")};
	static final Linha[] SEM_DOCA = { // ainda sem áudio
			new Linha("[sighing] Sem doca... durmo aqui mesmo.", null),
			new Linha("Barco também é cama, né Pinto?", null)};
	static final Linha[] ACORDOU = { // gravadas
			new Linha("Bom dia! Bora pescar!", "acordou_1")};
	static final Linha[] SONHO = { // gravadas
			new Linha("Sonhei que o Pinto pilotava o barco... e pilotava melhor que eu.", "sonho_1"),
			new Linha("Sonhei com um bacalhau do tamanho de uma vaca. Ele que me pescou.", "sonho_2"),
			new Linha("Sonhei que eu era um peixe... e ninguém me pescava. Que triste.", "sonho_3"),
			new Linha("Sonhei com um creeper. Ele me pediu uma vaga de emprego. Acredita nisso?", "sonho_4"),
			new Linha("Sonhei que eu tinha um barco com dois baús. Ah, sonho é sonho.", "sonho_5"),
			new Linha("Sonhei que a boia afundava sozinha. Ah não, isso foi ontem.", "sonho_6")};
	static final Linha[] BEBADO = { // ainda sem áudio
			new Linha("[drunken voice] Bom dia, poste! Cadê o rio que tava aqui?", null),
			new Linha("[drunken voice] Pinto... por que tem dois Pintos? [pause] Os dois são lindos.", null),
			new Linha("[drunken voice] Eu não bebi... [pause] eu me hidratei com cachaça.", null),
			new Linha("[drunken voice] O peixe... o peixe é tipo uma vaca molhada, né?", null),
			new Linha("[drunken voice] [laughing] Quem colocou o rio de lado?", null),
			new Linha("[drunken voice] Vou pescar a lua. [pause] Ela tá pertinho, ó.", null),
			new Linha("[drunken voice] Cadê minha vara? [pause] Ah, tá na mão.", null),
			new Linha("[drunken voice] Eu amo vocês, sabia? [voice breaking] Amo até o Pinto.", null)};
	static final Linha[] OI = { // gravadas
			new Linha("Opa!", "oi_1"),
			new Linha("E aí? Tá mordendo hoje? Quer dizer, tá pescando bastante?", "oi_2"),
			new Linha("Bem-vindo ao riacho!", "oi_3"),
			new Linha("Quer um peixinho?", "oi_4")};
	static final Linha[] OI_HIROZN = { // ainda sem áudio
			new Linha("[excited] Fala, hirozn! Veio pescar?", null),
			new Linha("hirozn! Trouxe isca?", null),
			new Linha("[chuckling] Opa, hirozn! O Pinto mandou um oi.", null)};
	static final Linha[] OI_GIIZANINI = { // ainda sem áudio
			new Linha("[excited] Oi, giizanini! Quer um peixinho?", null),
			new Linha("[softly] giizanini! O Pinto tava com saudade.", null),
			new Linha("Que bom te ver, giizanini!", null)};
	static final Linha[] PINTO = { // gravadas
			new Linha("O meu Pinto é o melhor marujo!", "pinto_1"),
			new Linha("Pinto! Sai de cima do baú!", "pinto_2"),
			new Linha("Quem pesca é o Márcio, quem come é o meu Pinto. Caiu na rede é peixe!", "pinto_3"),
			new Linha("Calma, Pinto, já já tem minhoca.", "pinto_4"),
			new Linha("Quieto, Pinto! Assusta os peixes!", "pinto_6"),
			new Linha("Esse Pinto já viu mais peixe que muito pescador.", "pinto_7")};
	static final Linha[] PIADA = { // gravadas
			new Linha("Hum, onde será que deixei minha carteira de vacinação?", "piada_1"),
			new Linha("Ô Pinto, sabe por que o peixe não usa celular, hein? Não? Medo de cair na rede.", "piada_2"),
			new Linha("Hmm, será que eu desliguei o forno? Ah, eu nem tenho forno.", "piada_3"),
			new Linha("O que o rio falou pro peixe? Nada!", "piada_4"),
			new Linha("Trinta anos pescando e o peixe ainda me olha com essa cara.", "piada_5"),
			new Linha("Se pescar fosse fácil, chamava pegar.", "piada_6"),
			new Linha("Pinto, se você botar um ovo de ouro, a gente fica rico!", "piada_7"),
			new Linha("Paguei o boleto da vara? Paguei.", "piada_8"),
			new Linha("Hum, peixe que dorme a correnteza leva. Igual minha ex-mulher: deixou só a canoa.", "piada_9")};
	static final Linha[] CANCAO = { // gravadas
			new Linha("Eu esqueci da isca...", "cancao_1")};
	static final Linha[] CARINHO = { // gravadas
			new Linha("Quem é o Pinto mais lindo?", "carinho_1"),
			new Linha("Cocoricó pra você também!", "carinho_3")};
	static final Linha[] BOCEJO = { // gravadas
			new Linha("Aaah, que preguiça.", "bocejo_1")};
	static final Linha[] ROUBO = { // gravadas
			new Linha("Ei, pilantra! Devolve meu barco, seu safado! Golem, pega ele!", "roubo_1"),
			new Linha("Seu pilantra, esse barco é meu!", "roubo_2"),
			new Linha("O Pinto tá aí dentro! Sequestrador de Pinto!", "roubo_3"),
			new Linha("Volta aqui com o meu barco, seu caipora!", "roubo_4"),
			new Linha("Ladrão! Ladrão de barco!", "roubo_5"),
			new Linha("Espera! Vagabundo! Eu tô velho pra correr!", "roubo_6")};
	static final Linha[] DEVOLVEU = { // gravadas
			new Linha("Ufa! Meu barquinho! (Pilantra!)", "devolveu_2"),
			new Linha("Pinto, você tá bem? (Ele te fez alguma coisa?)", "devolveu_3"),
			new Linha("Valeu... (da próxima eu chamo o golem)", "devolveu_4")};

	static final Linha[] MEDO = { // ainda sem áudio
			new Linha("[gasping] Bicho! Rema, Pinto, rema!", null),
			new Linha("[panting] Sai pra lá, assombração!", null),
			new Linha("[angry] Hoje não, zumbi!", null),
			new Linha("[gasping] Vou pra doca, que aqui tá perigoso!", null)};

	static final Linha[] BUSCA = { // ainda sem áudio
			new Linha("[sighing] Cadê meu barco?", null),
			new Linha("Peraí, Pinto, tô indo!", null),
			new Linha("[panting] Barquinho, volta aqui!", null)};
	static final Linha[] CORDA = { // ainda sem áudio
			new Linha("[panting] Segura essa corda, barquinho!", null),
			new Linha("[sharp exhale] Vem cá, sobe, sobe!", null)};

	/** Jeito de falar, para a pose acompanhar a frase. */
	public static final class Humor {
		public static final int NEUTRO = 0, ALEGRE = 1, TRISTE = 2, BRAVO = 3, MEDO = 4, SONO = 5, PIADA = 6, BEBADO = 7,
				SONHO = 8, CARINHO = 9, ANIMADO = 10;
		private Humor() {}
	}

	static final Linha[] TOCA = { // ainda sem áudio
			new Linha("[excited] Toca aqui!", null),
			new Linha("Bate aqui, parceiro!", null),
			new Linha("[chuckling] Dá cá essa mão!", null)};
	static final Linha[] BATEU = { // ainda sem áudio
			new Linha("[laughing] Isso aí!", null),
			new Linha("[excited] Boa!", null),
			new Linha("[chuckling] Essa doeu, hein!", null)};
	static final Linha[] RAIO = { // ainda sem áudio
			new Linha("[gasping] Nossa Senhora! Essa foi perto!", null),
			new Linha("[shouting] Raio! Corre, corre!", null),
			new Linha("[panting] Ai meu Deus, quase me pegou!", null)};
	static final Linha[] APANHOU = { // ainda sem áudio
			new Linha("[angry] Ai! Que isso?", null),
			new Linha("[annoyed] Ei! Respeita o pescador!", null),
			new Linha("Ai, minha costela!", null)};
	static final Linha[] LEITURA = { // ainda sem áudio
			new Linha("[softly] Hmm... onde eu parei...", null),
			new Linha("[whispering] Capítulo sete: o peixe gigante.", null),
			new Linha("[chuckling] Esse livro é melhor que o filme.", null)};
	static final Linha[] PAPO = { // ainda sem áudio
			new Linha("Senta aí, deixa eu te contar uma do Pinto.", null),
			new Linha("[sighing] A vida de pescador é boa, viu.", null),
			new Linha("Sabe o que eu queria? Um barco com dois baús.", null),
			new Linha("[chuckling] Uma vez eu pesquei uma bota. Uso até hoje.", null)};

	static final Linha[] QUENTE = { // ainda sem áudio
			new Linha("[gasping] Opa, tá quente aqui!", null),
			new Linha("Eita, quase chamusquei o bigode!", null),
			new Linha("[coughing] Fogo não é brinquedo, não!", null)};
	static final Linha[] FOGO = { // ainda sem áudio
			new Linha("[shouting] Tô pegando fogo! Água, água!", null),
			new Linha("[screaming] Ai ai ai, apaga, apaga!", null)};
	static final Linha[] ARMADURA = { // ainda sem áudio; %s é o nome do jogador
			new Linha("[impressed] Olha só, %s! Que armadura bonita!", null),
			new Linha("Tá brilhando, hein, %s!", null),
			new Linha("[chuckling] Com essa armadura nem o Pinto te bica!", null)};
	static final Linha[] QUARTO = { // ainda sem áudio; %s é quantos passos ele contou
			new Linha("%s passos... Isso é um quarto ou um armário?", null),
			new Linha("Só %s passos de parede a parede. Meu barco é maior!", null),
			new Linha("[sighing] %s passos. Até o Pinto ia reclamar desse quarto.", null)};
	static final Linha[] CONVERSA = { // ainda sem áudio
			new Linha("E aí, compadre, como tá a colheita?", null),
			new Linha("Sabia que o peixe morde mais de manhã?", null),
			new Linha("[chuckling] Você viu o Pinto hoje? Tá cada dia mais bonito.", null),
			new Linha("[whispering] Dizem que tem um peixe gigante no rio.", null)};
	static final Linha[] CHUVA = { // ainda sem áudio
			new Linha("[sighing] Chuva... os peixes adoram, eu não.", null),
			new Linha("Ai, molhou meu chapéu!", null),
			new Linha("Bom pra horta, ruim pro pescador.", null)};

	private Falas() {}

	public static int humor(Tipo t) {
		return switch (t) {
			case PEIXE, ACORDOU, OI, CANCAO, DEVOLVEU -> Humor.ALEGRE;
			case RARO -> Humor.ANIMADO;
			case VAZIO, SEM_DOCA, BUSCA -> Humor.TRISTE;
			case NOITE, BOCEJO -> Humor.SONO;
			case ROUBO, CORDA -> Humor.BRAVO;
			case MEDO -> Humor.MEDO;
			case PIADA -> Humor.PIADA;
			case BEBADO -> Humor.BEBADO;
			case SONHO -> Humor.SONHO;
			case PINTO, CARINHO -> Humor.CARINHO;
			case TOCA, BATEU -> Humor.ANIMADO;
			case RAIO -> Humor.MEDO;
			case APANHOU -> Humor.BRAVO;
			case LEITURA -> Humor.NEUTRO;
			case PAPO, QUARTO, CONVERSA -> Humor.PIADA;
			case QUENTE, FOGO -> Humor.MEDO;
			case ARMADURA -> Humor.ANIMADO;
			case CHUVA -> Humor.TRISTE;
		};
	}

	static Linha[] lista(Tipo tipo, String nome, RandomSource r) {
		return switch (tipo) {
			case PEIXE -> PEIXE;
			case RARO -> RARO;
			case VAZIO -> VAZIO;
			case NOITE -> NOITE;
			case SEM_DOCA -> SEM_DOCA;
			case ACORDOU -> ACORDOU;
			case SONHO -> SONHO;
			case BEBADO -> BEBADO;
			// hirozn e giizanini: metade das vezes pelo nome (só balão, ainda sem áudio), metade com voz
			case OI -> nome == null || r.nextBoolean() ? OI : switch (nome.toLowerCase(Locale.ROOT)) {
				case "hirozn" -> OI_HIROZN;
				case "giizanini" -> OI_GIIZANINI;
				default -> OI;
			};
			case PINTO -> PINTO;
			case PIADA -> PIADA;
			case CANCAO -> CANCAO;
			case CARINHO -> CARINHO;
			case BOCEJO -> BOCEJO;
			case ROUBO -> ROUBO;
			case DEVOLVEU -> DEVOLVEU;
			case MEDO -> MEDO;
			case BUSCA -> BUSCA;
			case CORDA -> CORDA;
			case TOCA -> TOCA;
			case BATEU -> BATEU;
			case RAIO -> RAIO;
			case APANHOU -> APANHOU;
			case LEITURA -> LEITURA;
			case PAPO -> PAPO;
			case QUENTE -> QUENTE;
			case FOGO -> FOGO;
			case ARMADURA -> ARMADURA;
			case QUARTO -> QUARTO;
			case CONVERSA -> CONVERSA;
			case CHUVA -> CHUVA;
		};
	}

	/** Fala sorteada, com o nome já no lugar do %s. */
	public static Linha frase(Tipo tipo, RandomSource r, String nome) {
		Linha[] l = lista(tipo, nome, r);
		Linha f = l[r.nextInt(l.length)];
		return f.texto().contains("%s") ? new Linha(String.format(f.texto(), nome == null ? "ei" : nome), f.som()) : f;
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
