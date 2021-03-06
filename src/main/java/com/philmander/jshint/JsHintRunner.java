package com.philmander.jshint;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.cli.PosixParser;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;

import com.google.javascript.jscomp.JSSourceFile;
import com.philmander.jshint.report.PlainJsHintReporter;

/**
 * Standalone class for running jshint
 * 
 * @author Phil Mander 
 */
public class JsHintRunner {

	private JsHintLogger logger = null;

	private String jshintSrc = null;

	/**
	 * Basic, intital CLI implementation
	 * @param args
	 */
	public static void main(String[] args) {

		/*
		 * TODO: options and reporting
		 */
		
		File currentDir = new File(".");

		CommandLineParser parser = new PosixParser();

		// create the Options
		Options options = new Options();

		try {

			JsHintLogger logger = new JsHintLogger() {

				public void log(String msg) {
					System.out.println("[jshint] " + msg);
				}

				public void error(String msg) {
					System.err.println("[jshint] " + msg);
				}
			};

			CommandLine cl = parser.parse(options, args);

			JsHintRunner runner = new JsHintRunner();
			runner.setLogger(logger);

			List<String> files = new ArrayList<String>();
			for (Object arg : cl.getArgList()) {

				File testFile = new File(currentDir, (String) arg);
				if (testFile.exists()) {
					files.add(testFile.getAbsolutePath());
				} else {
					logger.error("Couldn't find " + testFile.getAbsolutePath());
				}
			}

			logger.log("Running JSHint on " + files.size() + " files");
			JsHintReport report = runner.lint(files.toArray(new String[files.size()]), new Properties());
			
			if(report.getTotalErrors() > 0) {
				logger.log(PlainJsHintReporter.getFailureMessage(report.getTotalErrors()));
			} else {
				logger.log(PlainJsHintReporter.getSuccessMessage(report.getNumFiles()));
			}

		} catch (ParseException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	/**
	 * Create new instance with default embedded jshint src version
	 */
	public JsHintRunner() {

	}

	/**
	 * Create instance with custom jshint src file
	 * 
	 * @param jshintSrc
	 *            The jshint src file path. If null, the default embedded
	 *            version will be used
	 */
	public JsHintRunner(String jshintSrc) {

		if (jshintSrc != null) {
			this.jshintSrc = jshintSrc;
		}
	}

	/**
	 * Run JSHint over a list of one or more files
	 * 
	 * @param files A list of absolute files
	 * @param options A map of jshint options to apply
	 * @return A JSHintReport object containing the full results data
	 * @throws IOException
	 */
	public JsHintReport lint(String[] files, Properties options) throws IOException {

		JsHintReport report = new JsHintReport(files.length);
		
		// start rhino
		Context ctx = Context.enter();
		ctx.setLanguageVersion(Context.VERSION_1_5);
		ScriptableObject global = ctx.initStandardObjects();

		// get js hint
		String jsHintFileName = "/jshint.js";

		// get js hint source from classpath or user file
		InputStream jsHintIn = jshintSrc != null ? new FileInputStream(new File(jshintSrc)) : this.getClass()
				.getResourceAsStream(jsHintFileName);

		JSSourceFile jsHintSrc = JSSourceFile.fromInputStream(jsHintFileName, jsHintIn);

		String runJsHintFile = "/jshint-runner.js";
		InputStream runJsHintIn = this.getClass().getResourceAsStream(runJsHintFile);
		JSSourceFile runJsHint = JSSourceFile.fromInputStream(runJsHintFile, runJsHintIn);

		// load jshint
		ctx.evaluateReader(global, jsHintSrc.getCodeReader(), jsHintSrc.getName(), 0, null);

		// define properties to store current js source info
		global.defineProperty("currentFile", "", ScriptableObject.DONTENUM);
		global.defineProperty("currentCode", "", ScriptableObject.DONTENUM);

		// jshint options
		ScriptableObject jsHintOpts = (ScriptableObject) ctx.newObject(global);
		jsHintOpts.defineProperty("rhino", true, ScriptableObject.DONTENUM);

		// user options
		for (Object key : options.keySet()) {
			boolean optionValue = Boolean.valueOf((String) options.get(key));
			jsHintOpts.defineProperty((String) key, optionValue, ScriptableObject.DONTENUM);
		}

		global.defineProperty("jsHintOpts", jsHintOpts, ScriptableObject.DONTENUM);

		// define object to store errors
		global.defineProperty("errors", ctx.newArray(global, 0), ScriptableObject.DONTENUM);

		// validate each file
		for (String file : files) {
			
			JsHintResult result = new JsHintResult(file);

			JSSourceFile jsFile = JSSourceFile.fromFile(file);

			// set current file on scope
			global.put("currentFile", global, jsFile.getName());
			global.put("currentCode", global, jsFile.getCode());
			global.put("errors", global, ctx.newArray(global, 0));

			ctx.evaluateReader(global, runJsHint.getCodeReader(), runJsHint.getName(), 0, null);

			// extract lint errors
			Scriptable errors = (Scriptable) global.get("errors", global);
			int numErrors = ((Number) errors.get("length", global)).intValue();

			if (numErrors > 0) {
				if(logger != null) {
					logger.log(PlainJsHintReporter.getFileFailureMessage(jsHintFileName));
				}
			}
			for (int i = 0; i < numErrors; i++) {

				// log detail for each error
				Scriptable errorDetail = (Scriptable) errors.get(i, global);

				try {
					String reason = (String) errorDetail.get("reason", global);
					int line = ((Number) errorDetail.get("line", global)).intValue();
					int character = ((Number) errorDetail.get("character", global)).intValue();
					String evidence = ((String) errorDetail.get("evidence", global)).replace(
							"^\\s*(\\S*(\\s+\\S+)*)\\s*$", "$1");
					
					JsHintError hintError = new JsHintError(reason, evidence, line, character);
					result.addError(hintError);
					
					if(logger != null) {
						logger.log(PlainJsHintReporter.getIssueMessage(reason, evidence, line, character));
					}
				} catch (ClassCastException e) {
					
					if(logger != null) {
						// TODO: See issue #1. Why is this happening?
						logger.error(("Problem casting JShint error variable for previous error. See issue (#1) ("
								+ e.getMessage() + ")"));
					} else {
						throw new RuntimeException(e);
					}
				}
			}
			report.addResult(result);
		}
		
		return report;
	}

	public void setLogger(JsHintLogger logger) {
		this.logger = logger;
	}
}