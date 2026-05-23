package com.zametki.pro;

import com.zametki.pro.utils.ThemeHelper;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.HorizontalScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.zametki.pro.utils.DisguiseManager;
import com.zametki.pro.utils.PrefsManager;

import java.math.BigDecimal;
import java.util.Locale;

public class CalculatorActivity extends AppCompatActivity {

    private TextView tvMemory;
    private TextView tvHistory;
    private TextView tvExpr;
    private TextView tvResult;
    private HorizontalScrollView scrollExpr;
    private HorizontalScrollView scrollResult;
    private final StringBuilder expr = new StringBuilder();
    private PrefsManager prefs;
    private double memoryValue = 0d;
    private boolean hasMemory = false;

    @Override
    protected void onCreate(Bundle b) {
        ThemeHelper.applyThemeFromContext(this);
        super.onCreate(b);
        setContentView(R.layout.activity_calculator);

        prefs = new PrefsManager(this);
        DisguiseManager.apply(this, prefs.isDisguiseModeEnabled());
        if (!prefs.isDisguiseModeEnabled()) {
            openMain(false);
            return;
        }

        tvMemory = findViewById(R.id.tv_memory);
        tvHistory = findViewById(R.id.tv_history);
        tvExpr = findViewById(R.id.tv_expr);
        tvResult = findViewById(R.id.tv_result);
        scrollExpr = findViewById(R.id.scroll_expr);
        scrollResult = findViewById(R.id.scroll_result);

        bindMainButtons();
        bindScientificButtons();
        bindMemoryButtons();

        findViewById(R.id.btn_recip).setOnClickListener(v -> applyReciprocal());
        findViewById(R.id.btn_ac).setOnClickListener(v -> clearAll());
        findViewById(R.id.btn_del).setOnClickListener(v -> deleteOne());
        findViewById(R.id.btn_eq).setOnClickListener(v -> onEquals());

        updateMemoryLabel();
        clearAll();
    }

    private void bindMainButtons() {
        int[] ids = {
                R.id.btn_0, R.id.btn_1, R.id.btn_2, R.id.btn_3, R.id.btn_4,
                R.id.btn_5, R.id.btn_6, R.id.btn_7, R.id.btn_8, R.id.btn_9,
                R.id.btn_dot, R.id.btn_plus, R.id.btn_minus, R.id.btn_mul, R.id.btn_div,
                R.id.btn_percent, R.id.btn_sign, R.id.btn_lparen, R.id.btn_rparen,
                R.id.btn_square, R.id.btn_sqrt, R.id.btn_const_pi, R.id.btn_const_e,
                R.id.btn_pow, R.id.btn_fact
        };
        for (int id : ids) {
            findViewById(id).setOnClickListener(v -> append(((TextView) v).getText().toString()));
        }
    }

    private void bindScientificButtons() {
        findViewById(R.id.btn_fn_sin).setOnClickListener(v -> appendFunction("sin"));
        findViewById(R.id.btn_fn_cos).setOnClickListener(v -> appendFunction("cos"));
        findViewById(R.id.btn_fn_tan).setOnClickListener(v -> appendFunction("tan"));
        findViewById(R.id.btn_fn_ln).setOnClickListener(v -> appendFunction("ln"));
        findViewById(R.id.btn_fn_log).setOnClickListener(v -> appendFunction("log"));
    }

    private void bindMemoryButtons() {
        findViewById(R.id.btn_mem_clear).setOnClickListener(v -> {
            hasMemory = false;
            memoryValue = 0d;
            updateMemoryLabel();
            Toast.makeText(this, getString(R.string.calculator_memory_cleared), Toast.LENGTH_SHORT).show();
        });
        findViewById(R.id.btn_mem_recall).setOnClickListener(v -> recallMemory());
        findViewById(R.id.btn_mem_add).setOnClickListener(v -> applyMemoryOperation(true));
        findViewById(R.id.btn_mem_sub).setOnClickListener(v -> applyMemoryOperation(false));
    }

