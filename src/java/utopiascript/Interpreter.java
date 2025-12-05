package utopiascript;

import java.util.ArrayList;
import java.util.List;

class Interpreter implements Expr.Visitor<Object>, Stmt.Visitor<Void> {
    // fixed reference to outermost global environment
    final Environment globals = new Environment();
    // current environemnt
    private Environment environment = globals;

    Interpreter() {
        // define a library function for determining time
        globals.define("clock", new UtopiaScriptCallable() {
            @Override
            public int arity() { return 0; }

            @Override
            public Object call(Interpreter interpreter, List<Object> arguments) {
                return (double)System.currentTimeMillis() / 1000.0;
            }

            @Override
            public String toString() { return "<native fn>"; };
        });
    }

    void interpret(List<Stmt> statements){
        try {
            for (Stmt statement : statements){
                execute(statement);
            }
        } catch(RuntimeError error) {
            UtopiaScript.runtimeError(error);
        }
    }

    // Stringifies an object for output
    private String stringify(Object object) {
        if (object == null) return "nenio";
        if (object == Boolean.TRUE) return "vera";
        if (object == Boolean.FALSE) return "malvera";
    
        if (object instanceof Double){
            String text = object.toString();
            if (text.endsWith(".0")){
                text = text.substring(0, text.length() - 2);
            }
            return text;
        }

        return object.toString();
    }


    @Override
    public Object visitLiteralExpr(Expr.Literal expr){
        return expr.value;
    }

    // Interprets a logical expression
    @Override
    public Object visitLogicalExpr(Expr.Logical expr) {
        Object left = evaluate(expr.left);

        // if "or" and first expression is true, it's true
        if (expr.operator.type == TokenType.AU) {
            if (isTruthy(left)) {
                return true;
            }
        } 
        // if "and" and first expression is false, it's false
        else {
            if (!isTruthy(left)) {
                return left;
            }
        }

        return evaluate(expr.right);
    }

    @Override
    public Object visitGroupingExpr(Expr.Grouping expr){
        return evaluate(expr.expression);
    }

    // Interprets a unary expression
    // A unary is of the form !x or -x
    @Override
    public Object visitUnaryExpr(Expr.Unary expr){
        Object right = evaluate(expr.right);

        switch (expr.operator.type){
            case MINUS:
                checkNumberOperand(expr.operator, right);
                return -(double)right;
            case BANG:
                return !isTruthy(right);
            default:
                break;
        }

        return null;
    }

    private void checkNumberOperand(Token operator, Object operand){
        if (operand instanceof Double) return;
        throw new RuntimeError(operator, "Operand must be a number.");
    }

    private void checkNumberOperand(Token operator, Object left, Object right){
        if (left instanceof Double && right instanceof Double) return;
        throw new RuntimeError(operator, "Operands must be numbers.");
    }

    // Interprets a binary expression
    // A binary expression is of the form: left operator right
    @Override
    public Object visitBinaryExpr(Expr.Binary expr){
        Object right = evaluate(expr.right);
        Object left = evaluate(expr.left);

        switch(expr.operator.type) {
            case GREATER:
                checkNumberOperand(expr.operator, left, right);
                return (double)left > (double)right;

            case GREATER_EQUAL:
                checkNumberOperand(expr.operator, left, right);
                return (double)left >= (double)right;

            case LESS:
                checkNumberOperand(expr.operator, left, right);
                return (double)left < (double)right;

            case LESS_EQUAL:
                checkNumberOperand(expr.operator, left, right);
                return (double)left <= (double)right;

            case MINUS:
                checkNumberOperand(expr.operator, left, right);
                return (double)left - (double)right;

            case PLUS:
                if (left instanceof Double && right instanceof Double){
                    return (double)left + (double)right;
                }
                if (left instanceof String && right instanceof String){
                    return (String)left + (String)right;
                }
                if (left instanceof String) {
                    return (String)left + stringify(right);
                }
                if (right instanceof String){
                    return stringify(left) + (String)right;
                }

                throw new RuntimeError(expr.operator, "Operands must be two numbers or two strings.");

            case SLASH:
                checkNumberOperand(expr.operator, left, right);
                if ((double)right == 0.0){
                    throw new RuntimeError(expr.operator, "Cannot divide by zero.");
                }
                return (double)left / (double)right;

            case STAR:
                checkNumberOperand(expr.operator, left, right);
                return (double)left * (double)right;

            case BANG_EQUAL: return !isEqual(left, right);

            case EQUAL_EQUAL: return isEqual(left, right);

            default:
                break;
        }

        return null;
    }

