# Deploy no Render

## Pré-requisitos

- Conta no [Render](https://render.com)
- Repositório já publicado no GitHub (`thuzin27/football-analysis`)

---

## 1. Criar o banco de dados PostgreSQL

No painel do Render:

1. **New → PostgreSQL**
2. Nome: `datakick-db`
3. Região: **Ohio (US East)** — mesma do Web Service
4. Plano: Free
5. Clique em **Create Database**
6. Aguarde ficar `Available` e copie a **Internal Database URL** (começa com `postgres://...`)

---

## 2. Criar o Web Service

1. **New → Web Service**
2. Conecte o repositório `thuzin27/football-analysis`
3. Configurações:
   - **Name:** `datakick`
   - **Region:** Ohio (US East) — mesma do banco
   - **Branch:** `master`
   - **Runtime:** Docker
   - **Dockerfile path:** `./Dockerfile` (detectado automaticamente)
4. Clique em **Create Web Service**

---

## 3. Configurar variáveis de ambiente

No Web Service criado, vá em **Environment** e adicione:

| Variável | Valor |
|----------|-------|
| `DATABASE_URL` | Internal Database URL copiada do banco (passo 1) |
| `FOOTBALL_DATA_TOKEN` | Seu token do football-data.org |

> **Nota sobre SSL:** se o Render rejeitar a conexão com erro de SSL, acrescente
> `?sslmode=require` ao final da `DATABASE_URL`:
> `postgres://user:pass@host:5432/db?sslmode=require`

---

## 4. Criar o schema no banco

Após o primeiro deploy bem-sucedido, abra o **Shell** do Web Service no Render e execute:

```bash
# O schema.sql está em src/main/resources/schema.sql
# Conecte ao banco diretamente via a External Database URL:
psql "postgres://user:pass@host:5432/db?sslmode=require" -f src/main/resources/schema.sql
```

Ou use o **psql** localmente com a External Database URL disponível no painel do banco.

---

## 5. Verificar o deploy

- Logs em tempo real: aba **Logs** do Web Service
- Deve aparecer: `[DB] Pool de conexões inicializado com sucesso.` e `Dashboard: http://localhost:PORT`
- URL pública: `https://datakick.onrender.com` (ou o nome escolhido)

---

## Diagnóstico de falhas comuns

| Sintoma | Causa provável | Solução |
|---------|---------------|---------|
| `RuntimeException: Falha ao inicializar pool` | `DATABASE_URL` ausente ou inválida | Confira a variável no painel Environment |
| `Connection refused` ao banco | Regiões diferentes (banco em Oregon, app em Ohio) | Recrie um recurso na mesma região |
| `SSL SYSCALL error` | Banco exige SSL | Adicione `?sslmode=require` à `DATABASE_URL` |
| `Port already in use` | Não ocorre no Render (PORT é injetado automaticamente) | — |
| Página em branco após deploy | Schema não criado | Execute o `schema.sql` no banco (passo 4) |
| `[API] 429` nos logs | Rate limit da football-data.org atingido | Aguarde 1 minuto; o cache de 5 min mitiga |

---

## Aviso importante — Plano Free

O plano gratuito do Render **hiberna o Web Service após 15 minutos de inatividade**.
O primeiro acesso após hibernação demora **~30 segundos** para o container subir.

**Antes de apresentar o projeto:** abra a URL do dashboard pelo menos **5 minutos antes**
para garantir que o serviço está acordado e o pool de conexões inicializado.

---

## Atualizar o deploy

Qualquer `git push origin master` dispara um novo build automaticamente no Render.

```bash
git add -A
git commit -m "..."
git push origin master
```
