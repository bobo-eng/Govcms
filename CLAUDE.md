# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

GovCMS is a government-oriented CMS platform with two main components:
- **Backend**: Spring Boot admin API and portal rendering engine
- **Frontend**: Vue 3 admin dashboard

Current scope covers RBAC, content lifecycle, category/template/navigation/topic management, publish center, media management, portal page rendering, and search index operations.

## Tech Stack

- **Backend**: Java 17, Spring Boot 3.2, Spring Security, JWT, Spring Data JPA, Thymeleaf, MySQL 8 (dev), Dameng DM8 (prod)
- **Frontend**: Vue 3, TypeScript, Vite, Ant Design Vue 4, Axios
- **Build**: Maven (backend), npm (frontend)
- **Storage**: Local filesystem (`./storage/media`, `./storage/publish`)
- **Cache**: Spring Cache abstraction backed by Redis (`categoryTree`, `userPermissions`)
- **Rate Limiting**: Bucket4j 8.10.1 with Redis-backed distributed token bucket
- **Health Checks**: Spring Boot Actuator with custom `HealthIndicator` components (DB, Redis, Hibernate Search, Quartz)
- **Cryptography**: BouncyCastle GM (SM2/SM3/SM4); SM4 field-level transparent encryption via JPA `AttributeConverter`; SM3 streaming digest for publish artifacts

## Common Commands

### Backend

```bash
# Compile
mvn compile

# Run with local profile (default)
mvn spring-boot:run
# or
java -jar target/govcms-admin-0.0.1-SNAPSHOT.jar --spring.profiles.active=local

# Run tests
mvn test

# Run a single test class
mvn test -Dtest=ArticleServiceTest

# Package
mvn package -DskipTests
```

### Frontend

```bash
cd frontend

# Install dependencies
npm install

# Dev server (port 4869, proxies /api to localhost:8080)
npm run dev

# Type check
npx vue-tsc --noEmit

# Build for production
npm run build
```

### Full Stack (Local Development)

1. Start MySQL and create database `govcms`
2. Run backend: `mvn spring-boot:run` (port 8080)
3. Run frontend: `cd frontend && npm run dev` (port 4869)
4. Login with default admin: `admin` / `admin123`

## Project Structure

### Backend (`src/main/java/gov/cms/admin/`)

Standard Spring Boot layered architecture:

```
entity/         JPA entities (no Lombok on entities — plain getters/setters)
repository/     Spring Data JPA repositories
service/        Business logic; constructor injection only
controller/     REST API controllers; returns DTOs, never raw entities
dto/            Request/response records and POJOs
security/       JWT filter, GM crypto service (SM2/SM3/SM4), `Sm4Encryptor`, `Sm4FieldConverter`, `RateLimitFilter`
config/         Security config, DataInitializer (seeds permissions/roles/menus/admin user)
exception/      GlobalExceptionHandler
```

Key entities: `User`, `Role`, `Permission`, `Menu`, `Site`, `Category`, `Article`, `Template`, `TemplateVersion`, `TemplateBinding`, `NavigationItem`, `Topic`, `TopicContentItem`, `MediaFile`, `PublishJob`, `PublishArtifact`, `PublishImpactItem`, `PublishRollbackRecord`, `AuditLog`, `SearchIndexEntry`, `SearchQueryLog`.

### Frontend (`frontend/src/`)

```
views/          Page components (routed views)
components/     Shared components (MainLayout is the admin shell)
router/         Vue Router setup
api/            API modules per domain (articles, categories, templates, sites, navigation, topics, publish, searchIndex)
utils/          Axios instance (api.ts) and session helpers
styles/         admin-refresh.css (shared admin UI styles)
```

## Architecture Notes

### Content Lifecycle

Articles flow through six statuses: `draft` → `pending_review` → `rejected` | `approved` → `published` → `offline`. Transitions are enforced in `ArticleService` and audited in `ArticleLifecycleHistory`.

### RBAC and Site Governance

- Permission codes follow pattern: `domain:resource:action` (e.g., `content:article:create`)
- Roles: `admin` (global), `site_admin` (single-site governance), `editor`, `reviewer`, `publisher`, `viewer`
- `site_admin` users are bound to one site; many APIs enforce site-scoped access via `SiteAccessService`
- `DataInitializer` seeds all permissions, roles, menus, and the default admin user on startup

### Template and Portal Rendering

- `Template` defines page templates; `TemplateBinding` links templates to sites/categories/topics
- `PortalRenderService` uses Thymeleaf to render pages into static HTML
- Supported page types: `home`, `column-list`, `content-detail`, `topic-page`, `error-404`
- `RenderContextAssembler` builds the context snapshot consumed by the render engine

### Publish Center

- `PublishService` handles synchronous publish execution (not async yet)
- Pre-publish checks include missing-media validation via `MediaReferenceService`
- Publish produces `PublishArtifact` files under `./storage/publish/` and records `PublishJob` / `PublishImpactItem` / `PublishRollbackRecord`
- Rollback restores prior artifacts and updates article statuses

### Search

- `SearchIndexEntry` stores index records in the database (not a separate search engine)
- `SearchIndexService` rebuilds indexes for articles, categories, and topics
- Portal search endpoint is public (`/api/portal/search/**`)

### National Cryptography (GM)

