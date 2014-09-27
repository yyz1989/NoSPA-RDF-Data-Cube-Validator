package cn.yyz.rdf.validator;

/**
 * Created by yyz on 9/26/14.
 */
public class Main {
    public static void main(String[] args) {
        Validator validator = new Validator("test.ttl", "TTL");
        validator.checkConstraint("IC2");
    }
}
