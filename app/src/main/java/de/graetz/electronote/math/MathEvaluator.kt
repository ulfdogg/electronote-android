package de.graetz.electronote.math

import kotlin.math.E
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

class MathExpressionException(message: String) : Exception(message)

/**
 * Small recursive-descent evaluator for the calculator and function plotter. iOS solves
 * the same problem by handing the expression to JavaScriptCore; Android has no equivalent
 * bundled JS engine, so this hand-rolls just the arithmetic subset actually needed:
 * + - * / ^, parentheses, sin/cos/tan/sqrt/log/ln/exp/abs, pi/e, and the plotter's `x`.
 */
object MathEvaluator {
    fun evaluate(expression: String, x: Double? = null): Double {
        val tokens = tokenize(expression)
        val parser = Parser(tokens, x)
        val result = parser.parseExpression()
        if (!parser.isAtEnd()) throw MathExpressionException("Unerwartetes Zeichen am Ende")
        return result
    }

    private sealed class Token {
        data class Num(val value: Double) : Token()
        data class Ident(val name: String) : Token()
        data class Op(val symbol: Char) : Token()
        object LParen : Token()
        object RParen : Token()
    }

    private fun tokenize(input: String): List<Token> {
        val tokens = mutableListOf<Token>()
        var i = 0
        while (i < input.length) {
            val c = input[i]
            when {
                c.isWhitespace() -> i++
                c.isDigit() || c == '.' -> {
                    val start = i
                    while (i < input.length && (input[i].isDigit() || input[i] == '.')) i++
                    tokens.add(Token.Num(input.substring(start, i).toDouble()))
                }
                c == 'π' -> { tokens.add(Token.Ident("pi")); i++ }
                c.isLetter() -> {
                    val start = i
                    while (i < input.length && input[i].isLetterOrDigit()) i++
                    tokens.add(Token.Ident(input.substring(start, i)))
                }
                c == '(' -> { tokens.add(Token.LParen); i++ }
                c == ')' -> { tokens.add(Token.RParen); i++ }
                c in "+-*/^" -> { tokens.add(Token.Op(c)); i++ }
                else -> throw MathExpressionException("Unbekanntes Zeichen: $c")
            }
        }
        return tokens
    }

    private class Parser(val tokens: List<Token>, val x: Double?) {
        var pos = 0
        fun isAtEnd() = pos >= tokens.size
        fun peek(): Token? = tokens.getOrNull(pos)

        fun parseExpression(): Double = parseAddSub()

        private fun parseAddSub(): Double {
            var left = parseMulDiv()
            while (true) {
                val op = peek() as? Token.Op
                if (op != null && (op.symbol == '+' || op.symbol == '-')) {
                    pos++
                    val right = parseMulDiv()
                    left = if (op.symbol == '+') left + right else left - right
                } else break
            }
            return left
        }

        private fun parseMulDiv(): Double {
            var left = parseUnary()
            while (true) {
                val op = peek() as? Token.Op
                if (op != null && (op.symbol == '*' || op.symbol == '/')) {
                    pos++
                    val right = parseUnary()
                    left = if (op.symbol == '*') left * right else left / right
                } else break
            }
            return left
        }

        private fun parseUnary(): Double {
            val op = peek() as? Token.Op
            if (op != null && op.symbol == '-') { pos++; return -parseUnary() }
            if (op != null && op.symbol == '+') { pos++; return parseUnary() }
            return parsePower()
        }

        private fun parsePower(): Double {
            val base = parsePrimary()
            val op = peek() as? Token.Op
            if (op != null && op.symbol == '^') {
                pos++
                return base.pow(parseUnary())
            }
            return base
        }

        private fun parsePrimary(): Double {
            val token = peek() ?: throw MathExpressionException("Unerwartetes Ende des Ausdrucks")
            return when (token) {
                is Token.Num -> { pos++; token.value }
                is Token.LParen -> {
                    pos++
                    val value = parseExpression()
                    expectRParen()
                    value
                }
                is Token.Ident -> { pos++; parseIdentifier(token.name) }
                else -> throw MathExpressionException("Unerwartetes Zeichen")
            }
        }

        private fun expectRParen() {
            if (peek() != Token.RParen) throw MathExpressionException("Erwartet: )")
            pos++
        }

        private fun parseIdentifier(name: String): Double {
            val lower = name.lowercase()
            if (peek() == Token.LParen) {
                pos++
                val arg = parseExpression()
                expectRParen()
                return when (lower) {
                    "sin" -> sin(arg)
                    "cos" -> cos(arg)
                    "tan" -> tan(arg)
                    "sqrt" -> sqrt(arg)
                    "abs" -> abs(arg)
                    "log" -> log10(arg)
                    "ln" -> ln(arg)
                    "exp" -> exp(arg)
                    else -> throw MathExpressionException("Unbekannte Funktion: $name")
                }
            }
            return when (lower) {
                "pi" -> PI
                "e" -> E
                "x" -> x ?: throw MathExpressionException("x ist hier nicht definiert")
                else -> throw MathExpressionException("Unbekannter Bezeichner: $name")
            }
        }
    }
}
