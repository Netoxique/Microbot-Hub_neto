package net.runelite.client.plugins.microbot.banksbankstander;

import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.border.EmptyBorder;
import javax.swing.text.JTextComponent;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.util.Collection;
import java.util.Objects;

public class BanksBankStanderPanel extends PluginPanel
{
    private final BanksBankStanderPlugin plugin;

    private final JComboBox<String> stateDropdown;
    private final JTextComponent stateDropdownEditor;
    private final JButton saveButton;
    private final JButton deleteButton;
    private final JButton startStopButton;

    private final JComboBox<BanksInteractOrder> interactOrderCombo;
    private final JTextField firstItemField;
    private final JSpinner firstItemQuantitySpinner;
    private final JTextField secondItemField;
    private final JSpinner secondItemQuantitySpinner;
    private final JTextField thirdItemField;
    private final JSpinner thirdItemQuantitySpinner;
    private final JTextField fourthItemField;
    private final JSpinner fourthItemQuantitySpinner;

    private final JCheckBox pauseCheckbox;
    private final JCheckBox promptCheckbox;
    private final JCheckBox waitForProcessCheckbox;
    private final JCheckBox depositAllCheckbox;
    private final JCheckBox amuletCheckbox;

    private final JTextField interactionOptionField;

    private final JSpinner sleepMinSpinner;
    private final JSpinner sleepMaxSpinner;
    private final JSpinner sleepTargetSpinner;

    private boolean updatingDropdown;

    public BanksBankStanderPanel(BanksBankStanderPlugin plugin)
    {
        this.plugin = plugin;

        setLayout(new BorderLayout(0, 10));
        setBackground(ColorScheme.DARK_GRAY_COLOR);
        setBorder(new EmptyBorder(10, 10, 10, 10));

        stateDropdown = new JComboBox<>();
        stateDropdown.setEditable(true);
        stateDropdownEditor = (JTextComponent) stateDropdown.getEditor().getEditorComponent();
        stateDropdownEditor.setPreferredSize(new Dimension(0, 26));

        saveButton = new JButton("Save");
        configurePrimaryButton(saveButton);

        deleteButton = new JButton("Delete");
        configureDangerButton(deleteButton);

        interactOrderCombo = new JComboBox<>(BanksInteractOrder.values());
        firstItemField = new JTextField();
        firstItemField.setColumns(12);
        firstItemQuantitySpinner = new JSpinner(new SpinnerNumberModel(1, 0, 28, 1));
        secondItemField = new JTextField();
        secondItemField.setColumns(12);
        secondItemQuantitySpinner = new JSpinner(new SpinnerNumberModel(0, 0, 27, 1));
        thirdItemField = new JTextField();
        thirdItemField.setColumns(12);
        thirdItemQuantitySpinner = new JSpinner(new SpinnerNumberModel(0, 0, 27, 1));
        fourthItemField = new JTextField();
        fourthItemField.setColumns(12);
        fourthItemQuantitySpinner = new JSpinner(new SpinnerNumberModel(0, 0, 27, 1));

        pauseCheckbox = createCheckbox("Pause between states");
        promptCheckbox = createCheckbox("Prompt?");
        waitForProcessCheckbox = createCheckbox("Wait for process?");
        depositAllCheckbox = createCheckbox("Deposit all");
        amuletCheckbox = createCheckbox("Wear amulet of chemistry");

        interactionOptionField = new JTextField();
        interactionOptionField.setColumns(12);

        sleepMinSpinner = new JSpinner(new SpinnerNumberModel(0, 0, 30000, 1));
        sleepMaxSpinner = new JSpinner(new SpinnerNumberModel(0, 0, 30000, 1));
        sleepTargetSpinner = new JSpinner(new SpinnerNumberModel(0, 0, 30000, 1));

        startStopButton = new JButton();
        configureStartStopButton(startStopButton);

        add(buildStateSelectionPanel(), BorderLayout.CENTER);

        initializeListeners();
        loadInitialState();
        updateStartStopButton();
    }

    private void initializeListeners()
    {
        stateDropdown.addActionListener(e -> onStateSelected());
        saveButton.addActionListener(e -> onSave());
        deleteButton.addActionListener(e -> onDelete());
        startStopButton.addActionListener(e -> onStartStop());
    }

