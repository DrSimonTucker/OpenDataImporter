package uk.ac.shef.dcs.oak.importer;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.sql.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.regex.Pattern;

import org.postgresql.PGConnection;
import org.postgresql.copy.CopyManager;


import au.com.bytecode.opencsv.CSVReader;
import au.com.bytecode.opencsv.CSVWriter;
import uk.ac.shef.dcs.oak.importer.Column.*;

/**
 * @author alex@slab.org
 * Imported table	
 * assumes the following schema:
drop table import;
CREATE TABLE import (
    name varchar(255) NOT NULL PRIMARY KEY,
    createtime timestamp without time zone,
    filename character varying(255),
    filetype character varying(255)
);

drop table import_column;
CREATE TABLE import_column (
    id serial primary key,
    import_name character varying(255) not null references import(name) on delete cascade,
    name varchar(255) not null,
    type varchar(16) not null,
    min real,
	max real,
	mean real,
	breaks real[]
);
CREATE INDEX on import_column  (import_name, name);
 



 */

public class Table {
	public String name;
	public Column columns[];  //  @jve:decl-index=0:
	private int csvSkipHeader = 0; 
	String filename, filetype;
	char separator;
	public String id = null;
	
	/**
	 * Constructor that loads table structure from the metadata tables import and import_column.
	 * Could have used postgres's own metadata tables with custom types but hey.
	 */
	public Table(String table_name) throws SQLException {
		name = table_name;
		
		Connection cx = DB.getConnection();
		PreparedStatement ps = cx.prepareStatement("select filename, filetype from import where name = ?");
		ps.setString(1, table_name);
		ResultSet rs = ps.executeQuery();
		if(rs.next()) {
			filename = rs.getString(1);
			filetype = rs.getString(2);
		}
		else {
			throw(new SQLException("Table not found"));
		}
		ps.close();

		ps = cx.prepareStatement("select count(*) from import_column where import_name = ?");
		ps.setString(1, table_name);
		rs = ps.executeQuery();
		if (! rs.next() ) {
			throw(new SQLException("can't happen -- no column count"));
		}
			
		int n = rs.getInt(1);
		columns = new Column[n];
		
		ps = cx.prepareStatement("select name, type, breaks from import_column where import_name = ?");
		ps.setString(1, table_name);
		rs = ps.executeQuery();
		for (int i = 0; rs.next(); ++i) {
			columns[i] = Column.fromType(this, rs.getString(2), rs.getString(1), rs.getArray(3));
		}
	}
	
	/**
	 * Creates table with the given number of empty columns
	 */
	Table(String name, int column_n) {
		this.name = name;
		columns = new Column[column_n];
		for (int i = 0; i < column_n; ++i) {
			columns[i] = new ColEmpty();
		}
	}
	
	public Column columnByName(String name) {
		Column result = null;
		for (Column column : columns) {
			if (column.name.contentEquals(name)) {
				result = column;
				break;
			}
		}
		return(result);
	}
	
	/**
	 * Returns the link column with the highest (most detailed) granularity, 
	 * or null if there isn't a link column.
	 */
	public LinkColumn linkColumn() {
		int highest = -1;
		LinkColumn result = null;
		for (Column column : columns) {
			if (column.isLink()) {
				LinkColumn linkColumn = (LinkColumn) column;
				if (linkColumn.granularity() > highest) {
					result = linkColumn;
				}
			}
		}
		return(result);
	}
	
	/**
	 * Placeholder for soon to-be-written XLS import
	 */
	static Table fromXLS(String table_name, String filename, int worksheet_i, boolean header) throws IOException {
		Table result = new Table(table_name, 0);
		result.filename = filename;
		result.filetype = "xls";
		return(result);
	}
	
