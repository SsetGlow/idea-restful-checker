package com.ssetglow.restfulchecker.settings;

import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextArea;
import com.ssetglow.restfulchecker.util.KeyValueParser;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.JComponent;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.util.Objects;

public final class RestCheckerGlobalConfigurable implements SearchableConfigurable {
    private JPanel panel;
    private JBTextArea variablesArea;
    private JBTextArea headersArea;

    @Override
    public @NotNull String getId() {
        return "com.ssetglow.idea-restful-checker.global-settings";
    }

    @Override
    public @Nls String getDisplayName() {
        return "idea-restful-checker";
    }

    @Override
    public @Nullable JComponent createComponent() {
        variablesArea = area(7);
        headersArea = area(6);

        JPanel form = new JPanel(new GridBagLayout());
        addRow(form, 0, "Global variables", variablesArea);
        addRow(form, 1, "Global HTTP headers", headersArea);

        panel = new JPanel(new BorderLayout());
        panel.add(form, BorderLayout.NORTH);
        reset();
        return panel;
    }

    @Override
    public boolean isModified() {
        RestCheckerGlobalSettings settings = RestCheckerGlobalSettings.getInstance();
        return !Objects.equals(KeyValueParser.parseLines(variablesArea.getText()), settings.getVariables())
                || !Objects.equals(headersArea.getText(), settings.getDefaultHeaders());
    }

    @Override
    public void apply() {
        RestCheckerGlobalSettings settings = RestCheckerGlobalSettings.getInstance();
        settings.setVariables(KeyValueParser.parseLines(variablesArea.getText()));
        settings.setDefaultHeaders(headersArea.getText());
    }

    @Override
    public void reset() {
        if (variablesArea == null) {
            return;
        }
        RestCheckerGlobalSettings settings = RestCheckerGlobalSettings.getInstance();
        variablesArea.setText(KeyValueParser.formatLines(settings.getVariables()));
        headersArea.setText(settings.getDefaultHeaders());
    }

    @Override
    public void disposeUIResources() {
        panel = null;
        variablesArea = null;
        headersArea = null;
    }

    private static JBTextArea area(int rows) {
        JBTextArea area = new JBTextArea(rows, 64);
        area.setLineWrap(false);
        return area;
    }

    private static void addRow(JPanel form, int row, String label, JBTextArea area) {
        GridBagConstraints labelConstraints = new GridBagConstraints();
        labelConstraints.gridx = 0;
        labelConstraints.gridy = row;
        labelConstraints.anchor = GridBagConstraints.NORTHWEST;
        labelConstraints.insets.set(6, 0, 4, 12);
        form.add(new JBLabel(label), labelConstraints);

        GridBagConstraints fieldConstraints = new GridBagConstraints();
        fieldConstraints.gridx = 1;
        fieldConstraints.gridy = row;
        fieldConstraints.weightx = 1;
        fieldConstraints.fill = GridBagConstraints.HORIZONTAL;
        fieldConstraints.insets.set(6, 0, 4, 0);
        form.add(new JBScrollPane(area), fieldConstraints);
    }
}
