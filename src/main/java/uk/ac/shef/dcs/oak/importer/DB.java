package uk.ac.shef.dcs.oak.importer;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;
import java.util.Vector;

import javax.swing.JOptionPane;

import org.postgresql.PGConnection;

public class DB {
	static private java.sql.Connection _cx = null;
	static private java.sql.Connection _cx_oracle = null;
	public static Connection getConnection() throws SQLException {
		if (_cx == null) {
			String url = "jdbc:postgresql://oak-vm10.dcs.shef.ac.uk/bils";
			Properties props = new Properties();
			props.setProperty("user","bils");
			props.setProperty("sslfactory", "org.postgresql.ssl.NonValidatingFactory");
			props.setProperty("ssl","true");
			java.sql.Connection cx = null;
			
			while (cx == null) {
				String password = JOptionPane.showInputDialog(null, "Enter OAK db password: ", 
						  "", JOptionPane.QUESTION_MESSAGE);
				props.setProperty("password", password);

				try {
					cx = DriverManager.getConnection(url, props);
				}
				catch (SQLException e) {
					JOptionPane.showMessageDialog(null, "Failed to authenticate: " + e.getMessage(), "Database connection failed", JOptionPane.ERROR_MESSAGE);
					cx = null;
				}
			}
			_cx = cx;
		}
		return(_cx);
	}
	
	public static PGConnection getPGConnection() throws SQLException {
		return((PGConnection) getConnection());
	}

	public static Connection getConnectionOracle() throws SQLException {
		if (_cx_oracle == null) {
		    try {
				String driverName = "oracle.jdbc.driver.OracleDriver";
				Class.forName(driverName);
				String url = "jdbc:oracle:thin:@cisdbl.sheffield.ac.uk:1521:macl";
				Properties props = new Properties();
				String password = JOptionPane.showInputDialog(null, "Enter db password: ", 
						"", JOptionPane.QUESTION_MESSAGE);
	
				props.setProperty("user","ac1st");
				props.setProperty("password", password);
	
				//props.setProperty("ssl","false");
	
				_cx_oracle = DriverManager.getConnection(url, props);
			} catch (ClassNotFoundException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}			
		}
		return(_cx_oracle);
	}

	/** TODO BILS specific */
	public static Vector<String> getProgrammeCodes() {
		String sql = "select programme_code " +
				     "from application " +
				     "group by programme_code " +
				     "order by programme_code;";
		return(getStringVector(sql));
	}
	
	/** TODO move to Table */
	static Vector<String> getImports() {
		return(getStringVector("select name from import order by name"));
	}
	
	/** TODO move to Table */
	public static Vector<String> getImportNames() {
		Vector<String> result = getImports();
		
		for (int i = 0; i < result.size(); ++i) {
			result.set(i, result.get(i).replace("imported_", ""));
		}
		
		return(result);
	}
	
	/** TODO move to Table */
	static Vector<String> getColumnNames(String importTable) {
		return(getStringVector("select name from import_column where import_name = '" + importTable + "'"));
	}
	
	static Vector<String> getStringVector(String sql) {
		Vector<String> result = new Vector<String>();
		try {
			Connection cx = getConnection();
			Statement st = cx.createStatement();
			ResultSet rs = st.executeQuery(sql);
			
			while(rs.next()) {
				//System.err.println(rs.getString(1));
				result.add(rs.getString(1));
			}
			st.close();			
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return(result);
	}
}
