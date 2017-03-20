package org.eclipse.smarthome.automation.module.core.handler;

public enum CompareOperators {
    EQUALS("="),
    LT("<"),
    GT(">"),
    NOTEQUAL("!="),
    LT_EQ("<="),
    GT_EQ(">="),
    BETWEEN("IN");

    final String expression;

    CompareOperators(String expression) {
        this.expression = expression;
    }

}
