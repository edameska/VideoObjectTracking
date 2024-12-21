import util.Logger;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class Gui {
    private JFrame frame;
    private JComboBox<String> modeSelector;
    private JButton processButton;
    private JButton inputButton;
    private JButton outputButton;
    private JTextField inputField;
    private JTextField outputField;

    public void launch() {
        frame = new JFrame("Video Object Tracking");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(500, 300);

        JPanel panel = new JPanel();
        panel.setLayout(new GroupLayout(panel));

        JLabel inputLabel = new JLabel("Input Video Path:");
        inputField = new JTextField();
        inputField.setEditable(false); //prevent manual editing
        inputButton = new JButton("Browse");

        JLabel outputLabel = new JLabel("Output Folder:");
        outputField = new JTextField();
        outputField.setEditable(false);
        outputButton = new JButton("Browse");

        JLabel modeLabel = new JLabel("Processing Mode:");
        modeSelector = new JComboBox<>(new String[]{"Sequential", "Parallel", "Distributed"});

        processButton = new JButton("Process Video");

        //layout
        GroupLayout layout = new GroupLayout(panel);
        panel.setLayout(layout);
        layout.setAutoCreateGaps(true);
        layout.setAutoCreateContainerGaps(true);

        layout.setHorizontalGroup(
                layout.createParallelGroup(GroupLayout.Alignment.LEADING)
                        .addGroup(layout.createSequentialGroup()
                                .addGroup(layout.createParallelGroup(GroupLayout.Alignment.TRAILING)
                                        .addComponent(inputLabel)
                                        .addComponent(outputLabel)
                                        .addComponent(modeLabel))
                                .addGroup(layout.createParallelGroup(GroupLayout.Alignment.LEADING)
                                        .addGroup(layout.createSequentialGroup()
                                                .addComponent(inputField)
                                                .addComponent(inputButton))
                                        .addGroup(layout.createSequentialGroup()
                                                .addComponent(outputField)
                                                .addComponent(outputButton))
                                        .addGroup(layout.createSequentialGroup()
                                                .addComponent(modeSelector)
                                                .addComponent(processButton)))
                        )
        );

        layout.setVerticalGroup(
                layout.createSequentialGroup()
                        .addGroup(layout.createParallelGroup(GroupLayout.Alignment.BASELINE)
                                .addComponent(inputLabel)
                                .addComponent(inputField)
                                .addComponent(inputButton))
                        .addGroup(layout.createParallelGroup(GroupLayout.Alignment.BASELINE)
                                .addComponent(outputLabel)
                                .addComponent(outputField)
                                .addComponent(outputButton))
                        .addGroup(layout.createParallelGroup(GroupLayout.Alignment.BASELINE)
                                .addComponent(modeLabel)
                                .addComponent(modeSelector)
                                .addComponent(processButton))
        );

        // Set panel in the frame and display the frame
        frame.add(panel);
        frame.setVisible(true);

        // Add action listeners
        inputButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                chooseFile(inputField);
            }
        });

        outputButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                chooseFolder(outputField);
            }
        });

        processButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String inputPath = inputField.getText();
                String outputPath = outputField.getText();
                String mode = (String) modeSelector.getSelectedItem();
                handleProcessing(inputPath, outputPath, mode);
            }
        });
    }

    private void chooseFile(JTextField field) {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.FILES_ONLY); // Only allow file selection
        int result = chooser.showOpenDialog(frame);
        if (result == JFileChooser.APPROVE_OPTION) {
            field.setText(chooser.getSelectedFile().getAbsolutePath());
        }
    }

    private void chooseFolder(JTextField field) {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY); // Only allow directory selection
        int result = chooser.showOpenDialog(frame);
        if (result == JFileChooser.APPROVE_OPTION) {
            field.setText(chooser.getSelectedFile().getAbsolutePath());
        }
    }

    private void handleProcessing(String inputPath, String outputPath, String mode) {
        Logger.log("Handling processing");
    }
}