package io.github.kensuke1984.anisotime;

import java.awt.Point;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Timer;
import java.util.TimerTask;
import java.util.function.Consumer;

import javax.swing.BorderFactory;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JTextField;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import javax.swing.filechooser.FileNameExtensionFilter;

/**
 * Panel for inputting parameters
 * 
 * @version 0.3.0.1
 * @author Kensuke Konishi
 */
class ParameterInputPanel extends javax.swing.JPanel {

	/**
	 * 2016/12/3
	 */
	private static final long serialVersionUID = 6718030883815139117L;

	private final ANISOtimeGUI gui;

	void setMode(ComputationMode mode) {
		switch (mode) {
		case EPICENTRAL_DISTANCE:
			jLabelMostImportant.setText("Epicentral distance \u0394 [deg]:");
			jTextFieldMostImportant.setText("60.0");
			break;
		case RAY_PARAMETER:
			jLabelMostImportant.setText("Ray parameter p:");
			jTextFieldMostImportant.setText("680.0");
			break;
		}
	}

	/**
	 * Creates new form ParameterInputPanel
	 */
	public ParameterInputPanel(ANISOtimeGUI gui) {
		this.gui = gui;
		initComponents();
	}

	void changePropertiesVisible() {
		//TODO
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
		jLabelMostImportant = new javax.swing.JLabel();
		jLabelModel = new javax.swing.JLabel("Model:");
		jLabelDepth = new javax.swing.JLabel("Depth [km]:");

		jTextFieldMostImportant = GUIInputComponents.createPositiveNumberField("60.0");
		jTextFieldDepth = GUIInputComponents.createPositiveNumberField("100.0");
		
		changeBorderTitle("Mode:Epicentral Distance  Polarity:P-SV");

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
				.addGroup(layout.createSequentialGroup())
			  );

		layout.setVerticalGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
				.addGroup(layout.createSequentialGroup()
						.addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
								.addComponent(jLabelMostImportant).addComponent(jTextFieldMostImportant))
						.addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
								.addComponent(jComboBoxModel).addComponent(jLabelModel))
						.addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
								.addComponent(jTextFieldDepth).addComponent(jLabelDepth))
						.addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
								 )
					 )) ;
//		createStructure();
		gui.setStructure(VelocityStructure.prem());
		gui.setEventDepth(100);
	}

	private void addMouseListners() {
		jLabelDepth.addMouseListener(createDescriptionMouseListner(ParameterDescription.createFrameDepth()));
		jLabelMostImportant
				.addMouseListener(createDescriptionMouseListner(ParameterDescription.createFrameRayparameter()));
		jLabelModel.addMouseListener(createDescriptionMouseListner(ParameterDescription.createFrameModel()));
	}
	private static MouseListener createDescriptionMouseListner(final JFrame frame) {
		return new MouseAdapter() {
			Timer timer;

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
			}

		};
	}

	private static FocusListener createAdapter(Consumer<Double> setter) {
		return new FocusAdapter() {
			@Override
			public void focusLost(FocusEvent e) {
				setter.accept(Double.parseDouble(((JTextField) e.getSource()).getText()));
			}
		};
	}

	private void addListners() {
		addMouseListners();

		// Function
		jTextFieldDepth.addFocusListener(createAdapter(gui::setEventDepth));
		jTextFieldMostImportant.addFocusListener( createAdapter(gui::setMostImportant));
		jComboBoxModel.addPopupMenuListener(new PopupMenuListener() {

			@Override
			public void popupMenuWillBecomeVisible(PopupMenuEvent e) {
			}

			@Override
			public void popupMenuWillBecomeInvisible(PopupMenuEvent e) {
				gui.setStructure(createStructure());
				gui.setEventDepth(Double.parseDouble(jTextFieldDepth.getText()));
			}

			@Override
			public void popupMenuCanceled(PopupMenuEvent e) {
			}
		});

	}

	private VelocityStructure createStructure() {
		InputModel model = InputModel.titleOf((String) jComboBoxModel.getSelectedItem());
		JFileChooser fileChooser;
		switch (model) {
		case AK135:
			return PolynomialStructure.AK135;
		case ANISOTROPIC_PREM:
			return PolynomialStructure.PREM;
		case ISOTROPIC_PREM:
			return PolynomialStructure.ISO_PREM;
		case NAMED_DISCONTINUITY:
			fileChooser = new JFileChooser();
			fileChooser.setFileFilter(new FileNameExtensionFilter("named discontinuity file", "nd"));
			if (fileChooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
				Path file = fileChooser.getSelectedFile().toPath();
				try {
					return new NamedDiscontinuityStructure(file);
				} catch (Exception e) {
					JOptionPane.showMessageDialog(null, "The file is invalid!");
					return null;
				}
			}
			return null;
		case POLYNOMIAL:
			fileChooser = new JFileChooser();
			fileChooser.setFileFilter(new FileNameExtensionFilter("polynomial file", "inf"));
			if (fileChooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION)
				try {
					return new PolynomialStructure(fileChooser.getSelectedFile().toPath());
				} catch (Exception e) {
					JOptionPane.showMessageDialog(null, "The file is invalid!");
					return null;
				}
			return null;
		default:
			throw new RuntimeException("unexpected");
		}
	}

	// Variables declaration - do not modify//GEN-BEGIN:variables
	private javax.swing.JComboBox<String> jComboBoxModel;
	private javax.swing.JLabel jLabelDepth;
	private javax.swing.JLabel jLabelModel;
	private javax.swing.JLabel jLabelMostImportant;
	private javax.swing.JTextField jTextFieldDepth;
	private javax.swing.JTextField jTextFieldMostImportant;
	// End of variables declaration//GEN-END:variables
}