    private Component buildStateSelectionPanel()
    {
        JPanel container = new JPanel(new BorderLayout(0, 10));
        container.setOpaque(false);

        JPanel stateControls = new JPanel(new BorderLayout(0, 5));
        stateControls.setOpaque(false);

        JLabel savedStatesLabel = new JLabel("Saved states");
        savedStatesLabel.setForeground(Color.WHITE);
        stateControls.add(savedStatesLabel, BorderLayout.NORTH);

        stateControls.add(stateDropdown, BorderLayout.CENTER);

        JPanel buttonRow = new JPanel(new GridLayout(1, 2, 5, 0));
        buttonRow.setOpaque(false);
        buttonRow.add(saveButton);
        buttonRow.add(deleteButton);

        JPanel bottomButtons = new JPanel();
        bottomButtons.setOpaque(false);
        bottomButtons.setLayout(new BoxLayout(bottomButtons, BoxLayout.Y_AXIS));
        bottomButtons.add(buttonRow);
        bottomButtons.add(Box.createVerticalStrut(5));
        bottomButtons.add(startStopButton);

        stateControls.add(bottomButtons, BorderLayout.SOUTH);

        container.add(stateControls, BorderLayout.NORTH);

        JPanel optionsPanel = new JPanel(new GridBagLayout());
        optionsPanel.setOpaque(false);

        int row = 0;

        row = addSectionLabel(optionsPanel, row, "Item settings");
        row = addFormRow(optionsPanel, row, "Interact Order", interactOrderCombo);
        row = addFormRow(optionsPanel, row, "First Item", firstItemField);
        row = addFormRow(optionsPanel, row, "First Item Quantity", firstItemQuantitySpinner);
        row = addFormRow(optionsPanel, row, "Second Item", secondItemField);
        row = addFormRow(optionsPanel, row, "Second Item Quantity", secondItemQuantitySpinner);
        row = addFormRow(optionsPanel, row, "Third Item", thirdItemField);
        row = addFormRow(optionsPanel, row, "Third Item Quantity", thirdItemQuantitySpinner);
        row = addFormRow(optionsPanel, row, "Fourth Item", fourthItemField);
        row = addFormRow(optionsPanel, row, "Fourth Item Quantity", fourthItemQuantitySpinner);

        row = addSectionLabel(optionsPanel, row, "Toggles");
        row = addCheckboxRow(optionsPanel, row, pauseCheckbox);
        row = addCheckboxRow(optionsPanel, row, promptCheckbox);
        row = addCheckboxRow(optionsPanel, row, waitForProcessCheckbox);
        row = addCheckboxRow(optionsPanel, row, depositAllCheckbox);
        row = addCheckboxRow(optionsPanel, row, amuletCheckbox);

        row = addSectionLabel(optionsPanel, row, "Interaction Menu");
        row = addFormRow(optionsPanel, row, "Interaction Option", interactionOptionField);

        row = addSectionLabel(optionsPanel, row, "Sleep Settings");
        row = addFormRow(optionsPanel, row, "Sleep Min", sleepMinSpinner);
        row = addFormRow(optionsPanel, row, "Sleep Max", sleepMaxSpinner);
        row = addFormRow(optionsPanel, row, "Sleep Target", sleepTargetSpinner);

        GridBagConstraints filler = new GridBagConstraints();
        filler.gridx = 0;
        filler.gridy = row;
        filler.weightx = 1;
        filler.weighty = 1;
        filler.fill = GridBagConstraints.BOTH;
        optionsPanel.add(Box.createVerticalGlue(), filler);

        JScrollPane scrollPane = new JScrollPane(optionsPanel);
        scrollPane.setBorder(null);
        scrollPane.getViewport().setBackground(ColorScheme.DARK_GRAY_COLOR);

        container.add(scrollPane, BorderLayout.CENTER);

        return container;
    }

    private int addSectionLabel(JPanel panel, int row, String text)
    {
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = row++;
        gbc.gridwidth = 2;
        gbc.anchor = GridBagConstraints.WEST;
        int topInset = row == 1 ? 0 : 10;
        gbc.insets = new Insets(topInset, 10, 5, 10);
        JLabel label = new JLabel(text);
        label.setFont(FontManager.getRunescapeBoldFont());
        label.setForeground(Color.WHITE);
        panel.add(label, gbc);
        return row;
    }

