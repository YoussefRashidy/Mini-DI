package io.github.youssefrashidy.context;

public interface ApplicationContext {
	<T> T getInstance(Class<T> cls);

	Object getInstance(String identifier);

	<T> T getInstance(String identifier, Class<T> expectedType);

	java.util.Set<String> getBeanIdentifiers();
}
