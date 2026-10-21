package com.crmops.channel.service;

import org.mvel2.MVEL;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Evaluates the custom scoring formulas sales operations keep for lead and
 * account scorecards. The formula text is supplied by the operator and the
 * current row values are passed in as named variables.
 */
@Service
public class ScoringFormulaService {

    public Object evaluate(String formula, Map<String, Object> variables) {
        return MVEL.executeExpression(MVEL.compileExpression(formula), variables);
    }
}
