package com.meta.plugin;

import java.math.BigDecimal;
import java.math.MathContext;

/**
 * Small expression evaluator supporting + - * / % parentheses, unary minus,
 * powers (^), pi (\u03c0) and square root (\u221a).
 * Throws IllegalArgumentException on incomplete / malformed input so the UI can
 * show a live preview only when the expression is actually valid.
 */
public final class CalcEngine {

    private CalcEngine() {
    }

    public static double eval(String s) {
        if (s == null) {
            throw new IllegalArgumentException("null");
        }
        Parser p = new Parser(s);
        double v = p.parseExpression();
        p.skipSpaces();
        if (p.pos < p.src.length()) {
            throw new IllegalArgumentException("unexpected '" + p.src.charAt(p.pos) + "'");
        }
        if (Double.isNaN(v) || Double.isInfinite(v)) {
            throw new IllegalArgumentException("not finite");
        }
        return v;
    }

    /** Tries to evaluate, returning null instead of throwing. */
    public static Double tryEval(String s) {
        try {
            return Double.valueOf(eval(s));
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** Lenient variant used for the live preview: dangling operators are ignored. */
    public static Double tryEvalLenient(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        while (t.length() > 0) {
            char c = t.charAt(t.length() - 1);
            if (c == '+' || c == '-' || c == '*' || c == '/' || c == '^') {
                t = t.substring(0, t.length() - 1);
            } else {
                break;
            }
        }
        if (t.length() == 0) {
            return null;
        }
        return tryEval(t);
    }

    /** Human friendly number formatting (max 12 significant digits). */
    public static String format(double v) {
        if (Double.isNaN(v) || Double.isInfinite(v)) {
            return "错误";
        }
        if (v == 0d) {
            return "0";
        }
        BigDecimal bd = new BigDecimal(v, new MathContext(12)).stripTrailingZeros();
        String plain = bd.toPlainString();
        if (plain.length() > 16) {
            BigDecimal sci = new BigDecimal(v, new MathContext(6)).stripTrailingZeros();
            return sci.toString();
        }
        return plain;
    }

    private static class Parser {
        final String src;
        int pos;

        Parser(String src) {
            this.src = src;
            this.pos = 0;
        }

        void skipSpaces() {
            while (pos < src.length() && Character.isWhitespace(src.charAt(pos))) {
                pos++;
            }
        }

        char peek() {
            skipSpaces();
            return pos < src.length() ? src.charAt(pos) : '\0';
        }

        boolean eat(char c) {
            if (peek() == c) {
                pos++;
                return true;
            }
            return false;
        }

        double parseExpression() {
            double v = parseTerm();
            while (true) {
                char c = peek();
                if (c == '+') {
                    pos++;
                    v = v + parseTerm();
                } else if (c == '-' || c == '\u2212') {
                    pos++;
                    v = v - parseTerm();
                } else {
                    return v;
                }
            }
        }

        double parseTerm() {
            double v = parseUnary();
            while (true) {
                char c = peek();
                if (c == '*') {
                    pos++;
                    v = v * parseUnary();
                } else if (c == '/') {
                    pos++;
                    double d = parseUnary();
                    if (d == 0d) {
                        throw new IllegalArgumentException("division by zero");
                    }
                    v = v / d;
                } else {
                    return v;
                }
            }
        }

        double parseUnary() {
            char c = peek();
            if (c == '-') {
                pos++;
                return -parseUnary();
            }
            if (c == '+') {
                pos++;
                return parseUnary();
            }
            if (c == '\u221a') {
                pos++;
                double inner = parsePower();
                if (inner < 0) {
                    throw new IllegalArgumentException("sqrt of negative");
                }
                return Math.sqrt(inner);
            }
            return parsePower();
        }

        double parsePower() {
            double base = parsePostfix();
            if (peek() == '^') {
                pos++;
                double exp = parseUnary();
                return Math.pow(base, exp);
            }
            return base;
        }

        double parsePostfix() {
            double v = parsePrimary();
            while (peek() == '%') {
                pos++;
                v = v / 100d;
            }
            return v;
        }

        double parsePrimary() {
            char c = peek();
            if (c == '(') {
                pos++;
                double v = parseExpression();
                if (!eat(')')) {
                    throw new IllegalArgumentException("missing ')'");
                }
                return v;
            }
            if (c == '\u03c0') {
                pos++;
                return Math.PI;
            }
            int start = pos;
            boolean dot = false;
            while (pos < src.length()) {
                char ch = src.charAt(pos);
                if (ch >= '0' && ch <= '9') {
                    pos++;
                } else if (ch == '.' && !dot) {
                    dot = true;
                    pos++;
                } else {
                    break;
                }
            }
            if (start == pos) {
                throw new IllegalArgumentException("number expected at " + pos);
            }
            String num = src.substring(start, pos);
            try {
                return Double.parseDouble(num);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("bad number " + num);
            }
        }
    }
}
