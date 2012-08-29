package uk.ac.shef.dcs.oak.importer;

import java.sql.Array;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.regex.*;

import javax.swing.ProgressMonitor;


/**
 * @author alex@slab.org
 */ 
abstract public class Column {
	public String name = null;
	
	public int maxLength;   /** Maximum length in characters */
	public int minLength;   /** Minimum length in characters */
	public int inputColumn; /** Column number from source document where known */
	public Table table;     /** SQL Table of which this column is part */
	public Float[] breaks = null;
	
	/**
	 * Possible column types.  There's a subclass called Column.Col<name> for each of these.
	 * There is probably a design pattern that would remove the need for this nonsense. 
	 */	
	public enum ColType {
		Empty, Postcode, Char, Integer, Real, Geometry, OA, LSOA, MSOA, LA, Applicant
	}
	
	/**
	 * Overridden to return the class's corresponding ColType value.  This will be the first
	 * up against the wall come the refactor. 
	 */
	public abstract ColType type();	
	
	Column () {
	}
	
	/**
	 * Turns a column of one type into one of another type.  Used e.g. when a floating point number 
	 * crops up while importing a column which up to that point had only contained integers
	 */
	Column(Column column) {
		this();
		name = column.name;
		maxLength = column.maxLength;
		minLength = column.minLength;
		inputColumn = column.inputColumn;
		table = column.table;
	}
	
	/**
	 * Returns a ColType given its name
	 */
	public static ColType typeByName(String typeName) {
		ColType type = null;
		for (ColType tmp : ColType.values()) {
			if (tmp.name().contentEquals(typeName)) { 
				type = tmp;
				break;
			}
		}
		return(type);
	}

	
	/**
	 * Returns a column object given its Table, coltype name, column name and breaks
	 * @throws SQLException 
	 */
	public static Column fromType(Table table, String typeName, String name, Array breaks) throws SQLException {
		ColType type = typeByName(typeName);
		
		if (type != null) {
			return(fromType(table, type, name, breaks));
		}
		return(null);
	}
	
	/**
	 * Returns a column object given its Table, coltype, column name and breaks
	 * @throws SQLException 
	 */
	public static Column fromType(Table table, ColType type, String name, Array breaks) throws SQLException {
		Column result = null;

		try {
			result = classFromType(type).newInstance();
			result.table = table;
		} catch (InstantiationException e) {
			e.printStackTrace();
		} catch (IllegalAccessException e) {
			e.printStackTrace();
		}
		
		if (result != null) {
			result.name = name;
			if (breaks != null) {
				result.breaks = (Float[]) breaks.getArray();
			}
			result.maxLength = -1;
			result.minLength = -1;
			result.inputColumn = -1;
		}
		
		return(result);
	}
	
	static Class<Column> classFromType(String typeName) {
		ColType type = typeByName(typeName);

		if (type != null) {
			return(classFromType(type));
		}
		return(null);
	}
	
	@SuppressWarnings("unchecked")
	private static Class<Column> classFromType(ColType type) {
		Class<Column> result = null;
		try {
			result = (Class<Column>) Class.forName("uk.ac.shef.dcs.oak.importer.Column$Col" + type.name());
		} catch (Exception e) {
			e.printStackTrace();
		}
		return(result);
	}
	
	void breaks() {
		
	}
	
