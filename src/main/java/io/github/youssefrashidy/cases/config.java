package io.github.youssefrashidy.cases;

import io.github.youssefrashidy.annotations.Bean;
import io.github.youssefrashidy.annotations.Configuration;
import io.github.youssefrashidy.annotations.Qualifier;

@Configuration
public class config {
    @Bean("")
    public Node node() {
        return new Node() ;
    }

    @Bean("node2")
    public Node node2() {
        return new Node() ;
    }

    @Bean("node3")
    public Node node3(@Qualifier("node2") Node node) {
        return new Node() ;
    }


}
