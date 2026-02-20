package cncs.academy.ess;

import cncs.academy.ess.controller.AuthorizationMiddleware;
import cncs.academy.ess.controller.TodoController;
import cncs.academy.ess.controller.TodoListController;
import cncs.academy.ess.controller.UserController;
import cncs.academy.ess.repository.memory.InMemoryTodoListsRepository;
import cncs.academy.ess.repository.memory.InMemoryTodoRepository;
import cncs.academy.ess.repository.memory.InMemoryUserRepository;
import cncs.academy.ess.repository.sql.SQLListShareRepository;
import cncs.academy.ess.repository.sql.SQLTodoListsRepository;
import cncs.academy.ess.repository.sql.SQLTodoRepository;
import cncs.academy.ess.repository.sql.SQLUserRepository;
import cncs.academy.ess.service.TodoListsService;
import cncs.academy.ess.service.TodoUserService;
import cncs.academy.ess.service.TodoService;
import io.javalin.Javalin;
import io.javalin.community.ssl.SslPlugin;
import org.apache.commons.dbcp2.BasicDataSource;
import org.casbin.jcasbin.main.Enforcer;

import java.io.File;

public class App {
    public static void main(String[] args) throws Exception {
        /*SslPlugin sslPlugin = new SslPlugin(conf -> {
            conf.pemFromPath("cert.pem", "key.pem");
            conf.securePort = 7100;
        });*/

        String modelPath;
        String policyPath;

        File modelFile = new File("/app/casbin/model.conf");
        if(modelFile.exists()){
            modelPath = "/app/casbin/model.conf";
            policyPath = "/app/casbin/policy.csv";
        } else {
            modelPath = App.class.getClassLoader().getResource("casbin/model.conf").getPath();
            policyPath = App.class.getClassLoader().getResource("casbin/policy.csv").getPath();
        }

        Enforcer enforcer = new Enforcer(modelPath, policyPath);

        Javalin app = Javalin.create(config -> {
            config.bundledPlugins.enableCors(cors -> {
                cors.addRule(it -> {
                    it.anyHost();
                });
            });
            //config.registerPlugin(sslPlugin);
        }).start(7100);

        BasicDataSource ds = new BasicDataSource();
        ds.setDriverClassName("org.postgresql.Driver");
        String connectURI = String.format("jdbc:postgresql://%s:%s/%s?user=%s&password=%s", "localhost", "5432", "postgres", "postgres", "qwerty12345");
        ds.setUrl(connectURI);

        // Initialize routes for user management

        InMemoryUserRepository userRepository = new InMemoryUserRepository();
        //SQLUserRepository userRepository = new SQLUserRepository(ds);
        TodoUserService userService = new TodoUserService(userRepository);
        UserController userController = new UserController(userService);

        InMemoryTodoListsRepository listsRepository = new InMemoryTodoListsRepository();
        //SQLTodoListsRepository listsRepository = new SQLTodoListsRepository(ds);
        SQLListShareRepository listShareRepository = new SQLListShareRepository(ds);
        TodoListsService toDoListService = new TodoListsService(listsRepository, listShareRepository);
        TodoListController todoListController = new TodoListController(toDoListService);

        InMemoryTodoRepository todoRepository = new InMemoryTodoRepository();
        //SQLTodoRepository todoRepository = new SQLTodoRepository(ds);
        TodoService todoService = new TodoService(todoRepository, listsRepository);
        TodoController todoController = new TodoController(todoService, toDoListService);

        AuthorizationMiddleware authMiddleware = new AuthorizationMiddleware(enforcer);

        // CORS
        app.before(ctx -> {
            ctx.header("Access-Control-Allow-Origin", "*");
            ctx.header("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
            ctx.header("Access-Control-Allow-Headers", "*");
        });
        // Authorization middleware
        app.before(authMiddleware::handle);

        // User management
        app.post("/user", userController::createUser);
        app.get("/user/{userId}", userController::getUser);
        app.delete("/user/{userId}", userController::deleteUser);
        app.post("/login", userController::loginUser);

        // "To do" lists management
        /* POST /todolist
          {
              "listName": "Shopping list"
          }
         */
        app.post("/todolist", todoListController::createTodoList);
        app.get("/todolist", todoListController::getAllTodoLists);
        app.get("/todolist/{listId}", todoListController::getTodoList);

        // Rotas de partilha de listas
        app.post("/todolist/{listId}/share", todoListController::shareTodoList);
        app.delete("/todolist/{listId}/share/{userId}", todoListController::revokeShareTodoList);

        // "To do" list items management
        /* POST /todo/item
          {
              "description": "Buy milk",
              "listId": 1
          }
         */
        app.post("/todo/item", todoController::createTodoItem);
        /* GET /todo/1/tasks */
        app.get("/todo/{listId}/tasks", todoController::getAllTodoItems);
        /* GET /todo/1/tasks/1 */
        app.get("/todo/{listId}/tasks/{taskId}", todoController::getTodoItem);
        /* DELETE /todo/1/tasks/1 */
        app.delete("/todo/{listId}/tasks/{taskId}", todoController::deleteTodoItem);

        fillDummyData(userService, toDoListService, todoService);
    }

    private static void fillDummyData(
            TodoUserService userService,
            TodoListsService toDoListService,
            TodoService todoService) throws Exception {
        userService.addUser("user1", "password1");
        userService.addUser("user2", "password2");
        toDoListService.createTodoListItem("Shopping list", 1);
        toDoListService.createTodoListItem( "Other", 1);
        todoService.createTodoItem("Bread", 1);
        todoService.createTodoItem("Milk", 1);
        todoService.createTodoItem("Eggs", 1);
        todoService.createTodoItem("Cheese", 1);
        todoService.createTodoItem("Butter", 1);
    }
}