- `GmCryptoService` (`BouncyCastleGmCryptoService`) provides SM2 signing/verification, SM3 digest, and SM4 encryption/decryption
- `Sm4Encryptor` is a Spring-managed component that wraps SM4 with Base64 encoding; key from `gm.crypto.sm4.key-hex`
- `Sm4FieldConverter` is a JPA `AttributeConverter` applied to `User.email` and `User.fullName` for transparent encryption at rest
- Converter uses `SpringContextHolder` to look up the `Sm4Encryptor` bean and caches it in a `volatile` field
- Deterministic SM4 (fixed zero IV) is used so that encrypted values remain stable for DB `unique` constraints and exact-match queries
- `UserService` encrypts email parameters before calling `existsByEmail` / `existsByEmailAndIdNot` because Spring Data JPA does not apply `AttributeConverter` to query parameters
- `gm.crypto.enabled=false` disables encryption entirely (plaintext passthrough); missing key when enabled throws `IllegalStateException`
- Read-time compatibility: if decryption encounters invalid Base64, the raw value is returned as-is (supports legacy plaintext migration)
- Publish artifacts include an SM3 hex digest computed via `DigestOutputStream` during write; stored in `PublishArtifact.sm3Digest` and persisted as a `.sm3` sidecar file
- `GET /api/publish/artifacts/{id}/verify` re-computes the on-disk SM3 and returns `ArtifactVerifyResponse`

### Cache

- Spring Cache abstraction enabled via `@EnableCaching` in `GovCmsApplication`
- Redis-backed cache with `spring.cache.type=redis` in `application-prod.yml`
- `categoryTree` cache: `CategoryService.getTreeBySiteId(Long)` is `@Cacheable`; mutations evict the cache
- `userPermissions` cache: `UserService.getPermissionCodes(Long)` is `@Cacheable`; role assignment evicts the cache

### Rate Limiting

- Bucket4j 8.10.1 with Redis-backed `LettuceBasedProxyManager` for distributed rate limiting
- `RateLimitFilter` (`OncePerRequestFilter`) intercepts requests and enforces token-bucket rules per path
- Rules are configurable via `app.rate-limit.rules` in `application-prod.yml`
- Default rules: `/api/auth/login` (5/min), `/api/portal/search` (60/sec), `/api/publish/` (10/min)

### Health Checks

- Spring Boot Actuator exposes `health` and `info` endpoints (`management.endpoints.web.exposure.include`)
- Custom `HealthIndicator` components:
  - `DataSourceHealthIndicator` — validates DB connectivity with `SELECT 1`
  - `RedisHealthIndicator` — pings Redis via `RedisTemplate`
  - `HibernateSearchHealthIndicator` — checks `SearchMapping.allIndexedEntities()`
  - `QuartzHealthIndicator` — checks scheduler is started and not in standby

### Media

- Uploaded files stored in `./storage/media` (configurable via `app.media.storage-path`)
- `MediaReferenceService` tracks which articles reference which media files
- Media with active references is protected from deletion

## Configuration

- `application.yml` — base config, default profile `local`
- `application-local.yml` — local MySQL connection (database `govcms`, user `root` / `123456`)
- `application-dm.yml` — Dameng DM8 connection for local DM testing (`ddl-auto: validate`)
- `application-prod.yml` — production template with HikariCP, DM8, Redis, Quartz cluster, and media/publish storage paths
- `application-test.yml` — test profile; provides `gm.crypto.sm4.key-hex` for test encryption
- JWT secret and expiration in `application.yml` (not production-grade)
- GM crypto keys via environment variables: `GM_SM2_PRIVATE_KEY`, `GM_SM2_PUBLIC_KEY`, `GM_SM4_KEY`
- CORS is open (`*`) for local development

## API Conventions

- Base path: `/api`
- Auth endpoints: `/api/auth/**` (public)
- Portal search: `/api/portal/search/**` (public)
- All other endpoints require JWT Bearer token
- Controllers use constructor injection and return `ResponseEntity<?>` or DTOs directly
- `GlobalExceptionHandler` maps exceptions to HTTP statuses

## Frontend Conventions

- Axios instance in `utils/api.ts` attaches JWT token and handles 401 redirect to `/login`
- Route guards check token and permission cache readiness in `router/index.ts`
- Views fetch data directly from API modules; no centralized state management (Pinia/Vuex not used)
- `MainLayout.vue` fetches user menus from `/api/menus/user` and renders dynamic sidebar
- Permission-based UI control uses `v-if="hasPermission('code')"` via `usePermission` composable

## Testing

- Backend: JUnit 5 + Mockito. Controller tests mock services; service tests mock repositories.
- No frontend test suite is currently set up.
- To run backend tests: `mvn test`
- To run a single test: `mvn test -Dtest=ClassName`

## Documentation

The `docs/` directory is the single source of truth for product requirements and technical design. Key documents:

- `docs/README.md` — document index
- `docs/03-backoffice-prd.md` — backoffice product requirements
- `docs/04-portal-prd.md` — portal product requirements
- `docs/06-roadmap-and-acceptance.md` — roadmap and milestones
- `docs/10-publish-render-contract-prd.md` — publish/render contract
- `docs/16-role-system-definition.md` — role definitions
- `docs/18-admin-ux-visual-and-interaction-guidelines.md` — admin UI standards

When product behavior conflicts with code, the `docs/` directory takes precedence.

## Known Limitations

- Publish center is synchronous; async orchestration and multi-environment deployment are not yet implemented
- Search uses database tables, not a dedicated search engine
- Audit logs cover key publish actions and have a dedicated admin UI (`/audit-logs`)
- Default dev database is MySQL; production database is Dameng DM8
