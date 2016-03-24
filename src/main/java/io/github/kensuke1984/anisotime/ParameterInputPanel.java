package io.github.kensuke1984.anisotime;

import java.awt.Point;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Timer;
import java.util.TimerTask;

import javax.swing.BorderFactory;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import javax.swing.filechooser.FileNameExtensionFilter;

/**
 * Panel for inputting parameters
 * 
 * @version 0.2.1.2
 * @author Kensuke Konishi
 */
class ParameterInputPanel extends javax.swing.JPanel {

	/**
	 * if current parameter set is computed.
	 */
	private boolean isCurrentComputed;

	synchronized boolean isCurrentComputed() {
		return isCurrentComputed;
	}

	synchronized void computed(boolean b) {
		isCurrentComputed = b;
	}

	synchronized private void parameterChanged() {
		isCurrentComputed = false;
	}

	void setMode(ComputationMode mode) {
		switch (mode) {
		case EPICENTRAL_DISTANCE:
			jLabelMostImportant.setText("Epicentral Distance [deg]:");
			jTextFieldMostImportant.setText("60.0");
			break;
		case RAYPARAMETER:
			jLabelMostImportant.setText("Ray parameter :");
			jTextFieldMostImportant.setText("680.0");
			break;
		case TURNING_DEPTH:
			jLabelMostImportant.setText("Turning depth [km]:");
			jTextFieldMostImportant.setText("1000.0");
			break;
		case DIFFRACTION:
			jLabelMostImportant.setText("Angle on CMB [deg]:");
			jTextFieldMostImportant.setText("10.0");
			break;
		}

	}

	private static final long serialVersionUID = -1000972001047033575L;

	/**
	 * Creates new form ParameterInputPanel
	 */
	public ParameterInputPanel() {
		initComponents();
	}

	void changePropertiesVisible() {
		boolean b = !jLabelInterval.isVisible();
		jLabelInterval.setVisible(b);
		jLabelTurningRegionR.setVisible(b);
		jTextFieldInterval.setVisible(b);
		jTextFieldTurningRegionR.setVisible(b);
		((JFrame) SwingUtilities.getRoot(this)).pack();
	}

	void changeBorderTitle(String title) {
		setBorder(BorderFactory.createTitledBorder(title));
	}

	/**
	 * This method is called from within the constructor to initialize the form.
	 * WARNING: Do NOT modify this code. The content of this method is always
	 * regenerated by the Form Editor.
	 */
	private void initComponents() {
		jLabelMostImportant = new javax.swing.JLabel("Epicentral Distance [deg]:");
		jLabelModel = new javax.swing.JLabel("Model:");
		jLabelDepth = new javax.swing.JLabel("Depth [km]:");
		jLabelInterval = new javax.swing.JLabel("Integration interval [km]:");
		jLabelTurningRegionR = new javax.swing.JLabel("allowable error of turning depth [km]:");

		jTextFieldMostImportant = GUIInputComponents.createPositiveNumberField("60.0");
		jTextFieldDepth = GUIInputComponents.createPositiveNumberField("100.0");
		jTextFieldInterval = GUIInputComponents.createPositiveNumberField("1.0");
		jTextFieldTurningRegionR = GUIInputComponents.createPositiveNumberField("10.0");

		jLabelInterval.setVisible(false);
		jLabelTurningRegionR.setVisible(false);
		jTextFieldInterval.setVisible(false);
		jTextFieldTurningRegionR.setVisible(false);

		changeBorderTitle("Mode:Epicentral Distance   Polarity:P-SV");

		String[] modelTitles = Arrays.stream(InputModel.values()).map(model -> model.name).toArray(String[]::new);
		jComboBoxModel = new JComboBox<>(modelTitles);

		addListners();

		javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
		setLayout(layout);
		layout.setHorizontalGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)

