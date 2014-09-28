package cn.yyz.rdf.validator;

/**
 * Created by yyz on 9/26/14.
 */
public class Main {
    public static void main(String[] args) {
        Validator validator = new Validator("largeTest.ttl", "TTL");
        validator.normalize();
        long t1 = System.currentTimeMillis();
        validator.checkConstraint("IC5");
        long t2 = System.currentTimeMillis();
        validator.checkIC5();
        long t3 = System.currentTimeMillis();
        System.out.println(t2 - t1);
        System.out.println(t3 - t2);
    }
}
