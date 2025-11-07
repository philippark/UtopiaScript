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

    /**
     * @brief Generates parse tree for logical or
     * @return The resulting expression object of the logical or
     */
    private Expr or() {
        Expr expr = and();

        while (match(AU)) {
            Token operator = previous();
            Expr right = and();
            expr = new Expr.Logical(expr, operator, right);
        }

        return expr;
    }

    /**
     * @brief Generates parse tree for logical and
     * @return The resulting expression object of the logical and
     */
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

        return expressionStatement();
    }

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
    
    /**
     * @brief Generates the parse tree for the while statement
     * @return A Stmt object for the while statement
     */
    private Stmt whileStatement() {
        consume(LEFT_PAREN, "Expect an opening '(' after while");
        Expr condition = expression();
        consume(RIGHT_PAREN, "Expect a closing ')' after condition");

        Stmt body = statement();

        return new Stmt.While(condition, body);
    }

    /**
     * @brief Parses the for statement, which is composed of: 
     * for (initializer; condition; increment). 
     * The initializer, condition, and increment are optional.
     * The for loop is syntatic sugar that is desugarized to rely on the while loop.
     * @return A parse tree for the for statement
     */
    private Stmt forStatement() {
        consume(LEFT_PAREN, "Expect '(' after 'for'.");

        Stmt initializer;
        if (match(SEMICOLON)) {
            initializer = null;
        } else if (match(VAR)) {
            initializer = varDeclaration();
        } else {
            initializer = expressionStatement();
        }

        Expr condition = null;
        if (!check(SEMICOLON)) {
            condition = expression();
        }
        consume(SEMICOLON, "Expect ';' after loop condition");

        Expr increment = null;
        if (!check(RIGHT_PAREN)) {
            increment = expression();
        }
        consume(RIGHT_PAREN, "Expect ')' after for clauses.");

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

    private Expr equality() {
        Expr expr = comparison();
        while (match(BANG_EQUAL, EQUAL_EQUAL)){
            Token operator = previous();
            Expr right = comparison();
            expr = new Expr.Binary(expr, operator, right);
        }

        return expr;
    }
    
    private Expr comparison() {
        Expr expr = term();

        while (match(GREATER, GREATER_EQUAL, LESS, LESS_EQUAL)){
            Token operator = previous();
            Expr right = term();
            expr = new Expr.Binary(expr, operator, right);
        }

        return expr;
    }

    private Expr term() {
        Expr expr = factor();

        while (match(MINUS, PLUS)){
            Token operator = previous();
            Expr right = factor();
            expr = new Expr.Binary(expr, operator, right);
        }

        return expr;
    }

    private Expr factor() {
        Expr expr = unary();

        while (match(SLASH, STAR)){
            Token operator = previous();
            Expr right = unary();
            expr = new Expr.Binary(expr, operator, right);
        }

        return expr;
    }

    private Expr unary() {
        if (match(BANG, MINUS)) {
            Token operator = previous();
            Expr right = unary();
            return new Expr.Unary(operator, right);
        }

        return call();
    }

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

    private Token consume(TokenType type, String message) {
        if (check(type)) return advance();

        throw error(peek(), message);
    }

    private ParseError error(Token token, String message) {
        UtopiaScript.error(token, message);
        return new ParseError();
    }

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
            }

            advance();
        }
    }

    private boolean match(TokenType... types){
        for (TokenType type : types){
            if (check(type)){
                advance();
                return true;
            }
        }

        return false;
    }

    private boolean check(TokenType type){
        if (isAtEnd()) return false;
        return peek().type == type;
    }

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
