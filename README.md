# ⚽ Football Analysis System

Sistema de análise de dados de futebol com **Árvore B** e **Interpolation Search**, integrado à **API-Football (RapidAPI)** e banco de dados **PostgreSQL**.

---

## 🏗️ Arquitetura

```
API-Football (RapidAPI)
        │
        ▼
 DataSyncService          ← orquestra a ingestão de dados
        │
        ▼
 PostgreSQL (BTree interno)
        │
   Hierarquia:
   REGIAO → SELECAO → TEMPORADA → JOGADOR → PARTIDA
        │
        ▼
 AnalyticsService
   ├── BTree<Integer, List<Selecao>>  ← navegação hierárquica O(log n)
   └── InterpolationSearch            ← busca por ID/número O(log log n)
        │
        ▼
    Relatórios / Análises
```

---

## 📁 Estrutura de Pacotes

```
src/main/java/com/football/
├── Main.java                          ← ponto de entrada
├── api/
│   └── ApiFootballClient.java         ← cliente HTTP (OkHttp)
├── algorithm/
│   ├── BTree.java                     ← Árvore B genérica
│   └── InterpolationSearch.java       ← busca por interpolação
├── config/
│   └── DatabaseConfig.java            ← pool HikariCP + PostgreSQL
├── model/
│   ├── Regiao.java
│   ├── Selecao.java
│   ├── Temporada.java
│   ├── Jogador.java
│   ├── Partida.java
│   └── Estatistica.java
├── repository/
│   └── Repositories.java              ← CRUD para cada entidade
└── service/
    ├── DataSyncService.java            ← API → Banco de dados
    └── AnalyticsService.java          ← análises + buscas
```

---

## ⚙️ Como Configurar

### 1. Banco de dados
```sql
-- Execute no PostgreSQL:
psql -U postgres -d football_db -f src/main/resources/schema.sql
```

### 2. Variáveis de ambiente
```bash
cp .env.example .env
# Edite .env com suas credenciais
```

### 3. Compilar e executar
```bash
mvn clean package
java -jar target/football-analysis-1.0.0.jar
```

---

## 🔍 Algoritmos

### Árvore B (`BTree.java`)
- Grau mínimo `t = 3`
- Cada nó armazena entre `t-1` e `2t-1` chaves
- Usada para navegação hierárquica: dado um `regiaoId`, retorna as seleções em O(log n)

### Interpolation Search (`InterpolationSearch.java`)
| Caso | Complexidade |
|------|-------------|
| Melhor | O(1) |
| Médio (distribuição uniforme) | O(log log n) |
| Pior | O(n) |

**Pré-requisito**: a lista deve estar **ordenada** pela chave numérica.

**Fórmula**:
```
pos = baixo + ((alvo - arr[baixo]) × (alto - baixo)) / (arr[alto] - arr[baixo])
```

---

## 📊 Análises Disponíveis

| Análise | Método |
|---------|--------|
| Top artilheiros da temporada | `topArtilheiros(temporadaId, top)` |
| Buscar jogador por ID | `buscarJogadorPorId(selecaoId, jogadorId)` |
| Buscar partida por API-ID | `buscarPartidaPorApiId(temporadaId, apiId)` |
| Desempenho de uma seleção | `desempenhoSelecao(selecaoId, temporadaId)` |
| Navegar hierarquia (Árvore B) | `navegarPorRegiao(regiaoId)` |
| Relatório completo | `gerarRelatorio(selecaoId, temporadaId)` |

---

## 🗄️ Hierarquia do Banco (Árvore B)

```
REGIAO (ex: América do Sul)
  └── SELECAO (ex: Brasil, Copa Libertadores)
        └── TEMPORADA (ex: 2024)
              ├── JOGADOR (ex: Vinicius Jr.)
              │     └── ESTATISTICA (gols, assist., etc.)
              └── PARTIDA (Brasil 3x0 Bolívia)
                    └── PARTIDA_JOGADOR (performance individual)
```

---

## 📦 Dependências

| Lib | Versão | Uso |
|-----|--------|-----|
| PostgreSQL JDBC | 42.7.3 | Driver do banco |
| HikariCP | 5.1.0 | Pool de conexões |
| OkHttp | 4.12.0 | Requisições HTTP |
| Jackson | 2.17.1 | Parse JSON |
| dotenv-java | 3.0.0 | Variáveis de ambiente |
