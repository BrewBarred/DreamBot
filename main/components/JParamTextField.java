package main.components;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

/**
 * Extends {@link JTextField} to keep a synced {@code param} value.
 */
public class JParamTextField extends JTextField {
    private String param = "";

    public JParamTextField() {
        super();
        addInputListener();
    }

    public JParamTextField(String defaultParam) {
        super(defaultParam);
        this.param = defaultParam;
        addInputListener();
    }

    private void addInputListener() {
        this.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e)  { syncParam(); }
            public void removeUpdate(DocumentEvent e)  { syncParam(); }
            public void changedUpdate(DocumentEvent e) { syncParam(); }
        });

        //this.addActionListener(e -> syncParam());
    }

    private void syncParam() {
        param = super.getText();
    }

    public void setParam(String value) {
        param = value;
        super.setText(value);
    }

    public String getParam() {
        return param;
    }

    @Override @Deprecated
    public void setText(String text) {
        setParam(text);
    }

    @Override @Deprecated
    public String getText() {
        return getParam();
    }
}