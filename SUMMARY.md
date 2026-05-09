# Mini-DI — Project Summary (Current State)

A dense technical summary of the project as it stands. Intended as a reference for ongoing work, not a user-facing doc.

---

## What it is

A hand-rolled DI container inspired by Spring, built entirely on reflection + ClassGraph + ByteBuddy. Constructor injection only. No field injection, no setter injection. The container is eagerly initialized at startup.

---

## Startup pipeline

```
AnnotationConfigApplicationContext(entryPoint | Set<String> packages)
    │
    ├─ ComponentScanner.scan(ContextConfig)
    │       → ClassGraph scan over base packages
    │       → populates: components (List<ComponentBeanDefinition>)
    │                    resolveMap (Map<interface, List<ComponentBeanDefinition>>)
    │                    configurationClasses (List<Class<?>>)
    │       → returns ScanMap record
    │
    ├─ ConfigurationClassProcessor.processConfigurationClasses(configurationClasses, beanContainer)
    │       → ByteBuddy-proxies each @Configuration class
    │       → intercepts @Bean methods:
    │           SINGLETON calls  → container lookup, return cached; else super.call()
    │           PROTOTYPE calls  → always super.call()
    │       → builds List<MethodBeanDefinition> (cls=return type, method, proxy, identifier, scope)
    │       → validates no duplicate identifiers among @Bean methods
    │       → returns ConfigurationContext record (proxies map + bean definitions)
    │
    ├─ DependencyGraphBuilder.buildInitializationOrder(scanMap, configurationContext)
    │       → for each ComponentBeanDefinition: resolve @Inject constructor, build edges to deps
    │       → for each MethodBeanDefinition: inspect @Bean method params, build edges
    │       → adjacency: Map<BeanDefinition, Set<BeanDefinition>>
    │       → runs Kahn's algorithm (topological sort)
    │       → throws CircularDependencyException if sort is incomplete
    │       → returns List<BeanDefinition> in init order
    │
    └─ BeanInstantiator.instantiateBeans(scanMap, configurationContext, initOrder, beanContainer)
            → wraps scanMap + configurationContext + beanContainer into InstantiationContext
            → switches on BeanDefinition type (sealed: ComponentBeanDefinition | MethodBeanDefinition)
            → ComponentBeanDefinition: finds 0-arg or @Inject constructor, resolves params, calls newInstance
            → MethodBeanDefinition: resolves method params, calls method.invoke(proxy, args)
            → scope via instantiateWithScope:
                PROTOTYPE → registers ThrowingFactory wrapped as Supplier<T> (new instance per get())
                SINGLETON → creates instance once, registers () -> instance
            → writes into BeanContainer
```

---

## Key data structures

**`ScanMap`** (record)
- `resolveMap: Map<Class<?>, List<ComponentBeanDefinition>>` — interface → list of implementing `@Component` definitions
- `components: List<ComponentBeanDefinition>` — all scanned `@Component` beans (excludes `@Configuration` classes)
- `configurationClasses: List<Class<?>>` — all `@Configuration` classes

**`ConfigurationContext`** (record)
- `proxies: Map<Class<?>, Object>` — config class → ByteBuddy proxy instance
- `beanDefinitions: List<MethodBeanDefinition>` — all `@Bean` method definitions

**`InstantiationContext`** (record) — introduced in this version
- Bundles `ScanMap`, `ConfigurationContext`, and `BeanContainer` into a single parameter object, passed through `BeanInstantiator`'s private methods to avoid long parameter lists.

**`BeanDefinition`** (sealed interface, permits `ComponentBeanDefinition | MethodBeanDefinition | DependencyBeanDefinition`)
- All three are records. All implement `getName()`, `cls()`, `identifier()`, `scope()`.
- `ComponentBeanDefinition(cls, scope, identifier)` — for `@Component` beans
- `MethodBeanDefinition(cls, beanMethod, proxy, identifier, scope)` — for `@Bean` method beans; `cls` is the method's declared return type; `scope` comes from `@Bean.scope()`
- `DependencyBeanDefinition(cls, scope, identifier)` — ephemeral; produced by `DependencyResolver.resolveDependencyBeanDefinition()` during graph building and instantiation to represent a resolved dependency edge; never registered in `BeanContainer` directly

> **Note:** `AnnotationBeanDefinition` is a leftover record that is no longer used anywhere in the framework. It should be deleted.

**`BeanContainer`**
- `beanRegistry: Map<String, Supplier<?>>` — identifier → supplier (prototype factory or singleton wrapped in lambda)
- `definitions: Map<String, BeanDefinition>` — for duplicate detection and name lookup
- `typeIndex: Map<Class<?>, List<String>>` — type (including superclasses and all interfaces) → list of identifiers; populated via recursive `registerTypeHierarchy`
- `getInstance(Class<T>)` — throws if empty or ambiguous, delegates to `getInstance(identifier, cls)` if exactly one hit
- `getBeanIdentifiers()` — returns unmodifiable view of all registered identifiers

---

## Bean identifier resolution

**`@Component` beans:** `@Qualifier.value()` if present on the class, else `cls.getSimpleName()`.

**`@Bean` methods:** `@Bean.value()` if non-empty, else `method.getName()`. `@Bean.value()` now defaults to `""`, so bare `@Bean` works without `@Bean(value = "")`.

