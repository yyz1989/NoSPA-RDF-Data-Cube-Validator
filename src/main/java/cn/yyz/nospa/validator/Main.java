package cn.yyz.nospa.validator;

import cn.yyz.nospa.validator.nonsparql.Validator;
import cn.yyz.nospa.validator.sparql.SparqlValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Properties;

/**
 * Created by yyz on 9/26/14.
 */
public class Main {
    static {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMddHHmmss");
        System.setProperty("current.timestamp", dateFormat.format(new Date()));
    }
    public static void main(String[] args) {
        Logger logger = LoggerFactory.getLogger(Main.class);
        String workPath = System.getProperty("user.dir");
        String inputPath, inputFormat, outputPath, outputFormat;
        if (args.length != 2) {
            Properties properties = new Properties();
            InputStream inputStream;
            try {
                inputStream = Main.class.getClassLoader().getResourceAsStream("config.properties");
                properties.load(inputStream);
            } catch (IOException ioe) {
                logger.error("Unable to load the property file");
            }
            inputPath = workPath + properties.getProperty("INPUT_PATH");
            inputFormat = properties.getProperty("INPUT_FORMAT");
            //outputPath = workPath + properties.getProperty("OUTPUT_PATH");
            //outputFormat = properties.getProperty("OUTPUT_FORMAT");
        }
        else {
            inputPath = args[0];
            inputFormat = args[1];
            //outputPath = args[2];
            //outputFormat = args[3];
        }

        Validator validator = new Validator(inputPath, inputFormat);
        validator.normalize();

        //long t1 = System.currentTimeMillis();
        //validator.normalizeBySparql();
        long t2 = System.currentTimeMillis();
        validator.validateAll();
        long t3 = System.currentTimeMillis();

        //validator.exportModel(outputPath, outputFormat);

        //logger.info("The validation task completed in " + Long.toString(t2 - t1) + "ms");
        logger.info("The validation task completed in " + Long.toString(t3 - t2) + "ms");
    }
}
