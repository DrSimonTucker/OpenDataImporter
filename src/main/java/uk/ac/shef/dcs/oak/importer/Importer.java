package uk.ac.shef.dcs.oak.importer;

import java.awt.BorderLayout;
import java.awt.Button;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.FlowLayout;
import java.awt.Panel;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.sql.SQLException;
import java.util.Enumeration;
import java.util.Vector;

import javax.swing.DefaultCellEditor;
import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.JToolBar;
import javax.swing.ListSelectionModel;
import javax.swing.border.EmptyBorder;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import javax.swing.JSplitPane;
import javax.swing.JList;

import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.*;

import java.awt.GridLayout;
import java.awt.GridBagLayout;
import java.awt.GridBagConstraints;
import java.awt.Insets;
import javax.swing.JLabel;

@SuppressWarnings("serial")
public class Importer extends JPanel {

	private Table dataTable = null;
	private JTextField txtTablename;
	JComboBox comboBoxFormat;
	
	private boolean header = true;
	private JTable previewTable;
	private JButton btnImport, btnPreview;
	private File inputFile = null;
	private JFrame frame = null;
	DefaultListModel worksheetListModel = null;
	
	int worksheet_i = -1;
	
	String fileType = "CSV";
	private char separator = ',';
	private JTextField txtFilename;
	
	/**
	 * This method initializes this
	 * 
	 */
	@SuppressWarnings("unused")
	private void initialize() {
		setLayout(new BorderLayout());
	}