**Injection site resolution** (`DependencyResolver`):
1. `resolveParamType(param, scanMap, ctx)` — unwraps `Supplier<T>`, validates the inner type has at least one prototype candidate; returns the raw injection type
2. `resolveParamIdentifier(param, paramType, scanMap, ctx)` — delegates to `resolveDependencyBeanDefinition`, returns the identifier
3. `resolveDependencyBeanDefinition(paramType, scanMap, ctx, param)` — the single unified resolution path:
    - collects all `ComponentBeanDefinition`s and `MethodBeanDefinition`s where `paramType.isAssignableFrom(def.cls())`
    - applies `@Qualifier` filter if present on the param
    - throws `AmbiguousBeanException` or `UnregisteredDependencyException` as appropriate
    - returns a `DependencyBeanDefinition` wrapping the resolved identifier

The old `resolveConcreteOrInterface` and the `ConfigurationContext`-taking overload of `resolveIdentifier` are still present but `@Deprecated` and unused — dead code to be removed.

---

## Scope handling

**SINGLETON** (default): instantiated once at startup, registered as `() -> instance`.

**PROTOTYPE**: registered as a `Supplier<T>` that calls the constructor/method factory on every `get()`. Should be injected as `Supplier<T>`. Injecting a prototype bean as a direct (non-Supplier) dependency is allowed, but it becomes singleton from the dependent's perspective— `resolveParamType` validates that any `Supplier<T>` injection site's inner type resolves to at least one prototype.

**`@Bean` scope**: set via `@Bean(scope = ScopeType.PROTOTYPE)`. `ConfigurationClassProcessor` reads `bean.scope()` directly; ByteBuddy interceptor checks `bean.scope() == ScopeType.PROTOTYPE` and always calls `super.call()` instead of doing a container lookup.

**Composed scope annotations**: `@Prototype` and `@Singelton` are meta-annotated with `@Scope`. Both `ComponentScanner.resolveScope()` and `BeanContainer.resolveScope()` walk the annotation's meta-annotations to find `@Scope`, so these shorthand annotations work transparently.

---

## Boxed wrapper injection

Primitive types (`byte`, `short`, `int`, `long`, `float`, `double`, `boolean`, `char`) are still in `UNRESOLVABLE` and rejected at graph build time. Boxed wrappers (`Integer`, `Long`, `String`, etc.) were removed from `UNRESOLVABLE` and can now be registered as `@Bean` method beans in `@Configuration` classes and injected normally.

---

## Known issues / things still to fix

### Code quality

1. **`DependencyGraphBuilder.buildMaps()` duplication** — the loop over `scanMap.components()` and the loop over `configurationContext.beanDefinitions()` are still near-identical. The Supplier-unwrapping, `UNRESOLVABLE` check, `isResolvable` check, and candidate resolution steps repeat verbatim. Should be unified over a shared `Iterable<BeanDefinition>`.

2. **`@Deprecated` dead code in `DependencyResolver`** — `resolveConcreteOrInterface` and the old `resolveIdentifier(cls, ctx, param, scanMap)` overload are marked `@Deprecated` but still present. Should be removed.

3. **`AnnotationBeanDefinition` orphan** — the record exists in `Context/` but is not referenced anywhere in the framework. Should be deleted.

4. **`System.out.println` debug logs in `DependencyGraphBuilder`** — scattered throughout `buildMaps()` and `topologicalSort()`. Should use SLF4J (transitively available via ClassGraph) or be removed.

5. **`BeanContainer.registerBean(Class<?>, String, Supplier<?>)` dead overload** — this overload is never called from within the framework. Should be removed to reduce API surface.

### Design

1. **`@Configuration` meta-annotated with `@Component`** — `ComponentScanner.isComponent()` returns `true` for config classes, so they must be excluded by checking `isConfiguration()` first. Fragile ordering dependency; the separation should be cleaner.

2. **Package naming** — `Context`, `Exceptions` use uppercase first letter. Java convention is all-lowercase.

3. **`cases/` and `legacy/` in `src/main`** — scratchpad and historical code living alongside the framework. Should be deleted or moved to a separate module / test scope.

4. **`MethodBeanDefinition.cls` vs actual runtime type** — `cls` is the method's declared return type. If the method returns an interface, `cls` is that interface, not the concrete type. `isResolvable()` and the resolution path use `type.isAssignableFrom(definition.cls())` to handle this. Worth an explicit note in Javadoc or a naming change (`declaredType`).

### Testing

1. Comprehensive test suite now organized into 12 groups (`group1`–`group12`) under `Context/comprehensive/`. The `miniProject/` suite remains alongside the older `simpleTests/` suite.

2. No test for a `@Configuration` + `PROTOTYPE` `@Bean` method that also takes parameters — likely still a gap.

---

## Dependencies worth knowing

- **ClassGraph** — does the classpath scan. Configured with `enableAllInfo()`. Could be narrowed once scan requirements stabilize.
- **ByteBuddy** — used only in `ConfigurationClassProcessor` to subclass `@Configuration` classes at runtime. If `@Configuration` support were dropped, ByteBuddy could be removed entirely.
- **JUnit 5** — test scope only.
