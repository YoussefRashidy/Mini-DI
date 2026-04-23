package io.github.youssefrashidy.Context;

import java.util.Set;

public class AnnotationConfigApplicationContext implements ApplicationContext {
	private final ContextConfig config;
	private final BeanContainer beanContainer;

	public AnnotationConfigApplicationContext(Set<String> paths) {
		this.config = new ContextConfig(paths);
		this.beanContainer = new BeanContainer();
		initializeContext();
	}

	public AnnotationConfigApplicationContext(Class<?> entryPoint) {
		this.config = new ContextConfig(Set.of(entryPoint.getPackageName()));
		this.beanContainer = new BeanContainer();
		initializeContext();
	}

	private void initializeContext() {
		ComponentScanner scanner = new ComponentScanner();
		DependencyResolver resolver = new DependencyResolver();
		DependencyGraphBuilder graphBuilder = new DependencyGraphBuilder(resolver);
		BeanInstantiator beanInstantiator = new BeanInstantiator(resolver);

		ScanMap scanMap = scanner.scan(config);
		var initOrder = graphBuilder.buildInitializationOrder(scanMap);
		beanInstantiator.instantiateBeans(scanMap, initOrder, beanContainer);
	}

	@Override
	public <T> T getInstance(Class<T> cls) {
		return beanContainer.getInstance(cls);
	}

	@Override
	public Object getInstance(String identifier) {
		return beanContainer.getInstance(identifier);
	}

	@Override
	public <T> T getInstance(String identifier, Class<T> expectedType) {
		return beanContainer.getInstance(identifier, expectedType);
	}

	@Override
	public Set<String> getBeanIdentifiers() {
		return beanContainer.getBeanIdentifiers();
	}
}
