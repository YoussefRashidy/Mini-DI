# Mini-DI — Project Summary (Refactoring Context)

This document is a dense technical summary of the project's current state, intended as context for the upcoming refactoring phase. Not a user-facing doc.

---

## What it is

A hand-rolled DI container inspired by Spring, built entirely on reflection + ClassGraph + ByteBuddy. Constructor injection only. No field injection, no setter injection. The container is eagerly initialized at startup.

---

## Startup pipeline

```
AnnotationConfigApplicationContext(entryPoint)
    │
    ├─ ComponentScanner.scan(ContextConfig)
    │       → ClassGraph scan over base packages
    │       → populates: componentList (List<Class<?>>)
    │                    resolveMap (Map<interface, List<impl>>)
    │                    configurationClasses (List<Class<?>>)
    │       → returns ScanMap record
    │
    ├─ ConfigurationClassProcessor.processConfigurationClasses(scanMap, beanContainer)
    │       → ByteBuddy-proxies each @Configuration class
    │       → intercepts @Bean methods: singleton calls → container lookup, prototype → super.call()
    │       → builds List<MethodBeanDefinition> (cls=return type, method, proxy, identifier)
    │       → validates no duplicate identifiers among @Bean methods
    │       → returns ConfigurationContext record (proxies map + bean definitions)
    │
    ├─ DependencyGraphBuilder.buildInitializationOrder(scanMap, configurationContext)
    │       → for each component: resolve its @Inject constructor, build edges to its deps
    │       → for each config bean: look at @Bean method params, build edges
    │       → adjacency: Map<BeanDefinition, Set<BeanDefinition>>
    │       → runs Kahn's algorithm (topological sort)
    │       → throws CircularDependencyException if sort is incomplete
    │       → returns List<BeanDefinition> in init order
    │
    └─ BeanInstantiator.instantiateBeans(scanMap, configurationContext, initOrder, beanContainer)
            → switches on BeanDefinition type (sealed: AnnotationBeanDefinition | MethodBeanDefinition)
            → for AnnotationBeanDefinition: finds constructor (0-arg or @Inject), resolves params, calls newInstance
            → for MethodBeanDefinition: resolves method params, calls method.invoke(proxy, args)
            → scope logic: PROTOTYPE → registers Supplier<T>; SINGLETON → creates instance, wraps in () -> instance
            → writes into BeanContainer
```

---

## Key data structures

**`ScanMap`** (record)
- `resolveMap: Map<Class<?>, List<Class<?>>>` — interface → list of implementing `@Component` classes
- `componentList: List<Class<?>>` — all scanned `@Component` classes
- `configurationClasses: List<Class<?>>` — all `@Configuration` classes

**`ConfigurationContext`** (record)
- `proxies: Map<Class<?>, Object>` — config class → ByteBuddy proxy instance
- `beanDefinitions: List<MethodBeanDefinition>` — all `@Bean` method definitions

**`BeanDefinition`** (sealed interface, permits `AnnotationBeanDefinition | MethodBeanDefinition`)
- Both are records. Both implement `getName()`, `cls()`, `identifier()`.
- `AnnotationBeanDefinition(cls, identifier)` — for `@Component` beans
- `MethodBeanDefinition(cls, beanMethod, proxy, identifier)` — for `@Bean` method beans. `cls` is the method's declared return type (not necessarily the concrete type).

**`BeanContainer`**
- `beanRegistry: Map<String, Supplier<?>>` — identifier → supplier (prototype or singleton wrapped in lambda)
- `definitions: Map<String, BeanDefinition>` — for duplicate detection and name lookup
- `typeIndex: Map<Class<?>, List<String>>` — type (including superclasses and interfaces) → list of identifiers; populated via `registerTypeHierarchy`
- `getInstance(Class<T>)` — throws if empty or ambiguous, delegates to `getInstance(identifier, cls)` if exactly one hit

---

## Bean identifier resolution

Identifier for `@Component` beans: `@Qualifier.value()` if present on the class, else `cls.getSimpleName()`.
Identifier for `@Bean` methods: `@Bean.value()` if non-empty, else `method.getName()`.

Injection site resolution is handled by `DependencyResolver`:
- If the param type is concrete → use `resolveIdentifier(cls, configurationContext, param)`
- If the param type is an interface → look in `resolveMap` (component impls) + `configurationContext.beanDefinitions()` (method bean return types), merge candidates, apply `@Qualifier` if present on the param

---

## Scope handling

**SINGLETON** (default): bean is instantiated once at startup, registered as `() -> instance`.
**PROTOTYPE**: registered as a `Supplier<T>` that calls the constructor/method on every `get()`. Can only be injected as `Supplier<T>` — injecting a prototype as a direct dependency is rejected with a clear error.

