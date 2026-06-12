-- ============================================================
--  SCHEMA - Football Analysis System
--  Banco: PostgreSQL
--  Hierarquia (Árvore B): REGIAO → SELECAO → TEMPORADA → JOGADOR → PARTIDA
-- ============================================================

-- Extensão para UUID (opcional, mas útil)
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- ============================================================
--  1. REGIAO  (raiz da hierarquia)
-- ============================================================
CREATE TABLE IF NOT EXISTS regiao (
    id   SERIAL PRIMARY KEY,
    nome VARCHAR(100) NOT NULL UNIQUE   -- ex: "América do Sul"
);

-- ============================================================
--  2. SELECAO  (filho de REGIAO)
-- ============================================================
CREATE TABLE IF NOT EXISTS selecao (
    id          SERIAL PRIMARY KEY,
    nome        VARCHAR(100) NOT NULL,
    codigo_pais CHAR(3)      NOT NULL,  -- ISO 3166-1 alpha-3 ex: "BRA"
    regiao_id   INT          NOT NULL REFERENCES regiao(id) ON DELETE CASCADE,
    api_id      INT          UNIQUE,    -- ID da API-Football
    UNIQUE(nome, regiao_id)
);

CREATE INDEX IF NOT EXISTS idx_selecao_regiao ON selecao(regiao_id);
CREATE INDEX IF NOT EXISTS idx_selecao_api_id ON selecao(api_id);

-- ============================================================
--  3. TEMPORADA  (filho de SELECAO)
-- ============================================================
CREATE TABLE IF NOT EXISTS temporada (
    id         SERIAL PRIMARY KEY,
    ano        INT          NOT NULL,   -- ex: 2024
    nome       VARCHAR(150) NOT NULL,   -- ex: "Copa América 2024"
    selecao_id INT          NOT NULL REFERENCES selecao(id) ON DELETE CASCADE,
    api_id     INT          UNIQUE,
    UNIQUE(ano, selecao_id)
);

CREATE INDEX IF NOT EXISTS idx_temporada_selecao ON temporada(selecao_id);
CREATE INDEX IF NOT EXISTS idx_temporada_ano     ON temporada(ano);

-- ============================================================
--  4. JOGADOR  (filho de SELECAO)
-- ============================================================
CREATE TABLE IF NOT EXISTS jogador (
    id             SERIAL PRIMARY KEY,
    nome           VARCHAR(150) NOT NULL,
    posicao        VARCHAR(30),          -- Goleiro, Defensor, Meio-campista, Atacante
    numero_camisa  SMALLINT,
    idade          SMALLINT,
    nacionalidade  VARCHAR(100),
    selecao_id     INT NOT NULL REFERENCES selecao(id) ON DELETE CASCADE,
    api_id         INT UNIQUE
);

CREATE INDEX IF NOT EXISTS idx_jogador_selecao ON jogador(selecao_id);
CREATE INDEX IF NOT EXISTS idx_jogador_api_id  ON jogador(api_id);
-- Índice para Interpolation Search (busca por número de camisa ou id numérico)
CREATE INDEX IF NOT EXISTS idx_jogador_num_camisa ON jogador(numero_camisa);

-- ============================================================
--  5. PARTIDA  (filho de TEMPORADA, folha da árvore)
-- ============================================================
CREATE TABLE IF NOT EXISTS partida (
    id               SERIAL PRIMARY KEY,
    temporada_id     INT          NOT NULL REFERENCES temporada(id) ON DELETE CASCADE,
    selecao_casa_id  INT          NOT NULL REFERENCES selecao(id),
    selecao_fora_id  INT          NOT NULL REFERENCES selecao(id),
    gols_casa        SMALLINT     DEFAULT 0,
    gols_fora        SMALLINT     DEFAULT 0,
    data_hora        TIMESTAMP,
    status           VARCHAR(10)  DEFAULT 'NS', -- NS=Not Started, FT=Full Time, LIVE
    fase             VARCHAR(50),               -- "Grupo A", "Final", etc.
    api_id           INT          UNIQUE
);

CREATE INDEX IF NOT EXISTS idx_partida_temporada   ON partida(temporada_id);
CREATE INDEX IF NOT EXISTS idx_partida_casa        ON partida(selecao_casa_id);
CREATE INDEX IF NOT EXISTS idx_partida_fora        ON partida(selecao_fora_id);
CREATE INDEX IF NOT EXISTS idx_partida_data        ON partida(data_hora);
-- Índice para Interpolation Search por data_hora (valores numéricos ordenados)
CREATE INDEX IF NOT EXISTS idx_partida_data_ord    ON partida(data_hora ASC);

-- ============================================================
--  6. PARTIDA_JOGADOR  (tabela de junção M:N)
-- ============================================================
CREATE TABLE IF NOT EXISTS partida_jogador (
    id                  SERIAL PRIMARY KEY,
    partida_id          INT    NOT NULL REFERENCES partida(id)  ON DELETE CASCADE,
    jogador_id          INT    NOT NULL REFERENCES jogador(id)  ON DELETE CASCADE,
    posicao_na_partida  VARCHAR(30),
    minutos_jogados     SMALLINT DEFAULT 0,
    gols                SMALLINT DEFAULT 0,
    assistencias        SMALLINT DEFAULT 0,
    nota_jogo           NUMERIC(4,2),    -- ex: 7.50
    titular             BOOLEAN  DEFAULT FALSE,
    UNIQUE(partida_id, jogador_id)
);

CREATE INDEX IF NOT EXISTS idx_pj_partida ON partida_jogador(partida_id);
CREATE INDEX IF NOT EXISTS idx_pj_jogador ON partida_jogador(jogador_id);

-- ============================================================
--  7. ESTATISTICA  (agregado por jogador + temporada)
-- ============================================================
CREATE TABLE IF NOT EXISTS estatistica (
    id                  SERIAL PRIMARY KEY,
    jogador_id          INT    NOT NULL REFERENCES jogador(id)   ON DELETE CASCADE,
    temporada_id        INT    NOT NULL REFERENCES temporada(id) ON DELETE CASCADE,
    jogos               SMALLINT  DEFAULT 0,
    gols                SMALLINT  DEFAULT 0,
    assistencias        SMALLINT  DEFAULT 0,
    cartoes_amarelos    SMALLINT  DEFAULT 0,
    cartoes_vermelhos   SMALLINT  DEFAULT 0,
    media_nota_jogo     NUMERIC(4,2),
    minutos_jogados     INT       DEFAULT 0,
    UNIQUE(jogador_id, temporada_id)
);

CREATE INDEX IF NOT EXISTS idx_estat_jogador   ON estatistica(jogador_id);
CREATE INDEX IF NOT EXISTS idx_estat_temporada ON estatistica(temporada_id);
-- Índice para Interpolation Search por gols (campo numérico ordenado)
CREATE INDEX IF NOT EXISTS idx_estat_gols      ON estatistica(gols);
