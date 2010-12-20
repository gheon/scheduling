package org.ow2.proactive.scheduler.ext.matlab.worker.util;

import org.jdom.Document;
import org.jdom.Element;
import org.jdom.input.SAXBuilder;
import org.objectweb.proactive.core.runtime.ProActiveRuntimeImpl;
import org.objectweb.proactive.utils.OperatingSystem;
import org.ow2.proactive.scheduler.core.properties.PASchedulerProperties;
import org.ow2.proactive.scheduler.ext.matsci.worker.util.MatSciConfigurationParser;
import org.ow2.proactive.scheduler.ext.matsci.worker.util.MatSciEngineConfig;

import java.io.File;
import java.io.FileNotFoundException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Matcher;


/**
 * MatlabConfigurationParser
 *
 * @author The ProActive Team
 */
public class MatlabConfigurationParser extends MatSciConfigurationParser {

    static final String configPath = "extensions/matlab/config/worker/MatlabConfiguration.xml";

    protected static OperatingSystem os = OperatingSystem.getOperatingSystem();

    static Document document;
    static Element racine;

    public static ArrayList<MatSciEngineConfig> getConfigs() throws Exception {
        File configFile;
        ArrayList<MatSciEngineConfig> configs = new ArrayList<MatSciEngineConfig>();

        String homestr = ProActiveRuntimeImpl.getProActiveRuntime().getProActiveHome();
        File homesched = new File(homestr);
        if (PASchedulerProperties.MATLAB_WORKER_CONFIGURATION_FILE != null &&
            PASchedulerProperties.MATLAB_WORKER_CONFIGURATION_FILE.getValueAsString() != "") {
            configFile = new File(PASchedulerProperties.MATLAB_WORKER_CONFIGURATION_FILE.getValueAsString());
        } else if (System.getProperty(PASchedulerProperties.MATLAB_WORKER_CONFIGURATION_FILE.getKey()) != null) {
            configFile = new File(System.getProperty(PASchedulerProperties.MATLAB_WORKER_CONFIGURATION_FILE
                    .getKey()));
        } else {
            URI configFileURI = homesched.toURI().resolve(configPath);
            configFile = new File(configFileURI);
        }
        if (!configFile.exists() || !configFile.canRead()) {
            throw new FileNotFoundException(configFile + " not found, aborting...");
        }

        System.out.println("Parsing configuration file :" + configFile);

        SAXBuilder sxb = new SAXBuilder();
        Document document = sxb.build(configFile);
        racine = document.getRootElement();

        List listInstallations = racine.getChildren("installation");

        boolean hasManyConfigs = (listInstallations.size() > 1);

        //On crée un Iterator sur notre liste
        Iterator i = listInstallations.iterator();
        while (i.hasNext()) {

            Element courant = (Element) i.next();
            String version = courant.getChild("version").getText();
            if ((version == null) || (version.trim().length() == 0)) {
                throw new IllegalArgumentException("In " + configFile + ", version element must not be empty");
            }
            version = version.trim();
            if (!version.matches("^[1-9][\\d]*\\.[\\d]+$")) {
                throw new IllegalArgumentException("In " + configFile +
                    ", version element must match XX.xx, received : " + version);
            }
            String home = courant.getChild("home").getText();
            if ((home == null) || (home.trim().length() == 0)) {
                throw new IllegalArgumentException("In " + configFile + ", home element must not be empty");
            }

            home = home.trim().replaceAll("/", Matcher.quoteReplacement("" + os.fileSeparator()));
            File filehome = new File(home);
            checkDir(filehome, configFile);

            String libdir = courant.getChild("libdir").getText();
            if ((libdir == null) || (libdir.trim().length() == 0)) {
                throw new IllegalArgumentException("In " + configFile + ", libdir element must not be empty");
            }
            libdir = libdir.trim().replaceAll("/", Matcher.quoteReplacement("" + os.fileSeparator()));
            File filelib = new File(filehome, libdir);
            checkDir(filelib, configFile);

            // extdir can be null
            String extdir = null;
            Element extel = courant.getChild("extdir");
            if (extel != null) {
                extdir = courant.getChild("extdir").getText();
            }

            String bindir = courant.getChild("bindir").getText();
            if ((bindir == null) || (bindir.trim().length() == 0)) {
                throw new IllegalArgumentException("In " + configFile + ", bindir element must not be empty");
            }
            bindir = bindir.trim().replaceAll("/", Matcher.quoteReplacement("" + os.fileSeparator()));
            File filebin = new File(filehome, bindir.trim());
            checkDir(filebin, configFile);

            String command = courant.getChild("command").getText();
            if ((command == null) || (command.trim().length() == 0)) {
                throw new IllegalArgumentException("In " + configFile + ", command element must not be empty");
            }
            command = command.trim();
            File filecommand = new File(filebin, command);
            checkFile(filecommand, configFile, true);

            String ptolemydir = courant.getChild("ptolemydir").getText();
            if ((ptolemydir == null) || (ptolemydir.trim().length() == 0)) {
                throw new IllegalArgumentException("In " + configFile +
                    ", ptolemydir element must not be empty");
            }
            ptolemydir = ptolemydir.trim().replaceAll("/", Matcher.quoteReplacement("" + os.fileSeparator()));

            File ptolemydirfile = new File(ptolemydir);
            if (!ptolemydirfile.isAbsolute()) {
                ptolemydirfile = new File(homesched, ptolemydir);
            }
            checkDir(ptolemydirfile, configFile);

            configs.add(new MatlabEngineConfig(home, version, libdir, bindir, extdir, command,
                hasManyConfigs, ptolemydirfile.toString()));
        }

        return configs;

    }

    public static void main(String[] args) throws Exception {
        System.out.println(getConfigs());
    }
}