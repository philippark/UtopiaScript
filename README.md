# UtopiaScript

<p align="center">
    <img src="logo.png" width="200">
</p>

## About 

UtopiaScript is an object oriented imperative scripting language with a syntax following Esperanto. 

## Purpose
The current landscape of programming languages heavily relies on English-based keywords 
and syntax. This presents a significant hurdle for non-native English speakers, who must master 
not only the logical constructs of a language but also its associated English vocabulary. 

UtopiaScript aims to be an international auxiliary programming language designed to overcome 
these linguistic and cultural barriers in the programming world. Its core mission is to establish a 
universal standard for collaboration and code sharing.

## Examples

```
// Implements fibonacci sequence

funkcio fib(n) {
    se (n <= 1) revenigi n;
    revenigi fib(n - 2) + fib(n - 1);
}

por (var i = 0; i < 5; i = i + 1) {
    presi fib(i);
}
```