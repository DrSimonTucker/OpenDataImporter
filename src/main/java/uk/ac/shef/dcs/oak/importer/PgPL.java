package uk.ac.shef.dcs.oak.importer;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;


public class PgPL {
	static void load(String name, boolean replace) throws SQLException {
		System.err.println("Loading PgPLFunctions/" + name + "\n");
		String sql = readFile(PgPL.class.getResourceAsStream("PgPLFunctions/" + name));

		// Could just use create function here instead of create or replace function,
		// but nice to avoid the exception
		if (!replace) {
			if (exists(name)) { 
				return;
			}
		}

		sql = "create or replace " + sql;
		System.err.println("statement: " + sql);
		Connection cx = DB.getConnection();
		Statement st = cx.createStatement();
		st.execute(sql);
		st.close();
	}
	
	static boolean exists(String name) throws SQLException {
		boolean result = false;
		Connection cx = DB.getConnection();
		Statement st = cx.createStatement();
		
		ResultSet rs = st.executeQuery("select count(*) from pg_proc where proname = '" + name + "'");
		if(rs.next()) {
			result = (rs.getInt(1) == 1);			
		}
		st.close();
		return(result);
	}

	static String readFile(InputStream is) {
		BufferedReader br = new BufferedReader(new InputStreamReader(is/*, "UTF-8"*/));
        StringBuilder builder = new StringBuilder(1024);
        try {
            for(String line=br.readLine(); line!=null; line=br.readLine()) {
                builder.append(line);
                builder.append('\n');
            }
        } catch (IOException e) { }
	    return(builder.toString());
	}
	
}
