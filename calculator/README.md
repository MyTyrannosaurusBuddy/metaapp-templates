# Calculator

A full calculator app: expression editing with parentheses, percent,
power and sign operators, plus a tap-to-reuse history panel.

Layout:
- `DynamicPlugin.java` - PluginContract entry point
- `CalcEngine.java` - expression tokenizer / parser / evaluator
- `CalculatorView.java` - the UI (display, keypad, history panel)

Reference points: multi-file project structure, expression parser,
history persistence pattern.
