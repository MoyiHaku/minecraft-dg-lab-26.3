package cn.dancingsnow.dglab.status;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** The four values reported by the app, in the wire order used by upstream 1.2.7. */
record Strength(int a, int b, int aMax, int bMax) {
    private static final Pattern REPORT =
            Pattern.compile("strength-([0-9]{1,3})\\+([0-9]{1,3})\\+([0-9]{1,3})\\+([0-9]{1,3})");

    static Strength parse(String report) {
        Matcher m = REPORT.matcher(report);
        if (!m.matches()) return null;
        int[] fields = new int[4];
        for (int i = 0; i < fields.length; i++) {
            fields[i] = Integer.parseInt(m.group(i + 1));
            if (fields[i] > 200) return null;
        }
        return new Strength(fields[0], fields[1], fields[2], fields[3]);
    }
}