	/**
	 * Calculates natural breaks in numerical data via PL/R
	 */
	boolean calcBreaks() {
		boolean result = false;
		try {
			// Load R function if it's missing
			PgPL.load("r_calc_import_column_breaks.sql", false);
			Connection cx = DB.getConnection();
			Statement st = cx.createStatement();
			String sql = String.format("select r_calc_import_column_breaks('%s', '%s');",
					table.name, this.name
					);
			System.err.println("sql: " + sql);
			ResultSet rs = st.executeQuery(sql);
			if (rs.next() && rs.getString(1) == "done") {
				result = true;
			}
			st.close();
			
			st = cx.createStatement();
			rs = st.executeQuery(String.format("select breaks from import_column where import_name = '%s' and name = '%s'",
											   table.name,
											   name
						   					  )
						   		);
			if (rs.next()) {
				breaks = (Float[]) rs.getArray(1).getArray();
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return(result);
	}
	
	/**
	 * Returns true iff the column has had its breaks calculated
	 */
	
	boolean hasBreaks() {
		return(breaks != null && breaks.length > 0);
	}
	
	public Float[] getBreaks() {
		if (!hasBreaks()) {
			System.err.println("calculating breaks...");
			calcBreaks();
			System.err.println("calculated breaks.");
		}
		return(breaks);
	}
	
	/**
	 * Formats a given column value into canonical form.
	 */
	String format(String value) {
		return(value);
	}
	
	/**
	 * Formats a given column value into canonical, SQL quoted form.
	 */
	public String formatSQL(String value) {
		return(format(value));
	}

	/**
	 * Returns the column's name in the database table.
	 */
	public String dbName() {
		String result = name;
		if (!Pattern.matches("[a-zA-Z].*", name)) {
			result = "c" + name;
		}
		return(result);	
	}
	
	public String toString() {
		return(dbName());
	}
	
	/**
	 * Returns true for columns that have linking tables etc defined,
	 * e.g. geographical boundary keys
	 */
	final public boolean isLink() {
		return(LinkColumn.class.isInstance(this));
	}
	
	public Object formatObject(String value) {
		return(format(value));
	}
	
	/**
	 * Interface for column that is a link, in lieu of multiple inheritance 
	 */
	public static interface LinkColumn {
		String keyTables();
		String keyLink();
		int granularity();
	}
	
	/**
	 * Used when importing data where no values have yet been found to infer types from
	 */
	public static class ColEmpty extends Column {
		public ColEmpty() {
			super();
		}
		ColEmpty(Column column) {
			super(column);
		}

		public ColType type() {
			return(ColType.Empty);
		}
		
		public String toString() {
			return("");
		}
		
		String format(String value) {
			return("");
		}
	}

	public static class ColChar extends Column {
		ColChar() {
			super();
		}
		
		public ColChar(Column column) {
			super(column);
		}

		public ColType type() {
			return(ColType.Char);
		}

		public String toString() {
			String result = super.toString();
			if (minLength == maxLength) {
				result += " char(" + minLength + ")";
			}
			else {
				result += " varchar(" + maxLength + ")";
			}
			return(result);
		}
		
		public String formatSQL(String value) {
			value = format(value);
			value = value.replace("\\", "\\\\");
			value = value.replace("\'", "\\'");
			value = "E'" + value + "'";
			return(value);
		}
		
	}
	
	public static class ColPostcode extends ColChar {
		ColPostcode() {
			super();
		}
		public ColPostcode(Column column) {
			super(column);
		}

		public ColType type() {
			return(ColType.Postcode);
		}

		String format(String value) {
			// to Ordnance Survey format
			value.replace(" ", "");
			Pattern p = Pattern.compile("^(.+)(...)$");
			Matcher m = p.matcher(value);
			if (m.matches()) {
				value = String.format("%4s%s", m.group(1), m.group(2));
			}
			super.format(value);
			return(value);
		}
	}

	public static class ColInteger extends Column {
		ColInteger() {
			super();
		}
		public ColInteger(Column column) {
			super(column);
		}

		public ColType type() {
			return(ColType.Integer);
		}

		public String toString() {
			return(super.toString() + " integer");
		}
		
		String format(String value) {
			if (value.length() == 0) {
				value = "null";
			}
			return(value);
		}
		
		public Object formatObject(String value) {
			Object result;
			try {
				result = Integer.parseInt(value);
			}
			catch (NumberFormatException e) {
				result = "null";
			}
			return(result);
		}
	}
	
	public static class ColReal extends ColInteger {
		ColReal() {
			super();
		}
		public ColReal(Column column) {
			super(column);
		}

		public ColType type() {
			return(ColType.Real);
		}

		public String toString() {
			return(name + " real");
		}

		String format(String value) {
			value = super.format(value);
			value = value.replaceFirst("\\.", "0.");
			return(value);
		}
		
		public Object formatObject(String value) {
			Object result;
			try {
				result = Float.parseFloat(value);
			}
			catch (NumberFormatException e) {
				result = "null";
			}
			return(result);
		}
	}
	
	/** Postgis geometry, ordnance survey projection */
	static class ColGeometry extends Column {
		ColGeometry() {
			super();
		}
		ColGeometry(Column column) {
			super(column);
		}

		public ColType type() {
			return(ColType.Geometry);
		}

		public String toString() {
			String result = super.toString();
			result += " geometry";
			// assume 2D multipolygon in OS national grid
			result += ", CONSTRAINT enforce_dims_" + name + 
				" CHECK ((st_ndims(" + name + ") = 2))";
			result += ", CONSTRAINT enforce_geotype_" + name + 
				" CHECK (((geometrytype(" + name + ") = 'MULTIPOLYGON'::text) OR (the_geom IS NULL)))";
			result += ", CONSTRAINT enforce_srid_" + name + 
				" CHECK ((st_srid(" + name + ") = 27700))";
			return(result);
		}
	}
	
	/** ONS output area */
	static class ColOA extends ColChar implements LinkColumn {
		ColOA() {
			super();
		}
		ColOA(Column column) {
			super(column);
		}

		public ColType type() {
			return(ColType.OA);
		}
		public String keyTables() {
			String tableId = ((Column) this).table.id;
			return(String.format("oa_regions %s_oa_regions", tableId));
		}
		public String keyLink() {
			String tableId = ((Column) this).table.id;
			String result = String.format("%s.%s = %s_oa_regions.coa_code AND applicant.oa_regions_gid = %s_oa_regions.gid", tableId, name, tableId, tableId);
			return(result);
		}
		public int granularity() {
			return(40);
		}
	}

	/** ONS Lower super output area */
	public static class ColLSOA extends ColChar implements LinkColumn {
		ColLSOA() {
			super();
		}
		public ColLSOA(Column column) {
			super(column);
		}

		public ColType type() {
			return(ColType.LSOA);
		}
		public String keyTables() {
			String tableId = ((Column) this).table.id;
			return(String.format("lsoa_regions %s_lsoa_regions", tableId));
		}
		public String keyLink() {
			String tableId = ((Column) this).table.id;
			String result = String.format("%s.%s = %s_lsoa_regions.lsoa04cd AND applicant.lsoa_regions_gid = %s_lsoa_regions.gid", tableId, name, tableId, tableId);
			return(result);
		}
		
		public int granularity() {
			return(30);
		}
	}

	/** ONS Mid super output area */
	public static class ColMSOA extends ColChar implements LinkColumn {
		ColMSOA() {
			super();
		}
		public ColMSOA(Column column) {
			super(column);
		}

		public ColType type() {
			return(ColType.MSOA);
		}
		public String keyTables() {
			String tableId = ((Column) this).table.id;
			return(String.format("msoa_regions %s_msoa_regions", tableId));
		}
		public String keyLink() {
			String tableId = ((Column) this).table.id;
			String result = String.format("%s.%s = %s_msoa_regions.msoa04cd AND applicant.msoa_regions_gid = %s_msoa_regions.gid", tableId, name, tableId, tableId);
			return(result);
		}
		public int granularity() {
			return(20);
		}
	}
	
	/** Local authority census code */
	public static class ColLA extends ColChar implements LinkColumn {
		ColLA() {
			super();
		}
		public ColLA(Column column) {
			super(column);
		}

		public ColType type() {
			return(ColType.LA);
		}
		
		public String keyTables() {
			String tableId = ((Column) this).table.id;
			return(String.format("admin_lookup %s_admin_lookup, osregions %s_osregions", tableId, tableId));
		}
		public String keyLink() {
			String tableId = ((Column) this).table.id;
			String result = String.format("%s.%s = %s_admin_lookup.census_code and %s_osregions.unit_id = %s_admin_lookup.admin_unit_id AND applicant.osregions_gid = %s_osregions.gid", tableId, name, tableId, tableId, tableId, tableId);
			return(result);
		}
		public int granularity() {
			return(10);
		}
	}
	
	/** TODO BILS specific, and not yet implemented */
	static class ColApplicant extends ColInteger {
		ColApplicant() {
			super();
		}
		ColApplicant(Column column) {
			super(column);
		}

		public ColType type() {
			return(ColType.Applicant);
		}
	}

}
