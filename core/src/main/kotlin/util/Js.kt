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

    data class PropertyAccess(
        val target: Expression,
        val name: String,
    ) : Expression

    data class New(
        val callee: Expression,
        val arguments: List<Expression>,
    ) : Expression

    data class Call(
        val callee: Expression,
        val arguments: List<Expression>,
    ) : Expression

    class Unary(
        val kind: Kind,
        val expression: Expression,
    ) : Expression {
        enum class Kind {
            DELETE,
            VOID,
            TYPEOF,
            PLUS,
            MINUS,
            BITWISE_NOT,
            LOGICAL_NOT,
            AWAIT,
            YIELD,
            YIELD_GENERATOR,
        }
    }

    data class Binary(
        val kind: Kind,
        val left: Expression,
        val right: Expression,
    ) : Expression {
        enum class Kind {
            EXPONENTIATION,
            MULTIPLICATION,
            DIVISION,
            REMAINDER,
            ADDITION,
            SUBTRACTION,
            LEFT_SHIFT,
            SIGNED_RIGHT_SHIFT,
            UNSIGNED_RIGHT_SHIFT,
            LESS_THAN,
            GREATER_THAN,
            LESS_THAN_OR_EQUAL,
            GREATER_THAN_OR_EQUAL,
            INSTANCEOF,
            IN,
            LOOSELY_EQUAL,
            LOOSELY_NOT_EQUAL,
            STRICTLY_EQUAL,
            STRICTLY_NOT_EQUAL,
            BITWISE_AND,
            BITWISE_XOR,
            BITWISE_OR,
            LOGICAL_AND,
            LOGICAL_OR,
            COALESCE,
            ASSIGNMENT,
            MULTIPLICATION_ASSIGNMENT,
            DIVISION_ASSIGNMENT,
            REMAINDER_ASSIGNMENT,
            ADDITION_ASSIGNMENT,
            SUBTRACTION_ASSIGNMENT,
            LEFT_SHIFT_ASSIGNMENT,
            SIGNED_RIGHT_SHIFT_ASSIGNMENT,
            UNSIGNED_RIGHT_SHIFT_ASSIGNMENT,
            BITWISE_AND_ASSIGNMENT,
            BITWISE_XOR_ASSIGNMENT,
            BITWISE_OR_ASSIGNMENT,
            EXPONENTIATION_ASSIGNMENT,
            LOGICAL_AND_ASSIGNMENT,
            LOGICAL_OR_ASSIGNMENT,
            COALESCE_ASSIGNMENT,
            COMMA,
        }
    }

    data class Conditional(
        val condition: Expression,
        val thenExpression: Expression,
        val elseExpression: Expression,
    ) : Expression

    data class ArrowFunction(
        val parameters: List<Binding>,
        val body: Body,
    ) : Expression {
        sealed interface Body {
            data class Expression(val expression: koto.core.util.Expression) : Body
            data class Function(val statements: List<Statement>) : Body
        }
    }
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
