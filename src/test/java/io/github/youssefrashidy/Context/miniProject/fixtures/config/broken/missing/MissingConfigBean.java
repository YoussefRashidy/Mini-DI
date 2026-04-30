package io.github.youssefrashidy.Context.miniProject.fixtures.config.broken.missing;

public class MissingConfigBean {
    private final MissingDependency dependency;

    public MissingConfigBean(MissingDependency dependency) {
        this.dependency = dependency;
    }

    public MissingDependency getDependency() {
        return dependency;
    }
}

