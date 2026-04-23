package io.github.youssefrashidy.Context;

public interface ApplicationContext {
	<T> T getInstance(Class<T> cls);

	Object getInstance(String identifier);

	<T> T getInstance(String identifier, Class<T> expectedType);

	java.util.Set<String> getBeanIdentifiers();
}
