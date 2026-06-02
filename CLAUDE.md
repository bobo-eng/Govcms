# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

GovCMS is a government-oriented CMS platform with two main components:
- **Backend**: Spring Boot admin API and portal rendering engine
- **Frontend**: Vue 3 admin dashboard

Current scope covers RBAC, content lifecycle, category/template/navigation/topic management, publish center, media management, portal page rendering, and search index operations.

## Tech Stack

- **Backend**: Java 17, Spring Boot 3.2, Spring Security, JWT, Spring Data JPA, Thymeleaf, MySQL 8
- **Frontend**: Vue 3, TypeScript, Vite, Ant Design Vue 4, Axios
- **Build**: Maven (backend), npm (frontend)
- **Storage**: Local filesystem (`./storage/media`, `./storage/publish`)

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
security/       JWT filter and utility
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

### Media

- Uploaded files stored in `./storage/media` (configurable via `app.media.storage-path`)
- `MediaReferenceService` tracks which articles reference which media files
- Media with active references is protected from deletion

## Configuration

- `application.yml` — base config, default profile `local`
- `application-local.yml` — local MySQL connection (database `govcms`, user `root` / `123456`)
- `application-test.yml` — test profile
- JWT secret and expiration in `application.yml` (not production-grade)
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
- Audit logs cover key publish actions but do not yet have a dedicated admin UI
- JWT signing is standard HMAC, not SM2/SM3 (national cryptography) — required for final delivery but not yet integrated
- Default database is MySQL; target production database is KingbaseES
