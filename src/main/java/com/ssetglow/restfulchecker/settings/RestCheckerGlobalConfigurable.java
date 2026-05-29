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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public final class RestCheckerGlobalConfigurable implements SearchableConfigurable {
    private JPanel panel;
    private JBTextArea hostsArea;
    private JBTextArea variablesArea;
    private JBTextArea headersArea;

    @Override
    public @NotNull String getId() {
        return "com.ssetglow.restful-checker.global-settings";
    }

    @Override
    public @Nls String getDisplayName() {
        return "restful-checker";
    }

    @Override
    public @Nullable JComponent createComponent() {
        hostsArea = area(4);
        variablesArea = area(7);
        headersArea = area(6);

        JPanel form = new JPanel(new GridBagLayout());
        addRow(form, 0, "Global hosts", hostsArea);
        addRow(form, 1, "Global variables", variablesArea);
        addRow(form, 2, "Global HTTP headers", headersArea);

        panel = new JPanel(new BorderLayout());
        panel.add(form, BorderLayout.NORTH);
        reset();
        return panel;
    }

    @Override
    public boolean isModified() {
        RestCheckerGlobalSettings settings = RestCheckerGlobalSettings.getInstance();
        return !Objects.equals(parseHosts(), settings.getHosts())
                || !Objects.equals(KeyValueParser.parseLines(variablesArea.getText()), settings.getVariables())
                || !Objects.equals(headersArea.getText(), settings.getDefaultHeaders());
    }

    @Override
    public void apply() {
        RestCheckerGlobalSettings settings = RestCheckerGlobalSettings.getInstance();
        settings.setHosts(parseHosts());
        settings.setVariables(KeyValueParser.parseLines(variablesArea.getText()));
        settings.setDefaultHeaders(headersArea.getText());
    }

    @Override
    public void reset() {
        if (hostsArea == null) {
            return;
        }
        RestCheckerGlobalSettings settings = RestCheckerGlobalSettings.getInstance();
        hostsArea.setText(String.join("\n", settings.getHosts()));
        variablesArea.setText(KeyValueParser.formatLines(settings.getVariables()));
        headersArea.setText(settings.getDefaultHeaders());
    }

    @Override
    public void disposeUIResources() {
        panel = null;
        hostsArea = null;
        variablesArea = null;
        headersArea = null;
    }

    private List<String> parseHosts() {
        if (hostsArea == null || hostsArea.getText().isBlank()) {
            return new ArrayList<>();
        }
        return Arrays.stream(hostsArea.getText().split("\\R"))
                .map(String::trim)
                .filter(line -> !line.isBlank() && !line.startsWith("#"))
                .distinct()
                .collect(Collectors.toCollection(ArrayList::new));
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
