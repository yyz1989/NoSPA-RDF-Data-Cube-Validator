package cn.yyz.rdf.validator;

import com.hp.hpl.jena.update.UpdateAction;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;

/**
 * Created by yyz on 9/26/14.
 */
public class Main {
    public static void main(String[] args) {
        String workPath = System.getProperty("user.dir");
        Properties properties = new Properties();
        InputStream inputStream ;
        try {
            inputStream = Main.class.getClassLoader().getResourceAsStream("config.properties");
            properties.load(inputStream);
        } catch (Exception e) {
            e.printStackTrace();
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
        validator.checkIC5();
        long t3 = System.currentTimeMillis();
        System.out.println(t2 - t1);
        System.out.println(t3 - t2);
        //validator.output(outputPath, outputFormat);
    }
}
