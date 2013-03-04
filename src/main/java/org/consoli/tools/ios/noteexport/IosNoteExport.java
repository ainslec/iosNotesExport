/*
 * Copyright 2012 Consoli Limited
 * 
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.consoli.tools.ios.noteexport;


import java.awt.Desktop;
import java.awt.FlowLayout;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileFilter;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.UIManager;

/**
 * Tiny utility to scan through iOS backup folders looking for the SQLite Database containing notes then to export
 * the notes to a CSV file.<br/>
 * 
 * The heavy lifting is done via the excellent SQLite JDBC Driver at ({@link http://www.xerial.org}.
 * 
 * @author Chris Ainsley
 *
 */
public class IosNoteExport {
	
	static final String USER_HOME = System.getProperty("user.home");
	
	// Only currently supporting note databases up to 2 megabytes to speed up scanning ... this may be configurable in later releases
	static final int MAX_SIZE = 2048 * 1024;
	
	File _baseDir;



	public IosNoteExport() {
		File baseDir = new File(USER_HOME + "/AppData/Roaming/Apple Computer");
		if (!baseDir.isDirectory()) {
			baseDir = new File("anythinghere").getParentFile();
		}

		_baseDir = selectFolder("Select location of iTunes backup (unencrypted)", baseDir);

	}
	
	public File getBaseDir() {
		return _baseDir;
	}
	
	public File selectFolder(String title, File baseFolder) {
		JFileChooser fileChooser = new JFileChooser(baseFolder);
		fileChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
		fileChooser
				.setDialogTitle(title);
		fileChooser.setAcceptAllFileFilterUsed(false);
		if (fileChooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
			File currentDirectory = fileChooser.getSelectedFile();
			return currentDirectory;
		} else {
			throw new RuntimeException("No folder selected.");
		}
	}

	public List<File> findFileCandidates() throws SQLException {
		List<File> candidates = new ArrayList<File>();
		scanFolder(_baseDir, candidates, 0);

		return candidates;
	}

	public void scanFolder(File folder, List<File> results, int recursionLevel) throws SQLException {
		File[] matchingFiles = folder.listFiles(new FileFilter() {

			@Override
			public boolean accept(File pathname) {
				if (pathname.isDirectory()) {
					return true;
				}

				if (pathname.isFile() && pathname.length() <= MAX_SIZE) {
					return true;
				}

				return false;
			}
		});

		// Go through files first

		for (File f : matchingFiles) {
			if (f.isFile()) {
				if (isNoteBackupFile(f)) {
					results.add(f);
				}
				
			}
		}

		// Now recurse

		for (File f : matchingFiles) {
			if (f.isDirectory()) {
				scanFolder(f, results, recursionLevel + 1);
			}
		}
	}

	public boolean isNoteBackupFile(File f) throws SQLException {
		Connection c = null;
		Statement statement = null;
		try {
			c = getConnection(f.getAbsolutePath());
			statement = c.createStatement();
			statement
					.execute("select ZNOTE.Z_PK from ZNOTE, ZNOTEBODY where ZNOTE.ZBODY = ZNOTEBODY.Z_PK");
			return true;
		} catch (SQLException e) {
			return false;
		} finally {
			try {
				if (statement != null) {
					statement.close();
				}
			} catch (Exception e) {
			}
			try {
				if (c != null) {
					c.close();
				}
			} catch (Exception e) {
			}
		}
	}

	public Connection getConnection(String url) throws SQLException {
		url = url.replace("\\", "/");
		return DriverManager.getConnection("jdbc:sqlite:" + url);
	}


