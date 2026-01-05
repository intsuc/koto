package koto.core.util

private data class Script(
    val statements: List<Statement>,
)

private sealed interface Statement {
    data class Block(
        val statements: List<Statement>,
    ) : Statement

    data class Expression(
        val expression: koto.core.util.Expression,
    ) : Statement

    data class If(
        val condition: koto.core.util.Expression,
        val thenStatement: Statement,
        val elseStatement: Statement,
    ) : Statement

    data class DoWhile(
        val statement: Statement,
        val condition: koto.core.util.Expression,
    ) : Statement

    data class While(
        val condition: koto.core.util.Expression,
        val statement: Statement,
    ) : Statement

    // TODO: for

    data object Continue : Statement

    data object Break : Statement

    data class Return(
        val expression: koto.core.util.Expression?,
    ) : Statement

    data class Throw(
        val expression: koto.core.util.Expression,
    ) : Statement

    data class Try(
        val statements: List<Statement>,
        val catch: Pair<Binding?, List<Statement>>?,
        val finally: List<Statement>?,
    )

    data class Function(
        val kind: Kind,
        val identifier: String,
        val parameters: List<Binding>,
        val body: List<Statement>,
    ) {
        enum class Kind {
            FUNCTION,
            GENERATOR,
            ASYNC,
            ASYNC_GENERATOR,
        }
    }

    data class Lexical(
        val kind: Kind,
        val bindings: List<Binding>,
    ) {
        enum class Kind {
            LET,
            CONST,
        }
    }
}

private sealed interface Expression {
    data object This : Expression

    data class Identifier(
        val identifier: String,
    ) : Expression

    data object NullLiteral : Expression

    data class BooleanLiteral(
        val value: kotlin.Boolean,
    ) : Expression

    data class DecimalLiteral(
        val value: Double,
    ) : Expression

    data class BigIntegerLiteral(
        val value: java.math.BigInteger,
    ) : Expression

    data class StringLiteral(
        val value: String,
    ) : Expression

    data class Spread(
        val expression: Expression,
    ) : Expression

    data class ArrayLiteral(
        val expressions: List<Expression>,
    ) : Expression

    data class ObjectLiteral(
        val expressions: Map<String, Expression>,
    ) : Expression

    data class Function(
        val kind: Kind,
        val identifier: String,
        val parameters: List<Binding>,
        val body: List<Statement>,
    ) : Expression {
        enum class Kind {
            FUNCTION,
            GENERATOR,
            ASYNC,
            ASYNC_GENERATOR,
        }
    }

    data class RegularExpressionLiteral(
        val value: kotlin.String,
    ) : Expression

    data class TemplateLiteral(
        val spans: List<Span>,
    ) : Expression {
        sealed interface Span {
            data class NoSubstitution(val value: String) : Span
            data class Substitution(val expression: Expression) : Span
        }
    }

    // TODO
}

private data class Binding(
    val pattern: Pattern,
    val initializer: Expression?,
)

private sealed interface Pattern {
    data class Identifier(
        val identifier: String,
    ) : Pattern

    data class Object(
        val bindings: Map<String, Binding>,
    ) : Pattern

    data class Array(
        val bindings: List<Binding>,
    ) : Pattern
}
