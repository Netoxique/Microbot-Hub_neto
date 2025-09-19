package net.runelite.client.plugins.microbot.banksbankstander;

import net.runelite.client.plugins.microbot.util.inventory.InteractOrder;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;

import javax.swing.BorderFactory;
import javax.swing.Box;
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
import javax.swing.border.TitledBorder;
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

    private final JComboBox<InteractOrder> interactOrderCombo;
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

        interactOrderCombo = new JComboBox<>(InteractOrder.values());
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
        promptCheckbox = createCheckbox("Needs prompt entry");
        waitForProcessCheckbox = createCheckbox("Wait for process");
        depositAllCheckbox = createCheckbox("Deposit all");
        amuletCheckbox = createCheckbox("Wear amulet of chemistry");

        interactionOptionField = new JTextField();
        interactionOptionField.setColumns(12);

        sleepMinSpinner = new JSpinner(new SpinnerNumberModel(0, 0, 30000, 1));
        sleepMaxSpinner = new JSpinner(new SpinnerNumberModel(0, 0, 30000, 1));
        sleepTargetSpinner = new JSpinner(new SpinnerNumberModel(0, 0, 30000, 1));

        add(buildStateSelectionPanel(), BorderLayout.NORTH);
        add(buildContentPanel(), BorderLayout.CENTER);

        initializeListeners();
        loadInitialState();
    }

    private void initializeListeners()
    {
        stateDropdown.addActionListener(e -> onStateSelected());
        saveButton.addActionListener(e -> onSave());
        deleteButton.addActionListener(e -> onDelete());
    }

    private Component buildStateSelectionPanel()
    {
        JPanel container = new JPanel(new BorderLayout(0, 5));
        container.setOpaque(false);

        JLabel label = new JLabel("Saved states");
        label.setForeground(Color.WHITE);
        container.add(label, BorderLayout.NORTH);

        container.add(stateDropdown, BorderLayout.CENTER);

        JPanel buttonRow = new JPanel(new GridLayout(1, 2, 5, 0));
        buttonRow.setOpaque(false);
        buttonRow.add(saveButton);
        buttonRow.add(deleteButton);
        container.add(buttonRow, BorderLayout.SOUTH);

        return container;
    }

    private Component buildContentPanel()
    {
        JPanel content = new JPanel(new GridBagLayout());
        content.setOpaque(false);

        int row = 0;

        JPanel itemPanel = createSectionPanel("Item Settings");
        addFormRow(itemPanel, 0, "Interact Order", interactOrderCombo);
        addFormRow(itemPanel, 1, "First Item", firstItemField);
        addFormRow(itemPanel, 2, "First Item Quantity", firstItemQuantitySpinner);
        addFormRow(itemPanel, 3, "Second Item", secondItemField);
        addFormRow(itemPanel, 4, "Second Item Quantity", secondItemQuantitySpinner);
        addFormRow(itemPanel, 5, "Third Item", thirdItemField);
        addFormRow(itemPanel, 6, "Third Item Quantity", thirdItemQuantitySpinner);
        addFormRow(itemPanel, 7, "Fourth Item", fourthItemField);
        addFormRow(itemPanel, 8, "Fourth Item Quantity", fourthItemQuantitySpinner);
        addSection(content, row++, itemPanel);

        JPanel togglePanel = createSectionPanel("Toggles");
        addCheckboxRow(togglePanel, 0, pauseCheckbox);
        addCheckboxRow(togglePanel, 1, promptCheckbox);
        addCheckboxRow(togglePanel, 2, waitForProcessCheckbox);
        addCheckboxRow(togglePanel, 3, depositAllCheckbox);
        addCheckboxRow(togglePanel, 4, amuletCheckbox);
        addSection(content, row++, togglePanel);

        JPanel interactionPanel = createSectionPanel("Interaction Menu");
        addFormRow(interactionPanel, 0, "Interaction Option", interactionOptionField);
        addSection(content, row++, interactionPanel);

        JPanel sleepPanel = createSectionPanel("Sleep Settings");
        addFormRow(sleepPanel, 0, "Sleep Min", sleepMinSpinner);
        addFormRow(sleepPanel, 1, "Sleep Max", sleepMaxSpinner);
        addFormRow(sleepPanel, 2, "Sleep Target", sleepTargetSpinner);
        addSection(content, row++, sleepPanel);

        GridBagConstraints filler = new GridBagConstraints();
        filler.gridx = 0;
        filler.gridy = row;
        filler.weightx = 1;
        filler.weighty = 1;
        filler.fill = GridBagConstraints.BOTH;
        content.add(Box.createVerticalGlue(), filler);

        JScrollPane scrollPane = new JScrollPane(content);
        scrollPane.setBorder(null);
        scrollPane.getViewport().setBackground(ColorScheme.DARK_GRAY_COLOR);
        return scrollPane;
    }

    private JPanel createSectionPanel(String title)
    {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setOpaque(true);
        panel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        TitledBorder border = BorderFactory.createTitledBorder(BorderFactory.createLineBorder(ColorScheme.DARK_GRAY_COLOR), title);
        border.setTitleColor(Color.WHITE);
        panel.setBorder(border);
        return panel;
    }

    private void addSection(JPanel container, int row, JComponent section)
    {
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.weightx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(5, 0, 5, 0);
        container.add(section, gbc);
    }

    private void addFormRow(JPanel panel, int row, String labelText, JComponent component)
    {
        int baseRow = row * 2;

        GridBagConstraints labelConstraints = new GridBagConstraints();
        labelConstraints.gridx = 0;
        labelConstraints.gridy = baseRow;
        labelConstraints.gridwidth = 2;
        labelConstraints.anchor = GridBagConstraints.WEST;
        labelConstraints.insets = new Insets(5, 10, 0, 10);
        JLabel label = new JLabel(labelText);
        label.setForeground(Color.WHITE);
        panel.add(label, labelConstraints);

        GridBagConstraints componentConstraints = new GridBagConstraints();
        componentConstraints.gridx = 0;
        componentConstraints.gridy = baseRow + 1;
        componentConstraints.gridwidth = 2;
        componentConstraints.weightx = 1;
        componentConstraints.fill = GridBagConstraints.HORIZONTAL;
        componentConstraints.insets = new Insets(0, 10, 5, 10);
        panel.add(component, componentConstraints);
    }

    private void addCheckboxRow(JPanel panel, int row, JCheckBox checkbox)
    {
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.gridwidth = 2;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.insets = new Insets(5, 10, 5, 10);
        panel.add(checkbox, gbc);
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

    private BanksBankStanderState buildStateFromFields(String name)
    {
        BanksBankStanderState state = new BanksBankStanderState();
        state.setName(name);
        state.setInteractOrder((InteractOrder) Objects.requireNonNullElse(interactOrderCombo.getSelectedItem(), InteractOrder.STANDARD));
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

        interactOrderCombo.setSelectedItem(state.getInteractOrder() != null ? state.getInteractOrder() : InteractOrder.STANDARD);
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

    private static String nonNull(String value)
    {
        return value == null ? "" : value;
    }
}
