package cn.yyz.nospa.validator;

/**
 * Created by yyz on 11/7/14.
 */
public interface Validator {

    /**
     * Export the current RDF model to a file.
     * @param outputPath file path used for output
     * @param outputFormat RDF serialization format (eg., RDF/XML, TURTLE, ...)
     */
    public void exportModel(String outputPath, String outputFormat);

    /**
     * Normalizes an abbreviated Data Cube with SPARQL queries.
     */
    public void normalize();

    /**
     * A shortcut function to execute all constraint validations.
     */
    public void validateAll();
}
