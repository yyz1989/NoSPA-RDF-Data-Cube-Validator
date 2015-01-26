package cn.yyz.nospa.validator;

import cn.yyz.nospa.validator.nonsparql.NospaValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
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
        HashMap<String, String> rdfFileExt = new HashMap<String, String>();
        rdfFileExt.put("xml", "RDF/XML");
        rdfFileExt.put("rdf", "RDF/XML");
        rdfFileExt.put("nt", "N-TRIPLE");
        rdfFileExt.put("ttl", "TURTLE");
        rdfFileExt.put("n3", "N3");

        String inputPath, inputFormat, validatorType;
        System.out.println("===NoSPA RDF Data Cube Validator===");
        if (args.length != 2) {
            System.out.println("Error: Missing arguments");
            System.out.println("Usage: java -jar jar-name.jar <cube-file.(xml|rdf|nt|n3|ttl)> <(nospa|sparql)>");
            return;
        }
        else {
            inputPath = args[0];
            inputFormat = rdfFileExt.get(inputPath.substring(inputPath.lastIndexOf('.') + 1).toLowerCase());
            if (inputFormat == null) {
                System.out.println("Error: File path or filename is not valid");
                return;
            }
            validatorType = args[1].toUpperCase();
            if (!(validatorType.equals("NOSPA") || validatorType.equals("SPARQL"))) {
                System.out.println("Error: Validator type is not supported");
                return;
            }
        }

        long start = System.currentTimeMillis();
        Validator validator = ValidatorFactory.createValidator(validatorType, inputPath, inputFormat);
        validator.normalize();
        validator.validateAll();
        long end = System.currentTimeMillis();
        //validator.exportModel(outputPath, outputFormat);
        logger.info("The validation task completed in " + Long.toString(end - start) + "ms");
    }
}
