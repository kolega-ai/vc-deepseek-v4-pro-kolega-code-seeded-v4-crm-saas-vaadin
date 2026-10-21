package com.crmops.channel.service;

import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Evaluates the small expressions sales operations configure for computed
 * report columns and health-score previews. Variables are bound into the
 * evaluation context and referenced as #name in the expression.
 */
@Service
public class RuleExpressionService {

    private final ExpressionParser parser = new SpelExpressionParser();

    public Object evaluate(String expression, Map<String, Object> variables) {
        StandardEvaluationContext context = new StandardEvaluationContext();
        if (variables != null) {
            variables.forEach(context::setVariable);
        }
        return parser.parseExpression(expression).getValue(context);
    }
}