    private void append(String s) {
        if (TextUtils.isEmpty(s)) return;
        if ("±".equals(s)) {
            toggleSign();
            return;
        }
        if ("%".equals(s)) {
            appendPercent();
            return;
        }
        if (".".equals(s)) {
            appendDot();
            return;
        }
        if ("(".equals(s)) {
            appendLeftParen();
            return;
        }
        if (")".equals(s)) {
            appendRightParen();
            return;
        }
        if ("x²".equals(s)) {
            appendSquare();
            return;
        }
        if ("√".equals(s)) {
            appendSqrt();
            return;
        }
        if ("π".equals(s)) {
            appendConstant("π");
            return;
        }
        if ("e".equals(s)) {
            appendConstant("e");
            return;
        }
        if ("xʸ".equals(s)) {
            appendPower();
            return;
        }
        if ("n!".equals(s)) {
            appendFactorial();
            return;
        }

        if ("×".equals(s)) s = "*";
        else if ("−".equals(s)) s = "-";
        else if ("÷".equals(s)) s = "/";

        if (s.length() == 1 && isOp(s.charAt(0))) {
            appendOperator(s.charAt(0));
            return;
        }

        expr.append(s);
        updateDisplayedState();
    }

    private void appendFunction(String name) {
        if (TextUtils.isEmpty(name)) return;
        if (expr.length() == 0) {
            expr.append(name).append('(');
            updateDisplayedState();
            return;
        }
        char last = expr.charAt(expr.length() - 1);
        if (isValueEnd(last)) expr.append('*');
        expr.append(name).append('(');
        updateDisplayedState();
    }

    private void appendLeftParen() {
        if (expr.length() == 0) {
            expr.append('(');
            updateDisplayedState();
            return;
        }
        char last = expr.charAt(expr.length() - 1);
        if (isValueEnd(last)) expr.append("*(");
        else expr.append('(');
        updateDisplayedState();
    }

    private void appendRightParen() {
        if (expr.length() == 0 || countOpenParens() <= 0) return;
        char last = expr.charAt(expr.length() - 1);
        if (isValueEnd(last)) {
            expr.append(')');
            updateDisplayedState();
        }
    }

    private void appendSqrt() {
        if (expr.length() == 0) {
            expr.append('√');
            updateDisplayedState();
            return;
        }
        char last = expr.charAt(expr.length() - 1);
        if (isValueEnd(last)) expr.append("*√");
        else expr.append('√');
        updateDisplayedState();
    }

    private void appendSquare() {
        if (expr.length() == 0) return;
        char last = expr.charAt(expr.length() - 1);
        if (isValueEnd(last)) {
            expr.append('²');
            updateDisplayedState();
        }
    }

    private void appendPower() {
        if (expr.length() == 0) return;
        char last = expr.charAt(expr.length() - 1);
        if (isValueEnd(last)) {
            expr.append('^');
            updateDisplayedState();
        }
    }

    private void appendFactorial() {
        if (expr.length() == 0) return;
        char last = expr.charAt(expr.length() - 1);
        if (isValueEnd(last)) {
            expr.append('!');
            updateDisplayedState();
        }
    }

    private void appendConstant(String token) {
        if (TextUtils.isEmpty(token)) return;
        if (expr.length() == 0) {
            expr.append(token);
            updateDisplayedState();
            return;
        }
        char last = expr.charAt(expr.length() - 1);
        if (isValueEnd(last)) expr.append('*');
        expr.append(token);
        updateDisplayedState();
    }

    private void applyReciprocal() {
        String raw = expr.toString().trim();
        if (raw.isEmpty()) return;
        String completed = buildCompletedExpressionForEvaluation(raw);
        if (TextUtils.isEmpty(completed)) {
            Toast.makeText(this, getString(R.string.calculator_finish_expression_first), Toast.LENGTH_SHORT).show();
            return;
        }
        expr.setLength(0);
        expr.append("1/(").append(completed).append(')');
        updateDisplayedState();
    }

    private void appendDot() {
        if (expr.length() == 0) {
            expr.append("0.");
            updateDisplayedState();
            return;
        }
        char last = expr.charAt(expr.length() - 1);
        if (isOp(last) || last == '%' || last == '(' || last == '√' || last == '^') {
            expr.append("0.");
            updateDisplayedState();
            return;
        }
        if (last == ')' || last == '²' || last == 'π' || last == 'e' || last == '!') {
            expr.append("*0.");
            updateDisplayedState();
            return;
        }

        int i = expr.length() - 1;
        while (i >= 0) {
            char c = expr.charAt(i);
            if (c == '.') return;
            if (isOp(c) || c == '%' || c == '(' || c == ')' || c == '√'
                    || c == '²' || c == '^' || c == '!') break;
            i--;
        }
        expr.append('.');
        updateDisplayedState();
    }

    private void appendPercent() {
        if (expr.length() == 0) return;
        char last = expr.charAt(expr.length() - 1);
        if (isValueEnd(last)) {
            expr.append('%');
            updateDisplayedState();
        }
    }

