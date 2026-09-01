package cn.iocoder.yudao.module.tk.sql;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TkSqlMigrationIdempotencyTest {

    private static final String RESOURCE = "/sql/tk_tiktok_publish_center_upgrade_mysql.sql";

    @Test
    void tiktokPublishCenterMigrationIndependentlyGuardsEveryColumnOperation() throws IOException {
        String sql = readMigration();

        assertIndependentlyGuarded(sql, "tk_tiktok_publish_task", "generation_task_id", "MODIFY\\s+COLUMN");
        assertIndependentlyGuarded(sql, "tk_tiktok_publish_task", "uploaded_video_id", "ADD\\s+COLUMN");
        assertIndependentlyGuarded(sql, "tk_tiktok_publish_task", "source_type", "ADD\\s+COLUMN");
        assertIndependentlyGuarded(sql, "tk_tiktok_publish_task", "cover_url", "ADD\\s+COLUMN");
        assertIndependentlyGuarded(sql, "tk_tiktok_publish_task", "cover_timestamp_ms", "ADD\\s+COLUMN");

        assertIndependentlyGuarded(sql, "tk_tiktok_publish_detail", "generation_task_id", "MODIFY\\s+COLUMN");
        assertIndependentlyGuarded(sql, "tk_tiktok_publish_detail", "uploaded_video_id", "ADD\\s+COLUMN");
        assertIndependentlyGuarded(sql, "tk_tiktok_publish_detail", "source_type", "ADD\\s+COLUMN");
        assertIndependentlyGuarded(sql, "tk_tiktok_publish_detail", "cover_url", "ADD\\s+COLUMN");
        assertIndependentlyGuarded(sql, "tk_tiktok_publish_detail", "cover_timestamp_ms", "ADD\\s+COLUMN");

        assertNoCombinedAddColumns(sql);
        assertPreparedStatementLifecycles(sql);
    }

    @Test
    void combinedAddColumnValidatorRejectsDirectAndConcatStatements() {
        String directSql = "ALTER TABLE `tk_tiktok_publish_task` "
                + "ADD COLUMN `uploaded_video_id` bigint, ADD COLUMN `source_type` varchar(32);";
        String concatSql = "SET @migration_sql := CONCAT("
                + "'ALTER TABLE `tk_tiktok_publish_detail` ADD COLUMN `cover_url` varchar(512)', "
                + "', ADD COLUMN `cover_timestamp_ms` bigint');";

        assertThrows(AssertionError.class, () -> assertNoCombinedAddColumns(directSql));
        assertThrows(AssertionError.class, () -> assertNoCombinedAddColumns(concatSql));
    }

    @Test
    void preparedStatementValidatorRequiresExecuteAndDeallocate() {
        String missingExecute = guardedColumnFixture("DEALLOCATE PREPARE fixture_stmt;");
        String missingDeallocate = guardedColumnFixture("EXECUTE fixture_stmt;");

        assertThrows(AssertionError.class, () -> assertPreparedStatementLifecycles(missingExecute));
        assertThrows(AssertionError.class, () -> assertPreparedStatementLifecycles(missingDeallocate));
    }

    @Test
    void preparedStatementValidatorIgnoresCommentsAndStringsAndRequiresOrder() {
        String lineCommentExecute = guardedColumnFixture(
                "-- EXECUTE fixture_stmt;\nDEALLOCATE PREPARE fixture_stmt;");
        String blockCommentDeallocate = guardedColumnFixture(
                "EXECUTE fixture_stmt;\n/* DEALLOCATE PREPARE fixture_stmt; */");
        String stringDeallocate = guardedColumnFixture(
                "EXECUTE fixture_stmt;\nSET @decoy := 'DEALLOCATE PREPARE fixture_stmt;';");
        String executeBeforePrepare = "EXECUTE fixture_stmt;\n"
                + guardedColumnFixture("DEALLOCATE PREPARE fixture_stmt;");

        assertAll(
                () -> assertThrows(AssertionError.class,
                        () -> assertPreparedStatementLifecycles(lineCommentExecute)),
                () -> assertThrows(AssertionError.class,
                        () -> assertPreparedStatementLifecycles(blockCommentDeallocate)),
                () -> assertThrows(AssertionError.class,
                        () -> assertPreparedStatementLifecycles(stringDeallocate)),
                () -> assertThrows(AssertionError.class,
                        () -> assertPreparedStatementLifecycles(executeBeforePrepare))
        );
    }

    @Test
    void independentGuardValidatorRejectsMismatchedIfVariable() {
        String mismatchedGuard = guardedColumnFixture(
                "EXECUTE fixture_stmt;\nDEALLOCATE PREPARE fixture_stmt;")
                .replace("IF(@fixture_exists = 0", "IF(@other_exists = 0");

        assertThrows(AssertionError.class, () -> assertIndependentlyGuarded(mismatchedGuard,
                "tk_tiktok_publish_task", "uploaded_video_id", "ADD\\s+COLUMN"));
    }

    @Test
    void independentGuardValidatorRejectsCommentedBlocks() {
        String validBlock = guardedColumnFixture(
                "EXECUTE fixture_stmt;\nDEALLOCATE PREPARE fixture_stmt;");
        String lineCommentBlock = "-- " + validBlock.replace("\n", " ") + "\nSELECT 1;";
        String blockCommentBlock = "/* " + validBlock + " */\nSELECT 1;";

        assertAll(
                () -> assertThrows(AssertionError.class, () -> assertIndependentlyGuarded(lineCommentBlock,
                        "tk_tiktok_publish_task", "uploaded_video_id", "ADD\\s+COLUMN")),
                () -> assertThrows(AssertionError.class, () -> assertIndependentlyGuarded(blockCommentBlock,
                        "tk_tiktok_publish_task", "uploaded_video_id", "ADD\\s+COLUMN"))
        );
    }

    @Test
    void independentGuardValidatorRequiresTopLevelContiguousBoundBlock() {
        String validBlock = guardedColumnFixture(
                "EXECUTE fixture_stmt;\nDEALLOCATE PREPARE fixture_stmt;");
        String blockInsideString = "SET @wrapper := \"" + validBlock + "\";";
        String nonContiguousBlock = validBlock.replace(
                "SET @fixture_sql", "SELECT 1;\nSET @fixture_sql");
        String mismatchedLifecycle = guardedColumnFixture(
                "EXECUTE other_stmt;\nDEALLOCATE PREPARE other_stmt;");

        assertAll(
                () -> assertThrows(AssertionError.class, () -> assertIndependentlyGuarded(blockInsideString,
                        "tk_tiktok_publish_task", "uploaded_video_id", "ADD\\s+COLUMN")),
                () -> assertThrows(AssertionError.class, () -> assertIndependentlyGuarded(nonContiguousBlock,
                        "tk_tiktok_publish_task", "uploaded_video_id", "ADD\\s+COLUMN")),
                () -> assertThrows(AssertionError.class, () -> assertIndependentlyGuarded(mismatchedLifecycle,
                        "tk_tiktok_publish_task", "uploaded_video_id", "ADD\\s+COLUMN"))
        );
    }

    @Test
    void preparedStatementValidatorHandlesDoubleQuotedStringsAndAnyFromSource() {
        String doubleQuotedExecute = guardedColumnFixture(
                "SET @decoy := \"; EXECUTE fixture_stmt;\";\nDEALLOCATE PREPARE fixture_stmt;");
        String untrackedPrepare = guardedColumnFixture(
                "EXECUTE fixture_stmt;\nDEALLOCATE PREPARE fixture_stmt;")
                + "\nPREPARE rogue_stmt FROM 'SELECT 1';";

        assertAll(
                () -> assertThrows(AssertionError.class,
                        () -> assertPreparedStatementLifecycles(doubleQuotedExecute)),
                () -> assertThrows(AssertionError.class,
                        () -> assertPreparedStatementLifecycles(untrackedPrepare))
        );
    }

    @Test
    void combinedAddColumnValidatorRejectsCommentObfuscationAndAssembly() {
        String commentObfuscated = "ALTER TABLE `tk_tiktok_publish_task` "
                + "ADD/* split keyword */COLUMN `uploaded_video_id` bigint, "
                + "ADD COLUMN `source_type` varchar(32);";

        assertThrows(AssertionError.class, () -> assertNoCombinedAddColumns(commentObfuscated));
    }

    @Test
    void combinedAddColumnValidatorAllowsUnrelatedAndSingleColumnConcat() {
        String unrelatedConcat = "SET @label := CONCAT('publish', '-', 'center');";
        String singleColumnConcat = "SET @migration_sql := CONCAT("
                + "'ALTER TABLE `tk_tiktok_publish_task` ADD ', "
                + "'COLUMN `uploaded_video_id` bigint DEFAULT NULL');";
        String unrelatedVariableAssignment = "SET @migration_sql := @unrelated_fragment;";

        assertAll(
                () -> assertNoCombinedAddColumns(unrelatedConcat),
                () -> assertNoCombinedAddColumns(singleColumnConcat),
                () -> assertNoCombinedAddColumns(unrelatedVariableAssignment)
        );
    }

    @Test
    void independentGuardValidatorRequiresOperationSpecificCondition() {
        String wrongAddDirection = guardedColumnFixture(
                "EXECUTE fixture_stmt;\nDEALLOCATE PREPARE fixture_stmt;")
                .replace("@fixture_exists = 0", "@fixture_exists = 1");
        String wrongModifyDirection = nullableColumnFixture()
                .replace("@fixture_nullable = 1", "@fixture_nullable = 0");
        String guardOnlyInComment = guardedColumnFixture(
                "EXECUTE fixture_stmt;\nDEALLOCATE PREPARE fixture_stmt;")
                .replace("@fixture_exists = 0", "@other_exists = 0 /* @fixture_exists = 0 */");

        assertAll(
                () -> assertThrows(AssertionError.class, () -> assertIndependentlyGuarded(wrongAddDirection,
                        "tk_tiktok_publish_task", "uploaded_video_id", "ADD\\s+COLUMN")),
                () -> assertThrows(AssertionError.class, () -> assertIndependentlyGuarded(wrongModifyDirection,
                        "tk_tiktok_publish_task", "generation_task_id", "MODIFY\\s+COLUMN")),
                () -> assertThrows(AssertionError.class, () -> assertIndependentlyGuarded(guardOnlyInComment,
                        "tk_tiktok_publish_task", "uploaded_video_id", "ADD\\s+COLUMN"))
        );
    }

    private static void assertIndependentlyGuarded(String sql, String table, String column, String operation) {
        List<String> statements = SqlTestLexer.topLevelStatements(sql);
        Pattern guardPattern = Pattern.compile("(?is)^\\s*SET\\s+@([a-z0-9_]+?)_(exists|nullable)\\s*:=\\s*\\("
                + "[^;]*?information_schema\\.columns"
                + "[^;]*?table_name\\s*=\\s*'" + Pattern.quote(table) + "'"
                + "[^;]*?column_name\\s*=\\s*'" + Pattern.quote(column) + "'"
                + "[^;]*?\\)\\s*$");
        int guardIndex = -1;
        Matcher guardMatcher = null;
        int guardCount = 0;
        for (int index = 0; index < statements.size(); index++) {
            Matcher candidate = guardPattern.matcher(statements.get(index));
            if (candidate.matches()) {
                guardCount++;
                guardIndex = index;
                guardMatcher = candidate;
            }
        }

        assertEquals(1, guardCount, "expected one existence guard for " + table + "." + column);
        assertNotNull(guardMatcher, "missing existence guard for " + table + "." + column);
        String variable = guardMatcher.group(1);
        String guardKind = guardMatcher.group(2);
        String guardVariable = variable + "_" + guardKind;
        boolean addOperation = "ADD\\s+COLUMN".equals(operation);
        boolean modifyOperation = "MODIFY\\s+COLUMN".equals(operation);
        assertTrue(addOperation || modifyOperation, "unsupported migration operation: " + operation);
        assertEquals(addOperation ? "exists" : "nullable", guardKind,
                "guard kind must match migration operation for " + table + "." + column);
        int expectedGuardValue = addOperation ? 0 : 1;
        assertTrue(guardIndex + 4 < statements.size(),
                "incomplete guarded statement block for " + table + "." + column);

        Pattern sqlSetPattern = Pattern.compile("(?is)^\\s*SET\\s+@" + Pattern.quote(variable)
                + "_sql\\s*:=\\s*IF\\s*\\("
                + "\\s*@" + Pattern.quote(guardVariable) + "\\s*=\\s*" + expectedGuardValue + "\\s*,"
                + "\\s*'((?:''|\\\\.|[^'])*)'\\s*,"
                + "\\s*'(?:''|\\\\.|[^'])*'\\s*\\)\\s*$");
        Matcher sqlSetMatcher = sqlSetPattern.matcher(statements.get(guardIndex + 1));
        assertTrue(sqlSetMatcher.matches(),
                "guard must be followed by a matching literal IF statement for " + table + "." + column);
        assertSingleColumnAlter(decodeSqlString(sqlSetMatcher.group(1)), table, column, operation);

        Pattern preparePattern = Pattern.compile("(?is)^\\s*PREPARE\\s+([a-z0-9_]+)\\s+FROM\\s+@"
                + Pattern.quote(variable) + "_sql\\s*$");
        Matcher prepareMatcher = preparePattern.matcher(statements.get(guardIndex + 2));
        assertTrue(prepareMatcher.matches(),
                "IF statement must be followed by PREPARE from its SQL variable for " + table + "." + column);
        String preparedStatement = prepareMatcher.group(1);

        assertTrue(Pattern.compile("(?is)^\\s*EXECUTE\\s+" + Pattern.quote(preparedStatement) + "\\s*$")
                        .matcher(statements.get(guardIndex + 3)).matches(),
                "PREPARE must be followed by same-name EXECUTE for " + table + "." + column);
        assertTrue(Pattern.compile("(?is)^\\s*DEALLOCATE\\s+PREPARE\\s+"
                        + Pattern.quote(preparedStatement) + "\\s*$")
                        .matcher(statements.get(guardIndex + 4)).matches(),
                "EXECUTE must be followed by same-name DEALLOCATE PREPARE for " + table + "." + column);
    }

    private static void assertSingleColumnAlter(String payload, String table, String column, String operation) {
        String commentFreePayload = SqlTestLexer.stripComments(payload);
        Pattern expectedAlter = Pattern.compile("(?is)^\\s*ALTER\\s+TABLE\\s+`?" + Pattern.quote(table)
                + "`?\\s+" + operation + "\\s+`?" + Pattern.quote(column) + "`?(?=\\s|$).*$");
        assertTrue(expectedAlter.matcher(commentFreePayload).matches(),
                "guarded payload must alter only " + table + "." + column);
        Pattern columnOperation = Pattern.compile("(?is)\\b(?:ADD|MODIFY)\\s+COLUMN\\b");
        assertEquals(1, countMatches(columnOperation.matcher(commentFreePayload)),
                "guarded payload must contain exactly one column operation for " + table + "." + column);
    }

    private static int countMatches(Matcher matcher) {
        int count = 0;
        while (matcher.find()) {
            count++;
        }
        return count;
    }

    private static void assertNoCombinedAddColumns(String sql) {
        for (String statement : SqlTestLexer.topLevelStatements(sql)) {
            assertAtMostOneAddColumn(statement);
            for (String literal : SqlTestLexer.singleQuotedLiterals(statement)) {
                assertAtMostOneAddColumn(literal);
            }
            for (String concatValue : SqlTestLexer.staticConcatValues(statement)) {
                assertAtMostOneAddColumn(concatValue);
            }
        }
    }

    private static void assertAtMostOneAddColumn(String payload) {
        String commentFreePayload = SqlTestLexer.stripComments(payload);
        Pattern targetAlter = Pattern.compile("(?is)^\\s*ALTER\\s+TABLE\\s+`?"
                + "(?:tk_tiktok_publish_task|tk_tiktok_publish_detail)`?(?=\\s|$)");
        if (targetAlter.matcher(commentFreePayload).find()) {
            Pattern addColumn = Pattern.compile("(?is)\\bADD\\s+COLUMN\\b");
            assertFalse(countMatches(addColumn.matcher(commentFreePayload)) > 1,
                    "migration must not combine multiple ADD COLUMN operations");
        }
    }

    private static String decodeSqlString(String encoded) {
        StringBuilder decoded = new StringBuilder();
        for (int index = 0; index < encoded.length(); index++) {
            char character = encoded.charAt(index);
            if (character == '\'' && index + 1 < encoded.length() && encoded.charAt(index + 1) == '\'') {
                decoded.append('\'');
                index++;
            } else if (character == '\\' && index + 1 < encoded.length()) {
                decoded.append(encoded.charAt(++index));
            } else {
                decoded.append(character);
            }
        }
        return decoded.toString();
    }

    private static void assertPreparedStatementLifecycles(String sql) {
        Pattern preparePattern = Pattern.compile("(?is)^\\s*PREPARE\\s+([a-z0-9_]+)\\s+FROM\\b.*$");
        Pattern executePattern = Pattern.compile("(?is)^\\s*EXECUTE\\s+([a-z0-9_]+)\\s*$");
        Pattern deallocatePattern = Pattern.compile("(?is)^\\s*DEALLOCATE\\s+PREPARE\\s+([a-z0-9_]+)\\s*$");
        List<String> lifecycleEvents = new ArrayList<>();
        for (String statement : SqlTestLexer.executableStatements(sql)) {
            Matcher prepareMatcher = preparePattern.matcher(statement);
            Matcher executeMatcher = executePattern.matcher(statement);
            Matcher deallocateMatcher = deallocatePattern.matcher(statement);
            if (prepareMatcher.matches()) {
                lifecycleEvents.add("PREPARE:" + prepareMatcher.group(1));
            } else if (executeMatcher.matches()) {
                lifecycleEvents.add("EXECUTE:" + executeMatcher.group(1));
            } else if (deallocateMatcher.matches()) {
                lifecycleEvents.add("DEALLOCATE:" + deallocateMatcher.group(1));
            }
        }

        assertTrue(!lifecycleEvents.isEmpty(), "expected at least one PREPARE statement");
        assertEquals(0, lifecycleEvents.size() % 3,
                "prepared statement lifecycle events must form PREPARE/EXECUTE/DEALLOCATE groups");
        for (int index = 0; index < lifecycleEvents.size(); index += 3) {
            String prepareEvent = lifecycleEvents.get(index);
            assertTrue(prepareEvent.startsWith("PREPARE:"),
                    "prepared statement lifecycle must start with PREPARE");
            String statementName = prepareEvent.substring("PREPARE:".length());
            assertEquals("EXECUTE:" + statementName, lifecycleEvents.get(index + 1),
                    "PREPARE must be followed by same-name EXECUTE");
            assertEquals("DEALLOCATE:" + statementName, lifecycleEvents.get(index + 2),
                    "EXECUTE must be followed by same-name DEALLOCATE PREPARE");
        }
    }

    private static final class SqlTestLexer {

        private SqlTestLexer() {
        }

        private static String stripComments(String sql) {
            StringBuilder result = new StringBuilder();
            boolean inSingleQuote = false;
            boolean inDoubleQuote = false;
            boolean inLineComment = false;
            boolean inBlockComment = false;
            for (int index = 0; index < sql.length(); index++) {
                char character = sql.charAt(index);
                if (inLineComment) {
                    if (character == '\r' || character == '\n') {
                        inLineComment = false;
                        result.append(character);
                    }
                    continue;
                }
                if (inBlockComment) {
                    if (character == '*' && index + 1 < sql.length() && sql.charAt(index + 1) == '/') {
                        inBlockComment = false;
                        index++;
                        result.append(' ');
                    }
                    continue;
                }
                if (inSingleQuote || inDoubleQuote) {
                    result.append(character);
                    char quote = inSingleQuote ? '\'' : '"';
                    if (character == '\\' && index + 1 < sql.length()) {
                        result.append(sql.charAt(++index));
                    } else if (character == quote && index + 1 < sql.length() && sql.charAt(index + 1) == quote) {
                        result.append(sql.charAt(++index));
                    } else if (character == quote) {
                        inSingleQuote = false;
                        inDoubleQuote = false;
                    }
                    continue;
                }
                if (character == '\'' || character == '"') {
                    inSingleQuote = character == '\'';
                    inDoubleQuote = character == '"';
                    result.append(character);
                } else if (character == '#') {
                    inLineComment = true;
                    result.append(' ');
                } else if (isDashCommentStart(sql, index)) {
                    inLineComment = true;
                    index++;
                    result.append(' ');
                } else if (character == '/' && index + 1 < sql.length() && sql.charAt(index + 1) == '*') {
                    inBlockComment = true;
                    index++;
                    result.append(' ');
                } else {
                    result.append(character);
                }
            }
            return result.toString();
        }

        private static List<String> splitStatements(String sql) {
            List<String> statements = new ArrayList<>();
            StringBuilder statement = new StringBuilder();
            boolean inSingleQuote = false;
            boolean inDoubleQuote = false;
            for (int index = 0; index < sql.length(); index++) {
                char character = sql.charAt(index);
                statement.append(character);
                if (inSingleQuote || inDoubleQuote) {
                    char quote = inSingleQuote ? '\'' : '"';
                    if (character == '\\' && index + 1 < sql.length()) {
                        statement.append(sql.charAt(++index));
                    } else if (character == quote && index + 1 < sql.length() && sql.charAt(index + 1) == quote) {
                        statement.append(sql.charAt(++index));
                    } else if (character == quote) {
                        inSingleQuote = false;
                        inDoubleQuote = false;
                    }
                } else if (character == '\'' || character == '"') {
                    inSingleQuote = character == '\'';
                    inDoubleQuote = character == '"';
                } else if (character == ';') {
                    statement.setLength(statement.length() - 1);
                    statements.add(statement.toString());
                    statement.setLength(0);
                }
            }
            if (statement.length() > 0) {
                statements.add(statement.toString());
            }
            return statements;
        }

        private static List<String> topLevelStatements(String sql) {
            List<String> statements = new ArrayList<>();
            for (String statement : splitStatements(stripComments(sql))) {
                String trimmed = statement.trim();
                if (!trimmed.isEmpty()) {
                    statements.add(trimmed);
                }
            }
            return statements;
        }

        private static List<String> executableStatements(String sql) {
            List<String> statements = new ArrayList<>();
            for (String statement : topLevelStatements(sql)) {
                statements.add(stripQuotedContents(statement));
            }
            return statements;
        }

        private static List<String> singleQuotedLiterals(String sql) {
            List<String> literals = new ArrayList<>();
            for (int index = 0; index < sql.length(); index++) {
                char character = sql.charAt(index);
                if (character == '"') {
                    index = findQuoteEnd(sql, index, '"');
                } else if (character == '\'') {
                    SqlStringLiteral literal = readSingleQuotedLiteral(sql, index);
                    if (literal == null) {
                        break;
                    }
                    literals.add(literal.value);
                    index = literal.nextIndex - 1;
                }
            }
            return literals;
        }

        private static List<String> staticConcatValues(String sql) {
            List<String> values = new ArrayList<>();
            for (int index = 0; index < sql.length(); index++) {
                char character = sql.charAt(index);
                if (character == '\'' || character == '"') {
                    index = findQuoteEnd(sql, index, character);
                    continue;
                }
                if (!isKeywordAt(sql, index, "CONCAT")) {
                    continue;
                }
                int openParenthesis = index + "CONCAT".length();
                while (openParenthesis < sql.length() && Character.isWhitespace(sql.charAt(openParenthesis))) {
                    openParenthesis++;
                }
                if (openParenthesis >= sql.length() || sql.charAt(openParenthesis) != '(') {
                    continue;
                }
                int closeParenthesis = findClosingParenthesis(sql, openParenthesis);
                if (closeParenthesis < 0) {
                    continue;
                }
                String value = parseStaticStringArguments(sql.substring(openParenthesis + 1, closeParenthesis));
                if (value != null) {
                    values.add(value);
                }
                index = closeParenthesis;
            }
            return values;
        }

        private static String parseStaticStringArguments(String arguments) {
            StringBuilder value = new StringBuilder();
            int index = 0;
            boolean foundArgument = false;
            while (true) {
                while (index < arguments.length() && Character.isWhitespace(arguments.charAt(index))) {
                    index++;
                }
                if (index >= arguments.length()) {
                    return foundArgument ? value.toString() : null;
                }
                if (arguments.charAt(index) != '\'') {
                    return null;
                }
                SqlStringLiteral literal = readSingleQuotedLiteral(arguments, index);
                if (literal == null) {
                    return null;
                }
                value.append(literal.value);
                foundArgument = true;
                index = literal.nextIndex;
                while (index < arguments.length() && Character.isWhitespace(arguments.charAt(index))) {
                    index++;
                }
                if (index >= arguments.length()) {
                    return value.toString();
                }
                if (arguments.charAt(index) != ',') {
                    return null;
                }
                index++;
            }
        }

        private static SqlStringLiteral readSingleQuotedLiteral(String sql, int quoteIndex) {
            StringBuilder value = new StringBuilder();
            for (int index = quoteIndex + 1; index < sql.length(); index++) {
                char character = sql.charAt(index);
                if (character == '\\' && index + 1 < sql.length()) {
                    value.append(sql.charAt(++index));
                } else if (character == '\'' && index + 1 < sql.length() && sql.charAt(index + 1) == '\'') {
                    value.append('\'');
                    index++;
                } else if (character == '\'') {
                    return new SqlStringLiteral(value.toString(), index + 1);
                } else {
                    value.append(character);
                }
            }
            return null;
        }

        private static int findClosingParenthesis(String sql, int openParenthesis) {
            int depth = 1;
            for (int index = openParenthesis + 1; index < sql.length(); index++) {
                char character = sql.charAt(index);
                if (character == '\'' || character == '"') {
                    index = findQuoteEnd(sql, index, character);
                } else if (character == '(') {
                    depth++;
                } else if (character == ')' && --depth == 0) {
                    return index;
                }
            }
            return -1;
        }

        private static int findQuoteEnd(String sql, int quoteIndex, char quote) {
            for (int index = quoteIndex + 1; index < sql.length(); index++) {
                char character = sql.charAt(index);
                if (character == '\\' && index + 1 < sql.length()) {
                    index++;
                } else if (character == quote && index + 1 < sql.length() && sql.charAt(index + 1) == quote) {
                    index++;
                } else if (character == quote) {
                    return index;
                }
            }
            return sql.length() - 1;
        }

        private static boolean isKeywordAt(String sql, int index, String keyword) {
            if (!sql.regionMatches(true, index, keyword, 0, keyword.length())) {
                return false;
            }
            boolean validStart = index == 0 || !isIdentifierCharacter(sql.charAt(index - 1));
            int end = index + keyword.length();
            boolean validEnd = end >= sql.length() || !isIdentifierCharacter(sql.charAt(end));
            return validStart && validEnd;
        }

        private static boolean isIdentifierCharacter(char character) {
            return Character.isLetterOrDigit(character) || character == '_';
        }

        private static String stripQuotedContents(String sql) {
            StringBuilder result = new StringBuilder();
            boolean inSingleQuote = false;
            boolean inDoubleQuote = false;
            for (int index = 0; index < sql.length(); index++) {
                char character = sql.charAt(index);
                if (inSingleQuote || inDoubleQuote) {
                    char quote = inSingleQuote ? '\'' : '"';
                    if (character == '\\' && index + 1 < sql.length()) {
                        index++;
                    } else if (character == quote && index + 1 < sql.length() && sql.charAt(index + 1) == quote) {
                        index++;
                    } else if (character == quote) {
                        inSingleQuote = false;
                        inDoubleQuote = false;
                    }
                } else if (character == '\'' || character == '"') {
                    inSingleQuote = character == '\'';
                    inDoubleQuote = character == '"';
                    result.append(' ');
                } else {
                    result.append(character);
                }
            }
            return result.toString();
        }

        private static boolean isDashCommentStart(String sql, int index) {
            return sql.charAt(index) == '-' && index + 1 < sql.length() && sql.charAt(index + 1) == '-'
                    && (index + 2 >= sql.length() || Character.isWhitespace(sql.charAt(index + 2)));
        }

        private static final class SqlStringLiteral {

            private final String value;
            private final int nextIndex;

            private SqlStringLiteral(String value, int nextIndex) {
                this.value = value;
                this.nextIndex = nextIndex;
            }

        }

    }

    private static String guardedColumnFixture(String lifecycleSql) {
        return "SET @fixture_exists := ("
                + "SELECT COUNT(*) FROM information_schema.columns "
                + "WHERE table_schema = DATABASE() AND table_name = 'tk_tiktok_publish_task' "
                + "AND column_name = 'uploaded_video_id');\n"
                + "SET @fixture_sql := IF(@fixture_exists = 0, "
                + "'ALTER TABLE `tk_tiktok_publish_task` ADD COLUMN `uploaded_video_id` bigint DEFAULT NULL', "
                + "'SELECT 1');\n"
                + "PREPARE fixture_stmt FROM @fixture_sql;\n"
                + lifecycleSql;
    }

    private static String nullableColumnFixture() {
        return "SET @fixture_nullable := ("
                + "SELECT COUNT(*) FROM information_schema.columns "
                + "WHERE table_schema = DATABASE() AND table_name = 'tk_tiktok_publish_task' "
                + "AND column_name = 'generation_task_id');\n"
                + "SET @fixture_sql := IF(@fixture_nullable = 1, "
                + "'ALTER TABLE `tk_tiktok_publish_task` MODIFY COLUMN `generation_task_id` bigint DEFAULT NULL', "
                + "'SELECT 1');\n"
                + "PREPARE fixture_stmt FROM @fixture_sql;\n"
                + "EXECUTE fixture_stmt;\n"
                + "DEALLOCATE PREPARE fixture_stmt;";
    }

    private static String readMigration() throws IOException {
        try (InputStream input = TkSqlMigrationIdempotencyTest.class.getResourceAsStream(RESOURCE)) {
            assertNotNull(input, "missing SQL migration resource: " + RESOURCE);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static void assertTrue(boolean condition, String message) {
        org.junit.jupiter.api.Assertions.assertTrue(condition, message);
    }

}
