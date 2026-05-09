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
- **Composed scope annotations** — `@Prototype` and `@Singelton` as shorthand meta-annotations
- **Scoped `@Bean` methods** — `@Bean(scope = ScopeType.PROTOTYPE)` supported directly on `@Bean`
- **Prototype injection via `Supplier<T>`** — request a new instance every time
- **Boxed wrapper injection** — `Integer`, `Long`, `String`, etc. can now be registered and injected as beans (via `@Bean` methods in `@Configuration`)
- **Circular dependency detection** — caught at startup with a clear error
- **Duplicate identifier detection** — two beans can't share the same name
- **`getBeanIdentifiers()`** — inspect all registered bean names at runtime
- **Rich error messages** — every exception tells you exactly which bean, which parameter, and why it failed

---

## Project structure

```
src/main/java/io/github/youssefrashidy/
├── annotations/         @Component, @Configuration, @Bean, @Inject, @Qualifier, @Scope, @Prototype, @Singelton, ScopeType
├── Context/             Core framework classes (scanner, graph, instantiator, container, resolver...)
├── Exceptions/          Typed exceptions for every failure mode
├── cases/               Manual usage examples / scratchpad
└── legacy/              Old monolithic implementations (kept for reference)
```

### Core pipeline (`Context/`)

| Class | Role |
|---|---|
| `AnnotationConfigApplicationContext` | Entry point — orchestrates the full startup pipeline |
| `ApplicationContext` | Interface exposing `getInstance(...)` and `getBeanIdentifiers()` |
| `ComponentScanner` | Classpath scan via ClassGraph → produces `ScanMap` |
| `ConfigurationClassProcessor` | Proxies `@Configuration` classes via ByteBuddy, extracts `@Bean` method definitions |
| `DependencyGraphBuilder` | Builds the adjacency graph + runs Kahn's topological sort |
| `BeanInstantiator` | Walks init order, constructs beans, registers them in the container |
| `BeanContainer` | The registry — stores beans by identifier and by type hierarchy |
| `DependencyResolver` | Resolution logic: type → impl, qualifier matching, identifier naming |
| `ScanMap` | Record: `resolveMap` + `components` (as `ComponentBeanDefinition`) + `configurationClasses` |
| `ConfigurationContext` | Record: ByteBuddy proxies map + `MethodBeanDefinition` list |
| `InstantiationContext` | Record: bundles `ScanMap`, `ConfigurationContext`, and `BeanContainer` for passing into `BeanInstantiator` internals |
| `BeanDefinition` | Sealed interface with three permits: `ComponentBeanDefinition`, `MethodBeanDefinition`, `DependencyBeanDefinition` |
| `ContextConfig` | Record wrapping the set of base packages to scan |

### Annotations

| Annotation | Target | Purpose |
|---|---|---|
| `@Component` | Type | Marks a class as a DI-managed bean |
| `@Configuration` | Type | Marks a class as a config source (meta-annotated with `@Component`) |
| `@Bean` | Method | Declares a bean inside a `@Configuration` class; supports `value` (identifier) and `scope` |
| `@Inject` | Constructor | Selects the injection constructor when multiple exist |
| `@Qualifier` | Type / Parameter | Names a bean or disambiguates an injection site |
| `@Scope` | Type | Sets scope (`SINGELTON` or `PROTOTYPE`) |
| `@Prototype` | Type | Composed shorthand for `@Scope(ScopeType.PROTOTYPE)` |
| `@Singelton` | Type | Composed shorthand for `@Scope(ScopeType.SINGELTON)` |

### Exceptions

Every failure mode has its own typed exception, all with detailed messages:

- `AmbiguousBeanException` — multiple candidates, no qualifier
- `AmbiguousConstructorException` — multiple `@Inject`-annotated constructors
- `BeanInstantiationException` — constructor or `@Bean` method threw during instantiation
- `CircularDependencyException` — cycle in the dependency graph
- `DuplicateBeanIdentifierException` — two beans with the same identifier
- `UnregisteredDependencyException` — requested bean doesn't exist or Supplier wraps a non-prototype
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
    @Bean("clock")
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
@Prototype   // or @Scope(ScopeType.PROTOTYPE)
public class SessionFactory { ... }

@Component
public class OrderService {
    @Inject
    public OrderService(Supplier<SessionFactory> sessionFactory) { ... }
    // sessionFactory.get() → new instance every time
}
```

### Prototype `@Bean` method

```java
@Configuration
public class AppConfig {
    @Bean(scope = ScopeType.PROTOTYPE)
    public RequestContext requestContext() {
        return new RequestContext();
    }
}
```

### Scanning multiple packages

```java
ApplicationContext ctx = new AnnotationConfigApplicationContext(
    Set.of("com.example.services", "com.example.config")
);
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

## Known rough edges
- `System.out.println` debug logs are still present in `DependencyGraphBuilder` — should use a proper logger or be removed
- `AnnotationBeanDefinition` is an orphaned record (no longer used anywhere in the framework) — should be deleted
- `DependencyGraphBuilder.buildMaps()` still has two near-identical loops for component beans and method beans
- The `registerBean(Class<?>, String, Supplier<?>)` overload on `BeanContainer` is never called from within the framework — dead API surface