    private int addFormRow(JPanel panel, int row, String labelText, JComponent component)
    {
        GridBagConstraints labelConstraints = new GridBagConstraints();
        labelConstraints.gridx = 0;
        labelConstraints.gridy = row++;
        labelConstraints.gridwidth = 2;
        labelConstraints.anchor = GridBagConstraints.WEST;
        labelConstraints.insets = new Insets(0, 10, 0, 10);
        JLabel label = new JLabel(labelText);
        label.setForeground(Color.WHITE);
        panel.add(label, labelConstraints);

        GridBagConstraints componentConstraints = new GridBagConstraints();
        componentConstraints.gridx = 0;
        componentConstraints.gridy = row++;
        componentConstraints.gridwidth = 2;
        componentConstraints.weightx = 1;
        componentConstraints.fill = GridBagConstraints.HORIZONTAL;
        componentConstraints.insets = new Insets(0, 10, 5, 10);
        panel.add(component, componentConstraints);

        return row;
    }

    private int addCheckboxRow(JPanel panel, int row, JCheckBox checkbox)
    {
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = row++;
        gbc.gridwidth = 2;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.insets = new Insets(0, 10, 5, 10);
        panel.add(checkbox, gbc);
        return row;
    }

    private void loadInitialState()
    {
        populateStateDropdown();
        String selected = plugin.getLastSelectedState();
        if (selected != null && plugin.getSavedState(selected) != null)
        {
            setDropdownSelection(selected);
            loadStateIntoFields(plugin.getSavedState(selected));
        }
        else
        {
            setDropdownSelection("");
            loadStateIntoFields(plugin.getCurrentConfigState());
        }
    }

    private void populateStateDropdown()
    {
        updatingDropdown = true;
        stateDropdown.removeAllItems();
        Collection<String> names = plugin.getSavedStateNames();
        for (String name : names)
        {
            stateDropdown.addItem(name);
        }
        updatingDropdown = false;
    }

    private void onStateSelected()
    {
        if (updatingDropdown)
        {
            return;
        }

        Object selection = stateDropdown.getSelectedItem();
        if (selection == null)
        {
            return;
        }

        String name = selection.toString().trim();
        if (name.isEmpty())
        {
            return;
        }

        BanksBankStanderState state = plugin.getSavedState(name);
        if (state != null)
        {
            setDropdownSelection(name);
            loadStateIntoFields(state);
            plugin.setLastSelectedState(name);
            plugin.applyState(state, true);
        }
    }

    private void onSave()
    {
        String name = stateDropdownEditor.getText().trim();
        if (name.isEmpty())
        {
            JOptionPane.showMessageDialog(this, "Enter a name for the state before saving.", "Missing name", JOptionPane.WARNING_MESSAGE);
            return;
        }

        BanksBankStanderState state = buildStateFromFields(name);
        plugin.saveState(name, state);

        populateStateDropdown();
        setDropdownSelection(name);

        BanksBankStanderState saved = plugin.getSavedState(name);
        if (saved != null)
        {
            loadStateIntoFields(saved);
        }
    }

    private void onDelete()
    {
        Object selection = stateDropdown.getSelectedItem();
        String name = selection != null ? selection.toString().trim() : "";
        if (name.isEmpty())
        {
            JOptionPane.showMessageDialog(this, "Select a saved state to delete.", "No state selected", JOptionPane.WARNING_MESSAGE);
            return;
        }

        if (plugin.getSavedState(name) == null)
        {
            JOptionPane.showMessageDialog(this, "The selected state does not exist.", "Unknown state", JOptionPane.WARNING_MESSAGE);
            return;
        }

        plugin.deleteState(name);
        populateStateDropdown();

        String nextSelection = plugin.getLastSelectedState();
        if (nextSelection != null && plugin.getSavedState(nextSelection) != null)
        {
            setDropdownSelection(nextSelection);
            loadStateIntoFields(plugin.getSavedState(nextSelection));
        }
        else
        {
            setDropdownSelection("");
            loadStateIntoFields(plugin.getCurrentConfigState());
        }
    }

    private void onStartStop()
    {
        if (plugin.isScriptRunning())
        {
            plugin.stopScript();
        }
        else
        {
            plugin.startScript();
        }

        updateStartStopButton();
    }

