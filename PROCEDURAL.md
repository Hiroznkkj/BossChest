# Pose procedural do Márcio (e dos próximos NPCs)

As animações do Márcio não são ângulos fixos: a pose é calculada a cada quadro a partir do que existe no mundo
(onde está o barco, a borda dele, o Pinto, o chão embaixo de cada pé, a altura do assento, o fogo, a mão de quem
bate o hi five). Este guia mostra como criar uma reação nova sem fazer conta de matriz.

## Como a pose é montada (em ordem)

1. **Pose da ação** (`PoseMarcio`): pesca, sentado no barco, sentado num assento, briga com o peixe, andando.
2. **Animações customizadas** do Blockbench, se existirem (`ANIMACOES.md`).
3. **Transições** (`Trajeto` + `PoseMarcio.transicao`): subir e descer do barco e sentar e levantar, com o caminho
   calculado pela geometria real do barco ou do assento.
4. **Animações especiais** (espreguiçar, coçar, acenar, comemorar, carinho).
5. **Camadas procedurais** (`Camadas`): reações que somam por cima de tudo, na ordem da lista.
6. **Jeito de falar** (`PoseMarcio.expressao`).
7. **Suavização** (`MarcioModel.arrastar`): nada muda de uma vez.
8. **Resolvedor** (`Resolver`): acerta a pose no mundo de verdade (ver abaixo).

## O resolvedor: o corpo como coisa sólida

Cada parte do corpo (cabeça, tronco, braços, pernas) é uma caixa sólida do tamanho do cubo dela no modelo
(`Esqueleto`), e o mundo são as caixas de colisão dos blocos (`Colisao`): a mesma colisão que segura o jogador,
então laje, degrau, cerca, escada de mão e cadeira de mod entram sem lista. Depois que a animação e as camadas
dizem o que o corpo quer fazer, o resolvedor mexe o mínimo possível em 19 coisas (posição e inclinação do corpo,
cabeça, braços e pernas) para:

- **nenhuma parte entrar em bloco**: pontos espalhados em cada parte, e as arestas dos blocos contra cada parte
  (a quina de uma laje no meio da coxa);
- **os apoios encostarem**: a pose pede apoios (pés, quadril, costas, nuca), e cada um procura sua superfície
  com um raio a partir do próprio corpo. Encostado, as costas vão até a parede que está atrás, sem flutuar.
  Deitado, as costas e a nuca ficam no chão, e virando a cabeça a quina dela não entra no bloco;
- **não fugir da pose pedida** e **não pular** de um quadro para o outro.

Cada item vira um erro em pixels, e o resolvedor procura a pose com a menor soma (mínimos quadrados, Levenberg-Marquardt).
Os apoios de cada situação ficam numa tabela (`Resolver.pedidos`), não num `if` por ação. Uma animação pode pedir os
seus no arquivo (ver ANIMACOES.md). Um apoio novo é uma linha: um ponto de um osso, a direção em que ele procura e o alcance.

Para ver o que ele vê, digite **`/marcioanim colisao`**:
- a caixa de cada parte aparece em verde, ou em vermelho se estiver entrando num bloco;
- os blocos em volta aparecem em cinza;
- cada apoio é uma linha amarela até a superfície que ele achou;
- em cima da cabeça aparece quantos pixels estavam dentro de blocos antes e depois.

## A API: `Corpo`

Todo `Vec3` é um ponto do **mundo**. O resto é no **espaço do corpo**: pixels, y para baixo, frente = -z,
esquerda do NPC = +x. `w` é sempre o peso (0 a 1): mistura com a pose que já está lá, então nada salta.

| Método | O que faz |
|---|---|
| `mao(Lado, Vec3, w)` | a mão vai até o ponto do mundo (devolve quanto faltou, em px) |
| `alcancar(Lado, Vec3, w, max)` | igual, e se não alcança o corpo se inclina até `max` radianos |
| `maoNoCorpo(Lado, x, y, z, w)` | a mão vai até um ponto do próprio corpo (peito, bolso, atrás da cabeça) |
| `olhar(Vec3, w)` / `olharNoCorpo(x, y, z, w)` | vira a cabeça para o ponto, com limite de pescoço |
| `inclinar(frente, lado, w)` | inclina o corpo inteiro em volta dos pés (em pé) ou do quadril (sentado) |
| `recuarDe(Vec3, forca, w)` | inclina para longe do ponto |
| `direcao(Vec3)` | de que lado está o ponto: `{x, z}` (x > 0 = à esquerda dele; z < 0 = na frente) |
| `distancia(Vec3)` | distância no chão até o ponto (blocos) |
| `noMundo(parte, x, y, z)` | onde está um ponto de uma parte do corpo, no mundo (ex.: a mão) |
| `Corpo.envelope(t, entra, sai, fim)` | peso de uma reação no tempo: sobe em `entra` ticks, fica até `sai`, some até `fim` |