    private Object evaluate(Expr expr){
        return expr.accept(this);
    }

    private void execute(Stmt stmt){
        stmt.accept(this);
    }

    @Override
    public Void visitExpressionStmt(Stmt.Expression stmt){
        evaluate(stmt.expression);
        return null;
    }

    // Interprets a print statement
    @Override
    public Void visitPrintStmt(Stmt.Print stmt){
        Object value = evaluate(stmt.expression);
        System.out.println(stringify(value));
        return null;
    }

    // Helper to check if an object should be evaluated to true or false
    private boolean isTruthy(Object object){
        if (object == null) return false;
        if (object instanceof Boolean) return (boolean)object;

        return true;
    }

    // Helper to check if two objects should be considered equal or not
    private boolean isEqual(Object a, Object b) {
        if (a == null && b == null) return true;
        if (a == null) return false;
        return a.equals(b);
    }

    // Interprets a variable expression
    // Grabs the value for the variable from the environment's map of values to values
    @Override 
    public Object visitVariableExpr(Expr.Variable expr) {
        Object value = environment.get(expr.name);
        if (value == null) {
            throw new RuntimeError(expr.name, "Cannot access a variable that has not been initialized or assigned to");
        }
        return value;
    }

    // Interprets a definition of a variable
    // Evaluates the value and stores into the environment's map of vars to values
    @Override
    public Void visitVarStmt(Stmt.Var stmt) {
        Object value = null;

        if (stmt.initializer != null) {
            value = evaluate(stmt.initializer);
        }

        environment.define(stmt.name.lexeme, value);
        return null;
    }

    // Interprets an assignment expression
    @Override
    public Object visitAssignExpr(Expr.Assign expr) {
        Object value = evaluate(expr.value);
        environment.assign(expr.name, value);
        return value;
    }

    @Override
    public Void visitBlockStmt(Stmt.Block stmt) {
        executeBlock(stmt.statements, new Environment(environment));
        return null;
    }

    void executeBlock(List<Stmt> statements, Environment environment) {
        // hold outer scope environment
        Environment previous = this.environment;

        try {
            // set current scope environment
            this.environment = environment;
            for (Stmt statement : statements) {
                execute(statement);
            }
        } finally {
            // restore outer scope environment after inner scope is done
            this.environment = previous;
        }
    }

    // Interprets an if statement
    @Override
    public Void visitIfStmt(Stmt.If stmt) {
        if (isTruthy(evaluate(stmt.condition))) {
            execute(stmt.thenBranch);
        } else if (stmt.elseBranch != null) {
            execute(stmt.elseBranch);
        }

        return null;
    }

    // Interprets a while statement
    @Override
    public Void visitWhileStmt(Stmt.While stmt) {
        while (isTruthy(evaluate(stmt.condition))) {
            execute(stmt.body);
        }

        return null;
    }

    // Interprets a function call
    @Override
    public Object visitCallExpr(Expr.Call expr) {
        Object callee = evaluate(expr.callee);

        // evaluate and store the argument values
        List<Object> arguments = new ArrayList<>();
        for (Expr argument : expr.arguments) {
            arguments.add(evaluate(argument));
        }

        // check if the object is even callable
        if (!(callee instanceof UtopiaScriptCallable)) {
            throw new RuntimeError(expr.paren, 
                "Can only call functions and classes");
        }

        UtopiaScriptCallable function = (UtopiaScriptCallable)callee;

        // check that the number of parameters is equal to the number of arguments
        if (arguments.size() != function.arity()) {
            throw new RuntimeError(expr.paren, "Expected " + 
            function.arity() + " arguments but got " +
            arguments.size() + ".");
        }

        return function.call(this, arguments);
    } 

    // Interprets a function declaration
    @Override
    public Void visitFunctionStmt(Stmt.Function stmt) {
        UtopiaScriptFunction function = new UtopiaScriptFunction(stmt);
        environment.define(stmt.name.lexeme, function);
        return null;
    }

    // Interprets a return statement
    // default return value is a null
    @Override
    public Void visitReturnStmt(Stmt.Return stmt) {
        Object value = null;
        if (stmt.value != null) {
            value = evaluate(stmt.value);
        }

        throw new Return(value);
    }
}
