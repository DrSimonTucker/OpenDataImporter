function r_calc_import_column_breaks (tablename character, columnname character) returns character as $$
       require(classInt)
       s <- paste("select ", columnname, " as value from ", tablename)
       rows <<- pg.spi.exec(s)
       r <- rows$value
       breaks = classIntervals(r, n=7, style="fisher")
       breaks2 <- paste("'{", paste(breaks$brks, collapse=', '), "}'")
       s <- paste("update import_column set min=", min(r), ", max=", max(r), ", mean=", mean(r), ", breaks=", breaks2, " where import_name = '", tablename, "' and name = '", columnname, "';", sep='')
       pg.spi.exec(s)
       return("done")
$$
language 'plr';