	/**
	 * Infer table structure from a CSV file.  Does not actually import data, see loadCSV for that.
	 */
	static Table fromCSV(String table_name, String filename, char separator, boolean header) throws IOException {
		FileReader fr = new FileReader(filename);
		CSVReader csv = new CSVReader(fr, separator);

		String[] line = csv.readNext();
		Table result = new Table(table_name, line.length);
		result.filename = filename;
		result.filetype = "csv";
		result.separator = separator;
		
		Column[] columns = result.columns;

		//HashMap<String,Link> links = new HashMap<String, Link>();
				
		if (header) {
			String[] data_headings = null;
			boolean ons = false;

			if (line[0].contentEquals("DATASET_TITLE")) {
				// assume Office of National Statistics layout
				ons = true;
				csv.readNext();
				data_headings = csv.readNext();
				csv.readNext();
				csv.readNext();
				line = csv.readNext();
				result.csvSkipHeader = 6;
			}
			else {
				result.csvSkipHeader = 1;
			}
			
			ArrayList<String> seen = new ArrayList<String>();
			seen.add("bilsId"); // reserve this for our serial id
			
			Pattern nonword = Pattern.compile("\\W");
			Pattern startnumber = Pattern.compile("^\\d");
			int column_n = 0;
			
			for (int i = 0; i < line.length; ++i) {
				String col = line[i];
				if (ons && col.contentEquals("DATA_VALUE")) {
					col = data_headings[i];
				}
				col = col.toLowerCase();
				col = col.replace(' ', '_');
				col = nonword.matcher(col).replaceAll("");
				
				if (startnumber.matcher(col).matches()) {
					col = 'c' + col;
				}
				if(seen.contains(col)) {
					int n = 0;
					while(seen.contains(col + n)) {
						n++;
					}
					col += n;
				}
				seen.add(col);
				columns[column_n].inputColumn = column_n;
				columns[column_n].name = col;
				column_n++;
			}
			// start with data line
			line = csv.readNext();
		}
		else {
			for (int i = 0; i < line.length; ++i) {
				columns[i].name = "c" + i;
			}
		}
		
		Pattern integer = Pattern.compile("^-?\\d+$");
		Pattern real = Pattern.compile("^-?(\\d*\\.)?\\d+$");
		// Standard from http://en.wikipedia.org/wiki/Postcodes_in_the_United_Kingdom
		// but allowing any number (including zero) spaces
		String postcode_regex = 
			"([A-PR-UWYZ]([0-9][0-9A-HJKPS-UW]?|[A-HK-Y][0-9][0-9ABEHMNPRV-Y]?) *[0-9][ABD-HJLNP-UW-Z]{2})";
		Pattern postcode = Pattern.compile(postcode_regex);
		for (int i = 0; i < columns.length; ++i) {
			columns[i].minLength = columns[i].maxLength = line[i].length();
		}
		
		do {
			for (int i = 0; i < columns.length; ++i) {
				// ignore empty values
				if (line[i].length() == 0) {
					continue;
				}

				switch (columns[i].type()) {
				case Empty:
				case Integer:
					if (integer.matcher(line[i]).matches()) {
						if (columns[i].type() != ColType.Integer)
							columns[i] = new ColInteger(columns[i]);
						break;
					}
					else {
						System.err.println("not an int: '" + line[i] + "'");
					}					// fall through
				case Real:
					if (real.matcher(line[i]).matches()) {
						if (columns[i].type() != ColType.Real)
							columns[i] = new ColReal(columns[i]);
						break;
					}
				case Postcode:
					if (columns[i].name.contentEquals("postcode")) {
						if (columns[i].type() != ColType.Postcode)
							columns[i] = new ColPostcode(columns[i]);
						if (! postcode.matcher(line[i]).matches()) {
							System.err.println("bad postcode: " + line[i]);
						}
						break;
					}
					if (postcode.matcher(line[i]).matches()) {
						if (columns[i].type() != ColType.Postcode)
							columns[i] = new ColPostcode(columns[i]);
						break;
					}
				// TODO try to identify LSOA/MSOA etc by format if the column name doesn't match?
				case LSOA:
					if (columns[i].name.contentEquals("lsoa_id") ||
						columns[i].name.contentEquals("lsoa_code")) {
						if (columns[i].type() != ColType.LSOA)
							columns[i] = new ColLSOA(columns[i]);
						/*if (! lsoa.matcher(line[i]).matches()) {
							System.err.println("bad lsoa: " + line[i]);
						}*/
						break;
					}
				case MSOA:
					if (columns[i].name.contentEquals("msoa_id") ||
						columns[i].name.contentEquals("msoa_code")) {
						if (columns[i].type() != ColType.MSOA)
							columns[i] = new ColMSOA(columns[i]);
						/*if (! msoa.matcher(line[i]).matches()) {
							System.err.println("bad msoa: " + line[i]);
						}*/
						break;
					}
				case LA:
					if (columns[i].name.contentEquals("la_id") ||
						columns[i].name.contentEquals("la_code")) {
						if (columns[i].type() != ColType.LA)
							columns[i] = new ColLA(columns[i]);
						/*if (! la.matcher(line[i]).matches()) {
							System.err.println("bad la: " + line[i]);
						}*/
						break;
					}
				default:
					if (columns[i].type() != ColType.Char)
						columns[i] = new ColChar(columns[i]);
				}				
				
				if (line[i].length() > columns[i].maxLength) {
					columns[i].maxLength = line[i].length();
				}
				if (line[i].length() < columns[i].minLength) {
					columns[i].minLength = line[i].length();
				}
			}
		} while((line = csv.readNext()) != null);
		result.purgeEmptyColumns();
		return(result);
	}
	
	private void purgeEmptyColumns() {
		int n = 0;
		for (Column column : columns) {
			if (column.type() == ColType.Empty) {
				n++;
			}
		}
		
		// probably is a library call for this..
		if (n > 0) {
			Column[] tmp = new Column[columns.length - n];
			int i = 0;
			for (Column column : columns) {
				if (column.type() != ColType.Empty) {
					tmp[i++] = column;
				}
			}
			columns = tmp;
		}
	}
	