	private void saveCSV(File folderToSaveIn, File databaseFile) throws Exception {
		Connection c = null;
		Statement statement = null;
		File fileNameToSaveAs = new File(folderToSaveIn, databaseFile.getName() + ".csv");
		BufferedWriter w = null;
		boolean success = false;
		try {
			 
			w = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(fileNameToSaveAs), "UTF-8"));
			c = getConnection(databaseFile.getAbsolutePath());
			statement = c.createStatement();
			statement
					.execute("select datetime(ZCREATIONDATE + 978307200 ,'unixepoch', 'localtime') as CreationDate, datetime(ZMODIFICATIONDATE + 978307200 ,'unixepoch', 'localtime') as ModificationDate, ZTITLE as Title, (ZCONTENT) as Content,ZNOTE.Z_PK as Z_PK, ZCREATIONDATE, ZMODIFICATIONDATE from ZNOTE, ZNOTEBODY where ZNOTE.ZBODY = ZNOTEBODY.Z_PK order by ZMODIFICATIONDATE");
			ResultSet rs = statement.getResultSet();
			String headerLine = createCsvLine( "CreationDate","ModificationDate","Title","Content", "Z_PK", "ZCREATIONDATE","ZMODIFICATIONDATE");
			w.write(headerLine);
			
			while (rs.next()) {
				String bodyLine = createCsvLine(rs.getString("CreationDate"),rs.getString("ModificationDate"),rs.getString("Title"),"<html><body>" + rs.getString("Content") + "</body></html>", rs.getString("Z_PK"), rs.getString("ZCREATIONDATE"), rs.getString("ZMODIFICATIONDATE"));
				w.write(bodyLine);
			}
			
			success = true;
		} catch (SQLException e) {
			throw e;
		} finally {
			
			
			
			try {
				if (statement != null) {
					statement.close();
				}
			} catch (Exception e) {
			}
			
			try {
				if (c != null) {
					c.close();
				}
			} catch (Exception e) {
			}
			
			try {
				if (w != null) {
					w.flush();
					w.close();
				}
			} catch (Exception e) {
			}
			
			if (!success) {
				fileNameToSaveAs.delete();
			}
		}
	}

	StringBuilder sb1 = new StringBuilder();
	
	private String createCsvLine(String ... args) {
		sb1.setLength(0);
		int numArgs = args.length;
		
		for (int i=0; i < numArgs; i++) {
			if (i != 0) {
				sb1.append(",");
			}
			escapeCSVValue(args[i], sb1);
		}
		sb1.append("\r\n");
		
		return sb1.toString();
	}

	private String escapeCSVValue(String valueOf, StringBuilder sb) {

		sb.append("\"");
		
		valueOf = valueOf.replace("\r", "\\r");
		valueOf = valueOf.replace("\n", "\\n");
		valueOf = valueOf.replace("\"", "\"\"");
		sb.append(valueOf);
		sb.append("\"");
		
		return sb.toString();
	}
	
	

	public static void main(String[] args) {

		try {
			UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
		} catch (Exception e) {
			// Its OK, not a critical error
		}

		try {
			// Initialize the driver
			Class.forName("org.sqlite.JDBC");

			IosNoteExport noteExport = new IosNoteExport();
			
			List<File> candidates = noteExport.findFileCandidates();

			int numFiles = candidates.size();
			if (numFiles == 0) {
				System.out.println("Sorry, no unencrypted note files were found.");
			} else {
				for (File f : candidates) {
					System.out.println(f.getAbsolutePath());
				}
				String fileOrFiles = "file"+(numFiles > 1 ? "s" : "");
				JOptionPane.showMessageDialog(null,  + numFiles + " note " + fileOrFiles + " were found, please select folder in which to save CSV file.");

				File folderToSaveIn = noteExport.selectFolder("Select folder to save CSV files in", new File(USER_HOME));
				
				for (File f : candidates) {
					noteExport.saveCSV(folderToSaveIn, f);
				}

				JPanel dialogPanel = new JPanel();
				dialogPanel.setLayout(new FlowLayout(FlowLayout.LEFT,0,0));
				dialogPanel.add(new JLabel("Exported " + numFiles + " " + fileOrFiles + " successfully"));
				JOptionPane.showMessageDialog(null, dialogPanel, "Success", JOptionPane.INFORMATION_MESSAGE);
				
				try {
					Desktop.getDesktop().open(folderToSaveIn);
				} catch (Exception e) {
					// No worries, this is a niceity
				}
			}

		} catch (Exception e) {
			e.printStackTrace();
			JOptionPane.showMessageDialog(null, e.getMessage(),
					"Error occurred", JOptionPane.ERROR_MESSAGE);
		}
	}
}
