package io.github.youssefrashidy.Context;

import io.github.youssefrashidy.Exceptions.BeanInstantiationException;
import io.github.youssefrashidy.Exceptions.BeanMethodDependencyException;
import io.github.youssefrashidy.annotations.Inject;
import io.github.youssefrashidy.annotations.ScopeType;

import java.lang.reflect.*;
import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;

public class BeanInstantiator {
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
        InstantiationContext ctx = new InstantiationContext(scanMap, configurationContext, beanContainer);
        for (var definition : initOrder) {
            switch (definition) {
                case ComponentBeanDefinition componentBeanDefinition -> instantiateComponentBean(componentBeanDefinition, ctx);
                case MethodBeanDefinition methodBeanDefinition    -> instantiateMethodBean(methodBeanDefinition, ctx);
                default -> throw new RuntimeException() ; // shouldn't be instantiated
            }
        }
    }

    private void instantiateComponentBean(ComponentBeanDefinition definition, InstantiationContext ctx) {
        Class<?> cls = definition.cls();
        Constructor<?>[] constructors = cls.getDeclaredConstructors();

        Constructor<?> constructor = constructors.length == 1 && constructors[0].getParameterCount() == 0
                ? constructors[0]
                : Arrays.stream(constructors)
                .filter(c -> c.isAnnotationPresent(Inject.class))
                .findFirst()
                .orElseThrow();

        constructor.setAccessible(true);
        Parameter[] params = constructor.getParameters();

        instantiateWithScope(definition, definition.scope(),
                () -> constructor.newInstance(resolveArgs(params, ctx)),
                ctx.beanContainer());
    }

    private void instantiateMethodBean(MethodBeanDefinition definition, InstantiationContext ctx) {
        Method method = definition.beanMethod();
        Object proxy = definition.proxy();
        Parameter[] params = method.getParameters();
        String methodRef = method.getDeclaringClass().getName() + "#" + method.getName();

        instantiateWithScope(definition, definition.scope(), () -> {
            Object[] args = resolveArgs(params, ctx);
            if (Arrays.asList(args).contains(null))
                throw new BeanMethodDependencyException(
                        "DI error: configuration bean method '" + methodRef +
                                "' (bean id '" + definition.identifier() + "') could not resolve one or more dependencies."
                );
            return method.invoke(proxy, args);
        }, ctx.beanContainer());
    }

    private void instantiateWithScope(BeanDefinition definition, ScopeType scope,
                                      ThrowingFactory factory, BeanContainer beanContainer) {
        if (scope == ScopeType.PROTOTYPE) {
            beanContainer.registerBean(definition, () -> invoke(factory));
        } else {
            Object instance = invoke(factory);
            beanContainer.registerBean(definition, () -> instance);
        }
    }

    private Object invoke(ThrowingFactory factory) {
        try {
            return factory.create();
        } catch (InvocationTargetException e) {
            throw new BeanInstantiationException(e.getCause());
        } catch (Exception e) {
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
        return isSupplier
                ? (Supplier<?>) () -> ctx.beanContainer().getInstance(identifier)
                : ctx.beanContainer().getInstance(identifier);
    }
}