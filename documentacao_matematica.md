# DataKick Football Analysis — Documentação Matemática

> **Nível:** Matemática Discreta / Análise de Algoritmos — Ensino Superior  
> **Idioma:** Português  
> **Última atualização:** Junho de 2026

---

## Sumário

1. [Parte 1 — Banco de Dados e Integração com a API](#parte-1)
2. [Parte 2 — Algoritmo de Busca: Interpolation Search](#parte-2)
3. [Parte 3 — Algoritmo de Análise: DataKick Predictor (DKP)](#parte-3)
4. [Perguntas que o professor pode fazer](#perguntas)
5. [Glossário](#glossario)

---

<a name="parte-1"></a>
## Parte 1 — Banco de Dados e Integração com a API

### 1.1 Modelo Relacional

O sistema armazena o histórico de partidas e estatísticas em um banco de dados PostgreSQL com **sete tabelas**. Abaixo está o esquema simplificado:

```
regiao (id PK, nome)
    │
    └─ selecao (id PK, nome, codigo_fifa, regiao_id FK→regiao)
                  │
                  └─ temporada (id PK, ano, selecao_id FK→selecao)
                                  │
                                  └─ partida (id PK, temporada_id FK→temporada,
                                              adversario, gols_pro, gols_contra,
                                              resultado, data_partida)
                                              │
                                              └─ partida_jogador (id PK, partida_id FK→partida,
                                                                   jogador_id FK→jogador)
                                                                   │
                                                                   └─ estatistica (id PK,
                                                                       partida_jogador_id FK,
                                                                       gols, assistencias, ...)

jogador (id PK, nome, posicao, data_nascimento)
```

**Observações sobre o design:**

- A tabela `regiao` agrupa seleções por confederação (UEFA, CONMEBOL, etc.), permitindo filtros no dashboard sem joins complexos.
- A separação `selecao → temporada → partida` normaliza o histórico: uma mesma seleção pode ter múltiplos anos de dados sem duplicação de atributos.
- A tabela `partida_jogador` implementa um relacionamento **N:M** entre `partida` e `jogador`, possibilitando que um jogador apareça em várias partidas e que uma partida registre múltiplos jogadores.

### 1.2 Gerenciamento de Conexões — HikariCP

Conexões diretas a bancos de dados relacionais são operações custosas: envolvem autenticação, negociação de protocolo e alocação de memória no servidor. O projeto usa **HikariCP** — uma implementação de *connection pool* reconhecida pela comunidade Java por sua baixa latência.

**Configuração (classe `DatabaseConfig`):**

| Parâmetro | Valor | Significado |
|---|---|---|
| `maximumPoolSize` | 10 | Máximo de conexões simultâneas ao PostgreSQL |
| `minimumIdle` | 2 | Conexões mantidas abertas durante períodos ociosos |
| `connectionTimeout` | padrão (30s) | Tempo máximo de espera por uma conexão disponível |

**Por que pool de conexões?**

Sem pool, cada requisição HTTP abriria e fecharia uma conexão com o banco — latência típica de 20–100 ms por operação TCP + TLS. Com pool, a conexão já está estabelecida; o tempo de "empréstimo" é de microsegundos. Para um servidor que recebe múltiplas requisições simultâneas, isso representa a diferença entre escalar e travar.

**Ciclo de vida:**

```
Requisição HTTP
    │
    ▼
DatabaseConfig.getConnection()   ← retorna conexão do pool (não abre nova)
    │
    ▼
PreparedStatement / ResultSet    ← operação SQL
    │
    ▼
conn.close()                     ← devolve conexão ao pool (não fecha de fato)
    │
Shutdown: DatabaseConfig.closePool()   ← encerra todas as conexões de verdade
```

### 1.3 Integração com a API football-data.org

O sistema consome a API **football-data.org v4** para obter resultados em tempo real da Copa do Mundo 2026.

**Autenticação:**

Todo pedido HTTP inclui o cabeçalho:

```
X-Auth-Token: <token>
```

Esse modelo é chamado de *API Key Authentication* — mais simples que OAuth 2.0, sem necessidade de fluxo de autorização. O token é lido da variável de ambiente `FOOTBALL_DATA_TOKEN` (armazenada no arquivo `.env`), nunca hardcoded no código-fonte.

**Endpoints utilizados:**

| Endpoint | Dado retornado |
|---|---|
| `GET /v4/competitions/WC/standings` | Classificação por grupo |
| `GET /v4/competitions/WC/matches?status=SCHEDULED` | Próximas partidas agendadas |

**Limitação de taxa (rate limit):** 10 requisições/minuto no plano gratuito. O sistema mitiga isso com **cache em memória de 5 minutos** para cada endpoint:

```java
// Pseudocódigo do padrão de cache implementado
if (System.currentTimeMillis() - tsCache < 5 * 60 * 1000) {
    return cacheAnterior;   // resposta já armazenada
}
String resposta = chamarAPI();
cacheAnterior = resposta;
tsCache = System.currentTimeMillis();
return resposta;
```

**Pipeline completo de um dado de partida:**

```
football-data.org API
        │  JSON via HTTPS
        ▼
WebServer.java (parse Jackson ObjectMapper)
        │  Objeto Java Partida
        ▼
PartidaRepository.salvar(partida)
        │  INSERT SQL via JDBC
        ▼
PostgreSQL (tabela partida)
        │  SELECT posterior
        ▼
PredictionAlgorithm.prever(historico)
        │  Probabilidades calculadas
        ▼
/api/predicao/{id}   →   Dashboard HTML
```

### 1.4 Estrutura de Dados em Memória — B-Tree

A classe `AnalyticsService` mantém em memória uma **B-Tree** (árvore B) para navegação hierárquica `região → seleções`. Essa estrutura é carregada uma única vez na inicialização, eliminando joins ao banco em buscas de seleções por confederação.

**Por que B-Tree e não HashMap?**

Uma *HashMap* oferece busca O(1) por chave exata, mas não suporta travessias ordenadas eficientemente. A B-Tree garante:
- Busca por chave: $O(\log n)$
- Travessia em ordem: $O(n)$
- Inserção/remoção: $O(\log n)$

Em um índice de 6 confederações e ~200 seleções, a diferença prática é desprezível — mas a escolha demonstra o princípio de usar a estrutura de dados correta para o padrão de acesso esperado.

---

<a name="parte-2"></a>
## Parte 2 — Algoritmo de Busca: Interpolation Search

### 2.1 Motivação

Dado que o sistema pode armazenar históricos de centenas de partidas e precisar localizar uma por ID com frequência, a escolha do algoritmo de busca importa. A **busca por interpolação** é uma melhoria da busca binária clássica que explora a distribuição dos dados para fazer estimativas mais inteligentes de onde o elemento procurado provavelmente se encontra.

**Precondição obrigatória:** a lista deve estar **ordenada** pela chave numérica em ordem crescente. Sem isso, a fórmula de interpolação produz posições inválidas e o algoritmo pode entrar em loop infinito ou retornar resultados incorretos.

### 2.2 A Fórmula de Interpolação

Seja $A$ uma lista de $n$ elementos ordenados, com chaves $A[0] \leq A[1] \leq \cdots \leq A[n-1]$.

Dados os ponteiros `baixo` e `alto` delimitando o subespaço de busca atual, definimos:

$$K_{\text{baixo}} = A[\text{baixo}], \quad K_{\text{alto}} = A[\text{alto}]$$

A posição estimada para o alvo $x$ é:

$$\boxed{\text{pos} = \text{baixo} + \left\lfloor \frac{(x - K_{\text{baixo}}) \cdot (\text{alto} - \text{baixo})}{K_{\text{alto}} - K_{\text{baixo}}} \right\rfloor}$$

**Interpretação geométrica:** imagine que os valores das chaves são pontos no eixo horizontal e os índices são pontos no eixo vertical. A fórmula faz uma **interpolação linear** (regra de três) entre os extremos conhecidos $(\text{baixo}, K_{\text{baixo}})$ e $(\text{alto}, K_{\text{alto}})$ para estimar em qual índice o valor $x$ provavelmente está.

**Implementação Java (classe `InterpolationSearch.java`, linha 64):**

```java
int pos = baixo + ((alvo - chaveBaixo) * (alto - baixo))
                 / (chaveAlto - chaveBaixo);
```

**Caso degenerado:** se $K_{\text{baixo}} = K_{\text{alto}}$ (todos os elementos restantes têm o mesmo valor), a divisão seria por zero. O código trata isso explicitamente:

```java
if (chaveBaixo == chaveAlto) {
    return chave.applyAsInt(lista.get(baixo)) == alvo ? baixo : -1;
}
```

### 2.3 Prova de Corretude — Invariante de Laço

Para demonstrar que o algoritmo sempre termina corretamente, identificamos um **invariante de laço**: uma propriedade que é verdadeira antes e depois de cada iteração.

**Invariante:** Se o alvo $x$ existe na lista, então $A[\text{baixo}] \leq x \leq A[\text{alto}]$.

**Inicialização:** antes do laço, `baixo = 0` e `alto = n - 1`, logo o invariante é trivialmente satisfeito para qualquer lista não vazia.

**Manutenção:** a condição do `while` verifica explicitamente $x \geq A[\text{baixo}]$ e $x \leq A[\text{alto}]$, garantindo que o invariante é verificado antes de cada uso da fórmula. Após calcular `pos`:
- Se $A[\text{pos}] < x$: atualizamos `baixo = pos + 1`. O invariante é mantido porque sabemos que $x > A[\text{pos}]$, logo $x \geq A[\text{pos}+1]$.
- Se $A[\text{pos}] > x$: atualizamos `alto = pos - 1`. Simetricamente, $x \leq A[\text{pos}-1]$.

**Terminação:** a cada iteração, o espaço de busca $(\text{alto} - \text{baixo})$ estritamente diminui (pois `pos` nunca é igual a `baixo` nem a `alto` quando $K_{\text{baixo}} \neq K_{\text{alto}}$ e os limites são distintos). O laço termina quando o espaço de busca se esgota ou o elemento é encontrado.

### 2.4 Análise de Complexidade

#### Caso médio — O(log log n)

Quando os valores das chaves são **uniformemente distribuídos** (a hipótese de boas práticas), a interpolação localiza o alvo na fração correta do array em cada passo. Formalmente, se a fração de erro em cada estimativa é aproximadamente $1/\sqrt{n}$, o tamanho do espaço de busca evolui como:

$$n \to \sqrt{n} \to n^{1/4} \to n^{1/8} \to \cdots \to 1$$

O número de passos para chegar a 1 é $k$ tal que $n^{1/2^k} = 1$, ou seja:

$$\frac{1}{2^k} \log n = 0 \implies k = \log_2(\log_2 n)$$

Portanto, **complexidade média: $O(\log \log n)$**.

**Comparação prática** para $n = 10^6$:

| Algoritmo | Iterações esperadas |
|---|---|
| Busca linear | $\approx 500.000$ |
| Busca binária | $\approx \log_2(10^6) \approx 20$ |
| Busca por interpolação | $\approx \log_2(\log_2(10^6)) \approx 4{-}5$ |

#### Pior caso — O(n)

Se as chaves são **distribuídas de forma muito desigual** (ex.: $1, 2, 3, \ldots, 999, 10^9$), a interpolação subestima ou superestima a posição em cada passo, reduzindo o espaço de busca em apenas 1 elemento por vez — equivalente a uma busca linear.

#### Melhor caso — O(1)

Se a estimativa inicial acerta o elemento, o algoritmo retorna imediatamente.

### 2.5 Exemplo Passo a Passo

**Contexto:** buscar a partida com `id = 7` em uma lista de 8 partidas com IDs:

$$A = [1,\ 2,\ 4,\ 5,\ 7,\ 9,\ 11,\ 14]$$

**Passo 1:**
- $\text{baixo} = 0$, $\text{alto} = 7$
- $K_{\text{baixo}} = 1$, $K_{\text{alto}} = 14$, alvo $x = 7$

$$\text{pos} = 0 + \frac{(7 - 1) \cdot (7 - 0)}{14 - 1} = \frac{6 \cdot 7}{13} = \frac{42}{13} \approx 3 \quad \Rightarrow \text{pos} = 3$$

- $A[3] = 5 < 7$ → **busca na metade superior**: `baixo = 4`

**Passo 2:**
- $\text{baixo} = 4$, $\text{alto} = 7$
- $K_{\text{baixo}} = 7$, $K_{\text{alto}} = 14$, alvo $x = 7$

$$\text{pos} = 4 + \frac{(7 - 7) \cdot (7 - 4)}{14 - 7} = 4 + 0 = 4$$

- $A[4] = 7 = x$ → **ENCONTRADO** no índice 4 em apenas **2 iterações**.

Busca binária levaria $\lceil \log_2 8 \rceil = 3$ iterações para o mesmo caso.

### 2.6 Uso no Projeto

A classe `AnalyticsService` usa `InterpolationSearch.encontrar()` para localizar jogadores, partidas e seleções em listas previamente ordenadas por ID, garantindo que buscas frequentes no histórico de jogos sejam executadas em tempo sub-logarítmico.

```java
// Exemplo de uso em AnalyticsService
Jogador j = InterpolationSearch.encontrar(listaJogadores, idAlvo, Jogador::getId);
Partida p = InterpolationSearch.encontrar(listaPartidas, idPartida, Partida::getId);
```

---

<a name="parte-3"></a>
## Parte 3 — Algoritmo de Análise de Dados (DataKick Predictor — DKP)

O **DataKick Predictor (DKP)** é o coração analítico do sistema. Ele combina três camadas matemáticas — decaimento exponencial, cadeias de Markov e combinação bayesiana — para estimar as probabilidades do próximo jogo de uma seleção. O resultado do DKP alimenta também o **DataKick Moment Index (DMI)**, usado para comparações entre equipes.

### 3.1 Camada 1 — Decaimento Exponencial

**Problema:** jogos antigos devem pesar menos do que jogos recentes, pois o desempenho de uma seleção muda ao longo do tempo.

**Solução:** atribuir a cada jogo $i$ (com $i = 0$ sendo o mais antigo e $i = n-1$ o mais recente) um peso exponencialmente decrescente no passado:

$$w_i = e^{-\lambda \cdot (n - 1 - i)}, \quad \lambda = 0{,}18$$

Quanto maior $i$ (mais recente), menor é o expoente $(n-1-i)$, logo maior é $w_i$.

**Normalização:** para que os pesos somem 1 (formem uma distribuição de probabilidade), dividimos cada $w_i$ pela soma total:

$$\tilde{w}_i = \frac{w_i}{\sum_{j=0}^{n-1} w_j}$$

**Exemplo para $n = 5$ jogos e $\lambda = 0{,}18$:**

| Jogo $i$ | Expoente $(n-1-i)$ | $w_i = e^{-0.18 \cdot (n-1-i)}$ | $\tilde{w}_i$ (normalizado) |
|---|---|---|---|
| 0 (mais antigo) | 4 | $e^{-0.72} \approx 0.487$ | 0.145 |
| 1 | 3 | $e^{-0.54} \approx 0.583$ | 0.174 |
| 2 | 2 | $e^{-0.36} \approx 0.698$ | 0.208 |
| 3 | 1 | $e^{-0.18} \approx 0.835$ | 0.249 |
| 4 (mais recente) | 0 | $e^{0} = 1.000$ | 0.298 |
| **Soma** | | | **1.000** |

O jogo mais recente recebe quase o dobro do peso do jogo mais antigo, mas nenhum jogo é completamente descartado.

**Prior histórico ponderado:** usando os pesos normalizados, calculamos as probabilidades históricas de Vitória, Empate e Derrota:

$$P_V^{\text{hist}} = \sum_{i: R_i=V} \tilde{w}_i, \quad P_E^{\text{hist}} = \sum_{i: R_i=E} \tilde{w}_i, \quad P_D^{\text{hist}} = \sum_{i: R_i=D} \tilde{w}_i$$

E a **média ponderada de gols** (parâmetro $\lambda$ da distribuição de Poisson):

$$\lambda_{\text{pro}} = \sum_{i=0}^{n-1} \tilde{w}_i \cdot g_i^{\text{pro}}, \quad \lambda_{\text{contra}} = \sum_{i=0}^{n-1} \tilde{w}_i \cdot g_i^{\text{contra}}$$

### 3.2 Camada 2 — Cadeia de Markov

**Motivação:** o histórico ponderado captura a tendência geral, mas ignora o *momentum* recente. Uma seleção que venceu os últimos 3 jogos consecutivos pode ter uma probabilidade de vitória maior do que a média histórica sugere.

**Cadeia de Markov de ordem 1:** modela a sequência de resultados como um processo estocástico onde o próximo estado depende apenas do estado atual. Os três estados são:

$$\mathcal{S} = \{V, E, D\}$$

A **matriz de transição** $T$ é uma matriz $3 \times 3$ onde $T[i][j]$ representa a probabilidade de, após um resultado $i$, o próximo resultado ser $j$. Ela é estimada por contagem de frequências no histórico:

$$T[i][j] = \frac{\text{número de vezes que } i \text{ foi seguido de } j}{\text{número de vezes que o resultado } i \text{ ocorreu}}$$

**Exemplo:** se o histórico é $[V, V, E, D, V, E, V]$ (6 transições):
- Após V (ocorreu 3 vezes): V→V=1, V→E=1, V→D=1 → $T[V][*] = [1/3, 1/3, 1/3]$
- Após E (ocorreu 2 vezes): E→D=1, E→V=1 → $T[E][*] = [1/2, 0, 1/2]$
- Após D (ocorreu 1 vez): D→V=1 → $T[D][*] = [1, 0, 0]$

O **vetor de probabilidades Markov** para o próximo jogo é simplesmente a linha da matriz correspondente ao último resultado:

$$\mathbf{P}^{\text{Markov}} = T[\text{estado atual}]$$

**Tratamento de estados não observados:** se um resultado (ex.: E) nunca ocorreu, não há dados para estimar as transições a partir dele. O código usa um *fallback* uniforme:

```java
T[i][0] = T[i][1] = T[i][2] = 1.0 / 3.0;
```

### 3.3 Camada 3 — Combinação Bayesiana

As duas camadas anteriores fornecem duas estimativas independentes. A camada final as combina com pesos fixos $\alpha = 0{,}38$ (*peso Markov*):

$$\mathbf{P}^{\text{DKP}} = \alpha \cdot \mathbf{P}^{\text{Markov}} + (1 - \alpha) \cdot \mathbf{P}^{\text{hist}}$$

Explicitamente:

$$P_V = 0{,}38 \cdot T[\text{atual}][V] + 0{,}62 \cdot P_V^{\text{hist}}$$
$$P_E = 0{,}38 \cdot T[\text{atual}][E] + 0{,}62 \cdot P_E^{\text{hist}}$$
$$P_D = 0{,}38 \cdot T[\text{atual}][D] + 0{,}62 \cdot P_D^{\text{hist}}$$

Após o blend, a soma $P_V + P_E + P_D$ pode ser ligeiramente diferente de 1 por arredondamento. O código renormaliza:

$$P_V \leftarrow \frac{P_V}{P_V + P_E + P_D}, \quad \text{e analogamente para } P_E, P_D$$

**Interpretação bayesiana:** $\mathbf{P}^{\text{hist}}$ é o *prior* (crença anterior baseada no histórico completo), e $\mathbf{P}^{\text{Markov}}$ é a *likelihood* (evidência do estado atual). A combinação linear simula um *posterior* sem precisar calcular o denominador bayesiano explicitamente.

### 3.4 Função de Confiança

O modelo é mais confiável com mais dados. A **confiança** cresce com o número de partidas $n$ e nunca atinge 1:

$$c(n) = 1 - e^{-0{,}12 \cdot n}$$

Valores típicos:

| $n$ (partidas) | $c(n)$ |
|---|---|
| 1 | 11,3% |
| 5 | 45,1% |
| 10 | 69,9% |
| 20 | 90,9% |
| 30 | 97,2% |

O comportamento assintótico $c \to 1$ quando $n \to \infty$ é matematicamente garantido, mas na prática o modelo é considerado "confiável" a partir de ~10 partidas.

### 3.5 Aproveitamento Recente

O aproveitamento dos últimos $\min(5, n)$ jogos é calculado usando a escala de pontos do futebol (V=3, E=1, D=0), normalizada pelo máximo possível:

$$A_{\text{rec}} = \frac{\sum_{i=n-5}^{n-1} \text{pts}(R_i)}{5 \cdot 3} = \frac{\sum_{i=n-5}^{n-1} \text{pts}(R_i)}{15}$$

Esse valor $A_{\text{rec}} \in [0, 1]$ é usado como um dos componentes do DMI (Seção 3.6).

### 3.6 DataKick Moment Index (DMI)

O DKP gera probabilidades para uma única seleção em isolamento. Para **comparar duas seleções em um confronto**, o sistema calcula o **DataKick Moment Index (DMI)**, que condensa o estado atual de uma equipe em um único número $m \in [0, 1]$.

**Fórmula base:**

$$r_b = 0{,}35 \cdot A_{\text{rec}} + 0{,}35 \cdot P_V + 0{,}20 \cdot \tanh\!\left(\frac{\lambda_{\text{pro}}}{2}\right) + 0{,}10 \cdot \left(1 - \tanh\!\left(\frac{\lambda_{\text{contra}}}{2}\right)\right)$$

**Justificativa de cada peso:**

| Componente | Peso | Significado | Por que esse peso |
|---|---|---|---|
| $A_{\text{rec}}$ | 0,35 | Forma nos últimos 5 jogos | Maior preditor de curto prazo; captura momentum |
| $P_V$ | 0,35 | Probabilidade DKP de vitória | Resume toda a análise histórica ponderada + Markov |
| $\tanh(\lambda_{\text{pro}}/2)$ | 0,20 | Potência ofensiva normalizada | Gols esperados comprimidos em $[0,1]$ pela tangente hiperbólica |
| $1 - \tanh(\lambda_{\text{contra}}/2)$ | 0,10 | Solidez defensiva | Complemento: quanto menos gols sofre, mais próximo de 1 |

**Por que $\tanh$ para normalizar gols?**

A tangente hiperbólica $\tanh(x) = \frac{e^x - e^{-x}}{e^x + e^{-x}}$ tem as propriedades:
- $\tanh(0) = 0$, $\tanh(x) \to 1$ quando $x \to \infty$
- Crescimento rápido para valores baixos, saturação para valores altos

Para $\lambda_{\text{pro}} = 2$ (média de 2 gols por jogo): $\tanh(1) \approx 0{,}76$ — considerado "bom".
Para $\lambda_{\text{pro}} = 4$ (muito ofensivo): $\tanh(2) \approx 0{,}96$ — muito bom, mas não exagerado.

Isso evita que equipes excepcionalmente ofensivas dominem completamente o índice.

**Ajuste de confiança (regressão ao neutro):**

Com poucos dados, o índice $r_b$ pode ser distorcido. O ajuste final "puxa" o índice em direção ao valor neutro $0{,}5$:

$$\boxed{m = c \cdot r_b + (1 - c) \cdot 0{,}5}$$

Com $c = 0$ (sem dados): $m = 0{,}5$ (neutro, nenhuma informação).  
Com $c = 1$ (muito dados): $m = r_b$ (confiança total na estimativa).

### 3.7 Probabilidades do Confronto

Dados os índices $m_C$ (time da casa) e $m_F$ (time de fora), o sistema converte a diferença de momento em probabilidades de resultado:

**Passo 1 — Probabilidade bruta de vitória da casa:**

A função **sigmoide** é usada para mapear a diferença de momento para uma probabilidade em $(0, 1)$:

$$P_{\text{vit. casa}}^{\text{bruto}} = \sigma(6 \cdot \Delta) = \frac{1}{1 + e^{-6 \cdot (m_C - m_F)}}$$

O fator 6 controla a "inclinação" da sigmoide: com $|\Delta| = 0{,}2$, a probabilidade de vitória já é $\approx 77\%$. Sem o fator, a distinção seria muito suave.

**Passo 2 — Probabilidade de empate:**

$$P_E^{\text{bruto}} = 0{,}28 \cdot (1 - |\Delta|), \quad \text{limitado a } [5\%, 40\%]$$

Quando $\Delta = 0$ (equipes equivalentes): empate base de 28%, valor empiricamente comum no futebol.
Quando $|\Delta| = 1$ (máximo teórico): empate cai a 0%, mas o `clamp` garante no mínimo 5%.

**Passo 3 — Distribuição final (normalizada):**

$$P_C = P_{\text{vit. casa}}^{\text{bruto}} \cdot (1 - P_E), \quad P_F = (1 - P_{\text{vit. casa}}^{\text{bruto}}) \cdot (1 - P_E)$$

$$\Sigma = P_C + P_E + P_F, \quad \tilde{P}_C = \frac{P_C}{\Sigma}, \quad \tilde{P}_E = \frac{P_E}{\Sigma}, \quad \tilde{P}_F = \frac{P_F}{\Sigma}$$

### 3.8 Exemplo Numérico Completo (DKP → DMI → Confronto)

**Seleção A** — 5 partidas: V, E, D, V, V

**Passo 1 — Pesos ($\lambda = 0{,}18$, $n = 5$):**

$w_4 = 1{,}000$, $w_3 = 0{,}835$, $w_2 = 0{,}698$, $w_1 = 0{,}583$, $w_0 = 0{,}487$ → soma = 3{,}603

Normalizados: $\tilde{w}_4 = 0{,}278$, $\tilde{w}_3 = 0{,}232$, $\tilde{w}_2 = 0{,}194$, $\tilde{w}_1 = 0{,}162$, $\tilde{w}_0 = 0{,}135$

**Passo 2 — Prior histórico:**

$P_V^{\text{hist}} = \tilde{w}_0 + \tilde{w}_3 + \tilde{w}_4 = 0{,}135 + 0{,}232 + 0{,}278 = 0{,}645$

$P_E^{\text{hist}} = \tilde{w}_1 = 0{,}162$

$P_D^{\text{hist}} = \tilde{w}_2 = 0{,}194$

**Passo 3 — Cadeia de Markov** (transições de V,E,D,V,V):

- V→E: 1×, V→V: 1× (após V ocorreu 2 vezes no histórico de transições)
- E→D: 1×
- D→V: 1×

$T[V] = [0{,}5,\ 0{,}5,\ 0{,}0]$ (após V: 50% V, 50% E)

Estado atual = V, logo $\mathbf{P}^{\text{Markov}} = [0{,}5, 0{,}5, 0{,}0]$

**Passo 4 — Blend bayesiano ($\alpha = 0{,}38$):**

$P_V = 0{,}38 \times 0{,}5 + 0{,}62 \times 0{,}645 = 0{,}190 + 0{,}400 = 0{,}590$

$P_E = 0{,}38 \times 0{,}5 + 0{,}62 \times 0{,}162 = 0{,}190 + 0{,}100 = 0{,}290$

$P_D = 0{,}38 \times 0{,}0 + 0{,}62 \times 0{,}194 = 0{,}000 + 0{,}120 = 0{,}120$

Soma = 1,000 → já normalizado.

**Passo 5 — Confiança e aproveitamento recente:**

$c = 1 - e^{-0{,}12 \times 5} = 1 - e^{-0{,}6} \approx 0{,}451$ (45,1%)

$A_{\text{rec}} = \frac{3 + 3 + 1}{15} = \frac{7}{15} \approx 0{,}467$ (últimos 3 jogos computados: D,V,V com pontos 0+3+3=6... note que todos 5 são usados: V,E,D,V,V → 3+1+0+3+3=10 pts / 15 max = 0,667)

Corrigindo: últimos $\min(5,5) = 5$ jogos, V+E+D+V+V = 3+1+0+3+3 = 10 pts → $A_{\text{rec}} = 10/15 \approx 0{,}667$

Assumindo $\lambda_{\text{pro}} = 1{,}8$ e $\lambda_{\text{contra}} = 0{,}9$:

$r_b = 0{,}35 \times 0{,}667 + 0{,}35 \times 0{,}590 + 0{,}20 \times \tanh(0{,}9) + 0{,}10 \times (1 - \tanh(0{,}45))$

$= 0{,}233 + 0{,}207 + 0{,}20 \times 0{,}716 + 0{,}10 \times (1 - 0{,}422)$

$= 0{,}233 + 0{,}207 + 0{,}143 + 0{,}058 = 0{,}641$

$m_A = 0{,}451 \times 0{,}641 + 0{,}549 \times 0{,}5 = 0{,}289 + 0{,}275 = \mathbf{0{,}564}$

**Seleção B** — suponha $m_B = 0{,}480$

**Confronto A (casa) × B (fora):**

$\Delta = 0{,}564 - 0{,}480 = 0{,}084$

$P_{\text{vit. casa}} = \frac{1}{1 + e^{-6 \times 0{,}084}} = \frac{1}{1 + e^{-0{,}504}} \approx \frac{1}{1 + 0{,}604} \approx 0{,}623$

$P_E = 0{,}28 \times (1 - 0{,}084) = 0{,}28 \times 0{,}916 \approx 0{,}257$ (dentro do clamp)

$P_C = 0{,}623 \times (1 - 0{,}257) = 0{,}623 \times 0{,}743 = 0{,}463$

$P_F = 0{,}377 \times 0{,}743 = 0{,}280$

$\Sigma = 0{,}463 + 0{,}257 + 0{,}280 = 1{,}000$

**Resultado final:** A vence 46,3% | Empate 25,7% | B vence 28,0%

---

<a name="perguntas"></a>
## Perguntas que o Professor Pode Fazer

### Sobre Banco de Dados

**Q1.** O que acontece se dois usuários tentarem sincronizar a mesma seleção ao mesmo tempo? Como o HikariCP e o PostgreSQL lidam com isso?

> **Resposta esperada:** O HikariCP distribui as requisições entre conexões do pool. Se as duas transações tentarem fazer `INSERT` da mesma seleção e houver uma *UNIQUE constraint* em `(nome, regiao_id)`, o PostgreSQL garantirá que apenas uma terá sucesso; a outra receberá um erro de violação de unicidade. O código em `SelecaoRepository.salvar()` usa um padrão *SELECT-then-INSERT* para verificar a existência antes de inserir, mas esse padrão tem uma condição de corrida (*race condition*) que só é completamente resolvida com `INSERT ... ON CONFLICT DO NOTHING` ou com uma transação isolada.

**Q2.** Por que separar `temporada` de `selecao` em vez de armazenar o ano diretamente na tabela de `partida`?

> **Resposta esperada:** A separação garante a **Terceira Forma Normal (3NF)**: o ano da temporada é um atributo que depende da combinação `(seleção, ano)`, não de cada partida individualmente. Além disso, permite filtrar rapidamente todas as partidas de uma seleção em um ano específico sem precisar varrer toda a tabela `partida`.

**Q3.** Calcule quantas conexões simultâneas o sistema pode suportar com `maximumPoolSize = 10` se cada requisição HTTP demora em média 50 ms no banco.

> **Resposta esperada:** Pelo teorema de Little ($L = \lambda W$), com 10 conexões disponíveis e tempo médio de 50 ms por consulta, a taxa de throughput máxima é $10 / 0{,}05 = 200$ requisições por segundo antes de começar a fila de espera.

### Sobre Interpolation Search

**Q4.** Explique por que a busca por interpolação pode ser mais lenta que a binária em alguns casos.

> **Resposta esperada:** Quando os dados não são uniformemente distribuídos, a estimativa de posição pode ser sistematicamente errada. Exemplo extremo: array $[1, 2, 3, 4, 5, \ldots, 99, 1.000.000]$. Ao buscar 50, a interpolação estimará uma posição próxima do início (pois 50 está muito próximo do mínimo relativo ao máximo), mas o elemento está no meio. Cada iteração pode reduzir o espaço em apenas 1 elemento, levando a $O(n)$ iterações.

**Q5.** O que acontece com a fórmula de interpolação se `chaveBaixo == chaveAlto`? Por que o código trata esse caso especialmente?

> **Resposta esperada:** A fórmula teria divisão por zero, pois o denominador $K_{\text{alto}} - K_{\text{baixo}} = 0$. Matematicamente, isso significa que todos os elementos restantes no subespaço têm o mesmo valor — portanto, ou o alvo é igual a esse valor, ou não existe na lista. O código verifica diretamente: se o valor coincide, retorna o índice; caso contrário, retorna -1.

**Q6.** Prove que a complexidade média da busca por interpolação é $O(\log \log n)$ sob distribuição uniforme.

> **Resposta esperada:** Sob distribuição uniforme $U[a,b]$, o erro de estimativa da posição em cada passo é proporcional ao desvio padrão da distribuição uniforme no subintervalo. A análise formal (Yao e Yao, 1976) mostra que o tamanho esperado do espaço de busca após $k$ passos é $n^{(1/2)^k}$, que chega a 1 quando $k = \log_2 \log_2 n$, logo $O(\log \log n)$.

**Q7.** Como você adaptaria a Interpolation Search para uma lista de objetos `Partida` ordenada por data, em vez de por ID inteiro?

> **Resposta esperada:** Seria necessário converter as datas em timestamps inteiros (ex.: `LocalDate.toEpochDay()`) e usar uma função `ToLongFunction` em vez de `ToIntFunction`. A fórmula de interpolação continua válida desde que os valores sejam numericamente comparáveis e a lista esteja ordenada.

### Sobre o Algoritmo DKP

**Q8.** Por que o decaimento exponencial é preferível a uma janela deslizante de $k$ jogos fixos?

> **Resposta esperada:** Uma janela deslizante desconsidera completamente jogos fora da janela, causando descontinuidade: o jogo de 6 partidas atrás tem peso idêntico ao de 1 partida atrás, e o de 7 partidas atrás tem peso zero. O decaimento exponencial cria uma transição suave, onde nenhum jogo é completamente ignorado, mas jogos recentes têm peso proporcionalmente maior. Isso é mais robusto a tamanhos de histórico variáveis.

**Q9.** O Bayesian Blend usa $\alpha = 0{,}38$ para o peso Markov. Que problema ocorreria se $\alpha = 1{,}0$?

> **Resposta esperada:** Com $\alpha = 1$, a predição dependeria *exclusivamente* da transição Markov do estado atual. Se a seleção perdeu o último jogo e a linha $T[D]$ for baseada em poucas transições, a estimativa teria alta variância. Além disso, para seleções com poucos jogos, a matriz $T$ seria muito esparsa e não confiável. O prior histórico ponderado ($1 - \alpha = 0{,}62$) ancora a predição em um contexto mais amplo.

**Q10.** A confiança $c(n) = 1 - e^{-0{,}12n}$ foi definida empiricamente. Como você a redesenharia se quisesse que a confiança atingisse 90% com apenas 10 partidas?

> **Resposta esperada:** Resolver $0{,}90 = 1 - e^{-k \cdot 10}$: $e^{-10k} = 0{,}10$ → $-10k = \ln(0{,}1) \approx -2{,}303$ → $k \approx 0{,}230$. Substituir `0.12` por `0.23` no código.

**Q11.** Qual é o valor de $\tanh(\lambda/2)$ quando uma seleção marca em média 0 gols por jogo? E 3 gols por jogo? O que isso implica para o componente ofensivo do DMI?

> **Resposta esperada:** $\tanh(0/2) = \tanh(0) = 0$ (sem ofensividade). $\tanh(3/2) = \tanh(1{,}5) \approx 0{,}905$ (muito ofensivo). O componente ofensivo do DMI valeria $0{,}20 \times 0 = 0$ e $0{,}20 \times 0{,}905 = 0{,}181$, respectivamente. A saturação da $\tanh$ evita que marcar 10 gols por jogo domine completamente o índice.

**Q12.** Se $m_C = m_F = 0{,}5$ (equipes perfeitamente equivalentes), quais são as probabilidades do confronto?

> **Resposta esperada:** $\Delta = 0$, logo $\sigma(0) = 0{,}5$. $P_E = 0{,}28 \times 1 = 0{,}28$. $P_C = 0{,}5 \times 0{,}72 = 0{,}36$. $P_F = 0{,}5 \times 0{,}72 = 0{,}36$. Soma = 1,00. Resultado: vitória casa 36%, empate 28%, vitória fora 36% — simétrico, como esperado.

**Q13.** Por que o componente defensivo usa $(1 - \tanh(\lambda_{\text{contra}}/2))$ em vez de simplesmente $-\lambda_{\text{contra}}$?

> **Resposta esperada:** Usar $-\lambda_{\text{contra}}$ produziria valores negativos para seleções que sofrem muitos gols, e o DMI ficaria fora de $[0,1]$. O complemento $1 - \tanh(\cdot)$ permanece em $[0,1]$: defesas que sofrem 0 gols obtêm 1; defesas que sofrem muitos gols obtêm próximo de 0. Mantém a normalização do índice composto.

**Q14.** O que acontece com o DMI de uma seleção que nunca jogou na Copa ($n = 0$)?

> **Resposta esperada:** Com $n = 0$, a confiança $c = 1 - e^0 = 0$. O ajuste de confiança resulta em $m = 0 \cdot r_b + 1 \cdot 0{,}5 = 0{,}5$. O índice é exatamente neutro, independentemente do cálculo de $r_b$. No frontend, a flag `semDados` é ativada e as probabilidades de confronto recaem em $[1/3, 1/3, 1/3]$ (prior uniforme).

**Q15.** Um crítico diz que o DMI é "pseudociência" porque os pesos 0,35 / 0,35 / 0,20 / 0,10 foram escolhidos empiricamente, não derivados de dados. Como você responderia?

> **Resposta esperada:** O crítico tem razão ao apontar a limitação. A resposta honesta é que o DMI é um modelo *heurístico*, não um modelo estatístico ajustado a dados. Pesos ideais seriam obtidos por *regressão logística* ou *gradient boosting* sobre milhares de partidas históricas com o resultado real como variável dependente. Os pesos atuais representam uma escolha razoável baseada em conhecimento de domínio (forma recente e probabilidade DKP são os preditores mais fortes), mas não foram validados formalmente. O sistema seria mais robusto com um conjunto de treino e métricas de avaliação (ex.: Brier Score, log-loss).

**Q16.** Explique o trade-off entre os pesos da Cadeia de Markov ($\alpha = 0{,}38$) e do prior histórico ($1-\alpha = 0{,}62$). Em qual situação você aumentaria $\alpha$?

> **Resposta esperada:** Aumentar $\alpha$ faz o modelo reagir mais rapidamente ao momentum recente, mas com maior variância (mais sensível a resultados individuais). É adequado para torneios curtos onde o "estado emocional" recente importa mais (ex.: fase eliminatória). Diminuir $\alpha$ torna o modelo mais estável e baseado em tendências de longo prazo, adequado para temporadas longas. O valor 0,38 tenta equilibrar reatividade e estabilidade.

---

<a name="glossario"></a>
## Glossário

| Termo | Definição |
|---|---|
| **API (Application Programming Interface)** | Interface que define como sistemas trocam dados; neste projeto, o endpoint REST do football-data.org que fornece resultados em JSON |
| **Bayesian Blend** | Combinação ponderada de uma crença prévia (*prior*) com evidência recente (*likelihood*), produzindo uma estimativa balanceada |
| **B-Tree (Árvore B)** | Estrutura de dados balanceada em árvore que garante $O(\log n)$ para busca, inserção e remoção; usada no `AnalyticsService` para indexar seleções por região |
| **Cache** | Armazenamento temporário de resultados de operações custosas; o sistema usa cache em memória RAM com TTL de 5 minutos para as respostas das APIs externas |
| **Cadeia de Markov** | Processo estocástico onde a probabilidade do próximo estado depende apenas do estado atual, não dos anteriores (*propriedade de Markov*) |
| **Connection Pool** | Conjunto pré-alocado de conexões com o banco de dados, reutilizadas entre requisições para evitar overhead de abertura/fechamento |
| **DKP (DataKick Predictor)** | Algoritmo proprietário que combina decaimento exponencial, Markov e Bayesian blend para predizer resultados de partidas |
| **DMI (DataKick Moment Index)** | Índice composto em $[0,1]$ que condensa o momento atual de uma seleção; alimenta o cálculo de probabilidades de confronto |
| **Decaimento Exponencial** | Ponderação $w_i = e^{-\lambda (n-1-i)}$ que reduz a influência de jogos antigos de forma contínua |
| **HikariCP** | Biblioteca Java de *connection pooling* reconhecida por baixa latência e alto throughput |
| **Interpolação Linear** | Estimativa de um valor desconhecido entre dois pontos conhecidos usando proporcionalidade direta |
| **Interpolation Search** | Algoritmo de busca que usa interpolação linear para estimar a posição do alvo em vez de biseccionar; $O(\log \log n)$ médio |
| **JDBC** | Java Database Connectivity — API padrão do Java para comunicação com bancos relacionais via SQL |
| **Lambda (λ)** | Parâmetro de taxa em distribuições exponenciais e de Poisson; no DKP, representa a taxa de decaimento temporal (0,18) e a média de gols esperados por jogo |
| **Normalização (pesos)** | Transformação que faz uma lista de números positivos somar 1, convertendo contagens em probabilidades |
| **Parâmetro de Poisson (λ)** | Média esperada de eventos em um intervalo; aqui, a média ponderada de gols marca a intensidade ofensiva esperada |
| **Rate Limit** | Limite de requisições impostos pela API; football-data.org permite 10 req/min no plano gratuito |
| **Renormalização** | Divisão de todas as probabilidades pela sua soma, garantindo $P_V + P_E + P_D = 1$ após operações algébricas |
| **Sigmoide ($\sigma$)** | Função $\sigma(x) = \frac{1}{1+e^{-x}}$ que mapeia qualquer real para $(0,1)$; usada para converter diferença de momento em probabilidade |
| **Tanh (tangente hiperbólica)** | Função $\tanh(x) = \frac{e^x - e^{-x}}{e^x + e^{-x}}$ que mapeia reais para $(-1,1)$; no DMI usada no intervalo $[0,\infty)$ para compressão não-linear de gols |
| **TTL (Time To Live)** | Tempo máximo que um dado em cache é considerado válido antes de ser recalculado; no sistema, 5 minutos |
| **Invariante de Laço** | Propriedade lógica que permanece verdadeira antes e depois de cada iteração de um laço; usada para provar corretude de algoritmos |
| **Prior (bayesiano)** | Distribuição de probabilidade representando a crença *antes* de observar nova evidência |
| **Posterior (bayesiano)** | Distribuição de probabilidade atualizada *após* incorporar nova evidência ao prior |
