# Animações customizadas do Márcio

Dá para trocar qualquer animação do Márcio, ou criar novas, no [Blockbench](https://www.blockbench.net), sem
recompilar o mod. As animações do código continuam lá: uma customizada só toma o lugar da animação que tem o mesmo nome.
Se você apagar o arquivo, a do código volta.

## Passo a passo

1. No jogo, digite **`/marcioanim exportar`**. O mod grava em `config/bauchefe/animacoes/originais/`:
   - `marcio.geo.json`: o modelo do Márcio (modelo de jogador, com o osso `root` por fora de tudo);
   - `marcio.png`: a skin dele;
   - `marcio.animation.json`: todas as animações do código, já em quadros-chave, para servir de ponto de partida.
2. No Blockbench: **File > Open Model** e escolha o `marcio.geo.json`. Arraste o `marcio.png` para a textura.
3. Na aba **Animate**: **Animation > Import Animations** e escolha o `marcio.animation.json`.
4. Mexa no que quiser (ou crie uma animação nova com **+**).
5. **Animation > Export Animations**, salvando em `config/bauchefe/animacoes/` (não na pasta `originais`, que o mod não lê).
   O nome do arquivo não importa; pode ter várias animações num arquivo só.
6. Pronto: o mod relê a pasta sozinho quando um arquivo muda (no máximo 1 s depois). Para ver na hora,
   chegue perto do Márcio e digite **`/marcioanim tocar <nome>`** (toca só no seu PC; Tab completa o nome).

Outros comandos: `/marcioanim lista` (o que está carregado e o último erro de leitura) e `/marcioanim recarregar`.

As animações customizadas são só visuais e ficam no PC de quem joga. Num servidor, cada jogador que quiser vê-las
precisa ter os arquivos na própria pasta `config`. Para mandar junto com o mod, coloque os arquivos em
`src/main/resources/assets/bauchefe/animacoes/` (ou num resource pack, em `assets/bauchefe/animacoes/`). A pasta `config` passa por cima.

## Nomes

O nome é a última parte da chave da animação: `animation.marcio.acenar` vira `acenar`.

| Nome | Quando toca | Duração no jogo |
|---|---|---|
| `parado` | sentado no barco, sem pescar (fica em loop por baixo de tudo) | loop |
| `espreguicar` | espreguiçando | 3 s |
| `cocar` | coçando a cabeça | 2,5 s |
| `acenar` | acenando para um jogador | 2,2 s |
| `comemorar` | depois de pegar peixe | 2 s |
| `carinho` | carinho no Pinto | 3 s |
| `pesca_preparar` | levanta a vara para trás | 0,5 s |
| `pesca_lancar` | lança a boia | 0,6 s |
| `pesca_boiando` | esperando o peixe | até morder (use loop) |
| `pesca_mordida` | o peixe mordeu | 0,7 s |
| `pesca_luta` | peixe difícil: de pé, brigando | 4,5 s |
| `pesca_puxar` | puxa a linha | 0,9 s |
| qualquer outro | só com `/marcioanim tocar` | o `animation_length` |

Quem decide quando cada animação começa e quanto ela dura é o servidor, pela tabela acima. Se a sua for mais curta,
ela para no último quadro (ou repete, se tiver **Loop** ligado). Se for mais longa, ela é cortada. As especiais
(`espreguicar` até `carinho`) entram e saem misturando com a pose normal em 0,4 s, então não precisa começar nem terminar na pose de descanso.

## Como os valores funcionam

- **Ossos:** `head`, `body`, `rightArm`, `leftArm`, `rightLeg`, `leftLeg` e `root` (o corpo todo). Outros nomes são ignorados.
- **Rotação** em graus e **posição** em pixels, a partir do modelo parado (em pé, braços para baixo). Os valores
  são exatamente os que aparecem no Blockbench. **Escala** também funciona.
- Só os canais que existem no arquivo mudam. Um osso sem quadro-chave fica com a pose do código (por exemplo,
  se a sua `acenar` só mexe no `leftArm`, o resto continua sentado e respirando normalmente).
- Interpolação **Linear**, **Catmull-Rom** (suave) e **Step**. Molang (`math.sin(...)` etc.) **não** é suportado;
  um valor que não for número vira 0 e aparece no `/marcioanim lista`.
- Por padrão o osso vai para a pose do arquivo. Para **somar** na pose do código em vez de trocar (útil numa
  mexidinha por cima da respiração), coloque `"bauchefe_somar": true` dentro da animação no `.animation.json`.
- No jogo, toda pose passa por uma suavização (o "arrasto" de ~0,1 s), então movimentos muito secos ficam um pouco mais macios.
- Mexer no braço direito durante a pesca não muda de onde a boia sai: a linha continua saindo da ponta da vara do código.

Se algum eixo aparecer espelhado no jogo em relação ao Blockbench, inverta o sinal desse eixo no quadro-chave.
