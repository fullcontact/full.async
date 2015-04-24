package camelsnake;

public class Converter {

    private enum Type {
        UPPER,
        LOWER,
        DIGIT,
        DASH,
        UNDERSCORE,
        OTHER
    }

    static private Type getCharType(char ch) {
        if (Character.isLowerCase(ch)) {
            return Type.LOWER;
        } else if (Character.isUpperCase(ch)) {
            return Type.UPPER;
        } else if (Character.isDigit(ch)) {
            return Type.DIGIT;
        } else if (ch == '_') {
            return Type.UNDERSCORE;
        } else if (ch == '-') {
            return Type.DASH;
        } else {
            return Type.OTHER;
        }
    }

    /**
     * Converts string keyword formats for example:
     * AdventureTimes -> adventure-times
     * ADVENTURETimes -> adventure_times
     * adventure_times -> adventureTimes
     * etc
     *
     * This is implemented in Java for max performance as even tiny improvement
     * can significantly increase aggregate efficiency.
     *
     * @param in Input string.
     * @param sep Separator that will be inserted between parts. Null for none.
     * @param capitalizeFirst Whether to capitalize first letter of first part.
     * @param capitalizeRest Whether to capitalize first letter of rest of the
     *                       parts.
     * @return Converted string.
     */
    static public String convert(String in, String sep,
                                  boolean capitalizeFirst,
                                  boolean capitalizeRest) {
        StringBuilder out = new StringBuilder(in.length() + 2);
        Type prevType = null;
        int prevBoundary = 0;
        for (int i = 0; i < in.length(); i++) {
            char ch = in.charAt(i);
            Type type = getCharType(ch);
            if ((type != prevType
                    && (i - prevBoundary) > 0
                    && (Type.UPPER != prevType || Type.LOWER != type))
                    ||
                    // special case for HTTPServer -> http-server
                    (Type.UPPER == prevType
                            && Type.UPPER == type
                            && in.length() > (i + 1)
                            && Type.LOWER == getCharType(in.charAt(i + 1)))) {
                if (sep != null) {
                    out.append(sep);
                }
                prevBoundary = i;
            }
            if (type != Type.UNDERSCORE && type != Type.DASH) {
                if (prevBoundary == i) {
                    // start of new part
                    if ((i == 0 && capitalizeFirst)
                            || (i > 0 && capitalizeRest)) {
                        out.append(Character.toUpperCase(ch));
                    } else {
                        out.append(Character.toLowerCase(ch));
                    }
                } else {
                    out.append(Character.toLowerCase(ch));
                }
            } else {
                prevBoundary++;
            }
            prevType = type;
        }
        return out.toString();
    }
}