ByteBuddy intercepts `@Bean` method calls on `@Configuration` proxies:
- SINGLETON → checks container; if present, returns cached instance; else calls `super.call()`
- PROTOTYPE → always calls `super.call()` (bypasses cache)

---

## Known issues / things to fix in the refactor

### Code quality

1. **`DependencyResolver` method explosion** — there are 4 overloads of `resolveParamType` and 2 of `resolveParamIdentifier`, with significant copy-paste between them. The `ConfigurationContext`-aware overloads essentially repeat all the qualifier logic. Should be consolidated.

2. **`DependencyGraphBuilder.buildMaps()` duplication** — the loop over `scanMap.componentList()` and the loop over `configurationContext.beanDefinitions()` are near-identical (Supplier unwrapping, UNRESOLVABLE check, resolvability check, candidate resolution). Extract a shared method or unify into a single loop over all `BeanDefinition`s.

3. **`isResolvable()` redundancy** — `isResolvable = false` is assigned then immediately overwritten on the next line. Dead assignment.

4. **`BeanInstantiator.resolveScope` / `resolveMethodScope` duplication** — both methods have identical Supplier-wrapping logic for PROTOTYPE scope. Could be extracted.

5. **Debug `System.out.println` everywhere in `DependencyGraphBuilder`** — either wire up SLF4J (already in the classpath via ClassGraph's transitive deps) or remove. Not appropriate for a library.

### Design

6. **`@Bean.value()` has no default** — `@Bean(value = "")` is required even if you just want the method name. Spring uses `@Bean` with optional `value`. Fix: `String value() default ""`.

7. **`ScopeType.SINGELTON` typo** — misspelled. Should be `SINGLETON`. Breaking change but worth fixing before the API grows.

8. **`@Configuration` is meta-annotated with `@Component`** — this causes `ComponentScanner.isComponent()` to return `true` for config classes. They're currently excluded by checking `isConfiguration()` first in the scan loop. This is fragile; the separation should be cleaner.

9. **Package naming** — `Context`, `Exceptions` use uppercase. Java convention is all-lowercase. Also `Exceptions` → `exceptions` (or even fold into a single `exception` package).

10. **`cases/` and `legacy/` in `src/main`** — scratchpad and old code living alongside the framework. Should be deleted or moved to a separate module / test scope.

11. **`MethodBeanDefinition.cls` vs actual runtime type** — the `cls` field is the method's declared return type. If the method returns an interface (e.g., `Clock`), the actual instance is a concrete implementation, but the definition's `cls` is `Clock.class`. This is why `isResolvable()` uses `type.isAssignableFrom(definition.cls())` rather than equality. Worth documenting or enforcing a convention.

12. **`BeanContainer.registerBean(Class<?>, String, Supplier<?>)` overload** — this overload exists but is never called from within the framework (it was presumably from an older API). Consider removing to reduce surface area.

### Testing

13. Test fixtures are in a `miniProject/` subdirectory with their own `fixtures/` package tree — reasonable, but the `simpleTests/` suite mixes fixture classes with test classes in the same package. Could be better organized.

14. No test for the `@Configuration` + PROTOTYPE `@Bean` method combination where the method has parameters — likely a gap.

---

## Dependencies worth knowing

- **ClassGraph** — does the classpath scan. Configured with `enableAllInfo()` which is heavy (loads all annotations, methods, etc.). Could be narrowed once the scan requirements are stable.
- **ByteBuddy** — used only in `ConfigurationClassProcessor` to subclass `@Configuration` classes at runtime. The proxy intercepts `@Bean` method calls for singleton caching. If `@Configuration` support were dropped, ByteBuddy could be removed entirely.
- **JUnit 5** — test scope only.

---

## What the refactored architecture should look like

Per the existing `refactor.txt` notes, the intended pipeline is:

```
Scan → Build dependency graph → Instantiate
```

With clear ownership:
- `ComponentScanner` → produces `ScanMap`
- `ConfigurationClassProcessor` → produces `ConfigurationContext`  
- `DependencyGraphBuilder` → consumes both, produces `List<BeanDefinition>` (init order)
- `BeanInstantiator` → consumes init order + scan results, writes into `BeanContainer`
- `BeanContainer` → pure registry, no business logic
- `DependencyResolver` → stateless utility, shared by graph builder and instantiator
- `ApplicationContext` (interface) → implemented by `AnnotationConfigApplicationContext`

This is essentially already the structure — the refactor is more about cleaning up the internals of each class than restructuring the pipeline itself.
