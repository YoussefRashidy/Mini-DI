package io.github.youssefrashidy.cases;

import io.github.youssefrashidy.Context.AnnotationConfigApplicationContext;
import io.github.youssefrashidy.Context.ApplicationContext;

public class MainPoint {
    public static void main(String[] args) {
        ApplicationContext app = new AnnotationConfigApplicationContext(MainPoint.class) ;
        Node node1 = (Node)app.getInstance("node") ;
        Node node2 = (Node)app.getInstance("node2") ;
        Node node3 = (Node)app.getInstance("node3") ;
        System.out.println("Success");
        System.out.println("%s %s".formatted(node1,node2));

    }

}
