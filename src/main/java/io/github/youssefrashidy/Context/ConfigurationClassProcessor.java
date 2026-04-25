package io.github.youssefrashidy.Context;

import io.github.youssefrashidy.annotations.Bean;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.implementation.MethodDelegation;
import net.bytebuddy.implementation.bind.annotation.Origin;
import net.bytebuddy.implementation.bind.annotation.RuntimeType;
import net.bytebuddy.implementation.bind.annotation.SuperCall;
import net.bytebuddy.implementation.bind.annotation.This;
import net.bytebuddy.matcher.ElementMatchers;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

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
        List<BeanDefinition> definitions = proxies.keySet().stream()
                .flatMap(cls -> Arrays.stream(cls.getMethods()))
                .filter(method -> method.isAnnotationPresent(Bean.class))
                .map(method -> new BeanDefinition(method.getReturnType().getClass(),(method.isAnnotationPresent(Bean.class) &&
                        !method.getAnnotation(Bean.class).value().isEmpty()) ?
                        method.getAnnotation(Bean.class).value() : method.getName()))
                .toList();
        return new ConfigurationContext(proxies, definitions);
    }

    private static class BeanInterceptor {
        private final BeanContainer container;

        BeanInterceptor(BeanContainer container) {
            this.container = container;
        }

        @RuntimeType
        public Object intercept(@Origin Method method, @SuperCall Callable<?> sup, @This Object proxy) throws Exception {
            String beanIdentifier = (method.isAnnotationPresent(Bean.class) && !method.getAnnotation(Bean.class).value().isEmpty()) ?
                    method.getAnnotation(Bean.class).value() : method.getName();
            if (container.containsIdentifier(beanIdentifier)) return container.getInstance(beanIdentifier);
            else return sup.call();
        }
    }
}
