# PlaylistBridge: mapa do projeto

PlaylistBridge é uma ferramenta self-hosted para copiar a estrutura de uma playlist do Spotify para uma playlist do YouTube. Imagine duas bibliotecas: Spotify informa quais livros estão numa estante; YouTube recebe uma nova estante com vídeos equivalentes. O projeto transfere nomes, artistas e IDs de provedor. Nunca baixa, armazena, transmite ou converte áudio.

## Arquitetura

Projeto usa dois serviços:

- `backend/`: Java 21, Spring Boot 3.4.4. Controla OAuth, sessão, chamadas Spotify/YouTube, busca de candidatos e criação da playlist.
- `frontend/`: Next.js 14, React, TypeScript. Renderiza fluxo no navegador e chama API usando `NEXT_PUBLIC_API_BASE_URL`.
- `docker-compose.yml`: sobe ambos serviços. Backend porta `8080`; frontend porta `3000`.

Fluxo principal:

1. Usuário conecta Spotify e Google por OAuth.
2. Backend cria estado aleatório de uso único e sessão opaca em cookie `HttpOnly`.
3. Callback troca `code` por token e guarda token somente na sessão em memória.
4. Frontend carrega playlists Spotify.
5. Backend lê tracks e pesquisa candidatos no YouTube.
6. Frontend transforma candidatos aprovados em pares `trackId`/`videoId`.
7. Backend cria playlist YouTube e insere vídeos selecionados.

## Estrutura importante

### Backend

- `auth/`: `OAuthController`, `OAuthService`, `OAuthSessionStore` e cliente de troca de token.
- `api/`: endpoints REST, DTOs e tratamento de erros.
- `provider/`: interfaces de domínio e adapters HTTP para APIs externas.
- `service/`: paginação Spotify e orquestração da prévia/conversão.
- `config/`: propriedades e CORS.
- `src/test/`: testes de endpoint, OAuth, adapters e conversão.

### Frontend

- `src/app/page.tsx`: fluxo visual de conexão, seleção, revisão e destino.
- `src/lib/api.ts`: cliente HTTP tipado e normalização dos DTOs do backend.
- `src/lib/flow.ts`: reducer puro do fluxo; mantém comportamento testável sem depender do DOM.
- `tests/flow.test.ts`: testes de cliente API, seleção e transições.

### Documentação operacional

- `README.md`: entrada rápida, OAuth e configuração.
- `docs/api-contract.md`: contrato HTTP efetivo.
- `docs/deployment.md`: Docker, reverse proxy, HTTPS e operação.
- `.env.example`: nomes de variáveis sem segredos.
- `validate-config.ps1`: valida configuração básica antes deploy.

## Decisões que importam

OAuth fica no backend porque client secrets e refresh tokens não podem chegar ao browser. A sessão atual é em memória para manter primeiro release pequeno e sem banco; isso significa que reiniciar ou escalar horizontalmente perde sessões. Instância única é requisito atual. Próximo passo para produção multi-instância: store compartilhado, criptografia de tokens e política de expiração.

CORS aceita somente origens configuradas em `CORS_ALLOWED_ORIGINS` e permite credenciais porque autenticação usa cookie. O resolver de token lê somente cookie; header customizado foi evitado para não criar caminho de impersonação de sessão.

Adapters recebem `RestOperations` e fornecedor dinâmico de token. Isso permite testar chamadas externas sem rede e permite que cada request use token da sessão correta. Interfaces `SpotifyProvider` e `YouTubeProvider` isolam regras de negócio das APIs de terceiros.

Preview não cria playlist. Conversão aceita apenas seleções explícitas retornadas pela prévia. Essa separação reduz surpresa para usuário e limita chamadas de escrita.

## Como validar

Backend:

```powershell
cd backend
mvn clean test package
```

Frontend:

```powershell
cd frontend
npm ci
npm test -- --run
npm run lint
npm run build
```

Configuração:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\validate-config.tests.ps1
docker compose --env-file .env.example config --quiet
```

Docker build precisa de daemon Linux disponível. Sem daemon, testes Java/Node e parse Compose continuam válidos, mas imagem não foi comprovada.

## Armadilhas e lições

- DTOs frontend/backend precisam ser testados juntos. O backend usa `content`, `privacyStatus` e `selections`; aceitar nomes antigos no cliente quebraria conversão em runtime.
- `credentials: include` no browser exige CORS explícito com origem concreta e `Access-Control-Allow-Credentials: true`. `*` não funciona para cookies.
- OAuth state deve ser aleatório, associado à sessão e consumido uma vez. Reuso de state é replay.
- Redirect URI precisa coincidir exatamente com cadastro do provedor, incluindo esquema, porta e caminho.
- YouTube busca e escrita consomem quota. Não adicionar retry cego: respeitar status e limites do provedor.
- Nunca adicionar API key, client secret, token, `.env` preenchido ou credencial em commit.
- Docker roda como usuário não-root, com filesystem read-only, `/tmp` temporário e capabilities removidas. Novos recursos precisam respeitar essas restrições.
- Frontend browser não resolve `http://backend:8080`; esse hostname existe apenas dentro da rede Compose. Use origem pública em `NEXT_PUBLIC_API_BASE_URL`.

## Próximos incrementos naturais

1. Persistência opcional de sessão/token com criptografia e store compartilhado.
2. Job assíncrono para playlists grandes, progresso e retomada idempotente.
3. Escolha manual de candidato por faixa e relatório de itens sem match.
4. Logout visível, revogação de tokens e exclusão de dados operacionais.
5. Testes end-to-end com providers fake e build de imagens em CI.