    private void appendOperator(char op) {
        if (expr.length() == 0) {
            if (op == '-') {
                expr.append(op);
                updateDisplayedState();
            }
            return;
        }

        char last = expr.charAt(expr.length() - 1);
        if (last == '.' || last == '(' || last == '√' || last == '^') return;
        if (isOp(last)) {
            expr.setCharAt(expr.length() - 1, op);
            updateDisplayedState();
            return;
        }
        expr.append(op);
        updateDisplayedState();
    }

    private void toggleSign() {
        if (expr.length() == 0) {
            expr.append('-');
            updateDisplayedState();
            return;
        }
        int end = expr.length() - 1;
        char last = expr.charAt(end);
        if (!Character.isDigit(last) && last != '.') {
            if (isOp(last) || last == '(' || last == '√' || last == '^') {
                expr.append('-');
                updateDisplayedState();
            }
            return;
        }

        int start = end;
        while (start >= 0) {
            char c = expr.charAt(start);
            if (Character.isDigit(c) || c == '.') start--;
            else break;
        }
        int numStart = start + 1;
        boolean hasUnaryMinus = start >= 0
                && expr.charAt(start) == '-'
                && (start == 0 || isOp(expr.charAt(start - 1))
                || expr.charAt(start - 1) == '(' || expr.charAt(start - 1) == '√'
                || expr.charAt(start - 1) == '^');

        if (hasUnaryMinus) expr.deleteCharAt(start);
        else expr.insert(numStart, '-');
        updateDisplayedState();
    }

    private void updateDisplayedState() {
        updateExprText();
        updatePreviewResult();
    }

    private void updateExprText() {
        String shown = expr.toString()
                .replace("*", "×")
                .replace("/", "÷")
                .replace("-", "−")
                .replace("^", " ^ ");
        tvExpr.setText(shown);
        scrollToEnd(scrollExpr);
    }

    private void updatePreviewResult() {
        String raw = expr.toString().trim();
        if (raw.isEmpty()) {
            tvResult.setText("0");
            scrollToEnd(scrollResult);
            return;
        }
        String completed = buildCompletedExpressionForEvaluation(raw);
        if (TextUtils.isEmpty(completed)) {
            tvResult.setText("…");
            scrollToEnd(scrollResult);
            return;
        }
        Double val = eval(completed);
        if (val == null || val.isNaN() || val.isInfinite()) {
            tvResult.setText("…");
            scrollToEnd(scrollResult);
            return;
        }
        tvResult.setText(formatNumber(val));
        scrollToEnd(scrollResult);
    }

    private void scrollToEnd(HorizontalScrollView view) {
        if (view == null) return;
        view.post(() -> view.fullScroll(View.FOCUS_RIGHT));
    }

