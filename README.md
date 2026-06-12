# DataKick — Football Analysis

Sistema de análise preditiva de futebol para a **Copa do Mundo 2026**, com dashboard web em tempo real, algoritmo DKP proprietário e sincronização automática de dados.

**Stack:** Java 25 · PostgreSQL · HikariCP · OkHttp · Jackson · Maven

---

## Arquitetura

```
football-data.org v4 API
        │  HTTPS (X-Auth-Token)
        ▼
ApiFootballClient.java         ← HTTP + parse Jackson
        │
        ├─→ DataSyncService.java    ← ingestão manual/auto: API → banco
        │         │  JDBC
        │         ▼
        │   PostgreSQL (DataKick)
        │   REGIAO → SELECAO → TEMPORADA → PARTIDA
        │
        └─→ WebServer.java          ← servidor HTTP embutido (porta 8080)
                  │
                  ├── /api/copa        → banco (sem cache)
                  ├── /api/predicao    → banco + DKP (sem cache) + auto-sync
                  ├── /api/grupos      → API ao vivo (cache 5 min)
                  ├── /api/proximos    → API ao vivo (cache 5 min)
                  ├── /api/confrontos  → API + banco + DMI (cache 5 min)
                  ├── /api/status      → times em sincronização automática
                  └── /              → index.html (dashboard SPA)
```

---

## Estrutura de Pacotes

```
src/main/java/com/football/
├── Main.java                        ← CLI (menu) e modo --web
├── api/
│   └── ApiFootballClient.java       ← cliente HTTP, NAC_IDS, rate limit
├── algorithm/
│   ├── BTree.java                   ← árvore B genérica (t=3)
│   ├── InterpolationSearch.java     ← busca O(log log n) em listas ordenadas
│   └── PredictionAlgorithm.java     ← DKP: Markov + decaimento + Bayesian
├── config/
│   └── DatabaseConfig.java          ← pool HikariCP
├── model/
│   ├── Regiao.java  Selecao.java  Temporada.java
│   ├── Jogador.java  Partida.java  Estatistica.java
│   └── PartidaJogador.java
├── repository/
│   └── Repositories.java            ← CRUD para cada entidade (inner classes)
├── service/
│   ├── DataSyncService.java         ← orquestra sync API → banco
│   └── AnalyticsService.java        ← B-Tree + relatórios
└── web/
    └── WebServer.java               ← HTTP server + endpoints + auto-sync
```

---

## Configuração

### 1. Banco de dados

```sql
psql -U postgres -c "CREATE DATABASE datakick;"
psql -U postgres -d datakick -f src/main/resources/schema.sql
```

### 2. Variáveis de ambiente

Crie `.env` na raiz do projeto:

```
FOOTBALL_DATA_TOKEN=seu_token_aqui
DB_HOST=localhost
DB_PORT=5432
DB_NAME=DataKick
DB_USER=postgres
DB_PASSWORD=sua_senha
```

Token gratuito em: https://www.football-data.org/client/register

### 3. Compilar

```bash
mvn package -DskipTests
```

---

## Como Executar

### Modo web (dashboard)

```bash
java -jar target/football-analysis-1.0.0.jar --web
```

Abre `http://localhost:8080` no navegador.

### Modo CLI (menu interativo)

```bash
java -jar target/football-analysis-1.0.0.jar
```

| Opção | Ação |
|-------|------|
| 1 | Sincronizar uma seleção manualmente |
| 9 | Predição DKP para uma seleção |
| 10 | Sincronizar todas as 48 seleções da Copa 2026 |

---

## Dashboard

Quatro abas no navegador:

| Aba | Conteúdo |
|-----|----------|
| Seleções | Grid por confederação, stats Copa 2026, botão de predição DKP |
| Próximos Jogos | 5 próximas partidas com modal de análise comparativa |
| Análise DKP | Todos os jogos agendados + DataKick Moment Index inline |
| Grupos | Classificação ao vivo por grupo (standings da API) |

