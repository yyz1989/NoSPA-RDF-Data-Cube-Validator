package cn.yyz.nospa.validator;

import cn.yyz.nospa.validator.nonsparql.NospaValidator;
import cn.yyz.nospa.validator.sparql.SparqlValidator;
import com.hp.hpl.jena.rdf.model.Model;

/**
 * Created by yyz on 11/7/14.
 */
public class ValidatorFactory {
    private static ValidatorFactory ourInstance = new ValidatorFactory();

    public static ValidatorFactory getInstance() {
        return ourInstance;
    }

    private ValidatorFactory() {
    }

    /**
     * Create the dedicated validator based on the given type
     * @param validatorType validator type: "NOSPA" or "SPARQL"
     * @param model an RDF model
     * @return a concrete validator instance
     */
    public static Validator createValidator(String validatorType, Model model) {
        if (validatorType.equals("NOSPA")) {
            return new NospaValidator(model);
        }
        else if (validatorType.equals("SPARQL"))
            return new SparqlValidator(model);
        else {
            throw new IllegalArgumentException("Undefined Validator Type");
        }
    }

    /**
     * Create the dedicated validator based on the given type
     * @param validatorType validator type: "NOSPA" or "SPARQL"
     * @param filename complete path of the cube file to be validated
     * @param format RDF serialization format of the cube file
     * @return a concrete validator instance
     */
    public static Validator createValidator(String validatorType, String filename, String format) {
        if (validatorType.equals("NOSPA")) {
            return new NospaValidator(filename, format);
        }
        else if (validatorType.equals("SPARQL"))
            return new SparqlValidator(filename, format);
        else {
            throw new IllegalArgumentException("Undefined Validator Type");
        }
    }
}
