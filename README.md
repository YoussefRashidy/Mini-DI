# Mini-DI

A nano Spring / DI framework built from scratch in Java — annotation-driven, constructor-injection-only, with full support for `@Configuration` classes, scoping, and qualifier-based disambiguation.

This is a learning project, not a production library. The goal is to understand how DI containers actually work under the hood — no magic, just reflection, a dependency graph, and a topological sort.

---

## What it does

Mini-DI scans a base package, finds all beans (via `@Component` or `@Configuration`), builds a dependency graph, resolves the correct instantiation order via Kahn's algorithm, and wires everything together into a `BeanContainer` you can query.

Supported features:

- **Component scanning** via ClassGraph
- **Constructor injection** (annotate with `@Inject` if there's more than one constructor)
- **Interface resolution** — inject an interface, get the right implementation
- **`@Qualifier`** — disambiguate when multiple implementations exist for the same interface
- **`@Configuration` + `@Bean` methods** — define beans programmatically, Spring-style
- **Singleton scope** (default) and **Prototype scope** (`@Scope(ScopeType.PROTOTYPE)`)
- **Prototype injection via `Supplier<T>`** — request a new instance every time
- **Circular dependency detection** — caught at startup with a clear error
- **Duplicate identifier detection** — two beans can't share the same name
- **Rich error messages** — every exception tells you exactly which bean, which parameter, and why it failed

---

## Project structure

```
src/main/java/io/github/youssefrashidy/
├── annotations/         @Component, @Configuration, @Bean, @Inject, @Qualifier, @Scope, ScopeType
├── Context/             Core framework classes (scanner, graph, instantiator, container, resolver...)
├── Exceptions/          Typed exceptions for every failure mode
├── cases/               Manual usage examples / scratchpad
└── legacy/              Old monolithic implementations (kept for reference)
```

### Core pipeline (`Context/`)

| Class | Role |
|---|---|
| `AnnotationConfigApplicationContext` | Entry point — orchestrates the full startup pipeline |
| `ApplicationContext` | Interface exposing `getInstance(...)` |
| `ComponentScanner` | Classpath scan via ClassGraph → produces `ScanMap` |
| `ConfigurationClassProcessor` | Proxies `@Configuration` classes via ByteBuddy, extracts `@Bean` method definitions |
| `DependencyGraphBuilder` | Builds the adjacency graph + runs Kahn's topological sort |
| `BeanInstantiator` | Walks init order, constructs beans, registers them in the container |
| `BeanContainer` | The registry — stores beans by identifier and by type hierarchy |
| `DependencyResolver` | Resolution logic: interface → impl, qualifier matching, identifier naming |
| `ScanMap` | Record carrying the interface→impls map + component list + config classes |
| `ConfigurationContext` | Record carrying ByteBuddy proxies + `MethodBeanDefinition` list |
| `BeanDefinition` | Sealed interface with two permits: `AnnotationBeanDefinition` and `MethodBeanDefinition` |

### Annotations

| Annotation | Target | Purpose |
|---|---|---|
| `@Component` | Type | Marks a class as a DI-managed bean |
| `@Configuration` | Type | Marks a class as a config source (meta-annotated with `@Component`) |
| `@Bean` | Method | Declares a bean inside a `@Configuration` class |
| `@Inject` | Constructor | Selects the injection constructor when multiple exist |
| `@Qualifier` | Type / Parameter | Names a bean or disambiguates an injection site |
| `@Scope` | Type | Sets scope (`SINGLETON` or `PROTOTYPE`) |

### Exceptions

Every failure mode has its own typed exception, all with detailed messages:

- `AmbiguousBeanException` — multiple candidates, no qualifier
- `AmbiguousConstructorException` — multiple `@Inject`-annotated constructors
- `CircularDependencyException` — cycle in the dependency graph
- `DuplicateBeanIdentifierException` — two beans with the same identifier
- `UnregisteredDependencyException` — requested bean doesn't exist
- `UnmetBeanDependencyException` — bean method got a `null` dependency
- `BeanMethodDependencyException` — `@Bean` method dependency resolution failure

---

## Quick start

```java
// Your components
@Component
public class TaxCalculator { ... }

@Component
public class OrderService {
    @Inject
    public OrderService(TaxCalculator taxCalculator) { ... }
}

// Boot the context
ApplicationContext ctx = new AnnotationConfigApplicationContext(OrderService.class);
OrderService svc = ctx.getInstance(OrderService.class);
```

### With a `@Configuration` class

```java
@Configuration
public class AppConfig {
    @Bean(value = "clock")
    public Clock clock() {
        return Clock.systemUTC();
    }
}

ApplicationContext ctx = new AnnotationConfigApplicationContext(AppConfig.class);
Clock c = ctx.getInstance("clock", Clock.class);
```

### Multiple implementations + qualifier

```java
@Component @Qualifier("stripe")
public class StripeGateway implements IPaymentGateway { ... }

@Component @Qualifier("paypal")
public class PaypalGateway implements IPaymentGateway { ... }

@Component
public class PaymentProcessor {
    @Inject
    public PaymentProcessor(@Qualifier("stripe") IPaymentGateway gateway) { ... }
}
```

### Prototype scope

```java
@Component
@Scope(ScopeType.PROTOTYPE)
public class SessionFactory { ... }

@Component
public class OrderService {
    @Inject
    public OrderService(Supplier<SessionFactory> sessionFactory) { ... }
    // sessionFactory.get() → new instance every time
}
```

---

## Dependencies

| Library | Version | Purpose |
|---|---|---|
| ClassGraph | 4.8.179 | Fast classpath scanning |
| ByteBuddy | 1.14.14 | Runtime proxy generation for `@Configuration` classes |
| JUnit 5 | 5.8.1 | Testing |

Java 24, Maven.

---

## What's coming (refactor phase)

The codebase works but has some rough edges worth cleaning up. Key areas planned for refactoring:

- `DependencyResolver` has significant code duplication across its overloaded `resolveParamType` / `resolveParamIdentifier` methods — needs consolidation
- `DependencyGraphBuilder.buildMaps()` processes `@Component` beans and `@Bean` method beans in two near-identical loops — should be unified
- `BeanInstantiator` mixes scope resolution logic into instantiation — scope handling could be extracted
- `@Bean.value()` has no default, forcing `@Bean(value = "")` — should default to `""`
- `ScopeType.SINGELTON` is a typo (should be `SINGLETON`) — a breaking rename, but worth it
- Package naming uses `Context` (capital C) — Java convention says lowercase
- The `cases/` and `legacy/` directories are scratchpad code living in `main` — should be moved or removed
- `System.out.println` debug logs are scattered throughout the graph builder — should be a proper logger or removed
- `isResolvable()` in `DependencyGraphBuilder` has a redundant `isResolvable = false` assignment before being immediately overwritten
