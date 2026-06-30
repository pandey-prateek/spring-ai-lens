'README'
# spring-ai-lens

[![CI](https://github.com/bluntyrod/spring-ai-lens/actions/workflows/ci.yaml/badge.svg)](https://github.com/bluntyrod/spring-ai-lens/actions/workflows/ci.yaml)
[![Java](https://img.shields.io/badge/Java-21-blue)](https://openjdk.org/projects/jdk/21/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5-green)](https://spring.io/projects/spring-boot)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue)](LICENSE)

> **AI model / new contributor:** Read this file in full before writing any code. It is the single source of truth for project state, architecture, decisions, and what is left to do.

---

## What it does

`spring-ai-lens` is a zero-configuration observability library for Spring Boot applications that use Spring AI's `ChatModel`. Drop one dependency onto the classpath and instantly get:

- **AOP interception** of every `ChatModel.call()` — no code changes needed, and **provider-agnostic**: works with any Spring AI `ChatModel` (OpenAI, Azure OpenAI, Anthropic, Ollama, Mistral, Vertex AI Gemini, Bedrock, HuggingFace, etc.)
- **Live dashboard** at `/ai-lens` — prompt, response, latency, token usage, anomaly and diff badges, auto-refreshes every 5 seconds, with **per-model filtering** and **time-range filtering**
- **Prompt diff viewer** — `GET /ai-lens/diff/{eventId}` side-by-side previous/current prompt comparison with `<ins>`/`<del>` word-level highlighting, plus an optional regression-alert webhook
- **Actuator endpoint** at `/actuator/ai-lens` — JSON report for CI and monitoring hooks
- **Anomaly detection** — latency spikes and token cost outliers flagged automatically with `⚠` badges
- **Prompt diff tracking** — detects when a prompt template changes between calls, shown as `⟳` badges
- **Streaming support** via `AiLensStreamAdvisor` for `ChatClient` streaming calls
- **OpenTelemetry export** — `llm.*` span attributes, `ERROR` status on anomalies (optional, classpath-gated)
- **Micrometer metrics** — call count, latency timer, token counters, anomaly counter (optional, classpath-gated)
- **Three pluggable storage backends** — in-memory (default), Redis, Postgres — selected via one `application.yaml` property

It is a **library, not a standalone application**. It auto-configures itself via `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`.

**Organisation:** [github.com/bluntyrod](https://github.com/bluntyrod)
**Repo:** [github.com/bluntyrod/spring-ai-lens](https://github.com/bluntyrod/spring-ai-lens)
**Maven:** `com.bluntyrod:springailens:0.0.2`
**Stack:** Java 21, Spring Boot 3.5.1, Spring AI 2.0.0, Maven
**Active branch:** `feature/persistent-storage`
**License:** Apache 2.0 © 2026 Bluntyrod

---

## Quick start

```xml
<dependency>
    <groupId>com.bluntyrod</groupId>
    <artifactId>springailens</artifactId>
    <version>0.0.2</version>
</dependency>
```

That's it. Start your app and visit `http://localhost:8080/ai-lens`.

To expose the actuator endpoint add to `application.yaml`:
```yaml
management:
  endpoints:
    web:
      exposure:
        include: ai-lens
```

For streaming calls, register the advisor:
```java
@Bean
public ChatClient chatClient(ChatClient.Builder builder, AiLensStreamAdvisor streamAdvisor) {
    return builder.defaultAdvisors(streamAdvisor).build();
}
```

---

## Configuration

All properties are under the `ai-lens.*` prefix with IDE autocomplete via `additional-spring-configuration-metadata.json`.

```yaml
ai-lens:
  buffer-size: 500

  anomaly:
    latency-threshold: 2.0       # flag if latencyMs > avg * threshold
    token-threshold: 2.0         # flag if totalTokens > avg * threshold
    min-baseline-calls: 3        # minimum calls before detection activates

  dashboard:
    enabled: true
    path: /ai-lens

  diff:
    alert-on-change: true        # log a WARN when a prompt template changes
    webhook-url:                 # optional: POST a JSON payload here on prompt change

  storage:
    type: MEMORY                 # MEMORY | REDIS | POSTGRES

    redis:
      key-prefix: ai-lens
      ttl-days: 7
      max-events: 500

    postgres:
      batch-size: 50
      flush-interval-ms: 100
      max-events: 500
```

### Storage backend dependencies

**Redis** — add to consuming app:
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-redis</artifactId>
</dependency>
```

**Postgres** — add to consuming app:
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-jdbc</artifactId>
</dependency>
<dependency>
    <groupId>org.postgresql</groupId>
    <artifactId>postgresql</artifactId>
</dependency>
```

---

## Repository layout

```
spring-ai-lens/
├── pom.xml
├── README.md                                        ← you are here (AI context doc)
├── Work.md                                          ← detailed session notes and decision log
├── LICENSE                                          ← Apache 2.0 © 2026 Bluntyrod
├── .github/
│   ├── workflows/ci.yaml                            ← CI: spotless check + mvn test
│   ├── CONTRIBUTING.md
│   └── ISSUE_TEMPLATE/
└── src/
    ├── main/java/com/bluntyrod/springailens/
    │   ├── config/
    │   │   ├── AiLensAutoConfiguration.java         ← @AutoConfiguration entry point; @Import sub-configs
    │   │   ├── AiLensMemoryAutoConfiguration.java   ← EventStore bean for MEMORY (no extra deps)
    │   │   ├── AiLensRedisAutoConfiguration.java    ← EventStore bean for REDIS (@ConditionalOnClass)
    │   │   ├── AiLensPostgresAutoConfiguration.java ← EventStore bean for POSTGRES (@ConditionalOnClass)
    │   │   ├── AiLensProperties.java                ← @ConfigurationProperties("ai-lens")
    │   │   ├── AnomalyProperties.java
    │   │   ├── StorageProperties.java
    │   │   ├── BaseStorageProperties.java
    │   │   ├── InMemoryStorageProperties.java
    │   │   ├── RedisStorageProperties.java
    │   │   ├── PostgresStorageProperties.java
    │   │   ├── DashboardProperties.java
    │   │   └── DiffProperties.java                  ← ai-lens.diff.* — alertOnChange, webhookUrl
    │   ├── model/
    │   │   ├── AiCallEvent.java                     ← core immutable record
    │   │   ├── AnomalyReport.java                   ← hasAnomaly, type, message
    │   │   ├── AnomalyType.java                     ← LATENCY_SPIKE | TOKEN_SPIKE | LATENCY_AND_TOKEN_SPIKE
    │   │   ├── PromptDiffResult.java                ← status, currentHash, previousHash, prompts
    │   │   ├── DiffStatus.java                      ← FIRST_SEEN | UNCHANGED | CHANGED
    │   │   └── StorageType.java                     ← MEMORY | REDIS | POSTGRES
    │   ├── util/
    │   │   ├── EventStore.java                      ← interface: add/getAll/getRecent/count/clear
    │   │   ├── advisor/AiLensStreamAdvisor.java     ← StreamAdvisor for ChatClient streaming
    │   │   ├── anomaly/AnomalyDetector.java         ← rolling avg comparison, configurable thresholds
    │   │   ├── diff/PromptDiffTracker.java          ← per-model ConcurrentHashMap tracking
    │   │   ├── diff/PromptHasher.java               ← SHA-256 + normalization (numbers/UUIDs/emails)
    │   │   ├── diff/PromptDiffRenderer.java         ← word-level LCS diff → ins/del HTML
    │   │   ├── diff/PromptRegressionAlerter.java    ← log warning + optional webhook on prompt change
    │   │   ├── interceptor/AiLensInterceptor.java   ← AOP @Around on ChatModel.call()
    │   │   ├── metrics/AiLensMetrics.java           ← Micrometer counters + timers
    │   │   ├── otel/AiLensOtelExporter.java         ← OTel span export via GlobalOpenTelemetry
    │   │   └── store/
    │   │       ├── InMemoryEventStore.java          ← ArrayDeque ring buffer, synchronized
    │   │       ├── RedisEventStore.java             ← leftPush + trim + TTL + Jackson
    │   │       ├── PostgresEventStore.java          ← async batch writer + JdbcTemplate
    │   │       └── RingBufferEventStore.java        ← @Deprecated alias for InMemoryEventStore
    │   ├── actuator/AiLensEndpoint.java             ← @Endpoint(id="ai-lens")
    │   └── web/AiLensDashboardController.java       ← GET /ai-lens → HTML dashboard
    └── test/java/com/bluntyrod/springailens/
        ├── actuator/AiLensEndpointTest.java
        ├── config/
        │   ├── AiLensPropertiesTest.java
        │   ├── InMemoryEventStoreTest.java
        │   └── StoragePropertiesTest.java
        ├── store/
        │   ├── RedisEventStoreTest.java             ← unit (Mockito)
        │   ├── RedisEventStoreIntegrationTest.java  ← Testcontainers
        │   ├── PostgresEventStoreTest.java          ← unit (Mockito)
        │   └── PostgresEventStoreIntegrationTest.java ← Testcontainers
        └── util/
            ├── advisor/AiLensStreamAdvisorTest.java
            ├── anomaly/AnomalyDetectorTest.java
            ├── diff/PromptDiffTrackerTest.java
            ├── diff/PromptHasherTest.java
            ├── interceptor/AiLensInterceptorTest.java
            ├── metrics/AiLensMetricsTest.java
            ├── otel/AiLensOtelExporterTest.java
            └── web/AiLensDashboardControllerTest.java
```

---

## Architecture

### Blocking call flow

```
User code → ChatModel.call(prompt)
    └─▶ AiLensInterceptor (@Around AOP)
            ├─ captures: prompt text, start time
            ├─ pjp.proceed() — actual LLM call
            ├─ captures: response, latency, token counts, model name
            ├─▶ AnomalyDetector.analyze()    — rolling avg comparison
            ├─▶ PromptDiffTracker.track()    — SHA-256 hash per model
            ├─▶ EventStore.add(event)        — Memory / Redis / Postgres
            ├─▶ AiLensOtelExporter.export()  — if OTel on classpath
            └─▶ AiLensMetrics.record()       — if Micrometer on classpath
```

### Streaming call flow

```
User code → chatClient.prompt(q).stream()
    └─▶ AiLensStreamAdvisor (order = Integer.MIN_VALUE)
            ├─ doOnNext: accumulate chunks, capture metadata
            └─ doOnComplete: assemble AiCallEvent → same post-processing pipeline
```

### Auto-configuration wiring

`AiLensAutoConfiguration` is the single registered entry point (via `AutoConfiguration.imports`). It uses `@Import` to pull in three storage sub-configurations:

- `AiLensMemoryAutoConfiguration` — active when `type=MEMORY` or unset; no extra classpath deps
- `AiLensRedisAutoConfiguration` — active when `type=REDIS` AND `StringRedisTemplate` on classpath
- `AiLensPostgresAutoConfiguration` — active when `type=POSTGRES` AND `JdbcTemplate` on classpath

This split is critical — it prevents `StringRedisTemplate` and `JdbcTemplate` from being loaded when not needed, which previously caused `TypeNotPresentException` on startup.

All beans use `@ConditionalOnMissingBean` so consuming apps can override any bean.

---

## Data model

```java
record AiCallEvent(
    String id,               // UUID
    Instant timestamp,
    String model,            // e.g. "OllamaChatModel"
    String prompt,
    String response,
    long latencyMs,
    int promptTokens,
    int completionTokens,
    AnomalyReport anomaly,   // AnomalyReport.none() if clean
    PromptDiffResult diff    // null if not tracked
)

record AnomalyReport(
    boolean hasAnomaly,
    AnomalyType type,        // LATENCY_SPIKE | TOKEN_SPIKE | LATENCY_AND_TOKEN_SPIKE
    String message           // human-readable, e.g. "Latency 2500ms is 3.2x above avg 780ms"
)

record PromptDiffResult(
    DiffStatus status,       // FIRST_SEEN | UNCHANGED | CHANGED
    String currentHash,      // 16-char SHA-256 prefix
    String previousHash,
    String previousPrompt,   // only populated when CHANGED
    String currentPrompt     // only populated when CHANGED
)
```

### Prompt normalization before hashing

Numbers → `N`, UUIDs → `UUID`, emails → `EMAIL`, then lowercased and trimmed. This means only structural template changes trigger a diff — variable data changes do not.

---

## Storage backends

### InMemoryEventStore
`ArrayDeque` with fixed capacity. `pollFirst()` evicts oldest when full. All methods `synchronized`. Zero deps. Default.

### RedisEventStore
`leftPush` + `LTRIM` keeps list capped atomically. Jackson + `JavaTimeModule` for `Instant` serialization. TTL set on key after every write. `getAll()` reverses list for chronological order.

### PostgresEventStore
Table `ai_lens_events` auto-created on startup (`CREATE TABLE IF NOT EXISTS`). Writes are async — `add()` enqueues to `ArrayBlockingQueue(10_000)`; daemon thread batch-flushes via `JdbcTemplate.batchUpdate()`. `ON CONFLICT (id) DO NOTHING` for idempotency.

**Schema:**
```sql
id VARCHAR(36) PRIMARY KEY,
timestamp TIMESTAMP NOT NULL,
model VARCHAR(255),
prompt TEXT,
response TEXT,
latency_ms BIGINT,
prompt_tokens INT,
completion_tokens INT,
has_anomaly BOOLEAN,
anomaly_type VARCHAR(50),
anomaly_message TEXT,
prompt_changed BOOLEAN,
prompt_hash VARCHAR(32),
previous_prompt_hash VARCHAR(32)
```

`mapRow()` fully reconstructs `AnomalyReport` and `PromptDiffResult` from columns on read-back.

---

## Test suite

**106 tests, 0 failures.**

Run all:
```bash
./mvnw test
```

Run without Docker (skips integration tests):
```bash
./mvnw test -Dskip.integration.tests=true
```

| Test class | Type | Tests |
|---|---|---|
| `AiLensEndpointTest` | Unit | 2 |
| `AiLensPropertiesTest` | Unit | 2 |
| `InMemoryEventStoreTest` | Unit | 4 |
| `StoragePropertiesTest` | Unit | 6 |
| `AiLensStreamAdvisorTest` | Unit | 3 |
| `AnomalyDetectorTest` | Unit | 5 |
| `PromptDiffTrackerTest` | Unit | 4 |
| `PromptHasherTest` | Unit | 4 |
| `PromptDiffRendererTest` | Unit | 5 |
| `PromptRegressionAlerterTest` | Unit | 4 |
| `AiLensInterceptorTest` | Unit | 2 |
| `AiLensMetricsTest` | Unit | 5 |
| `AiLensOtelExporterTest` | Unit | 3 |
| `AiLensDashboardControllerTest` | Unit | 8 |
| `RedisEventStoreTest` | Unit (Mockito) | 12 |
| `RedisEventStoreIntegrationTest` | Integration (Testcontainers) | 11 |
| `PostgresEventStoreTest` | Unit (Mockito) | 9 |
| `PostgresEventStoreIntegrationTest` | Integration (Testcontainers) | 14 |

Integration tests use `@DisabledIfSystemProperty(named = "skip.integration.tests", matches = "true")` to skip gracefully when Docker is unavailable.

---

## Build commands

```bash
./mvnw compile
./mvnw test
./mvnw test -Dskip.integration.tests=true   # skip Testcontainers tests
./mvnw package -DskipTests
./mvnw install -DskipTests                  # install to local Maven repo
./mvnw spotless:check                       # check formatting
./mvnw spotless:apply                       # fix formatting
./mvnw clean install                        # full clean build
```

---

## What is done ✅

- AOP interception of all `ChatModel.call()` invocations — provider-agnostic, works with **any** Spring AI `ChatModel` implementation (OpenAI, Azure OpenAI, Anthropic, Ollama, Mistral, Vertex AI Gemini, Bedrock, HuggingFace, etc.) with zero code changes, since interception happens on the framework interface, not a specific provider class
- Streaming interception via `AiLensStreamAdvisor`
- `InMemoryEventStore` ring buffer
- `RedisEventStore` — leftPush, trim, TTL, full Jackson serialization
- `PostgresEventStore` — async batch writer, auto table creation, idempotent inserts, full `mapRow()` reconstruction of `AnomalyReport` and `PromptDiffResult`
- Auto-configuration split into per-backend classes — no classpath pollution, no `TypeNotPresentException`
- Live dashboard at `/ai-lens` with anomaly and diff badges
- **Per-model filtering** on the dashboard — dropdown auto-populated from every model name seen across stored events, so all AI models/providers in use are discoverable and filterable in one place
- **Time-range filtering** on the dashboard — `?from=`/`?to=` ISO-8601 instant query params
- **Prompt diff viewer UI** — `GET /ai-lens/diff/{eventId}` renders a side-by-side previous/current prompt comparison with `<ins>`/`<del>` word-level highlighting
- **`PromptRegressionAlerter`** — logs a `WARN` and optionally POSTs a JSON payload to a configured webhook whenever a prompt template changes
- Actuator endpoint at `/actuator/ai-lens`
- Anomaly detection — latency and token spikes
- Prompt diff tracking with normalization
- OpenTelemetry export — validated with Jaeger
- Micrometer metrics — validated with Prometheus
- IntelliJ autocomplete for all `ai-lens.*` properties, including the new `ai-lens.diff.*` block
- Mockito dynamic-agent-loading warning suppressed in Surefire config
- Javadoc on the public API surface (`EventStore`, `AiCallEvent`, `AiLensProperties`)
- Release profile (`-Prelease`) added to `pom.xml` and a tag-triggered `release` job added to CI for Maven Central publishing under `com.bluntyrod`
- 92 passing tests including Testcontainers integration tests
- Spotless formatting enforced
- GitHub Actions CI green
- Published on Maven Central `0.0.1` (under old groupId `io.github.pandey-prateek`)
- Migrated to Bluntyrod organisation — all URLs, groupId, copyright updated

---

## What is TODO 🔲

### Phase 5 — Maven Central publish under com.bluntyrod

Code-side wiring is done (`release` Maven profile, GPG-sign-on-deploy, `central-publishing-maven-plugin`, tag-triggered CI `release` job). What remains is account/credential setup, which cannot be done from inside the repo:

- Create a Sonatype Central account and verify the `com.bluntyrod` namespace (DNS TXT record on `bluntyrod.com`)
- Generate a GPG keypair, publish the public key to a keyserver, and add `GPG_PRIVATE_KEY`, `GPG_PASSPHRASE`, `SONATYPE_USERNAME`, `SONATYPE_PASSWORD` as GitHub Actions secrets
- Push a `vX.Y.Z` tag to trigger the new `release` job and verify the artifact lands on Central
- `AiLensOtelExporter` — integrate properly with Spring Boot's OTel autoconfigure rather than `GlobalOpenTelemetry`

### Nice to have

- Webhook delivery currently fires on every `CHANGED` diff result with no retry/backoff — fine for low-volume alerting, but a dead-letter or retry policy would help under webhook outages
- `AiLensDashboardController` filtering is in-memory (`store.getAll()` + stream filter); fine at the default 500-event buffer size, but a `getInRange`/`getByModel` method on `EventStore` would let Redis/Postgres push the filter down instead

---

## Out of scope

- Multi-tenancy
- Dashboard authentication (consuming apps add Spring Security if needed)
- Log ingestion
- Sampling rate
- Cost calculation (pricing changes too often)

---

## Key decisions

| Decision | Rationale |
|---|---|
| AOP `@Around` not `BeanPostProcessor` | Less invasive, works with any `ChatModel` implementation transparently |
| Separate `StreamAdvisor` | AOP pointcut does not fire for reactive `Flux` returns; `StreamAdvisor` is the correct Spring AI hook |
| `Integer.MIN_VALUE` advisor order | Runs first in chain — sees raw request, captures complete response |
| `ArrayDeque` not `LinkedList` | Better cache locality, lower memory overhead for ring buffer pattern |
| Async Postgres writes | LLM calls are already slow; synchronous DB writes on the AOP advice would add latency |
| `leftPush` for Redis | Atomic O(1) cap with `LTRIM`; `getAll()` reverses for chronological order |
| SHA-256 truncated to 16 chars | 64-bit hash space; negligible collision probability at typical prompt volumes |
| Split auto-configuration | `Optional<StringRedisTemplate>` in method signature still triggers class loading — only `@ConditionalOnClass` at the configuration class level prevents it |
| Dashboard filtering done in-memory, not pushed to `EventStore` | Buffer is capped (default 500); pushing filters into Redis/Postgres adds complexity with little payoff at that scale |
| `PromptRegressionAlerter` webhook is fire-and-forget async | Alerting must never add latency or failure risk to the intercepted LLM call path |
| GPG-sign and Central-publish moved into a `release` Maven profile | Keeps `mvn install`/`mvn deploy` safe for contributors without a GPG key; only the tagged CI job activates `-Prelease` |

---

## Anomaly detection

Compares each call against a rolling baseline of all stored events. Requires `minBaselineCalls` (default 3) before activating. Flags if `current > avg * threshold` (default 2.0x). Both latency and token spikes can fire simultaneously → `LATENCY_AND_TOKEN_SPIKE`. Message includes actual vs average values.

## Prompt hashing

SHA-256 of normalised prompt (numbers → `N`, UUIDs → `UUID`, emails → `EMAIL`, lowercased, trimmed). First 16 hex characters used as fingerprint. Stored per model name in `ConcurrentHashMap`. `CHANGED` only fires on structural template changes, not variable data changes.

---

## Roadmap

- [x] Phase 1 — AOP interceptor + in-memory event store
- [x] Phase 2 — embedded dashboard + actuator endpoint
- [x] Phase 3 — anomaly detection + streaming advisor
- [x] Phase 3.5 — persistent storage (Redis + Postgres)
- [x] Phase 3.6 — prompt diff tracking
- [x] Phase 3.7 — OpenTelemetry + Micrometer integrations
- [x] Phase 4 — prompt diff viewer UI + regression alerts + per-model/time-range dashboard filtering
- [ ] Phase 5 — Maven Central publish under com.bluntyrod (code wiring done; account/credential setup is a manual, one-time human step)

exit code 0
Done
