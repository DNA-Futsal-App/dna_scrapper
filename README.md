# DNA Futsal Scraper API

API REST em Java 17 + Spring Boot que transforma páginas públicas do portal de eventos da Federação Paulista de Futsal em JSON estruturado.

O projeto usa duas estratégias:

1. **Playwright** para operar os quatro filtros dinâmicos da página inicial: temporada, título, divisão e categoria.
2. **HttpClient + jsoup** para baixar e interpretar as páginas de classificação, jogos, equipes, detalhes da equipe e artilharia.

A aplicação inclui cache Caffeine, intervalo mínimo entre acessos ao portal, retries limitados, validação de hosts, leitura de `robots.txt`, API key opcional, rate limit de entrada, CORS, erros no formato Problem Details e proteção de dados pessoais por padrão. O arquivo `openapi.yaml` descreve o contrato completo e pode ser importado no Postman, Insomnia ou em um gerador de cliente.

> Decisão arquitetural importante: esta aplicação é um **adaptador de coleta**, não deve ser o banco de dados principal do aplicativo DNA Futsal. Para dezenas de milhares de usuários, o correto é sincronizar os dados em background para um banco próprio e fazer o aplicativo cliente consultar esse banco. O scraping sob demanda é útil para o MVP e para rotinas de atualização controladas, mas não deve ser executado a cada acesso de cada usuário.

## Dados extraídos

- Metadados do campeonato: título, temporada, categoria e divisão.
- Classificação por fase e grupo.
- Jogos realizados e agendados.
- Data, horário, ginásio, equipes, placar e situação de W.O.
- Identificador e link da súmula externa quando disponíveis.
- Equipes participantes.
- Detalhes da equipe.
- Atletas e comissão técnica, quando expressamente habilitados.
- Artilharia, com nomes e fotos apenas quando expressamente habilitados.
- Snapshot agregado do evento.

A súmula é um PDF externo. Esta versão retorna `gameId` e `matchSheetUrl`, mas não interpreta o conteúdo interno do PDF. Esse processamento deve ficar em um adaptador separado, pois o layout e as regras de privacidade são diferentes das páginas HTML.

## Estrutura do projeto

```text
openapi.yaml                       # Contrato OpenAPI 3.1
src/main/java/br/com/dnafutsal/scraper
├── api
│   ├── ApiAccessFilter.java       # API key e limite por minuto
│   ├── ApiExceptionHandler.java   # Problem Details
│   └── EventController.java       # Endpoints REST
├── browser
│   └── EventSearchBrowser.java    # Busca pelos selects dinâmicos
├── config
│   ├── ApiAccessProperties.java
│   ├── InfrastructureConfig.java
│   ├── ScraperProperties.java
│   └── WebConfig.java             # CORS
├── domain                         # Records retornados em JSON
├── exception
├── http
│   ├── PoliteHttpFetcher.java     # HTTP, delay, retry e allowlist
│   └── RobotsGuard.java
├── parser
│   ├── EventMetadataParser.java
│   ├── GamesParser.java
│   ├── StandingsParser.java
│   ├── TeamsParser.java
│   ├── TeamDetailsParser.java
│   └── ScorersParser.java
└── service
    └── FutsalScraperService.java  # Orquestração e cache
```

## Contrato dos endpoints

O contrato também está disponível em `openapi.yaml`. Todos os endpoints abaixo começam em `/api/v1/events`.

| Método | Endpoint | Finalidade |
|---|---|---|
| GET | `/search` | Pesquisa eventos pelos filtros da página inicial |
| GET | `/{eventId}` | Metadados do evento |
| GET | `/{eventId}/standings` | Classificação |
| GET | `/{eventId}/games` | Jogos |
| GET | `/{eventId}/teams` | Equipes participantes |
| GET | `/{eventId}/teams/{teamId}` | Detalhes da equipe |
| GET | `/{eventId}/scorers` | Artilharia |
| GET | `/{eventId}/snapshot` | Evento, classificação, jogos, equipes e artilharia |

### 1. Pesquisar campeonatos

A temporada é obrigatória para impedir pesquisas abertas e custosas contra a fonte.

```http
GET /api/v1/events/search?season=2026&title=Campeonato%20Paulista&division=A1&category=Principal
X-API-Key: troque-esta-chave
```

Resposta:

```json
[
  {
    "eventId": 917,
    "title": "Campeonato Paulista",
    "season": 2026,
    "category": "Principal",
    "division": "A1",
    "sourceUrl": "https://eventos.admfutsal.com.br/evento/917"
  }
]
```

O Playwright abre a página inicial, seleciona os filtros na ordem correta, clica em `Buscar`, captura os IDs encontrados nos links `/evento/{id}` e valida cada resultado pela página do evento.

### 2. Consultar metadados

```http
GET /api/v1/events/917
```

O `EventMetadataParser` procura os títulos que contêm `Temporada` e `Categoria ..., Divisao ...` e os transforma em um `EventMetadata`.

### 3. Consultar classificação

```http
GET /api/v1/events/917/standings?phase=classificação&group=grupo%20a
```

Filtros opcionais:

