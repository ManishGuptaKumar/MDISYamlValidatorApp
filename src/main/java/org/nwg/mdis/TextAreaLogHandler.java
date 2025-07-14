package org.nwg.mdis;

import javax.swing.*;
import java.util.logging.*;

public class TextAreaLogHandler extends Handler {
    private final JTextArea textArea;

    public TextAreaLogHandler(JTextArea textArea) {
        this.textArea = textArea;
        setFormatter(new MDISLogger.SingleLineFormatter()); // Simple text formatter for logs
    }

    @Override
    public void publish(LogRecord record) {
        if (!isLoggable(record)) return;
        String msg = getFormatter().format(record);
        // Append message to textArea on EDT thread safely
        SwingUtilities.invokeLater(() -> {
            textArea.append(msg);
            // Optional: auto-scroll to bottom
            textArea.setCaretPosition(textArea.getDocument().getLength());
        });
    }

    @Override
    public void flush() {
        // Not needed for JTextArea
    }

    @Override
    public void close() throws SecurityException {
        // Not needed
    }
}
