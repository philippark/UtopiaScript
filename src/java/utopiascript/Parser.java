package utopiascript;

import static utopiascript.TokenType.*;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;

public class Parser {
    private static class ParseError extends RuntimeException {}
    private final List<Token> tokens;
    private int current = 0;

    Parser(List<Token> tokens){
        this.tokens = tokens;
    }

    List<Stmt> parse() {
        List<Stmt> statements = new ArrayList<>();
        while (!isAtEnd()){
            statements.add(declaration());
        }

        return statements;
    }

    private Expr expression(){
        return assignment();
    }

    private Expr assignment() {
        Expr expr = or();

        if (match(EQUAL)) {
            Token equals = previous();
            Expr value = assignment();

            if (expr instanceof Expr.Variable) {
                Token name = ((Expr.Variable)expr).name;
                return new Expr.Assign(name, value);
            }

            error(equals, "Invalid assignment target.");
        }

        return expr;
    }

    // Parses a logical or
    private Expr or() {
        Expr expr = and();

        while (match(AU)) {
            Token operator = previous();
            Expr right = and();
            expr = new Expr.Logical(expr, operator, right);
        }

        return expr;
    }

    // Parses a logical and
    private Expr and() {
        Expr expr = equality();

        while (match(KAJ)) {
            Token operator = previous();
            Expr right = equality();
            expr = new Expr.Logical(expr, operator, right);
        }

        return expr;
    }

    private Stmt declaration() {
        try {
            if (match(VAR)) return varDeclaration();
            if (match(FUNKCIO)) return function("function");
            return statement();
        } catch (ParseError error) {
            synchronize();
            return null;
        }
    }

    private Stmt statement() {
        if (match(PRESI)) return printStatement();
        if (match(LEFT_BRACE)) return new Stmt.Block(block());
        if (match(SE)) return ifStatement();
        if (match(DUM)) return whileStatement();
        if (match(POR)) return forStatement();
        if (match(REVENIGI)) return returnStatement();

        return expressionStatement();
    }
    
    // Parses a print statement
    private Stmt printStatement() {
        Expr value = expression();
        consume(SEMICOLON, "Expect ; after value.");
        return new Stmt.Print(value);
    }

    private List<Stmt> block() {
        List<Stmt> statements = new ArrayList<Stmt>();

        while (!check(RIGHT_BRACE) && !isAtEnd()) {
            statements.add(declaration());
        }

        consume(RIGHT_BRACE, "Expect '}' after block.");

        return statements;
    }

    // Parses a if statement
    // else is optional
    private Stmt ifStatement() {
        consume(LEFT_PAREN, "Expect '(' after 'if'");
        Expr condition = expression();
        consume(RIGHT_PAREN, "Expect ')' after if condition");

        Stmt thenBranch = statement();
        Stmt elseBranch = null;

        if (match(ALIE)) {
            elseBranch = statement();
        }

        return new Stmt.If(condition, thenBranch, elseBranch);
    }
    
    // Parses a while statement
    private Stmt whileStatement() {
        consume(LEFT_PAREN, "Expect an opening '(' after while");
        Expr condition = expression();
        consume(RIGHT_PAREN, "Expect a closing ')' after condition");

        Stmt body = statement();

        return new Stmt.While(condition, body);
    }

    // Parses a for statement
    // for (initializer; condition; increment). 
    // The initializer, condition, and increment are optional.
    // The for loop is syntatic sugar that is desugarized to rely on the while loop.
    private Stmt forStatement() {
        consume(LEFT_PAREN, "Expect '(' after 'for'.");

        // check for initializer
        Stmt initializer;
        if (match(SEMICOLON)) {
            initializer = null;
        } else if (match(VAR)) {
            initializer = varDeclaration();
        } else {
            initializer = expressionStatement();
        }

        // check for condition
        Expr condition = null;
        if (!check(SEMICOLON)) {
            condition = expression();
        }
        consume(SEMICOLON, "Expect ';' after loop condition");

        // check for increment
        Expr increment = null;
        if (!check(RIGHT_PAREN)) {
            increment = expression();
        }
        consume(RIGHT_PAREN, "Expect ')' after for clauses.");

        // grab the body
        Stmt body = statement();

        if (increment != null) {
            body = new Stmt.Block(
                Arrays.asList(
                    body,
                    new Stmt.Expression(increment)
                )
            );
        }

        if (condition == null) condition = new Expr.Literal(true);
        body = new Stmt.While(condition, body);

        if (initializer != null) {
            body = new Stmt.Block(Arrays.asList(initializer, body));
        }

        return body;
    }

    // Parses a variable declaration
    private Stmt varDeclaration() {
        Token name = consume(IDENTIFIER, "Expect variable name");

        Expr initializer = null;
        if (match(EQUAL)) {
            initializer = expression();
        }

        consume(SEMICOLON, "Expect ; after variable declaration");

        return new Stmt.Var(name, initializer);
    }
    
    private Stmt expressionStatement() {
        Expr expr = expression();
        consume(SEMICOLON, "Expect ; after expression.");
        return new Stmt.Expression(expr);
    }

