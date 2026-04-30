package io.github.youssefrashidy.Context;

import io.github.youssefrashidy.Exceptions.BeanMethodDependencyException;
import io.github.youssefrashidy.Exceptions.UnregisteredDependencyException;
import io.github.youssefrashidy.annotations.*;

import java.lang.reflect.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

public class BeanInstantiator {
    private final DependencyResolver dependencyResolver;

    public BeanInstantiator(DependencyResolver dependencyResolver) {
        this.dependencyResolver = dependencyResolver;
    }

    public void instantiateBeans(ScanMap scanMap, List<BeanDefinition> initOrder, BeanContainer beanContainer) {
        for (var beanDefinition : initOrder) {
            switch (beanDefinition) {
                case AnnotationBeanDefinition annotationBeanDefinition -> {
                    Class<?> cls = annotationBeanDefinition.cls();
                    Constructor<?>[] constructors = cls.getDeclaredConstructors();
                    if (constructors.length == 1 && constructors[0].getParameterCount() == 0) {
                        Constructor<?> constructor = constructors[0];
                        constructor.setAccessible(true);
                        resolveScope(cls, constructor, new Parameter[0], scanMap.resolveMap(), beanContainer, annotationBeanDefinition);
                    } else {
                        var annotatedConstructor = Arrays.stream(constructors)
                                .filter(c -> c.isAnnotationPresent(Inject.class))
                                .toArray(Constructor<?>[]::new)[0];
                        annotatedConstructor.setAccessible(true);
                        resolveScope(cls, annotatedConstructor, annotatedConstructor.getParameters(), scanMap.resolveMap(), beanContainer, annotationBeanDefinition);
                    }
                }
                case MethodBeanDefinition methodBeanDefinition -> {
                    Method method = methodBeanDefinition.beanMethod();
                    Object proxy = methodBeanDefinition.proxy();
                    String identifier = methodBeanDefinition.identifier();
                    ScopeType scope = method.getAnnotation(Bean.class).scope();
                    resolveMethodScope(identifier, method, proxy, method.getParameters(), scanMap.resolveMap(), beanContainer, scope, methodBeanDefinition);
                }
            }

        }
    }

    private void resolveScope(
            Class<?> cls,
            Constructor<?> constructor,
            Parameter[] params,
            Map<Class<?>, List<Class<?>>> resolveMap,
            BeanContainer beanContainer,
            BeanDefinition definition
    ) {
        try {
            String identifier = dependencyResolver.resolveIdentifier(cls);
            if (cls.isAnnotationPresent(Scope.class) && cls.getAnnotation(Scope.class).value().equals(ScopeType.PROTOTYPE)) {
                Supplier<?> supplier = () -> {
                    try {
                        ArrayList<Object> beans = new ArrayList<>();
                        for (var param : params) {
                            resolveParameter(param, beans, resolveMap, beanContainer);
                        }
                        return constructor.newInstance(beans.toArray());
                    } catch (InstantiationException | InvocationTargetException | IllegalAccessException e) {
                        throw new RuntimeException(e);
                    }
                };
                beanContainer.registerBean(definition, supplier);
            } else {
                ArrayList<Object> beans = new ArrayList<>();
                for (var param : params) {
                    resolveParameter(param, beans, resolveMap, beanContainer);
                }
                Object instance = constructor.newInstance(beans.toArray());
                beanContainer.registerBean(definition, () -> instance);
            }
        } catch (InvocationTargetException | InstantiationException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    private void resolveMethodScope(String identifier,
                                    Method method,
                                    Object proxy,
                                    Parameter[] params,
                                    Map<Class<?>, List<Class<?>>> resolveMap,
                                    BeanContainer beanContainer,
                                    ScopeType scope,
                                    BeanDefinition definition
    ) {
        try {
            String methodRef = method.getDeclaringClass().getName() + "#" + method.getName();
            String message = "DI error: configuration bean method '" + methodRef + "' (bean id '" + identifier +
                    "') could not resolve one or more dependencies.";
            if (scope == ScopeType.PROTOTYPE) {
                Supplier<?> supplier = () -> {
                    try {
                        ArrayList<Object> beans = new ArrayList<>();
                        for (var param : params) {
                            resolveParameter(param, beans, resolveMap, beanContainer);
                        }
                        if (beans.contains(null))
                            throw new BeanMethodDependencyException(message);

                        return method.invoke(proxy, beans.toArray());
                    } catch (InvocationTargetException | IllegalAccessException e) {
                        throw new RuntimeException(e);
                    }
                };
                beanContainer.registerBean(definition, supplier);
            } else {
                ArrayList<Object> beans = new ArrayList<>();
                for (var param : params) {
                    resolveParameter(param, beans, resolveMap, beanContainer);
                }
                if (beans.contains(null))
                    throw new BeanMethodDependencyException(message);

                Object instance = method.invoke(proxy, beans.toArray());
                beanContainer.registerBean(definition, () -> instance);
            }
        } catch (InvocationTargetException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }

    }

    private void resolveParameter(
            Parameter param,
            ArrayList<Object> beans,
            Map<Class<?>, List<Class<?>>> resolveMap,
            BeanContainer beanContainer
    ) {
        var type = param.getType();
        boolean isSupplier = type == Supplier.class;

        if (isSupplier) {
            Type generic = param.getParameterizedType();
            if (!(generic instanceof ParameterizedType)) {
                throw new UnregisteredDependencyException(
                        "DI error: Supplier parameter '" + param.getName() + "' must have a type argument e.g. Supplier<Foo>."
                );
            }
            type = (Class<?>) ((ParameterizedType) generic).getActualTypeArguments()[0];
            type = dependencyResolver.resolveParamType(param, type, resolveMap);
            if (!type.isAnnotationPresent(Scope.class) || type.getAnnotation(Scope.class).value() != ScopeType.PROTOTYPE) {
                throw new UnregisteredDependencyException(
                        "DI error: Supplier<" + type.getSimpleName() + "> wraps a non-prototype bean. " +
                                "Only prototype beans may be injected as Supplier."
                );
            }
        }

        if (type.isInterface()) {
            String identifier;
            if (param.isAnnotationPresent(Qualifier.class)) {
                identifier = param.getAnnotation(Qualifier.class).value();
            } else {
                var paramCls = resolveMap.get(type).getFirst();
                identifier = dependencyResolver.resolveIdentifier(paramCls);
            }
            Object resolved = isSupplier
                    ? (Supplier<?>) () -> beanContainer.getInstance(identifier)
                    : beanContainer.getInstance(identifier);
            beans.add(resolved);
            return;
        }

        String identifier = dependencyResolver.resolveIdentifier(type);
        Object resolved = isSupplier
                ? (Supplier<?>) () -> beanContainer.getInstance(identifier)
                : beanContainer.getInstance(identifier);
        beans.add(resolved);
    }
}