    private BanksBankStanderState buildStateFromFields(String name)
    {
        BanksBankStanderState state = new BanksBankStanderState();
        state.setName(name);
        state.setInteractOrder((BanksInteractOrder) Objects.requireNonNullElse(interactOrderCombo.getSelectedItem(), BanksInteractOrder.STANDARD));
        state.setFirstItemIdentifier(firstItemField.getText().trim());
        state.setFirstItemQuantity(((Number) firstItemQuantitySpinner.getValue()).intValue());
        state.setSecondItemIdentifier(secondItemField.getText().trim());
        state.setSecondItemQuantity(((Number) secondItemQuantitySpinner.getValue()).intValue());
        state.setThirdItemIdentifier(thirdItemField.getText().trim());
        state.setThirdItemQuantity(((Number) thirdItemQuantitySpinner.getValue()).intValue());
        state.setFourthItemIdentifier(fourthItemField.getText().trim());
        state.setFourthItemQuantity(((Number) fourthItemQuantitySpinner.getValue()).intValue());
        state.setPause(pauseCheckbox.isSelected());
        state.setNeedPromptEntry(promptCheckbox.isSelected());
        state.setWaitForAnimation(waitForProcessCheckbox.isSelected());
        state.setDepositAll(depositAllCheckbox.isSelected());
        state.setAmuletOfChemistry(amuletCheckbox.isSelected());
        state.setInteractionOption(interactionOptionField.getText().trim());
        state.setSleepMin(((Number) sleepMinSpinner.getValue()).intValue());
        state.setSleepMax(((Number) sleepMaxSpinner.getValue()).intValue());
        state.setSleepTarget(((Number) sleepTargetSpinner.getValue()).intValue());
        return state;
    }

    private void loadStateIntoFields(BanksBankStanderState state)
    {
        if (state == null)
        {
            return;
        }

        interactOrderCombo.setSelectedItem(state.getInteractOrder() != null ? state.getInteractOrder() : BanksInteractOrder.STANDARD);
        firstItemField.setText(nonNull(state.getFirstItemIdentifier()));
        firstItemQuantitySpinner.setValue(state.getFirstItemQuantity());
        secondItemField.setText(nonNull(state.getSecondItemIdentifier()));
        secondItemQuantitySpinner.setValue(state.getSecondItemQuantity());
        thirdItemField.setText(nonNull(state.getThirdItemIdentifier()));
        thirdItemQuantitySpinner.setValue(state.getThirdItemQuantity());
        fourthItemField.setText(nonNull(state.getFourthItemIdentifier()));
        fourthItemQuantitySpinner.setValue(state.getFourthItemQuantity());
        pauseCheckbox.setSelected(state.isPause());
        promptCheckbox.setSelected(state.isNeedPromptEntry());
        waitForProcessCheckbox.setSelected(state.isWaitForAnimation());
        depositAllCheckbox.setSelected(state.isDepositAll());
        amuletCheckbox.setSelected(state.isAmuletOfChemistry());
        interactionOptionField.setText(nonNull(state.getInteractionOption()));
        sleepMinSpinner.setValue(state.getSleepMin());
        sleepMaxSpinner.setValue(state.getSleepMax());
        sleepTargetSpinner.setValue(state.getSleepTarget());
    }

    private void setDropdownSelection(String value)
    {
        updatingDropdown = true;
        if (value == null || value.isEmpty())
        {
            stateDropdown.setSelectedItem(null);
            stateDropdownEditor.setText("");
        }
        else
        {
            stateDropdown.setSelectedItem(value);
            stateDropdownEditor.setText(value);
        }
        updatingDropdown = false;
    }

    private JCheckBox createCheckbox(String text)
    {
        JCheckBox checkBox = new JCheckBox(text);
        checkBox.setForeground(Color.WHITE);
        checkBox.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        return checkBox;
    }

    private void configurePrimaryButton(JButton button)
    {
        button.setFont(FontManager.getRunescapeBoldFont());
        button.setBackground(ColorScheme.BRAND_ORANGE);
        button.setForeground(Color.WHITE);
        button.setFocusPainted(false);
    }

    private void configureDangerButton(JButton button)
    {
        button.setFont(FontManager.getRunescapeBoldFont());
        button.setBackground(new Color(200, 55, 55));
        button.setForeground(Color.WHITE);
        button.setFocusPainted(false);
    }

    private void configureStartStopButton(JButton button)
    {
        button.setFont(FontManager.getRunescapeBoldFont());
        button.setFocusPainted(false);
        button.setForeground(Color.WHITE);
        button.setOpaque(true);
        button.setAlignmentX(Component.CENTER_ALIGNMENT);
    }

    public void updateStartStopButton()
    {
        boolean running = plugin.isScriptRunning();
        if (running)
        {
            startStopButton.setText("Stop");
            startStopButton.setBackground(new Color(200, 55, 55));
        }
        else
        {
            startStopButton.setText("Start");
            startStopButton.setBackground(new Color(46, 204, 113));
        }

        startStopButton.setMaximumSize(new Dimension(Integer.MAX_VALUE, startStopButton.getPreferredSize().height));
    }

    private static String nonNull(String value)
    {
        return value == null ? "" : value;
    }
}
