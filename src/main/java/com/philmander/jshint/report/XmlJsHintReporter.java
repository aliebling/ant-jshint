package com.philmander.jshint.report;

import org.apache.commons.lang3.StringEscapeUtils;

import com.philmander.jshint.JsHintError;
import com.philmander.jshint.JsHintReport;
import com.philmander.jshint.JsHintResult;

/**
 * XML format report
 * @author Phil Mander
 *
 */
public class XmlJsHintReporter implements JsHintReporter {

	private JsHintReport report;

	public XmlJsHintReporter(JsHintReport report) {

		this.report = report;
	}

	public String createReport() {

		StringBuilder output = new StringBuilder();

		output.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n");
		output.append("<jshint>\n");

		for (JsHintResult result : report.getResults()) {

			output.append("    <file");
			output.append(attr("name", result.getFile()));
			output.append(">\n");
			for (JsHintError error : result.getErrors()) {
				output.append("        <issue");
				output.append(attr("line", Integer.toString(error.getLine())));
				output.append(attr("char", Integer.toString(error.getCharacter())));
				output.append(attr("reason", error.getReason()));
				output.append(attr("evidence", error.getEvidence()));
				output.append("/>\n");
			}
			output.append("    </file>\n");
		}
		output.append("</jshint>");

		return output.toString();
	}

	private String attr(String name, String value) {
		String attr = " " + name + "=\"" + StringEscapeUtils.escapeXml(value) + "\"";
		return attr;
	}

}