- `phase`: parte do nome da fase.
- `group`: parte do nome do grupo ou chave.

O parser detecta tabelas que possuem pontos, vitórias e gols. Os cabeçalhos são convertidos em índices para evitar dependência rígida da posição das colunas.

### 4. Consultar jogos

```http
GET /api/v1/events/917/games?team=Corinthians&from=2026-04-01&to=2026-12-31
```

Filtros opcionais:

- `phase`
- `team`
- `from`, no formato `yyyy-MM-dd`
- `to`, no formato `yyyy-MM-dd`

Exemplo de resposta:

```json
[
  {
    "gameId": 12345,
    "phase": "1º FASE DE CLASSIFICAÇÃO",
    "date": "2026-04-10",
    "time": "19:30:00",
    "venue": "GINÁSIO EXEMPLO",
    "homeTeam": "TIME A",
    "homeLogoUrl": "https://eventos.admfutsal.com.br/...",
    "homeScore": 3,
    "awayTeam": "TIME B",
    "awayLogoUrl": "https://eventos.admfutsal.com.br/...",
    "awayScore": 2,
    "walkover": false,
    "matchSheetUrl": "https://admfutsal.com.br/sumula_online/sumula_imprimir.php?id_jogo=12345"
  }
]
```

Jogos ainda não realizados continuam sendo retornados com `homeScore` e `awayScore` nulos.

### 5. Consultar equipes

```http
GET /api/v1/events/917/teams
```

Cada link no formato `/evento/{eventId}/equipe/{teamId}` se torna um `TeamSummary`.

### 6. Consultar detalhes da equipe

```http
GET /api/v1/events/917/teams/10970
```

Por padrão, endereço, telefone, atletas e comissão técnica não são devolvidos:

```json
{
  "eventId": 917,
  "teamId": 10970,
  "name": "TIME A",
  "logoUrl": "https://eventos.admfutsal.com.br/...",
  "athletes": [],
  "staff": [],
  "personalDataSuppressed": true,
  "sourceUrl": "https://eventos.admfutsal.com.br/evento/917/equipe/10970"
}
```

Para liberar esses dados, são necessárias as duas condições:

1. A variável de ambiente `EXPOSE_PERSONAL_DATA=true`.
2. A requisição conter `includePersonalData=true`.

```http
GET /api/v1/events/917/teams/10970?includePersonalData=true
```

A dupla trava evita que uma chamada do frontend libere dados pessoais por acidente.

### 7. Consultar artilharia

```http
GET /api/v1/events/917/scorers?phase=classificação&limit=20
```

Os nomes e as fotos seguem a mesma dupla trava:

```http
GET /api/v1/events/917/scorers?limit=20&includePersonalData=true
```

Sem autorização, a API preserva equipe e quantidade de gols, mas devolve `player` e `playerImageUrl` como nulos e `personalDataSuppressed=true`.

### 8. Snapshot completo

```http
GET /api/v1/events/917/snapshot
```

O snapshot reúne os principais dados para uma sincronização. Dados pessoais continuam suprimidos.

## Fluxo interno de uma requisição

```text
Cliente
  │
  ▼
ApiAccessFilter
  ├── valida X-API-Key, quando configurada
  └── aplica limite por minuto
  │
  ▼
EventController
  ├── valida IDs, datas e limites
  └── aplica filtros sobre a resposta
  │
  ▼
FutsalScraperService
  ├── consulta o cache
  ├── escolhe página e parser
  └── aplica a política de dados pessoais
  │
  ├── busca de eventos: Playwright
  └── páginas do evento: HttpClient + jsoup
          ├── valida host
          ├── consulta robots.txt
          ├── serializa acessos ao upstream
          ├── espera o intervalo mínimo
          └── executa retries limitados
```

## Executar localmente

Requisitos:

- Java 17 ou superior.
- Maven 3.6.3 ou superior.
- Chromium do Playwright.

### 1. Instalar dependências

```bash
mvn clean test
```

### 2. Instalar o Chromium usado pelo Playwright

```bash
mvn exec:java \
  -Dexec.mainClass=com.microsoft.playwright.CLI \
  -Dexec.args="install chromium"
```

No Linux, caso faltem dependências do navegador:

```bash
mvn exec:java \
  -Dexec.mainClass=com.microsoft.playwright.CLI \
  -Dexec.args="install --with-deps chromium"
```

### 3. Configurar variáveis

Linux/macOS:

```bash
export DNA_FUTSAL_API_KEY="uma-chave-longa-e-aleatoria"
export ALLOWED_ORIGINS="http://localhost:3000,http://localhost:5173"
export SCRAPER_USER_AGENT="DNAFutsalDataAdapter/1.0 (+seu-email@dominio.com.br)"
export EXPOSE_PERSONAL_DATA="false"
```

PowerShell:

```powershell
$env:DNA_FUTSAL_API_KEY="uma-chave-longa-e-aleatoria"
$env:ALLOWED_ORIGINS="http://localhost:3000,http://localhost:5173"
$env:SCRAPER_USER_AGENT="DNAFutsalDataAdapter/1.0 (+seu-email@dominio.com.br)"
$env:EXPOSE_PERSONAL_DATA="false"
```

