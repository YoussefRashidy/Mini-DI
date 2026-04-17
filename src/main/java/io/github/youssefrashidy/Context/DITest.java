package io.github.youssefrashidy.Context;

import io.github.youssefrashidy.annotations.Inject;
import io.github.youssefrashidy.annotations.Singelton;

import java.util.Set;

/**
 * A simple test class to demonstrate the DI context functionality.
 */
public class DITest {

    /**
     * A simple service dependency
     */
    @Singelton
    static class DatabaseService {
        public String connect() {
            return "Connected to database";
        }
    }

    /**
     * Another service dependency
     */
    @Singelton
    static class EmailService {
        public String sendEmail() {
            return "Email sent";
        }
    }

    /**
     * Application service that depends on the above services
     */
    @Singelton
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

        ApplicationContext context = new ApplicationContext(DITest.class);

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