Partes: `c.cabeca`, `c.tronco`, `c.bracoD`, `c.bracoE`, `c.pernaD`, `c.pernaE`, `c.root` (o corpo todo),
e `c.d` com tudo o que o cliente sabe do NPC (`DadosMarcio`).

Dentro de `Ik` estão as peças de baixo nível (`modelo`, `mundo`, `mirar`, `girarNosPes`), caso precise. Em
`Colisao` estão `entra` (quanto uma caixa girada entrou num bloco), `fundo` (quão fundo um ponto está) e `raio`
(a primeira superfície numa direção), para usar em outras mecânicas. O comprimento do braço e da perna (`Ik.BRACO`, `Ik.PERNA`) é medido dos cubos do modelo.

## Criar uma reação nova (exemplo: espirrar quando chove)

1. **O cliente precisa saber quando.** Se dá para saber só olhando o mundo no cliente (chuva, fogo no NPC, hora),
   calcule em `MarcioCliente.alvos` e guarde em `DadosMarcio`. Se é algo que só o servidor sabe (um raio caiu,
   um jogador bateu), mande uma `Pacotes.Reacao` do `Pescador` com `reagir(v, tipo, alvo, onde)`. No cliente ela
   chega em `reacoes`, com o tempo desde que aconteceu, a posição e o id da entidade envolvida.
2. **Escreva a camada** em `Camadas.java`:

```java
/** Espirro na chuva: cabeça vai para trás, depois o tranco para a frente com a mão na boca. */
public static final Camada ESPIRRO = new Camada() {
	public float peso(DadosMarcio d) { return d.chuva ? Corpo.envelope(d.agora % 400f, 3, 10, 18) : 0f; }
	public void aplicar(Corpo c, float w, float age) {
		float t = c.d.agora % 400f;
		float antes = t < 6 ? t / 6f : 0f, tranco = t >= 6 ? 1f - (t - 6) / 12f : 0f;
		c.cabeca.xRot -= 0.4f * antes * w;          // "a... a..."
		c.inclinar(0.25f * tranco, 0f, w);          // "...tchim!"
		c.maoNoCorpo(Corpo.Lado.ESQUERDO, 1f, -3f, -5f, tranco * w); // mão na boca
	}
};
```

3. **Registre**: acrescente na lista do `static { ... }` no fim de `Camadas.java`, na posição certa (as de baixo
   ganham das de cima quando mexem na mesma parte). De fora da classe, `Camadas.registrar(minhaCamada)`.
4. **Teste sem abrir o jogo**: em `src/testePose/java/.../TestePose.java`, monte um `DadosMarcio`, chame
   `aplicar(d, t)` e confira com `ok(condição, "o que", valor, limite)`. Rode `./gradlew testePose`.

## Regras que evitam "teleporte"

- Use sempre `w` e `Corpo.envelope` para entrar e sair. Nunca ligue uma pose de uma vez.
- Mexa somando (`+=`) ou misturando com `w`; não sobrescreva um ângulo sem peso.
- Mudança de lugar (montar, desmontar, ser teleportado) passa pelo `Trajeto`: o corpo faz o caminho em vez de pular.
- O `./gradlew testePose` mede a velocidade da cabeça no mundo em cada transição e falha acima de 0,35 bloco por tick.

## O que o servidor já detecta (`Arredores`, `Lugares`)

- `Lugares.assento / cadeira / chao / encosto / deitar`: onde sentar (pelo formato do bloco, qualquer mod),
  sentar no chão, se encostar (parede, escada de mão, cerca) e deitar.
- `Arredores.calor`: fogo, lava ou fogueira colada. `Arredores.fogueira`: fogueira acesa. `pertoDoFogo`: lugar
  no chão virado para ela.
- `Arredores.agua`: água para se apagar.
- `Arredores.quarto` e `pontas`: o chão do quarto da cama e as duas pontas para medir.
- `Arredores.armadura` e `valor`: a armadura de alguém e quão boa ela é.