	/**
	 * Launch the application.
	 */
	public static void main(String[] args) {
			
		EventQueue.invokeLater(new Runnable() {
			public void run() {
				try {
					Importer panel = new Importer();
					JFrame frame = new JFrame();
			        frame.setSize(new Dimension(328, 180));
			        frame.setContentPane(panel);
					frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
					frame.setBounds(100, 100, 700, 395);
					frame.setVisible(true);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		});
	}

	public void setFrame(JFrame frame) {
		this.frame = frame;
	}
	
	void loadWorksheets(String filename) throws IOException {
		InputStream is = new FileInputStream(filename);
		Workbook wb = new HSSFWorkbook(is);
		worksheetListModel.clear();
		for (int i = 0; i < wb.getNumberOfSheets(); ++i) {
			String name = wb.getSheetAt(i).getSheetName();
			worksheetListModel.addElement(name);
		}
	}
	
	void checkEnable() {
		if (dataTable != null) {
			dataTable.name = getTablename();
		}
		
		if (inputFile != null) {
			btnPreview.setEnabled(true);
			if (dataTable != null && dataTable.name != null && dataTable.name.length() > 0) {
				btnImport.setEnabled(true);
			}
			else {
				btnImport.setEnabled(false);
			}
		}
		else {
			btnPreview.setEnabled(false);
			btnImport.setEnabled(false);
		}
	}
	
	/**
	 * Create the frame.
	 */
	public Importer() {
		setBorder(new EmptyBorder(5, 5, 5, 5));
		setLayout(new BorderLayout(0, 0));
		
		
		Panel panel = new Panel();
		add(panel, BorderLayout.SOUTH);
		panel.setLayout(new FlowLayout(FlowLayout.CENTER, 5, 5));
		
		btnImport = new JButton("Import");
		btnImport.setActionCommand("");
		btnImport.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				try {
					Importer.this.apply();
					if (frame != null) {
						//ImportFrame f = (ImportFrame) frame;
						//((ImportFrame)frame).refreshSources();
						frame.setVisible(false);
						frame.dispose();
					}
					// TODO Importer.this.dispose();
				} catch (SQLException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		});
		
		JButton resetButton = new JButton("Reset");
		panel.add(resetButton);
		resetButton.setActionCommand("");
		resetButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				dataTable.name = null;
				txtFilename.setText("");
				txtTablename.setText("");
				resetData();
				checkEnable();
			}
		});
		
		btnPreview = new JButton("Preview");
		btnPreview.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				try {
					if (fileType == "CSV") {
						dataTable = Table.fromCSV(Importer.this.getTablename(), inputFile.getAbsoluteFile().toString(), separator, header);
					}
					else if (fileType == "XLS"){
						Importer.this.loadWorksheets(inputFile.getAbsoluteFile().toString());
						dataTable = Table.fromXLS(Importer.this.getTablename(), inputFile.getAbsoluteFile().toString(), worksheet_i, header);							
					}
					else {
						// can't happen
					}
					loadHeadings();
					loadSampleData();
				} catch (FileNotFoundException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		});
		btnPreview.setEnabled(false);
		panel.add(btnPreview);
		btnImport.setEnabled(false);
		panel.add(btnImport);
		DefaultTableModel model = new MyDefaultTableModel();
		
		JScrollPane scrollPane = new JScrollPane();
		add(scrollPane, BorderLayout.CENTER);
		previewTable = new JTable(model) {
			public Component prepareRenderer(TableCellRenderer renderer,
		                                     int rowIndex, int vColIndex) {
		        Component c = super.prepareRenderer(renderer, rowIndex, vColIndex);
		        if (rowIndex == 0) {
		            c.setBackground(Color.lightGray);
		        }
		        else if (isCellSelected(rowIndex, vColIndex)) {
		            c.setBackground(Color.blue);
		        }
		        else {
		        	c.setBackground(getBackground());
		        }
		        return c;
		    }
		};
		ListSelectionModel lsm = previewTable.getSelectionModel();
		
		previewTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
		previewTable.setAutoCreateColumnsFromModel(false);
		
		scrollPane.setViewportView(previewTable);
		
		JPanel panel_1 = new JPanel();
		add(panel_1, BorderLayout.NORTH);
		GridBagLayout gbl_panel_1 = new GridBagLayout();
		gbl_panel_1.columnWidths = new int[]{73, 359, 0};
		gbl_panel_1.rowHeights = new int[]{39, 39, 0, 0, 0};
		gbl_panel_1.columnWeights = new double[]{1.0, 0.0, Double.MIN_VALUE};
		gbl_panel_1.rowWeights = new double[]{1.0, 1.0, 1.0, 1.0, Double.MIN_VALUE};
		panel_1.setLayout(gbl_panel_1);
		
		JPanel panel_6 = new JPanel();
		GridBagConstraints gbc_panel_6 = new GridBagConstraints();
		gbc_panel_6.anchor = GridBagConstraints.EAST;
		gbc_panel_6.insets = new Insets(0, 0, 5, 5);
		gbc_panel_6.fill = GridBagConstraints.VERTICAL;
		gbc_panel_6.gridx = 0;
		gbc_panel_6.gridy = 0;
		panel_1.add(panel_6, gbc_panel_6);
		
		JLabel lblFile = new JLabel("File");
		panel_6.add(lblFile);
		
		JPanel panel_2 = new JPanel();
		FlowLayout flowLayout = (FlowLayout) panel_2.getLayout();
		flowLayout.setAlignment(FlowLayout.LEFT);
		GridBagConstraints gbc_panel_2 = new GridBagConstraints();
		gbc_panel_2.fill = GridBagConstraints.BOTH;
		gbc_panel_2.insets = new Insets(0, 0, 5, 0);
		gbc_panel_2.gridx = 1;
		gbc_panel_2.gridy = 0;
		panel_1.add(panel_2, gbc_panel_2);
		
		txtFilename = new JTextField();
		txtFilename.setEditable(false);
		panel_2.add(txtFilename);
		txtFilename.setColumns(20);
		
		JButton btnLoadFile = new JButton("Choose File");
		panel_2.add(btnLoadFile);
		btnLoadFile.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				JFileChooser fc = new JFileChooser();
				int v = fc.showOpenDialog(Importer.this);
				if (v == JFileChooser.APPROVE_OPTION) {
					inputFile = fc.getSelectedFile();
					txtFilename.setText(inputFile.getAbsoluteFile().toString());
					checkEnable();
				}
			}

		});
		
		JPanel panel_7 = new JPanel();
		GridBagConstraints gbc_panel_7 = new GridBagConstraints();
		gbc_panel_7.anchor = GridBagConstraints.EAST;
		gbc_panel_7.insets = new Insets(0, 0, 5, 5);
		gbc_panel_7.fill = GridBagConstraints.VERTICAL;
		gbc_panel_7.gridx = 0;
		gbc_panel_7.gridy = 1;
		panel_1.add(panel_7, gbc_panel_7);
		
		JLabel lblFormat = new JLabel("Format");
		panel_7.add(lblFormat);
		
		JPanel panel_4 = new JPanel();
		FlowLayout flowLayout_1 = (FlowLayout) panel_4.getLayout();
		flowLayout_1.setAlignment(FlowLayout.LEFT);
		GridBagConstraints gbc_panel_4 = new GridBagConstraints();
		gbc_panel_4.insets = new Insets(0, 0, 5, 0);
		gbc_panel_4.fill = GridBagConstraints.BOTH;
		gbc_panel_4.gridx = 1;
		gbc_panel_4.gridy = 1;
		panel_1.add(panel_4, gbc_panel_4);
		
		comboBoxFormat = new JComboBox();
		panel_4.add(comboBoxFormat);
		comboBoxFormat.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				JComboBox cb = (JComboBox) arg0.getSource();
				String selected = (String) cb.getSelectedItem();
				if (selected == "CSV") {
					fileType = "CSV";
					separator = ',';
				}
				else if (selected == "TSV") { 
					fileType = "CSV";
					separator = '\t';					
				}
				else if (selected == "XLS"){
					fileType = "XLS";
				}
			}
		});
		comboBoxFormat.setModel(new DefaultComboBoxModel(new String[] {"CSV", "TSV", "XLS"}));
		
		JCheckBox chckbxHasHeader = new JCheckBox("Has header");
		panel_4.add(chckbxHasHeader);
		chckbxHasHeader.setSelected(true);
		chckbxHasHeader.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				JCheckBox cb = (JCheckBox) arg0.getSource();
				header = cb.isSelected();
			}
		});
		
		JPanel panel_5 = new JPanel();
		GridBagConstraints gbc_panel_5 = new GridBagConstraints();
		gbc_panel_5.anchor = GridBagConstraints.EAST;
		gbc_panel_5.insets = new Insets(0, 0, 0, 5);
		gbc_panel_5.fill = GridBagConstraints.VERTICAL;
		gbc_panel_5.gridx = 0;
		gbc_panel_5.gridy = 3;
		panel_1.add(panel_5, gbc_panel_5);
		
		JLabel lblTableName = new JLabel("Table name");
		panel_5.add(lblTableName);
		
		JPanel panel_3 = new JPanel();
		FlowLayout flowLayout_2 = (FlowLayout) panel_3.getLayout();
		flowLayout_2.setAlignment(FlowLayout.LEFT);
		GridBagConstraints gbc_panel_3 = new GridBagConstraints();
		gbc_panel_3.fill = GridBagConstraints.BOTH;
		gbc_panel_3.gridx = 1;
		gbc_panel_3.gridy = 3;
		panel_1.add(panel_3, gbc_panel_3);
		
		txtTablename = new JTextField();
		panel_3.add(txtTablename);
		txtTablename.addFocusListener(new FocusAdapter() {
			@Override
			public void focusLost(FocusEvent arg0) {
				checkEnable();
			}
		});
		txtTablename.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				checkEnable();
			}
		});
		txtTablename.setColumns(20);
				
		worksheetListModel = new DefaultListModel();
		JList worksheetList = new JList(worksheetListModel);

		//scrollPane_1.setViewportView(worksheetList);
		
		
		lsm.addListSelectionListener(new ListSelectionListener() {
			public void valueChanged(ListSelectionEvent e) {
				Importer.this.updateColTypes();
			}
		});
	}

	private void apply() throws SQLException, IOException {
		dataTable.dropIfExists();
		dataTable.create();
		dataTable.loadCSV();
	}

	private void resetData() {
		DefaultTableModel model = (DefaultTableModel) previewTable.getModel();
		while (model.getRowCount() > 0) {
			model.removeRow(0);
		}
		while (model.getColumnCount() > 0) {
			removeColumnAndData(previewTable, 0);
		}
	}

	private void loadHeadings() {
		System.err.println("adding columns\n");
		Column[] columns = dataTable.columns;

		resetData();
		
		for (int i = 0; i < columns.length; ++i) {
			if (columns[i].type() == Column.ColType.Empty) {
				continue;
			}
			System.err.println("adding column: " + columns[i].name);
			//model.addColumn(columns[i].name);
			betterAddColumn(previewTable, columns[i].name, new Object[]{});
		}
		
	}

		
	private void loadSampleData() throws IOException {
		Object[][] rows = dataTable.data(16);
		DefaultTableModel model = (DefaultTableModel) previewTable.getModel();
		String[] topRow = new String[dataTable.columns.length];
		JComboBox comboBox = new JComboBox();
		
		for (Column.ColType type : Column.ColType.values()) {
			comboBox.addItem(type.name());
		}

		for (int i = 0; i < dataTable.columns.length; ++i) {
			TableColumn columnModel = previewTable.getColumnModel().getColumn(i);
			columnModel.setCellEditor(new DefaultCellEditor(comboBox));
			topRow[i] = dataTable.columns[i].type().name();
		}
		model.addRow(topRow);
		

		for (Object[] row : rows) {
			model.addRow(row);
		}
		
		// add ellipses
		if (rows[0].length > 0) {
			String[] row = new String[rows[0].length];
			for(int j = 0; j < rows[0].length; ++j) {
				row[j] = "...";
			}
			model.addRow(row);
		}
		
	}
	
	String getTablename() {
		return("imported_" + this.txtTablename.getText());
	}

	void updateColTypes() {
		MyDefaultTableModel model = (MyDefaultTableModel) previewTable.getModel();
		for (int i = 0; i < dataTable.columns.length; ++i) {
			String value = (String) model.getValueAt(0, i);
			Column column = dataTable.columns[i];
			if (! column.type().name().contentEquals(value)) {
				Class<Column> colClass = Column.classFromType(value);
				if (colClass == null) {
					System.err.println("no class found: " + value);
				}
				try {
					// a little reflection is never enough
					Constructor<Column> ctor = colClass.getDeclaredConstructor(Column.class);
					Column col = ctor.newInstance(dataTable.columns[i]);
					dataTable.columns[i] = col;
				} catch (InstantiationException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (IllegalAccessException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (IllegalArgumentException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (InvocationTargetException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (SecurityException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (NoSuchMethodException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}
	}
	
	// below ripped from http://www.exampledepot.com/egs/javax.swing.table/AppendCol.html
	public void betterAddColumn(JTable table, Object headerLabel,
        Object[] values) {
		DefaultTableModel model = (DefaultTableModel)table.getModel();
		TableColumn col = new TableColumn(model.getColumnCount());

		// Ensure that auto-create is off
		if (table.getAutoCreateColumnsFromModel()) {
			throw new IllegalStateException();
		}
		col.setHeaderValue(headerLabel);
		table.addColumn(col);
		model.addColumn(headerLabel.toString(), values);
	}
	
	// Removes the specified column from the table and the associated
	// call data from the table model.
	public void removeColumnAndData(JTable table, int vColIndex) {
	    MyDefaultTableModel model = (MyDefaultTableModel)table.getModel();
	    TableColumn col = table.getColumnModel().getColumn(vColIndex);
	    int columnModelIndex = col.getModelIndex();
	    Vector<?> data = model.getDataVector();
	    Vector<?> colIds = model.getColumnIdentifiers();

	    // Remove the column from the table
	    table.removeColumn(col);

	    // Remove the column header from the table model
	    colIds.removeElementAt(columnModelIndex);

	    // Remove the column data
	    for (int r=0; r<data.size(); r++) {
	        Vector<?> row = (Vector<?>)data.get(r);
	        row.removeElementAt(columnModelIndex);
	    }
	    model.setDataVector(data, colIds);

	    // Correct the model indices in the TableColumn objects
	    // by decrementing those indices that follow the deleted column
	    Enumeration<?> en = table.getColumnModel().getColumns();
	    for (; en.hasMoreElements(); ) {
	        TableColumn c = (TableColumn)en.nextElement();
	        if (c.getModelIndex() >= columnModelIndex) {
	            c.setModelIndex(c.getModelIndex()-1);
	        }
	    }
	    model.fireTableStructureChanged();
	}
	
	// This subclass adds a method to retrieve the columnIdentifiers
	// which is needed to implement the removal of
	// column data from the table model

	class MyDefaultTableModel extends DefaultTableModel {
	    @SuppressWarnings("rawtypes")
		public Vector getColumnIdentifiers() {
	        return columnIdentifiers;
	    }
	    
	    @Override
	    public boolean isCellEditable(int row, int column) {
	        //all cells false
	        return row == 0;
	    }
	}
	
}  //  @jve:decl-index=0:visual-constraint="60,19"