### 4. Subir a API

```bash
mvn spring-boot:run
```

Teste de saúde:

```bash
curl http://localhost:8080/actuator/health
```

Teste da API:

```bash
curl "http://localhost:8080/api/v1/events/search?season=2026&title=Campeonato%20Paulista&division=A1&category=Principal" \
  -H "X-API-Key: uma-chave-longa-e-aleatoria"
```

## Executar com Docker

```bash
docker compose up --build
```

A imagem de runtime do Playwright já contém os navegadores e dependências necessárias, evitando instalar o Chromium manualmente dentro do container.

## Configurações

| Variável | Padrão | Descrição |
|---|---|---|
| `PORT` | `8080` | Porta HTTP |
| `DNA_FUTSAL_API_KEY` | vazio | Chave exigida em `X-API-Key`; vazio desabilita autenticação |
| `API_REQUESTS_PER_MINUTE` | `60` | Limite de entrada por instância |
| `ALLOWED_ORIGINS` | localhost 3000 e 5173 | Origens CORS separadas por vírgula |
| `SCRAPER_BASE_URL` | portal de eventos | Host das páginas HTML |
| `MATCH_SHEET_BASE_URL` | portal de súmulas | Segundo host permitido |
| `SCRAPER_USER_AGENT` | identificador de exemplo | Identificação enviada ao portal |
| `BROWSER_SEARCH_ENABLED` | `true` | Ativa a pesquisa via Playwright |
| `EXPOSE_PERSONAL_DATA` | `false` | Autoriza a API a devolver dados pessoais quando também solicitado |
| `RESPECT_ROBOTS_TXT` | `true` | Valida caminhos bloqueados |

O limite em memória funciona em uma única instância. Em produção com múltiplas réplicas, use API Gateway ou Redis para um rate limit compartilhado.

## Cache e proteção do portal de origem

O Caffeine mantém cada resposta por dez minutos e limita o número de entradas. O `PoliteHttpFetcher` também:

- aceita apenas HTTPS;
- aceita somente os dois hosts configurados;
- permite uma chamada externa por vez em cada instância;
- aguarda pelo menos 900 ms entre chamadas;
- repete apenas erros transitórios, como 429 e 5xx;
- respeita `Retry-After`;
- consulta `robots.txt`.

Essas medidas não substituem autorização do proprietário do portal. Antes de uso comercial, formalize permissão, frequência de coleta, atribuição, retenção e campos que podem ser republicados.

## Tratamento de erros

Erros são retornados como `application/problem+json`:

```json
{
  "type": "https://api.dnafutsal.com.br/problems/502",
  "title": "Falha na fonte externa",
  "status": 502,
  "detail": "A estrutura dos filtros do site de origem mudou",
  "instance": "/api/v1/events/search",
  "timestamp": "2026-07-17T20:00:00Z"
}
```

Principais status:

- `400`: parâmetros ou filtros inválidos.
- `401`: API key ausente ou inválida.
- `404`: evento ou página inexistente.
- `429`: limite de entrada excedido.
- `502`: portal indisponível ou HTML incompatível com o parser.
- `500`: erro interno inesperado.

## Testes

`ParserContractTest` usa documentos HTML controlados e valida:

- metadados;
- classificação;
- partidas realizadas e futuras;
- IDs de súmula;
- equipes;
- artilharia;
- supressão de dados pessoais.

Execute:

```bash
mvn test
```

Também é recomendável manter amostras anonimizadas do HTML real como testes de contrato. Quando o portal alterar o markup, esses testes devem falhar antes de a mudança chegar à produção.

## Arquitetura recomendada para produção

Para 25 mil a 100 mil usuários, não conecte o aplicativo móvel diretamente a esta API de scraping em tempo real. Use este fluxo:

```text
Scheduler/Worker
      │
      ▼
Scraper API
      │
      ▼
Normalização + comparação de mudanças
      │
      ├── PostgreSQL: campeonatos, equipes, jogos, classificação e artilharia
      ├── Redis: leituras quentes
      └── Fila/Outbox: jogo iniciado, gol, resultado e alteração
                         │
                         ▼
                 API principal DNA Futsal
                         │
                         ▼
                   App, site e painel
```

Benefícios:

- o portal original recebe poucas consultas controladas;
- o aplicativo continua funcionando se a fonte ficar fora do ar;
- mudanças podem ser comparadas para gerar notificações;
- o frontend recebe respostas rápidas e estáveis;
- há histórico, auditoria e possibilidade de correção manual.

## Próximas evoluções técnicas

1. Descobrir e documentar os endpoints AJAX usados pelos selects para substituir o navegador quando possível.
2. Persistir snapshots em PostgreSQL.
3. Criar um worker agendado por evento ativo.
4. Comparar snapshots e publicar eventos de domínio.
5. Adicionar métricas de sucesso, duração, cache hit e alterações de markup.
6. Criar testes de contrato com HTML real anonimizado.
7. Implementar circuit breaker e alertas quando a taxa de falha aumentar.
8. Criar um adaptador separado para súmulas PDF, somente após validar autorização e finalidade.
