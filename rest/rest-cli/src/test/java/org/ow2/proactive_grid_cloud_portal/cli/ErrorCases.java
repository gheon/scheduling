package org.ow2.proactive_grid_cloud_portal.cli;

import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ssl.SslSelectChannelConnector;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.hamcrest.core.IsEqual;
import org.junit.AfterClass;
import org.junit.Assume;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import jline.WindowsTerminal;


public class ErrorCases {

    private static Server server;
    private static String serverUrl;

    private ByteArrayOutputStream capturedOutput;
    private String inputLines;

    @BeforeClass
    public static void startHttpsServer() throws Exception {    	
        server = new Server();
        SslContextFactory httpsConfiguration = new SslContextFactory();
        httpsConfiguration.setKeyStorePath(ErrorCases.class.getResource("keystore").toURI().getPath());
        httpsConfiguration.setKeyStorePassword("activeeon");
        SslSelectChannelConnector ssl = new SslSelectChannelConnector(httpsConfiguration);
        server.addConnector(ssl);
        server.start();
        serverUrl = "https://localhost:" + ssl.getLocalPort() + "/rest";
    }

    private static void skipIfHeadlessEnvironment() {
    	Assume.assumeThat(Boolean.valueOf(System.getProperty("java.awt.headless")), IsEqual.equalTo(false));
	}

	@AfterClass
    public static void stopHttpsServer() throws Exception {
        server.stop();
    }

    @Before
    public void captureInputOutput() throws Exception {
        System.setProperty(WindowsTerminal.DIRECT_CONSOLE, "false"); // to be able to type input on Windows
        inputLines = "";
        capturedOutput = new ByteArrayOutputStream();
        PrintStream captureOutput = new PrintStream(capturedOutput);
        System.setOut(captureOutput);
    }

    @Test
    public void ssl_error_is_showed_when_certificate_is_invalid_interactive_mode() throws Exception {
        typeLine("url('" + serverUrl + "')");
        typeLine("login('admin')").typeLine("admin");

        int exitCode = runCli();

        assertEquals(0, exitCode);
        assertThat(capturedOutput.toString(), containsString("SSL error"));
    }

    @Test
    public void ssl_error_is_showed_when_certificate_is_invalid_cli_mode() throws Exception {
        typeLine("admin");

        int exitCode = runCli("-u", serverUrl, "-l", "admin", "-lj");

        assertEquals(1, exitCode);
        assertThat(capturedOutput.toString(), containsString("SSL error"));
    }

    private ErrorCases typeLine(String line) {
        inputLines += line + System.lineSeparator();
        return this;
    }

    private int runCli(String... args) {
        System.setIn(new ByteArrayInputStream(inputLines.getBytes()));
        return new CommonEntryPoint().run(args);
    }

}