---

## Algoritmos

### DKP — DataKick Predictor (`PredictionAlgorithm.java`)

Combina três modelos em sequência:

**1. Decaimento exponencial** — pondera partidas mais recentes com peso maior:

```
w_i = e^(−0.18 × (n−1−i))    pesos normalizados: w̃_i = w_i / Σw_j
λ   = Σ w̃_i × gols_i         gols esperados (parâmetro Poisson)
```

**2. Cadeia de Markov** — probabilidades de transição V→V, V→E, V→D, etc. a partir do histórico. Peso Markov: α = 0.38 na blend final.

**3. Blend Bayesiana** — combina os dois modelos ponderando pela confiança:

```
c(n) = 1 − e^(−0.12·n)       confiança cresce com nº de partidas
                               c(5)≈45%  c(10)≈70%  c(30)≈97%
```

**DMI — DataKick Moment Index** (usado nos confrontos):

```
rb     = 0.35×aprovRec + 0.35×probVitoria + 0.20×tanh(λ/2) + 0.10×(1−tanh(λS/2))
momento = c(n) × rb + (1 − c(n)) × 0.5
```

Probabilidades do confronto via sigmoid: `pVitCasa = sigmoid(6 × (momCasa − momFora))`

### B-Tree (`BTree.java`)

Grau mínimo t=3. Mantida em memória por `AnalyticsService` para navegação hierárquica `região → seleções` sem joins ao banco na inicialização.

### Interpolation Search (`InterpolationSearch.java`)

```
pos = baixo + ⌊(alvo − A[baixo]) × (alto − baixo) / (A[alto] − A[baixo])⌋
```

| Caso | Complexidade |
|------|-------------|
| Médio (distribuição uniforme) | O(log log n) |
| Pior | O(n) |

---

## Auto-Sync

Quando a API de standings mostra que um time disputou jogos (`playedGames > 0`) mas o banco local ainda não tem esses dados, o sistema sincroniza automaticamente em background — sem intervenção manual.

**Fluxo:**

```
/api/grupos → standings atualiza apiJogosPlayed e apiTeamIdPorTla
                    │
                    ▼
       verificarEAutoSincronizar()   ← executor single-thread (daemon)
                    │
              para cada TLA com jogosAPI > jogosDB:
                    │
                    ▼
       DataSyncService.sincronizar(regiao, nome, tla, 0, teamId, 2026)
                    │
                    ▼
       cacheConfrontos = null   ← força rebuild com dados novos
```

**No frontend:**
- `/api/predicao/{id}` retorna `{sincronizando: true}` enquanto o sync está em andamento
- Modal exibe "⚙️ Calculando predição DKP..." com retry automático a cada 3 segundos
- `/api/status` expõe os TLAs atualmente em sincronização

---

## API Endpoints

| Endpoint | Cache | Fonte | Descrição |
|----------|-------|-------|-----------|
| `GET /api/copa` | Sem cache | Banco | Seleções + stats Copa 2026 por confederação |
| `GET /api/predicao/{id}` | Sem cache | Banco + DKP | Predição completa de uma seleção |
| `GET /api/grupos` | 5 min | API ao vivo | Classificação por grupo |
| `GET /api/proximos` | 5 min | API ao vivo | Próximas 5 partidas |
| `GET /api/confrontos` | 5 min | API + Banco | Todos os jogos + DMI + probabilidades |
| `GET /api/status` | Sem cache | Memória | TLAs atualmente em auto-sync |

---

## Dependências

| Lib | Versão | Uso |
|-----|--------|-----|
| PostgreSQL JDBC | 42.7.3 | Driver do banco |
| HikariCP | 5.1.0 | Pool de conexões |
| OkHttp | 4.12.0 | Requisições HTTP |
| Jackson | 2.17.1 | Parse/serialização JSON |
| dotenv-java | 3.0.0 | Leitura do `.env` |
