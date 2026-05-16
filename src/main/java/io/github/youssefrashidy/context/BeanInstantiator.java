package io.github.youssefrashidy.context;

import io.github.youssefrashidy.annotations.Inject;
import io.github.youssefrashidy.annotations.ScopeType;
import io.github.youssefrashidy.exceptions.BeanInstantiationException;
import io.github.youssefrashidy.exceptions.BeanMethodDependencyException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.*;
import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;

public class BeanInstantiator {
    private static final Logger logger = LoggerFactory.getLogger(BeanInstantiator.class);
    private final DependencyResolver dependencyResolver;

    @FunctionalInterface
    private interface ThrowingFactory {
        Object create() throws Exception;
    }

    public BeanInstantiator(DependencyResolver dependencyResolver) {
        this.dependencyResolver = dependencyResolver;
    }

    public void instantiateBeans(ScanMap scanMap, ConfigurationContext configurationContext,
                                 List<BeanDefinition> initOrder, BeanContainer beanContainer) {
        logger.info("Starting bean instantiation for {} definition(s)", initOrder.size());
        InstantiationContext ctx = new InstantiationContext(scanMap, configurationContext, beanContainer);
        for (var definition : initOrder) {
            logger.debug("Instantiating bean definition: {} ({})", definition.identifier(), definition.cls().getName());
            switch (definition) {
                case ComponentBeanDefinition componentBeanDefinition -> instantiateComponentBean(componentBeanDefinition, ctx);
                case MethodBeanDefinition methodBeanDefinition    -> instantiateMethodBean(methodBeanDefinition, ctx);
                default -> throw new RuntimeException("How did you get here ??") ; // shouldn't be instantiated
            }
        }
        logger.info("Bean instantiation completed");
    }

    private void instantiateComponentBean(ComponentBeanDefinition definition, InstantiationContext ctx) {
        Class<?> cls = definition.cls();
        Constructor<?>[] constructors = cls.getDeclaredConstructors();

        logger.trace("Selecting constructor for component bean {}", cls.getName());

        Constructor<?> constructor = constructors.length == 1 && constructors[0].getParameterCount() == 0
                ? constructors[0]
                : Arrays.stream(constructors)
                .filter(c -> c.isAnnotationPresent(Inject.class))
                .findFirst()
                .orElseThrow();

        logger.debug("Using constructor {} for component bean {}", constructor, cls.getName());

        constructor.setAccessible(true);
        Parameter[] params = constructor.getParameters();

        logger.trace("Component bean {} has {} constructor parameter(s)", cls.getName(), params.length);

        instantiateWithScope(definition, definition.scope(),
                () -> constructor.newInstance(resolveArgs(params, ctx)),
                ctx.beanContainer());
    }

    private void instantiateMethodBean(MethodBeanDefinition definition, InstantiationContext ctx) {
        Method method = definition.beanMethod();
        Object proxy = definition.proxy();
        Parameter[] params = method.getParameters();
        String methodRef = method.getDeclaringClass().getName() + "#" + method.getName();

        logger.debug("Instantiating @Bean method {} with {} parameter(s)", methodRef, params.length);

        instantiateWithScope(definition, definition.scope(), () -> {
            Object[] args = resolveArgs(params, ctx);
            if (Arrays.asList(args).contains(null)) {
                logger.warn("Bean method {} produced unresolved dependency arguments", methodRef);
                throw new BeanMethodDependencyException(
                        "DI error: configuration bean method '" + methodRef +
                                "' (bean id '" + definition.identifier() + "') could not resolve one or more dependencies."
                );
            }
            logger.trace("Invoking @Bean method {}", methodRef);
            return method.invoke(proxy, args);
        }, ctx.beanContainer());
    }

    private void instantiateWithScope(BeanDefinition definition, ScopeType scope,
                                      ThrowingFactory factory, BeanContainer beanContainer) {
        if (scope == ScopeType.PROTOTYPE) {
            logger.debug("Registering prototype bean {} ({})", definition.identifier(), definition.cls().getName());
            beanContainer.registerBean(definition, () -> invoke(factory));
        } else {
            logger.debug("Creating singleton bean {} ({})", definition.identifier(), definition.cls().getName());
            Object instance = invoke(factory);
            beanContainer.registerBean(definition, () -> instance);
        }
    }

    private Object invoke(ThrowingFactory factory) {
        try {
            return factory.create();
        } catch (InvocationTargetException e) {
            logger.error("Bean creation failed due to invocation target exception", e.getCause());
            throw new BeanInstantiationException(e.getCause());
        } catch (Exception e) {
            logger.error("Bean creation failed", e);
            throw new BeanInstantiationException(e);
        }
    }

    private Object[] resolveArgs(Parameter[] params, InstantiationContext ctx) {
        return Arrays.stream(params)
                .map(p -> resolveParameter(p, ctx))
                .toArray();
    }

    private Object resolveParameter(Parameter param, InstantiationContext ctx) {
        Class<?> type = dependencyResolver.resolveParamType(param, ctx.scanMap(), ctx.configurationContext());
        boolean isSupplier = param.getType() == Supplier.class;
        String identifier = dependencyResolver.resolveParamIdentifier(param, type, ctx.scanMap(), ctx.configurationContext());
        logger.trace("Resolved parameter '{}' as type {} with bean identifier '{}'{}",
                param.getName(), type.getName(), identifier, isSupplier ? " (supplier)" : "");
        return isSupplier
                ? (Supplier<?>) () -> ctx.beanContainer().getInstance(identifier)
                : ctx.beanContainer().getInstance(identifier);
    }
}