	/** Imports the CSV file.  You probably want to call this on a Table object 
	 * built via a call to fromCSV() 
	 */
	public void loadCSVSlower() throws IOException, SQLException {
		
		CSVReader csv = new CSVReader(new FileReader(filename), separator);
		
		String[] line;
		for (int i=0; i < csvSkipHeader; ++i) {
			csv.readNext();
		}
		
		Connection cx = DB.getConnection();

		while ((line = csv.readNext()) != null) {
			String sql = "insert into " + name + " values (default";
			for (Column column : columns) {
				sql += ", " + column.formatSQL(line[column.inputColumn]);
			}
			sql += ")";
			// TODO prepared statements now that columns are polymorphic
			Statement st = cx.createStatement();
			st.execute(sql);
		}
	}

	public void loadCSV() throws IOException, SQLException {
		System.err.println("Speedy loader");
		CSVReader csvIn = new CSVReader(new FileReader(filename), separator);
	    PGConnection pcx = DB.getPGConnection();
	    CopyManager copyAPI = pcx.getCopyAPI();
		String[] line;
		File temp = File.createTempFile("load", ".csv");
		CSVWriter csvOut = new CSVWriter(new FileWriter(temp), '|', CSVWriter.NO_QUOTE_CHARACTER);
		
		for (int i=0; i < csvSkipHeader; ++i) {
			csvIn.readNext();
		}
		
		ArrayList<String> columnNames = new ArrayList<String>();
		for (int i = 0; i < columns.length; ++i) {
			Column column = columns[i];
			columnNames.add(column.name);
		}
		String columnNamesStr = join(columnNames, ",");
		
		String buf[] = new String[columns.length];
		while ((line = csvIn.readNext()) != null) {
			for (int i = 0; i < columns.length; ++i) {
				Column column = columns[i];
				// something is doing the formatting elsewhere..
				//buf[i] = column.formatSQL(line[column.inputColumn]);
				buf[i] = line[column.inputColumn];
			}
			csvOut.writeNext(buf);
		}
		csvOut.close();
		csvIn.close();
		copyAPI.copyIn("COPY " + name + " (" + columnNamesStr + ") FROM STDIN WITH DELIMITER '|' NULL AS ''", new FileReader(temp));
	}

	static String join(Collection<?> s, String delimiter) {
	    StringBuilder builder = new StringBuilder();
	    Iterator iter = s.iterator();
	    while (iter.hasNext()) {
	        builder.append(iter.next());
	        if (!iter.hasNext()) {
	          break;                  
	        }
	        builder.append(delimiter);
	    }
	    return builder.toString();
	}
	
	/** Returns CREATE TABLE and CREATE INDEX statements for now. */
	public String toString() {
		String result = "create table " + name + " (\n";
		result += "  id serial primary key";
		for (Column column : columns) {
			result += ",\n  " + column.toString();
		}
		result += "\n);\n";
		for (Column column : columns) {
			if (column.isLink()) {
				result += String.format("create index on %s (%s);\n", name, column.name);
			}
		}
		return(result);
	}
	
	/** create the table structure and metadata rows in the database.  destroys any existing table with the same name (!) */
	public void create() throws SQLException {
		Connection cx = DB.getConnection();
		Statement st = cx.createStatement();
		System.err.println(this.toString());
		st.execute(this.toString());
		st.close();
		
		// delete cascades to import_column
		PreparedStatement ps = cx.prepareStatement("delete from import where name = ?");
		ps.setString(1, name);
		ps.execute();
		ps.close();
		
		ps = cx.prepareStatement("insert into import (name, createtime, filename, filetype) values (?, now(), ?, ?)");
		ps.setString(1, name);
		ps.setString(2, filename);
		ps.setString(3, filetype);
		ps.execute();
		ps.close();
		
		for (Column column : columns) {
			ps = cx.prepareStatement("insert into import_column (import_name, name, type) values (?, ?, ?)");
			ps.setString(1, name);
			ps.setString(2, column.dbName());
			ps.setString(3, column.type().name());
			ps.execute();
			ps.close();				
		}
		
	}
		
	public void dropIfExists() throws SQLException {
		Connection cx = DB.getConnection();
		Statement st = cx.createStatement();
		st.execute("drop table if exists " + name);		
	}
	
	public Object[][] data(int rows) throws IOException {
		CSVReader csv = new CSVReader(new FileReader(filename), separator);

		Object[][] result = new Object[rows][this.columns.length];
		String[] line;
		
		for (int i=0; i < csvSkipHeader; ++i) {
			csv.readNext();
		}
		
		for (int row = 0; row < rows; ++row) {
			line = csv.readNext();
			if (line == null) {
				break;
			}
		  
			int i = 0;
			for (Column column : columns) {
				result[row][i++] = column.formatObject(line[column.inputColumn]);
			}
		}
		return(result);
	}
}