    // Parses a function declaration
    // Function declaration takes the form of: Name(parameters). parameters are optional.
    private Stmt.Function function(String kind) {
        // check for name
        Token name = consume(IDENTIFIER, "Expect " + kind + " name.");
        consume(LEFT_PAREN, "Expect '(' after " + kind + " name.");

        // check for parameters
        List<Token> parameters = new ArrayList<>();
        if (!check(RIGHT_PAREN)) {
            do {
                if (parameters.size() >= 255) {
                    error(peek(), "Can't have more than 255 parameters");
                }
                parameters.add(consume(IDENTIFIER, "Expect parameter name"));
            } while (match(COMMA));
        }
        consume(RIGHT_PAREN, "Expect ')' after parameters");

        consume(LEFT_BRACE, "Expect '{' before " + kind + " body");
        
        // grab the body
        List<Stmt> body = block();

        return new Stmt.Function(name, parameters, body);
    }

    // Parses a return statement
    // value is optional, default is null
    private Stmt returnStatement() {
        Token keyword = previous();

        Expr value = null;
        if (!check(SEMICOLON)) {
            value = expression();
        }

        consume(SEMICOLON, "Expect a ';' after return statement");

        return new Stmt.Return(keyword, value);
    }

    // Parses an equality comparison
    private Expr equality() {
        Expr expr = comparison();
        while (match(BANG_EQUAL, EQUAL_EQUAL)){
            Token operator = previous();
            Expr right = comparison();
            expr = new Expr.Binary(expr, operator, right);
        }

        return expr;
    }
    
    // Parses a comparison
    private Expr comparison() {
        Expr expr = term();

        while (match(GREATER, GREATER_EQUAL, LESS, LESS_EQUAL)){
            Token operator = previous();
            Expr right = term();
            expr = new Expr.Binary(expr, operator, right);
        }

        return expr;
    }
   
    // Parses a term
    // A term is an expression that can be evaluated to a value
    private Expr term() {
        Expr expr = factor();

        while (match(MINUS, PLUS)){
            Token operator = previous();
            Expr right = factor();
            expr = new Expr.Binary(expr, operator, right);
        }

        return expr;
    }

    // Parses a factor
    // A factor is a binary operator for multiplication and division
    private Expr factor() {
        Expr expr = unary();

        while (match(SLASH, STAR)){
            Token operator = previous();
            Expr right = unary();
            expr = new Expr.Binary(expr, operator, right);
        }

        return expr;
    }

    // Parses a unary
    // A unary is of the form !x or -x
    private Expr unary() {
        if (match(BANG, MINUS)) {
            Token operator = previous();
            Expr right = unary();
            return new Expr.Unary(operator, right);
        }

        return call();
    }

    // Parses a call expression using the previously parsed expression as the callee
    private Expr finishCall(Expr callee) {
        List<Expr> arguments = new ArrayList<>();

        // gather arguments if any
        if (!check(RIGHT_PAREN)){
            do {
                if (arguments.size() >= 255) {
                    error(peek(), "Can't have more than 255 arguments.");
                }
                arguments.add(expression());
            } while (match(COMMA));
        }

        Token paren = consume(RIGHT_PAREN, "Expect ')' after arguments.");

        return new Expr.Call(callee, paren, arguments);
    }

    // Parses a function call
    private Expr call() {
        Expr expr = primary();

        while (true) {
            if (match(LEFT_PAREN)) {
                expr = finishCall(expr);
            } else {
                break;
            }
        }

        return expr;
    }

    // Parses primaries
    private Expr primary() {
        if (match(MALVERA)) return new Expr.Literal(false);
        if (match(VERA)) return new Expr.Literal(true);
        if (match(NENIO)) return new Expr.Literal(null);

        if (match(NUMBER, STRING)) {
            return new Expr.Literal(previous().literal);
        }

        if (match(IDENTIFIER)) {
            return new Expr.Variable(previous());
        }

        if (match(LEFT_PAREN)) {
            Expr expr = expression();
            consume(RIGHT_PAREN, "Expect ')' after expression.");
            return new Expr.Grouping(expr);
        }

        throw error(peek(), "Expect expression.");
    }

    // consumes a token
    private Token consume(TokenType type, String message) {
        if (check(type)) return advance();

        throw error(peek(), message);
    }

    // handles parse errors
    private ParseError error(Token token, String message) {
        UtopiaScript.error(token, message);
        return new ParseError();
    }

    // Synchronizes token stream by discarding tokens until it reaches a point that can start a rule
    // Used when there is a parsing error
    private void synchronize() {
        advance();

        while (!isAtEnd()){
            if (previous().type == SEMICOLON){
                return;
            }

            switch (peek().type){
                case KLASO:
                case FUNKCIO:
                case VAR:
                case POR:
                case SE:
                case DUM:
                case PRESI:
                case REVENIGI:
                    return;
                default:
                    break;
            }

            advance();
        }
    }

    // Matches for a token
    private boolean match(TokenType... types){
        for (TokenType type : types){
            if (check(type)){
                advance();
                return true;
            }
        }

        return false;
    }

    // Checks for a token type
    private boolean check(TokenType type){
        if (isAtEnd()) return false;
        return peek().type == type;
    }

    // Advances the token stream
    private Token advance(){
        if (!isAtEnd()) current++;
        return previous();
    }

    private boolean isAtEnd(){
        return peek().type == EOF;
    }

    private Token peek(){
        return tokens.get(current);
    }

    private Token previous(){
        return tokens.get(current-1);
    }
}