				.addGroup(layout.createSequentialGroup().addComponent(jLabelMostImportant)
						.addComponent(jTextFieldMostImportant))
				.addGroup(layout.createSequentialGroup().addComponent(jLabelModel).addComponent(jComboBoxModel))
				.addGroup(layout.createSequentialGroup().addComponent(jLabelDepth).addComponent(jTextFieldDepth,
						javax.swing.GroupLayout.PREFERRED_SIZE, 80, javax.swing.GroupLayout.PREFERRED_SIZE))
				.addGroup(layout.createSequentialGroup().addComponent(jLabelInterval).addComponent(jTextFieldInterval,
						javax.swing.GroupLayout.PREFERRED_SIZE, 70, javax.swing.GroupLayout.PREFERRED_SIZE))
				.addGroup(layout.createSequentialGroup().addComponent(jLabelTurningRegionR)
						.addComponent(jTextFieldTurningRegionR)));

		layout.setVerticalGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
				.addGroup(layout.createSequentialGroup()
						.addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
								.addComponent(jLabelMostImportant).addComponent(jTextFieldMostImportant))
				.addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
						.addComponent(jComboBoxModel).addComponent(jLabelModel))
				.addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
						.addComponent(jTextFieldDepth).addComponent(jLabelDepth))
				.addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
						.addComponent(jLabelInterval).addComponent(jTextFieldInterval))
				.addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
						.addComponent(jLabelTurningRegionR).addComponent(jTextFieldTurningRegionR))));
		createStructure();

	}// </editor-fold>//GEN-END:initComponents

	private VelocityStructure structure;

	/**
	 * @return radius [km] (not depth)
	 */
	double getEventR() {
		return structure.earthRadius() - Double.parseDouble(jTextFieldDepth.getText());
	}

	private void addMouseListners() {

		jLabelDepth.addMouseListener(createDescriptionMouseListner(ParameterDescription.createFrameDepth()));
		jLabelMostImportant
				.addMouseListener(createDescriptionMouseListner(ParameterDescription.createFrameRayparameter()));
		jLabelModel.addMouseListener(createDescriptionMouseListner(ParameterDescription.createFrameModel()));
		jLabelTurningRegionR
				.addMouseListener(createDescriptionMouseListner(ParameterDescription.createFrameAllowableError()));
		jLabelInterval.addMouseListener(createDescriptionMouseListner(ParameterDescription.createFrameInterval()));
	}

	private static MouseListener createDescriptionMouseListner(final JFrame frame) {
		MouseListener ml = new MouseListener() {
			Timer timer = null;

			// JDialog d = ParameterDescription.dialogForRayparameter();
			@Override
			public void mouseReleased(MouseEvent e) {
			}

			@Override
			public void mousePressed(MouseEvent e) {
			}

			@Override
			public void mouseExited(MouseEvent e) {
				timer.cancel();
				timer.purge();
				timer = null;
				frame.setVisible(false);
			}

			@Override
			public void mouseEntered(MouseEvent e) {
				timer = new Timer();
				timer.schedule(new TimerTask() {
					@Override
					public void run() {
						frame.setVisible(true);
					}
				}, 1500);
				Point newLocation = e.getLocationOnScreen();
				newLocation.x += 10;
				newLocation.y += 10;
				frame.setLocation(newLocation);
				// frame.setVisible(true);
			}

			@Override
			public void mouseClicked(MouseEvent e) {
			}
		};
		return ml;
	}

	private void addListners() {
		addMouseListners();
		jTextFieldDepth.addFocusListener(textFieldFocusListner);
		jTextFieldMostImportant.addFocusListener(textFieldFocusListner);
		jTextFieldInterval.addFocusListener(textFieldFocusListner);
		jTextFieldTurningRegionR.addFocusListener(textFieldFocusListner);

		jComboBoxModel.addPopupMenuListener(new PopupMenuListener() {
			InputModel currentModel = null;

			@Override
			public void popupMenuWillBecomeVisible(PopupMenuEvent e) {
				@SuppressWarnings("unchecked")
				JComboBox<String> box = (JComboBox<String>) e.getSource();
				currentModel = InputModel.titleOf((String) box.getSelectedItem());
				// System.out.println("visible " + currentModel);
			}

			@Override
			public void popupMenuWillBecomeInvisible(PopupMenuEvent e) {
				@SuppressWarnings("unchecked")
				JComboBox<String> box = (JComboBox<String>) e.getSource();
				InputModel changedModel = InputModel.titleOf((String) box.getSelectedItem());
				if (!changedModel.equals(currentModel))
					parameterChanged();
				createStructure();
				// System.out.println("invisible "+changedModel);;
			}

			@Override
			public void popupMenuCanceled(PopupMenuEvent e) {
			}
		});

	}

	/**
	 * if textField changed then computed will be false
	 */
	private FocusListener textFieldFocusListner = new FocusListener() {
		// private String currentText;
		private double currentValue;

		@Override
		public void focusLost(FocusEvent e) {
			JTextField textField = (JTextField) e.getSource();

			String changedText = textField.getText();
			double changedValue = Double.parseDouble(changedText);
			if (changedValue != currentValue)
				parameterChanged();
			// System.out.println(changedValue==currentValue);
		}

		@Override
		public void focusGained(FocusEvent e) {
			JTextField textField = (JTextField) e.getSource();
			String currentText = textField.getText();
			currentValue = Double.parseDouble(currentText);
			// System.out.println(currentText);
		}
	};

	/**
	 * @return Epicentral Distance mode: epicentral distance[deg]<br>
	 *         Ray parameter mode: ray parameter<br>
	 *         Turning depth mode: turning depth[km]<br>
	 *         Diffraction mode: angle on CMB [deg]
	 * 
	 */
	double getMostImportant() {
		return Double.parseDouble(jTextFieldMostImportant.getText());
	}

	VelocityStructure getStructure() {
		return structure;
	}

	private void createStructure() {
		InputModel model = InputModel.titleOf((String) jComboBoxModel.getSelectedItem());
		JFileChooser fileChooser = null;
		switch (model) {
		case AK135:
			structure = PolynomialStructure.AK135;
			break;
		case ANISOTROPIC_PREM:
			structure = PolynomialStructure.PREM;
			break;
		case ISOTROPIC_PREM:
			structure = PolynomialStructure.ISO_PREM;
			break;
		case NAMED_DISCONTINUITY:
			fileChooser = new JFileChooser();
			fileChooser.setFileFilter(new FileNameExtensionFilter("named discontinuity file", "nd"));
			if (fileChooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
				Path file = fileChooser.getSelectedFile().toPath();
				try {
					structure = new NamedDiscontinuityStructure(file);
				} catch (Exception e) {
					// e.printStackTrace();
					JOptionPane.showMessageDialog(null, "The file is invalid!");
					structure = null;
				}
			} else
				structure = null;
			break;
		case POLYNOMIAL:
			fileChooser = new JFileChooser();
			fileChooser.setFileFilter(new FileNameExtensionFilter("polynomial file", "inf"));
			if (fileChooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
				Path file = fileChooser.getSelectedFile().toPath();
				try {
					structure = new PolynomialStructure(file);
				} catch (Exception e) {
					// e.printStackTrace();
					JOptionPane.showMessageDialog(null, "The file is invalid!");
					structure = null;
				}
			} else
				structure = null;
			break;
		default:
			structure = null;
		}

	}

	double getIntegralInterval() {
		return Double.parseDouble(jTextFieldInterval.getText());
	}

	double getTurningRegionR() {
		return Double.parseDouble(jTextFieldTurningRegionR.getText());
	}

	// Variables declaration - do not modify//GEN-BEGIN:variables
	private javax.swing.JComboBox<String> jComboBoxModel;
	private javax.swing.JLabel jLabelDepth;
	private javax.swing.JLabel jLabelInterval;
	private javax.swing.JLabel jLabelModel;
	private javax.swing.JLabel jLabelMostImportant;
	private javax.swing.JLabel jLabelTurningRegionR;
	private javax.swing.JTextField jTextFieldDepth;
	private javax.swing.JTextField jTextFieldInterval;
	private javax.swing.JTextField jTextFieldMostImportant;
	private javax.swing.JTextField jTextFieldTurningRegionR;
	// End of variables declaration//GEN-END:variables
}
