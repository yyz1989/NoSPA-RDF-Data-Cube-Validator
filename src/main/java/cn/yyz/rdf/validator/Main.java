package cn.yyz.rdf.validator;

import com.hp.hpl.jena.update.UpdateAction;
<<<<<<< HEAD
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
=======

import java.io.FileInputStream;
import java.io.FileNotFoundException;
>>>>>>> 4209017f268b0da66ae5a24d739901fdc89a45ef
import java.io.InputStream;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;

/**
 * Created by yyz on 9/26/14.
 */
public class Main {
    public static void main(String[] args) {
<<<<<<< HEAD
        Logger logger = LoggerFactory.getLogger(Main.class);
=======
>>>>>>> 4209017f268b0da66ae5a24d739901fdc89a45ef
        String workPath = System.getProperty("user.dir");
        Properties properties = new Properties();
        InputStream inputStream ;
        try {
            inputStream = Main.class.getClassLoader().getResourceAsStream("config.properties");
            properties.load(inputStream);
<<<<<<< HEAD
        } catch (IOException ioe) {
            logger.error("Unable to load the property file");
=======
        } catch (Exception e) {
            e.printStackTrace();
>>>>>>> 4209017f268b0da66ae5a24d739901fdc89a45ef
        }
        String inputPath = workPath + properties.getProperty("INPUT_PATH");
        String inputFormat = properties.getProperty("INPUT_FORMAT");
        String outputPath = workPath + properties.getProperty("OUTPUT_PATH");
        String outputFormat = properties.getProperty("OUTPUT_FORMAT");

        Validator validator = new Validator(inputPath, inputFormat);
        validator.normalizePhase1();
        validator.normalizePhase2();

        long t1 = System.currentTimeMillis();
        //validator.normalizeBySparql();
        long t2 = System.currentTimeMillis();
<<<<<<< HEAD
        //validator.checkIC19();
        long t3 = System.currentTimeMillis();

        //validator.exportModel(outputPath, outputFormat);

        logger.info("The validation task completed in " + Long.toString(t2 - t1) + "ms");
        logger.info("The validation task completed in " + Long.toString(t3 - t2) + "ms");
=======
        validator.checkIC19();
        long t3 = System.currentTimeMillis();
        System.out.println(t2 - t1);
        System.out.println(t3 - t2);
        //validator.output(outputPath, outputFormat);
>>>>>>> 4209017f268b0da66ae5a24d739901fdc89a45ef
    }
}
