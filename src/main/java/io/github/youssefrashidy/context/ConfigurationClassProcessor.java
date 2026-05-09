package io.github.youssefrashidy.context;

import io.github.youssefrashidy.annotations.Bean;
import io.github.youssefrashidy.annotations.ScopeType;
import io.github.youssefrashidy.exceptions.DuplicateBeanIdentifierException;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.implementation.MethodDelegation;
import net.bytebuddy.implementation.bind.annotation.Origin;
import net.bytebuddy.implementation.bind.annotation.RuntimeType;
import net.bytebuddy.implementation.bind.annotation.SuperCall;
import net.bytebuddy.implementation.bind.annotation.This;
import net.bytebuddy.matcher.ElementMatchers;

import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

public class ConfigurationClassProcessor {
    public ConfigurationContext processConfigurationClasses(List<Class<?>> configurationClasses, BeanContainer container) {
        Map<Class<?>, Object> proxies = new HashMap<>();
        for (Class<?> cls : configurationClasses) {
            try {
                Class<?> proxyClass = new ByteBuddy()
                        .subclass(cls)
                        .method(ElementMatchers.isAnnotatedWith(Bean.class))
                        .intercept(MethodDelegation.to(new BeanInterceptor(container)))
                        .make()
                        .load(cls.getClassLoader())
                        .getLoaded();
                Object proxyInstance = proxyClass.getDeclaredConstructor().newInstance();
                proxies.put(cls, proxyInstance);
            } catch (Exception e) {
                throw new RuntimeException("Failed to proxy config class: " + cls.getName(), e);
            }
        }

        // Important here declared type is used which may not be concrete class so check it carefully since components are
        // assigned using their concrete classes
        List<MethodBeanDefinition> definitions = proxies.keySet().stream()
                .flatMap(cls -> Arrays.stream(cls.getMethods()))
                .filter(method -> method.isAnnotationPresent(Bean.class))
                .map(method -> new MethodBeanDefinition(
                        method.getReturnType(),
                        method,
                        proxies.get(method.getDeclaringClass()),
                        (method.isAnnotationPresent(Bean.class) && !method.getAnnotation(Bean.class).value().isEmpty())
                                ? method.getAnnotation(Bean.class).value() : method.getName(),
                        method.getAnnotation(Bean.class).scope()
                ))
                .collect(Collectors.toList());
        validateUniqueIdentifiers(definitions);
        return new ConfigurationContext(proxies, definitions);
    }

    private void validateUniqueIdentifiers(List<MethodBeanDefinition> definitions) {
        Set<String> identifiers = new HashSet<>();
        for (MethodBeanDefinition definition : definitions) {
            String identifier = definition.identifier();
            if (!identifiers.add(identifier)) {
                throw new DuplicateBeanIdentifierException(
                        "DI error: duplicate bean identifier '" + identifier + "' in configuration classes. " +
                                "Identifiers must be unique."
                );
            }
        }
    }

    public static class BeanInterceptor {
        private final BeanContainer container;

        BeanInterceptor(BeanContainer container) {
            this.container = container;
        }

        @RuntimeType
        public Object intercept(@Origin Method method, @SuperCall Callable<?> sup, @This Object proxy) throws Exception {
            Bean bean = method.getAnnotation(Bean.class);
            if (bean == null) {
                try {
                    Method original = method.getDeclaringClass()
                            .getSuperclass()
                            .getMethod(method.getName(), method.getParameterTypes());
                    bean = original.getAnnotation(Bean.class);
                } catch (NoSuchMethodException e) {
                    throw new IllegalStateException(
                            "DI error: failed to locate original @Bean method for '" +
                                    method.getDeclaringClass().getName() + "#" + method.getName() + "'.",
                            e
                    );
                }
            }

            if (bean != null && bean.scope() == ScopeType.PROTOTYPE) {
                return sup.call();
            }
            String beanIdentifier = (bean != null && !bean.value().isEmpty())
                    ? bean.value()
                    : method.getName();
            if (container.containsIdentifier(beanIdentifier)) return container.getInstance(beanIdentifier);
            else return sup.call();
        }
    }
}