    private String buildCompletedExpressionForEvaluation(String raw) {
        if (TextUtils.isEmpty(raw)) return null;
        int balance = 0;
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c == '(') balance++;
            else if (c == ')') balance--;
            if (balance < 0) return null;
        }
        char last = raw.charAt(raw.length() - 1);
        if (!isValueEnd(last)) return null;
        if (balance == 0) return raw;
        StringBuilder completed = new StringBuilder(raw);
        while (balance-- > 0) completed.append(')');
        return completed.toString();
    }

    private void clearAll() {
        expr.setLength(0);
        tvExpr.setText("");
        tvHistory.setText("");
        tvResult.setText("0");
        scrollToEnd(scrollExpr);
        scrollToEnd(scrollResult);
    }

    private void deleteOne() {
        if (expr.length() == 0) return;
        expr.deleteCharAt(expr.length() - 1);
        updateDisplayedState();
    }

    private void onEquals() {
        String raw = expr.toString().trim();
        if (raw.isEmpty()) return;

        if (prefs.isDisguiseModeEnabled() && prefs.checkPassword(raw)) {
            openMain(true);
            return;
        }

        String completed = buildCompletedExpressionForEvaluation(raw);
        if (TextUtils.isEmpty(completed)) {
            tvResult.setText(getString(R.string.error_label));
            scrollToEnd(scrollResult);
            return;
        }

        Double val = eval(completed);
        if (val == null || val.isNaN() || val.isInfinite()) {
            tvResult.setText(getString(R.string.error_label));
            scrollToEnd(scrollResult);
            return;
        }

        String shownExpr = completed.replace("*", "×")
                .replace("/", "÷")
                .replace("-", "−")
                .replace("^", " ^ ");
        String out = formatNumber(val);
        tvHistory.setText(shownExpr + " =");
        tvResult.setText(out);
        expr.setLength(0);
        expr.append(out);
        updateExprText();
        scrollToEnd(scrollResult);
    }

    private void applyMemoryOperation(boolean plus) {
        Double current = getCurrentDisplayedValue();
        if (current == null) {
            Toast.makeText(this, getString(R.string.calculator_no_valid_result), Toast.LENGTH_SHORT).show();
            return;
        }
        if (!hasMemory) {
            memoryValue = plus ? current : -current;
            hasMemory = true;
        } else {
            memoryValue = plus ? memoryValue + current : memoryValue - current;
        }
        updateMemoryLabel();
        Toast.makeText(this, plus ? getString(R.string.calculator_memory_added) : getString(R.string.calculator_memory_subtracted), Toast.LENGTH_SHORT).show();
    }

    private void recallMemory() {
        if (!hasMemory) {
            Toast.makeText(this, getString(R.string.calculator_memory_empty), Toast.LENGTH_SHORT).show();
            return;
        }
        String value = formatNumber(memoryValue);
        if (expr.length() == 0) {
            expr.append(value);
        } else {
            char last = expr.charAt(expr.length() - 1);
            if (isValueEnd(last)) expr.append('*');
            expr.append(value);
        }
        updateDisplayedState();
    }

    private void updateMemoryLabel() {
        if (tvMemory == null) return;
        tvMemory.setText(hasMemory ? "M: " + formatNumber(memoryValue) : "M: —");
    }

    private Double getCurrentDisplayedValue() {
        String value = tvResult == null ? null : tvResult.getText().toString();
        if (TextUtils.isEmpty(value) || "…".equals(value) || getString(R.string.error_label).equals(value)) return null;
        try {
            return Double.parseDouble(value);
        } catch (Exception e) {
            return null;
        }
    }

    private String formatNumber(double value) {
        if (Math.abs(value) < 0.0000000001d) value = 0d;
        BigDecimal bd = BigDecimal.valueOf(value).stripTrailingZeros();
        return bd.toPlainString();
    }

    private int countOpenParens() {
        int balance = 0;
        for (int i = 0; i < expr.length(); i++) {
            char c = expr.charAt(i);
            if (c == '(') balance++;
            else if (c == ')') balance--;
        }
        return balance;
    }

    private Double eval(String s) {
        try {
            ExpressionParser parser = new ExpressionParser(s);
            double value = parser.parse();
            if (Double.isNaN(value) || Double.isInfinite(value)) return null;
            return value;
        } catch (Exception e) {
            return null;
        }
    }

    private boolean isValueEnd(char c) {
        return Character.isDigit(c) || c == '.' || c == ')' || c == '%' || c == '²'
                || c == 'π' || c == 'e' || c == '!';
    }

    private boolean isOp(char c) {
        return c == '+' || c == '-' || c == '*' || c == '/';
    }

    private void openMain(boolean unlocked) {
        Intent i = new Intent(this, MainActivity.class);
        if (unlocked) i.putExtra("disguise_unlock", true);
        i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(i);
        finish();
    }

    private static class ExpressionParser {
        private final String input;
        private int pos = 0;

        ExpressionParser(String input) {
            this.input = input == null ? "" : input;
        }

        double parse() {
            double value = parseExpression();
            skipSpaces();
            if (pos != input.length()) throw new IllegalArgumentException("Unexpected token");
            return value;
        }

        private double parseExpression() {
            TermValue first = parseTerm();
            double value = first.relativePercent ? first.value / 100d : first.value;
            while (true) {
                skipSpaces();
                if (consume('+')) {
                    TermValue right = parseTerm();
                    value += right.relativePercent ? (value * right.value / 100d) : right.value;
                } else if (consume('-')) {
                    TermValue right = parseTerm();
                    value -= right.relativePercent ? (value * right.value / 100d) : right.value;
                } else {
                    return value;
                }
            }
        }

        private TermValue parseTerm() {
            TermValue left = parsePower();
            while (true) {
                skipSpaces();
                if (consume('*')) {
                    left = new TermValue(normalizePercent(left) * normalizePercent(parsePower()), false);
                } else if (consume('/')) {
                    double divisor = normalizePercent(parsePower());
                    if (Math.abs(divisor) < 0.0000000001d) throw new ArithmeticException("Division by zero");
                    left = new TermValue(normalizePercent(left) / divisor, false);
                } else {
                    return left;
                }
            }
        }

        private TermValue parsePower() {
            TermValue base = parseUnary();
            skipSpaces();
            if (consume('^')) {
                TermValue exponent = parsePower();
                return new TermValue(Math.pow(normalizePercent(base), normalizePercent(exponent)), false);
            }
            return base;
        }

        private TermValue parseUnary() {
            skipSpaces();
            if (consume('+')) return parseUnary();
            if (consume('-')) {
                TermValue inner = parseUnary();
                inner.value = -inner.value;
                return inner;
            }
            if (consume('√')) {
                double value = normalizePercent(parseUnary());
                if (value < 0d) throw new ArithmeticException("Negative root");
                return new TermValue(Math.sqrt(value), false);
            }
            return parsePostfix();
        }

        private TermValue parsePostfix() {
            TermValue value = parsePrimary();
            while (true) {
                skipSpaces();
                if (consume('%')) {
                    value.relativePercent = true;
                } else if (consume('²')) {
                    double base = normalizePercent(value);
                    value.value = base * base;
                    value.relativePercent = false;
                } else if (consume('!')) {
                    value.value = factorial(normalizePercent(value));
                    value.relativePercent = false;
                } else {
                    return value;
                }
            }
        }

        private TermValue parsePrimary() {
            skipSpaces();
            if (consume('(')) {
                double value = parseExpression();
                skipSpaces();
                if (!consume(')')) throw new IllegalArgumentException("Missing )");
                return new TermValue(value, false);
            }
            if (consume('π')) return new TermValue(Math.PI, false);
            if (consume('e')) return new TermValue(Math.E, false);
            if (peekLetter()) return parseFunction();
            return parseNumber();
        }

        private TermValue parseFunction() {
            String name = parseIdentifier().toLowerCase(Locale.US);
            double arg;
            if (consume('(')) {
                arg = parseExpression();
                skipSpaces();
                if (!consume(')')) throw new IllegalArgumentException("Missing )");
            } else {
                arg = normalizePercent(parseUnary());
            }
            switch (name) {
                case "sin": return new TermValue(Math.sin(Math.toRadians(arg)), false);
                case "cos": return new TermValue(Math.cos(Math.toRadians(arg)), false);
                case "tan": return new TermValue(Math.tan(Math.toRadians(arg)), false);
                case "ln":
                    if (arg <= 0d) throw new ArithmeticException("ln domain");
                    return new TermValue(Math.log(arg), false);
                case "log":
                    if (arg <= 0d) throw new ArithmeticException("log domain");
                    return new TermValue(Math.log10(arg), false);
                case "abs": return new TermValue(Math.abs(arg), false);
                default: throw new IllegalArgumentException("Unknown function");
            }
        }

        private String parseIdentifier() {
            skipSpaces();
            int start = pos;
            while (pos < input.length() && Character.isLetter(input.charAt(pos))) pos++;
            if (start == pos) throw new IllegalArgumentException("Missing function");
            return input.substring(start, pos);
        }

        private TermValue parseNumber() {
            skipSpaces();
            int start = pos;
            boolean hasDot = false;
            while (pos < input.length()) {
                char c = input.charAt(pos);
                if (Character.isDigit(c)) {
                    pos++;
                } else if (c == '.' && !hasDot) {
                    hasDot = true;
                    pos++;
                } else {
                    break;
                }
            }
            if (start == pos) throw new IllegalArgumentException("Missing number");
            return new TermValue(Double.parseDouble(input.substring(start, pos)), false);
        }

        private boolean peekLetter() {
            skipSpaces();
            return pos < input.length() && Character.isLetter(input.charAt(pos));
        }

        private boolean consume(char expected) {
            skipSpaces();
            if (pos < input.length() && input.charAt(pos) == expected) {
                pos++;
                return true;
            }
            return false;
        }

        private void skipSpaces() {
            while (pos < input.length() && Character.isWhitespace(input.charAt(pos))) pos++;
        }

        private static double normalizePercent(TermValue value) {
            return value.relativePercent ? value.value / 100d : value.value;
        }

        private static double factorial(double value) {
            if (value < 0d) throw new ArithmeticException("Negative factorial");
            long rounded = Math.round(value);
            if (Math.abs(value - rounded) > 0.0000001d) {
                throw new ArithmeticException("Factorial integer only");
            }
            if (rounded > 20) throw new ArithmeticException("Factorial too large");
            double result = 1d;
            for (long i = 2; i <= rounded; i++) result *= i;
            return result;
        }

        private static class TermValue {
            double value;
            boolean relativePercent;

            TermValue(double value, boolean relativePercent) {
                this.value = value;
                this.relativePercent = relativePercent;
            }
        }
    }
}
