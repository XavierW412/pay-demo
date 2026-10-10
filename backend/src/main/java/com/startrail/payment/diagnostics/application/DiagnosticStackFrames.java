package com.startrail.payment.diagnostics.application;

public final class DiagnosticStackFrames {
    private DiagnosticStackFrames() {}

    public static String summarize(Throwable exception) {
        StringBuilder summary = new StringBuilder();
        int count = 0;
        for (StackTraceElement frame : exception.getStackTrace()) {
            if (!frame.getClassName().startsWith("com.startrail.payment.")) {
                continue;
            }
            if (count > 0) {
                summary.append(';');
            }
            summary.append(frame.getClassName()).append('#').append(frame.getMethodName())
                    .append(':').append(frame.getLineNumber());
            if (++count == 3 || summary.length() >= 300) {
                break;
            }
        }
        return summary.length() > 300 ? summary.substring(0, 300) : summary.toString();
    }
}
