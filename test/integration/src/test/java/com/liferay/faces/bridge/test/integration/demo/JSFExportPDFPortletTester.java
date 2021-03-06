/**
 * Copyright (c) 2000-2017 Liferay, Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.liferay.faces.bridge.test.integration.demo;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

import org.junit.Assert;
import org.junit.Test;

import org.openqa.selenium.Cookie;
import org.openqa.selenium.WebElement;

import com.liferay.faces.bridge.test.integration.BridgeTestUtil;
import com.liferay.faces.test.selenium.Browser;
import com.liferay.faces.test.selenium.IntegrationTesterBase;
import com.liferay.faces.test.selenium.TestUtil;
import com.liferay.faces.test.selenium.applicant.ApplicantTesterBase;
import com.liferay.faces.test.selenium.assertion.SeleniumAssert;


/**
 * @author  Kyle Stiemann
 */
public class JSFExportPDFPortletTester extends IntegrationTesterBase {

	private static final Logger logger = Logger.getLogger(ApplicantTesterBase.class.getName());

	static {
		logger.setLevel(TestUtil.getLogLevel());
	}

	@Test
	public void runJSFExportPDFPortletTest() {

		// Test that the view contains links to all three pdfs.
		Browser browser = Browser.getInstance();
		browser.get(BridgeTestUtil.getDemoPageURL("jsf-pdf"));
		browser.waitForElementVisible(
			"//div[contains(@id,'export')][contains(@id,'pdf')][contains(@class,'liferay-faces-bridge-body')]");
		SeleniumAssert.assertElementVisible(browser,
			"//td[contains(text(),'Green')]/preceding-sibling::td/a[contains(text(),'Export')]");
		SeleniumAssert.assertElementVisible(browser,
			"//td[contains(text(),'Kessler')]/preceding-sibling::td/a[contains(text(),'Export')]");

		String shearerPDFLinkXpath =
			"//td[contains(text(),'Shearer')]/preceding-sibling::td/a[contains(text(),'Export')]";

		SeleniumAssert.assertElementVisible(browser, shearerPDFLinkXpath);

		try {

			// Test that the "Rich Shearer" link generates a PDF with the correct test. Note: since different browsers
			// and WebDriver implementations handle downloading files differently, download the file using a Java URL
			// connection.
			WebElement shearerPDFLinkElement = browser.findElementByXpath(shearerPDFLinkXpath);
			String shearerPDFLink = shearerPDFLinkElement.getAttribute("href");
			URL shearerPDFURL = new URL(shearerPDFLink);
			HttpURLConnection httpURLConnection = (HttpURLConnection) shearerPDFURL.openConnection();
			httpURLConnection.setRequestMethod("GET");

			Set<Cookie> cookies = browser.manage().getCookies();
			String cookieString = "";

			for (Cookie cookie : cookies) {
				cookieString += cookie.getName() + "=" + cookie.getValue() + ";";
			}

			httpURLConnection.addRequestProperty("Cookie", cookieString);

			InputStream inputStream = httpURLConnection.getInputStream();
			ReadableByteChannel readableByteChannel = Channels.newChannel(inputStream);
			String shearerRichPDFFilePath = TestUtil.JAVA_IO_TMPDIR + "Shearer-Rich.pdf";
			FileOutputStream fileOutputStream = new FileOutputStream(shearerRichPDFFilePath);
			FileChannel fileOutputStreamChannel = fileOutputStream.getChannel();
			fileOutputStreamChannel.transferFrom(readableByteChannel, 0, Long.MAX_VALUE);

			// Compare the text of the PDFs rather than the files (via a hash such as md5) becuase the portlet generates
			// slightly different PDFs each time the link is clicked (CreationDate, ModDate, and Info 7 0 R/ID are
			// different each time).
			File shearerRichPDFFile = new File(shearerRichPDFFilePath);
			String shearerRichPDFText = getPDFText(shearerRichPDFFile);
			URL expectedShearerRichPDFURL = JSFExportPDFPortletTester.class.getResource("/Shearer-Rich.pdf");
			String expectedShearerRichPDFFilePath = expectedShearerRichPDFURL.getFile();
			File expectedShearerRichPDFFile = new File(expectedShearerRichPDFFilePath);
			String expectedShearerRichPDFText = getPDFText(expectedShearerRichPDFFile);
			logger.log(Level.INFO, "Expected Shearer-Rich.pdf text:\n\n{0}\nDownloaded Shearer-Rich.pdf text:\n\n{1}",
				new String[] { expectedShearerRichPDFText, shearerRichPDFText });
			Assert.assertTrue(
				"The downloaded Shearer-Rich.pdf file's text does not match the expected Shearer-Rich.pdf file's text.",
				expectedShearerRichPDFText.equals(shearerRichPDFText));
		}
		catch (IOException e) {
			throw new AssertionError("Test failed due to the following " + e.getClass() + ": " + e.getMessage(), e);
		}
	}

	private String getPDFText(File file) throws IOException {

		PDDocument pdDocument = PDDocument.load(file);
		PDFTextStripper pdfTextStripper = new PDFTextStripper();
		String text = pdfTextStripper.getText(pdDocument);
		pdDocument.close();

		return text;
	}
}
