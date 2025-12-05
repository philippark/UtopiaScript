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

var prev2 = 0;
var prev1 = 1;
var val = 0;

por (var i = 2; i < 10; i = i + 1){
    val = prev2 + prev1;
    presi("fibo: " + val);
    
    prev2 = prev1;
    prev1 = val;
}
```