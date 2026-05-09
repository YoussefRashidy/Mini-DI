package io.github.youssefrashidy.context.simpleTests;

import io.github.youssefrashidy.context.AnnotationConfigApplicationContext;
import io.github.youssefrashidy.context.ApplicationContext;
import io.github.youssefrashidy.annotations.Inject;
import io.github.youssefrashidy.annotations.Component;
import io.github.youssefrashidy.annotations.Singleton;

/**
 * A simple test class to demonstrate the DI context functionality.
 */
public class DITest {

    /**
     * A simple service dependency
     */
    @Component
    @Singleton
    static class DatabaseService {
        public String connect() {
            return "Connected to database";
        }
    }

    /**
     * Another service dependency
     */
    @Component
    @Singleton
    static class EmailService {
        public String sendEmail() {
            return "Email sent";
        }
    }

    /**
     * Application service that depends on the above services
     */
    @Component
    @Singleton
    static class UserService {
        private DatabaseService databaseService;
        private EmailService emailService;

        @Inject
        public UserService(DatabaseService databaseService, EmailService emailService) {
            this.databaseService = databaseService;
            this.emailService = emailService;
        }

        public void createUser(String username) {
            System.out.println("Creating user: " + username);
            System.out.println(databaseService.connect());
            System.out.println(emailService.sendEmail());
        }
    }

    public static void main(String[] args) {
        System.out.println("=== Testing Dependency Injection Context ===\n");

        ApplicationContext context = new AnnotationConfigApplicationContext(DITest.class);

        // Test 1: Retrieve a service with dependencies
        System.out.println("Test 1: Creating UserService with dependencies...");
        UserService userService = context.getInstance(UserService.class);
        userService.createUser("John Doe");

        System.out.println("\n" + "=".repeat(50) + "\n");

        // Test 2: Retrieve a standalone service
        System.out.println("Test 2: Creating DatabaseService...");
        DatabaseService dbService = context.getInstance(DatabaseService.class);
        System.out.println(dbService.connect());

        System.out.println("\n" + "=".repeat(50) + "\n");

        // Test 3: Verify singleton behavior (same instance from registry)
        System.out.println("Test 3: Verifying singleton behavior...");
        UserService userService2 = context.getInstance(UserService.class);
        System.out.println("Same UserService instance: " + (userService == userService2));

        System.out.println("\nAll tests completed successfully!");
    }
}


