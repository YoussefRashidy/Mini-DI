package io.github.youssefrashidy.Context.comprehensive.group2;

import io.github.youssefrashidy.Context.AnnotationConfigApplicationContext;
import io.github.youssefrashidy.Context.ApplicationContext;
import io.github.youssefrashidy.annotations.Component;
import io.github.youssefrashidy.annotations.Inject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Group 2 — class hierarchy with a single component on the subclass")
class Group2HierarchyTests {

    private ApplicationContext newContext() {
        return new AnnotationConfigApplicationContext(Group2HierarchyTests.class);
    }

    static class Animal {
        String sound() {
            return "...";
        }
    }

    @Component
    static class Dog extends Animal {
        @Override
        String sound() {
            return "Woof";
        }
    }

    @Component
    static class AnimalHouse{
        Animal animal ;
        @Inject
        public AnimalHouse(Animal animal) {
            this.animal = animal ;
        }
    }

    @Component
    static class VehicleHouse{
        Vehicle vehicle ;
        @Inject
        public VehicleHouse(Vehicle vehicle) {
            this.vehicle = vehicle ;
        }
    }

    static class Vehicle {
    }

    static class Car extends Vehicle {
    }

    @Component
    static class Sedan extends Car {
    }

    @Test
    @DisplayName("H2_T1 — retrieving the concrete subclass works")
    void retrieveConcreteTypeWorks() {
        ApplicationContext ctx = newContext();

        Dog dog = ctx.getInstance(Dog.class);
        assertNotNull(dog);
        assertEquals("Woof", dog.sound());
    }

    @Test
    @DisplayName("H2_T2 — supertype lookup resolves to the only registered subtype")
    void supertypeLookupResolvesToOnlySubtype() {
        ApplicationContext ctx = newContext();

        Animal animal = ctx.getInstance(Animal.class);
        assertInstanceOf(Dog.class, animal);
        assertEquals("Woof", animal.sound());
    }

    @Test
    @DisplayName("H2_T3 — multi-level hierarchy resolves to the single leaf component")
    void multiLevelHierarchyResolvesToSingleLeafComponent() {
        ApplicationContext ctx = newContext();

        Sedan sedan = ctx.getInstance(Sedan.class);
        Car car = ctx.getInstance(Car.class);
        Vehicle vehicle = ctx.getInstance(Vehicle.class);

        assertNotNull(sedan);
        assertSame(sedan, car);
        assertSame(sedan, vehicle);
    }

    @Test
    @DisplayName("H2_T3 — multi-level hierarchy resolves to the single leaf component")
    void multiLevelHierarchyResolvesToSingleLeafComponentPara() {
        ApplicationContext ctx = newContext();

        Sedan sedan = ctx.getInstance(Sedan.class);
        Car car = ctx.getInstance(Car.class);
        Vehicle vehicle = ctx.getInstance(Vehicle.class);
        VehicleHouse house = ctx.getInstance(VehicleHouse.class) ;

        assertNotNull(sedan);
        assertSame(sedan, car);
        assertSame(sedan, vehicle);
        assertSame(sedan, house.vehicle);
    }
}

